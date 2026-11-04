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
import com.chiilkkabwa.app.core.mission.ParkingSim
import com.chiilkkabwa.app.core.mission.ParkingSlot
import com.chiilkkabwa.app.core.mission.Progress
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
import kotlin.math.PI

/**
 * 화면 라우터. 규칙: 한 화면 한 행동, 큰 버튼, 텍스트 최소.
 * 주행 잠금(DRIVING_LOCK)은 어떤 화면보다 우선한다(정적 화면, 애니메이션·소리 없음).
 */
@Composable
fun AppRoot(vm: ChiilViewModel, ui: UiState) {
    Box(Modifier.fillMaxSize().background(Tokens.Bg).padding(24.dp)) {
        when (ui.session.phase) {
            Phase.DRIVING_LOCK -> DrivingLockScreen()
            Phase.CHECKING -> GateScreen(ui, onStart = {})
            Phase.READY -> GateScreen(ui, onStart = vm::startPractice)
            Phase.PREPARING -> if (ui.gate is GateState.Locked) GateScreen(ui, onStart = {}) else PrepRouter(vm, ui)
            Phase.REVIEW -> ReviewScreen(ui, vm::saveReview)
        }
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
fun GateScreen(ui: UiState, onStart: () -> Unit) {
    val s = ui.snapshot
    Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
        Title("연습 준비를 확인할게요")
        CheckRow("기어 P", s.gear.isUsable && s.gear.value == Gear.PARK)
        CheckRow("주차 브레이크", s.parkingBrakeOn.isUsable && s.parkingBrakeOn.value == true)
        CheckRow("주행 제한 없음", s.uxRestricted.isUsable && s.uxRestricted.value == false)
        (ui.gate as? GateState.Locked)?.let { Body(it.reason.message) }
        BigButton("연습 시작", enabled = ui.gate is GateState.Ready && ui.session.phase == Phase.READY, onClick = onStart)
        ui.profile.nextGoal?.let { Body("지난번 목표: $it — 이어서 해 볼까요?") }
    }
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
        r.results.forEach { Body("● ${it.id.name.lowercase()} 완료", Tokens.SuccessText) }
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
            Body("워밍업 완료! 출발 준비가 됐어요", Tokens.SuccessText)
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
        Body(if (parked) "칸 안에 들어왔어요! 기준점을 기억해 둬요" else "뒷바퀴가 옆 차 범퍼를 지날 때 핸들을 끝까지 돌려 보세요",
            if (parked) Tokens.SuccessText else Tokens.TextSub)
        BigButton(if (parked) "다음" else "그만하고 다음", onClick = onDone)
    }
}

// ── A-4 페달 감각(조건부) ─────────────────────────────────
@Composable
fun PedalScreen(ui: UiState, onDone: () -> Unit) {
    val pct = ui.snapshot.accelPedalPct.value ?: 0f
    Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) { Title("살살 밟기"); Spacer(Modifier.width(12.dp)); SourceBadge(ui.snapshot.accelPedalPct.source) }
        Body("초록 칸 안에서 2초 버티기 · 지금 ${pct.toInt()}%")
        Canvas(Modifier.fillMaxWidth().height(36.dp)) {
            drawRect(Tokens.Surface2, size = size)
            drawRect(Tokens.Primary, size = Size(size.width * pct / 100f, size.height))
            drawRect(Tokens.SuccessText, topLeft = Offset(size.width * 0.20f, 0f), size = Size(size.width * 0.15f, size.height), style = Stroke(4f))
        }
        when (val p = ui.pedal) {
            is Progress.Done -> { Body(p.praise, Tokens.SuccessText); BigButton("다음", onClick = onDone) }
            is Progress.Unavailable -> { Body("이 차에서는 페달 값을 받을 수 없어요"); BigButton("건너뛰기", ghost = true, onClick = onDone) }
            else -> Body("이 값은 연습용이며 실제 운전 기준이 아니에요")
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
        Title("수고했어요!")
        sum?.let { Body("오늘 연습 ${it.durationMin}분" + (it.distanceKm?.let { d -> " · %.1fkm".format(d) } ?: "") + if (!it.durationFromVehicle) " (앱 기준 시간)" else "") }
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
            Dbg("가속 27%") { m.setAccelPct(27f) }; Dbg("가속 0") { m.setAccelPct(0f) }
            Dbg("주행 +3.2km/14분") { m.addTrip(3.2f, 840) }
            Dbg("브레이크 미지원") { m.breakSignal(SignalStatus.UNSUPPORTED) }
        }
    }
}

@Composable private fun Dbg(t: String, f: () -> Unit) =
    Text(t, color = Color.White, modifier = Modifier.clip(RoundedCornerShape(6.dp)).background(Color(0xFF333333)).clickable(onClick = f).padding(8.dp))
