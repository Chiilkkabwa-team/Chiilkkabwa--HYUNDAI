package com.chiilkkabwa.app.ui.screens

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.chiilkkabwa.app.core.mission.CarPose
import com.chiilkkabwa.app.data.store.PracticeStore
import com.chiilkkabwa.app.core.mission.ParkingSim
import com.chiilkkabwa.app.core.mission.ParkingSlot
import com.chiilkkabwa.app.core.mission.Progress
import com.chiilkkabwa.app.core.mission.MissionCatalog
import com.chiilkkabwa.app.core.review.Goals
import com.chiilkkabwa.app.core.review.PactOptions
import com.chiilkkabwa.app.core.review.ReviewChips
import com.chiilkkabwa.app.core.safety.GateState
import com.chiilkkabwa.app.core.session.Phase
import com.chiilkkabwa.app.core.signal.CarDimensions
import com.chiilkkabwa.app.core.signal.Gear
import com.chiilkkabwa.app.core.signal.SignalSource
import com.chiilkkabwa.app.core.signal.SignalStatus
import com.chiilkkabwa.app.core.signal.TurnSignalState
import com.chiilkkabwa.app.ui.ChiilViewModel
import com.chiilkkabwa.app.ui.PrepStep
import com.chiilkkabwa.app.ui.UiState
import com.chiilkkabwa.app.ui.theme.Tokens
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.math.PI

/**
 * 화면 라우터. 규칙: 한 화면 한 행동, 큰 버튼, 텍스트 최소.
 * 주행 잠금(DRIVING_LOCK)은 어떤 화면보다 우선한다(정적 화면, 애니메이션 없음).
 * 소리는 mock 플레이버의 모의 음성 길잡이만(MainActivity) — 화면은 그대로다.
 */
@Composable
fun AppRoot(
    vm: ChiilViewModel,
    ui: UiState,
    onVoicePreview: (() -> Unit)? = null,
    voicePreviewStatus: String? = null,
) {
    // 시트(내 기록·설정·소개·사용법)는 정차 확인 화면에서만. 그 밖의 단계가 되면 바로 닫는다(S04)
    val parkedMenu = ui.session.phase == Phase.CHECKING || ui.session.phase == Phase.READY
    var sheet by remember { mutableStateOf<Sheet?>(null) }
    LaunchedEffect(parkedMenu) { if (!parkedMenu) sheet = null }
    // 처음 실행이면 사용법을 한 번 보여 준다(닫으면 다시 안 뜸)
    val shown = if (!parkedMenu) null else sheet ?: if (!ui.onboardingSeen) Sheet.INTRO else null
    Box(Modifier.fillMaxSize().background(Tokens.Bg).padding(24.dp)) {
        when (ui.session.phase) {
            Phase.DRIVING_LOCK -> DrivingLockScreen()
            Phase.CHECKING -> GateScreen(ui, onStart = {}, profiles = vm, onOpen = { sheet = it })
            Phase.READY -> GateScreen(ui, vm::startPractice, onVoicePreview, voicePreviewStatus, profiles = vm, onOpen = { sheet = it })
            Phase.PREPARING -> if (ui.gate is GateState.Locked) GateScreen(ui, onStart = {}) else PrepRouter(vm, ui)
            // 복기 중 주차 확인이 깨지면(E3) 복기 대신 확인 화면을 보여 준다. 기록은 남아 있다
            Phase.REVIEW -> if (ui.session.lockReason != null) GateScreen(ui, onStart = {}) else ReviewScreen(ui, vm::saveReview)
        }
        shown?.let { s -> SheetHost(s, ui, vm) { if (s == Sheet.INTRO) vm.markOnboardingSeen(); sheet = null } }
        if (ui.newBadges.isNotEmpty() && ui.session.phase != Phase.DRIVING_LOCK) BadgeToast(ui) { vm.dismissBadges() }
        if (ui.mockMode) DebugPanel(vm, ui, Modifier.align(Alignment.BottomEnd))
    }
}

@Composable
private fun PrepRouter(vm: ChiilViewModel, ui: UiState) = when (ui.step) {
    PrepStep.GOAL -> GoalScreen(ui, vm::chooseGoal)
    PrepStep.WARMUP -> WarmupScreen(ui, vm::skipMission, vm::toParking)
    PrepStep.PARKING_SIM -> ParkingSimScreen(ui, vm::parkingDone)
    PrepStep.PEDAL -> PedalScreen(ui, vm::pedalDone)
    PrepStep.PACT -> PactScreen(ui, vm::togglePact, vm::confirmPact)
    PrepStep.READY_TO_GO -> ReadyToGoScreen(ui)
}

// ── 공통 컴포넌트 ─────────────────────────────────────────
@Composable fun Title(t: String) = Text(t, color = Tokens.TextMain, fontSize = Tokens.Title, fontWeight = FontWeight.Bold)
@Composable fun Body(t: String, c: Color = Tokens.TextSub) = Text(t, color = c, fontSize = Tokens.Body)

@Composable
fun BigButton(text: String, enabled: Boolean = true, ghost: Boolean = false, onClick: () -> Unit) {
    Box(
        Modifier.fillMaxWidth().heightIn(min = Tokens.TouchMin).clip(RoundedCornerShape(16.dp))
            .background(if (!enabled) Tokens.Surface2 else if (ghost) Tokens.Surface2 else Tokens.Primary)
            .clickable(enabled = enabled, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) { Text(text, color = Tokens.TextMain, fontSize = Tokens.Body, fontWeight = FontWeight.Bold) }
}

@Composable
fun SourceBadge(src: SignalSource?) {
    val (label, bg) = when (src) {
        SignalSource.VEHICLE -> "실차 신호" to Tokens.Success
        SignalSource.EMULATOR -> "에뮬레이터 신호" to Color(0xFF3B4656)
        SignalSource.MOCK, null -> "모의" to Tokens.Warn
    }
    Text(label, color = Tokens.TextMain, fontSize = Tokens.Caption, modifier = Modifier.clip(RoundedCornerShape(99.dp)).background(bg).padding(horizontal = 12.dp, vertical = 4.dp))
}

// ── A-1 안전 게이트 ───────────────────────────────────────
@Composable
fun GateScreen(
    ui: UiState,
    onStart: () -> Unit,
    onVoicePreview: (() -> Unit)? = null,
    voicePreviewStatus: String? = null,
    profiles: ChiilViewModel? = null,   // 안전 확인 단계에서만 넘긴다(복기 잠금 화면에서는 null → 프로필 줄 숨김)
    onOpen: ((Sheet) -> Unit)? = null,
) {
    val s = ui.snapshot
    Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
        profiles?.let { ProfileRow(ui, it) }
        onOpen?.let { MenuRow(it) }
        Title("연습 준비를 확인할게요")
        CheckRow("기어 P", s.gear.isUsable && s.gear.value == Gear.PARK)
        CheckRow("주차 브레이크", s.parkingBrakeOn.isUsable && s.parkingBrakeOn.value == true)
        CheckRow("주행 제한 없음", s.uxRestricted.isUsable && s.uxRestricted.value == false)
        (ui.gate as? GateState.Locked)?.let { Body(it.reason.message) }
        BigButton("연습 시작", enabled = ui.gate is GateState.Ready && ui.session.phase == Phase.READY, onClick = onStart)
        if (onVoicePreview != null && ui.gate is GateState.Ready) {
            BigButton("음성 안내 듣기 (모의)", ghost = true, onClick = onVoicePreview)
            voicePreviewStatus?.let { Body(it) }
        }
        ui.profile.nextGoal?.let { Body("지난번 목표: $it — 이어서 해 볼까요?") }
    }
}

/** C02: 누구의 연습인지. 칩으로 전환·추가, 지우기는 두 번 눌러 확인 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun ProfileRow(ui: UiState, vm: ChiilViewModel) {
    var confirm by remember(ui.profile.id) { mutableStateOf(false) }
    FlowRow(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        (ui.profiles.ifEmpty { listOf(ui.profile) }).forEach { p -> Chip(p.displayName, p.id == ui.profile.id) { vm.switchProfile(p.id) } }
        if (ui.profiles.size < PracticeStore.ALIASES.size) Chip("+ 추가", false) { vm.addProfile() }
        Chip(if (confirm) "한 번 더 누르면 ${ui.profile.displayName} 기록 삭제" else "기록 지우기", false) {
            if (confirm) vm.deleteCurrentProfile() else confirm = true
        }
    }
    Body("기록은 이 기기에만, 사람마다 따로 저장돼요")
}

/** 정차 화면 전용 메뉴(웹 머리말의 내 기록·설정·소개·사용법) */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun MenuRow(onOpen: (Sheet) -> Unit) =
    FlowRow(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Chip("내 기록", false) { onOpen(Sheet.HISTORY) }
        Chip("설정", false) { onOpen(Sheet.SETTINGS) }
        Chip("소개", false) { onOpen(Sheet.ABOUT) }
        Chip("사용법", false) { onOpen(Sheet.INTRO) }
    }

@Composable
private fun CheckRow(label: String, ok: Boolean) = Row(verticalAlignment = Alignment.CenterVertically) {
    Box(Modifier.size(22.dp).clip(RoundedCornerShape(99.dp)).background(if (ok) Tokens.Success else Tokens.Surface2))
    Spacer(Modifier.width(12.dp)); Body(label, Tokens.TextMain); Spacer(Modifier.width(12.dp)); Body(if (ok) "확인" else "대기")
}

// ── A-2 목표 고르기 ────────────────────────────────────────
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun GoalScreen(ui: UiState, onChoose: (String, Boolean) -> Unit) {
    var goal by remember { mutableStateOf<String?>(null) }
    val passenger = ui.snapshot.passengerSeated.isUsable && ui.snapshot.passengerSeated.value == true
    Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
        Title("오늘 뭐가 제일 걱정돼요?")
        FlowRow(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Goals.all.forEach { g -> Chip(g, g == goal) { goal = g } }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Box(Modifier.weight(1f)) { BigButton("혼자 연습", enabled = goal != null, ghost = passenger) { onChoose(goal!!, false) } }
            Box(Modifier.weight(1f)) { BigButton("멘토와 함께", enabled = goal != null, ghost = !passenger) { onChoose(goal!!, true) } }
        }
        if (passenger) Body("조수석에 누가 앉아 있네요. 함께 연습해요")
    }
}

@Composable
fun Chip(text: String, selected: Boolean, onClick: () -> Unit) =
    Text(text, color = Tokens.TextMain, fontSize = Tokens.Body,
        modifier = Modifier.clip(RoundedCornerShape(12.dp)).background(if (selected) Tokens.Primary else Tokens.Surface2)
            .clickable(onClick = onClick).padding(horizontal = 18.dp, vertical = 14.dp))

// ── A-3 워밍업 미션 ───────────────────────────────────────
@Composable
fun WarmupScreen(ui: UiState, onSkip: () -> Unit, onNext: () -> Unit) {
    val r = ui.runner ?: return
    Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Title("운전석 워밍업 ${minOf(r.index + 1, r.total)}/${r.total}"); Spacer(Modifier.width(16.dp))
            SourceBadge(r.results.lastOrNull()?.source ?: ui.snapshot.brakePressed.source)
        }
        // 사용자에게 영어 내부 이름(seat_belt)을 보이지 않는다
        val titles = remember { (MissionCatalog.warmupRequired() + MissionCatalog.warmupOptional()).associate { it.id to it.title } }
        r.results.forEach { Body("● ${titles[it.id] ?: "미션"} 완료", Tokens.SuccessText) }
        if (!r.finished) {
            Body(r.currentTitle ?: "", Tokens.TextMain)
            when (val p = r.progress) {
                is Progress.InProgress -> Body(p.hint)
                is Progress.Done -> Body(p.praise, Tokens.SuccessText)
                is Progress.Unavailable -> {
                    Body(when (p.status) {
                        SignalStatus.PERMISSION_DENIED -> "이 미션에 필요한 권한이 없어요. 건너뛰고 화면 연습으로 할게요"
                        SignalStatus.DISCONNECTED -> "차량 상태를 확인할 수 없어요(연결 끊김)"
                        else -> "이 차에서는 이 신호를 지원하지 않아요"
                    })
                    BigButton("건너뛰기", ghost = true, onClick = onSkip)
                }
                null -> Body("조작해 보세요")
            }
        } else {
            Body("워밍업 완료. 출발 준비가 됐어요", Tokens.SuccessText)
            BigButton("가상 주차 연습으로", onClick = onNext)
        }
    }
}

// ── A-5 가상 주차 ─────────────────────────────────────────
@Composable
fun ParkingSimScreen(ui: UiState, onDone: () -> Unit) {
    val dims = ui.snapshot.carDimensions.value ?: CarDimensions.COMPACT_SEDAN_DEFAULT
    val sim = remember(dims) { ParkingSim(dims) }
    val slot = remember { ParkingSlot(cx = 0.0, cy = 0.0) }
    var pose by remember { mutableStateOf(CarPose(3.2, -5.5, PI / 2)) }   // 칸 오른쪽 위, 오른쪽을 향함
    var speed by remember { mutableFloatStateOf(0f) }
    var sliderDeg by remember { mutableFloatStateOf(0f) }
    val realSteer = ui.snapshot.steeringDeg.takeIf { it.isUsable }?.value
    val steer = realSteer ?: sliderDeg
    val parked = sim.isParked(pose, slot)

    LaunchedEffect(speed, steer) {
        while (speed != 0f) { delay(50); pose = sim.step(pose, steer.toDouble(), speed.toDouble(), 0.05) }
    }
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Title("내 차로 하는 가상 주차"); Spacer(Modifier.width(12.dp))
            SourceBadge(SignalSource.MOCK) // 화면 속 연습은 항상 '가상'
        }
        Body("차 크기 ${dims.lengthMm / 1000.0}m × ${dims.widthMm / 1000.0}m" + if (dims.isDefault) " (기본값)" else "")
        Row(Modifier.fillMaxWidth().height(280.dp)) {
            Canvas(Modifier.weight(1f).fillMaxSize().background(Tokens.Surface2)) {
                val ppm = size.minDimension / 14f // pixels per meter
                val o = Offset(size.width / 2, size.height * 0.62f)
                fun px(x: Double, y: Double) = Offset(o.x + (x * ppm).toFloat(), o.y + (y * ppm).toFloat())
                // 주차 칸과 양옆 차
                val sw = (slot.widthM * ppm).toFloat(); val sl = (slot.lengthM * ppm).toFloat()
                drawRect(Color(0xFF7EE2A8), topLeft = px(-slot.widthM / 2, -slot.lengthM / 2), size = Size(sw, sl), style = Stroke(3f))
                drawRect(Color(0xFF3B4656), topLeft = px(-slot.widthM * 1.5 + 0.1, -2.3), size = Size(sw - 8, (4.6 * ppm).toFloat()))
                drawRect(Color(0xFF3B4656), topLeft = px(slot.widthM * 0.5 + 0.1, -2.3), size = Size(sw - 8, (4.6 * ppm).toFloat()))
                // 내 차
                val c = px(pose.x, pose.y)
                rotate(degrees = (pose.heading * 180 / PI).toFloat(), pivot = c) {
                    drawRect(if (parked) Tokens.Success else Tokens.Primary,
                        topLeft = Offset(c.x - (sim.widthM * ppm / 2).toFloat(), c.y - (sim.lengthM * ppm * 0.78).toFloat()),
                        size = Size((sim.widthM * ppm).toFloat(), (sim.lengthM * ppm).toFloat()))
                }
            }
            Column(Modifier.width(220.dp).padding(start = 12.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Body(if (realSteer != null) "실제 핸들 ${steer.toInt()}°" else "핸들(화면) ${steer.toInt()}°")
                if (realSteer == null) Slider(value = sliderDeg, onValueChange = { sliderDeg = it }, valueRange = -540f..540f)
                BigButton("후진", ghost = speed >= 0) { speed = if (speed < 0) 0f else -0.8f }
                BigButton("전진", ghost = speed <= 0) { speed = if (speed > 0) 0f else 0.8f }
            }
        }
        Body(if (parked) "칸 안에 들어왔어요. 기준점을 기억해 둬요" else "뒷바퀴가 옆 차 범퍼를 지날 때 핸들을 끝까지 돌려 보세요",
            if (parked) Tokens.SuccessText else Tokens.TextSub)
        BigButton(if (parked) "다음" else "그만하고 다음", onClick = onDone)
    }
}

// ── A-4 페달 감각(조건부) ─────────────────────────────────
@Composable
fun PedalScreen(ui: UiState, onDone: () -> Unit) {
    val pct = ui.snapshot.brakePedalPct.value ?: 0f   // 브레이크 페달 압력(가속 페달은 실차에서 열지 않음)
    Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) { Title("살살 밟기"); Spacer(Modifier.width(12.dp)); SourceBadge(ui.snapshot.brakePedalPct.source) }
        Body("초록 칸 안에서 2초 버티기 · 지금 ${pct.toInt()}%")
        Canvas(Modifier.fillMaxWidth().height(36.dp)) {
            drawRect(Tokens.Surface2, size = size)
            drawRect(Tokens.Primary, size = Size(size.width * pct / 100f, size.height))
            drawRect(Tokens.SuccessText, topLeft = Offset(size.width * 0.20f, 0f), size = Size(size.width * 0.15f, size.height), style = Stroke(4f))
        }
        when (val p = ui.pedal) {
            is Progress.Done -> { Body(p.praise, Tokens.SuccessText); BigButton("다음", onClick = onDone) }
            is Progress.Unavailable -> { Body("이 차에서는 페달 값을 받을 수 없어요"); BigButton("건너뛰기", ghost = true, onClick = onDone) }
            else -> Body("브레이크 페달로 해요. 이 값은 연습용이며 실제 운전 기준이 아니에요")
        }
    }
}

// ── A-6 멘토 약속 ─────────────────────────────────────────
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun PactScreen(ui: UiState, onToggle: (String) -> Unit, onConfirm: () -> Unit) = Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
    Title("오늘의 약속 (최대 ${PactOptions.MAX}개)")
    Body("오늘 목표: ${ui.goal ?: "-"}")
    FlowRow(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        PactOptions.all.forEach { Chip(it, it in ui.pact) { onToggle(it) } }
    }
    BigButton("둘 다 약속했어요", enabled = ui.pact.isNotEmpty(), onClick = onConfirm)
}

@Composable
fun ReadyToGoScreen(ui: UiState) = Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
    Title("출발 준비 완료")
    Body("기어를 D로 옮기면 화면은 쉬어요. 연습은 함께 탄 사람과 하세요.")
    if (!ui.mentorMode) Body("혼자라면 주차장 안 연습만 이어 가도 좋아요. 실제 도로 연습은 동승자와 함께 하세요.")
}

// ── A-7 주행 중 잠금(정적) ─────────────────────────────────
@Composable
fun DrivingLockScreen() = Column(Modifier.fillMaxSize(), verticalArrangement = Arrangement.Center, horizontalAlignment = Alignment.CenterHorizontally) {
    Title("연습 중")
    Body("화면은 잠시 쉴게요. 도로만 보세요.")
    Spacer(Modifier.height(8.dp))
    Body("주차하면 오늘 연습을 함께 돌아봐요")
}

// ── A-8 주차 후 복기 ─────────────────────────────────────
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun ReviewScreen(ui: UiState, onSave: (String?, String?, String?, Boolean) -> Unit) {
    var learner by remember { mutableStateOf<String?>(null) }
    var mentor by remember { mutableStateOf<String?>(null) }
    var next by remember { mutableStateOf<String?>(null) }
    val sum = ui.summary
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Title("수고했어요")
        sum?.let { Body("오늘 연습 ${it.durationMin}분" + (it.distanceKm?.let { d -> " · %.1fkm".format(d) } ?: "") + if (!it.durationFromVehicle) " (앱 기준 시간)" else "") }
        TimelineSection(ui.timeline, ui.spokenEventIds)
        Body("오늘 어땠어요?", Tokens.TextMain)
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) { ReviewChips.learner.forEach { Chip(it, it == learner) { learner = it } } }
        if (ui.mentorMode) {
            Body("멘토 한마디", Tokens.TextMain)
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) { ReviewChips.mentor.forEach { Chip(it, it == mentor) { mentor = it } } }
        }
        Body("다음 목표 하나", Tokens.TextMain)
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) { ReviewChips.nextGoals.forEach { Chip(it, it == next) { next = it } } }
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Box(Modifier.weight(1f)) { BigButton("저장하고 다음 연습") { onSave(learner, mentor, next, false) } }
            Box(Modifier.weight(1f)) { BigButton("혼자 운전 시작할래요", ghost = true) { onSave(learner, mentor, next, true) } }
        }
    }
}

/** C01: 주차 후에만 보이는 정적 속도 시간표. 사건 점 + 글 목록(그래프를 못 보는 사람도 같은 내용을 읽는다) */
@Composable
private fun TimelineSection(tl: com.chiilkkabwa.app.core.review.DriveTimeline, spoken: Set<String> = emptySet()) {
    if (!tl.hasData) {
        Body("이번 주행의 속도 기록은 없어요. 속도 신호가 꺼져 있거나 받지 못했어요", Tokens.TextSub)
        return
    }
    val maxT = tl.points.last().tSec.coerceAtLeast(1f)
    val maxK = (tl.points.maxOf { it.kmh }.coerceAtLeast(20f) / 10f).let { kotlin.math.ceil(it) * 10f }
    Body("속도 시간표 · ${tl.sources.joinToString { it.label }}", Tokens.TextMain)
    Canvas(Modifier.fillMaxWidth().height(140.dp).clip(RoundedCornerShape(12.dp)).background(Tokens.Surface)) {
        val pad = 12.dp.toPx()
        fun at(t: Float, k: Float) = Offset(pad + (size.width - 2 * pad) * t / maxT, size.height - pad - (size.height - 2 * pad) * k / maxK)
        tl.points.zipWithNext { a, b -> drawLine(Tokens.Primary, at(a.tSec, a.kmh), at(b.tSec, b.kmh), strokeWidth = 3.dp.toPx()) }
        tl.marks.forEach { drawCircle(if (it.type == com.chiilkkabwa.app.core.event.SpeedEventType.RAPID_SPEED_DECREASE) Tokens.Lock else Tokens.Warn, 7.dp.toPx(), at(it.tSec, it.toKmh)) }
    }
    Text("최고 %.0fkm/h · 가로 %d분 %02d초".format(tl.points.maxOf { it.kmh }, maxT.toInt() / 60, maxT.toInt() % 60), color = Tokens.TextSub, fontSize = Tokens.Caption)
    if (tl.marks.isEmpty()) Body("급하게 속도가 바뀐 순간은 없었어요")
    tl.marks.take(5).forEach { m ->
        val what = if (m.type == com.chiilkkabwa.app.core.event.SpeedEventType.RAPID_SPEED_DECREASE) "속도가 빠르게 줄었어요" else "속도가 빠르게 늘었어요"
        Body("%d:%02d  %s  %.0f → %.0fkm/h".format(m.tSec.toInt() / 60, m.tSec.toInt() % 60, what, m.fromKmh, m.toKmh) +
            if (m.eventId in spoken) "  · 주행 중에 말했어요(모의)" else "")
    }
    Text("속도 변화만 본 기록이에요. 페달 조작이나 주변 상황을 판단한 것이 아니에요", color = Tokens.TextSub, fontSize = Tokens.Caption)
}

@Composable
fun BadgeToast(ui: UiState, onDismiss: () -> Unit) {
    LaunchedEffect(ui.newBadges) { delay(2000); onDismiss() }   // 정차 중 2초 이내로 짧게
    val b = ui.newBadges.first()
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.TopCenter) {
        Text("배지 · ${b.badge.title}" + if (b.mockOnly) " (모의)" else "", color = Tokens.TextMain, fontSize = Tokens.Body,
            modifier = Modifier.clip(RoundedCornerShape(99.dp)).background(Tokens.Surface2).padding(horizontal = 20.dp, vertical = 10.dp))
    }
}

// ── 디버그 패널(USE_MOCK 빌드 전용): 모의 차량 조작 ───────────────────
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun DebugPanel(vm: ChiilViewModel, ui: UiState, modifier: Modifier) {
    var open by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    val m = vm.mock; val s = ui.snapshot
    Column(modifier.width(if (open) 420.dp else 120.dp).clip(RoundedCornerShape(12.dp)).background(Color(0xCC000000)).padding(8.dp)) {
        Text(if (open) "모의 차량 ▼" else "모의 ▲", color = Color.Yellow, modifier = Modifier.clickable { open = !open })
        if (open) FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Dbg("P") { m.setGear(Gear.PARK) }; Dbg("D") { m.setGear(Gear.DRIVE) }
            Dbg("주차브레이크 ${s.parkingBrakeOn.value}") { m.setParkingBrake(s.parkingBrakeOn.value != true) }
            Dbg("벨트 ${s.driverBeltBuckled.value}") { m.setBelt(s.driverBeltBuckled.value != true) }
            Dbg("브레이크 ${s.brakePressed.value}") { m.setBrake(s.brakePressed.value != true) }
            Dbg("◀") { m.setTurn(TurnSignalState.LEFT) }; Dbg("▶") { m.setTurn(TurnSignalState.RIGHT) }; Dbg("깜빡이 끔") { m.setTurn(TurnSignalState.NONE) }
            Dbg("와이퍼") { m.setWiper(s.wiperOn.value != true) }; Dbg("전조등") { m.setLowBeam(s.lowBeamOn.value != true) }
            Dbg("핸들 0") { m.setSteering(0f) }; Dbg("핸들 -540") { m.setSteering(-540f) }; Dbg("핸들 +540") { m.setSteering(540f) }
            Dbg("브레이크 27%") { m.setBrakePct(27f) }; Dbg("브레이크 0") { m.setBrakePct(0f) }
            Dbg("주행 +3.2km/14분") { m.addTrip(3.2f, 840) }
            // C01 시간표 시험용(mock 전용): 0→50→20처럼 눌러 급가속·급감속 표본을 만든다
            listOf(0, 20, 50).forEach { k -> Dbg("속도 $k") { m.setSpeedMps(k / 3.6f) } }
            // 모의 음성 길잡이 시연용: A02 판정 구간(0.05~2초) 안에서 0→50km/h를 만든다
            Dbg("급가속(모의)") { m.setSpeedMps(0f); scope.launch { delay(1_200); m.setSpeedMps(50 / 3.6f) } }
            Dbg("브레이크 미지원") { m.breakSignal(SignalStatus.UNSUPPORTED) }
        }
    }
}

@Composable private fun Dbg(t: String, f: () -> Unit) =
    Text(t, color = Color.White, modifier = Modifier.clip(RoundedCornerShape(6.dp)).background(Color(0xFF333333)).clickable(onClick = f).padding(8.dp))
