package com.chiilkkabwa.app.core.achievement

import com.chiilkkabwa.app.core.mission.MissionId
import com.chiilkkabwa.app.core.mission.MissionResult
import com.chiilkkabwa.app.core.signal.SignalSource

/**
 * 성취 시스템(11/5 회의: 게임화보다 '뿌듯함', 주행 중 금지, 정차 후 짧게).
 * 원칙: 배지는 '한 행동을 해냈다'만 표시. 운전 실력 등급·순위는 만들지 않는다.
 * 모의로 얻은 배지는 mockOnly=true로 저장하고 화면에 '모의' 표시.
 */
enum class Badge(val title: String, val desc: String) {
    FIRST_CLICK("첫 딸깍", "처음으로 안전벨트 미션을 해냈어요"),
    WARMUP_DONE("운전석 워밍업", "워밍업 미션을 모두 마쳤어요"),
    SOFT_FOOT("살살 밟기", "페달을 초록 칸 안에서 2초 유지했어요"),
    PARKING_IMAGE("첫 주차 연습", "가상 주차를 끝까지 해 봤어요"),
    PROMISE("오늘의 약속", "멘토와 연습 약속을 정했어요"),
    FIRST_REVIEW("첫 복기", "연습 후 오늘을 돌아봤어요"),
    THREE_SESSIONS("세 번째 연습", "연습을 세 번 이어 갔어요"),
    GRADUATE("혼자 출발", "‘혼자 운전 시작’을 선택했어요 — 수료 카드가 만들어졌어요"),
    MENTOR_RETURN("이번엔 멘토", "다른 사람의 연습에 멘토로 함께했어요"),
}

data class EarnedBadge(val badge: Badge, val atMs: Long, val mockOnly: Boolean)

/** 연습 이력(기기 내 저장 단위) */
data class PracticeHistory(
    val missionResults: List<MissionResult> = emptyList(),
    val parkingSimCompleted: Int = 0,
    val pactsMade: Int = 0,
    val reviewsSaved: Int = 0,
    val graduated: Boolean = false,
    val mentorSessions: Int = 0,
    val badges: List<EarnedBadge> = emptyList(),
)

object AchievementRules {
    /** 새로 얻은 배지만 돌려준다. 호출 측에서 history.badges에 합친다. */
    fun newlyEarned(h: PracticeHistory, warmupIds: Set<MissionId>, nowMs: Long): List<EarnedBadge> {
        val have = h.badges.map { it.badge }.toSet()
        val out = mutableListOf<EarnedBadge>()
        fun add(b: Badge, mock: Boolean) { if (b !in have && out.none { it.badge == b }) out += EarnedBadge(b, nowMs, mock) }
        fun mockOnly(id: MissionId) = h.missionResults.filter { it.id == id }.all { it.source == SignalSource.MOCK }

        if (h.missionResults.any { it.id == MissionId.SEAT_BELT }) add(Badge.FIRST_CLICK, mockOnly(MissionId.SEAT_BELT))
        val done = h.missionResults.map { it.id }.toSet()
        if (warmupIds.isNotEmpty() && done.containsAll(warmupIds)) add(Badge.WARMUP_DONE, warmupIds.all { mockOnly(it) })
        if (MissionId.PEDAL_FEEL in done) add(Badge.SOFT_FOOT, mockOnly(MissionId.PEDAL_FEEL))
        if (h.parkingSimCompleted >= 1) add(Badge.PARKING_IMAGE, false)
        if (h.pactsMade >= 1) add(Badge.PROMISE, false)
        if (h.reviewsSaved >= 1) add(Badge.FIRST_REVIEW, false)
        if (h.reviewsSaved >= 3) add(Badge.THREE_SESSIONS, false)
        if (h.graduated) add(Badge.GRADUATE, false)
        if (h.mentorSessions >= 1) add(Badge.MENTOR_RETURN, false)
        return out
    }

    /** 레벨: 복기 횟수 기반 1~5. 실력이 아니라 '이어 간 연습 횟수'를 뜻한다. */
    fun level(h: PracticeHistory): Int = when {
        h.reviewsSaved >= 10 -> 5
        h.reviewsSaved >= 6 -> 4
        h.reviewsSaved >= 3 -> 3
        h.reviewsSaved >= 1 -> 2
        else -> 1
    }
}
