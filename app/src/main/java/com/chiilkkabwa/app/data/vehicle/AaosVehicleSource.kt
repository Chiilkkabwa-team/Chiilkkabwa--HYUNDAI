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

    /** 정상 신호만 기록하고, 비정상 상태·잘못된 타입은 항상 fail-closed. */
    private var callback: CarPropertyManager.CarPropertyEventCallback? = null
    private fun callbackFor(sink: SnapshotSink) = object : CarPropertyManager.CarPropertyEventCallback {
        override fun onChangeEvent(v: CarPropertyValue<*>) {
            val at = now()
            val available = v.status == CarPropertyValue.STATUS_AVAILABLE
            // 미사용/오류 상태의 값은 읽거나 해석하지 않는다.
            val raw = if (available) v.value else null
            sink?.update { snapshot ->
                when (v.propertyId) {
                    VehiclePropertyIds.GEAR_SELECTION ->
                        snapshot.copy(gear = aaosObserved<Gear>(raw, available, label, at) { any ->
                            (any as? Int)?.let(::mapGear)?.takeUnless { it == Gear.UNKNOWN }
                        })
                    VehiclePropertyIds.PARKING_BRAKE_ON ->
                        snapshot.copy(parkingBrakeOn = aaosObserved<Boolean>(raw, available, label, at) { any ->
                            any as? Boolean
                        })
                    PROP_BRAKE_PEDAL ->
                        snapshot.copy(brakePedalPct = aaosObserved<Float>(raw, available, label, at, ::pedalPercent))
                    PROP_ACCEL_PEDAL ->
                        snapshot.copy(accelPedalPct = aaosObserved<Float>(raw, available, label, at, ::pedalPercent))
                    else -> snapshot
                }
            }
        }

        override fun onErrorEvent(propId: Int, areaId: Int) {
            Log.w(TAG, "AAOS property error: $propId / $areaId")
            val at = now()
            sink?.update { snapshot ->
                when (propId) {
                    VehiclePropertyIds.GEAR_SELECTION ->
                        snapshot.copy(gear = Signal.failed(SignalStatus.DISCONNECTED, label, at))
                    VehiclePropertyIds.PARKING_BRAKE_ON ->
                        snapshot.copy(parkingBrakeOn = Signal.failed(SignalStatus.DISCONNECTED, label, at))
                    PROP_BRAKE_PEDAL ->
                        snapshot.copy(brakePedalPct = Signal.failed(SignalStatus.DISCONNECTED, label, at))
                    PROP_ACCEL_PEDAL ->
                        snapshot.copy(accelPedalPct = Signal.failed(SignalStatus.DISCONNECTED, label, at))
                    else -> snapshot
                }
            }
        }
    }

    override fun start(sink: SnapshotSink) {
        this.sink = sink
        // 이전 Binder callback은 이전 repository 세션 sink만 참조한다.
        callback = callbackFor(sink)
        val uxListener = CarUxRestrictionsManager.OnUxRestrictionsChangedListener { r: CarUxRestrictions ->
            sink.update { it.copy(uxRestricted = Signal.ok(r.isRequiresDistractionOptimization, label, now())) }
        }
        val c = try {
            Car.createCar(context)
        } catch (e: Exception) {
            Log.w(TAG, "AAOS Car service unavailable", e)
            markAll(SignalStatus.DISCONNECTED)
            return
        }
        if (c == null) {
            markAll(SignalStatus.DISCONNECTED)
            return
        }
        car = c
        try {
            props = c.getCarManager(Car.PROPERTY_SERVICE) as? CarPropertyManager
            ux = c.getCarManager(Car.CAR_UX_RESTRICTION_SERVICE) as? CarUxRestrictionsManager
        } catch (e: SecurityException) {
            Log.w(TAG, "AAOS car manager permission denied", e)
            markAll(SignalStatus.PERMISSION_DENIED)
            return
        } catch (e: Exception) {
            Log.w(TAG, "AAOS car manager unavailable", e)
            markAll(SignalStatus.DISCONNECTED)
            return
        }

        // CarUxRestrictions는 화면 안전 게이트의 필수 신호다.
        val manager = ux
        if (manager == null) {
            sink.update {
                it.copy(uxRestricted = Signal.failed(SignalStatus.UNSUPPORTED, label, now()))
            }
        } else {
            try {
                manager.registerListener(uxListener)
                val initial = manager.currentCarUxRestrictions
                if (initial != null) {
                    sink.update {
                        it.copy(uxRestricted = Signal.ok(initial.isRequiresDistractionOptimization, label, now()))
                    }
                } // 초기값이 없으면 UNKNOWN 유지; READY로 전환하지 않는다.
            } catch (e: SecurityException) {
                Log.w(TAG, "AAOS UX restriction permission denied", e)
                sink.update {
                    it.copy(uxRestricted = Signal.failed(SignalStatus.PERMISSION_DENIED, label, now()))
                }
            } catch (e: Exception) {
                Log.w(TAG, "AAOS UX restriction listener failed", e)
                sink.update {
                    it.copy(uxRestricted = Signal.failed(SignalStatus.DISCONNECTED, label, now()))
                }
            }
        }

        // registerCallback은 최초 등록 후 신호의 현재 상태도 콜백으로 제공한다.
        // 디바이스와 정책 검증 전에 메인 스레드에서 동기 getProperty()를 호출하지 않는다.
        subscribe(VehiclePropertyIds.GEAR_SELECTION, CarPropertyManager.SENSOR_RATE_ONCHANGE) { snapshot, status ->
            snapshot.copy(gear = Signal.failed(status, label, now()))
        }
        subscribe(VehiclePropertyIds.PARKING_BRAKE_ON, CarPropertyManager.SENSOR_RATE_ONCHANGE) { snapshot, status ->
            snapshot.copy(parkingBrakeOn = Signal.failed(status, label, now()))
        }

        // 페달 속성은 공개 android.car.jar에 상수가 없을 수 있다.
        if (PROP_BRAKE_PEDAL == null && PROP_ACCEL_PEDAL == null) {
            Log.w(TAG, "AAOS pedal property IDs unavailable: keeping conditional pedal missions disabled")
            sink.update {
                it.copy(
                    brakePedalPct = Signal.failed(SignalStatus.UNSUPPORTED, label, now()),
                    accelPedalPct = Signal.failed(SignalStatus.UNSUPPORTED, label, now()),
                )
            }
        } else if (hasPedalPermission()) {
            PROP_BRAKE_PEDAL?.let { id ->
                subscribe(id, 10f) { snapshot, status ->
                    snapshot.copy(brakePedalPct = Signal.failed(status, label, now()))
                }
            } ?: sink.update {
                it.copy(brakePedalPct = Signal.failed(SignalStatus.UNSUPPORTED, label, now()))
            }
            PROP_ACCEL_PEDAL?.let { id ->
                subscribe(id, 10f) { snapshot, status ->
                    snapshot.copy(accelPedalPct = Signal.failed(status, label, now()))
                }
            } ?: sink.update {
                it.copy(accelPedalPct = Signal.failed(SignalStatus.UNSUPPORTED, label, now()))
            }
        } else {
            sink.update {
                it.copy(
                    brakePedalPct = Signal.failed(SignalStatus.PERMISSION_DENIED, label, now()),
                    accelPedalPct = Signal.failed(SignalStatus.PERMISSION_DENIED, label, now()),
                )
            }
        }
        logSupportedProperties()
    }

    /** 지원 확인 후 구독. 미지원/실패면 onFail로 해당 필드에 상태를 기록 */
    private fun subscribe(
        id: Int,
        rateHz: Float,
        onFail: (com.chiilkkabwa.app.core.signal.VehicleSnapshot, SignalStatus) -> com.chiilkkabwa.app.core.signal.VehicleSnapshot,
    ) {
        val manager = props ?: run {
            sink?.update { onFail(it, SignalStatus.DISCONNECTED) }
            return
        }
        val config = try {
            manager.getCarPropertyConfig(id)
        } catch (e: SecurityException) {
            Log.w(TAG, "Permission denied when reading property config: $id", e)
            sink?.update { onFail(it, SignalStatus.PERMISSION_DENIED) }
            return
        } catch (e: Exception) {
            Log.w(TAG, "Cannot read property config: $id", e)
            sink?.update { onFail(it, SignalStatus.UNKNOWN) }
            return
        }
        if (config == null) {
            sink?.update { onFail(it, SignalStatus.UNSUPPORTED) }
            return
        }
        val registered = try {
            manager.registerCallback(callback, id, rateHz)
        } catch (e: SecurityException) {
            Log.w(TAG, "Permission denied subscribing property: $id", e)
            sink?.update { onFail(it, SignalStatus.PERMISSION_DENIED) }
            return
        } catch (e: Exception) {
            Log.w(TAG, "Cannot subscribe property: $id", e)
            sink?.update { onFail(it, SignalStatus.DISCONNECTED) }
            return
        }
        if (!registered) {
            // registerCallback(false)는 미지원뿐 아니라 권한 문제도 가능하다.
            // 정확한 원인을 모르면 UNKNOWN으로 남겨 안전 게이트를 연다거나 오진하지 않는다.
            Log.w(TAG, "Property subscription declined (status unverified): $id")
            sink?.update { onFail(it, SignalStatus.UNKNOWN) }
        }
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
        // stop() 후 도착한 이벤트는 현재 sink에 반영되지 않도록 먼저 차단한다.
        sink = null
        try { callback?.let { props?.unregisterCallback(it) } } catch (e: Exception) { Log.w(TAG, "AAOS unregister property failed", e) }
        try { ux?.unregisterListener() } catch (e: Exception) { Log.w(TAG, "AAOS unregister UX failed", e) }
        try { car?.disconnect() } catch (e: Exception) { Log.w(TAG, "AAOS disconnect failed", e) }
        car = null; props = null; ux = null; callback = null
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
