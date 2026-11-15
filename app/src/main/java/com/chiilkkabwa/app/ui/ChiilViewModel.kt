package com.chiilkkabwa.app.ui

import android.app.Application
import android.os.SystemClock
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
import com.chiilkkabwa.app.core.review.PactOptions
import com.chiilkkabwa.app.core.session.Phase
import com.chiilkkabwa.app.core.session.PracticeSummary
import com.chiilkkabwa.app.core.session.SessionReducer
import com.chiilkkabwa.app.core.session.SessionState
import com.chiilkkabwa.app.core.session.summarize
import com.chiilkkabwa.app.core.signal.SignalStatus
import com.chiilkkabwa.app.core.signal.VehicleSnapshot
import com.chiilkkabwa.app.core.voice.CoachingOffer
import com.chiilkkabwa.app.core.voice.MockDriveCoach
import com.chiilkkabwa.app.core.voice.PlaybackOutcome
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
    val profiles: List<Profile> = emptyList(),
    val timeline: com.chiilkkabwa.app.core.review.DriveTimeline = com.chiilkkabwa.app.core.review.DriveTimeline(),
    val newBadges: List<EarnedBadge> = emptyList(),
    val mockMode: Boolean = BuildConfig.USE_MOCK,
    /** 기기 설정(웹 '설정'과 같은 뜻) */
    val voiceOn: Boolean? = null,
    val voiceCap: Int = 5,
    val onboardingSeen: Boolean = true,
    /** 이번 주행에서 실제로 말한 사건 id — 복기 시간표의 '말했어요' 표시 */
    val spokenEventIds: Set<String> = emptySet(),
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

    private val _ui = MutableStateFlow(store.profiles().let {
        UiState(profile = PracticeStore.pick(it, store.activeId()), profiles = it,
            voiceOn = store.voiceOn(), voiceCap = store.voiceCap(), onboardingSeen = store.onboardingSeen())
    })
    val ui: StateFlow<UiState> = _ui.asStateFlow()

    private var runner: MissionRunner? = null
    private var pedalJudge: MissionJudge? = null
    private var lastSignalTrace: String? = null
    private val timelineRecorder = com.chiilkkabwa.app.core.review.DriveTimelineRecorder()

    // B02 모의 음성 길잡이: mock 플레이버의 주행 잠금 동안만. Activity가 이 값을 보고 소리를 내고 coachDone으로 결과를 돌려준다.
    private var coach: MockDriveCoach? = null
    private var spoken: Set<String> = emptySet()
    private val _coachOffer = MutableStateFlow<CoachingOffer?>(null)
    val coachOffer: StateFlow<CoachingOffer?> = _coachOffer.asStateFlow()

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

        // C01: 주행 잠금 동안 들어온 속도 관측만 시간표에 모은다. 새 주행마다 비우고, 다시 주차하면 복기에 넘긴다
        if (session.phase == Phase.DRIVING_LOCK) {
            if (prev.session.phase != Phase.DRIVING_LOCK) {
                timelineRecorder.start()
                if (BuildConfig.USE_MOCK) coach = MockDriveCoach(prev.goal, prev.mentorMode, SystemClock.elapsedRealtime(), prev.voiceOn, prev.voiceCap)
            }
            val event = timelineRecorder.accept(s.speed, SystemClock.elapsedRealtimeNanos())
            if (event != null) coach?.let { c ->
                c.onSpeedEvent(event, SystemClock.elapsedRealtime())
                // 0.8초 비교 창이 닫힌 뒤 가장 급한 하나만 꺼낸다
                viewModelScope.launch { delay(900); _coachOffer.value = c.flush(SystemClock.elapsedRealtime()) ?: _coachOffer.value }
            }
        } else if (coach != null) {
            val c = coach!!
            spoken = timelineRecorder.snapshot().marks.map { it.eventId }.filter(c::didSpeak).toSet()
            c.stop(SystemClock.elapsedRealtime()); coach = null; _coachOffer.value = null
        }
        val timeline = if (session.phase == Phase.REVIEW && prev.session.phase == Phase.DRIVING_LOCK) timelineRecorder.snapshot() else prev.timeline

        _ui.value = prev.copy(session = session, gate = gate, snapshot = s, runner = runnerState, pedal = pedal, summary = summary, timeline = timeline,
            spokenEventIds = if (session.phase == Phase.REVIEW) spoken else emptySet())
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
        val next = if (item in it.pact) it.pact - item else if (it.pact.size < PactOptions.MAX) it.pact + item else it.pact
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
                // 기기 안에도 필요한 만큼만(S09): 최근 30개만 남긴다(내 기록 화면은 10개를 보여 줌)
                reviews = (p.reviews + ReviewRecord(System.currentTimeMillis(), sum?.durationMin ?: 0, sum?.distanceKm, learner, mentor, nextGoal)).takeLast(30),
            )
        }
        _ui.update { it.copy(session = SessionReducer.onReviewDone(it.session), step = PrepStep.GOAL, pact = emptySet(), summary = null) }
    }

    /** Activity가 재생 결과를 돌려준다. 정상 완료만 '말함'으로 센다(B02 계약). */
    fun coachDone(offer: CoachingOffer, outcome: PlaybackOutcome) {
        coach?.complete(offer, outcome, SystemClock.elapsedRealtime())
        if (_coachOffer.value === offer) _coachOffer.value = null
    }

    // ── 설정·온보딩(정차 화면에서만 연다) ──
    fun setVoiceOn(on: Boolean) { store.setVoiceOn(on); _ui.update { it.copy(voiceOn = on) } }
    fun setVoiceCap(n: Int) { store.setVoiceCap(n); _ui.update { it.copy(voiceCap = store.voiceCap()) } }
    fun markOnboardingSeen() { store.setOnboardingSeen(); _ui.update { it.copy(onboardingSeen = true) } }

    fun dismissBadges() = _ui.update { it.copy(newBadges = emptyList()) }

    // ── C02 프로필(가족 공유 차량) ──
    // 연습·복기·주행 중에는 바꾸지 않는다: 기록이 다른 사람에게 섞이지 않게. 안전 확인 화면에서만.
    private fun profileEditable() = _ui.value.session.phase.let { it == Phase.CHECKING || it == Phase.READY }

    private fun showProfile(p: Profile) {
        store.setActive(p.id)
        _ui.update { it.copy(profile = p, profiles = store.profiles(), newBadges = emptyList()) }
    }

    fun switchProfile(id: String) {
        if (!profileEditable()) return
        store.profiles().firstOrNull { it.id == id }?.let(::showProfile)
    }

    fun addProfile() {
        if (!profileEditable()) return
        val p = PracticeStore.newProfile(store.profiles(), System.currentTimeMillis()) ?: return
        store.save(p); showProfile(p)
    }

    /** 지금 프로필의 기록을 이 기기에서 지운다. 남은 프로필이 없으면 빈 '나'로 시작 */
    fun deleteCurrentProfile() {
        if (!profileEditable()) return
        store.delete(_ui.value.profile.id)
        showProfile(PracticeStore.pick(store.profiles(), null))
    }

    private fun updateProfile(f: (Profile) -> Profile) {
        val now = System.currentTimeMillis()
        val p0 = f(_ui.value.profile)
        val warmIds = MissionCatalog.warmupRequired().map { it.id }.toSet()
        val earned = AchievementRules.newlyEarned(p0.history, warmIds, now)
        val p = p0.copy(history = p0.history.copy(badges = p0.history.badges + earned))
        store.save(p); store.setActive(p.id)
        _ui.update { it.copy(profile = p, profiles = store.profiles(), newBadges = it.newBadges + earned) }
    }

    override fun onCleared() { repo.stop() }
}
