package com.chiilkkabwa.app

import android.os.Bundle
import android.car.Car
import com.chiilkkabwa.app.data.vehicle.AaosSpeedSource
import android.content.pm.PackageManager
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
import com.chiilkkabwa.app.core.voice.CoachingOffer
import com.chiilkkabwa.app.core.voice.ParkedVoicePreview
import com.chiilkkabwa.app.core.voice.PlaybackOutcome
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
    private var speaking: CoachingOffer? = null

    private val pedalPermission = registerForActivityResult(ActivityResultContracts.RequestPermission()) { /* 결과는 AaosVehicleSource가 재시작 시 확인 */ }

    private val speedPermission = registerForActivityResult(ActivityResultContracts.RequestPermission()) {
        vm.stopSignals()
        if (lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED)) vm.startSignals()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        if (!BuildConfig.USE_MOCK && AaosSpeedSource.isTestEnabled() && hasPermissionDefinition(Car.PERMISSION_SPEED) &&
            checkSelfPermission(Car.PERMISSION_SPEED) != PackageManager.PERMISSION_GRANTED) speedPermission.launch(Car.PERMISSION_SPEED)
        if (!BuildConfig.USE_MOCK && hasPedalPermissionDefinition()) {
            pedalPermission.launch(AaosVehicleSource.PEDAL_PERMISSION)
        }
        if (BuildConfig.USE_MOCK) {
            val audio = AndroidPreviewAudio(this)
            previewAudio = audio
            if (BuildConfig.DEBUG) {
                preview = ParkedVoicePreview(audio) { previewResult.value = it }
                lifecycleScope.launch {
                    repeatOnLifecycle(Lifecycle.State.RESUMED) {
                        vm.ui.collect { if (!canPreview(it)) preview?.cancel() }
                    }
                }
            }
            // 모의 주행 음성 길잡이(mock 플레이버 전용, S05: 실제 주행 중 음성은 에뮬레이터 검증 전이라 vehicle에서는 켜지 않는다)
            lifecycleScope.launch {
                repeatOnLifecycle(Lifecycle.State.RESUMED) {
                    vm.coachOffer.collect { o -> if (o == null) stopCoach() else speak(audio, o) }
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

    /** API 34 Pleos에는 이 선택 권한 자체가 없을 수 있다. 미지원 권한 창을 열지 않는다. */
    private fun hasPedalPermissionDefinition(): Boolean = hasPermissionDefinition(AaosVehicleSource.PEDAL_PERMISSION)

    private fun hasPermissionDefinition(permission: String): Boolean = try {
        packageManager.getPermissionInfo(permission, 0)
        true
    } catch (_: PackageManager.NameNotFoundException) {
        false
    }

    override fun onResume() {
        super.onResume()
        vm.startSignals()
    }

    override fun onPause() {
        preview?.cancel()
        // 앱이 가려지면 그 발화는 버린다(나중에 다시 말하지 않는다)
        speaking?.let { stopCoach(); vm.coachDone(it, PlaybackOutcome.APP_STOPPED) }
        vm.stopSignals()
        super.onPause()
    }

    override fun onDestroy() {
        previewAudio?.close()
        super.onDestroy()
    }

    /** 포커스를 얻은 뒤에만 말하고, 포커스를 잃으면 멈추고 그 말은 버린다(내비·통화에 양보). */
    private fun speak(audio: AndroidPreviewAudio, offer: CoachingOffer) {
        if (speaking === offer) return
        speaking = offer
        val done = { r: PlaybackOutcome -> if (speaking === offer) { speaking = null; audio.abandonFocus(); vm.coachDone(offer, r) } }
        when {
            !audio.ready -> done(PlaybackOutcome.TTS_FAILED)
            !audio.acquireFocus { audio.stop(); done(PlaybackOutcome.FOCUS_LOST) } -> done(PlaybackOutcome.FOCUS_DENIED)
            !audio.play(offer.line) { ok -> done(if (ok) PlaybackOutcome.COMPLETED else PlaybackOutcome.TTS_FAILED) } ->
                done(PlaybackOutcome.TTS_FAILED)
        }
    }

    private fun stopCoach() {
        if (speaking == null) return
        speaking = null
        previewAudio?.stop()
        previewAudio?.abandonFocus()
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
