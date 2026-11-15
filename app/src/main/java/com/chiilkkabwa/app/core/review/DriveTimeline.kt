package com.chiilkkabwa.app.core.review

import com.chiilkkabwa.app.core.event.SpeedChangeEvent
import com.chiilkkabwa.app.core.event.SpeedDetectionResult
import com.chiilkkabwa.app.core.event.SpeedEventBridge
import com.chiilkkabwa.app.core.event.SpeedEventType
import com.chiilkkabwa.app.core.signal.SignalSource
import com.chiilkkabwa.app.core.signal.VehicleSpeedObservation

/** C01: 주차 후 복기에 보여 줄 속도 시간표. tSec은 이번 주행의 첫 표본부터 잰 초 */
data class TimelinePoint(val tSec: Float, val kmh: Float)
data class TimelineMark(val tSec: Float, val fromKmh: Float, val toKmh: Float, val type: SpeedEventType, val eventId: String)
data class DriveTimeline(
    val points: List<TimelinePoint> = emptyList(),
    val marks: List<TimelineMark> = emptyList(),
    val sources: Set<SignalSource> = emptySet(),
) {
    val hasData: Boolean get() = points.size >= 2
}

/**
 * 주행 중 스냅샷의 속도 관측을 모은다. 새 수집 경로를 만들지 않는다(S05):
 * A02가 켜 둔 속도 신호가 들어올 때만 쌓이고, 꺼져 있으면 빈 시간표가 된다.
 * 사건 판정은 A02 [SpeedEventBridge]를 그대로 쓴다(임계값·거부 규칙이 A02와 같다).
 * 메모리에만 두고 저장하지 않는다(S09).
 */
class DriveTimelineRecorder(private val stepNanos: Long = 1_000_000_000L, private val maxPoints: Int = 3_600) {
    private val bridge = SpeedEventBridge()
    private var sessionId: String? = null
    private var firstNanos: Long? = null
    private var lastSampleNanos: Long? = null
    private var lastPointNanos: Long? = null
    private val points = ArrayList<TimelinePoint>()
    private val marks = ArrayList<TimelineMark>()
    private val sources = LinkedHashSet<SignalSource>()

    fun start() {
        bridge.stop(); sessionId = null; firstNanos = null; lastSampleNanos = null; lastPointNanos = null
        points.clear(); marks.clear(); sources.clear()
    }

    /** 새로 판정된 A02 사건이 있으면 돌려준다(mock 음성 길잡이가 같은 사건을 쓴다). */
    fun accept(o: VehicleSpeedObservation, nowNanos: Long): SpeedChangeEvent? {
        val kmh = o.kilometersPerHour ?: return null
        val ts = o.timestampNanos ?: return null
        // 스냅샷은 속도와 무관한 값이 바뀔 때도 다시 온다. 같은 표본을 다시 넣으면 A02 엔진이 기준을 초기화해 사건을 놓친다
        if (o.sessionId == sessionId && ts == lastSampleNanos) return null
        if (o.sessionId != sessionId) { sessionId = o.sessionId; bridge.start(o.sessionId) }   // 새 구독 = 새 기준(A02 규칙)
        lastSampleNanos = ts
        val t0 = firstNanos ?: ts.also { firstNanos = it }
        val sec = { n: Long -> (n - t0) / 1e9f }

        val event = (bridge.accept(o, nowNanos) as? SpeedDetectionResult.Evaluated)?.event?.also { e ->
            marks += TimelineMark(sec(ts), (e.evidence.previousMps * 3.6).toFloat(), (e.evidence.currentMps * 3.6).toFloat(), e.type, e.eventId)
        }
        val last = lastPointNanos
        if (points.size < maxPoints && (last == null || ts - last >= stepNanos)) {
            points += TimelinePoint(sec(ts), kmh.toFloat()); lastPointNanos = ts; sources += o.source
        }
        return event
    }

    fun snapshot() = DriveTimeline(points.toList(), marks.toList(), sources.toSet())
}
