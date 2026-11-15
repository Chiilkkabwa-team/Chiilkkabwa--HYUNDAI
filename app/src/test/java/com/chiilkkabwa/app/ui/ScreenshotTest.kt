package com.chiilkkabwa.app.ui

import androidx.compose.foundation.layout.BoxScope
import androidx.compose.runtime.Composable
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.chiilkkabwa.app.core.achievement.Badge
import com.chiilkkabwa.app.core.achievement.EarnedBadge
import com.chiilkkabwa.app.core.achievement.PracticeHistory
import com.chiilkkabwa.app.core.event.SpeedEventType
import com.chiilkkabwa.app.core.mission.MissionId
import com.chiilkkabwa.app.core.mission.MissionResult
import com.chiilkkabwa.app.core.mission.Progress
import com.chiilkkabwa.app.core.mission.RunnerState
import com.chiilkkabwa.app.core.review.DriveTimeline
import com.chiilkkabwa.app.core.review.TimelineMark
import com.chiilkkabwa.app.core.review.TimelinePoint
import com.chiilkkabwa.app.core.safety.GateState
import com.chiilkkabwa.app.core.safety.LockReason
import com.chiilkkabwa.app.core.session.Phase
import com.chiilkkabwa.app.core.session.PracticeSummary
import com.chiilkkabwa.app.core.session.SessionState
import com.chiilkkabwa.app.core.signal.Gear
import com.chiilkkabwa.app.core.signal.Signal
import com.chiilkkabwa.app.core.signal.SignalSource
import com.chiilkkabwa.app.core.signal.SignalStatus
import com.chiilkkabwa.app.core.signal.VehicleSnapshot
import com.chiilkkabwa.app.data.store.Profile
import com.chiilkkabwa.app.data.store.ReviewRecord
import com.chiilkkabwa.app.ui.screens.BadgeToast
import com.chiilkkabwa.app.ui.screens.DrivingLockScreen
import com.chiilkkabwa.app.ui.screens.GateScreen
import com.chiilkkabwa.app.ui.screens.GoalScreen
import com.chiilkkabwa.app.ui.screens.PactScreen
import com.chiilkkabwa.app.ui.screens.ParkingSimScreen
import com.chiilkkabwa.app.ui.screens.PedalScreen
import com.chiilkkabwa.app.ui.screens.ReadyToGoScreen
import com.chiilkkabwa.app.ui.screens.ReviewScreen
import com.chiilkkabwa.app.ui.screens.ScreenFrame
import com.chiilkkabwa.app.ui.screens.Sheet
import com.chiilkkabwa.app.ui.screens.SheetHost
import com.chiilkkabwa.app.ui.screens.WarmupScreen
import com.chiilkkabwa.app.ui.theme.ChiilTheme
import com.github.takahirom.roborazzi.captureRoboImage
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * 에뮬레이터 없이 차량 화면 크기로 모든 화면을 그려 PNG로 남긴다(Roborazzi + Robolectric).
 * 사람이 눈으로 보는 확인용이다 — 픽셀 비교로 실패시키지 않는다.
 * 크기: Pleos 에뮬레이터 앱 창(2026-11-15 영상 추정 약 1680×1200dp)과 2창 분할(800×600dp).
 * 결과: app/build/outputs/roborazzi/{크기}_{화면}.png (release-apk 워크플로 artifact로 올라간다)
 */
@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
abstract class ScreenshotBase(private val size: String) {

    /** 앱과 같은 틀(ScreenFrame: 배율·머리줄·최대 폭)에 화면을 넣어 찍는다 */
    private fun shot(
        name: String,
        phase: Phase = Phase.PREPARING,
        onOpen: ((Sheet) -> Unit)? = null,
        overlay: @Composable BoxScope.() -> Unit = {},
        content: @Composable () -> Unit = {},
    ) = captureRoboImage("build/outputs/roborazzi/${size}_$name.png") {
        ChiilTheme { ScreenFrame(phase, SignalSource.MOCK, onOpen, overlay = overlay, content = content) }
    }

    private fun <T> ok(v: T) = Signal.ok(v, SignalSource.MOCK, 1L)
    private val parked = VehicleSnapshot(gear = ok(Gear.PARK), parkingBrakeOn = ok(true), uxRestricted = ok(false), passengerSeated = ok(true))
    private val me = Profile("p1", "첫째", nextGoal = "주차")
    private val readyUi = UiState(session = SessionState(Phase.READY, null), gate = GateState.Ready, snapshot = parked, profile = me, profiles = listOf(me, Profile("p2", "둘째")))

    @Test fun gateReady() = shot("01_gate_ready", Phase.READY, onOpen = {}) { GateScreen(readyUi, onStart = {}, onVoicePreview = {}) }

    @Test fun gateChecking() = shot("02_gate_checking", Phase.CHECKING, onOpen = {}) {
        GateScreen(UiState(snapshot = VehicleSnapshot(gear = ok(Gear.DRIVE), parkingBrakeOn = ok(false)), gate = GateState.Locked(LockReason.UNKNOWN_STATE)), onStart = {})
    }

    @Test fun goal() = shot("03_goal") { GoalScreen(readyUi) { _, _ -> } }

    @Test fun warmup() = shot("04_warmup") {
        WarmupScreen(readyUi.copy(runner = RunnerState(2, 6, "방향지시등 왼쪽 → 오른쪽", Progress.InProgress(1, 3, "왼쪽 방향지시등을 켜 보세요(레버 아래로)"),
            listOf(MissionResult(MissionId.SEAT_BELT, SignalSource.MOCK, 1, null), MissionResult(MissionId.BRAKE_PRESS_RELEASE, SignalSource.MOCK, 2, null)), false)), {}, {})
    }

    @Test fun warmupUnavailable() = shot("05_warmup_unsupported") {
        WarmupScreen(readyUi.copy(runner = RunnerState(3, 6, "와이퍼 켜고 끄기", Progress.Unavailable(SignalStatus.UNSUPPORTED), emptyList(), false)), {}, {})
    }

    @Test fun warmupDone() = shot("06_warmup_done") {
        WarmupScreen(readyUi.copy(runner = RunnerState(6, 6, null, null, MissionId.entries.take(6).map { MissionResult(it, SignalSource.MOCK, 1, null) }, true)), {}, {})
    }

    @Test fun parking() = shot("07_parking") { ParkingSimScreen(readyUi) {} }

    @Test fun pedal() = shot("08_pedal") {
        PedalScreen(readyUi.copy(snapshot = parked.copy(brakePedalPct = ok(27f)), pedal = Progress.Done(SignalSource.MOCK, "부드러웠어요"))) {}
    }

    @Test fun pact() = shot("09_pact") { PactScreen(readyUi.copy(goal = "주차", pact = setOf("천천히 출발하기")), {}, {}) }

    @Test fun ready() = shot("10_ready") { ReadyToGoScreen(readyUi) }

    @Test fun lock() = shot("11_lock", Phase.DRIVING_LOCK) { DrivingLockScreen() }

    private val timeline = DriveTimeline(
        points = listOf(0f to 0f, 30f to 18f, 60f to 32f, 62f to 48f, 120f to 40f, 150f to 22f, 152f to 6f, 200f to 0f).map { (t, k) -> TimelinePoint(t, k) },
        marks = listOf(TimelineMark(62f, 32f, 48f, SpeedEventType.RAPID_SPEED_INCREASE, "e1"), TimelineMark(152f, 22f, 6f, SpeedEventType.RAPID_SPEED_DECREASE, "e2")),
        sources = setOf(SignalSource.MOCK),
    )

    @Test fun review() = shot("12_review", Phase.REVIEW) {
        ReviewScreen(readyUi.copy(summary = PracticeSummary(3.2f, 14, true), timeline = timeline, spokenEventIds = setOf("e1"), mentorMode = true)) { _, _, _, _ -> }
    }

    private val history = me.copy(
        history = PracticeHistory(reviewsSaved = 2, parkingSimCompleted = 3, graduated = true,
            badges = listOf(EarnedBadge(Badge.FIRST_CLICK, 1, true), EarnedBadge(Badge.GRADUATE, 1_700_000_000_000, false))),
        reviews = listOf(ReviewRecord(1_700_000_000_000, 14, 3.2f, "조금 떨렸어요", null, "주차")),
    )

    @Test fun sheetIntro() = shot("13_sheet_intro", Phase.READY, onOpen = {}, overlay = { SheetHost(Sheet.INTRO, readyUi) {} })
    @Test fun sheetHistory() = shot("14_sheet_history", Phase.READY, onOpen = {}, overlay = { SheetHost(Sheet.HISTORY, readyUi.copy(profile = history)) {} })
    @Test fun sheetSettings() = shot("15_sheet_settings", Phase.READY, onOpen = {}, overlay = { SheetHost(Sheet.SETTINGS, readyUi) {} })
    @Test fun sheetAbout() = shot("16_sheet_about", Phase.READY, onOpen = {}, overlay = { SheetHost(Sheet.ABOUT, readyUi) {} })

    @Test fun badge() = shot("17_badge", Phase.READY, onOpen = {}, overlay = { BadgeToast(readyUi.copy(newBadges = listOf(EarnedBadge(Badge.FIRST_CLICK, 1, true)))) {} }) {
        GateScreen(readyUi, onStart = {})
    }
}

/** Pleos 에뮬레이터 앱 창(영상 추정). mdpi라 PNG 크기 = dp */
@Config(sdk = [34], qualifiers = "w1680dp-h1200dp-land-mdpi")
class ScreenshotPleosTest : ScreenshotBase("pleos1680")

/** 2창 분할 등 작은 창 */
@Config(sdk = [34], qualifiers = "w800dp-h600dp-land-mdpi")
class ScreenshotSplitTest : ScreenshotBase("split800")
