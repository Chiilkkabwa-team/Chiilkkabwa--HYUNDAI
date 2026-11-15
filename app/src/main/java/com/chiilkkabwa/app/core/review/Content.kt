package com.chiilkkabwa.app.core.review

/**
 * 화면 문구·선택지 사전. 문구를 바꿀 때는 docs/04_UI_SPEC.md의 문구 표와 함께 고친다.
 * 원칙: 한 화면 한 행동, 짧게, 실패 대신 '한 번 더', 칭찬은 구체적으로.
 */
object Goals {
    val all = listOf("출발·정지", "주차", "차선 바꾸기", "좌회전", "그냥 몸풀기")
}

/** 멘토 약속 카드(최대 3개 선택) */
object PactOptions {
    val all = listOf(
        "멈출 땐 한 단어로 “멈춰”",
        "주차 땐 “핸들 반 바퀴”처럼 숫자로",
        "잘하면 바로 말해 주기",
        "한 번에 하나만 말하기",
        "조작하기 전에 먼저 물어보기",
        "큰 소리 내지 않기",
    )
    const val MAX = 3
}

/** 주차 후 복기 선택지 */
object ReviewChips {
    val learner = listOf("자신감 생겼어요", "후진이 아직 무서워요", "말이 너무 빨랐어요", "출발이 어려웠어요", "생각보다 괜찮았어요")
    val mentor = listOf("속도 조절이 좋아졌어", "핸들 감각이 늘었어", "주변 확인을 잘했어", "다음엔 후진 연습하자", "오늘은 쉬어 가자")
    val nextGoals = listOf("후진 주차 속도", "평행 주차 기준점", "출발할 때 부드럽게", "차선 바꾸기 전 확인", "좌회전 타이밍")
}
