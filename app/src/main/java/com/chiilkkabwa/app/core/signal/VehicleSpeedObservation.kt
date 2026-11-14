package com.chiilkkabwa.app.core.signal

/** A02 only: signed m/s and elapsedRealtime nanoseconds. Never uses Signal's epoch clock. */
data class VehicleSpeedObservation(
    val rawMps: Float? = null,
    val timestampNanos: Long? = null,
    val receivedAtNanos: Long = 0,
    val source: SignalSource = SignalSource.MOCK,
    val status: SignalStatus = SignalStatus.UNKNOWN,
    val sessionId: String = "",
) {
    val kilometersPerHour: Double? get() = rawMps?.takeIf { status == SignalStatus.OK && it.isFinite() }?.toDouble()?.times(3.6)
    fun disconnected() = copy(rawMps = null, timestampNanos = null, status = SignalStatus.DISCONNECTED)
}

