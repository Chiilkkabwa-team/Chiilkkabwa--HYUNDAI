package com.chiilkkabwa.app.core.safety

import com.chiilkkabwa.app.core.signal.Gear
import com.chiilkkabwa.app.core.signal.SignalStatus
import com.chiilkkabwa.app.core.signal.VehicleSnapshot

/** 안전 게이트 판정 결과 */
sealed interface GateState {
    data object Ready : GateState
    data class Locked(val reason: LockReason) : GateState
}

enum class LockReason(val message: String) {
    DRIVING_RESTRICTED("연습 중 — 화면은 잠시 쉴게요. 도로만 보세요."),
    NOT_IN_PARK("기어를 P에 두면 시작할 수 있어요."),
    PARKING_BRAKE_OFF("주차 브레이크를 걸면 시작할 수 있어요."),
    UNKNOWN_STATE("차량 상태를 확인하고 있어요. 기어 P와 주차 브레이크를 확인해 주세요."),
}

/**
 * 주차 판정 정책.
 * - requireParkingBrake: 기본 true(주차 브레이크까지 확인).
 * - allowWhenParkingBrakeUnsupported: 차량/에뮬레이터가 주차 브레이크 신호를 '지원하지 않음'으로
 *   명확히 알려줄 때만 기어 P + 주행 제한 없음으로 허용할지. 기본 false(팀 결정 사항, docs/02 참고).
 *   UNKNOWN(아직 못 받음)·DISCONNECTED는 어떤 정책에서도 허용하지 않는다.
 */
data class GatePolicy(
    val requireParkingBrake: Boolean = true,
    val allowWhenParkingBrakeUnsupported: Boolean = false,
)

/**
 * 순수 함수. 규칙:
 * 1) 주행 제한 신호가 true면 무조건 DRIVING_RESTRICTED (가장 우선)
 * 2) 기어·주행제한 신호를 못 믿으면 UNKNOWN_STATE (모르면 잠근다)
 * 3) 기어 P가 아니면 NOT_IN_PARK (속도 0만으로 주차로 보지 않는다)
 * 4) 주차 브레이크 확인(정책에 따라)
 */
object SafetyGate {
    fun evaluate(s: VehicleSnapshot, policy: GatePolicy = GatePolicy()): GateState {
        val ux = s.uxRestricted
        if (ux.isUsable && ux.value == true) return GateState.Locked(LockReason.DRIVING_RESTRICTED)
        if (!ux.isUsable || !s.gear.isUsable) return GateState.Locked(LockReason.UNKNOWN_STATE)
        if (s.gear.value != Gear.PARK) return GateState.Locked(LockReason.NOT_IN_PARK)

        if (policy.requireParkingBrake) {
            val pb = s.parkingBrakeOn
            when {
                pb.isUsable && pb.value == true -> Unit
                pb.isUsable && pb.value == false -> return GateState.Locked(LockReason.PARKING_BRAKE_OFF)
                pb.status == SignalStatus.UNSUPPORTED && policy.allowWhenParkingBrakeUnsupported -> Unit
                else -> return GateState.Locked(LockReason.UNKNOWN_STATE)
            }
        }
        return GateState.Ready
    }
}
