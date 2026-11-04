package com.chiilkkabwa.app.core.mission

import com.chiilkkabwa.app.core.signal.CarDimensions
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.tan

/**
 * 가상 주차(정차 중 전용 화면 속 연습). 실제 차량은 움직이지 않는다.
 * 단순 자전거 모델: 뒤축 중심 기준, 앞바퀴 조향각 = 핸들 각도 / steeringRatio.
 * 입력: 핸들 각도(실제 SteeringWheel 신호 또는 화면 슬라이더), 화면의 전진/후진 버튼(속도).
 *
 * 좌표: 미터, x 오른쪽, y 아래쪽(화면 좌표와 같음), heading 라디안(0 = 화면 위쪽을 향함).
 * steeringRatio·maxWheelDeg는 일반적인 승용차 근사값. 실제 차종 값이 있으면 교체.
 */
data class CarPose(val x: Double, val y: Double, val heading: Double)

data class ParkingSlot(val cx: Double, val cy: Double, val widthM: Double = 2.5, val lengthM: Double = 5.0)

class ParkingSim(
    val dims: CarDimensions,
    private val steeringRatio: Double = 15.0,
    private val maxWheelDeg: Double = 35.0,
) {
    val wheelbaseM: Double = (dims.wheelbaseMm ?: (dims.lengthMm * 0.58).toInt()) / 1000.0
    val lengthM = dims.lengthMm / 1000.0
    val widthM = dims.widthMm / 1000.0

    /** 핸들 각도(도) → 앞바퀴 각도(라디안), 기계적 한계로 자름 */
    fun wheelAngleRad(steeringDeg: Double): Double {
        val w = (steeringDeg / steeringRatio).coerceIn(-maxWheelDeg, maxWheelDeg)
        return w * PI / 180.0
    }

    /** speedMps: +전진 / -후진. dtSec 동안 이동 */
    fun step(p: CarPose, steeringDeg: Double, speedMps: Double, dtSec: Double): CarPose {
        val delta = wheelAngleRad(steeringDeg)
        val dist = speedMps * dtSec
        val dHeading = dist / wheelbaseM * tan(delta)
        val h = p.heading + dHeading
        // heading 0 = 위쪽(-y)
        return CarPose(p.x + dist * sin(h), p.y - dist * cos(h), h)
    }

    /** 주차 칸 안에 들어왔는지(중심 오차와 방향 오차 허용치) */
    fun isParked(p: CarPose, slot: ParkingSlot, posTolM: Double = 0.35, headingTolDeg: Double = 6.0): Boolean {
        val dx = abs(p.x - slot.cx); val dy = abs(p.y - slot.cy)
        var hd = (p.heading * 180 / PI) % 180.0
        if (hd < 0) hd += 180.0
        val headingErr = minOf(hd, 180.0 - hd) // 앞·뒤 방향 모두 허용(전면/후면 주차)
        return dx <= posTolM && dy <= posTolM && headingErr <= headingTolDeg
    }
}
