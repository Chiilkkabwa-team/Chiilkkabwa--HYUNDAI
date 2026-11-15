package com.chiilkkabwa.app.ui.screens

import androidx.compose.runtime.Composable
import androidx.compose.ui.tooling.preview.Preview
import com.chiilkkabwa.app.core.session.Phase
import com.chiilkkabwa.app.core.signal.SignalSource
import com.chiilkkabwa.app.ui.UiState
import com.chiilkkabwa.app.ui.theme.ChiilTheme

/**
 * Android Studio 'Split/Design' 화면에서 에뮬레이터 없이 차량 화면 비율로 미리 보기.
 * 크기는 **가정값**이다(공식 Pleos 해상도는 에뮬레이터 `adb shell wm size`로 확인해 바꾼다):
 * 넓은 1창(가로로 길고 세로 짧음)과 2창 분할(앱 창이 절반쯤).
 */
@Composable
private fun CarFrame(phase: Phase = Phase.READY, content: @Composable () -> Unit) = ChiilTheme {
    ScreenFrame(phase, SignalSource.MOCK, onOpen = {}, content = content)   // 앱과 같은 틀(배율·머리줄)
}

@Preview(name = "Pleos 에뮬레이터 앱 창(영상 추정 1680×1200dp)", widthDp = 1680, heightDp = 1200)
@Preview(name = "차량 넓은 1창(가정 1600×600dp)", widthDp = 1600, heightDp = 600)
@Preview(name = "차량 2창 분할(가정 800×600dp)", widthDp = 800, heightDp = 600)
@Composable
private fun GatePreview() = CarFrame { GateScreen(UiState(), onStart = {}) }

@Preview(name = "주행 잠금 · 넓은 1창(가정)", widthDp = 1600, heightDp = 600)
@Composable
private fun LockPreview() = CarFrame(Phase.DRIVING_LOCK) { DrivingLockScreen() }

@Preview(name = "복기 · 2창 분할(가정)", widthDp = 800, heightDp = 600)
@Composable
private fun ReviewPreview() = CarFrame(Phase.REVIEW) { ReviewScreen(UiState(), onSave = { _, _, _, _ -> }) }
