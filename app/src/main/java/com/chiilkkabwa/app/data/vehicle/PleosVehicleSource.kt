package com.chiilkkabwa.app.data.vehicle

import android.content.Context
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.util.Log
import com.chiilkkabwa.app.BuildConfig

/**
 * A01 읽기 전용 Seat/Brake/TurnSignal. 실수신 검증 전 기본 비활성화.
 * -PenablePleosSignals=true 디버그 빌드 + EMULATOR에서만 시험. 실차는 항상 차단.
 * AAOS 주차 조건·UX 제한·페달 압력 필드는 덮어쓰지 않는다.
 */
class PleosVehicleSource(context: Context) : VehicleSource {
    private val handler = Handler(Looper.getMainLooper())
    private val label = VehicleRepository.realSourceLabel()
    private val enabled = allowPleosSignals(BuildConfig.DEBUG, BuildConfig.PLEOS_SIGNALS_ENABLED, label)
    private val session = PleosSignalSession(
        {
            try { PleosSdkReadClient(context.applicationContext) }
            catch (e: Exception) { Log.e("PleosVehicleSource", "SDK client creation failed", e); throw e }
        }, label,
        enabled, elapsed = SystemClock::elapsedRealtime,
    )
    private var running = false
    private val heartbeat = object : Runnable {
        override fun run() {
            if (!running) return
            session.tick()
            handler.postDelayed(this, 1_000L)
        }
    }
    override fun start(sink: SnapshotSink) {
        stop()
        if (enabled) Log.d("PleosVehicleSource", "SDK session start source=$label")
        session.start(sink)
        running = enabled
        if (running) handler.postDelayed(heartbeat, 1_000L)
    }
    override fun stop() {
        running = false
        handler.removeCallbacks(heartbeat)
        session.stop()
    }
}
