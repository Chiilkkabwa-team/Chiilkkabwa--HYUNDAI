package com.chiilkkabwa.app.data.navigation

import ai.pleos.playground.navi.constants.NaviErrorCode
import ai.pleos.playground.navi.constants.NavigationStatus
import ai.pleos.playground.navi.constants.WaypointIndex
import ai.pleos.playground.navi.data.RequestWaypointInfo
import ai.pleos.playground.navi.data.RouteInfo
import ai.pleos.playground.navi.data.RouteStartInfo
import ai.pleos.playground.navi.data.RouteWithWaypointsRequest
import ai.pleos.playground.navi.helper.NaviHelper
import ai.pleos.playground.navi.helper.listener.NaviHelperEventListener
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.os.RemoteException
import com.chiilkkabwa.app.BuildConfig
import com.chiilkkabwa.app.core.navigation.*
import com.chiilkkabwa.app.core.signal.SignalSource

/** No cancelRoute, private API, reflection, location collection or foreground/navigation-start call. */
internal class NaviHelperClient(context: Context) : NaviClient {
    private val context = context.applicationContext
    private val main = Handler(Looper.getMainLooper())
    private var epoch = 0L
    private var helper: NaviHelper? = null
    private var listener: NaviHelperEventListener? = null

    override fun connect(sink: (NaviEvent) -> Unit) {
        check(Looper.myLooper() == Looper.getMainLooper())
        if (!actualRuntime().allowed) throw NaviClientException(RouteProblem.DISABLED)
        check(helper == null)
        val current = ++epoch
        fun deliver(event: NaviEvent) {
            main.post { if (epoch == current && helper != null) sink(event) }
        }
        val sdk = NaviHelper(context)
        helper = sdk
        val events = object : NaviHelperEventListener {
            override fun onRouteStarted(routeStartInfo: RouteStartInfo) = deliver(NaviEvent.SearchStarted)
            override fun onRoutingCompleted() = deliver(NaviEvent.SearchCompleted)
            override fun onNavigationStatus(status: NavigationStatus) = deliver(NaviEvent.NavigationObserved(status.name))
            override fun onError(errorCode: NaviErrorCode) = deliver(NaviEvent.Error(errorCode.name))
        }
        listener = events
        sdk.addListener(events)
        sdk.initialize(
            onConnected = { deliver(NaviEvent.Connected) },
            onFailed = { deliver(NaviEvent.InitializationFailed(classify(it))) },
        )
    }

    override fun request(route: PracticeRouteRequest) {
        check(Looper.myLooper() == Looper.getMainLooper())
        if (!actualRuntime().allowed) throw NaviClientException(RouteProblem.DISABLED)
        if (context.checkSelfPermission(ROUTE_PERMISSION) != PackageManager.PERMISSION_GRANTED)
            throw NaviClientException(RouteProblem.PERMISSION_DENIED)
        if (route.invalidReason() != null) throw NaviClientException(RouteProblem.INVALID_ROUTE)
        val sdk = helper ?: throw NaviClientException(RouteProblem.NOT_CONNECTED)
        try {
            if (route.waypoints.isEmpty()) sdk.requestRoute(NaviSdkMapper.destination(route))
            else sdk.requestRouteWithWaypoints(NaviSdkMapper.withWaypoints(route))
        } catch (e: RemoteException) { throw NaviClientException(classify(e)) }
    }

    override fun close() {
        check(Looper.myLooper() == Looper.getMainLooper())
        ++epoch
        val sdk = helper
        val events = listener
        helper = null
        listener = null
        try { if (sdk != null && events != null) sdk.removeListener(events) }
        finally { sdk?.release() }
    }

    companion object {
        const val ROUTE_PERMISSION = "pleos.car.permission.NAVI_ROUTE_SEARCH"
        fun actualRuntime(): NaviRuntime {
            val emulator = Build.HARDWARE == "ranchu" || Build.HARDWARE == "goldfish"
            return NaviRuntime(BuildConfig.DEBUG, BuildConfig.PLEOS_NAVI_ENABLED,
                if (emulator) SignalSource.EMULATOR else SignalSource.VEHICLE)
        }
        private fun classify(error: Throwable): RouteProblem = when (error) {
            is SecurityException -> RouteProblem.PERMISSION_DENIED
            is UnsupportedOperationException -> RouteProblem.UNSUPPORTED
            is RemoteException -> RouteProblem.DISCONNECTED
            else -> RouteProblem.CONNECTION_FAILED
        }
    }
}

/** Named arguments follow the actual navi-data 3.0.4 constructors shipped with Helper 3.0.5. */
internal object NaviSdkMapper {
    fun destination(route: PracticeRouteRequest): RouteInfo = route.destination.let {
        RouteInfo(latitude = it.latitude, longitude = it.longitude, poiName = it.poiName,
            poiId = it.poiId, address = it.address, poiSubId = it.poiSubId, poiProvider = it.poiProvider)
    }
    fun withWaypoints(route: PracticeRouteRequest) = RouteWithWaypointsRequest(
        destination = destination(route),
        waypoints = route.waypoints.map {
            val p = it.point
            RequestWaypointInfo(latitude = p.latitude, longitude = p.longitude, poiName = p.poiName,
                waypointIndex = when (it.position) {
                    1 -> WaypointIndex.FIRST
                    2 -> WaypointIndex.SECOND
                    else -> throw IllegalArgumentException("Invalid waypoint position")
                }, poiId = p.poiId, address = p.address, poiSubId = p.poiSubId, poiProvider = p.poiProvider)
        },
    )
}

/** Construction is inert: the A03 owner must explicitly start/updateSafety/request and poll tick.
 * Kept independent of B/C UI; no Activity or automatic route request is registered. */
internal fun createDebugNaviAdapter(context: Context): PleosNaviAdapter = PleosNaviAdapter(
    runtime = NaviHelperClient.actualRuntime(),
    clientFactory = { NaviHelperClient(context) },
    wallClockMs = { System.currentTimeMillis() },
    elapsedMs = { android.os.SystemClock.elapsedRealtime() },
)