package com.chiilkkabwa.app.data.vehicle

import android.car.Car
import android.car.VehicleGear
import android.car.VehiclePropertyIds
import android.car.drivingstate.CarUxRestrictions
import android.car.drivingstate.CarUxRestrictionsManager
import android.car.hardware.CarPropertyValue
import android.car.hardware.property.CarPropertyManager
import android.content.Context
import android.content.pm.PackageManager
import android.util.Log
import com.chiilkkabwa.app.core.signal.Gear
import com.chiilkkabwa.app.core.signal.Signal
import com.chiilkkabwa.app.core.signal.SignalStatus

/**
 * AAOS 표준 Car API (안드로이드 공식 API — 이름이 실제와 같다).
 * 맡는 필드: uxRestricted, gear, parkingBrakeOn(대체), brakePedalPct, accelPedalPct.
 *
 * 권한:
 *  - GEAR_SELECTION, PARKING_BRAKE_ON: android.car.permission.CAR_POWERTRAIN (normal)
 *  - *_PEDAL_COMPRESSION_PERCENTAGE: android.car.permission.READ_CAR_PEDALS (dangerous → 런타임 요청)
 * 지원 여부: getCarPropertyConfig(id) == null 이면 UNSUPPORTED.
 * TODO(day1): 에뮬레이터에서 getPropertyList() 결과를 로그로 남기고 docs/03 표를 갱신할 것.
 */
class AaosVehicleSource(private val context: Context) : VehicleSource {
    private var car: Car? = null
    private var props: CarPropertyManager? = null
    private var ux: CarUxRestrictionsManager? = null
    private var sink: SnapshotSink? = null
    private val label = VehicleRepository.realSourceLabel()

    private val callback = object : CarPropertyManager.CarPropertyEventCallback {
        override fun onChangeEvent(v: CarPropertyValue<*>) {
            val at = System.currentTimeMillis()
            val okStatus = v.status == CarPropertyValue.STATUS_AVAILABLE
            sink?.update { s ->
                when (v.propertyId) {
                    VehiclePropertyIds.GEAR_SELECTION ->
                        s.copy(gear = if (okStatus) Signal.ok(mapGear(v.value as Int), label, at) else Signal.failed(SignalStatus.STALE, label, at))
                    VehiclePropertyIds.PARKING_BRAKE_ON ->
                        // 표준 데이터는 AAOS 우선(Pleos SDK API 정책). Pleos Brake의 주차 브레이크는 AAOS 미지원 시 대체로만 사용
                        s.copy(parkingBrakeOn = if (okStatus) Signal.ok(v.value as Boolean, label, at) else Signal.failed(SignalStatus.STALE, label, at))
                    PROP_BRAKE_PEDAL ->
                        s.copy(brakePedalPct = if (okStatus) Signal.ok((v.value as Float), label, at) else Signal.failed(SignalStatus.STALE, label, at))
                    PROP_ACCEL_PEDAL ->
                        s.copy(accelPedalPct = if (okStatus) Signal.ok((v.value as Float), label, at) else Signal.failed(SignalStatus.STALE, label, at))
                    else -> s
                }
            }
        }
        override fun onErrorEvent(propId: Int, areaId: Int) {
            Log.w(TAG, "property error $propId/$areaId")
            val at = System.currentTimeMillis()
            sink?.update { s ->
                when (propId) {
                    VehiclePropertyIds.GEAR_SELECTION -> s.copy(gear = Signal.failed(SignalStatus.DISCONNECTED, label, at))
                    else -> s
                }
            }
        }
    }

    private val uxListener = CarUxRestrictionsManager.OnUxRestrictionsChangedListener { r: CarUxRestrictions ->
        val at = System.currentTimeMillis()
        sink?.update { it.copy(uxRestricted = Signal.ok(r.isRequiresDistractionOptimization, label, at)) }
    }

    override fun start(sink: SnapshotSink) {
        this.sink = sink
        val c = Car.createCar(context) ?: run { markAll(SignalStatus.DISCONNECTED); return }
        car = c
        props = c.getCarManager(Car.PROPERTY_SERVICE) as? CarPropertyManager
        ux = c.getCarManager(Car.CAR_UX_RESTRICTION_SERVICE) as? CarUxRestrictionsManager

        // 주행 제한: 현재값 + 변화 구독
        ux?.let { m ->
            m.registerListener(uxListener)
            m.currentCarUxRestrictions?.let { r ->
                sink.update { it.copy(uxRestricted = Signal.ok(r.isRequiresDistractionOptimization, label, System.currentTimeMillis())) }
            }
        } ?: sink.update { it.copy(uxRestricted = Signal.failed(SignalStatus.UNSUPPORTED, label, System.currentTimeMillis())) }

        subscribe(VehiclePropertyIds.GEAR_SELECTION, CarPropertyManager.SENSOR_RATE_ONCHANGE) { s, st -> s.copy(gear = Signal.failed(st, label, now())) }
        subscribe(VehiclePropertyIds.PARKING_BRAKE_ON, CarPropertyManager.SENSOR_RATE_ONCHANGE) { s, st -> if (s.parkingBrakeOn.isUsable) s /* Pleos 대체값 유지 */ else s.copy(parkingBrakeOn = Signal.failed(st, label, now())) }

        // 페달 압력: 위험 권한 + 이 SDK에 상수가 있는지도 확인해야 한다(아래 PROP_* 주석 참고)
        if (PROP_BRAKE_PEDAL == null && PROP_ACCEL_PEDAL == null) {
            Log.w(TAG, "pedal compression properties not in this SDK/platform - skipping pedal missions")
            sink.update { it.copy(
                brakePedalPct = Signal.failed(SignalStatus.UNSUPPORTED, label, now()),
                accelPedalPct = Signal.failed(SignalStatus.UNSUPPORTED, label, now()),
            ) }
        } else if (hasPedalPermission()) {
            PROP_BRAKE_PEDAL?.let { id -> subscribe(id, 10f) { s, st -> s.copy(brakePedalPct = Signal.failed(st, label, now())) } }
                ?: sink.update { it.copy(brakePedalPct = Signal.failed(SignalStatus.UNSUPPORTED, label, now())) }
            PROP_ACCEL_PEDAL?.let { id -> subscribe(id, 10f) { s, st -> s.copy(accelPedalPct = Signal.failed(st, label, now())) } }
                ?: sink.update { it.copy(accelPedalPct = Signal.failed(SignalStatus.UNSUPPORTED, label, now())) }
        } else {
            sink.update { it.copy(
                brakePedalPct = Signal.failed(SignalStatus.PERMISSION_DENIED, label, now()),
                accelPedalPct = Signal.failed(SignalStatus.PERMISSION_DENIED, label, now()),
            ) }
        }
        logSupportedProperties()
    }

    /** 지원 확인 후 구독. 미지원/실패면 onFail로 해당 필드에 상태를 기록 */
    private fun subscribe(id: Int, rateHz: Float, onFail: (com.chiilkkabwa.app.core.signal.VehicleSnapshot, SignalStatus) -> com.chiilkkabwa.app.core.signal.VehicleSnapshot) {
        val pm = props ?: return sink?.update { onFail(it, SignalStatus.DISCONNECTED) } ?: Unit
        if (pm.getCarPropertyConfig(id) == null) { sink?.update { onFail(it, SignalStatus.UNSUPPORTED) }; return }
        val ok = try { pm.registerCallback(callback, id, rateHz) } catch (e: SecurityException) {
            sink?.update { onFail(it, SignalStatus.PERMISSION_DENIED) }; return
        }
        if (!ok) sink?.update { onFail(it, SignalStatus.UNSUPPORTED) }
    }

    private fun hasPedalPermission(): Boolean =
        context.checkSelfPermission(PEDAL_PERMISSION) == PackageManager.PERMISSION_GRANTED

    private fun logSupportedProperties() {
        try {
            val ids = props?.propertyList?.map { VehiclePropertyIds.toString(it.propertyId) }
            Log.i(TAG, "Supported AAOS properties: $ids")
        } catch (e: Exception) { Log.w(TAG, "propertyList failed", e) }
    }

    private fun markAll(st: SignalStatus) {
        val at = now()
        sink?.update { it.copy(uxRestricted = Signal.failed(st, label, at), gear = Signal.failed(st, label, at)) }
    }

    override fun stop() {
        props?.unregisterCallback(callback)
        ux?.unregisterListener()
        car?.disconnect()
        car = null; props = null; ux = null; sink = null
    }

    private fun now() = System.currentTimeMillis()

    private fun mapGear(v: Int): Gear = when (v) {
        VehicleGear.GEAR_PARK -> Gear.PARK
        VehicleGear.GEAR_REVERSE -> Gear.REVERSE
        VehicleGear.GEAR_NEUTRAL -> Gear.NEUTRAL
        VehicleGear.GEAR_DRIVE -> Gear.DRIVE
        else -> if (v >= VehicleGear.GEAR_FIRST) Gear.DRIVE else Gear.UNKNOWN
    }

    companion object {
        private const val TAG = "AaosVehicleSource"
        const val PEDAL_PERMISSION = "android.car.permission.READ_CAR_PEDALS"

        /**
         * 페달 압력 속성 ID를 **실행 중에** 찾는다.
         *
         * 공식 문서(developer.android.com/reference/android/car/VehiclePropertyIds)에는
         * `BRAKE_PEDAL_COMPRESSION_PERCENTAGE` · `ACCELERATOR_PEDAL_COMPRESSION_PERCENTAGE`가
         * API 35로 적혀 있다. 그런데 **2026-11-08 기준 Android SDK 35의 공개 `android.car.jar` stub에는
         * 이 두 상수가 들어 있지 않다**(`javap`로 확인: GEAR_SELECTION·PARKING_BRAKE_ON·PERF_VEHICLE_SPEED는 있음).
         * 그래서 이름으로 참조하면 컴파일되지 않는다.
         *
         * 숫자 ID를 직접 적으면 확인하지 않은 값을 지어내는 것이 되므로(CLAUDE.md §6) 그렇게 하지 않는다.
         * 대신 리플렉션으로 찾고, 없으면 해당 미션을 `UNSUPPORTED`로 두고 건너뛴다(F5의 '미지원 → 자동 생략' 경로).
         * 차량·에뮬레이터의 실제 `android.car` 구현에 상수가 있으면 그대로 동작한다.
         *
         * TODO(day1): 에뮬레이터에서 `logSupportedProperties()` 로그로 실제 ID를 확인하고
         *   결과를 docs/03 §1 표의 '에뮬레이터 수신' 열에 적는다(B03).
         */
        private fun propIdOrNull(name: String): Int? = try {
            VehiclePropertyIds::class.java.getField(name).getInt(null)
        } catch (e: Throwable) {
            Log.i(TAG, "VehiclePropertyIds.$name not available in this platform")
            null
        }

        private val PROP_BRAKE_PEDAL: Int? = propIdOrNull("BRAKE_PEDAL_COMPRESSION_PERCENTAGE")
        private val PROP_ACCEL_PEDAL: Int? = propIdOrNull("ACCELERATOR_PEDAL_COMPRESSION_PERCENTAGE")
    }
}
