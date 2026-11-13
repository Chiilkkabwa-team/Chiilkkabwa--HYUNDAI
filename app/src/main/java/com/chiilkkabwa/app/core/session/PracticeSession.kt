package com.chiilkkabwa.app.core.session

import com.chiilkkabwa.app.core.safety.GateState
import com.chiilkkabwa.app.core.safety.LockReason
import com.chiilkkabwa.app.core.signal.VehicleSnapshot

/**
 * 연습 세션의 큰 흐름(별지 B 상태 전이도와 동일).
 *
 *  CHECKING ──(게이트 Ready)──▶ READY ──(사용자 시작)──▶ PREPARING(목표·워밍업·가상주차·약속)
 *      ▲                                                   │
 *      │(재주차 확인, 주행 기록 없음)                        │(주행 제한/기어 D)
 *      │                                                   ▼
 *    REVIEW ◀──(재주차 확인, 주행 기록 있음)── DRIVING_LOCK
 *
 * 어느 상태에서든 게이트가 DRIVING_RESTRICTED 또는 (준비 이후) NOT_IN_PARK면 DRIVING_LOCK.
 * 준비 전(CHECKING/READY)에서 잠기면 CHECKING에 이유를 표시한다.
 */
enum class Phase { CHECKING, READY, PREPARING, DRIVING_LOCK, REVIEW }

data class TripMark(val distanceKm: Float?, val tripTimeSec: Long?, val wallClockMs: Long)

data class SessionState(
    val phase: Phase = Phase.CHECKING,
    val lockReason: LockReason? = LockReason.UNKNOWN_STATE,
    /** 주행 시작 시점의 트립값(Odometer). 주차 후 복기에서 차이를 계산 */
    val tripStart: TripMark? = null,
    val tripEnd: TripMark? = null,
)

object SessionReducer {
    /** 게이트 판정이 바뀔 때마다 호출. 순수 함수. */
    fun onGate(state: SessionState, gate: GateState, s: VehicleSnapshot, nowMs: Long): SessionState {
        val locked = gate as? GateState.Locked
        val drivingLike = locked != null && (locked.reason == LockReason.DRIVING_RESTRICTED || locked.reason == LockReason.NOT_IN_PARK)
        return when (state.phase) {
            Phase.CHECKING, Phase.READY ->
                if (locked == null) state.copy(phase = Phase.READY, lockReason = null)
                else state.copy(phase = Phase.CHECKING, lockReason = locked.reason)

            Phase.PREPARING ->
                when {
                    // 주차 브레이크를 다시 걸어 Ready로 돌아오면 일시 정지 이유를 지운다(지우지 않으면 '멈춤'이 풀리지 않음)
                    locked == null -> if (state.lockReason != null) state.copy(lockReason = null) else state
                    drivingLike -> state.copy(phase = Phase.DRIVING_LOCK, lockReason = locked.reason, tripStart = mark(s, nowMs), tripEnd = null)
                    // 주차 브레이크만 풀렸거나 상태 불명: 연습은 멈추고 확인 화면으로
                    else -> state.copy(lockReason = locked.reason)
                }

            Phase.DRIVING_LOCK ->
                if (locked == null) state.copy(phase = Phase.REVIEW, lockReason = null, tripEnd = mark(s, nowMs))
                else state.copy(lockReason = locked.reason)

            // E3(C01): 주행이 아닌 잠금(주차 브레이크 해제·신호 불명)도 복기를 잠근다.
            // 단계는 REVIEW로 남겨 작성 중인 복기를 지키고, 다시 주차가 확인되면 lockReason만 지운다.
            Phase.REVIEW ->
                when {
                    locked == null -> if (state.lockReason != null) state.copy(lockReason = null) else state
                    drivingLike -> state.copy(phase = Phase.DRIVING_LOCK, lockReason = locked.reason, tripStart = mark(s, nowMs), tripEnd = null)
                    else -> if (state.lockReason == locked.reason) state else state.copy(lockReason = locked.reason)
                }
        }
    }

    /** 사용자가 '연습 시작'을 누름(READY에서만) */
    fun onStartPressed(state: SessionState): SessionState =
        if (state.phase == Phase.READY) state.copy(phase = Phase.PREPARING) else state

    /** 복기 저장 후 다음 연습 준비로 */
    fun onReviewDone(state: SessionState): SessionState =
        state.copy(phase = Phase.PREPARING, tripStart = null, tripEnd = null)

    private fun mark(s: VehicleSnapshot, nowMs: Long) = TripMark(
        distanceKm = s.tripDistanceKm.takeIf { it.isUsable }?.value,
        tripTimeSec = s.tripTimeSec.takeIf { it.isUsable }?.value,
        wallClockMs = nowMs,
    )
}

/**
 * 오늘 연습 요약. 트립 신호가 없으면 앱이 잰 시간(wall clock)으로 대체하고 그 사실을 표시한다.
 * 트립 카운터가 주행 중 리셋되면(끝 < 시작) 거리는 null로 둔다.
 */
data class PracticeSummary(val distanceKm: Float?, val durationMin: Int, val durationFromVehicle: Boolean)

fun summarize(start: TripMark?, end: TripMark?): PracticeSummary? {
    if (start == null || end == null) return null
    val dist = if (start.distanceKm != null && end.distanceKm != null && end.distanceKm >= start.distanceKm) end.distanceKm - start.distanceKm else null
    val vehSec = if (start.tripTimeSec != null && end.tripTimeSec != null && end.tripTimeSec >= start.tripTimeSec) end.tripTimeSec - start.tripTimeSec else null
    val sec = vehSec ?: ((end.wallClockMs - start.wallClockMs) / 1000)
    return PracticeSummary(dist, (sec / 60).toInt(), vehSec != null)
}
