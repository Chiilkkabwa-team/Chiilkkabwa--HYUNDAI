package com.chiilkkabwa.app.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.chiilkkabwa.app.BuildConfig
import com.chiilkkabwa.app.core.achievement.AchievementRules
import com.chiilkkabwa.app.core.achievement.Badge
import com.chiilkkabwa.app.ui.UiState
import com.chiilkkabwa.app.ui.theme.Tokens
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * 웹 체험판에만 있던 정차 화면 메뉴를 앱으로 옮긴 것: 내 기록·설정·소개·사용법(온보딩).
 * 정차 확인 화면(CHECKING·READY)에서만 열리고, 그 밖의 단계가 되면 AppRoot가 닫는다(S04).
 * 글 입력 없이 고르기만 한다(차량 UI 규칙). ViewModel 대신 함수만 받아 미리보기·스크린샷에서도 그린다.
 */
enum class Sheet { HISTORY, SETTINGS, ABOUT, INTRO }

@Composable
fun SheetHost(
    sheet: Sheet,
    ui: UiState,
    onVoiceOn: (Boolean) -> Unit = {},
    onVoiceCap: (Int) -> Unit = {},
    onClose: () -> Unit,
) {
    Box(Modifier.fillMaxSize().background(Tokens.Bg)) {
        Column(Modifier.align(Alignment.TopCenter).widthIn(max = MaxContentWidth).fillMaxSize().verticalScroll(rememberScrollState()).padding(bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)) {
            when (sheet) {
                Sheet.HISTORY -> HistorySheet(ui)
                Sheet.SETTINGS -> SettingsSheet(ui, onVoiceOn, onVoiceCap)
                Sheet.ABOUT -> AboutSheet()
                Sheet.INTRO -> IntroSheet(onClose)
            }
            if (sheet != Sheet.INTRO) Box(Modifier.widthIn(max = 420.dp)) { BigButton("닫기", ghost = true, onClick = onClose) }
        }
    }
}

/** 시트 머리: 아이콘 + 제목 */
@Composable
private fun SheetTitle(icon: Ico, t: String) = Row(verticalAlignment = Alignment.CenterVertically) {
    IconTile(icon, 64.dp); Spacer(Modifier.width(16.dp)); Display(t)
}

/** 점 목록 한 줄 */
@Composable
private fun Bullet(t: String, icon: Ico = Ico.CHECK, color: androidx.compose.ui.graphics.Color = Tokens.SuccessText) =
    Row(verticalAlignment = Alignment.CenterVertically) { LineIcon(icon, 24.dp, color); Spacer(Modifier.width(12.dp)); Body(t, Tokens.TextMain) }

// ── 내 기록: 레벨(이어 간 횟수), 복기 기록, 배지, 수료 카드 ──
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun HistorySheet(ui: UiState) {
    val p = ui.profile
    val h = p.history
    val fmt = remember { SimpleDateFormat("M월 d일 HH:mm", Locale.KOREA) }
    SheetTitle(Ico.STAR, "${p.displayName}의 연습 기록")
    Row(horizontalArrangement = Arrangement.spacedBy(14.dp)) {
        StatTile("${AchievementRules.level(h)}", "레벨(이어 간 횟수)", Modifier.weight(1f))
        StatTile("${h.reviewsSaved}번", "복기", Modifier.weight(1f))
        StatTile("${h.parkingSimCompleted}번", "가상 주차", Modifier.weight(1f))
        StatTile("${h.badges.size}/${Badge.entries.size}", "배지", Modifier.weight(1f))
    }
    Caption("레벨은 실력이 아니라 이어 간 연습 횟수예요")
    p.nextGoal?.let { Banner("다음 목표: $it", Pill.INFO) }

    if (h.graduated) Certificate(p.displayName, h.badges.firstOrNull { it.badge == Badge.GRADUATE }?.atMs, fmt)

    Card {
        Heading("배지")
        if (h.badges.isEmpty()) Body("아직 없어요. 안전벨트 미션부터 해 봐요")
        h.badges.forEach { b ->
            Row(verticalAlignment = Alignment.CenterVertically) {
                LineIcon(Ico.STAR, 28.dp, Tokens.Yellow); Spacer(Modifier.width(12.dp))
                Column { Body(b.badge.title + if (b.mockOnly) " (모의)" else "", Tokens.TextMain); Caption(b.badge.desc) }
            }
        }
    }
    Card {
        Heading("최근 복기")
        if (p.reviews.isEmpty()) Body("아직 저장한 복기가 없어요")
        p.reviews.asReversed().take(10).forEach { r ->
            val parts = listOfNotNull(
                "${r.durationMin}분", r.distanceKm?.let { "%.1fkm".format(it) },
                r.learnerChip, r.mentorChip?.let { "멘토: $it" }, r.nextGoal?.let { "다음: $it" },
            )
            Row(verticalAlignment = Alignment.CenterVertically) {
                Caption(fmt.format(Date(r.atMs)), Tokens.TextSub); Spacer(Modifier.width(14.dp)); Body(parts.joinToString(" · "), Tokens.TextMain)
            }
        }
    }
    Caption("기록은 이 기기에만 있어요. 서버·계정·위치는 쓰지 않아요")
}

/** 수료 카드: '혼자 운전 시작'을 고른 사람. 면허·실력 인증이 아니다 */
@Composable
private fun Certificate(name: String, atMs: Long?, fmt: SimpleDateFormat) = Card(color = Tokens.SuccessBg) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Mascot(Mood.HAPPY, 96.dp); Spacer(Modifier.width(16.dp))
        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
            StatusPill("수료 카드", Pill.OK)
            Title("$name, 혼자 출발")
            Body("치일까봐 연습을 마치고 혼자 운전을 시작했어요" + (atMs?.let { " · ${fmt.format(Date(it))}" } ?: ""), Tokens.TextMain)
            Caption("연습 기록일 뿐 운전 실력이나 면허를 인증하지 않아요", Tokens.TextSub)
        }
    }
}

// ── 설정: 주행 중 모의 음성 켜기·횟수 ──
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun SettingsSheet(ui: UiState, onVoiceOn: (Boolean) -> Unit, onVoiceCap: (Int) -> Unit) {
    SheetTitle(Ico.SOUND, "설정")
    Card {
        Heading("주행 중 음성 길잡이")
        if (BuildConfig.USE_MOCK) {
            val on = ui.voiceOn ?: true
            FlowRow(horizontalArrangement = Arrangement.spacedBy(Tokens.TouchGap)) {
                ChoiceCard("켜기", on, Ico.SOUND) { onVoiceOn(true) }
                ChoiceCard("끄기", !on, Ico.LOCK) { onVoiceOn(false) }
            }
            Caption(if (ui.voiceOn == null) "정한 적 없으면 혼자 연습은 켜짐, 멘토와 함께는 꺼짐이에요" else "끈 상태도 기억해요")
        } else {
            Body("차량 신호용 앱은 주행 중에 말하지 않아요. 실제 주행 중 음성이 허용되는지 확인한 뒤에 켤게요")
        }
    }
    if (BuildConfig.USE_MOCK) Card {
        Heading("주행당 최대 횟수")
        FlowRow(horizontalArrangement = Arrangement.spacedBy(Tokens.TouchGap)) {
            listOf(3, 5).forEach { n -> ChoiceCard("${n}번", ui.voiceCap == n) { onVoiceCap(n) } }
        }
        Caption("간격은 45초로 고정이에요. 안전 정책상 앱은 5번까지만 말해요. 이 앱은 모의 신호로만 말해요")
    }
}

// ── 소개: 왜·무엇을·어디까지(웹 '소개'와 같은 내용) ──
@Composable
private fun AboutSheet() {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Mascot(Mood.CALM, 120.dp); Spacer(Modifier.width(18.dp))
        Column { Display("치일까봐"); Body("운전석이 아직 낯선 사람을 위한 차 안 연습", Tokens.TextMain) }
    }
    Row(horizontalArrangement = Arrangement.spacedBy(14.dp)) {
        listOf(
            Triple(Ico.PARKING, "주차 중 5분", "벨트·브레이크·깜빡이를 해 보면 차가 신호로 확인해 줘요"),
            Triple(Ico.LOCK, "주행 중 0터치", "화면은 잠기고, 필요할 때만 짧게 한마디"),
            Triple(Ico.NOTE, "다시 주차 3분", "속도 시간표로 돌아보고 다음 목표를 골라요"),
        ).forEachIndexed { i, (ic, t, d) ->
            Card(Modifier.weight(1f)) { IconTile(ic, 72.dp); Caption("${i + 1}단계", Tokens.PrimaryText); Heading(t); Body(d) }
        }
    }
    Card {
        Heading("지키는 약속")
        Bullet("확인은 차가, 판단은 사람이. 운전 실력을 판정하지 않아요")
        Bullet("차를 읽기만 해요. 브레이크·핸들을 조종하지 않아요")
        Bullet("모르면 잠가요. 기어 P + 주차 브레이크 + 주행 제한 없음일 때만 열어요")
        Bullet("기록은 기기 안에만. 서버·계정·위치 수집 없음")
    }
    Card {
        Heading("지금 어디까지")
        Bullet("모의 신호로 전체 흐름 동작(이 앱의 '모의' 표시)", Ico.INFO, Tokens.PrimaryText)
        Bullet("에뮬레이터에서 일부 차량 신호 수신 확인, 실제 차는 아직 시험 전", Ico.INFO, Tokens.PrimaryText)
        Bullet("주행 중 음성은 모의 신호로만. 실제 주행 중 허용은 확인 전", Ico.INFO, Tokens.PrimaryText)
    }
}

// ── 사용법(온보딩): 처음 실행 때 한 번, 메뉴에서 다시 ──
private data class IntroPage(val tag: String, val title: String, val body: String, val note: String)

private val introPages = listOf(
    IntroPage("무엇인가요", "면허는 땄는데 운전석은 아직 낯선 분께",
        "연수는 비싸고, 가족한테 배우다 보면 언성이 높아지기 쉬워요. 치일까봐는 그 사이 연습을 도와요. 조작이 맞았는지는 차가 확인해요.",
        "차를 움직이거나 멈추게 하지 않아요. 차량 신호를 읽기만 해요."),
    IntroPage("출발 전 · 주차된 상태에서", "조작해 보면 차가 맞았는지 알려 줘요",
        "안전벨트를 매면 매졌다고, 브레이크를 밟았다 떼면 됐다고 차 신호로 확인해요.",
        "기어 P, 주차 브레이크, 주행 제한 없음. 세 가지가 확인될 때만 연습 화면이 열려요."),
    IntroPage("달리는 동안", "화면은 잠그고 필요할 때만 말로 알려요",
        "움직이는 동안에는 화면을 보지 않게 잠가요. 꼭 필요한 순간에만 짧게 말해요.",
        "횟수와 간격을 정해 뒀어요. 내비 안내나 전화가 오면 바로 멈춰요."),
    IntroPage("다시 주차한 뒤", "오늘 연습을 시간 순서대로 돌아봐요",
        "속도가 어떻게 바뀌었는지, 언제 어떤 일이 있었는지 보여 줘요. 옆에 탔던 분과 같이 봐도 돼요.",
        "점수를 매기지 않아요. 기록은 이 기기에만 저장돼요."),
    IntroPage("이 앱 쓰는 법", "위에서부터 하나씩 따라가면 돼요",
        if (BuildConfig.USE_MOCK) "이 앱은 모의 신호 버전이에요. 오른쪽 아래 '모의' 패널로 기어·브레이크·벨트를 바꾸면 앱이 반응해요."
        else "차량 신호로 동작해요. 에뮬레이터에서는 Virtual Console로 기어·브레이크를 바꿔 보세요.",
        "메뉴의 '사용법'으로 이 안내를 다시 볼 수 있어요."),
)

private val introArt = listOf(Mood.CALM, Mood.HAPPY, Mood.REST, Mood.HAPPY, Mood.CALM)

@Composable
private fun IntroSheet(onDone: () -> Unit) {
    var i by remember { mutableIntStateOf(0) }
    val p = introPages[i]
    TwoPane(
        left = { Mascot(introArt[i], 200.dp); StepDots(introPages.size, i) },
        right = {
            StatusPill("${i + 1}/${introPages.size} · ${p.tag}", Pill.INFO)
            Display(p.title)
            Body(p.body, Tokens.TextMain)
            Banner(p.note, Pill.WAIT)
            Row(horizontalArrangement = Arrangement.spacedBy(Tokens.TouchGap)) {
                Box(Modifier.weight(1f)) { BigButton("건너뛰기", ghost = true, onClick = onDone) }
                Box(Modifier.weight(1f)) {
                    BigButton(if (i == introPages.lastIndex) "시작하기" else "다음") { if (i == introPages.lastIndex) onDone() else i++ }
                }
            }
        },
    )
}
