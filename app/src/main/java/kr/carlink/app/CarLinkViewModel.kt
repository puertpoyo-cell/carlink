package kr.carlink.app

import android.app.Application
import android.speech.tts.TextToSpeech
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kr.carlink.app.data.Brand
import kr.carlink.app.data.CardState
import kr.carlink.app.data.Catalog
import kr.carlink.app.data.Category
import kr.carlink.app.data.IncomingCard
import kr.carlink.app.data.RelayClient
import kr.carlink.app.data.Routing
import kr.carlink.app.data.SendTarget
import kr.carlink.app.data.Sentence
import kr.carlink.app.data.Tier
import kr.carlink.app.data.Trust
import kr.carlink.app.data.VehicleProfile
import kr.carlink.app.data.Verification
import kr.carlink.app.data.VerifySignal
import kr.carlink.app.data.WireMessage
import kr.carlink.app.vehicle.VehicleGateway
import java.util.Locale
import java.util.concurrent.atomic.AtomicLong

enum class LinkMode { LOCAL_DEMO, CONNECTING, ONLINE, ERROR }

data class UiState(
    val profile: VehicleProfile = VehicleProfile(),
    val linkMode: LinkMode = LinkMode.LOCAL_DEMO,
    val linkDetail: String = "",
    val vehicleReady: Boolean = false,
    val vehicleDetail: String = "",
    val doorArea: String = "ROW_1_LEFT",
    val doorAreas: List<String> = emptyList(),
    val doorPosition: Int? = null,
    val cards: List<IncomingCard> = emptyList(),
    val emergency: IncomingCard? = null,
    val brakeSignal: String = "수신 전",
    val brakeWatching: Boolean = false,
    val lastSent: String = "",
    val diagnostics: String = ""
)

class CarLinkViewModel(app: Application) : AndroidViewModel(app) {

    private val _state = MutableStateFlow(UiState())
    val state: StateFlow<UiState> = _state.asStateFlow()

    private val relay = RelayClient(BuildConfig.SUPABASE_URL, BuildConfig.SUPABASE_ANON_KEY)
    private val vehicle = VehicleGateway(app)

    private var pollJob: Job? = null
    private var emergencyJob: Job? = null
    private val localKey = AtomicLong(-1) // 로컬 데모 메시지는 음수 번호를 쓴다.

    private var tts: TextToSpeech? = null
    private var ttsReady = false

    init {
        tts = TextToSpeech(app) { status ->
            if (status == TextToSpeech.SUCCESS) {
                tts?.language = Locale.KOREAN
                ttsReady = true
            }
        }
        startVehicle()
        startRelay()
    }

    // ---------------------------------------------------------------- 차량 SDK

    private fun startVehicle() {
        val ok = vehicle.initialize()
        _state.update {
            it.copy(
                vehicleReady = ok,
                vehicleDetail = if (ok) "Vehicle SDK ${BuildConfig.VEHICLE_SDK_VERSION} 연결됨" else (vehicle.lastError ?: "연결 실패"),
                doorAreas = vehicle.doorAreaNames()
            )
        }
        if (ok) {
            watchDoor(_state.value.doorArea)
            watchBrake()
        }
    }

    private var lastBrakePressed: Boolean? = null
    private var lastAutoSendAt = 0L

    /** 브레이크 페달이 '밟음'으로 바뀌는 순간 뒤쪽 차량에 긴급 알림을 자동 발신한다. */
    private fun watchBrake() {
        val started = vehicle.startBrakeUpdates { raw, pressed ->
            _state.update { it.copy(brakeSignal = raw) }
            if (pressed != null) {
                val rising = pressed && lastBrakePressed == false
                lastBrakePressed = pressed
                val now = System.currentTimeMillis()
                if (rising && now - lastAutoSendAt > AUTO_SEND_COOLDOWN_MS) {
                    lastAutoSendAt = now
                    triggerHardBrake()
                }
            }
        }
        _state.update { it.copy(brakeWatching = started) }
    }

    fun selectDoorArea(area: String) {
        _state.update { it.copy(doorArea = area, doorPosition = null) }
        watchDoor(area)
    }

    private fun watchDoor(area: String) {
        if (!vehicle.isReady) return
        vehicle.startDoorUpdates(
            areaName = area,
            onChanged = { pos -> _state.update { it.copy(doorPosition = pos) } },
            onError = { msg -> _state.update { it.copy(vehicleDetail = "도어 신호 오류: $msg") } }
        )
        vehicle.readDoorPosition(
            areaName = area,
            onResult = { pos -> _state.update { it.copy(doorPosition = pos) } },
            onError = { msg -> _state.update { it.copy(vehicleDetail = "도어 신호 오류: $msg") } }
        )
    }

    fun runDiagnostics() {
        _state.update { it.copy(diagnostics = vehicle.describeApis()) }
    }

    // ---------------------------------------------------------------- 중계 서버

    private fun startRelay() {
        pollJob?.cancel()
        if (!relay.isConfigured) {
            _state.update { it.copy(linkMode = LinkMode.LOCAL_DEMO, linkDetail = "서버 설정 없음 · 로컬 데모 모드") }
            return
        }
        _state.update { it.copy(linkMode = LinkMode.CONNECTING, linkDetail = "서버 연결 중") }
        pollJob = viewModelScope.launch {
            var lastId = -1L
            while (isActive) {
                try {
                    if (lastId < 0) {
                        lastId = relay.latestId()
                        _state.update { it.copy(linkMode = LinkMode.ONLINE, linkDetail = "서버 연결됨") }
                    }
                    val me = _state.value.profile
                    val incoming = relay.fetchAfter(lastId, me.segment)
                    for (m in incoming) {
                        if (m.id > lastId) lastId = m.id
                        if (Routing.isForMe(m, me)) onIncoming(m, Routing.isFromAhead(m, me))
                    }
                    if (_state.value.linkMode != LinkMode.ONLINE) {
                        _state.update { it.copy(linkMode = LinkMode.ONLINE, linkDetail = "서버 연결됨") }
                    }
                } catch (e: Exception) {
                    _state.update { it.copy(linkMode = LinkMode.ERROR, linkDetail = e.message ?: "서버 오류") }
                }
                delay(1000)
            }
        }
    }

    fun updateProfile(profile: VehicleProfile) {
        _state.update { it.copy(profile = profile) }
    }

    // ---------------------------------------------------------------- 발신

    fun send(sentence: Sentence, target: SendTarget, trust: Trust = Trust.NONE) {
        val me = _state.value.profile
        val effectiveTrust = when {
            sentence.tier == Tier.EMERGENCY && trust == Trust.NONE -> Trust.DRIVER_REPORT
            else -> trust
        }
        val message = WireMessage(
            segment = me.segment,
            fromVehicle = me.vehicleId,
            fromOrder = me.order,
            target = target,
            sentenceId = sentence.id,
            tier = sentence.tier,
            trust = effectiveTrust
        )
        val label = "${target.label}에게: ${sentence.text(me.brand)}"
        if (!relay.isConfigured) {
            _state.update { it.copy(lastSent = "$label (로컬 데모 · 서버 미연결)") }
            return
        }
        viewModelScope.launch {
            try {
                relay.send(message)
                _state.update { it.copy(lastSent = "$label · 전송됨") }
            } catch (e: Exception) {
                _state.update { it.copy(lastSent = "$label · 전송 실패: ${e.message}") }
            }
        }
    }

    /** 급감속 신호가 들어왔을 때의 자동 발신. 현재는 진단 화면의 버튼으로 호출한다. */
    fun triggerHardBrake() {
        val sentence = Catalog.find(Catalog.AUTO_HARD_BRAKE) ?: return
        send(sentence, SendTarget.BEHIND_ALL, Trust.VEHICLE_SENSED)
    }

    // ---------------------------------------------------------------- 수신

    private fun onIncoming(m: WireMessage, fromAhead: Boolean) {
        val sentence = Catalog.find(m.sentenceId) ?: return // 카탈로그에 없는 문장은 표시하지 않는다.
        val key = if (m.id != 0L) m.id else localKey.getAndDecrement()
        val immediate = sentence.tier == Tier.EMERGENCY || sentence.category == Category.REPLY
        val card = IncomingCard(
            key = key,
            message = m,
            sentence = sentence,
            fromAhead = fromAhead,
            state = if (immediate) CardState.ACCEPTED else CardState.PENDING
        )
        _state.update { s ->
            s.copy(
                cards = (listOf(card) + s.cards).take(20),
                emergency = if (card.isEmergency) card else s.emergency
            )
        }
        if (card.isEmergency) {
            speak(sentence.text(_state.value.profile.brand), urgent = true)
            emergencyJob?.cancel()
            emergencyJob = viewModelScope.launch {
                delay(6000)
                _state.update { s -> if (s.emergency?.key == key) s.copy(emergency = null) else s }
            }
        } else if (sentence.category == Category.REPLY) {
            speak("${card.directionLabel} ${sentence.text(_state.value.profile.brand)}", urgent = false)
        }
    }

    fun dismissEmergency() {
        emergencyJob?.cancel()
        _state.update { it.copy(emergency = null) }
    }

    fun accept(key: Long) {
        val card = _state.value.cards.firstOrNull { it.key == key } ?: return
        val needsCheck = card.sentence.verify != VerifySignal.NONE
        updateCard(key) {
            it.copy(
                state = CardState.ACCEPTED,
                verification = if (needsCheck) Verification.CHECKING else Verification.NOT_APPLICABLE
            )
        }
        speak(card.sentence.text(_state.value.profile.brand), urgent = false)
        if (needsCheck) verifyWithDoor(key, if (card.sentence.verify == VerifySignal.TRUNK) TRUNK_AREA else _state.value.doorArea)
    }

    private fun verifyWithDoor(key: Long, area: String) {
        if (!vehicle.isReady) {
            updateCard(key) { it.copy(verification = Verification.UNAVAILABLE) }
            return
        }
        vehicle.readDoorPosition(
            areaName = area,
            onResult = { pos ->
                val result = when {
                    pos == null -> Verification.UNAVAILABLE
                    pos > 0 -> Verification.VERIFIED
                    else -> Verification.REFERENCE
                }
                updateCard(key) { it.copy(verification = result) }
            },
            onError = { updateCard(key) { it.copy(verification = Verification.UNAVAILABLE) } }
        )
    }

    fun decline(key: Long) = updateCard(key) { it.copy(state = CardState.DECLINED) }

    fun reply(key: Long, reply: Sentence) {
        val card = _state.value.cards.firstOrNull { it.key == key } ?: return
        send(reply, if (card.fromAhead) SendTarget.FRONT else SendTarget.BACK)
        updateCard(key) { it.copy(state = CardState.REPLIED) }
    }

    private fun updateCard(key: Long, transform: (IncomingCard) -> IncomingCard) {
        _state.update { s -> s.copy(cards = s.cards.map { if (it.key == key) transform(it) else it }) }
    }

    /** 서버 없이 수신 화면을 확인하기 위한 가상 수신. */
    fun injectIncoming(sentenceId: String, fromAhead: Boolean) {
        val sentence = Catalog.find(sentenceId) ?: return
        val me = _state.value.profile
        val m = WireMessage(
            segment = me.segment,
            fromVehicle = "DEMO",
            fromOrder = if (fromAhead) me.order + 1 else me.order - 1,
            target = if (fromAhead) SendTarget.BACK else SendTarget.FRONT,
            sentenceId = sentence.id,
            tier = sentence.tier,
            trust = if (sentence.id == Catalog.AUTO_HARD_BRAKE) Trust.VEHICLE_SENSED
            else if (sentence.tier == Tier.EMERGENCY) Trust.DRIVER_REPORT else Trust.NONE
        )
        onIncoming(m, fromAhead)
    }

    fun setBrand(brand: Brand) = updateProfile(_state.value.profile.copy(brand = brand))

    // ---------------------------------------------------------------- 음성 안내

    private fun speak(text: String, urgent: Boolean) {
        if (!ttsReady) return
        val mode = if (urgent) TextToSpeech.QUEUE_FLUSH else TextToSpeech.QUEUE_ADD
        tts?.speak(text, mode, null, "carlink-" + System.nanoTime())
    }

    private companion object {
        const val TRUNK_AREA = "REAR"
        const val AUTO_SEND_COOLDOWN_MS = 8000L
    }

    override fun onCleared() {
        pollJob?.cancel()
        emergencyJob?.cancel()
        vehicle.release()
        tts?.shutdown()
        tts = null
        super.onCleared()
    }
}
