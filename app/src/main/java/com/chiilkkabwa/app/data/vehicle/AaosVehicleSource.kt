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
                    VehiclePropertyIds.BRAKE_PEDAL_COMPRESSION_PERCENTAGE ->
                        s.copy(brakePedalPct = if (okStatus) Signal.ok((v.value as Float), label, at) else Signal.failed(SignalStatus.STALE, label, at))
                    VehiclePropertyIds.ACCELERATOR_PEDAL_COMPRESSION_PERCENTAGE ->
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

        // 페달 압력: 위험 권한. 권한이 없으면 PERMISSION_DENIED로 표시(UI가 '모의 연습' 제안)
        if (hasPedalPermission()) {
            subscribe(VehiclePropertyIds.BRAKE_PEDAL_COMPRESSION_PERCENTAGE, 10f) { s, st -> s.copy(brakePedalPct = Signal.failed(st, label, now())) }
            subscribe(VehiclePropertyIds.ACCELERATOR_PEDAL_COMPRESSION_PERCENTAGE, 10f) { s, st -> s.copy(accelPedalPct = Signal.failed(st, label, now())) }
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
    }
}
