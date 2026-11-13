package kr.carlink.app.vehicle

import ai.pleos.playground.vehicle.Vehicle
import ai.pleos.playground.vehicle.api.model.area.DoorArea
import ai.pleos.playground.vehicle.constant.area.DoorValue
import ai.pleos.playground.vehicle.listener.DoorPositionListener
import android.content.Context
import android.util.Log

/**
 * Pleos Vehicle SDK 래퍼.
 *
 * 현재 연결된 신호
 *  - Door position: 수신한 '차량 상태 안내' 메시지를 내 차량의 실제 도어 상태와 대조(교차검증)
 *
 * 다음 단계에서 연결할 신호
 *  - Brake / Light / Safety: 급감속·비상등을 감지해 긴급 알림을 자동 발신.
 *    공식 샘플에 해당 메서드 이름이 나와 있지 않아, [describeApis] 로 에뮬레이터에서
 *    실제 메서드 목록을 확인한 뒤 연결한다.
 */
class VehicleGateway(context: Context) {

    private val appContext = context.applicationContext
    private var vehicle: Vehicle? = null

    var isReady: Boolean = false
        private set

    var lastError: String? = null
        private set

    private var doorListener: DoorPositionListener? = null
    private var listeningArea: DoorArea? = null

    /** SDK 를 초기화한다. 일반 Android 기기처럼 Vehicle Service 가 없는 환경에서는 false 를 돌려준다. */
    fun initialize(): Boolean {
        if (isReady) return true
        return try {
            val v = Vehicle(appContext)
            v.initialize()
            vehicle = v
            isReady = true
            lastError = null
            true
        } catch (t: Throwable) {
            lastError = t.javaClass.simpleName + ": " + (t.message ?: "")
            Log.w(TAG, "Vehicle SDK 초기화 실패", t)
            false
        }
    }

    fun release() {
        stopDoorUpdates()
        try {
            vehicle?.release()
        } catch (t: Throwable) {
            Log.w(TAG, "Vehicle SDK 해제 실패", t)
        }
        vehicle = null
        isReady = false
    }

    /** 이 SDK 버전이 제공하는 도어 영역 이름 목록 (예: ROW_1_LEFT). */
    fun doorAreaNames(): List<String> = try {
        DoorValue.values().map { it.name }
    } catch (t: Throwable) {
        emptyList()
    }

    /**
     * 도어 위치를 한 번 읽는다. 0 이면 닫힘, 0 보다 크면 열림으로 본다.
     * 결과는 SDK 콜백 스레드에서 전달될 수 있다.
     */
    fun readDoorPosition(areaName: String, onResult: (Int?) -> Unit, onError: (String) -> Unit) {
        val v = vehicle
        if (!isReady || v == null) {
            onError("Vehicle SDK 가 준비되지 않았습니다")
            return
        }
        try {
            val area = DoorArea(DoorValue.valueOf(areaName))
            v.getDoor().getCurrentDoorPosition(
                area = area,
                onComplete = { position -> onResult(position) },
                onFailed = { e -> onError(e.message ?: e.javaClass.simpleName) }
            )
        } catch (t: Throwable) {
            onError(t.message ?: t.javaClass.simpleName)
        }
    }

    /** 도어 위치 변화를 계속 받는다. */
    fun startDoorUpdates(areaName: String, onChanged: (Int?) -> Unit, onError: (String) -> Unit): Boolean {
        val v = vehicle
        if (!isReady || v == null) return false
        stopDoorUpdates()
        return try {
            val area = DoorArea(DoorValue.valueOf(areaName))
            val listener = object : DoorPositionListener {
                override fun onPositionUpdated(pos: Int?) = onChanged(pos)
                override fun onFailed(e: Exception) = onError(e.message ?: e.javaClass.simpleName)
            }
            v.getDoor().registerDoorPosition(area, listener)
            doorListener = listener
            listeningArea = area
            true
        } catch (t: Throwable) {
            onError(t.message ?: t.javaClass.simpleName)
            false
        }
    }

    fun stopDoorUpdates() {
        val v = vehicle
        val l = doorListener
        val a = listeningArea
        if (v != null && l != null && a != null) {
            try {
                v.getDoor().unregisterDoorPosition(a, l)
            } catch (t: Throwable) {
                Log.w(TAG, "도어 리스너 해제 실패", t)
            }
        }
        doorListener = null
        listeningArea = null
    }

    /**
     * 브레이크 페달 신호를 구독한다.
     *
     * SDK 의 리스너 타입이 공개 문서에 없어, 실행 중에 메서드 모양을 읽어 연결한다.
     * [onEvent] 의 첫 값은 SDK 가 준 원본 표시, 둘째 값은 '밟음' 여부(판단할 수 없으면 null)이다.
     */
    fun startBrakeUpdates(onEvent: (String, Boolean?) -> Unit): Boolean {
        val v = vehicle
        if (!isReady || v == null) return false
        return try {
            val brake = v.getBrake()
            val handlerFor = { label: String -> eventHandler(label, onEvent) }
            val register = brake.javaClass.methods.firstOrNull { it.name == "registerBrakePedalStatus" }
                ?: return false
            invokeWithProxies(brake, register, handlerFor("변화"))
            brake.javaClass.methods.firstOrNull { it.name == "getCurrentBrakePedalStatus" }?.let { m ->
                try {
                    invokeWithProxies(brake, m, handlerFor("현재"))
                } catch (t: Throwable) {
                    Log.w(TAG, "브레이크 현재 값 읽기 실패", t)
                }
            }
            true
        } catch (t: Throwable) {
            val cause = (t as? java.lang.reflect.InvocationTargetException)?.targetException ?: t
            onEvent("연결 실패: " + (cause.message ?: cause.javaClass.simpleName), null)
            Log.w(TAG, "브레이크 신호 구독 실패", cause)
            false
        }
    }

    /** 메서드의 인터페이스형 매개변수마다 콜백 대리 객체를 만들어 호출한다. */
    private fun invokeWithProxies(target: Any, method: java.lang.reflect.Method, handler: java.lang.reflect.InvocationHandler) {
        val args = method.parameterTypes.map { type ->
            if (!type.isInterface) {
                throw IllegalStateException("지원하지 않는 매개변수: " + method.parameterTypes.joinToString { it.simpleName })
            }
            java.lang.reflect.Proxy.newProxyInstance(type.classLoader, arrayOf(type), handler)
        }
        method.invoke(target, *args.toTypedArray())
    }

    private fun eventHandler(label: String, onEvent: (String, Boolean?) -> Unit) =
        java.lang.reflect.InvocationHandler { proxy, method, args ->
            when (method.name) {
                "hashCode" -> return@InvocationHandler System.identityHashCode(proxy)
                "equals" -> return@InvocationHandler (proxy === args?.getOrNull(0))
                "toString" -> return@InvocationHandler "CarLinkBrakeCallback"
            }
            val value = args?.getOrNull(0)
            if (value is Throwable) {
                onEvent("$label 오류: " + (value.message ?: value.javaClass.simpleName), null)
            } else {
                Log.i(TAG, "brake ${method.name}($value)")
                onEvent("$label: $value", interpretPressed(value))
            }
            when (method.returnType) {
                java.lang.Boolean.TYPE -> false
                java.lang.Integer.TYPE -> 0
                java.lang.Void.TYPE -> null
                else -> Unit
            }
        }

    private fun interpretPressed(value: Any?): Boolean? = when (value) {
        null -> null
        is Boolean -> value
        is Number -> value.toDouble() > 0.0
        else -> {
            val s = value.toString().uppercase()
            when {
                listOf("NOT", "RELEAS", "OFF", "NONE", "FALSE", "INACTIVE").any { s.contains(it) } -> false
                listOf("PRESS", "ON", "TRUE", "ACTIVE").any { s.contains(it) } -> true
                else -> null
            }
        }
    }

    /**
     * 긴급 알림 자동 발신에 쓸 도메인(Brake·Light·Safety·TurnSignal·DrivingMode)의
     * 공개 메서드 이름을 나열한다. 진단 화면과 Logcat(태그 CarLinkVehicle)에 표시된다.
     */
    fun describeApis(): String {
        val v = vehicle ?: return "Vehicle SDK 가 준비되지 않았습니다. ${lastError ?: ""}"
        val sb = StringBuilder()
        fun dump(title: String, getter: () -> Any) {
            try {
                val names = getter().javaClass.methods
                    .map { it.name }
                    .filter { n -> PREFIXES.any { n.startsWith(it) } }
                    .distinct()
                    .sorted()
                sb.append("[").append(title).append("] ").append(names.joinToString(", ")).append("\n\n")
            } catch (t: Throwable) {
                sb.append("[").append(title).append("] 조회 실패: ").append(t.message).append("\n\n")
            }
        }
        dump("Brake") { v.getBrake() }
        dump("Light") { v.getLight() }
        dump("Safety") { v.getSafety() }
        dump("TurnSignal") { v.getTurnSignal() }
        dump("DrivingMode") { v.getDrivingMode() }
        dump("Door") { v.getDoor() }
        dump("Fuel") { v.getFuel() }
        sb.append("[DoorValue] ").append(doorAreaNames().joinToString(", "))
        val text = sb.toString()
        text.chunked(3000).forEach { Log.i(TAG, it) }
        return text
    }

    private companion object {
        const val TAG = "CarLinkVehicle"
        val PREFIXES = listOf("get", "check", "register", "is")
    }
}
