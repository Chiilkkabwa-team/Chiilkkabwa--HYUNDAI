package com.chiilkkabwa.app.data.navigation

import com.chiilkkabwa.app.core.navigation.*
import com.chiilkkabwa.app.core.signal.*

/** Debug-only owner. No timers, Android UI, injected "good" signals, or automatic initialization. */
internal class NaviProbeOwner(
    private val runtime: NaviRuntime,
    private val adapter: PleosNaviAdapter,
    private val permissionAllowed: () -> Boolean,
    private val wallClockMs: () -> Long,
) {
    private var foreground = false
    private var snapshot = VehicleSnapshot()
    private var lastProgress = adapter.progress
    var event = if (runtime.allowed) "IDLE" else "BLOCKED"; private set
    var reason: String? = if (runtime.allowed) null else "DISABLED"; private set
    var hasAttemptedStart = false; private set
    val progress: NaviProgress get() = adapter.progress
    val mustCloseView: Boolean get() = snapshot.uxRestricted.value == true ||
        (snapshot.gear.isUsable && snapshot.gear.value != Gear.PARK)

    fun onResume() { foreground = true } // Never auto-start after re-entry.
    fun onPause() {
        foreground = false
        adapter.stop()
        snapshot = VehicleSnapshot()
        lastProgress = progress
        event = "RELEASED"
        reason = if (progress.cleanupFailed) "CLEANUP_UNCERTAIN" else null
    }

    fun updateSafety(value: VehicleSnapshot) {
        if (!foreground) return
        snapshot = value
        adapter.updateSafety(value)
        if (hasAttemptedStart && (value.uxRestricted.value == true ||
                !value.uxRestricted.isUsable || !value.gear.isUsable ||
                (value.gear.isUsable && value.gear.value != Gear.PARK))) {
            adapter.stop()
            lastProgress = progress
            block("PLATFORM_RESTRICTED")
        }
        poll()
    }

    fun initializationBlock(): String? {
        if (!runtime.allowed) return "DISABLED"
        if (!foreground) return "NOT_FOREGROUND"
        val now = wallClockMs()
        val signals = listOf(snapshot.gear, snapshot.uxRestricted)
        if (signals.any { !it.isUsable }) return "UNUSABLE_SIGNAL"
        if (signals.any { it.source != SignalSource.EMULATOR }) return "SOURCE_MISMATCH"
        if (now < 0 || signals.any { it.receivedAtMs < 0 || it.receivedAtMs > now ||
                now - it.receivedAtMs > 1500 }) return "STALE_SIGNAL"
        if (snapshot.gear.value != Gear.PARK || snapshot.uxRestricted.value != false) return "NOT_PARKED"
        return null
    }

    fun begin(explicit: Boolean): Boolean {
        if (!explicit) return block("NO_USER_REQUEST")
        initializationBlock()?.let { return block(it) }
        hasAttemptedStart = true
        val started = adapter.start()
        if (!started) return block(adapter.lastRejection?.name ?: progress.problem?.name ?: "START_FAILED")
        reason = null
        refresh()
        return true
    }

    fun submit(route: PracticeRouteRequest?, explicit: Boolean): Boolean {
        if (!runtime.allowed) return block("DISABLED")
        if (!foreground) return block("NOT_FOREGROUND")
        if (!explicit) return block("NO_USER_REQUEST")
        RouteRequestGate.check(snapshot, wallClockMs(), runtime.source)?.let { return block(it.name) }
        if (!permissionAllowed()) return block("PERMISSION_DENIED")
        if (route == null || route.invalidReason() != null) return block("INVALID_ROUTE")
        // Synthetic POIs are for JVM tests only, never a real SDK request.
        if (route.dataSource != RouteDataSource.USER_SELECTED) return block("USER_POI_REQUIRED")
        adapter.updateSafety(snapshot)
        if (!adapter.request(route, true)) return block(adapter.lastRejection?.name ?: progress.problem?.name ?: "REQUEST_FAILED")
        reason = null
        refresh()
        return true
    }

    fun poll() {
        if (!foreground) return
        adapter.tick()
        if (progress != lastProgress) refresh()
    }

    private fun refresh() {
        lastProgress = progress
        reason = progress.problem?.name
        event = when (progress.phase) {
            NaviPhase.INITIALIZING -> "INITIALIZING"
            NaviPhase.STOPPED -> "RELEASED"
            NaviPhase.BLOCKED -> "BLOCKED"
            NaviPhase.FAILED -> if (progress.problem in setOf(RouteProblem.INIT_TIMEOUT, RouteProblem.RESPONSE_TIMEOUT))
                "TIMEOUT" else "FAILED"
            else -> when {
                progress.navigationStatus != null -> "NAVIGATION_STATUS_OBSERVED"
                progress.searchCompleted -> "SEARCH_COMPLETED"
                progress.searchStarted -> "SEARCH_STARTED"
                progress.callReturned -> "REQUEST_CALLED"
                progress.initialized -> "CONNECTED"
                else -> "IDLE"
            }
        }
    }

    private fun block(why: String): Boolean { event = "BLOCKED"; reason = why; return false }
}
