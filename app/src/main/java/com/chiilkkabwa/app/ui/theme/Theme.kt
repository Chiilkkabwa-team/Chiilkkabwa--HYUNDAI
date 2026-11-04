package com.chiilkkabwa.app.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/** 디자인 토큰 — docs/04_UI_SPEC.md '디자인 토큰'과 같은 값 */
object Tokens {
    val Bg = Color(0xFF0F141B)
    val Surface = Color(0xFF1B2430)
    val Surface2 = Color(0xFF222C39)
    val Primary = Color(0xFF2F80ED)
    val Success = Color(0xFF27AE60)
    val SuccessText = Color(0xFF7EE2A8)
    val Warn = Color(0xFF6B4F12)
    val Lock = Color(0xFF6E2A2A)
    val TextMain = Color(0xFFE9EEF5)
    val TextSub = Color(0xFFB8C4D2)
    val TouchMin = 76.dp        // 큰 터치 영역(차량 화면)
    val Title = 30.sp
    val Body = 22.sp
    val Caption = 17.sp
}

@Composable
fun ChiilTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = darkColorScheme(
            primary = Tokens.Primary, background = Tokens.Bg, surface = Tokens.Surface,
            onBackground = Tokens.TextMain, onSurface = Tokens.TextMain,
        ),
        content = content,
    )
}
