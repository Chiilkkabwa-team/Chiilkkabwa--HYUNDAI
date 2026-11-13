package com.chiilkkabwa.app.core.mission

import com.chiilkkabwa.app.core.signal.Signal
import com.chiilkkabwa.app.core.signal.SignalSource
import com.chiilkkabwa.app.core.signal.SignalStatus
import com.chiilkkabwa.app.core.signal.TurnSignalState
import com.chiilkkabwa.app.core.signal.VehicleSnapshot
import kotlin.math.abs
import kotlin.math.sqrt

enum class MissionId {
    SEAT_BELT, BRAKE_PRESS_RELEASE, TURN_SIGNAL, WIPER, LOW_BEAM, STEERING_CENTER, PEDAL_FEEL,
}

/** 미션 진행 상태. UI는 이것만 보고 그린다. */
sealed interface Progress {
    /** step/total: 몇 단계 중 몇 번째, hint: 지금 할 행동(한 문장) */
    data class InProgress(val step: Int, val total: Int, val hint: String) : Progress
    /** source: 성공을 확인한 신호 출처(기록·배지에 표시) */
    data class Done(val source: SignalSource, val praise: String, val metric: Float? = null) : Progress
    /** 필요한 신호를 쓸 수 없음 → UI는 '화면 연습(모의)' 전환을 제안 */
    data class Unavailable(val status: SignalStatus) : Progress
}

/**
 * 미션 판정기. 규칙(미션 공통):
 * - start() 이후에 '새로 받은' 값만 판정에 쓴다(receivedAtMs >= startedAtMs, 그리고 값이 바뀐 경우).
 *   → 미션 시작 전부터 밟고 있던 브레이크, 이전 세션 값으로 완료되지 않는다(T04, T07).
 * - 필요한 신호가 UNSUPPORTED/PERMISSION_DENIED/DISCONNECTED면 Unavailable.
 */
interface MissionJudge {
    val id: MissionId
    val title: String
    fun start(s: VehicleSnapshot, nowMs: Long)
    fun update(s: VehicleSnapshot, nowMs: Long): Progress
}

private fun Signal<*>.blockingStatus(): SignalStatus? = when (status) {
    SignalStatus.UNSUPPORTED, SignalStatus.PERMISSION_DENIED, SignalStatus.DISCONNECTED -> status
    else -> null
}

/** 값 변화(edge)를 '시작 이후'로만 추적하는 도우미 */
class EdgeTracker<T> {
    private var startedAt = 0L
    private var last: T? = null
    private var lastAt = -1L
    fun reset(sig: Signal<T>, nowMs: Long) { startedAt = nowMs; last = sig.value; lastAt = sig.receivedAtMs }
    /** 시작 이후 새로 바뀐 값이면 그 값을, 아니면 null */
    fun newValue(sig: Signal<T>): T? {
        if (!sig.isUsable) return null
        if (sig.receivedAtMs < startedAt) return null
        if (sig.receivedAtMs == lastAt && sig.value == last) return null
        val changed = sig.value != last
        last = sig.value; lastAt = sig.receivedAtMs
        return if (changed) sig.value else null
    }
}

/** 안전벨트는 체결 상태 확인 미션(S08).
 * 이미 착용한 경우에도 그대로 완료하며, 다시 풀어 매게 하지 않는다.
 * 다른 조작 미션의 '시작 이후 값 변화' 판정은 유지한다.
 */
class SeatBeltJudge : MissionJudge {
    override val id = MissionId.SEAT_BELT
    override val title = "안전벨트 매기"
    override fun start(s: VehicleSnapshot, nowMs: Long) {
        // 벨트는 조작 이력이 아닌 현재 유효 신호를 update()에서 확인한다.
    }
    override fun update(s: VehicleSnapshot, nowMs: Long): Progress {
        val belt = s.driverBeltBuckled
        belt.blockingStatus()?.let { return Progress.Unavailable(it) }
        if (!belt.isUsable) {
            return Progress.InProgress(1, 1, "안전벨트 상태를 확인 중이에요. 이미 맸다면 그대로 유지해 주세요.")
        }
        return if (belt.value == true) {
            Progress.Done(belt.source, "안전벨트가 체결되어 있어요. 그대로 유지해 주세요.")
        } else {
            Progress.InProgress(1, 1, "안전벨트를 매 주세요. 이미 맸다면 풀지 말고 그대로 두세요.")
        }
    }
}

/** 브레이크: 밟음 → 뗌 (시작 이후 새 이벤트만). 연속값이 있으면 최대 압력을 metric으로 기록 */
class BrakePressReleaseJudge : MissionJudge {
    override val id = MissionId.BRAKE_PRESS_RELEASE
    override val title = "브레이크 끝까지 밟고 천천히 떼기"
    private val edge = EdgeTracker<Boolean>()
    private var pressed = false
    private var maxPct: Float? = null
    override fun start(s: VehicleSnapshot, nowMs: Long) { edge.reset(s.brakePressed, nowMs); pressed = false; maxPct = null }
    override fun update(s: VehicleSnapshot, nowMs: Long): Progress {
        s.brakePressed.blockingStatus()?.let { return Progress.Unavailable(it) }
        if (pressed && s.brakePedalPct.isUsable) maxPct = maxOf(maxPct ?: 0f, s.brakePedalPct.value!!)
        when (edge.newValue(s.brakePressed)) {
            true -> pressed = true
            false -> if (pressed) return Progress.Done(s.brakePressed.source, "끝까지 꾹, 그리고 천천히 떼기 — 좋아요", maxPct)
            null -> Unit
        }
        return if (!pressed) Progress.InProgress(1, 2, "오른발로 브레이크를 끝까지 밟아 주세요")
        else Progress.InProgress(2, 2, "좋아요. 이제 천천히 떼 볼까요?")
    }
}

/** 방향지시등: 왼쪽 → 오른쪽 → 끄기 */
class TurnSignalJudge : MissionJudge {
    override val id = MissionId.TURN_SIGNAL
    override val title = "방향지시등 왼쪽 → 오른쪽"
    private val edge = EdgeTracker<TurnSignalState>()
    private var step = 0 // 0: 왼쪽 대기, 1: 오른쪽 대기, 2: 끄기 대기
    override fun start(s: VehicleSnapshot, nowMs: Long) { edge.reset(s.turnSignal, nowMs); step = 0 }
    override fun update(s: VehicleSnapshot, nowMs: Long): Progress {
        s.turnSignal.blockingStatus()?.let { return Progress.Unavailable(it) }
        val v = edge.newValue(s.turnSignal)
        when {
            step == 0 && v == TurnSignalState.LEFT -> step = 1
            step == 1 && v == TurnSignalState.RIGHT -> step = 2
            step == 2 && v == TurnSignalState.NONE -> return Progress.Done(s.turnSignal.source, "깜빡이, 손가락 하나로 충분해요")
        }
        val hint = when (step) { 0 -> "왼쪽 방향지시등을 켜 보세요(레버 아래로)"; 1 -> "이번엔 오른쪽(레버 위로)"; else -> "이제 꺼 볼까요?" }
        return Progress.InProgress(step + 1, 3, hint)
    }
}

/** 스위치형(와이퍼·전조등): 상태가 한 번 바뀌면 성공 */
class ToggleJudge(
    override val id: MissionId,
    override val title: String,
    private val pick: (VehicleSnapshot) -> Signal<Boolean>,
    private val hint: String,
    private val praise: String,
) : MissionJudge {
    private val edge = EdgeTracker<Boolean>()
    override fun start(s: VehicleSnapshot, nowMs: Long) = edge.reset(pick(s), nowMs)
    override fun update(s: VehicleSnapshot, nowMs: Long): Progress {
        val sig = pick(s)
        sig.blockingStatus()?.let { return Progress.Unavailable(it) }
        return if (edge.newValue(sig) != null) Progress.Done(sig.source, praise) else Progress.InProgress(1, 1, hint)
    }
}

/** 핸들 정렬: |각도| <= toleranceDeg 를 holdMs 동안 유지 */
class SteeringCenterJudge(private val toleranceDeg: Float = 5f, private val holdMs: Long = 2000) : MissionJudge {
    override val id = MissionId.STEERING_CENTER
    override val title = "핸들 똑바로 맞추기"
    private var startedAt = 0L
    private var inRangeSince: Long? = null
    override fun start(s: VehicleSnapshot, nowMs: Long) { startedAt = nowMs; inRangeSince = null }
    override fun update(s: VehicleSnapshot, nowMs: Long): Progress {
        val sig = s.steeringDeg
        sig.blockingStatus()?.let { return Progress.Unavailable(it) }
        if (sig.isUsable && sig.receivedAtMs >= startedAt && abs(sig.value!!) <= toleranceDeg) {
            val since = inRangeSince ?: nowMs.also { inRangeSince = it }
            if (nowMs - since >= holdMs) return Progress.Done(sig.source, "바퀴가 똑바로! 출발 전에 늘 확인해요")
        } else if (sig.isUsable && abs(sig.value!!) > toleranceDeg) inRangeSince = null
        return Progress.InProgress(1, 1, "핸들을 가운데로 맞추고 2초 기다려요")
    }
}

/**
 * 페달 감각 '살살 밟기': 압력이 [low, high] 구간에 holdMs 연속으로 머물면 성공.
 * metric = 유지 구간 표준편차(작을수록 부드러움) → '방금보다 부드러웠어요' 비교에 사용.
 * 이 값들은 연습용 설정이며 실제 운전 기준이 아니다(문구에도 표시).
 * usePedal: ACCEL 또는 BRAKE. 가속 페달은 '차가 구동되지 않는 조건' 확인 전에는 쓰지 않는다(docs/02).
 */
class PedalHoldJudge(
    private val pick: (VehicleSnapshot) -> Signal<Float>,
    private val low: Float = 20f,
    private val high: Float = 35f,
    private val holdMs: Long = 2000,
) : MissionJudge {
    override val id = MissionId.PEDAL_FEEL
    override val title = "초록 칸 안에서 2초 버티기"
    private var startedAt = 0L
    private var since: Long? = null
    private val samples = ArrayList<Float>()
    override fun start(s: VehicleSnapshot, nowMs: Long) { startedAt = nowMs; since = null; samples.clear() }
    override fun update(s: VehicleSnapshot, nowMs: Long): Progress {
        val sig = pick(s)
        sig.blockingStatus()?.let { return Progress.Unavailable(it) }
        if (sig.isUsable && sig.receivedAtMs >= startedAt) {
            val v = sig.value!!
            if (v in low..high) {
                val st = since ?: nowMs.also { since = it }
                samples += v
                if (nowMs - st >= holdMs) return Progress.Done(sig.source, "부드러웠어요!", stdDev(samples))
            } else { since = null; samples.clear() }
        }
        return Progress.InProgress(1, 1, "초록 칸(${low.toInt()}~${high.toInt()}%) 안에서 2초 버텨 보세요")
    }
    private fun stdDev(xs: List<Float>): Float {
        if (xs.size < 2) return 0f
        val m = xs.average()
        return sqrt(xs.sumOf { (it - m) * (it - m) } / xs.size).toFloat()
    }
}

/** 기본 워밍업 묶음(MVP 필수 3 + 조건부). 조건부는 지원 여부에 따라 UI가 건너뛴다. */
object MissionCatalog {
    fun warmupRequired(): List<MissionJudge> = listOf(SeatBeltJudge(), BrakePressReleaseJudge(), TurnSignalJudge())
    fun warmupOptional(): List<MissionJudge> = listOf(
        ToggleJudge(MissionId.WIPER, "와이퍼 켜고 끄기", { it.wiperOn }, "와이퍼 레버를 한 번 움직여 보세요", "비 오는 날 이 레버, 기억해 둬요"),
        ToggleJudge(MissionId.LOW_BEAM, "전조등 켜기", { it.lowBeamOn }, "전조등 스위치를 돌려 보세요", "어두워지면 여기!"),
        SteeringCenterJudge(),
    )
}
