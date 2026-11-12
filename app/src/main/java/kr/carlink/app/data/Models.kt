package kr.carlink.app.data

/** 긴급 알림의 근거 등급. 수신 화면에 배지로 표시한다. */
enum class Trust(val label: String) {
    VEHICLE_SENSED("차량 감지"),
    MULTI_REPORT("복수 확인"),
    DRIVER_REPORT("운전자 신고"),
    NONE("")
}

/** 서버를 오가는 메시지. 문장 본문은 없고 문장 ID 만 담는다. */
data class WireMessage(
    val id: Long = 0,
    val segment: String,
    val fromVehicle: String,
    val fromOrder: Int,
    val target: SendTarget,
    val sentenceId: String,
    val tier: Tier,
    val trust: Trust = Trust.NONE,
    val createdAt: String = ""
)

/** 수신 차량이 자기 차량 신호와 대조한 결과. */
enum class Verification(val label: String) {
    NOT_APPLICABLE(""),
    CHECKING("차량 신호 확인 중"),
    VERIFIED("확인됨"),
    REFERENCE("참고"),
    UNAVAILABLE("차량 신호 확인 불가")
}

enum class CardState { PENDING, ACCEPTED, DECLINED, REPLIED }

/** 화면에 표시되는 수신 카드. */
data class IncomingCard(
    val key: Long,
    val message: WireMessage,
    val sentence: Sentence,
    /** true 면 발신 차량이 내 앞에 있다. */
    val fromAhead: Boolean,
    val state: CardState,
    val verification: Verification = Verification.NOT_APPLICABLE,
    val receivedAtMillis: Long = System.currentTimeMillis()
) {
    val directionLabel: String get() = if (fromAhead) "앞차에서" else "뒤차에서"
    val isEmergency: Boolean get() = sentence.tier == Tier.EMERGENCY
}

/** 이 차량의 설정. 실제 서비스에서는 위치·도로 정보로 자동 결정되며, 프로토타입은 직접 지정한다. */
data class VehicleProfile(
    val vehicleId: String = "CAR-A",
    /** 같은 도로 구간(segment) 안에서의 순서. 숫자가 클수록 앞쪽에 있다. */
    val order: Int = 2,
    val segment: String = "DEMO-ROAD-1",
    val brand: Brand = Brand.HYUNDAI
)

object Routing {
    /** 이 메시지가 [me] 에게 전달되어야 하는지 판단한다. */
    fun isForMe(m: WireMessage, me: VehicleProfile): Boolean {
        if (m.segment != me.segment) return false
        if (m.fromVehicle == me.vehicleId) return false
        return when (m.target) {
            SendTarget.FRONT -> me.order == m.fromOrder + 1
            SendTarget.BACK -> me.order == m.fromOrder - 1
            SendTarget.AHEAD_ALL -> me.order > m.fromOrder
            SendTarget.BEHIND_ALL -> me.order < m.fromOrder
        }
    }

    fun isFromAhead(m: WireMessage, me: VehicleProfile): Boolean = m.fromOrder > me.order
}
