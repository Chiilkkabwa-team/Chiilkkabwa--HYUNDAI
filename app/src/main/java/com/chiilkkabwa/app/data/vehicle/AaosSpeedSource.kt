package com.chiilkkabwa.app.data.vehicle

import android.content.Context
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.util.Log
import com.chiilkkabwa.app.BuildConfig
import com.chiilkkabwa.app.core.event.SpeedDetectionResult
import com.chiilkkabwa.app.core.signal.SignalSource
import java.util.UUID

internal fun allowAaosSpeed(debug: Boolean, optIn: Boolean, source: SignalSource, hardware: String): Boolean =
    debug && optIn && source == SignalSource.EMULATOR && hardware in setOf("ranchu", "goldfish")

/** Independent, default-disabled foreground experiment; no driving service or event persistence. */
class AaosSpeedSource(context: Context, onResult: (SpeedDetectionResult) -> Unit = {}) : VehicleSource {
    private val enabled = isTestEnabled()
    private val handler = Handler(Looper.getMainLooper())
    private val session = SpeedSignalSession(
        { AaosSpeedReadClient(context.applicationContext) }, VehicleRepository.realSourceLabel(), enabled,
        SystemClock::elapsedRealtimeNanos, { UUID.randomUUID().toString() },
    ) { result ->
        if (enabled) Log.d(TAG, when (result) {
            is SpeedDetectionResult.Baseline -> "consumed baseline reason=${result.reason} mps=${result.sample.speed} sampleMs=${result.sample.timestampMs}"
            is SpeedDetectionResult.Rejected -> "consumed rejected reason=${result.reason}"
            is SpeedDetectionResult.Evaluated -> "consumed evaluated mps=${result.evidence.currentMps} sampleMs=${result.evidence.current.timestampMs} event=${result.event?.type} suppression=${result.suppression}"
        })
        onResult(result)
    }
    private val heartbeat = object : Runnable {
        override fun run() { session.tick(); if (session.needsTick) handler.postDelayed(this, 200) }
    }
    override fun start(sink: SnapshotSink) {
        stop()
        if (enabled) Log.d(TAG, "lifecycle source_start elapsedNs=${SystemClock.elapsedRealtimeNanos()}")
        session.start { reading ->
            sink.update { it.copy(speed = reading) }
            if (enabled) Log.d(TAG, "observation source=${reading.source} status=${reading.status} rawMps=${reading.rawMps} propertyNs=${reading.timestampNanos} receiptNs=${reading.receivedAtNanos} session=${reading.sessionId}")
        }
        if (session.needsTick) handler.postDelayed(heartbeat, 200)
    }
    override fun stop() {
        handler.removeCallbacks(heartbeat)
        session.stop()
        if (enabled) Log.d(TAG, "lifecycle source_stop_returned elapsedNs=${SystemClock.elapsedRealtimeNanos()}")
    }
    companion object {
        private const val TAG = "A02SpeedTrace"
        fun isTestEnabled(): Boolean = allowAaosSpeed(BuildConfig.DEBUG, BuildConfig.AAOS_SPEED_ENABLED,
            VehicleRepository.realSourceLabel(), Build.HARDWARE)
    }
}
