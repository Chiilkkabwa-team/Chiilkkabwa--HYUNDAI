package com.chiilkkabwa.app.ui.screens

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.graphics.vector.PathParser
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * 웹 체험판(web/art.js)과 같은 그림: 마스코트 '콩콩이'와 선 아이콘. 외부 이미지·폰트 없음(S11).
 * 마스코트 색은 캐릭터 고유색이라 테마를 따르지 않는다.
 */
enum class Mood { CALM, HAPPY, WORRIED, REST }

private fun p(d: String): Path = PathParser().parsePathString(d).toPath()

private val Yellow = Color(0xFFFFD66B)
private val YellowHi = Color(0xFFFFE59A)
private val Ink = Color(0xFF1B2430)
private val Leaf = Color(0xFF3E7B4F)
private val LeafHi = Color(0xFF7EE2A8)
private val Cheek = Color(0xFFF7A69B)
private val Blue = Color(0xFF2F80ED)

@Composable
fun Mascot(mood: Mood, size: Dp = 120.dp, modifier: Modifier = Modifier) {
    val body = remember { p("M60 14c24 0 38 18 38 44 0 30-17 48-38 48S22 88 22 58c0-26 14-44 38-44z") }
    val shine = remember { p("M60 14c24 0 38 18 38 44 0 3-.2 6-.6 9C92 44 80 26 60 24 42 24 30 36 25 52c2-22 15-38 35-38z") }
    val stem = remember { p("M52 16q8-10 18-6") }
    val leaf = remember { p("M70 10q8-2 10 6q-8 2-10-6z") }
    Canvas(modifier.size(size)) {
        scale(this.size.width / 120f, this.size.height / 120f, pivot = Offset.Zero) {
            if (mood == Mood.HAPPY) {
                drawPath(p("M100 22l2 6 6 2-6 2-2 6-2-6-6-2 6-2z"), Yellow)
                drawPath(p("M18 34l1.5 4 4 1.5-4 1.5-1.5 4-1.5-4-4-1.5 4-1.5z"), LeafHi)
            }
            drawPath(body, Yellow)
            drawPath(shine, YellowHi)
            drawPath(stem, Leaf, style = Stroke(4f, cap = StrokeCap.Round))
            drawPath(leaf, LeafHi)
            drawOval(Cheek.copy(alpha = .75f), Offset(32f, 64.4f), Size(12f, 7.2f))
            drawOval(Cheek.copy(alpha = .75f), Offset(76f, 64.4f), Size(12f, 7.2f))
            face(mood)
            // 작은 핸들
            drawCircle(Blue, 15f, Offset(60f, 94f), style = Stroke(5f))
            drawCircle(Blue, 4f, Offset(60f, 94f))
            drawLine(Blue, Offset(47f, 97f), Offset(73f, 97f), 4f)
            drawLine(Blue, Offset(60f, 98f), Offset(60f, 109f), 4f)
            drawOval(Yellow, Offset(35f, 86f), Size(14f, 12f))
            drawOval(Yellow, Offset(71f, 86f), Size(14f, 12f))
        }
    }
}

private fun DrawScope.face(mood: Mood) {
    val s = { w: Float -> Stroke(w, cap = StrokeCap.Round) }
    when (mood) {
        Mood.CALM -> {
            drawOval(Ink, Offset(42.4f, 50f), Size(9.2f, 12f)); drawOval(Ink, Offset(68.4f, 50f), Size(9.2f, 12f))
            drawCircle(Color.White, 1.6f, Offset(48.6f, 53.6f)); drawCircle(Color.White, 1.6f, Offset(74.6f, 53.6f))
            drawPath(p("M55 68q5 4 10 0"), Ink, style = s(3f))
        }
        Mood.HAPPY -> {
            drawPath(p("M41 57q6-8 12 0M67 57q6-8 12 0"), Ink, style = s(4f))
            drawPath(p("M53 66q7 10 14 0z"), Ink)
            drawPath(p("M56 70q4 3 8 0"), Color(0xFFF2857B))
        }
        Mood.WORRIED -> {
            drawOval(Ink, Offset(42.6f, 52.4f), Size(8.8f, 11.2f)); drawOval(Ink, Offset(68.6f, 52.4f), Size(8.8f, 11.2f))
            drawPath(p("M39 50l12-4M81 50l-12-4"), Ink, style = s(3f))
            drawPath(p("M55 71q5-4 10 0"), Ink, style = s(3f))
            drawPath(p("M88 40q4 7 0 10q-4-3 0-10z"), Color(0xFF9FD3FF))
        }
        Mood.REST -> {
            drawPath(p("M41 58h12M67 58h12"), Ink, style = s(4f))
            drawPath(p("M56 69h8"), Ink, style = s(3f))
        }
    }
}

/** 48×48 선 아이콘(web/art.js의 P와 같은 그림) */
enum class Ico(val d: String) {
    BELT("M12 6l22 30M24 26h16v12H24zM28 32h8M8 38h16"),
    BRAKE("M14 10h20l-3 22H17zM18 16h12M18 21h12M18 26h12M24 36v6m-4-4 4 4 4-4"),
    TURN("M18 14 8 24l10 10M8 24h12M30 14l10 10-10 10M40 24H28"),
    WIPER("M6 34a18 18 0 0 1 36 0M24 34 12 16"),
    BEAM("M22 12c-7 0-12 5-12 12s5 12 12 12zM28 14l12-2M28 20l12-1M28 26l12 1M28 32l12 2"),
    WHEEL("M24 7a17 17 0 1 1 0 34a17 17 0 1 1 0-34zM24 19.5a4.5 4.5 0 1 1 0 9a4.5 4.5 0 1 1 0-9zM8 22h11.5M28.5 22H40M24 28.5V41"),
    PEDAL("M8 34a16 16 0 0 1 32 0M24 34l8-11M14 22l3 3M24 18v4M34 22l-3 3"),
    PARKING("M16 8h16a8 8 0 0 1 8 8v16a8 8 0 0 1-8 8H16a8 8 0 0 1-8-8V16a8 8 0 0 1 8-8zM19 34V14h7a6 6 0 0 1 0 12h-7"),
    PACT("M6 12h22v14H16l-6 5v-5H6zM32 18h10v14h-4v5l-6-5h-8v-4"),
    NOTE("M14 6h20a4 4 0 0 1 4 4v28a4 4 0 0 1-4 4H14a4 4 0 0 1-4-4V10a4 4 0 0 1 4-4zM16 15h16M16 22h16M16 29h10"),
    FLAG("M12 42V6M12 8h22l-5 7 5 7H12"),
    MENTOR("M16 10a5 5 0 1 1 0 10a5 5 0 1 1 0-10zM33 14a4 4 0 1 1 0 8a4 4 0 1 1 0-8zM7 38c0-7 4-12 9-12s9 5 9 12M26 38c0-6 3-10 7-10s7 4 7 10"),
    LOCK("M15 21h18a4 4 0 0 1 4 4v12a4 4 0 0 1-4 4H15a4 4 0 0 1-4-4V25a4 4 0 0 1 4-4zM16 21v-6a8 8 0 0 1 16 0v6"),
    CHECK("M10 25l9 9 19-20"),
    WAIT("M24 8a16 16 0 1 1 0 32a16 16 0 1 1 0-32zM24 15v10l6 4"),
    GEAR("M16 6h16a6 6 0 0 1 6 6v24a6 6 0 0 1-6 6H16a6 6 0 0 1-6-6V12a6 6 0 0 1 6-6zM19 32V16h5a5 5 0 0 1 0 10h-5"),
    PBRAKE("M24 12a12 12 0 1 1 0 24a12 12 0 1 1 0-24zM8 12a20 20 0 0 0 0 24M40 12a20 20 0 0 1 0 24M21 30V18h4a3.5 3.5 0 0 1 0 7h-4"),
    ROAD("M18 42 22 6M30 42 26 6M24 36v-4M24 26v-4M24 16v-4"),
    STAR("M24 6l5.5 11.5 12.5 1.8-9 8.8 2.1 12.4L24 34.6l-11.1 5.9 2.1-12.4-9-8.8 12.5-1.8z"),
    INFO("M24 7a17 17 0 1 1 0 34a17 17 0 1 1 0-34zM24 21v12M24 15v1"),
    SOUND("M8 19h8l10-8v26l-10-8H8zM32 18a8 8 0 0 1 0 12M36 13a14 14 0 0 1 0 22"),
    USER("M24 8a8 8 0 1 1 0 16a8 8 0 1 1 0-16zM10 42c0-8 6-13 14-13s14 5 14 13"),
}

@Composable
fun LineIcon(icon: Ico, size: Dp = 32.dp, color: Color, modifier: Modifier = Modifier) {
    val path = remember(icon) { p(icon.d) }
    Canvas(modifier.size(size)) {
        scale(this.size.width / 48f, this.size.height / 48f, pivot = Offset.Zero) {
            drawPath(path, color, style = Stroke(3.4f, cap = StrokeCap.Round, join = StrokeJoin.Round))
        }
    }
}
