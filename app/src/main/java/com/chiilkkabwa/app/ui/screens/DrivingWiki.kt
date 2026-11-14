package com.chiilkkabwa.app.ui.screens

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.chiilkkabwa.app.ui.theme.Tokens

private data class WikiArticle(val title: String, val paragraphs: List<String>)
private data class WikiTopic(val title: String, val articles: List<WikiArticle>)

@Composable
internal fun DrivingWiki(onClose: () -> Unit) {
    var topic by remember { mutableStateOf<WikiTopic?>(null) }
    var article by remember { mutableStateOf<WikiArticle?>(null) }
    fun goBack() {
        when {
            article != null -> article = null
            topic != null -> topic = null
            else -> onClose()
        }
    }
    BackHandler { goBack() }
    val currentTopic = topic
    val currentArticle = article
    key(currentTopic, currentArticle) {
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(bottom = 16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            item {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    TextButton(onClick = { goBack() }) {
                        Text(if (currentTopic == null) "닫기" else "뒤로", color = Tokens.TextMain)
                    }
                    Title(currentArticle?.title ?: currentTopic?.title ?: "운전 지식 위키")
                }
            }
            when {
                currentArticle != null -> items(currentArticle.paragraphs) { Body(it, Tokens.TextMain) }
                currentTopic != null -> items(currentTopic.articles) { entry ->
                    BigButton(entry.title, ghost = true) { article = entry }
                }
                else -> items(drivingTopics) { entry ->
                    BigButton(entry.title, ghost = true) { topic = entry }
                }
            }
        }
    }
}

private val drivingTopics = listOf(
    WikiTopic("출발 전", listOf(
        WikiArticle("좌석과 거울", listOf(
            "브레이크를 끝까지 밟았을 때 무릎이 살짝 굽도록 좌석을 맞추세요.",
            "운전 자세를 잡은 뒤 룸미러와 옆 거울로 뒤쪽이 잘 보이게 맞추세요.",
        )),
        WikiArticle("안전띠", listOf(
            "어깨띠는 가슴을 가로지르게, 허리띠는 골반 위에 낮게 두세요.",
            "출발하기 전에 함께 탄 사람도 모두 안전띠를 맸는지 확인하세요.",
        )),
    )),
    WikiTopic("주행과 차로 변경", listOf(
        WikiArticle("안전거리", listOf(
            "앞차가 갑자기 멈춰도 충돌을 피할 수 있게 거리를 두세요. 노면이 미끄러우면 간격을 더 벌려요.",
        )),
        WikiArticle("차로 변경", listOf(
            "거울로 뒤를 보고 미리 깜빡이를 켜세요. 사각지대와 간격을 확인한 뒤 여유가 있을 때 옮겨요.",
            "옆 차로에 여유가 없으면 지금 차로를 유지하세요. 안전한 간격이 생기면 다시 확인해요.",
        )),
        WikiArticle("고속도로 출구를 지나쳤을 때", listOf(
            "고속도로 출구를 놓쳤다면 멈추거나 후진하지 말고, 다음 출구로 나가서 경로를 다시 확인하세요.",
        )),
    )),
    WikiTopic("교차로와 보행자", listOf(
        WikiArticle("우회전", listOf(
            "우회전 전용 신호등이 있으면 그 신호를 따르세요. 진행할 때도 횡단보도와 주변을 살펴요.",
            "우회전 전용 신호등이 없고 앞 차량 신호가 빨간불이면 먼저 정지선이나 횡단보도 등 정지해야 할 곳 앞에서 완전히 멈추세요. 다른 차량과 보행자의 통행을 방해하지 않을 때만 서행해 우회전해요.",
            "횡단보도를 건너는 사람이나 건너려는 사람이 있으면 횡단보도 앞에서 멈추세요.",
        )),
        WikiArticle("가려진 횡단보도", listOf(
            "가려진 쪽도 살펴요.",
            "횡단보도를 건너는 사람이나 건너려는 사람이 있으면 횡단보도 앞에서 멈추세요.",
        )),
        WikiArticle("어린이 보호구역", listOf(
            "어린이 보호구역의 신호등 없는 횡단보도에서는 사람이 보이지 않아도 먼저 멈추세요.",
        )),
    )),
    WikiTopic("날씨와 시야", listOf(
        WikiArticle("빗길", listOf(
            "빗길에서는 속도를 낮추고 앞차와 거리를 더 두세요. 급가속이나 급조향을 피하고 시야를 확보하세요.",
        )),
        WikiArticle("도로 살얼음", listOf(
            "다리 위와 터널 출입구처럼 얼기 쉬운 곳에선 미리 속도를 낮추고, 앞차와 간격을 더 벌리세요.",
        )),
        WikiArticle("침수된 지하차도", listOf(
            "침수된 지하차도는 피하세요.",
            "이미 들어갔다면 차를 두고 지체 없이 안전한 곳으로 대피하세요.",
        )),
    )),
    WikiTopic("주차", listOf(
        WikiArticle("후진 주차", listOf(
            "후진 전 주변에 사람이나 장애물이 없는지 확인하세요. 천천히 움직이고, 잘 안 보이면 먼저 멈춰요.",
            "차가 완전히 멈추면 주차 브레이크와 주차 위치를 차량 설명서에 맞게 설정하세요.",
        )),
    )),
    WikiTopic("돌발 상황", listOf(
        WikiArticle("고속도로에서 차가 멈췄을 때", listOf(
            "고속도로에서 차가 멈췄다면 비상등을 켜세요. 안전하게 내릴 수 있다면 모두 가드레일 밖 안전한 곳으로 대피한 뒤 신고하세요.",
            "신고할 때는 도로 이름과 진행 방향, 주변 표지와 다친 사람이 있는지를 알려 주세요.",
        )),
        WikiArticle("타이어가 갑자기 파열됐을 때", listOf(
            "핸들을 두 손으로 잡고 급브레이크를 피하세요. 차가 안정되면 서서히 속도를 줄여 안전한 곳에 정차하세요.",
        )),
        WikiArticle("철길 건널목에서 차가 멈췄을 때", listOf(
            "모두 차에서 내려 철길 밖으로 즉시 대피하세요. 안전한 곳에서 신고하고 건널목 위치를 알려 주세요.",
        )),
    )),
)
