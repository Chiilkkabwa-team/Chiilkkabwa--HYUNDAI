package com.chiilkkabwa.app.data.navigation

import com.chiilkkabwa.app.core.navigation.*
import com.chiilkkabwa.app.core.signal.VehicleSnapshot

/**
 * No automatic requests, navigation cancellation, UI, IO or scheduling.
 * Single context (Android main thread). Owner polls tick and supplies fresh safety on every update.
 * One request per connection; explicit stop/start creates a new callback generation, never a retry.
 */
class PleosNaviAdapter(
    private val runtime: NaviRuntime,
    private val clientFactory: () -> NaviClient,
    private val wallClockMs: () -> Long,
    private val elapsedMs: () -> Long,
    private val initTimeoutMs: Long = 15_000,
    private val responseTimeoutMs: Long = 15_000,
) {
    init { require(initTimeoutMs > 0 && responseTimeoutMs > 0) }
    var progress = NaviProgress()
        private set
    var lastRejection: RouteProblem? = null
        private set
    private var client: NaviClient? = null
    private var generation = 0L
    private var safety = VehicleSnapshot()
    private var lastElapsed: Long? = null
    private var connectedAt: Long? = null
    private var requestedAt: Long? = null
    private val consumedIds = mutableSetOf<String>()

    fun start(): Boolean {
        if (!runtime.allowed) return deny(RouteProblem.DISABLED)
        if (progress.cleanupFailed) return deny(RouteProblem.CLEANUP_UNCERTAIN)
        if (client != null) return deny(RouteProblem.BUSY)
        val now = elapsedMs()
        if (now < 0 || lastElapsed?.let { now < it } == true) return deny(RouteProblem.CLOCK_REVERSED)
        lastElapsed = now
        safety = VehicleSnapshot() // Never carry a pre-stop safety snapshot into a new connection.
        requestedAt = null
        connectedAt = now
        progress = NaviProgress(phase = NaviPhase.INITIALIZING)
        val epoch = ++generation
        try {
            val created = clientFactory()
            client = created
            created.connect { receive(epoch, it) }
        } catch (e: Exception) {
            terminate(NaviPhase.FAILED, failure(e))
            return false
        }
        return progress.phase != NaviPhase.FAILED
    }

    fun updateSafety(snapshot: VehicleSnapshot) {
        safety = snapshot
        tick()
    }

    /** True means the Unit-returning function returned, NOT search/route guidance success. */
    fun request(route: PracticeRouteRequest, userInitiated: Boolean): Boolean {
        tick()
        if (!runtime.allowed) return deny(RouteProblem.DISABLED)
        if (!userInitiated) return deny(RouteProblem.NO_USER_REQUEST)
        val current = client
        if (current == null || !progress.initialized) return deny(RouteProblem.NOT_CONNECTED)
        if (requestedAt != null) return deny(RouteProblem.BUSY)
        if (route.requestId in consumedIds) return deny(RouteProblem.DUPLICATE_REQUEST)
        if (consumedIds.size >= 256) return deny(RouteProblem.CAPACITY_REACHED)
        val frozen = route.copy(waypoints = route.waypoints.toList())
        if (frozen.invalidReason() != null) return deny(RouteProblem.INVALID_ROUTE)
        RouteRequestGate.check(safety, wallClockMs(), runtime.source)?.let { return deny(it) }
        consumedIds += frozen.requestId
        requestedAt = lastElapsed
        progress = progress.copy(phase = NaviPhase.REQUESTING, requestId = frozen.requestId, problem = null)
        lastRejection = null
        val epoch = generation
        try {
            current.request(frozen)
        } catch (e: Exception) {
            terminate(NaviPhase.FAILED, failure(e))
            return false
        }
        if (epoch != generation) return false
        progress = progress.copy(callReturned = true)
        return true
    }

    /** Must be polled by a future owner; timeouts do not preempt a synchronously blocking SDK call. */
    fun tick() {
        if (client == null) return
        val now = elapsedMs()
        if (now < 0 || lastElapsed?.let { now < it } == true) {
            terminate(NaviPhase.FAILED, RouteProblem.CLOCK_REVERSED)
            return
        }
        lastElapsed = now
        if (!progress.initialized && connectedAt?.let { now - it >= initTimeoutMs } == true) {
            terminate(NaviPhase.FAILED, RouteProblem.INIT_TIMEOUT)
            return
        }
        val requested = requestedAt ?: return
        RouteRequestGate.check(safety, wallClockMs(), runtime.source)?.let {
            terminate(NaviPhase.BLOCKED, it)
            return
        }
        if (!progress.searchCompleted && now - requested >= responseTimeoutMs)
            terminate(NaviPhase.FAILED, RouteProblem.RESPONSE_TIMEOUT)
    }

    fun stop() { terminate(NaviPhase.STOPPED, progress.problem) }

    private fun receive(epoch: Long, event: NaviEvent) {
        if (epoch != generation || client == null) return
        tick()
        if (epoch != generation || client == null) return
        when (event) {
            NaviEvent.Connected -> if (progress.phase == NaviPhase.INITIALIZING) {
                progress = progress.copy(phase = NaviPhase.READY, initialized = true, problem = null)
            }
            is NaviEvent.InitializationFailed -> if (!progress.initialized)
                terminate(NaviPhase.FAILED, event.problem)
            else -> {
                if (requestedAt == null) return // Do not attribute an existing navigation event to a new request.
                progress = when (event) {
                    NaviEvent.SearchStarted -> progress.copy(searchStarted = true)
                    NaviEvent.SearchCompleted -> progress.copy(phase = NaviPhase.OBSERVING, searchCompleted = true)
                    is NaviEvent.NavigationObserved -> progress.copy(navigationStatus = event.status)
                    is NaviEvent.Error -> {
                        terminate(NaviPhase.FAILED, RouteProblem.SDK_ERROR, event.code)
                        return
                    }
                    else -> return
                }
            }
        }
    }

    private fun deny(problem: RouteProblem): Boolean {
        lastRejection = problem
        // A rejected duplicate must not change the in-flight request's observation.
        if (requestedAt == null) progress = progress.copy(problem = problem)
        return false
    }

    private fun terminate(phase: NaviPhase, problem: RouteProblem?, errorCode: String? = progress.errorCode) {
        ++generation // Invalidate callbacks BEFORE removing listener/releasing SDK.
        val closing = client
        client = null
        connectedAt = null
        requestedAt = null
        var cleanupFailed = progress.cleanupFailed
        try { closing?.close() } catch (_: Exception) { cleanupFailed = true }
        progress = progress.copy(phase = phase, problem = problem, errorCode = errorCode, cleanupFailed = cleanupFailed)
    }

    private fun failure(e: Exception): RouteProblem = when (e) {
        is NaviClientException -> e.problem
        is SecurityException -> RouteProblem.PERMISSION_DENIED
        is UnsupportedOperationException -> RouteProblem.UNSUPPORTED
        else -> RouteProblem.SDK_ERROR
    }
}
