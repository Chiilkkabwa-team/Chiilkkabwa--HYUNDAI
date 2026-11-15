package com.chiilkkabwa.app.data.navigation

import com.chiilkkabwa.app.core.navigation.*
import com.chiilkkabwa.app.core.signal.*
import org.junit.Assert.*
import org.junit.Test

class NaviProbeOwnerTest {
    private class Fake : NaviClient {
        lateinit var sink: (NaviEvent) -> Unit
        var connects = 0; var requests = 0; var closes = 0
        override fun connect(sink: (NaviEvent) -> Unit) { connects++; this.sink = sink }
        override fun request(route: PracticeRouteRequest) { requests++ }
        override fun close() { closes++ }
    }
    private var wall = 1000L
    private var elapsed = 0L
    private var permission = true
    private val fakes = mutableListOf<Fake>()
    private fun owner(runtime: NaviRuntime = NaviRuntime(true, true, SignalSource.EMULATOR)) =
        NaviProbeOwner(runtime, PleosNaviAdapter(runtime,
            { Fake().also { fakes += it } }, { wall }, { elapsed }), { permission }, { wall })
    private fun snapshot(brake: Boolean = true) = VehicleSnapshot(
        gear = Signal.ok(Gear.PARK, SignalSource.EMULATOR, wall),
        parkingBrakeOn = Signal.ok(brake, SignalSource.EMULATOR, wall),
        uxRestricted = Signal.ok(false, SignalSource.EMULATOR, wall))
    private fun route() = PracticeRouteRequest("probe-1",
        RoutePoint(37.0, 127.0, "시험 POI", "USER_SUPPLIED_POI", "시험 주소", "0"),
        dataSource = RouteDataSource.USER_SELECTED)

    @Test fun constructAndResumeNeverInitialize() {
        val o = owner(); o.onResume(); o.updateSafety(snapshot()); o.poll()
        assertTrue(fakes.isEmpty()); assertEquals("IDLE", o.event)
    }
    @Test fun defaultOffRealVehicleAndReleaseAreBlocked() {
        for (r in listOf(NaviRuntime(), NaviRuntime(false, true, SignalSource.EMULATOR),
                NaviRuntime(true, true, SignalSource.VEHICLE))) {
            val o = owner(r); o.onResume(); o.updateSafety(snapshot())
            assertFalse(o.begin(true))
        }
        assertTrue(fakes.isEmpty())
    }
    @Test fun explicitStartRequiredButBrakeFalseDoesNotBlockInitialization() {
        val o = owner(); o.onResume(); o.updateSafety(snapshot(false))
        assertFalse(o.begin(false)); assertTrue(fakes.isEmpty())
        assertTrue(o.begin(true)); assertEquals("INITIALIZING", o.event)
        fakes.last().sink(NaviEvent.Connected); o.poll(); assertEquals("CONNECTED", o.event)
        assertFalse(o.submit(route(), true)); assertEquals("NOT_PARKED", o.reason)
        assertEquals(0, fakes.last().requests)
    }
    @Test fun initializationRequiresValidFreshParkAndUx() {
        for (bad in listOf(VehicleSnapshot(),
                snapshot().copy(uxRestricted = Signal.ok(true, SignalSource.EMULATOR, wall)),
                snapshot().copy(gear = Signal.ok(Gear.DRIVE, SignalSource.EMULATOR, wall)))) {
            val o = owner(); o.onResume(); o.updateSafety(bad); assertFalse(o.begin(true))
        }
        val stale = owner(); stale.onResume(); stale.updateSafety(snapshot()); wall += 1501
        assertFalse(stale.begin(true)); assertTrue(fakes.isEmpty())
    }
    @Test fun unknownStaleAndDisconnectedSafetyCannotRequest() {
        for (status in listOf(SignalStatus.UNKNOWN, SignalStatus.STALE, SignalStatus.DISCONNECTED)) {
            val o = owner(); o.onResume(); o.updateSafety(snapshot()); o.begin(true)
            fakes.last().sink(NaviEvent.Connected)
            o.updateSafety(snapshot().copy(parkingBrakeOn = Signal.failed(status, SignalSource.EMULATOR, wall)))
            assertFalse(o.submit(route(), true)); assertEquals(0, fakes.last().requests)
        }
    }
    @Test fun permissionAndPoiAreCheckedBeforeAnyRequest() {
        val o = owner(); o.onResume(); o.updateSafety(snapshot()); o.begin(true)
        fakes.last().sink(NaviEvent.Connected)
        permission = false; assertFalse(o.submit(route(), true)); assertEquals("PERMISSION_DENIED", o.reason)
        permission = true; assertFalse(o.submit(null, true)); assertEquals("INVALID_ROUTE", o.reason)
        val synthetic = route().copy(dataSource = RouteDataSource.SYNTHETIC_TEST)
        assertFalse(o.submit(synthetic, true)); assertEquals("USER_POI_REQUIRED", o.reason)
        assertEquals(0, fakes.last().requests)
    }
    @Test fun successfulUnitReturnIsNeverSearchOrGuidanceSuccess() {
        val o = owner(); o.onResume(); o.updateSafety(snapshot()); o.begin(true)
        fakes.last().sink(NaviEvent.Connected)
        assertTrue(o.submit(route(), true)); assertEquals("REQUEST_CALLED", o.event)
        assertFalse(o.progress.searchCompleted); assertFalse(o.progress.guidanceConfirmed)
        fakes.last().sink(NaviEvent.SearchStarted); o.poll(); assertEquals("SEARCH_STARTED", o.event)
        fakes.last().sink(NaviEvent.SearchCompleted); o.poll(); assertEquals("SEARCH_COMPLETED", o.event)
        fakes.last().sink(NaviEvent.NavigationObserved("ROUTE_DRIVE")); o.poll()
        assertEquals("NAVIGATION_STATUS_OBSERVED", o.event); assertFalse(o.progress.guidanceConfirmed)
    }
    @Test fun pauseAndResumeDoNotAutomaticallyReconnectAndOldCallbackIsIgnored() {
        val o = owner(); o.onResume(); o.updateSafety(snapshot()); o.begin(true)
        val old = fakes.last(); o.onPause(); assertEquals(1, old.closes)
        assertEquals("RELEASED", o.event); o.onResume(); old.sink(NaviEvent.Connected)
        o.poll(); assertEquals(1, fakes.size); assertFalse(o.progress.initialized)
        o.updateSafety(snapshot()); assertTrue(o.begin(true))
        old.sink(NaviEvent.SearchCompleted); assertFalse(o.progress.searchCompleted)
    }
    @Test fun initFailureAndTimeoutRemainSeparateFromSuccess() {
        val o = owner(); o.onResume(); o.updateSafety(snapshot()); o.begin(true)
        fakes.last().sink(NaviEvent.InitializationFailed(RouteProblem.UNSUPPORTED)); o.poll()
        assertEquals("FAILED", o.event); assertEquals("UNSUPPORTED", o.reason)
        val t = owner(); t.onResume(); t.updateSafety(snapshot()); t.begin(true)
        elapsed = 15000; t.poll(); assertEquals("TIMEOUT", t.event)
    }
    @Test fun foregroundLossAndUxRestrictionStopEvenBeforeRoute() {
        val o = owner(); o.onResume(); o.updateSafety(snapshot()); o.begin(true)
        o.updateSafety(snapshot().copy(uxRestricted = Signal.ok(true, SignalSource.EMULATOR, wall)))
        assertEquals(1, fakes.last().closes); assertEquals("BLOCKED", o.event)
        assertFalse(o.begin(true))
    }
    @Test fun losingGearValidityAlsoStopsAnInitializationOnlySession() {
        val o = owner(); o.onResume(); o.updateSafety(snapshot()); o.begin(true)
        o.updateSafety(snapshot().copy(gear = Signal.failed(SignalStatus.DISCONNECTED, SignalSource.EMULATOR, wall)))
        assertEquals(1, fakes.last().closes)
        assertEquals("BLOCKED", o.event)
    }    @Test fun drivingOrUxRestrictionRequiresClosingTheDebugView() {
        val o = owner(); o.onResume(); o.updateSafety(snapshot())
        assertFalse(o.mustCloseView)
        o.updateSafety(snapshot().copy(gear = Signal.ok(Gear.DRIVE, SignalSource.EMULATOR, wall)))
        assertTrue(o.mustCloseView)
        val u = owner(); u.onResume()
        u.updateSafety(snapshot().copy(uxRestricted = Signal.ok(true, SignalSource.EMULATOR, wall)))
        assertTrue(u.mustCloseView)
    }}
