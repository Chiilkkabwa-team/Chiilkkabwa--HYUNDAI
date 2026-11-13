package com.chiilkkabwa.app.ui

import android.app.Application
import android.util.Log
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.chiilkkabwa.app.BuildConfig
import com.chiilkkabwa.app.core.achievement.AchievementRules
import com.chiilkkabwa.app.core.achievement.EarnedBadge
import com.chiilkkabwa.app.core.mission.MissionCatalog
import com.chiilkkabwa.app.core.mission.MissionJudge
import com.chiilkkabwa.app.core.mission.MissionRunner
import com.chiilkkabwa.app.core.mission.PedalHoldJudge
import com.chiilkkabwa.app.core.mission.Progress
import com.chiilkkabwa.app.core.mission.RunnerState
import com.chiilkkabwa.app.core.safety.GatePolicy
import com.chiilkkabwa.app.core.safety.GateState
import com.chiilkkabwa.app.core.safety.SafetyGate
import com.chiilkkabwa.app.core.session.Phase
import com.chiilkkabwa.app.core.session.PracticeSummary
import com.chiilkkabwa.app.core.session.SessionReducer
import com.chiilkkabwa.app.core.session.SessionState
import com.chiilkkabwa.app.core.session.summarize
import com.chiilkkabwa.app.core.signal.SignalStatus
import com.chiilkkabwa.app.core.signal.VehicleSnapshot
import com.chiilkkabwa.app.data.store.PracticeStore
import com.chiilkkabwa.app.data.store.Profile
import com.chiilkkabwa.app.data.store.ReviewRecord
import com.chiilkkabwa.app.data.vehicle.AaosVehicleSource
import com.chiilkkabwa.app.data.vehicle.MockVehicleSource
import com.chiilkkabwa.app.data.vehicle.PleosVehicleSource
import com.chiilkkabwa.app.data.vehicle.VehicleRepository
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/** 주차 중 준비 단계(PREPARING 내부) */
enum class PrepStep { GOAL, WARMUP, PARKING_SIM, PEDAL, PACT, READY_TO_GO }

data class UiState(
    val session: SessionState = SessionState(),
    val gate: GateState = GateState.Locked(com.chiilkkabwa.app.core.safety.LockReason.UNKNOWN_STATE),
    val snapshot: VehicleSnapshot = VehicleSnapshot(),
    val step: PrepStep = PrepStep.GOAL,
    val goal: String? = null,
    val mentorMode: Boolean = false,
    val runner: RunnerState? = null,
    val pedal: Progress? = null,
    val pact: Set<String> = emptySet(),
    val summary: PracticeSummary? = null,
    val profile: Profile = Profile("default", "나"),
    val newBadges: List<EarnedBadge> = emptyList(),
    val mockMode: Boolean = BuildConfig.USE_MOCK,
)

/**
 * 앱 상태의 단일 출처. 흐름:
 *  차량 스냅샷 → SafetyGate → SessionReducer → (PREPARING이면) MissionRunner → UiState
 * 시간 기반 판정(핸들 2초 유지, 페달 2초 유지)을 위해 200ms 틱으로도 update를 호출한다.
 */
class ChiilViewModel(app: Application) : AndroidViewModel(app) {
    val mock = MockVehicleSource()
    private val repo = VehicleRepository(
        if (BuildConfig.USE_MOCK) listOf(mock)
        else listOf(AaosVehicleSource(app), PleosVehicleSource(app))
    )
    private val store = PracticeStore(app)
    private val policy = GatePolicy()

    private val _ui = MutableStateFlow(UiState(profile = store.profiles().firstOrNull() ?: Profile("default", "나")))
    val ui: StateFlow<UiState> = _ui.asStateFlow()

    private var runner: MissionRunner? = null
    private var pedalJudge: MissionJudge? = null
    private var lastSignalTrace: String? = null

    init {
        viewModelScope.launch { repo.snapshot.collect { onSnapshot(it) } }
        // 시간 기반 판정(2초 유지)은 준비 단계에서만 필요하다 → 그 외(특히 주행 중)에는 틱을 돌리지 않는다(CPU 규정)
        viewModelScope.launch { while (true) { delay(200); if (_ui.value.session.phase == Phase.PREPARING) onSnapshot(repo.snapshot.value) } }
    }

    /** A01: 백그라운드에서 SDK 구독/heartbeat를 유지하지 않는다. 복귀 시 새 관측만 사용. */
    fun startSignals() = repo.start()
    fun stopSignals() {
        runner?.pause()
        repo.stop()
        // StateFlow의 중간 Locked가 합쳐져도 ViewModel은 즉시 잠긴 상태를 관측한다.
        onSnapshot(repo.snapshot.value)
    }

    private fun onSnapshot(s: VehicleSnapshot) {
        val now = System.currentTimeMillis()
        val gate = SafetyGate.evaluate(s, policy)
        val prev = _ui.value
        val session = SessionReducer.onGate(prev.session, gate, s, now)

        // 잠금 진입/해제에 따른 미션 일시정지·재개
        if (gate is GateState.Locked && prev.gate is GateState.Ready) runner?.pause()
        if (gate is GateState.Ready && prev.gate is GateState.Locked && session.phase == Phase.PREPARING) runner?.resume(s, now)

        var runnerState = prev.runner
        var pedal = prev.pedal
        if (session.phase == Phase.PREPARING && gate is GateState.Ready) {
            if (prev.step == PrepStep.WARMUP) runnerState = runner?.update(s, now)
            if (prev.step == PrepStep.PEDAL) pedal = pedalJudge?.update(s, now)
        }
        val summary = if (session.phase == Phase.REVIEW) summarize(session.tripStart, session.tripEnd) else prev.summary

        _ui.value = prev.copy(session = session, gate = gate, snapshot = s, runner = runnerState, pedal = pedal, summary = summary)
        // A01 진단: 명시적 opt-in debug 에뮬레이터에서 앱이 소비한 값/상태만 기록한다.
        if (BuildConfig.DEBUG && BuildConfig.PLEOS_SIGNALS_ENABLED && !BuildConfig.USE_MOCK &&
            s.driverBeltBuckled.source == com.chiilkkabwa.app.core.signal.SignalSource.EMULATOR) {
            val trace = "gate=$gate gear=${s.gear.status}/${s.gear.value} " +
                "parkingBrake=${s.parkingBrakeOn.status}/${s.parkingBrakeOn.value} " +
                "ux=${s.uxRestricted.status}/${s.uxRestricted.value} " +
                "belt=${s.driverBeltBuckled.status}/${s.driverBeltBuckled.value} " +
                "brake=${s.brakePressed.status}/${s.brakePressed.value} " +
                "turn=${s.turnSignal.status}/${s.turnSignal.value} source=EMULATOR"
            if (trace != lastSignalTrace) { Log.d("A01SignalTrace", trace); lastSignalTrace = trace }
        }
    }

    // ── 사용자 행동 ─────────────────────────────────────────
    fun startPractice() = _ui.update { it.copy(session = SessionReducer.onStartPressed(it.session), step = PrepStep.GOAL) }

    fun chooseGoal(goal: String, mentor: Boolean) {
        val now = System.currentTimeMillis()
        val s = repo.snapshot.value
        runner = MissionRunner(MissionCatalog.warmupRequired() + MissionCatalog.warmupOptional()).also { it.start(s, now) }
        _ui.update { it.copy(goal = goal, mentorMode = mentor, step = PrepStep.WARMUP, runner = runner?.state()) }
    }

    /** 신호가 없을 때: 건너뛰기(조건부 미션) */
    fun skipMission() { runner?.let { it.skipCurrent(repo.snapshot.value, System.currentTimeMillis()); _ui.update { u -> u.copy(runner = it.state()) } } }

    /** 워밍업 종료 → 결과를 이력에 반영(배지 판정) 후 가상 주차로 */
    fun toParking() {
        val results = _ui.value.runner?.results ?: emptyList()
        updateProfile { p -> p.copy(history = p.history.copy(missionResults = p.history.missionResults + results)) }
        _ui.update { it.copy(step = PrepStep.PARKING_SIM) }
    }

    fun parkingDone() {
        updateProfile { p -> p.copy(history = p.history.copy(parkingSimCompleted = p.history.parkingSimCompleted + 1)) }
        // 페달 감각은 브레이크 페달 압력으로 한다(PRD F5: 가속 페달은 '차가 구동되지 않는 조건' 확인 전 실차에서 열지 않음)
        val pedalAvailable = repo.snapshot.value.brakePedalPct.status == SignalStatus.OK
        if (pedalAvailable) {
            pedalJudge = PedalHoldJudge({ it.brakePedalPct }).also { it.start(repo.snapshot.value, System.currentTimeMillis()) }
            _ui.update { it.copy(step = PrepStep.PEDAL, pedal = null) }
        } else _ui.update { it.copy(step = if (it.mentorMode) PrepStep.PACT else PrepStep.READY_TO_GO) }
    }

    fun pedalDone() = _ui.update { it.copy(step = if (it.mentorMode) PrepStep.PACT else PrepStep.READY_TO_GO) }

    fun togglePact(item: String) = _ui.update {
        val next = if (item in it.pact) it.pact - item else if (it.pact.size < 3) it.pact + item else it.pact
        it.copy(pact = next)
    }

    fun confirmPact() {
        updateProfile { p -> p.copy(history = p.history.copy(pactsMade = p.history.pactsMade + 1)) }
        _ui.update { it.copy(step = PrepStep.READY_TO_GO) }
    }

    fun saveReview(learner: String?, mentor: String?, nextGoal: String?, graduate: Boolean) {
        val sum = _ui.value.summary
        updateProfile { p ->
            p.copy(
                nextGoal = nextGoal,
                history = p.history.copy(reviewsSaved = p.history.reviewsSaved + 1, graduated = p.history.graduated || graduate),
                reviews = p.reviews + ReviewRecord(System.currentTimeMillis(), sum?.durationMin ?: 0, sum?.distanceKm, learner, mentor, nextGoal),
            )
        }
        _ui.update { it.copy(session = SessionReducer.onReviewDone(it.session), step = PrepStep.GOAL, pact = emptySet(), summary = null) }
    }

    fun dismissBadges() = _ui.update { it.copy(newBadges = emptyList()) }

    private fun updateProfile(f: (Profile) -> Profile) {
        val now = System.currentTimeMillis()
        val p0 = f(_ui.value.profile)
        val warmIds = MissionCatalog.warmupRequired().map { it.id }.toSet()
        val earned = AchievementRules.newlyEarned(p0.history, warmIds, now)
        val p = p0.copy(history = p0.history.copy(badges = p0.history.badges + earned))
        store.save(p)
        _ui.update { it.copy(profile = p, newBadges = it.newBadges + earned) }
    }

    override fun onCleared() { repo.stop() }
}
