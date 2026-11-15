package com.chiilkkabwa.app.data.vehicle

import com.chiilkkabwa.app.core.signal.Signal
import com.chiilkkabwa.app.core.signal.SignalSource
import com.chiilkkabwa.app.core.signal.SignalStatus

/**
 * AAOS 콜백 값 검증. Android 프레임워크 의존 없는 순수 함수라 JVM 테스트 가능하다.
 *
 * STATUS_AVAILABLE이더라도 값 타입이 잘못되었거나 범위를 벗어나면 성공으로 쓰지 않는다.
 * STATUS_UNAVAILABLE/ERROR는 이전 값을 재사용하지 않고 안전하게 STALE로 내려간다.
 */
internal fun <T : Any> aaosObserved(
    raw: Any?,
    available: Boolean,
    source: SignalSource,
    atMs: Long,
    convert: (Any) -> T?,
): Signal<T> {
    if (!available) return Signal.failed(SignalStatus.STALE, source, atMs)
    val value = raw?.let(convert) ?: return Signal.failed(SignalStatus.UNKNOWN, source, atMs)
    return Signal.ok(value, source, atMs)
}

/** 페달 입력이 확인된 공식 속성에서 온 수치일 때만 0~100%로 수용한다. */
internal fun pedalPercent(raw: Any): Float? =
    (raw as? Number)?.toFloat()?.takeIf { it.isFinite() && it >= 0f && it <= 100f }
