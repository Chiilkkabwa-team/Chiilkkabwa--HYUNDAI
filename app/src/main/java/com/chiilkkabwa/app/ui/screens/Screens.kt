package com.chiilkkabwa.app.ui.screens

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
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
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.chiilkkabwa.app.core.event.SpeedEventType
import com.chiilkkabwa.app.core.mission.CarPose
import com.chiilkkabwa.app.core.mission.MissionCatalog
import com.chiilkkabwa.app.core.mission.MissionId
import com.chiilkkabwa.app.core.mission.ParkingSim
import com.chiilkkabwa.app.core.mission.ParkingSlot
import com.chiilkkabwa.app.core.mission.Progress
import com.chiilkkabwa.app.core.review.DriveTimeline
import com.chiilkkabwa.app.core.review.Goals
import com.chiilkkabwa.app.core.review.PactOptions
import com.chiilkkabwa.app.core.review.ReviewChips
import com.chiilkkabwa.app.core.safety.GateState
import com.chiilkkabwa.app.core.session.Phase
import com.chiilkkabwa.app.core.signal.CarDimensions
import com.chiilkkabwa.app.core.signal.Gear
import com.chiilkkabwa.app.core.signal.Signal
import com.chiilkkabwa.app.core.signal.SignalSource
import com.chiilkkabwa.app.core.signal.SignalStatus
import com.chiilkkabwa.app.core.signal.TurnSignalState
import com.chiilkkabwa.app.data.store.PracticeStore
import com.chiilkkabwa.app.ui.ChiilViewModel
import com.chiilkkabwa.app.ui.PrepStep
import com.chiilkkabwa.app.ui.UiState
import com.chiilkkabwa.app.ui.theme.Tokens
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.math.PI

/**
 * 화면 라우터. 규칙: 한 화면 한 행동, 큰 버튼(80dp, 간격 26dp — Pleos P01 12.5mm·4mm), 넓은 화면은 왼쪽 그림·오른쪽 조작(TwoPane).
 * 주행 잠금(DRIVING_LOCK)은 어떤 화면보다 우선한다(정적 화면, 애니메이션·스크롤 없음).
 * 소리는 mock 플레이버의 모의 음성 길잡이만(MainActivity) — 화면은 그대로다.
 * 모양은 웹 체험판(web/)과 맞춘다: 같은 마스코트·아이콘·색.
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
    ScreenFrame(
        ui.session.phase, ui.snapshot.gear.source, onOpen = if (parkedMenu) { s -> sheet = s } else null, scrollKey = ui.session.phase to ui.step,
        overlay = {
            shown?.let { s ->
                SheetHost(s, ui, onVoiceOn = vm::setVoiceOn, onVoiceCap = vm::setVoiceCap) {
                    if (s == Sheet.INTRO) vm.markOnboardingSeen(); sheet = null
                }
            }
            if (ui.newBadges.isNotEmpty() && ui.session.phase != Phase.DRIVING_LOCK) BadgeToast(ui) { vm.dismissBadges() }
            if (ui.mockMode) DebugPanel(vm, ui, Modifier.align(Alignment.BottomEnd))
        },
    ) {
        when (ui.session.phase) {
            Phase.DRIVING_LOCK -> DrivingLockScreen()
            Phase.CHECKING -> GateScreen(ui, onStart = {}, profiles = vm)
            Phase.READY -> GateScreen(ui, vm::startPractice, onVoicePreview, voicePreviewStatus, profiles = vm)
            Phase.PREPARING -> if (ui.gate is GateState.Locked) GateScreen(ui, onStart = {}) else PrepRouter(vm, ui)
            // 복기 중 주차 확인이 깨지면(E3) 복기 대신 확인 화면을 보여 준다. 기록은 남아 있다
            Phase.REVIEW -> if (ui.session.lockReason != null) GateScreen(ui, onStart = {}) else ReviewScreen(ui, vm::saveReview)
        }
    }
}

/**
 * 모든 화면의 틀(앱과 스크린샷 테스트가 같이 쓴다): 배경 → 큰 창 배율(CarScale) → 여백 →
 * 머리줄(주행 잠금 제외) → 가운데 최대 폭 내용(세로 스크롤, 주행 잠금은 스크롤 없음) → 겹쳐 그릴 것(시트·알림·모의 패널)
 */
@Composable
fun ScreenFrame(
    phase: Phase,
    source: SignalSource?,
    onOpen: ((Sheet) -> Unit)?,
    scrollKey: Any? = phase,
    overlay: @Composable BoxScope.() -> Unit = {},
    content: @Composable () -> Unit,
) = Box(Modifier.fillMaxSize().background(Tokens.Bg)) {
    CarScale {
        Box(Modifier.fillMaxSize().padding(horizontal = 32.dp, vertical = 20.dp)) {
            val lock = phase == Phase.DRIVING_LOCK
            // 화면이 바뀌면 맨 위부터 보이게(이전 화면의 스크롤 위치를 끌고 오지 않는다)
            val scroll = key(scrollKey) { rememberScrollState() }
            Column(Modifier.align(Alignment.TopCenter).widthIn(max = MaxContentWidth).fillMaxSize()) {
                if (!lock) { TopBar(phase, source, onOpen); Spacer(Modifier.height(20.dp)) }
                Box(Modifier.weight(1f).fillMaxWidth().then(if (lock) Modifier else Modifier.verticalScroll(scroll))) { content() }
            }
            overlay()
        }
    }
}

/** 머리줄(웹 머리말과 같은 역할): 앱 이름·지금 단계·신호 출처(S10), 정차 확인 중에만 메뉴(S04) */
@Composable
private fun TopBar(phase: Phase, source: SignalSource?, onOpen: ((Sheet) -> Unit)?) = BoxWithConstraints(Modifier.fillMaxWidth()) {
    val brand: @Composable () -> Unit = {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Mascot(Mood.CALM, 48.dp); Spacer(Modifier.width(10.dp))
            Text("치일까봐", color = Tokens.TextMain, fontSize = Tokens.Heading, fontWeight = FontWeight.Bold)
            Spacer(Modifier.width(16.dp))
            when (phase) {
                Phase.CHECKING -> StatusPill("정차 확인 중", Pill.CHECKING)
                Phase.READY -> StatusPill("정차 중", Pill.OK)
                Phase.PREPARING -> StatusPill("연습 준비", Pill.INFO)
                Phase.REVIEW -> StatusPill("돌아보기", Pill.INFO)
                Phase.DRIVING_LOCK -> StatusPill("연습 중", Pill.BLOCKED)
            }
            Spacer(Modifier.width(10.dp)); SourceBadge(source)
        }
    }
    val menu: @Composable () -> Unit = {
        onOpen?.let { open ->
            Row(horizontalArrangement = Arrangement.spacedBy(Tokens.TouchGap)) {
                listOf(Triple("내 기록", Ico.STAR, Sheet.HISTORY), Triple("설정", Ico.SOUND, Sheet.SETTINGS),
                    Triple("소개", Ico.INFO, Sheet.ABOUT), Triple("사용법", Ico.NOTE, Sheet.INTRO)).forEach { (t, i, s) -> MenuTile(t, i) { open(s) } }
            }
        }
    }
    if (maxWidth >= 860.dp) Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) { brand(); Spacer(Modifier.weight(1f)); menu() }
    else Column(verticalArrangement = Arrangement.spacedBy(12.dp)) { brand(); menu() }
}

/** 머리줄 메뉴 버튼: 아이콘 + 글자, 터치 80dp */
@Composable
private fun MenuTile(text: String, icon: Ico, onClick: () -> Unit) = Column(
    Modifier.widthIn(min = 92.dp).heightIn(min = Tokens.TouchMin).clip(RoundedCornerShape(18.dp)).background(Tokens.Surface)
        .border(1.5.dp, Tokens.Line, RoundedCornerShape(18.dp)).clickable(role = Role.Button, onClick = onClick).padding(horizontal = 12.dp, vertical = 8.dp),
    horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center,
) {
    LineIcon(icon, 28.dp, Tokens.PrimaryText); Spacer(Modifier.height(4.dp))
    Text(text, color = Tokens.TextMain, fontSize = Tokens.Caption, fontWeight = FontWeight.SemiBold)
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

/** 넓은 차량 화면에서 내용이 너무 넓게 퍼지지 않게 하는 최대 폭 */
val MaxContentWidth = 1240.dp

// ── 공통 컴포넌트 ─────────────────────────────────────────
@Composable fun Title(t: String) = Text(t, color = Tokens.TextMain, fontSize = Tokens.Title, fontWeight = FontWeight.Bold, lineHeight = Tokens.Title * 1.3f)
@Composable fun Body(t: String, c: Color = Tokens.TextSub) = Text(t, color = c, fontSize = Tokens.Body, lineHeight = Tokens.Body * 1.5f)

@Composable
fun BigButton(text: String, enabled: Boolean = true, ghost: Boolean = false, onClick: () -> Unit) {
    Box(
        Modifier.fillMaxWidth().heightIn(min = Tokens.TouchMin).clip(RoundedCornerShape(18.dp))
            .background(when { !enabled -> Tokens.Surface2; ghost -> Tokens.Surface3; else -> Tokens.Primary })
            .clickable(enabled = enabled, role = Role.Button, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) { Text(text, color = if (enabled) Tokens.TextMain else Tokens.Muted, fontSize = Tokens.Body, fontWeight = FontWeight.Bold) }
}

@Composable
fun SourceBadge(src: SignalSource?) {
    val (label, bg) = when (src) {
        SignalSource.VEHICLE -> "실차 신호" to Tokens.Success
        SignalSource.EMULATOR -> "에뮬레이터 신호" to Tokens.Surface3
        SignalSource.MOCK, null -> "모의" to Tokens.Warn
    }
    Text(label, color = Tokens.TextMain, fontSize = Tokens.Caption, fontWeight = FontWeight.SemiBold,
        modifier = Modifier.clip(RoundedCornerShape(99.dp)).background(bg).padding(horizontal = 12.dp, vertical = 5.dp))
}

@Composable
fun Chip(text: String, selected: Boolean, onClick: () -> Unit) = Box(
    Modifier.heightIn(min = Tokens.TouchMin).clip(RoundedCornerShape(16.dp))
        .background(if (selected) Tokens.Primary else Tokens.Surface)
        .border(1.5.dp, if (selected) Tokens.Primary else Tokens.Line, RoundedCornerShape(16.dp))
        .clickable(role = Role.Button, onClick = onClick).padding(horizontal = 24.dp),
    contentAlignment = Alignment.Center,
) { Text(text, color = if (selected) Tokens.TextMain else Tokens.TextSub, fontSize = Tokens.Body, fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal) }

/** 신호 → 확인 상태 알약 */
private fun <T> Signal<T>.pill(ok: (T) -> Boolean): Pair<Pill, String> = when {
    status == SignalStatus.UNKNOWN -> Pill.CHECKING to "확인 중"
    status != SignalStatus.OK || value == null -> Pill.BLOCKED to "읽을 수 없음"
    ok(value) -> Pill.OK to "확인"
    else -> Pill.WAIT to "대기"
}

// ── A-1 안전 게이트 ───────────────────────────────────────
@Composable
fun GateScreen(
    ui: UiState,
    onStart: () -> Unit,
    onVoicePreview: (() -> Unit)? = null,
    voicePreviewStatus: String? = null,
    profiles: ChiilViewModel? = null,   // 안전 확인 단계에서만 넘긴다(복기 잠금 화면에서는 null → 프로필 줄 숨김)
) {
    val s = ui.snapshot
    val ready = ui.gate is GateState.Ready && ui.session.phase == Phase.READY
    Column(verticalArrangement = Arrangement.spacedBy(22.dp)) {
        TwoPane(
            left = {
                Hero(if (ready) Mood.HAPPY else Mood.WORRIED, "연습 준비를 확인할게요", "차가 직접 확인해요. 셋 다 ‘확인’이면 시작할 수 있어요.")
                ui.profile.nextGoal?.let { Banner("지난번 목표: $it — 이어서 해 볼까요?", Pill.INFO) }
            },
            right = {
                val (g, gt) = s.gear.pill { it == Gear.PARK }
                val (b, bt) = s.parkingBrakeOn.pill { it }
                val (u, ut) = s.uxRestricted.pill { !it }
                Row(verticalAlignment = Alignment.CenterVertically) { Caption("차 신호", Tokens.TextSub); Spacer(Modifier.width(10.dp)); SourceBadge(s.gear.source) }
                CheckCard(Ico.GEAR, "기어 P", g, gt)
                CheckCard(Ico.PBRAKE, "주차 브레이크", b, bt)
                CheckCard(Ico.ROAD, "주행 제한 없음", u, ut)
                Body(if (ready) "준비됐어요. 시작해 볼까요?" else (ui.gate as? GateState.Locked)?.reason?.message ?: "", if (ready) Tokens.SuccessText else Tokens.TextSub)
                Column(verticalArrangement = Arrangement.spacedBy(Tokens.TouchGap)) {
                    BigButton("연습 시작", enabled = ready, onClick = onStart)
                    if (onVoicePreview != null && ui.gate is GateState.Ready) BigButton("음성 안내 듣기 (모의)", ghost = true, onClick = onVoicePreview)
                }
                if (onVoicePreview != null && ui.gate is GateState.Ready) voicePreviewStatus?.let { Caption(it) }
                Caption("속도가 0이어도 기어가 P가 아니면 주차로 보지 않아요")
            },
        )
        profiles?.let { Card { ProfileRow(ui, it) } }
    }
}

/** C02: 누구의 연습인지. 칩으로 전환·추가, 지우기는 두 번 눌러 확인 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun ProfileRow(ui: UiState, vm: ChiilViewModel) {
    var confirm by remember(ui.profile.id) { mutableStateOf(false) }
    Row(verticalAlignment = Alignment.CenterVertically) { LineIcon(Ico.USER, 26.dp, Tokens.TextSub); Spacer(Modifier.width(10.dp)); Heading("누구의 연습인가요") }
    FlowRow(horizontalArrangement = Arrangement.spacedBy(Tokens.TouchGap), verticalArrangement = Arrangement.spacedBy(Tokens.TouchGap)) {
        (ui.profiles.ifEmpty { listOf(ui.profile) }).forEach { p -> Chip(p.displayName, p.id == ui.profile.id) { vm.switchProfile(p.id) } }
        if (ui.profiles.size < PracticeStore.ALIASES.size) Chip("+ 추가", false) { vm.addProfile() }
        Chip(if (confirm) "한 번 더 누르면 ${ui.profile.displayName} 기록 삭제" else "기록 지우기", false) {
            if (confirm) vm.deleteCurrentProfile() else confirm = true
        }
    }
    Caption("기록은 이 기기에만, 사람마다 따로 저장돼요")
}

// ── A-2 목표 고르기 ────────────────────────────────────────
private val goalIcons = mapOf("출발·정지" to Ico.BRAKE, "주차" to Ico.PARKING, "차선 바꾸기" to Ico.TURN, "좌회전" to Ico.WHEEL, "그냥 몸풀기" to Ico.FLAG)

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun GoalScreen(ui: UiState, onChoose: (String, Boolean) -> Unit) {
    var goal by remember { mutableStateOf<String?>(null) }
    val passenger = ui.snapshot.passengerSeated.isUsable && ui.snapshot.passengerSeated.value == true
    TwoPane(
        left = { Hero(Mood.CALM, "오늘 뭐가 제일 걱정돼요?", "하나만 골라요. 음성 길잡이도 이 목표에 대해서만 말해요.") },
        right = {
            FlowRow(horizontalArrangement = Arrangement.spacedBy(Tokens.TouchGap), verticalArrangement = Arrangement.spacedBy(Tokens.TouchGap)) {
                Goals.all.forEach { g -> ChoiceCard(g, g == goal, goalIcons[g] ?: Ico.FLAG) { goal = g } }
            }
            Spacer(Modifier.height(Tokens.TouchGap - 14.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(Tokens.TouchGap)) {
                Box(Modifier.weight(1f)) { BigButton("혼자 연습", enabled = goal != null, ghost = passenger) { onChoose(goal!!, false) } }
                Box(Modifier.weight(1f)) { BigButton("멘토와 함께", enabled = goal != null, ghost = !passenger) { onChoose(goal!!, true) } }
            }
            if (passenger) Banner("조수석에 누가 앉아 있네요. 함께 연습해요", Pill.INFO)
        },
    )
}

// ── A-3 워밍업 미션 ───────────────────────────────────────
/** 미션별 그림과 두 줄 설명(웹 MISSION_META와 같은 내용): 필기에서 배운 것 / 내 차에서는 */
private data class Meta(val icon: Ico, val know: String, val car: String)
private val missionMeta = mapOf(
    MissionId.SEAT_BELT to Meta(Ico.BELT, "출발 전 전 좌석 안전벨트", "‘딸깍’ 소리가 날 때까지"),
    MissionId.BRAKE_PRESS_RELEASE to Meta(Ico.BRAKE, "브레이크는 오른발, 뒤꿈치는 바닥에", "끝까지 밟았다가 천천히"),
    MissionId.TURN_SIGNAL to Meta(Ico.TURN, "진로를 바꾸기 30m 전에 신호", "레버 아래 = 왼쪽, 위 = 오른쪽"),
    MissionId.WIPER to Meta(Ico.WIPER, "비 오는 날은 시야부터", "와이퍼 레버 위치 익히기"),
    MissionId.LOW_BEAM to Meta(Ico.BEAM, "해 질 녘·터널에서는 전조등", "스위치를 돌려 켜기"),
    MissionId.STEERING_CENTER to Meta(Ico.WHEEL, "출발 전 바퀴는 똑바로", "핸들 가운데 ±5°에서 2초"),
    MissionId.PEDAL_FEEL to Meta(Ico.PEDAL, "페달은 살살, 발목으로", "초록 칸 안에서 2초"),
)

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun WarmupScreen(ui: UiState, onSkip: () -> Unit, onNext: () -> Unit) {
    val r = ui.runner ?: return
    val judges = remember { MissionCatalog.warmupRequired() + MissionCatalog.warmupOptional() }
    // 사용자에게 영어 내부 이름(seat_belt)을 보이지 않는다
    val titles = remember { judges.associate { it.id to it.title } }
    val currentId = judges.firstOrNull { it.title == r.currentTitle }?.id
    val meta = currentId?.let { missionMeta[it] }
    Column(verticalArrangement = Arrangement.spacedBy(18.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Title("운전석 워밍업"); Spacer(Modifier.width(14.dp))
            Heading("${minOf(r.index + 1, r.total)}/${r.total}", Tokens.TextSub); Spacer(Modifier.width(18.dp))
            StepDots(r.total, if (r.finished) r.total else r.index)
            Spacer(Modifier.weight(1f))
            SourceBadge(r.results.lastOrNull()?.source ?: ui.snapshot.brakePressed.source)
        }
        if (!r.finished) Card(pad = 28.dp) {
            BoxWithConstraints(Modifier.fillMaxWidth()) {
                val wide = maxWidth >= 700.dp
                Row(horizontalArrangement = Arrangement.spacedBy(if (wide) 36.dp else 22.dp), verticalAlignment = Alignment.Top) {
                    IconTile(meta?.icon ?: Ico.CHECK, if (wide) 220.dp else 120.dp, done = r.progress is Progress.Done)
                    Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(16.dp)) {
                        if (wide) Display(r.currentTitle ?: "") else Title(r.currentTitle ?: "")
                        meta?.let {
                            RuleLine("필기", Pill.INFO, it.know)
                            RuleLine("내 차에서는", Pill.WAIT, it.car)
                        }
                        when (val p = r.progress) {
                            is Progress.InProgress -> {
                                if (p.total > 1) Row(verticalAlignment = Alignment.CenterVertically) { StepDots(p.total, p.step - 1); Spacer(Modifier.width(12.dp)); Caption("${p.step}/${p.total} 단계") }
                                // 지금 할 행동 한 문장: 가장 눈에 띄게
                                Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(18.dp)).background(Tokens.Primary.copy(alpha = .16f))
                                    .border(1.5.dp, Tokens.Primary.copy(alpha = .5f), RoundedCornerShape(18.dp)).padding(horizontal = 20.dp, vertical = 16.dp),
                                    verticalAlignment = Alignment.CenterVertically) {
                                    LineIcon(Ico.FLAG, 30.dp, Tokens.PrimaryText); Spacer(Modifier.width(14.dp)); Heading(p.hint)
                                }
                            }
                            is Progress.Done -> Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(18.dp)).background(Tokens.SuccessBg).padding(horizontal = 20.dp, vertical = 12.dp),
                                verticalAlignment = Alignment.CenterVertically) {
                                Mascot(Mood.HAPPY, 64.dp); Spacer(Modifier.width(14.dp)); Heading(p.praise, Tokens.SuccessText)
                            }
                            is Progress.Unavailable -> Banner(when (p.status) {
                                SignalStatus.PERMISSION_DENIED -> "이 미션에 필요한 권한이 없어요. 건너뛰고 다음으로 갈게요"
                                SignalStatus.DISCONNECTED -> "차량 상태를 확인할 수 없어요(연결 끊김)"
                                else -> "이 차에서는 이 신호를 지원하지 않아요"
                            })
                            null -> Heading("조작해 보세요", Tokens.PrimaryText)
                        }
                    }
                }
            }
        } else Card(color = Tokens.SuccessBg.copy(alpha = .5f)) {
            Row(verticalAlignment = Alignment.CenterVertically) { Mascot(Mood.HAPPY, 110.dp); Spacer(Modifier.width(18.dp)); Title("워밍업 완료. 출발 준비가 됐어요") }
        }
        if (r.results.isNotEmpty()) FlowRow(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            r.results.forEach { StatusPill("${titles[it.id] ?: "미션"} 완료", Pill.OK) }
        }
        Box(Modifier.widthIn(max = 560.dp)) {
            when {
                r.finished -> BigButton("가상 주차 연습으로", onClick = onNext)
                r.progress is Progress.Unavailable -> BigButton("건너뛰기", ghost = true, onClick = onSkip)
            }
        }
    }
}

/** 미션 설명 한 줄: 같은 폭 이름표 + 내용(두 줄이 나란히 맞게) */
@Composable
private fun RuleLine(tag: String, kind: Pill, text: String) = Row(verticalAlignment = Alignment.CenterVertically) {
    Box(Modifier.width(132.dp)) { StatusPill(tag, kind) }
    Body(text, Tokens.TextMain)
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
    // 차(실차·에뮬레이터)에서 온 핸들 값만 '실제 핸들'로 쓴다. 모의 값이면 화면 슬라이더로 연습
    val realSteer = ui.snapshot.steeringDeg.takeIf { it.isUsable && it.source != SignalSource.MOCK }?.value
    val steer = realSteer ?: sliderDeg
    val parked = sim.isParked(pose, slot)

    LaunchedEffect(speed, steer) {
        while (speed != 0f) { delay(50); pose = sim.step(pose, steer.toDouble(), speed.toDouble(), 0.05) }
    }
    Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Title("내 차로 하는 가상 주차"); Spacer(Modifier.width(12.dp))
            SourceBadge(SignalSource.MOCK) // 화면 속 연습은 항상 '가상'
            Spacer(Modifier.width(12.dp))
            Caption("차 크기 ${dims.lengthMm / 1000.0}m × ${dims.widthMm / 1000.0}m" + if (dims.isDefault) " (기본값)" else "")
        }
        Row(Modifier.fillMaxWidth().height(380.dp), horizontalArrangement = Arrangement.spacedBy(18.dp)) {
            Canvas(Modifier.weight(1f).fillMaxSize().clip(RoundedCornerShape(22.dp)).background(Tokens.Surface)) {
                val ppm = size.minDimension / 14f // pixels per meter
                val o = Offset(size.width / 2, size.height * 0.62f)
                fun px(x: Double, y: Double) = Offset(o.x + (x * ppm).toFloat(), o.y + (y * ppm).toFloat())
                // 주차 칸과 양옆 차
                val sw = (slot.widthM * ppm).toFloat(); val sl = (slot.lengthM * ppm).toFloat()
                drawRect(Tokens.SuccessText, topLeft = px(-slot.widthM / 2, -slot.lengthM / 2), size = Size(sw, sl), style = Stroke(4f))
                drawRect(Tokens.Surface3, topLeft = px(-slot.widthM * 1.5 + 0.1, -2.3), size = Size(sw - 8, (4.6 * ppm).toFloat()))
                drawRect(Tokens.Surface3, topLeft = px(slot.widthM * 0.5 + 0.1, -2.3), size = Size(sw - 8, (4.6 * ppm).toFloat()))
                // 내 차
                val c = px(pose.x, pose.y)
                rotate(degrees = (pose.heading * 180 / PI).toFloat(), pivot = c) {
                    drawRect(if (parked) Tokens.Success else Tokens.Primary,
                        topLeft = Offset(c.x - (sim.widthM * ppm / 2).toFloat(), c.y - (sim.lengthM * ppm * 0.78).toFloat()),
                        size = Size((sim.widthM * ppm).toFloat(), (sim.lengthM * ppm).toFloat()))
                }
            }
            Column(Modifier.width(300.dp), verticalArrangement = Arrangement.spacedBy(Tokens.TouchGap)) {
                Card(pad = 18.dp) {
                    Row(verticalAlignment = Alignment.CenterVertically) { LineIcon(Ico.WHEEL, 30.dp, Tokens.PrimaryText); Spacer(Modifier.width(10.dp))
                        Heading(if (realSteer != null) "실제 핸들 ${steer.toInt()}°" else "핸들(화면) ${steer.toInt()}°") }
                    if (realSteer == null) Slider(value = sliderDeg, onValueChange = { sliderDeg = it }, valueRange = -540f..540f)
                }
                BigButton(if (speed < 0) "멈춤" else "후진", ghost = speed >= 0) { speed = if (speed < 0) 0f else -0.8f }
                BigButton(if (speed > 0) "멈춤" else "전진", ghost = speed <= 0) { speed = if (speed > 0) 0f else 0.8f }
            }
        }
        Banner(if (parked) "칸 안에 들어왔어요. 기준점을 기억해 둬요" else "뒷바퀴가 옆 차 범퍼를 지날 때 핸들을 끝까지 돌려 보세요",
            if (parked) Pill.OK else Pill.INFO)
        Box(Modifier.widthIn(max = 560.dp)) { BigButton(if (parked) "다음" else "그만하고 다음", ghost = !parked, onClick = onDone) }
    }
}

// ── A-4 페달 감각(조건부) ─────────────────────────────────
@Composable
fun PedalScreen(ui: UiState, onDone: () -> Unit) {
    val pct = ui.snapshot.brakePedalPct.value ?: 0f   // 브레이크 페달 압력(가속 페달은 실차에서 열지 않음)
    TwoPane(
        left = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                IconTile(Ico.PEDAL, 96.dp, done = ui.pedal is Progress.Done); Spacer(Modifier.width(18.dp))
                Column { Display("살살 밟기"); SourceBadge(ui.snapshot.brakePedalPct.source) }
            }
            Body("브레이크 페달로 해요. 초록 칸 안에서 2초 버티면 돼요. 연습용 값이며 실제 운전 기준이 아니에요")
        },
        right = {
            Card {
                Row(verticalAlignment = Alignment.Bottom) { Display("${pct.toInt()}"); Spacer(Modifier.width(6.dp)); Heading("%", Tokens.TextSub) }
                Canvas(Modifier.fillMaxWidth().height(56.dp).clip(RoundedCornerShape(14.dp))) {
                    drawRect(Tokens.Surface3, size = size)
                    drawRect(Tokens.SuccessBg, topLeft = Offset(size.width * 0.20f, 0f), size = Size(size.width * 0.15f, size.height))
                    drawRect(Tokens.Primary, size = Size(size.width * pct / 100f, size.height))
                    drawRect(Tokens.SuccessText, topLeft = Offset(size.width * 0.20f, 0f), size = Size(size.width * 0.15f, size.height), style = Stroke(5f))
                }
                Caption("초록 칸 = 살살 밟은 정도(20~35%)")
            }
            when (val p = ui.pedal) {
                is Progress.Done -> { Banner(p.praise, Pill.OK); BigButton("다음", onClick = onDone) }
                is Progress.Unavailable -> { Banner("이 차에서는 페달 값을 받을 수 없어요"); BigButton("건너뛰기", ghost = true, onClick = onDone) }
                is Progress.InProgress -> Heading(p.hint, Tokens.PrimaryText)
                null -> Heading("페달을 살살 밟아 보세요", Tokens.PrimaryText)
            }
        },
    )
}

// ── A-6 멘토 약속 ─────────────────────────────────────────
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun PactScreen(ui: UiState, onToggle: (String) -> Unit, onConfirm: () -> Unit) = TwoPane(
    left = { Hero(Mood.CALM, "오늘의 약속", "운전하는 사람과 옆자리 사람이 같이 골라요. 최대 ${PactOptions.MAX}개") ; Banner("오늘 목표: ${ui.goal ?: "-"}", Pill.INFO) },
    right = {
        Column(verticalArrangement = Arrangement.spacedBy(Tokens.TouchGap)) {
            PactOptions.all.forEach { ChoiceCard(it, it in ui.pact, Ico.PACT, Modifier.fillMaxWidth()) { onToggle(it) } }
            BigButton("둘 다 약속했어요", enabled = ui.pact.isNotEmpty(), onClick = onConfirm)
        }
    },
)

@Composable
fun ReadyToGoScreen(ui: UiState) = CenterMessage(
    Mood.CALM, "출발 준비 완료", "기어를 D로 옮기면 화면은 쉬어요. 연습은 함께 탄 사람과 하세요.",
    if (!ui.mentorMode) "혼자라면 주차장 안 연습만 이어 가도 좋아요. 실제 도로 연습은 동승자와 함께 하세요." else null,
)

// ── A-7 주행 중 잠금(정적: 애니메이션·스크롤·버튼 없음) ─────────────
@Composable
fun DrivingLockScreen() = Column(Modifier.fillMaxSize(), verticalArrangement = Arrangement.Center, horizontalAlignment = Alignment.CenterHorizontally) {
    CenterMessage(Mood.REST, "화면은 잠시 쉴게요", "도로만 보세요. 주차하면 오늘 연습을 같이 돌아봐요.")
    StatusPill("연습 중", Pill.BLOCKED)
}

// ── A-8 주차 후 복기 ─────────────────────────────────────
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun ReviewScreen(ui: UiState, onSave: (String?, String?, String?, Boolean) -> Unit) {
    var learner by remember { mutableStateOf<String?>(null) }
    var mentor by remember { mutableStateOf<String?>(null) }
    var next by remember { mutableStateOf<String?>(null) }
    val sum = ui.summary
    Column(verticalArrangement = Arrangement.spacedBy(20.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Mascot(Mood.HAPPY, 110.dp); Spacer(Modifier.width(18.dp))
            Column { Display("수고했어요"); Body("오늘 있었던 일을 같이 돌아봐요. 점수가 아니라 그 순간의 사실이에요.") }
        }
        sum?.let {
            Row(horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                StatTile("${it.durationMin}분", if (it.durationFromVehicle) "연습 시간" else "연습 시간(앱 기준)", Modifier.weight(1f))
                StatTile(it.distanceKm?.let { d -> "%.1fkm".format(d) } ?: "—", "거리", Modifier.weight(1f))
                StatTile("${ui.timeline.marks.size}번", "빠른 속도 변화", Modifier.weight(1f))
            }
        }
        Card { TimelineSection(ui.timeline, ui.spokenEventIds) }
        Card {
            Heading("오늘 어땠어요?")
            FlowRow(horizontalArrangement = Arrangement.spacedBy(Tokens.TouchGap), verticalArrangement = Arrangement.spacedBy(Tokens.TouchGap)) { ReviewChips.learner.forEach { Chip(it, it == learner) { learner = it } } }
            if (ui.mentorMode) {
                Heading("멘토 한마디")
                FlowRow(horizontalArrangement = Arrangement.spacedBy(Tokens.TouchGap), verticalArrangement = Arrangement.spacedBy(Tokens.TouchGap)) { ReviewChips.mentor.forEach { Chip(it, it == mentor) { mentor = it } } }
            }
            Heading("다음 목표 하나")
            FlowRow(horizontalArrangement = Arrangement.spacedBy(Tokens.TouchGap), verticalArrangement = Arrangement.spacedBy(Tokens.TouchGap)) { ReviewChips.nextGoals.forEach { Chip(it, it == next) { next = it } } }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(Tokens.TouchGap)) {
            Box(Modifier.weight(1f)) { BigButton("저장하고 다음 연습") { onSave(learner, mentor, next, false) } }
            Box(Modifier.weight(1f)) { BigButton("혼자 운전 시작할래요", ghost = true) { onSave(learner, mentor, next, true) } }
        }
    }
}

/** C01: 주차 후에만 보이는 정적 속도 시간표. 사건 점 + 글 목록(그래프를 못 보는 사람도 같은 내용을 읽는다) */
@Composable
private fun TimelineSection(tl: DriveTimeline, spoken: Set<String> = emptySet()) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Heading("속도 시간표"); Spacer(Modifier.width(12.dp))
        if (tl.hasData) tl.sources.forEach { SourceBadge(it); Spacer(Modifier.width(6.dp)) }
    }
    if (!tl.hasData) {
        Body("이번 주행의 속도 기록은 없어요. 속도 신호가 꺼져 있거나 받지 못했어요")
        return
    }
    val maxT = tl.points.last().tSec.coerceAtLeast(1f)
    val maxK = (tl.points.maxOf { it.kmh }.coerceAtLeast(20f) / 10f).let { kotlin.math.ceil(it) * 10f }
    Canvas(Modifier.fillMaxWidth().height(170.dp).clip(RoundedCornerShape(14.dp)).background(Tokens.Bg)) {
        val pad = 14.dp.toPx()
        fun at(t: Float, k: Float) = Offset(pad + (size.width - 2 * pad) * t / maxT, size.height - pad - (size.height - 2 * pad) * k / maxK)
        listOf(0f, maxK / 2, maxK).forEach { k -> drawLine(Tokens.Surface3, at(0f, k), at(maxT, k), 1.dp.toPx()) }
        tl.points.zipWithNext { a, b -> drawLine(Tokens.Primary, at(a.tSec, a.kmh), at(b.tSec, b.kmh), strokeWidth = 3.dp.toPx()) }
        tl.marks.forEach {
            val c = at(it.tSec, it.toKmh)
            drawCircle(if (it.type == SpeedEventType.RAPID_SPEED_DECREASE) Tokens.Danger else Tokens.Yellow, 8.dp.toPx(), c)
            if (it.eventId in spoken) drawCircle(Tokens.TextMain, 12.dp.toPx(), c, style = Stroke(2.dp.toPx()))
        }
    }
    Caption("최고 %.0fkm/h · %d분 %02d초 · ● 빨강 = 빠르게 줄어듦, 노랑 = 빠르게 늘어남, 고리 = 주행 중에 말함(모의)".format(tl.points.maxOf { it.kmh }, maxT.toInt() / 60, maxT.toInt() % 60))
    if (tl.marks.isEmpty()) Body("급하게 속도가 바뀐 순간은 없었어요", Tokens.SuccessText)
    tl.marks.take(5).forEach { m ->
        val what = if (m.type == SpeedEventType.RAPID_SPEED_DECREASE) "속도가 빠르게 줄었어요" else "속도가 빠르게 늘었어요"
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("%d:%02d".format(m.tSec.toInt() / 60, m.tSec.toInt() % 60), color = Tokens.TextSub, fontSize = Tokens.Body, modifier = Modifier.width(80.dp))
            Body("$what  %.0f → %.0fkm/h".format(m.fromKmh, m.toKmh), Tokens.TextMain)
            if (m.eventId in spoken) { Spacer(Modifier.width(10.dp)); StatusPill("말했어요(모의)", Pill.INFO) }
        }
    }
    Caption("속도 변화만 본 기록이에요. 페달 조작이나 주변 상황을 판단한 것이 아니에요")
}

@Composable
fun BadgeToast(ui: UiState, onDismiss: () -> Unit) {
    LaunchedEffect(ui.newBadges) { delay(2000); onDismiss() }   // 정차 중 2초 이내로 짧게
    val b = ui.newBadges.first()
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.TopCenter) {   // 머리줄 가운데 빈 곳에 잠깐(내용·제목을 가리지 않게)
        Row(Modifier.clip(RoundedCornerShape(20.dp)).background(Tokens.Surface2).border(1.5.dp, Tokens.Yellow.copy(alpha = .5f), RoundedCornerShape(20.dp)).padding(horizontal = 22.dp, vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically) {
            LineIcon(Ico.STAR, 32.dp, Tokens.Yellow); Spacer(Modifier.width(12.dp))
            Column { Caption("배지"); Heading(b.badge.title + if (b.mockOnly) " (모의)" else "") }
        }
    }
}

// ── 디버그 패널(USE_MOCK 빌드 전용): 모의 차량 조작 ───────────────────
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun DebugPanel(vm: ChiilViewModel, ui: UiState, modifier: Modifier) {
    var open by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    val m = vm.mock; val s = ui.snapshot
    Column(modifier.width(if (open) 460.dp else 130.dp).clip(RoundedCornerShape(14.dp)).background(Color(0xE6000000)).padding(10.dp)) {
        Text(if (open) "모의 차량 ▼" else "모의 ▲", color = Tokens.Yellow, fontSize = Tokens.Caption, fontWeight = FontWeight.Bold,
            modifier = Modifier.clickable { open = !open }.padding(6.dp))
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
    Text(t, color = Color.White, fontSize = Tokens.Caption, modifier = Modifier.clip(RoundedCornerShape(8.dp)).background(Color(0xFF333333)).clickable(onClick = f).padding(horizontal = 10.dp, vertical = 8.dp))
