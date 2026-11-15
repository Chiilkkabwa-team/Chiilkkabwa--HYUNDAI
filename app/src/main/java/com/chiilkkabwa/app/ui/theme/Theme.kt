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
    // 웹 styles.css 다크 테마와 같은 값(docs/04 §0-2)
    val Surface3 = Color(0xFF2A3544)
    val Line = Color(0xFF2A3544)
    val Muted = Color(0xFF93A1B3)
    val PrimaryText = Color(0xFFA9CBFA)
    val Yellow = Color(0xFFFFD66B)
    val OnAccent = Color(0xFF1B2430)
    val Danger = Color(0xFFD9483F)
    val DangerText = Color(0xFFFFD3D3)
    val SuccessBg = Color(0xFF173A28)
    val WarnBg = Color(0xFF3A2F12)
    val TouchMin = 76.dp        // 큰 터치 영역(차량 화면)
    val Display = 40.sp
    val Title = 30.sp
    val Heading = 25.sp
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
