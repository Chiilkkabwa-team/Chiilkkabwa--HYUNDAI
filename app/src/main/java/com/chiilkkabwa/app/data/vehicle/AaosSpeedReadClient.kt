package com.chiilkkabwa.app.data.vehicle

import android.car.Car
import android.car.VehicleAreaType
import android.car.VehiclePropertyIds
import android.car.hardware.CarPropertyConfig
import android.car.hardware.CarPropertyValue
import android.car.hardware.property.CarPropertyManager
import android.content.Context
import android.content.pm.PackageManager
import android.os.Handler
import android.os.Looper
import com.chiilkkabwa.app.core.signal.SignalStatus

/** Public AAOS reads only. No get/set numeric property IDs, VHAL or synchronous service wait. */
internal class AaosSpeedReadClient(private val context: Context) : SpeedReadClient {
    private val handler = Handler(Looper.getMainLooper())
    private var car: Car? = null
    private var manager: CarPropertyManager? = null
    private var callback: CarPropertyManager.CarPropertyEventCallback? = null
    private var closed = false

    override fun access(): SpeedReadAccess {
        try { context.packageManager.getPermissionInfo(Car.PERMISSION_SPEED, 0) }
        catch (_: PackageManager.NameNotFoundException) { return SpeedReadAccess.MISSING_PERMISSION }
        return if (context.checkSelfPermission(Car.PERMISSION_SPEED) == PackageManager.PERMISSION_GRANTED)
            SpeedReadAccess.GRANTED else SpeedReadAccess.DENIED
    }

    override fun connect(ready: () -> Unit, disconnected: (SignalStatus) -> Unit) {
        val created = Car.createCar(context, handler, Car.CAR_WAIT_TIMEOUT_DO_NOT_WAIT) { connectedCar, available ->
            if (closed) return@createCar
            car = connectedCar
            if (!available) disconnected(SignalStatus.DISCONNECTED)
            else {
                try {
                    manager = connectedCar.getCarManager(Car.PROPERTY_SERVICE) as? CarPropertyManager
                    if (manager == null) disconnected(SignalStatus.UNSUPPORTED) else ready()
                } catch (_: SecurityException) { disconnected(SignalStatus.PERMISSION_DENIED) }
                catch (_: Exception) { disconnected(SignalStatus.DISCONNECTED) }
            }
        } ?: throw IllegalStateException("AAOS service unavailable")
        if (closed) created.disconnect() else car = created
    }

    override fun isSupported(): Boolean {
        val config = manager?.getCarPropertyConfig(VehiclePropertyIds.PERF_VEHICLE_SPEED) ?: return false
        return config.propertyType == Float::class.javaObjectType &&
            config.areaType == VehicleAreaType.VEHICLE_AREA_TYPE_GLOBAL &&
            config.changeMode == CarPropertyConfig.VEHICLE_PROPERTY_CHANGE_MODE_CONTINUOUS &&
            (config.access == CarPropertyConfig.VEHICLE_PROPERTY_ACCESS_READ ||
                config.access == CarPropertyConfig.VEHICLE_PROPERTY_ACCESS_READ_WRITE)
    }

    override fun subscribe(value: (Any?, SignalStatus, Long) -> Unit, error: (SignalStatus) -> Unit): Boolean {
        val cb = object : CarPropertyManager.CarPropertyEventCallback {
            override fun onChangeEvent(v: CarPropertyValue<*>) {
                if (closed || v.propertyId != VehiclePropertyIds.PERF_VEHICLE_SPEED || v.areaId != 0) return
                val status = when (v.status) {
                    CarPropertyValue.STATUS_AVAILABLE -> SignalStatus.OK
                    CarPropertyValue.STATUS_UNAVAILABLE -> SignalStatus.STALE
                    else -> SignalStatus.UNKNOWN
                }
                value(if (status == SignalStatus.OK) v.value else null, status, v.timestamp)
            }
            override fun onErrorEvent(propertyId: Int, areaId: Int) {
                if (!closed && propertyId == VehiclePropertyIds.PERF_VEHICLE_SPEED && areaId == 0)
                    error(SignalStatus.UNKNOWN) // this callback alone does not prove a disconnect
            }
        }
        callback = cb
        return manager?.registerCallback(cb, VehiclePropertyIds.PERF_VEHICLE_SPEED, 5f) == true
    }

    override fun close() {
        closed = true
        try { callback?.let { manager?.unregisterCallback(it, VehiclePropertyIds.PERF_VEHICLE_SPEED) } }
        finally { callback = null; manager = null; val old = car; car = null; old?.disconnect() }
    }
}
