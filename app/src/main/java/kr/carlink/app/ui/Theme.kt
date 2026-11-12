package kr.carlink.app.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/** Deep Midnight Navy 팔레트. */
object Palette {
    val Navy0 = Color(0xFF070C1A)
    val Navy1 = Color(0xFF0B1226)
    val Navy2 = Color(0xFF111A33)
    val Glass = Color(0x14FFFFFF)
    val GlassStrong = Color(0x24FFFFFF)
    val Line = Color(0x2EFFFFFF)
    val Text = Color(0xFFE9EDF7)
    val Text2 = Color(0xFFB6BFD6)
    val Text3 = Color(0xFF7F8AA8)
    val Accent = Color(0xFF6FA8FF)
    val Accent2 = Color(0xFF9CC6FF)
    val Mint = Color(0xFF7ADFB4)
    val Amber = Color(0xFFF4C56A)
    val Red = Color(0xFFFF6B6B)
    val RedDeep = Color(0xFF5A1620)

    val Background = Brush.verticalGradient(listOf(Navy2, Navy1, Navy0))
}

/** 반투명 유리 패널. */
@Composable
fun GlassPanel(
    modifier: Modifier = Modifier,
    strong: Boolean = false,
    borderColor: Color = Palette.Line,
    padding: Dp = 20.dp,
    content: @Composable () -> Unit
) {
    val shape = RoundedCornerShape(18.dp)
    Box(
        modifier = modifier
            .clip(shape)
            .background(if (strong) Palette.GlassStrong else Palette.Glass)
            .border(1.dp, borderColor, shape)
            .padding(padding)
    ) { content() }
}

/** 선택 가능한 알약 모양 버튼. */
@Composable
fun Pill(
    text: String,
    selected: Boolean = false,
    color: Color = Palette.Accent,
    fontSize: TextUnit = 18.sp,
    modifier: Modifier = Modifier,
    onClick: () -> Unit
) {
    val shape = RoundedCornerShape(50)
    Box(
        modifier = modifier
            .clip(shape)
            .background(if (selected) color.copy(alpha = 0.28f) else Palette.Glass)
            .border(1.dp, if (selected) color else Palette.Line, shape)
            .clickable(onClick = onClick)
            .padding(PaddingValues(horizontal = 20.dp, vertical = 12.dp)),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = text,
            color = if (selected) Palette.Text else Palette.Text2,
            fontSize = fontSize,
            fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal,
            textAlign = TextAlign.Center
        )
    }
}

/** 큰 터치 영역의 사각 버튼. 주행 중 조작을 고려해 최소 높이를 넉넉히 둔다. */
@Composable
fun TileButton(
    text: String,
    modifier: Modifier = Modifier,
    color: Color = Palette.Accent,
    fontSize: TextUnit = 20.sp,
    onClick: () -> Unit
) {
    val shape = RoundedCornerShape(16.dp)
    Box(
        modifier = modifier
            .clip(shape)
            .background(color.copy(alpha = 0.16f))
            .border(1.dp, color.copy(alpha = 0.55f), shape)
            .clickable(onClick = onClick)
            .padding(PaddingValues(horizontal = 18.dp, vertical = 18.dp)),
        contentAlignment = Alignment.CenterStart
    ) {
        Text(text = text, color = Palette.Text, fontSize = fontSize, fontWeight = FontWeight.Medium)
    }
}

@Composable
fun Badge(text: String, color: Color) {
    val shape = RoundedCornerShape(50)
    Box(
        modifier = Modifier
            .clip(shape)
            .background(color.copy(alpha = 0.18f))
            .border(1.dp, color.copy(alpha = 0.6f), shape)
            .padding(PaddingValues(horizontal = 12.dp, vertical = 5.dp))
    ) {
        Text(text = text, color = color, fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
    }
}
