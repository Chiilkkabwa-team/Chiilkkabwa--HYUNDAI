package com.chiilkkabwa.app.data.navigation

import com.chiilkkabwa.app.core.navigation.*
import com.chiilkkabwa.app.core.signal.*
import org.junit.Assert.*
import org.junit.Test

class PleosNaviAdapterTest {
    private class Fake : NaviClient {
        lateinit var sink: (NaviEvent) -> Unit
        var received: PracticeRouteRequest? = null
        var calls = 0
        var closes = 0
        var failure: RouteProblem? = null
        var closeFails = false
        var onRequest: (() -> Unit)? = null
        override fun connect(sink: (NaviEvent) -> Unit) { this.sink = sink }
        override fun request(route: PracticeRouteRequest) {
            calls++
            received = route
            failure?.let { throw NaviClientException(it) }
            onRequest?.invoke()
        }
        override fun close() { closes++; if (closeFails) error("release failed") }
    }
    private var wall = 10_000L
    private var elapsed = 0L
    private val clients = mutableListOf<Fake>()
    private fun safe() = VehicleSnapshot(
        gear = Signal.ok(Gear.PARK, SignalSource.EMULATOR, wall),
        parkingBrakeOn = Signal.ok(true, SignalSource.EMULATOR, wall),
        uxRestricted = Signal.ok(false, SignalSource.EMULATOR, wall))
    private fun point(lat: Double = 37.0, lon: Double = 127.0, id: String = "SYNTHETIC_DEST") =
        RoutePoint(lat, lon, "합성 장소", id, "합성 시험 주소", "0")
    private fun request(id: String = "r1") = PracticeRouteRequest(id, point(),
        dataSource = RouteDataSource.SYNTHETIC_TEST)
    private fun adapter(r: NaviRuntime = NaviRuntime(true, true, SignalSource.EMULATOR)) =
        PleosNaviAdapter(r, { Fake().also { clients += it } }, { wall }, { elapsed })
    private fun ready(): PleosNaviAdapter = adapter().also {
        assertTrue(it.start()); clients.last().sink(NaviEvent.Connected); it.updateSafety(safe())
    }

    @Test fun runtimeDefaultsOffAndOnlyExplicitDebugEmulatorIsAllowed() {
        for (r in listOf(NaviRuntime(), NaviRuntime(false, true, SignalSource.EMULATOR),
                NaviRuntime(true, false, SignalSource.EMULATOR),
                NaviRuntime(true, true, SignalSource.VEHICLE), NaviRuntime(true, true, SignalSource.MOCK))) {
            assertFalse(adapter(r).start())
        }
        assertTrue(clients.isEmpty())
        assertTrue(adapter().start())
    }
    @Test fun initializationNeedsCallbackAndCannotDuplicateConnect() {
        val a = adapter(); assertTrue(a.start()); assertFalse(a.progress.initialized)
        assertFalse(a.start()); assertEquals(1, clients.size)
        clients.last().sink(NaviEvent.Connected)
        assertTrue(a.progress.initialized); assertEquals(NaviPhase.READY, a.progress.phase)
    }
    @Test fun failedInitializationCanExplicitlyRestartButIgnoresOldConnection() {
        val a = adapter(); a.start(); val old = clients.last()
        old.sink(NaviEvent.InitializationFailed(RouteProblem.CONNECTION_FAILED))
        assertEquals(NaviPhase.FAILED, a.progress.phase); assertEquals(1, old.closes)
        assertTrue(a.start()); old.sink(NaviEvent.Connected); assertFalse(a.progress.initialized)
        clients.last().sink(NaviEvent.Connected); assertTrue(a.progress.initialized)
    }
    @Test fun requestsRequireExplicitActionConnectedClientAndFreshGate() {
        val a = adapter(); a.updateSafety(safe()); assertFalse(a.request(request(), true))
        val b = ready(); assertFalse(b.request(request(), false))
        assertEquals(RouteProblem.NO_USER_REQUEST, b.progress.problem)
        b.updateSafety(VehicleSnapshot()); assertFalse(b.request(request(), true))
        b.updateSafety(safe()); assertTrue(b.request(request(), true))
        assertEquals(1, clients.last().calls)
    }
    @Test fun threeStrictParkedConditionsAreRequired() {
        for (snapshot in listOf(
            safe().copy(gear = Signal.ok(Gear.DRIVE, SignalSource.EMULATOR, wall)),
            safe().copy(parkingBrakeOn = Signal.ok(false, SignalSource.EMULATOR, wall)),
            safe().copy(uxRestricted = Signal.ok(true, SignalSource.EMULATOR, wall)))) {
            assertEquals(RouteProblem.NOT_PARKED, RouteRequestGate.check(snapshot, wall, SignalSource.EMULATOR))
        }
        assertNull(RouteRequestGate.check(safe(), wall, SignalSource.EMULATOR))
    }
    @Test fun everyBadStatusOnEveryRequiredSignalBlocks() {
        for (status in SignalStatus.entries.filter { it != SignalStatus.OK }) {
            for (s in listOf(
                safe().copy(gear = Signal.failed(status, SignalSource.EMULATOR, wall)),
                safe().copy(parkingBrakeOn = Signal.failed(status, SignalSource.EMULATOR, wall)),
                safe().copy(uxRestricted = Signal.failed(status, SignalSource.EMULATOR, wall)))) {
                assertEquals(RouteProblem.UNUSABLE_SIGNAL, RouteRequestGate.check(s, wall, SignalSource.EMULATOR))
            }
        }
    }
    @Test fun futureStaleNullAndMixedSourceSafetyNeverOpens() {
        assertNull(RouteRequestGate.check(safe(), wall + 1500, SignalSource.EMULATOR))
        assertEquals(RouteProblem.STALE_SIGNAL, RouteRequestGate.check(safe(), wall + 1501, SignalSource.EMULATOR))
        assertEquals(RouteProblem.STALE_SIGNAL, RouteRequestGate.check(safe(), wall - 1, SignalSource.EMULATOR))
        assertEquals(RouteProblem.UNUSABLE_SIGNAL, RouteRequestGate.check(VehicleSnapshot(), wall, SignalSource.EMULATOR))
        assertEquals(RouteProblem.SOURCE_MISMATCH, RouteRequestGate.check(safe().copy(
            parkingBrakeOn = Signal.ok(true, SignalSource.MOCK, wall)), wall, SignalSource.EMULATOR))
    }
    @Test fun coordinateBoundariesAndInvalidNumbers() {
        for ((lat, lon) in listOf(-90.0 to -180.0, 90.0 to 180.0, 0.0 to 0.0))
            assertNull(request().copy(destination = point(lat, lon)).invalidReason())
        for ((lat, lon) in listOf(Double.NaN to 0.0, 0.0 to Double.POSITIVE_INFINITY,
                90.001 to 0.0, 0.0 to -180.001, 127.0 to 37.0))
            assertEquals(RouteInputError.COORDINATES, request().copy(destination = point(lat, lon)).invalidReason())
    }
    @Test fun requiredFieldsAndRequestIdAreValidatedBeforeSdkCall() {
        for (p in listOf(point().copy(poiName = ""), point().copy(poiId = " "),
                point().copy(address = ""), point().copy(poiSubId = "")))
            assertEquals(RouteInputError.REQUIRED_FIELD, request().copy(destination = p).invalidReason())
        assertEquals(RouteInputError.REQUEST_ID, request("").invalidReason())
        val a = ready(); assertFalse(a.request(request().copy(destination = point(Double.NaN)), true))
        assertEquals(0, clients.last().calls); assertEquals(RouteProblem.INVALID_ROUTE, a.progress.problem)
    }
    @Test fun waypointCountsOrderAndDuplicatePointsOrPoisAreRejected() {
        val w1 = RouteWaypoint(1, point(37.1, 127.1, "SYNTHETIC_W1"))
        val w2 = RouteWaypoint(2, point(37.2, 127.2, "SYNTHETIC_W2"))
        assertNull(request().copy(waypoints = listOf(w1, w2)).invalidReason())
        assertEquals(RouteInputError.WAYPOINT_COUNT, request().copy(waypoints = listOf(w1, w2, w2)).invalidReason())
        for (ws in listOf(listOf(w2), listOf(w2, w1), listOf(w1, w1)))
            assertEquals(RouteInputError.WAYPOINT_ORDER, request().copy(waypoints = ws).invalidReason())
        assertEquals(RouteInputError.DUPLICATE_POINT, request().copy(waypoints = listOf(RouteWaypoint(1, point()))).invalidReason())
        assertEquals(RouteInputError.DUPLICATE_POINT, request().copy(waypoints = listOf(
            RouteWaypoint(1, point(37.1, 127.1)))).invalidReason())
    }
    @Test fun unitReturnStartCompleteAndNavigationAreDistinctObservations() {
        val a = ready(); assertTrue(a.request(request(), true))
        assertTrue(a.progress.callReturned); assertFalse(a.progress.searchStarted)
        assertFalse(a.progress.searchCompleted); assertNull(a.progress.navigationStatus)
        clients.last().sink(NaviEvent.SearchStarted)
        assertTrue(a.progress.searchStarted); assertFalse(a.progress.searchCompleted)
        clients.last().sink(NaviEvent.SearchCompleted); assertTrue(a.progress.searchCompleted)
        clients.last().sink(NaviEvent.NavigationObserved("ROUTE_DRIVE"))
        assertEquals("ROUTE_DRIVE", a.progress.navigationStatus)
        assertFalse(a.progress.guidanceConfirmed); assertFalse(a.progress.callbackCorrelationVerified)
    }
    @Test fun duplicateRequestAndConsumedIdCannotBeRetried() {
        val a = ready(); a.request(request(), true)
        assertFalse(a.request(request("r2"), true)); assertEquals(1, clients.last().calls)
        a.stop(); a.start(); clients.last().sink(NaviEvent.Connected); a.updateSafety(safe())
        assertFalse(a.request(request(), true)); assertEquals(RouteProblem.DUPLICATE_REQUEST, a.progress.problem)
    }
    @Test fun permissionUnsupportedAndDisconnectFailureAreNotSuccess() {
        for (p in listOf(RouteProblem.PERMISSION_DENIED, RouteProblem.UNSUPPORTED, RouteProblem.DISCONNECTED)) {
            val a = ready(); clients.last().failure = p
            assertFalse(a.request(request(), true)); assertEquals(p, a.progress.problem)
            assertFalse(a.progress.callReturned); assertEquals(NaviPhase.FAILED, a.progress.phase)
        }
    }
    @Test fun sdkErrorClosesObservationAndIgnoresLateCompletion() {
        val a = ready(); a.request(request(), true); val old = clients.last()
        old.sink(NaviEvent.Error("CAUSE_SEARCH_ROUTE"))
        assertEquals(RouteProblem.SDK_ERROR, a.progress.problem)
        assertEquals("CAUSE_SEARCH_ROUTE", a.progress.errorCode)
        old.sink(NaviEvent.SearchCompleted); assertFalse(a.progress.searchCompleted)
    }
    @Test fun gateLossStopsLocallyWithoutAnyNavigationCancellationApi() {
        val a = ready(); a.request(request(), true); val old = clients.last()
        a.updateSafety(safe().copy(parkingBrakeOn = Signal.ok(false, SignalSource.EMULATOR, wall)))
        assertEquals(NaviPhase.BLOCKED, a.progress.phase); assertEquals(1, old.closes)
        old.sink(NaviEvent.SearchCompleted); assertFalse(a.progress.searchCompleted)
        assertFalse(a.request(request("new"), true))
    }
    @Test fun staleGateIsRecheckedOnCallbacks() {
        val a = ready(); a.request(request(), true); wall += 1501
        clients.last().sink(NaviEvent.SearchCompleted)
        assertEquals(RouteProblem.STALE_SIGNAL, a.progress.problem); assertFalse(a.progress.searchCompleted)
    }
    @Test fun initAndResponseTimeoutIgnoreLateCallbacks() {
        val a = adapter(); a.start(); val old = clients.last()
        elapsed = 15_000; a.tick(); assertEquals(RouteProblem.INIT_TIMEOUT, a.progress.problem)
        old.sink(NaviEvent.Connected); assertFalse(a.progress.initialized)
        elapsed = 20_000
        val b = ready(); b.request(request(), true)
        wall += 15_000; b.updateSafety(safe()); elapsed += 15_000
        b.tick(); assertEquals(RouteProblem.RESPONSE_TIMEOUT, b.progress.problem)
        clients.last().sink(NaviEvent.SearchCompleted); assertFalse(b.progress.searchCompleted)
    }
    @Test fun stopAndReconnectRejectPreviousGenerationCallbacks() {
        val a = ready(); a.request(request(), true); val old = clients.last()
        a.stop(); a.stop(); assertEquals(1, old.closes)
        a.start(); clients.last().sink(NaviEvent.Connected); a.updateSafety(safe())
        a.request(request("fresh"), true)
        old.sink(NaviEvent.SearchStarted); old.sink(NaviEvent.SearchCompleted)
        assertFalse(a.progress.searchStarted); assertFalse(a.progress.searchCompleted)
    }
    @Test fun preRequestAndOutOfOrderCallbacksNeverConfirmGuidance() {
        val a = ready(); clients.last().sink(NaviEvent.SearchCompleted)
        assertFalse(a.progress.searchCompleted); a.request(request(), true)
        clients.last().sink(NaviEvent.SearchCompleted); clients.last().sink(NaviEvent.SearchStarted)
        assertTrue(a.progress.searchStarted); assertTrue(a.progress.searchCompleted)
        assertFalse(a.progress.guidanceConfirmed)
    }
    @Test fun synchronousCallbackSurvivesUnitReturn() {
        val a = ready(); clients.last().onRequest = { clients.last().sink(NaviEvent.SearchCompleted) }
        assertTrue(a.request(request(), true)); assertTrue(a.progress.callReturned)
        assertTrue(a.progress.searchCompleted)
    }
    @Test fun uncertainCleanupBlocksFurtherClientCreation() {
        val a = ready(); clients.last().closeFails = true; a.stop()
        assertTrue(a.progress.cleanupFailed); assertFalse(a.start()); assertEquals(1, clients.size)
    }
    @Test fun monotonicClockReversalFailsClosed() {
        elapsed = 100; val a = ready(); a.request(request(), true)
        elapsed = 99; a.tick(); assertEquals(RouteProblem.CLOCK_REVERSED, a.progress.problem)
        assertEquals(1, clients.last().closes)
    }
    @Test fun sdkMappingUsesNamedLatitudeLongitudeAndFirstSecondWaypoints() {
        val route = request().copy(waypoints = listOf(
            RouteWaypoint(1, point(37.1, 127.1, "SYNTHETIC_W1")),
            RouteWaypoint(2, point(37.2, 127.2, "SYNTHETIC_W2"))))
        val sdk = NaviSdkMapper.withWaypoints(route)
        assertEquals(37.0, sdk.destination.latitude, 0.0)
        assertEquals(127.0, sdk.destination.longitude, 0.0)
        assertEquals(37.1, sdk.waypoints[0].latitude, 0.0)
        assertEquals(127.2, sdk.waypoints[1].longitude, 0.0)
        assertEquals("FIRST", sdk.waypoints[0].waypointIndex.name)
        assertEquals("SECOND", sdk.waypoints[1].waypointIndex.name)
    }
    @Test fun signedZeroCoordinatesAreStillTheSamePoint() {
        val r = request().copy(destination = point(0.0, 0.0), waypoints = listOf(
            RouteWaypoint(1, point(-0.0, 0.0, "SYNTHETIC_OTHER"))))
        assertEquals(RouteInputError.DUPLICATE_POINT, r.invalidReason())
    }
    @Test fun callerCannotMutateWaypointListAfterItIsSubmitted() {
        val a = ready()
        val mutable = mutableListOf(RouteWaypoint(1, point(37.1, 127.1, "SYNTHETIC_W1")))
        val r = request().copy(waypoints = mutable)
        assertTrue(a.request(r, true)); mutable.clear()
        assertEquals(1, clients.last().received?.waypoints?.size)
        assertTrue(a.progress.callReturned)
        assertFalse(a.request(request("second"), true))
        assertTrue(a.progress.callReturned)
        assertEquals(RouteProblem.BUSY, a.lastRejection)
    }
    @Test fun consumedRequestIdsStayBoundedAndFailClosed() {
        val a = adapter()
        repeat(256) { i ->
            a.start(); clients.last().sink(NaviEvent.Connected); a.updateSafety(safe())
            assertTrue(a.request(request("unique-" + i), true)); a.stop()
        }
        a.start(); clients.last().sink(NaviEvent.Connected); a.updateSafety(safe())
        assertFalse(a.request(request("over-cap"), true))
        assertEquals(RouteProblem.CAPACITY_REACHED, a.lastRejection)
    }}
