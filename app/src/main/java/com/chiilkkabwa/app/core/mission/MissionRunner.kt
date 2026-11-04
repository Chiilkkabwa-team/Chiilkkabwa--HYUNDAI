package com.chiilkkabwa.app.core.mission

import com.chiilkkabwa.app.core.signal.SignalSource
import com.chiilkkabwa.app.core.signal.VehicleSnapshot

/** 미션 결과 기록. source가 MOCK이면 배지·기록에 '모의'로 표시한다(T09). */
data class MissionResult(val id: MissionId, val source: SignalSource, val completedAtMs: Long, val metric: Float?)

data class RunnerState(
    val index: Int,
    val total: Int,
    val currentTitle: String?,
    val progress: Progress?,
    val results: List<MissionResult>,
    val finished: Boolean,
)

/**
 * 미션 묶음을 순서대로 진행한다. 순수 로직(안드로이드 의존 없음).
 * - pause(): 주행 제한 등으로 잠길 때 호출. 진행 중 미션은 완료 처리하지 않는다(T06).
 * - resume(): 다시 주차 확인 후 현재 미션을 '새로' 시작한다(T07).
 * - skipCurrent(): Unavailable일 때 사용자가 '건너뛰기' 또는 모의 연습으로 전환할 때.
 */
class MissionRunner(private val judges: List<MissionJudge>) {
    private var index = 0
    private var started = false
    private var paused = false
    private val results = mutableListOf<MissionResult>()
    private var last: Progress? = null

    fun start(s: VehicleSnapshot, nowMs: Long) {
        if (judges.isEmpty()) return
        started = true; paused = false
        judges[index].start(s, nowMs)
    }

    fun pause() { paused = true }

    fun resume(s: VehicleSnapshot, nowMs: Long) {
        if (!started || index >= judges.size) return
        paused = false
        judges[index].start(s, nowMs)
    }

    fun skipCurrent(s: VehicleSnapshot, nowMs: Long) {
        if (index >= judges.size) return
        index++
        if (index < judges.size) judges[index].start(s, nowMs)
        last = null
    }

    fun update(s: VehicleSnapshot, nowMs: Long): RunnerState {
        if (started && !paused && index < judges.size) {
            val p = judges[index].update(s, nowMs)
            last = p
            if (p is Progress.Done) {
                results += MissionResult(judges[index].id, p.source, nowMs, p.metric)
                index++
                if (index < judges.size) judges[index].start(s, nowMs)
            }
        }
        return state()
    }

    fun state() = RunnerState(
        index = index,
        total = judges.size,
        currentTitle = judges.getOrNull(index)?.title,
        progress = last,
        results = results.toList(),
        finished = index >= judges.size,
    )
}
