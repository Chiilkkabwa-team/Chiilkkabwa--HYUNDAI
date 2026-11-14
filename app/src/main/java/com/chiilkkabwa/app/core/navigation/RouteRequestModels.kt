package com.chiilkkabwa.app.core.navigation

import com.chiilkkabwa.app.core.signal.SignalSource

/** Input coordinates, never a collected route trace. POI validity still needs the provider. */
data class RoutePoint(val latitude: Double, val longitude: Double, val poiName: String,
    val poiId: String, val address: String, val poiSubId: String, val poiProvider: String = "")
data class RouteWaypoint(val position: Int, val point: RoutePoint)
enum class RouteDataSource { USER_SELECTED, SYNTHETIC_TEST }
enum class RouteInputError { REQUEST_ID, COORDINATES, REQUIRED_FIELD, WAYPOINT_COUNT, WAYPOINT_ORDER, DUPLICATE_POINT }

data class PracticeRouteRequest(
    val requestId: String,
    val destination: RoutePoint,
    val waypoints: List<RouteWaypoint> = emptyList(),
    val dataSource: RouteDataSource,
) {
    fun invalidReason(): RouteInputError? {
        if (requestId.isBlank() || requestId.length > 128) return RouteInputError.REQUEST_ID
        if (waypoints.size > 2) return RouteInputError.WAYPOINT_COUNT
        if (waypoints.map { it.position } != (1..waypoints.size).toList()) return RouteInputError.WAYPOINT_ORDER
        val points = listOf(destination) + waypoints.map { it.point }
        if (points.any { !it.latitude.isFinite() || !it.longitude.isFinite() ||
                it.latitude !in -90.0..90.0 || it.longitude !in -180.0..180.0 }) return RouteInputError.COORDINATES
        if (points.any { it.poiName.isBlank() || it.poiId.isBlank() || it.address.isBlank() || it.poiSubId.isBlank() })
            return RouteInputError.REQUIRED_FIELD
        if (points.map { (if (it.latitude == 0.0) 0.0 else it.latitude) to
                (if (it.longitude == 0.0) 0.0 else it.longitude) }.distinct().size != points.size ||
            points.map { Triple(it.poiProvider, it.poiId, it.poiSubId) }.distinct().size != points.size)
            return RouteInputError.DUPLICATE_POINT
        return null
    }
}

/** Defense in depth: the Android SDK client independently checks the actual build/device too. */
data class NaviRuntime(val debugBuild: Boolean = false, val enabled: Boolean = false,
                       val source: SignalSource = SignalSource.VEHICLE) {
    val allowed: Boolean get() = debugBuild && enabled && source == SignalSource.EMULATOR
}
enum class NaviPhase { STOPPED, INITIALIZING, READY, REQUESTING, OBSERVING, BLOCKED, FAILED }
enum class RouteProblem {
    DISABLED, NO_USER_REQUEST, NOT_CONNECTED, BUSY, DUPLICATE_REQUEST, CAPACITY_REACHED,
    INVALID_ROUTE, UNUSABLE_SIGNAL, STALE_SIGNAL, SOURCE_MISMATCH, NOT_PARKED,
    PERMISSION_DENIED, UNSUPPORTED, CONNECTION_FAILED, DISCONNECTED, SDK_ERROR,
    INIT_TIMEOUT, RESPONSE_TIMEOUT, CLOCK_REVERSED, CLEANUP_UNCERTAIN,
}

/** These are observations, not proof that a particular requested route is guiding the user. */
data class NaviProgress(
    val phase: NaviPhase = NaviPhase.STOPPED,
    val initialized: Boolean = false,
    val requestId: String? = null,
    val callReturned: Boolean = false,
    val searchStarted: Boolean = false,
    val searchCompleted: Boolean = false,
    val navigationStatus: String? = null,
    val problem: RouteProblem? = null,
    val errorCode: String? = null,
    val cleanupFailed: Boolean = false,
) {
    // Global SDK events have no per-request ID; only future external evidence can establish these.
    val callbackCorrelationVerified: Boolean get() = false
    val guidanceConfirmed: Boolean get() = false
}
