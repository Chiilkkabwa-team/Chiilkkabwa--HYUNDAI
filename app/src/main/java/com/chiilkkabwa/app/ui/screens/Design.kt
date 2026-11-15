package com.chiilkkabwa.app.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.chiilkkabwa.app.ui.theme.Tokens

/**
 * 차량 화면용 공통 조각. 웹 체험판(web/styles.css)과 같은 모양: 둥근 카드, 큰 터치 영역(76dp),
 * 상태 알약(확인·대기), 넓은 화면에서는 왼쪽 그림·오른쪽 조작 두 칸.
 */

/**
 * 차량 화면 배율. Pleos 에뮬레이터 앱 창은 dp로 약 1680×1200(2026-11-15 영상 실측 추정)이라
 * 그대로 그리면 글씨·버튼이 깨알만 해진다. 웹처럼 설계 크기(1100×680dp)에 맞춰 1~2배로 키운다.
 * 작은 창(2창 분할 등)에서는 1배 그대로.
 */
val DesignWidth = 1100.dp
val DesignHeight = 680.dp

@Composable
fun CarScale(content: @Composable () -> Unit) = BoxWithConstraints(Modifier.fillMaxSize()) {
    val k = minOf(maxWidth / DesignWidth, maxHeight / DesignHeight).coerceIn(1f, 2f)
    val d = LocalDensity.current
    CompositionLocalProvider(LocalDensity provides Density(d.density * k, d.fontScale)) { content() }
}

/** 큰 글씨 계층: Display(40) > Title(30) > Heading(25) > Body(22) > Caption(17) */
@Composable fun Display(t: String, c: Color = Tokens.TextMain, align: TextAlign? = null) =
    Text(t, color = c, fontSize = Tokens.Display, fontWeight = FontWeight.Bold, lineHeight = Tokens.Display * 1.25f, textAlign = align)
@Composable fun Heading(t: String, c: Color = Tokens.TextMain) =
    Text(t, color = c, fontSize = Tokens.Heading, fontWeight = FontWeight.SemiBold, lineHeight = Tokens.Heading * 1.3f)
@Composable fun Caption(t: String, c: Color = Tokens.Muted) =
    Text(t, color = c, fontSize = Tokens.Caption, lineHeight = Tokens.Caption * 1.45f)

@Composable
fun Card(modifier: Modifier = Modifier, color: Color = Tokens.Surface, pad: Dp = 22.dp, content: @Composable ColumnScope.() -> Unit) =
    Column(modifier.fillMaxWidth().clip(RoundedCornerShape(22.dp)).background(color).padding(pad),
        verticalArrangement = Arrangement.spacedBy(12.dp), content = content)

/** 넓은 화면(≥760dp): 왼쪽 그림·제목, 오른쪽 조작. 좁으면 위아래 */
@Composable
fun TwoPane(left: @Composable ColumnScope.() -> Unit, right: @Composable ColumnScope.() -> Unit) =
    BoxWithConstraints(Modifier.fillMaxWidth()) {
        if (maxWidth >= 760.dp) Row(horizontalArrangement = Arrangement.spacedBy(28.dp)) {
            Column(Modifier.weight(0.42f), verticalArrangement = Arrangement.spacedBy(16.dp), content = left)
            Column(Modifier.weight(0.58f), verticalArrangement = Arrangement.spacedBy(14.dp), content = right)
        } else Column(verticalArrangement = Arrangement.spacedBy(16.dp)) { left(); right() }
    }

/** 마스코트 + 제목 + 부제 */
@Composable
fun Hero(mood: Mood, title: String, sub: String? = null, size: Dp = 150.dp) = Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
    Mascot(mood, size)
    Display(title)
    sub?.let { Body(it) }
}

enum class Pill(val bg: Color, val fg: Color) {
    OK(Tokens.SuccessBg, Tokens.SuccessText), WAIT(Tokens.Surface3, Tokens.TextSub),
    CHECKING(Tokens.WarnBg, Tokens.Yellow), BLOCKED(Tokens.Lock, Tokens.DangerText),
    INFO(Tokens.Surface3, Tokens.PrimaryText),
}

@Composable
fun StatusPill(text: String, kind: Pill) = Row(
    Modifier.clip(RoundedCornerShape(99.dp)).background(kind.bg).padding(horizontal = 14.dp, vertical = 7.dp),
    verticalAlignment = Alignment.CenterVertically,
) {
    if (kind == Pill.OK) { LineIcon(Ico.CHECK, 18.dp, kind.fg); Spacer(Modifier.width(6.dp)) }
    Text(text, color = kind.fg, fontSize = Tokens.Caption, fontWeight = FontWeight.SemiBold)
}

/** 안전 확인 한 줄: 아이콘 + 이름 + 상태 알약. 확인되면 초록 테두리 */
@Composable
fun CheckCard(icon: Ico, label: String, state: Pill, stateText: String) = Row(
    Modifier.fillMaxWidth().heightIn(min = 76.dp).clip(RoundedCornerShape(18.dp))
        .background(if (state == Pill.OK) Tokens.SuccessBg.copy(alpha = .55f) else Tokens.Surface)
        .border(1.5.dp, if (state == Pill.OK) Tokens.Success.copy(alpha = .6f) else Tokens.Line, RoundedCornerShape(18.dp))
        .padding(horizontal = 20.dp, vertical = 12.dp),
    verticalAlignment = Alignment.CenterVertically,
) {
    LineIcon(icon, 34.dp, if (state == Pill.OK) Tokens.SuccessText else Tokens.TextSub)
    Spacer(Modifier.width(16.dp))
    Text(label, color = Tokens.TextMain, fontSize = Tokens.Body, fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f))
    StatusPill(stateText, state)
}

/** 단계 점(현재 단계는 길게) */
@Composable
fun StepDots(total: Int, current: Int) = Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
    repeat(total) { i ->
        Box(Modifier.size(width = if (i == current) 26.dp else 10.dp, height = 10.dp).clip(CircleShape)
            .background(when { i < current -> Tokens.Success; i == current -> Tokens.Primary; else -> Tokens.Surface3 }))
    }
}

/** 아이콘 타일(미션 그림 자리) */
@Composable
fun IconTile(icon: Ico, size: Dp = 120.dp, done: Boolean = false) = Box(
    Modifier.size(size).clip(RoundedCornerShape(28.dp)).background(if (done) Tokens.SuccessBg else Tokens.Surface3),
    contentAlignment = Alignment.Center,
) { LineIcon(icon, size * 0.56f, if (done) Tokens.SuccessText else Tokens.PrimaryText) }

/** 고르는 카드(목표·약속): 선택되면 파란 테두리 */
@Composable
fun ChoiceCard(text: String, selected: Boolean, icon: Ico? = null, modifier: Modifier = Modifier, onClick: () -> Unit) = Row(
    modifier.heightIn(min = 76.dp).clip(RoundedCornerShape(18.dp))
        .background(if (selected) Tokens.Primary.copy(alpha = .22f) else Tokens.Surface)
        .border(2.dp, if (selected) Tokens.Primary else Tokens.Line, RoundedCornerShape(18.dp))
        .clickable(role = Role.RadioButton, onClick = onClick).padding(horizontal = 20.dp, vertical = 14.dp),
    verticalAlignment = Alignment.CenterVertically,
) {
    icon?.let { LineIcon(it, 30.dp, if (selected) Tokens.PrimaryText else Tokens.TextSub); Spacer(Modifier.width(14.dp)) }
    Text(text, color = Tokens.TextMain, fontSize = Tokens.Body, fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal)
}

/** 회색 안내 띠(미지원·권한 없음 등) */
@Composable
fun Banner(text: String, kind: Pill = Pill.CHECKING) = Row(
    Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp)).background(kind.bg).padding(16.dp),
    verticalAlignment = Alignment.CenterVertically,
) {
    LineIcon(Ico.INFO, 26.dp, kind.fg); Spacer(Modifier.width(12.dp))
    Text(text, color = kind.fg, fontSize = Tokens.Body)
}

/** 숫자 칸(복기 요약) */
@Composable
fun StatTile(value: String, label: String, modifier: Modifier = Modifier) = Column(
    modifier.clip(RoundedCornerShape(18.dp)).background(Tokens.Surface).padding(18.dp),
    verticalArrangement = Arrangement.spacedBy(4.dp),
) {
    Text(value, color = Tokens.TextMain, fontSize = Tokens.Title, fontWeight = FontWeight.Bold)
    Caption(label)
}

/** 가운데 정렬 큰 안내(잠금·준비 완료) */
@Composable
fun CenterMessage(mood: Mood, title: String, sub: String, note: String? = null) = Column(
    Modifier.fillMaxWidth().padding(vertical = 24.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(14.dp),
) {
    Mascot(mood, 170.dp)
    Display(title, align = TextAlign.Center)
    Text(sub, color = Tokens.TextSub, fontSize = Tokens.Heading, textAlign = TextAlign.Center)
    note?.let { Caption(it) }
}
