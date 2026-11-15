package com.chiilkkabwa.app.data.vehicle

import ai.pleos.playground.vehicle.api.model.area.SeatArea
import ai.pleos.playground.vehicle.api.model.area.WindowArea
import ai.pleos.playground.vehicle.api.model.capability.CapabilityConfig
import ai.pleos.playground.vehicle.constant.area.SeatValue
import ai.pleos.playground.vehicle.constant.area.WindowValue
import ai.pleos.playground.vehicle.constant.control.LightState
import ai.pleos.playground.vehicle.constant.control.WiperState

/** SDK2.3.2 public capability and enum contracts, not console switch modes. */
internal fun supportsFrontWiper(config: CapabilityConfig, areas: Map<WindowArea, List<WiperState>>): Boolean =
    readable(config) && areas[WindowArea(WindowValue.FRONT_WINDSHIELD)]?.let {
        WiperState.ON in it && WiperState.OFF in it
    } == true

internal fun supportsLowBeam(config: CapabilityConfig, states: List<LightState>): Boolean =
    readable(config) && LightState.ON in states && LightState.OFF in states

/** Korean LHD front passenger only; combined areas are not an individual seat observation. */
internal fun supportsFrontPassenger(config: CapabilityConfig, areas: List<SeatArea>): Boolean =
    readable(config) && SeatArea(SeatValue.ROW_1_RIGHT) in areas

internal fun pleosWiperOn(raw: Any?): Boolean? = when (raw as? WiperState) {
    WiperState.ON -> true
    WiperState.OFF -> false
    else -> null // OTHER/SERVICE do not prove either normal ON or OFF.
}

internal fun pleosLowBeamOn(raw: Any?): Boolean? = when (raw as? LightState) {
    LightState.ON -> true
    LightState.OFF -> false
    else -> null // DAYTIME_RUNNING is not a low-beam observation for this mission.
}

/** get/registerTripCurrentDrivingDistance returns Float meters, unlike total odometer km. */
internal fun pleosTripKm(raw: Any?): Float? =
    (raw as? Float)?.takeIf { it.isFinite() && it >= 0f }?.div(1000f)

/** get/registerTripCurrentDrivingTime returns Int minutes; multiply after widening. */
internal fun pleosTripSeconds(raw: Any?): Long? =
    (raw as? Int)?.takeIf { it >= 0 }?.toLong()?.times(60L)
