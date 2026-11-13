package com.chiilkkabwa.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import com.chiilkkabwa.app.data.vehicle.AaosVehicleSource
import com.chiilkkabwa.app.core.voice.ParkedVoicePreview
import com.chiilkkabwa.app.core.voice.PreviewResult
import com.chiilkkabwa.app.core.safety.GateState
import com.chiilkkabwa.app.core.session.Phase
import com.chiilkkabwa.app.data.voice.AndroidPreviewAudio
import com.chiilkkabwa.app.ui.ChiilViewModel
import com.chiilkkabwa.app.ui.UiState
import com.chiilkkabwa.app.ui.screens.AppRoot
import com.chiilkkabwa.app.ui.theme.ChiilTheme
import kotlinx.coroutines.launch

/**
 * 단일 Activity. DO(distractionOptimized)를 선언하지 않는다 → 주행 중에는 플랫폼이 앱 사용을 막고,
 * 앱도 CarUxRestrictions로 스스로 잠근다(이중 안전).
 */
class MainActivity : ComponentActivity() {
    private val vm: ChiilViewModel by viewModels()
    private var previewAudio: AndroidPreviewAudio? = null
    private var preview: ParkedVoicePreview? = null
    private val previewResult = mutableStateOf(PreviewResult.IDLE)

    private val pedalPermission = registerForActivityResult(ActivityResultContracts.RequestPermission()) { /* 결과는 AaosVehicleSource가 재시작 시 확인 */ }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        if (!BuildConfig.USE_MOCK) pedalPermission.launch(AaosVehicleSource.PEDAL_PERMISSION) // 페달 미션(조건부)용 위험 권한
        if (BuildConfig.DEBUG && BuildConfig.USE_MOCK) {
            val audio = AndroidPreviewAudio(this)
            previewAudio = audio
            preview = ParkedVoicePreview(audio) { previewResult.value = it }
            lifecycleScope.launch {
                repeatOnLifecycle(Lifecycle.State.RESUMED) {
                    vm.ui.collect { if (!canPreview(it)) preview?.cancel() }
                }
            }
        }
        setContent {
            ChiilTheme {
                val ui by vm.ui.collectAsState()
                val voicePreview = preview?.let { controller ->
                    {
                        controller.play(
                            lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED) && canPreview(vm.ui.value),
                        )
                    }
                }
                AppRoot(vm, ui, voicePreview, previewResult.value.message())
            }
        }
    }

    override fun onResume() {
        super.onResume()
        vm.startSignals()
    }

    override fun onPause() {
        preview?.cancel()
        vm.stopSignals()
        super.onPause()
    }

    override fun onDestroy() {
        previewAudio?.close()
        super.onDestroy()
    }

    private fun canPreview(ui: UiState): Boolean =
        ui.mockMode && ui.session.phase == Phase.READY && ui.gate is GateState.Ready

    private fun PreviewResult.message(): String? = when (this) {
        PreviewResult.IDLE -> null
        PreviewResult.PLAYING -> "음성 안내를 재생 중이에요"
        PreviewResult.SPOKEN -> "음성 안내가 끝났어요"
        PreviewResult.BLOCKED -> "주차 상태나 오디오 사용 가능 여부를 확인해 주세요"
        PreviewResult.UNAVAILABLE -> "기기의 오프라인 한국어 음성을 확인해 주세요"
        PreviewResult.INTERRUPTED -> "음성 안내를 중단했어요"
    }
}
