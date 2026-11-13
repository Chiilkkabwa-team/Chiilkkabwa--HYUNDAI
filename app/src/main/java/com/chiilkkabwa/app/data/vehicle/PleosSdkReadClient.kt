package com.chiilkkabwa.app.data.vehicle

import ai.pleos.playground.vehicle.Vehicle
import ai.pleos.playground.vehicle.api.model.area.SeatArea
import ai.pleos.playground.vehicle.api.model.capability.AccessType
import ai.pleos.playground.vehicle.api.model.capability.CapabilityConfig
import ai.pleos.playground.vehicle.constant.PleosPermission
import ai.pleos.playground.vehicle.constant.area.SeatValue
import ai.pleos.playground.vehicle.constant.area.TurnSignalValue
import ai.pleos.playground.vehicle.listener.BrakePedalStatusListener
import ai.pleos.playground.vehicle.listener.SeatBeltBuckledStateListener
import ai.pleos.playground.vehicle.listener.TurnSignalStateListener
import android.content.Context
import android.content.pm.PackageManager
import android.util.Log

internal fun readable(config: CapabilityConfig): Boolean =
    config.accessType == AccessType.READ || config.accessType == AccessType.READ_WRITE

/** 한국 LHD 시험 한정. 여러 좌석을 합친 area를 운전석 관측으로 간주하지 않는다. */
internal fun supportsLeftDriver(config: CapabilityConfig, areas: List<SeatArea>): Boolean =
    readable(config) && areas.any { it == SeatArea(SeatValue.ROW_1_LEFT) }

/** 캐시 Vehicle 2.3.2 classes.jar + sources + 공개 Reference와 대조한 읽기 호출만 사용. */
internal class PleosSdkReadClient(private val context: Context) : PleosReadClient {
    private val vehicle = Vehicle(context.applicationContext)
    private val driver = SeatArea(SeatValue.ROW_1_LEFT)
    private val seat = vehicle.getSeat()
    private val brake = vehicle.getBrake()
    private val turn = vehicle.getTurnSignal()

    override fun initialize() = vehicle.initialize()

    private fun requireReadPermission(sensor: PleosSensor) {
        val permission = when (sensor) {
            PleosSensor.BELT -> PleosPermission.CAR_SEATS
            PleosSensor.BRAKE -> PleosPermission.CAR_DRIVE
            PleosSensor.TURN -> PleosPermission.CAR_LIGHTS
        }
        if (context.checkSelfPermission(permission) != PackageManager.PERMISSION_GRANTED) throw SecurityException()
    }

    override fun check(sensor: PleosSensor, complete: (Boolean) -> Unit, failed: (Exception) -> Unit) {
        requireReadPermission(sensor)
        when (sensor) {
            PleosSensor.BELT -> seat.checkSeatBeltBuckledCapabilities(
                onComplete = { config, areas -> complete(supportsLeftDriver(config, areas)) }, onFailed = failed,
            )
            PleosSensor.BRAKE -> brake.checkBrakePedalStatusCapabilities({ complete(readable(it)) }, failed)
            PleosSensor.TURN -> turn.checkTurnSignalStateCapabilities({ complete(readable(it)) }, failed)
        }
    }

    override fun read(sensor: PleosSensor, complete: (Any?) -> Unit, failed: (Exception) -> Unit) {
        requireReadPermission(sensor)
        when (sensor) {
            PleosSensor.BELT -> seat.getCurrentSeatBeltBuckledState(driver, complete, failed)
            PleosSensor.BRAKE -> brake.getCurrentBrakePedalStatus(complete, failed)
            PleosSensor.TURN -> turn.getCurrentTurnSignalState(complete, failed)
        }
    }

    override fun subscribe(sensor: PleosSensor, updated: (Any?) -> Unit, failed: (Exception) -> Unit): () -> Unit {
        requireReadPermission(sensor)
        fun event(value: Any?) {
            // get 응답과 구분한 callback 로그. VIN/위치/프로필 등 민감정보는 포함하지 않는다.
            Log.d("PleosVehicleSource", "SDK callback sensor=$sensor value=$value")
            updated(value)
        }
        return when (sensor) {
            PleosSensor.BELT -> {
                val listener = object : SeatBeltBuckledStateListener {
                    override fun onSeatBeltBuckledStateUpdated(state: Boolean?) = event(state)
                    override fun onFailed(e: Exception) = failed(e)
                }
                seat.registerSeatBeltBuckledState(driver, listener)
                val remove: () -> Unit = { seat.unregisterSeatBeltBuckledState(driver, listener) }
                remove
            }
            PleosSensor.BRAKE -> {
                val listener = object : BrakePedalStatusListener {
                    override fun onBrakePedalStatusUpdated(status: Boolean?) = event(status)
                    override fun onFailed(e: Exception) = failed(e)
                }
                brake.registerBrakePedalStatus(listener)
                val remove: () -> Unit = { brake.unregisterBrakePedalStatus(listener) }
                remove
            }
            PleosSensor.TURN -> {
                val listener = object : TurnSignalStateListener {
                    override fun onTurnSignalStateUpdated(state: TurnSignalValue?) = event(state)
                    override fun onFailed(e: Exception) = failed(e)
                }
                turn.registerTurnSignalState(listener)
                val remove: () -> Unit = { turn.unregisterTurnSignalState(listener) }
                remove
            }
        }
    }

    override fun release() = vehicle.release()
}
