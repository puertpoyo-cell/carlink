package kr.carlink.app.data

/** 메시지 등급. 일반은 수락 후 본문 표시, 긴급은 수락 없이 즉시 표시한다. */
enum class Tier { COURTESY, EMERGENCY }

enum class Category(val label: String, val tier: Tier) {
    EMERGENCY("긴급 안전", Tier.EMERGENCY),
    THANKS("감사", Tier.COURTESY),
    SORRY("사과", Tier.COURTESY),
    VEHICLE_STATE("차량 상태 안내", Tier.COURTESY),
    YIELD("양보 요청", Tier.COURTESY),
    NOTICE("진행 예고", Tier.COURTESY),
    REPLY("답장", Tier.COURTESY)
}

/** 누구에게 보내는지. FRONT/BACK 은 1:1, *_ALL 은 같은 도로 구간의 한쪽 방향 전체. */
enum class SendTarget(val label: String) {
    FRONT("앞차"),
    BACK("뒤차"),
    AHEAD_ALL("앞쪽 차량 전체"),
    BEHIND_ALL("뒤쪽 차량 전체")
}

/** 수신 차량이 자기 차량 신호로 확인할 수 있는 항목. */
enum class VerifySignal { NONE, DOOR, TRUNK }

/**
 * 카탈로그의 한 문장. 앱은 문장 본문이 아니라 [id] 만 주고받는다.
 * 자유 텍스트를 보내는 경로가 없으므로 욕설·혐오 표현은 구조적으로 전송될 수 없다.
 */
data class Sentence(
    val id: String,
    val category: Category,
    val hyundai: String,
    val genesis: String,
    val verify: VerifySignal = VerifySignal.NONE,
    val defaultTarget: SendTarget? = null
) {
    val tier: Tier get() = category.tier
    fun text(brand: Brand): String = if (brand == Brand.GENESIS) genesis else hyundai
}

enum class Brand(val label: String) { HYUNDAI("현대·기아"), GENESIS("제네시스") }

object Catalog {
    val sentences: List<Sentence> = listOf(
        // 긴급 안전 (6) — 수락 없이 즉시 표시
        Sentence("E01", Category.EMERGENCY, "뒤에서 구급차가 와요. 오른쪽으로 비켜 주세요", "긴급차량이 접근 중입니다. 진로를 양보해 주십시오", defaultTarget = SendTarget.AHEAD_ALL),
        Sentence("E02", Category.EMERGENCY, "전방에 사고가 났어요. 속도를 줄이세요", "전방 사고 발생. 감속하십시오", defaultTarget = SendTarget.BEHIND_ALL),
        Sentence("E03", Category.EMERGENCY, "앞차가 급정거했어요", "전방 차량 급정거. 주의하십시오", defaultTarget = SendTarget.BEHIND_ALL),
        Sentence("E04", Category.EMERGENCY, "앞에 낙하물이 있어요", "전방 낙하물 주의", defaultTarget = SendTarget.BEHIND_ALL),
        Sentence("E05", Category.EMERGENCY, "앞에 멈춘 차가 있어요", "전방 정차 차량 주의", defaultTarget = SendTarget.BEHIND_ALL),
        Sentence("E06", Category.EMERGENCY, "역주행 차량이 있어요", "역주행 차량 주의", defaultTarget = SendTarget.BEHIND_ALL),

        // 감사 (4)
        Sentence("T01", Category.THANKS, "감사합니다", "감사드립니다"),
        Sentence("T02", Category.THANKS, "양보해 주셔서 고마워요", "양보에 감사드립니다"),
        Sentence("T03", Category.THANKS, "알려주셔서 고마워요", "안내 감사드립니다"),
        Sentence("T04", Category.THANKS, "덕분에 안전하게 왔어요", "덕분에 안전하게 도착했습니다"),

        // 사과 (3)
        Sentence("S01", Category.SORRY, "죄송합니다", "실례했습니다"),
        Sentence("S02", Category.SORRY, "급하게 끼어들어 미안해요", "급하게 진입해 죄송합니다"),
        Sentence("S03", Category.SORRY, "놀라게 해서 죄송해요", "불편을 드려 죄송합니다"),

        // 차량 상태 안내 (8)
        Sentence("V01", Category.VEHICLE_STATE, "트렁크가 열려 있어요", "트렁크가 열려 있습니다", VerifySignal.TRUNK),
        Sentence("V02", Category.VEHICLE_STATE, "주유구가 열려 있어요", "주유구가 열려 있습니다"),
        Sentence("V03", Category.VEHICLE_STATE, "충전구가 열려 있어요", "충전구가 열려 있습니다"),
        Sentence("V04", Category.VEHICLE_STATE, "헤드라이트가 꺼져 있어요", "전조등이 꺼져 있습니다"),
        Sentence("V05", Category.VEHICLE_STATE, "방향지시등이 켜져 있어요", "방향지시등이 켜져 있습니다"),
        Sentence("V06", Category.VEHICLE_STATE, "문이 덜 닫혔어요", "도어가 완전히 닫히지 않았습니다", VerifySignal.DOOR),
        Sentence("V07", Category.VEHICLE_STATE, "타이어가 이상해 보여요", "타이어 점검이 필요해 보입니다"),
        Sentence("V08", Category.VEHICLE_STATE, "짐이 떨어질 것 같아요", "적재물이 불안정해 보입니다"),

        // 양보 요청 (5)
        Sentence("Y01", Category.YIELD, "잠깐 지나가게 비켜주세요. 감사합니다", "잠시 길을 양보해 주시면 감사하겠습니다"),
        Sentence("Y02", Category.YIELD, "급한 일이 있어요, 양해 부탁드려요", "급한 사정이 있습니다. 양해 부탁드립니다"),
        Sentence("Y03", Category.YIELD, "차 빼주실 수 있을까요", "출차 협조 부탁드립니다"),
        Sentence("Y04", Category.YIELD, "진입 좀 도와주세요", "진입 협조 부탁드립니다"),
        Sentence("Y05", Category.YIELD, "앞에 차선이 줄어요", "전방 차로가 감소합니다"),

        // 진행 예고 (4)
        Sentence("N01", Category.NOTICE, "먼저 지나갈게요", "먼저 지나가겠습니다"),
        Sentence("N02", Category.NOTICE, "여기서 빠질게요", "이곳에서 진출하겠습니다"),
        Sentence("N03", Category.NOTICE, "뒤에서 따라갈게요", "뒤에서 따르겠습니다"),
        Sentence("N04", Category.NOTICE, "잠시 정차할게요", "잠시 정차하겠습니다"),

        // 빠른 답장 (카탈로그 30문장과 별도, 수신 카드에서만 사용)
        Sentence("R01", Category.REPLY, "감사합니다", "감사드립니다"),
        Sentence("R02", Category.REPLY, "알겠습니다", "알겠습니다"),
        Sentence("R03", Category.REPLY, "죄송합니다", "실례했습니다")
    )

    private val byId = sentences.associateBy { it.id }

    fun find(id: String): Sentence? = byId[id]

    fun of(category: Category): List<Sentence> = sentences.filter { it.category == category }

    val sendCategories: List<Category> = listOf(
        Category.EMERGENCY, Category.THANKS, Category.SORRY,
        Category.VEHICLE_STATE, Category.YIELD, Category.NOTICE
    )

    val quickReplies: List<Sentence> = of(Category.REPLY)

    /** 차량 신호(급감속 등)로 자동 발신되는 긴급 문장. */
    const val AUTO_HARD_BRAKE = "E03"
}
