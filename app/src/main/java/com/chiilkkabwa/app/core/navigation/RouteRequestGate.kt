package com.chiilkkabwa.app.core.navigation

import com.chiilkkabwa.app.core.safety.GateState
import com.chiilkkabwa.app.core.safety.SafetyGate
import com.chiilkkabwa.app.core.signal.SignalSource
import com.chiilkkabwa.app.core.signal.VehicleSnapshot

object RouteRequestGate {
    /** Same wall clock as Signal.receivedAtMs. The timeout clock is deliberately separate. */
    fun check(s: VehicleSnapshot, nowMs: Long, expectedSource: SignalSource,
              maxAgeMs: Long = 1_500): RouteProblem? {
        require(maxAgeMs >= 0)
        val required = listOf(s.gear, s.parkingBrakeOn, s.uxRestricted)
        if (required.any { !it.isUsable }) return RouteProblem.UNUSABLE_SIGNAL
        if (required.any { it.source != expectedSource }) return RouteProblem.SOURCE_MISMATCH
        if (nowMs < 0 || required.any { it.receivedAtMs < 0 || it.receivedAtMs > nowMs ||
                nowMs - it.receivedAtMs > maxAgeMs }) return RouteProblem.STALE_SIGNAL
        return if (SafetyGate.evaluate(s) == GateState.Ready) null else RouteProblem.NOT_PARKED
    }
}
