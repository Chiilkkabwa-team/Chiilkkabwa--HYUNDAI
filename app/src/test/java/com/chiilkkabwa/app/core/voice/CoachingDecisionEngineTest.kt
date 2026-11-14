package com.chiilkkabwa.app.core.voice

import com.chiilkkabwa.app.core.event.*
import com.chiilkkabwa.app.core.signal.*
import org.junit.Assert.*
import org.junit.Test

class CoachingDecisionEngineTest {
    private val session = CoachingSession(
        sessionId = "drive-1", goal = "출발·정지",
        runtime = CoachingRuntime.MOCK_REHEARSAL, parkedMockAllowed = true,
    )
    private fun engine(s: CoachingSession = session) = CoachingDecisionEngine(s, 0)
    private fun candidate(
        id: String = "e1", at: Long = 0,
        code: CoachingCode = CoachingCode.RAPID_SPEED_INCREASE,
        source: SignalSource = SignalSource.MOCK,
        status: SignalStatus = SignalStatus.OK,
        goal: String = session.goal,
    ) = CoachingCandidate(
        eventId = id, sessionId = session.sessionId, code = code,
        occurredAtMs = at, evidence = CoachingEvidence(
            if (code == CoachingCode.RAPID_SPEED_DECREASE) -4.0 else 3.0,
            CoachingUnit.METERS_PER_SECOND_SQUARED,
        ), status = status, source = source, goal = goal, voiceAllowed = true,
    )
    private fun offer(e: CoachingDecisionEngine, id: String, at: Long,
                      code: CoachingCode = CoachingCode.RAPID_SPEED_INCREASE): CoachingOffer {
        assertNull(e.enqueue(candidate(id, at, code), at))
        return requireNotNull(e.flush(at + 800))
    }
    private fun speak(e: CoachingDecisionEngine, id: String, at: Long,
                      code: CoachingCode = CoachingCode.RAPID_SPEED_INCREASE) {
        val o = offer(e, id, at, code)
        assertEquals(CoachingStatus.SPOKEN, e.complete(o, PlaybackOutcome.COMPLETED, at + 801)?.status)
    }

    @Test fun selectsMatchingGoalOnlyAfterFullWindow() {
        val e = engine()
        assertNull(e.enqueue(candidate(), 0))
        assertNull(e.flush(799))
        val offered = requireNotNull(e.flush(800))
        assertEquals("e1", offered.candidate.eventId)
        assertEquals(CoachingStatus.OFFERED, e.history.last().status)
        assertFalse(e.didSpeak("e1"))
    }

    @Test fun mismatchedGoalIsBlocked() {
        assertEquals(CoachingReason.NOT_THIS_GOAL,
            engine().enqueue(candidate(goal = "주차"), 0)?.reason)
    }

    @Test fun higherPriorityIneligibleCandidateDoesNotHideEligibleOne() {
        val e = engine()
        e.enqueue(candidate("bad", code = CoachingCode.RAPID_SPEED_DECREASE, goal = "그냥 몸풀기"), 0)
        e.enqueue(candidate("good", 100), 100)
        assertEquals("good", e.flush(900)?.candidate?.eventId)
        assertTrue(e.history.any { it.eventId == "bad" && it.reason == CoachingReason.NOT_THIS_GOAL })
    }

    @Test fun choosesOneEligibleCandidateByPriorityAndRecordsSuppression() {
        val e = engine()
        e.enqueue(candidate("increase"), 0)
        e.enqueue(candidate("decrease", 100, CoachingCode.RAPID_SPEED_DECREASE), 100)
        assertEquals("decrease", e.flush(800)?.candidate?.eventId)
        assertTrue(e.history.any { it.eventId == "increase" && it.reason == CoachingReason.LOWER_PRIORITY })
    }

    @Test fun stableTieUsesObservationTimeAndInsertionOrder() {
        val e = engine()
        e.enqueue(candidate("first"), 0)
        e.enqueue(candidate("second"), 0)
        assertEquals("first", e.flush(800)?.candidate?.eventId)
    }

    @Test fun intervalBoundaryIs45000MsFromSuccessfulOffer() {
        val e = engine()
        speak(e, "first", 0)
        assertEquals(CoachingReason.TOO_SOON, e.enqueue(candidate("early", 45_799), 45_799)?.reason)
        assertNull(e.enqueue(candidate("boundary", 45_800), 45_800))
        assertNotNull(e.flush(46_600))
    }

    @Test fun sameCodeCapIsTwoCompletedUtterances() {
        val e = engine()
        speak(e, "one", 0)
        speak(e, "two", 45_800)
        assertEquals(CoachingReason.SAME_CODE_CAP, e.enqueue(candidate("three", 91_600), 91_600)?.reason)
    }

    @Test fun driveCapIsFiveIncludingOnePraise() {
        val e = engine()
        speak(e, "one", 0)
        speak(e, "two", 45_800)
        speak(e, "three", 91_600, CoachingCode.RAPID_SPEED_DECREASE)
        speak(e, "four", 137_400, CoachingCode.RAPID_SPEED_DECREASE)
        val at = 228_000L
        assertNull(e.considerSteady("praise", at, Signal.ok(20.0, SignalSource.MOCK, at), true))
        val praise = requireNotNull(e.flush(at + 800))
        e.complete(praise, PlaybackOutcome.COMPLETED, at + 801)
        assertEquals(5, e.spokenCount)
        assertEquals(CoachingReason.DRIVE_CAP, e.enqueue(candidate("six", 274_000), 274_000)?.reason)
    }

    @Test fun mentorIsOffByDefaultButExplicitMockOptInIsPossible() {
        assertEquals(CoachingReason.VOICE_OFF,
            engine(session.copy(mode = CoachingMode.MENTOR)).enqueue(candidate(), 0)?.reason)
        assertNull(engine(session.copy(mode = CoachingMode.MENTOR, voiceEnabled = true)).enqueue(candidate(), 0))
    }

    @Test fun defaultRuntimeAndMissingParkedPermissionBlock() {
        assertEquals(CoachingReason.RUNTIME_BLOCKED,
            engine(CoachingSession("drive-1", "출발·정지")).enqueue(candidate(), 0)?.reason)
        assertEquals(CoachingReason.RUNTIME_BLOCKED,
            engine(session.copy(parkedMockAllowed = false)).enqueue(candidate(), 0)?.reason)
        assertEquals(CoachingReason.VOICE_OFF,
            engine().enqueue(candidate().copy(voiceAllowed = false), 0)?.reason)
    }

    @Test fun duplicateIdsCannotBeQueuedOrRetriedAfterFailure() {
        val e = engine()
        val o = offer(e, "same", 0)
        e.complete(o, PlaybackOutcome.TTS_FAILED, 900)
        assertEquals(CoachingReason.DUPLICATE_EVENT,
            e.enqueue(candidate("same", 1000), 1000)?.reason)
        assertNull(e.flush(1800))
        assertEquals(0, e.spokenCount)
    }

    @Test fun staleFutureAndReversedObservationTimesAreRejected() {
        assertEquals(CoachingReason.STALE_EVENT, engine().enqueue(candidate(), 2001)?.reason)
        assertEquals(CoachingReason.FUTURE_EVENT, engine().enqueue(candidate(at = 1), 0)?.reason)
        val e = engine()
        e.enqueue(candidate("new", 100), 100)
        assertEquals(CoachingReason.TIME_REVERSED, e.enqueue(candidate("old", 99), 101)?.reason)
    }

    @Test fun nonIncreasingCallerClockFailsClosed() {
        val e = engine()
        e.enqueue(candidate("first", 100), 100)
        assertNull(e.flush(99))
        assertNull(e.flush(1000))
        assertTrue(e.history.any { it.reason == CoachingReason.TIME_REVERSED })
    }

    @Test fun allInvalidSignalStatusesAreBlockedAndPreserved() {
        for (status in SignalStatus.entries.filter { it != SignalStatus.OK }) {
            val record = requireNotNull(engine().enqueue(candidate(status = status), 0))
            assertEquals(CoachingReason.INVALID_SIGNAL, record.reason)
            assertEquals(status, record.candidate.status)
        }
    }

    @Test fun otherSessionIsRejectedAndOldOfferCannotCompleteNewEngine() {
        val e = engine()
        assertEquals(CoachingReason.WRONG_SESSION,
            e.enqueue(candidate().copy(sessionId = "old"), 0)?.reason)
        val old = offer(e, "old-offer", 0)
        val fresh = engine()
        val next = offer(fresh, "new-offer", 0)
        assertNull(fresh.complete(old, PlaybackOutcome.COMPLETED, 900))
        assertEquals(CoachingStatus.SPOKEN, fresh.complete(next, PlaybackOutcome.COMPLETED, 901)?.status)
    }

    @Test fun onlyNormalCompletionIsSpoken() {
        val e = engine()
        val o = offer(e, "one", 0)
        assertEquals(0, e.spokenCount)
        assertEquals(CoachingStatus.SPOKEN, e.complete(o, PlaybackOutcome.COMPLETED, 900)?.status)
        assertTrue(e.didSpeak("one"))
        assertEquals(1, e.spokenCount)
        assertNull(e.complete(o, PlaybackOutcome.COMPLETED, 901))
    }

    @Test fun everyFailureDiscardsOfferAndLateCompletion() {
        for (outcome in PlaybackOutcome.entries.filter { it != PlaybackOutcome.COMPLETED }) {
            val e = engine()
            val o = offer(e, outcome.name, 0)
            val result = requireNotNull(e.complete(o, outcome, 900))
            assertNotEquals(CoachingStatus.SPOKEN, result.status)
            assertNull(e.complete(o, PlaybackOutcome.COMPLETED, 901))
            assertEquals(0, e.spokenCount)
            assertFalse(e.didSpeak(outcome.name))
            // No failure occupies success quota/cooldown; only a NEW event can be offered.
            assertNotNull(offer(e, "fresh", 1000))
        }
    }

    @Test fun offerTimeoutCannotBeUpgradedToSpoken() {
        val e = engine()
        val o = offer(e, "expired", 0)
        assertNull(e.complete(o, PlaybackOutcome.COMPLETED, 4800))
        assertEquals(CoachingReason.TIMEOUT, e.history.last().reason)
        assertEquals(0, e.spokenCount)
    }

    @Test fun stoppedSessionDiscardsQueueAndActiveOffer() {
        val e = engine()
        val o = offer(e, "active", 0)
        e.stop(900)
        assertNull(e.complete(o, PlaybackOutcome.COMPLETED, 901))
        assertEquals(CoachingReason.SESSION_STOPPED, e.enqueue(candidate("new", 1000), 1000)?.reason)
        val queued = engine()
        queued.enqueue(candidate(), 0)
        queued.stop(100)
        assertNull(queued.flush(1000))
    }

    @Test fun mockEmulatorVehicleSourceIsPreservedAndLiveInputsNeverOffered() {
        for (source in SignalSource.entries) {
            val e = engine()
            val result = e.enqueue(candidate(source = source), 0)
            if (source == SignalSource.MOCK) {
                assertEquals(source, e.flush(800)?.candidate?.source)
            } else {
                assertEquals(CoachingReason.RUNTIME_BLOCKED, result?.reason)
                assertEquals(source, result?.candidate?.source)
                assertNull(e.flush(800))
            }
        }
    }

    @Test fun staleAndNoLongerEligibleQueuedItemsAreNotOffered() {
        val e = engine()
        e.enqueue(candidate(), 0)
        assertNull(e.flush(2001))
        assertEquals(CoachingReason.STALE_EVENT, e.history.last().reason)
    }

    @Test fun mismatchedEvidenceUnitNonfiniteAndWrongSignAreBlocked() {
        val bad = listOf(
            CoachingEvidence(Double.NaN, CoachingUnit.METERS_PER_SECOND_SQUARED),
            CoachingEvidence(Double.POSITIVE_INFINITY, CoachingUnit.METERS_PER_SECOND_SQUARED),
            CoachingEvidence(3.0, CoachingUnit.KILOMETERS_PER_HOUR),
            CoachingEvidence(-3.0, CoachingUnit.METERS_PER_SECOND_SQUARED),
        )
        for (evidence in bad) assertEquals(CoachingReason.INVALID_EVIDENCE,
            engine().enqueue(candidate().copy(evidence = evidence), 0)?.reason)
    }

    @Test fun praiseNeeds90SecondsValidFreshDrivingSpeedAndIsOnceOnly() {
        val e = engine()
        assertEquals(CoachingReason.QUIET_TOO_SHORT,
            e.considerSteady("early", 89_999, Signal.ok(20.0, SignalSource.MOCK, 89_999), true)?.reason)
        assertEquals(CoachingReason.INVALID_EVIDENCE,
            e.considerSteady("slow", 90_000, Signal.ok(9.99, SignalSource.MOCK, 90_000), true)?.reason)
        assertEquals(CoachingReason.INVALID_EVIDENCE,
            e.considerSteady("not-driving", 90_001, Signal.ok(20.0, SignalSource.MOCK, 90_001), false)?.reason)
        assertNull(e.considerSteady("valid", 90_002, Signal.ok(10.0, SignalSource.MOCK, 90_002), true))
        val o = requireNotNull(e.flush(90_802))
        e.complete(o, PlaybackOutcome.COMPLETED, 90_803)
        assertEquals(CoachingReason.ONCE_ONLY,
            e.considerSteady("again", 180_803, Signal.ok(20.0, SignalSource.MOCK, 180_803), true)?.reason)
    }

    @Test fun praiseDoesNotTreatMissingOrStaleSignalsAsGoodDriving() {
        for (signal in listOf(Signal.unknown<Double>(), Signal.ok(20.0, SignalSource.MOCK, 0))) {
            val e = engine()
            assertNotNull(e.considerSteady("praise", 90_000, signal, true))
            assertNull(e.flush(90_800))
        }
    }

    @Test fun invalidGoalAndBlankIdsDoNotPass() {
        assertEquals(CoachingReason.NOT_THIS_GOAL,
            engine(session.copy(goal = "invented")).enqueue(candidate(goal = "invented"), 0)?.reason)
        assertEquals(CoachingReason.INVALID_ID, engine().enqueue(candidate(id = ""), 0)?.reason)
    }

    @Test fun linesStayNeutralAndEstimatedLengthIsNotMeasuredPlayback() {
        assertEquals(setOf("RAPID_SPEED_INCREASE", "RAPID_SPEED_DECREASE", "STEADY"),
            CoachingCode.entries.map { it.name }.toSet())
        for (code in CoachingCode.entries) {
            val line = CoachingPolicy.line(code)
            assertTrue(CoachingPolicy.estimateSeconds(line) <= 2.5)
            for (forbidden in listOf("브레이크", "사고", "충돌", "차선", "앞차", "밟", "꺾")) {
                assertFalse(line.contains(forbidden))
            }
        }
    }

    @Test fun a02AdapterPreservesIdentityEvidenceUnitsAndDoesNotInferBraking() {
        val speed = SpeedEventEngine("drive-1")
        speed.update(SpeedSample(10.0, 0, SpeedUnit.METERS_PER_SECOND, SignalSource.MOCK, SignalStatus.OK), 0)
        val result = speed.update(SpeedSample(6.0, 1000, SpeedUnit.METERS_PER_SECOND, SignalSource.MOCK, SignalStatus.OK), 1000)
            as SpeedDetectionResult.Evaluated
        val event = requireNotNull(result.event)
        val c = CoachingCandidate.fromSpeedEvent(event, "출발·정지", true)
        assertEquals(event.eventId, c.eventId)
        assertEquals(event.sessionId, c.sessionId)
        assertEquals(event.evidence, c.evidence.speedChange)
        assertEquals(event.occurredAtMs, c.occurredAtMs)
        assertEquals(SignalSource.MOCK, c.source)
        assertEquals(CoachingCode.RAPID_SPEED_DECREASE, c.code)
        assertEquals(CoachingUnit.METERS_PER_SECOND_SQUARED, c.evidence.unit)
        val e = engine()
        e.enqueue(c, 1000)
        assertEquals("속도가 빠르게 줄었어요.", e.flush(1800)?.line)
    }
    @Test fun finiteButImplausibleEvidenceCannotGenerateCoaching() {
        assertEquals(CoachingReason.INVALID_EVIDENCE,
            engine().enqueue(candidate().copy(evidence = CoachingEvidence(1e100,
                CoachingUnit.METERS_PER_SECOND_SQUARED)), 0)?.reason)
        val e = engine()
        assertEquals(CoachingReason.INVALID_EVIDENCE,
            e.considerSteady("impossible-speed", 90_000,
                Signal.ok(1e100, SignalSource.MOCK, 90_000), true)?.reason)
        assertNull(e.flush(90_800))
    }

    @Test fun boundedHistoryDoesNotAllowConsumedIdsOrSpokenIdsToReplay() {
        val e = CoachingDecisionEngine(session, 0,
            CoachingPolicy(maxTrackedEventIds = 2, maxHistoryRecords = 2))
        speak(e, "spoken", 0)
        e.enqueue(candidate("blocked", 1000), 1000)
        e.enqueue(candidate("overflow", 1001), 1001)
        assertEquals(CoachingReason.CAPACITY_REACHED, e.history.last().reason)
        assertEquals(2, e.history.size)
        assertTrue(e.didSpeak("spoken"))
        assertEquals(CoachingReason.DUPLICATE_EVENT,
            e.enqueue(candidate("spoken", 1002), 1002)?.reason)
    }

    @Test fun sameCodeCapDoesNotHideAnotherEligibleCode() {
        val e = engine()
        speak(e, "decrease1", 0, CoachingCode.RAPID_SPEED_DECREASE)
        speak(e, "decrease2", 45_800, CoachingCode.RAPID_SPEED_DECREASE)
        e.enqueue(candidate("capped", 91_600, CoachingCode.RAPID_SPEED_DECREASE), 91_600)
        e.enqueue(candidate("eligible", 91_700), 91_700)
        assertEquals("eligible", e.flush(92_500)?.candidate?.eventId)
        assertTrue(e.history.any { it.eventId == "capped" && it.reason == CoachingReason.SAME_CODE_CAP })
    }

    @Test fun lateWindowAdmissionAndBoundaryAgeAreExplicit() {
        val e = engine()
        e.enqueue(candidate("first"), 0)
        assertEquals(CoachingReason.WINDOW_ELAPSED,
            e.enqueue(candidate("late", 801), 801)?.reason)
        assertEquals("first", e.flush(2000)?.candidate?.eventId)
    }
}
