package com.chiilkkabwa.app.data.vehicle

import android.content.Context
import android.util.Log
import com.chiilkkabwa.app.core.signal.Signal
import com.chiilkkabwa.app.core.signal.SignalStatus

/**
 * Pleos Vehicle SDK (ai.pleos.playground:Vehicle:2.3.2, 문서 기준) — 읽기 전용.
 * 맡는 필드: driverBeltBuckled, passengerSeated(Seat) / brakePressed, (대체) parkingBrakeOn(Brake)
 *           turnSignal(TurnSignal) / wiperOn(Wiper) / lowBeamOn(Light) / steeringDeg(SteeringWheel)
 *           tripDistanceKm, tripTimeSec(Odometer) / carDimensions(CarInfo)
 *
 * ⚠️ 아래 SDK 호출은 공식 문서의 '패턴'(Vehicle(context) → initialize() → getXxx() → checkXxxCapabilities
 *    → getXxx(onComplete, onFailed) / registerXxx(listener))으로 적은 의사코드다.
 *    정확한 클래스·메서드·리스너·값 타입은 Gradle Sync 후 자동완성과 docs/03 링크로 확인해 TODO(sdk)를 채운다.
 *    채우기 전까지는 모든 필드를 UNSUPPORTED로 표시해 앱이 '모의 연습'으로 동작하게 한다(앱이 멈추지 않음).
 *
 * 권한(AndroidManifest): pleos.car.permission.CAR_SEATS / CAR_DRIVE / CAR_LIGHTS / CAR_WINDOWS / CAR_INFO
 *   - SteeringWheel 권한 상수는 문서 매핑에 없음 → TODO(day1) 확인
 * 미승인 권한 호출 시 onFailed → VehicleException(ERROR_SECURITY_PERMISSION) → PERMISSION_DENIED로 표시.
 * release()는 비동기 콜백이 끝난 뒤 호출(문서 주의사항).
 */
class PleosVehicleSource(private val context: Context) : VehicleSource {
    private var sink: SnapshotSink? = null
    private val label = VehicleRepository.realSourceLabel()
    // private var vehicle: ai.pleos.playground.vehicle.Vehicle? = null   // TODO(sdk)

    override fun start(sink: SnapshotSink) {
        this.sink = sink
        val at = System.currentTimeMillis()
        try {
            // TODO(sdk) ── 연결 예시(문서 패턴, 시그니처 확인 필요) ─────────────────────────
            // val v = Vehicle(context).also { it.initialize() }; vehicle = v
            //
            // // 1) 안전벨트
            // val seat = v.getSeat()
            // seat.checkSeatBeltBuckledStateCapabilities(onComplete = { config, areaMap -> /* 운전석 area 확인 */ },
            //     onFailed = { e -> mark { it.copy(driverBeltBuckled = fail(e)) } })
            // seat.registerSeatBeltBuckledState(area = /* 운전석 */, listener = object : SeatBeltBuckledStateListener {
            //     override fun onStateUpdated(buckled: Boolean?) = mark { it.copy(driverBeltBuckled = ok(buckled)) }
            //     override fun onFailed(e: Exception) = mark { it.copy(driverBeltBuckled = fail(e)) }
            // })
            // seat.registerSeatOccupancy(area = /* 조수석 */, ...) → passengerSeated
            //
            // // 2) 브레이크 페달 상태(밟음/뗌) — 값 타입(Boolean? Int? enum?) Day1 확인
            // val brake = v.getBrake()
            // brake.checkBrakePedalStatusCapabilities(...)
            // brake.getCurrentBrakePedalStatus(onComplete = { st -> mark { it.copy(brakePressed = ok(st.isPressed())) } }, onFailed = ...)
            // brake.registerBrakePedalStatus(listener = ...)
            // // AAOS PARKING_BRAKE_ON 미지원일 때만 Brake의 주차 브레이크 값을 parkingBrakeOn에 기록
            //
            // // 3) 방향지시등 / 와이퍼 / 전조등
            // v.getTurnSignal().registerTurnSignalState(...) → turnSignal (LEFT/RIGHT/NONE/HAZARD 매핑)
            // v.getWiper().registerWiperState(...) → wiperOn
            // v.getLight().register...LowBeam...(...) → lowBeamOn
            //
            // // 4) 핸들 각도 — 부호(왼쪽 음수?)와 단위(도) 확인 후 정규화
            // v.getSteeringWheel().registerSteeringWheelAngle(...) → steeringDeg
            //
            // // 5) 트립 거리·시간(주행 전후 차이로 '오늘 연습')
            // v.getOdometer().getTripCurrentDistance(...) / getTripCurrentDrivingTime(...) → tripDistanceKm / tripTimeSec
            //
            // // 6) 차체 크기(가상 주차)
            // v.getCarInfo().get...(...) → carDimensions (필드가 없으면 CarDimensions.COMPACT_SEDAN_DEFAULT)
            // ─────────────────────────────────────────────────────────────────────────
            throw NotImplementedError("Pleos SDK 연결 전")
        } catch (e: Throwable) {
            Log.w(TAG, "Pleos SDK not wired yet: ${e.message}")
            sink.update {
                it.copy(
                    driverBeltBuckled = Signal.failed(SignalStatus.UNSUPPORTED, label, at),
                    brakePressed = Signal.failed(SignalStatus.UNSUPPORTED, label, at),
                    turnSignal = Signal.failed(SignalStatus.UNSUPPORTED, label, at),
                    wiperOn = Signal.failed(SignalStatus.UNSUPPORTED, label, at),
                    lowBeamOn = Signal.failed(SignalStatus.UNSUPPORTED, label, at),
                    steeringDeg = Signal.failed(SignalStatus.UNSUPPORTED, label, at),
                    passengerSeated = Signal.failed(SignalStatus.UNSUPPORTED, label, at),
                    tripDistanceKm = Signal.failed(SignalStatus.UNSUPPORTED, label, at),
                    tripTimeSec = Signal.failed(SignalStatus.UNSUPPORTED, label, at),
                    carDimensions = Signal.failed(SignalStatus.UNSUPPORTED, label, at),
                )
            }
        }
    }

    override fun stop() {
        // TODO(sdk): 각 register에 대응하는 unregister 호출 → 콜백 완료 후 vehicle?.release()
        sink = null
    }

    companion object { private const val TAG = "PleosVehicleSource" }
}
