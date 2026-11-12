package kr.carlink.app.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kr.carlink.app.CarLinkViewModel
import kr.carlink.app.LinkMode
import kr.carlink.app.UiState
import kr.carlink.app.data.Brand
import kr.carlink.app.data.CardState
import kr.carlink.app.data.Catalog
import kr.carlink.app.data.Category
import kr.carlink.app.data.IncomingCard
import kr.carlink.app.data.SendTarget
import kr.carlink.app.data.Sentence
import kr.carlink.app.data.Tier
import kr.carlink.app.data.Trust
import kr.carlink.app.data.Verification

@Composable
fun CarLinkScreen(state: UiState, vm: CarLinkViewModel) {
    var showSetup by remember { mutableStateOf(false) }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Palette.Background)
            .systemBarsPadding()
    ) {
        Column(modifier = Modifier.fillMaxSize().padding(24.dp)) {
            TopBar(state = state, showSetup = showSetup, onToggleSetup = { showSetup = !showSetup }, onBrand = vm::setBrand)
            Spacer(Modifier.height(18.dp))
            Row(modifier = Modifier.fillMaxSize(), horizontalArrangement = Arrangement.spacedBy(18.dp)) {
                if (showSetup) {
                    SetupPanel(state = state, vm = vm, modifier = Modifier.weight(1.15f).fillMaxHeight())
                } else {
                    SendPanel(state = state, vm = vm, modifier = Modifier.weight(1.15f).fillMaxHeight())
                }
                ReceivePanel(state = state, vm = vm, modifier = Modifier.weight(1f).fillMaxHeight())
            }
        }

        // 긴급 안전 알림: 수락 절차 없이 화면 최상단에 즉시 표시한다.
        state.emergency?.let { card ->
            EmergencyBanner(
                card = card,
                brand = state.profile.brand,
                onDismiss = vm::dismissEmergency,
                modifier = Modifier.align(Alignment.TopCenter).padding(top = 16.dp)
            )
        }
    }
}

// ------------------------------------------------------------------ 상단 바

@Composable
private fun TopBar(state: UiState, showSetup: Boolean, onToggleSetup: () -> Unit, onBrand: (Brand) -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.weight(1f)) {
            Text("CarLink", color = Palette.Text, fontSize = 34.sp, fontWeight = FontWeight.Bold)
            Text(
                "${state.profile.vehicleId} · 도로 ${state.profile.segment} · 순서 ${state.profile.order}",
                color = Palette.Text3, fontSize = 16.sp
            )
        }
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically) {
            val linkColor = when (state.linkMode) {
                LinkMode.ONLINE -> Palette.Mint
                LinkMode.ERROR -> Palette.Red
                LinkMode.CONNECTING -> Palette.Amber
                LinkMode.LOCAL_DEMO -> Palette.Amber
            }
            Badge(
                text = when (state.linkMode) {
                    LinkMode.ONLINE -> "서버 연결됨"
                    LinkMode.ERROR -> "서버 오류"
                    LinkMode.CONNECTING -> "서버 연결 중"
                    LinkMode.LOCAL_DEMO -> "로컬 데모"
                },
                color = linkColor
            )
            Badge(
                text = if (state.vehicleReady) "차량 신호 연결됨" else "차량 신호 없음",
                color = if (state.vehicleReady) Palette.Mint else Palette.Text3
            )
            Brand.values().forEach { b ->
                Pill(text = b.label, selected = state.profile.brand == b, fontSize = 15.sp) { onBrand(b) }
            }
            Pill(text = if (showSetup) "보내기 화면" else "설정·진단", selected = showSetup, color = Palette.Amber, fontSize = 15.sp, onClick = onToggleSetup)
        }
    }
}

// ------------------------------------------------------------------ 발신

@Composable
private fun SendPanel(state: UiState, vm: CarLinkViewModel, modifier: Modifier = Modifier) {
    var category by remember { mutableStateOf(Category.THANKS) }
    var target by remember { mutableStateOf(SendTarget.FRONT) }
    val isEmergency = category.tier == Tier.EMERGENCY

    GlassPanel(modifier = modifier) {
        Column(modifier = Modifier.fillMaxSize()) {
            Text("보내기", color = Palette.Text, fontSize = 22.sp, fontWeight = FontWeight.SemiBold)
            Spacer(Modifier.height(12.dp))

            Row(
                modifier = Modifier.horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Catalog.sendCategories.forEach { c ->
                    Pill(
                        text = c.label,
                        selected = category == c,
                        color = if (c.tier == Tier.EMERGENCY) Palette.Red else Palette.Accent
                    ) { category = c }
                }
            }
            Spacer(Modifier.height(14.dp))

            if (isEmergency) {
                Text(
                    "긴급 안전 알림은 상대의 수락 없이 즉시 표시되고 음성으로 안내됩니다. 문장마다 정해진 방향의 차량 전체에 전달됩니다.",
                    color = Palette.Red, fontSize = 16.sp
                )
            } else {
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text("받는 차량", color = Palette.Text3, fontSize = 16.sp)
                    Pill(text = "앞차", selected = target == SendTarget.FRONT) { target = SendTarget.FRONT }
                    Pill(text = "뒤차", selected = target == SendTarget.BACK) { target = SendTarget.BACK }
                }
            }
            Spacer(Modifier.height(14.dp))

            Column(
                modifier = Modifier.weight(1f).verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Catalog.of(category).chunked(2).forEach { pair ->
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.fillMaxWidth()) {
                        pair.forEach { s ->
                            val sendTo = if (isEmergency) (s.defaultTarget ?: SendTarget.BEHIND_ALL) else target
                            TileButton(
                                text = if (isEmergency) "${s.text(state.profile.brand)}\n→ ${sendTo.label}" else s.text(state.profile.brand),
                                color = if (isEmergency) Palette.Red else Palette.Accent,
                                modifier = Modifier.weight(1f)
                            ) { vm.send(s, sendTo) }
                        }
                        if (pair.size == 1) Spacer(Modifier.weight(1f))
                    }
                }
            }

            if (state.lastSent.isNotBlank()) {
                Spacer(Modifier.height(12.dp))
                Text("최근 발신  ${state.lastSent}", color = Palette.Text2, fontSize = 15.sp)
            }
        }
    }
}

// ------------------------------------------------------------------ 수신

@Composable
private fun ReceivePanel(state: UiState, vm: CarLinkViewModel, modifier: Modifier = Modifier) {
    GlassPanel(modifier = modifier) {
        Column(modifier = Modifier.fillMaxSize()) {
            Text("받은 메시지", color = Palette.Text, fontSize = 22.sp, fontWeight = FontWeight.SemiBold)
            Spacer(Modifier.height(12.dp))
            if (state.cards.isEmpty()) {
                Text("아직 받은 메시지가 없습니다.", color = Palette.Text3, fontSize = 17.sp)
            }
            Column(
                modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                state.cards.forEach { card ->
                    MessageCard(card = card, brand = state.profile.brand, vm = vm)
                }
            }
        }
    }
}

@Composable
private fun MessageCard(card: IncomingCard, brand: Brand, vm: CarLinkViewModel) {
    val accent = if (card.isEmergency) Palette.Red else Palette.Accent
    GlassPanel(
        modifier = Modifier.fillMaxWidth(),
        strong = true,
        borderColor = if (card.isEmergency) Palette.Red.copy(alpha = 0.6f) else Palette.Line,
        padding = 16.dp
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                Text(card.directionLabel, color = accent, fontSize = 17.sp, fontWeight = FontWeight.SemiBold)
                Badge(text = card.sentence.category.label, color = accent)
                if (card.message.trust != Trust.NONE) Badge(text = card.message.trust.label, color = Palette.Amber)
                when (card.verification) {
                    Verification.VERIFIED -> Badge(text = card.verification.label, color = Palette.Mint)
                    Verification.REFERENCE -> Badge(text = card.verification.label, color = Palette.Text3)
                    Verification.CHECKING, Verification.UNAVAILABLE -> Badge(text = card.verification.label, color = Palette.Amber)
                    Verification.NOT_APPLICABLE -> Unit
                }
            }

            when (card.state) {
                CardState.PENDING -> {
                    // 일반 메시지는 수락하기 전까지 본문을 보여 주지 않는다.
                    Text("메시지가 도착했습니다. 확인하시겠습니까?", color = Palette.Text2, fontSize = 18.sp)
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        Pill(text = "확인", selected = true, color = Palette.Mint) { vm.accept(card.key) }
                        Pill(text = "받지 않음") { vm.decline(card.key) }
                    }
                }
                CardState.DECLINED -> {
                    Text("받지 않은 메시지입니다.", color = Palette.Text3, fontSize = 17.sp)
                }
                CardState.ACCEPTED, CardState.REPLIED -> {
                    Text(card.sentence.text(brand), color = Palette.Text, fontSize = 24.sp, fontWeight = FontWeight.SemiBold)
                    if (card.verification == Verification.VERIFIED) {
                        Text("내 차량의 도어 신호와 일치합니다.", color = Palette.Mint, fontSize = 15.sp)
                    } else if (card.verification == Verification.REFERENCE) {
                        Text("내 차량 신호로는 열림이 확인되지 않았습니다.", color = Palette.Text3, fontSize = 15.sp)
                    }
                    val canReply = card.state == CardState.ACCEPTED && !card.isEmergency && card.sentence.category != Category.REPLY
                    if (canReply) {
                        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                            Catalog.quickReplies.forEach { r ->
                                Pill(text = r.text(brand), fontSize = 16.sp) { vm.reply(card.key, r) }
                            }
                        }
                    } else if (card.state == CardState.REPLIED) {
                        Text("답장을 보냈습니다.", color = Palette.Text3, fontSize = 15.sp)
                    }
                }
            }
        }
    }
}

@Composable
private fun EmergencyBanner(card: IncomingCard, brand: Brand, onDismiss: () -> Unit, modifier: Modifier = Modifier) {
    val shape = RoundedCornerShape(22.dp)
    Box(
        modifier = modifier
            .fillMaxWidth(0.72f)
            .clip(shape)
            .background(Palette.RedDeep)
            .border(2.dp, Palette.Red, shape)
            .clickable(onClick = onDismiss)
            .padding(horizontal = 32.dp, vertical = 24.dp)
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically) {
                Text("긴급 안전 알림", color = Palette.Red, fontSize = 20.sp, fontWeight = FontWeight.Bold)
                Text(card.directionLabel, color = Palette.Text2, fontSize = 18.sp)
                if (card.message.trust != Trust.NONE) Badge(text = card.message.trust.label, color = Palette.Amber)
            }
            Text(card.sentence.text(brand), color = Color.White, fontSize = 40.sp, fontWeight = FontWeight.Bold)
        }
    }
}

// ------------------------------------------------------------------ 설정·진단

@Composable
private fun SetupPanel(state: UiState, vm: CarLinkViewModel, modifier: Modifier = Modifier) {
    GlassPanel(modifier = modifier) {
        Column(
            modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            Text("이 차량 설정", color = Palette.Text, fontSize = 22.sp, fontWeight = FontWeight.SemiBold)
            Text(
                "에뮬레이터마다 서로 다른 차량과 순서를 지정합니다. 숫자가 클수록 도로에서 앞쪽에 있는 차량입니다.",
                color = Palette.Text3, fontSize = 15.sp
            )
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                listOf("CAR-A" to 3, "CAR-B" to 2, "CAR-C" to 1).forEach { (id, order) ->
                    Pill(
                        text = "$id (순서 $order)",
                        selected = state.profile.vehicleId == id
                    ) { vm.updateProfile(state.profile.copy(vehicleId = id, order = order)) }
                }
            }
            Text("${state.linkDetail}  ·  ${state.vehicleDetail}", color = Palette.Text2, fontSize = 15.sp)

            Text("차량 신호 (도어)", color = Palette.Text, fontSize = 20.sp, fontWeight = FontWeight.SemiBold)
            Text(
                "확인 대상 ${state.doorArea} · 현재 값 ${state.doorPosition?.toString() ?: "알 수 없음"} (0 닫힘, 0 초과 열림)",
                color = Palette.Text2, fontSize = 16.sp
            )
            if (state.doorAreas.isNotEmpty()) {
                Row(
                    modifier = Modifier.horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    state.doorAreas.forEach { a ->
                        Pill(text = a, selected = state.doorArea == a, fontSize = 14.sp) { vm.selectDoorArea(a) }
                    }
                }
            }

            Text("수신 테스트 (서버 없이 화면 확인)", color = Palette.Text, fontSize = 20.sp, fontWeight = FontWeight.SemiBold)
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Pill(text = "뒤차: 트렁크 열림") { vm.injectIncoming("V01", fromAhead = false) }
                Pill(text = "뒤차: 문 덜 닫힘") { vm.injectIncoming("V06", fromAhead = false) }
                Pill(text = "앞차: 감사") { vm.injectIncoming("T02", fromAhead = true) }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Pill(text = "긴급: 앞차 급정거", color = Palette.Red, selected = true) { vm.injectIncoming("E03", fromAhead = true) }
                Pill(text = "긴급: 뒤에서 구급차", color = Palette.Red, selected = true) { vm.injectIncoming("E01", fromAhead = false) }
            }

            Text("긴급 자동 발신", color = Palette.Text, fontSize = 20.sp, fontWeight = FontWeight.SemiBold)
            Text(
                "급감속 신호를 감지했을 때와 같은 동작을 수동으로 실행합니다. 뒤쪽 차량 전체에 '앞차 급정거'가 '차량 감지' 등급으로 전달됩니다.",
                color = Palette.Text3, fontSize = 15.sp
            )
            Row {
                Pill(text = "급정거 신호 발생시키기", color = Palette.Red, selected = true) { vm.triggerHardBrake() }
            }

            Text("Vehicle SDK 진단", color = Palette.Text, fontSize = 20.sp, fontWeight = FontWeight.SemiBold)
            Row {
                Pill(text = "사용 가능한 차량 신호 목록 보기", color = Palette.Amber) { vm.runDiagnostics() }
            }
            if (state.diagnostics.isNotBlank()) {
                Text(
                    state.diagnostics,
                    color = Palette.Text2, fontSize = 13.sp, fontFamily = FontFamily.Monospace
                )
            }
            Spacer(Modifier.width(1.dp))
        }
    }
}
