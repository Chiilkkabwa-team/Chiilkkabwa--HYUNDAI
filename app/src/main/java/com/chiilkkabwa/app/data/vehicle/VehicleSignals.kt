package com.chiilkkabwa.app.data.vehicle

import android.os.Build
import com.chiilkkabwa.app.core.signal.SignalSource
import com.chiilkkabwa.app.core.signal.Signal
import com.chiilkkabwa.app.core.signal.SignalStatus
import com.chiilkkabwa.app.core.signal.VehicleSnapshot
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

/**
 * 차량 신호 공급자 하나. 각 공급자는 자기가 맡은 필드만 갱신한다(읽기 전용, 제어 명령 없음).
 * 구현: [AaosVehicleSource](표준 속성·주행 제한), [PleosVehicleSource](Pleos Vehicle SDK), [MockVehicleSource](모의).
 */
interface VehicleSource {
    fun start(sink: SnapshotSink)
    /** 구독 해제. 백그라운드 진입·주행 잠금 시 호출 가능(T12) */
    fun stop()
}

fun interface SnapshotSink {
    fun update(transform: (VehicleSnapshot) -> VehicleSnapshot)
}

/** 여러 공급자를 하나의 스냅샷으로 합친다. */
class VehicleRepository(private val sources: List<VehicleSource>) {
    private val _snapshot = MutableStateFlow(VehicleSnapshot())
    val snapshot: StateFlow<VehicleSnapshot> = _snapshot.asStateFlow()
    @Volatile private var running = false
    @Volatile private var generation = 0L

    fun start() {
        if (running) return
        running = true
        val token = ++generation
        sources.forEach { it.start { transform ->
            _snapshot.update { snapshot ->
                if (running && generation == token) transform(snapshot) else snapshot
            }
        } }
    }

    fun stop() {
        if (!running) return
        running = false
        generation++
        sources.forEach { it.stop() }
        _snapshot.update { snapshot ->
            val at = System.currentTimeMillis()
            fun <T> Signal<T>.disconnected(): Signal<T> = Signal.failed(SignalStatus.DISCONNECTED, source, at)
            snapshot.copy(
                gear = snapshot.gear.disconnected(),
                parkingBrakeOn = snapshot.parkingBrakeOn.disconnected(),
                uxRestricted = snapshot.uxRestricted.disconnected(),
                driverBeltBuckled = snapshot.driverBeltBuckled.disconnected(),
                brakePressed = snapshot.brakePressed.disconnected(),
                brakePedalPct = snapshot.brakePedalPct.disconnected(),
                accelPedalPct = snapshot.accelPedalPct.disconnected(),
                turnSignal = snapshot.turnSignal.disconnected(),
                wiperOn = snapshot.wiperOn.disconnected(),
                lowBeamOn = snapshot.lowBeamOn.disconnected(),
                steeringDeg = snapshot.steeringDeg.disconnected(),
                passengerSeated = snapshot.passengerSeated.disconnected(),
                tripDistanceKm = snapshot.tripDistanceKm.disconnected(),
                tripTimeSec = snapshot.tripTimeSec.disconnected(),
                carDimensions = snapshot.carDimensions.disconnected(),
                speed = snapshot.speed.disconnected(),
            )
        }
    }

    companion object {
        /**
         * 실차 여부. Pleos FAQ: 실차의 Build.DEVICE는 CONNECT_S / CONNECT_L / CONNECT_W.
         * 그 외(에뮬레이터)는 EMULATOR로 표시한다. → 화면 라벨 '실차 신호/에뮬레이터 신호'
         */
        fun realSourceLabel(): SignalSource =
            if (Build.DEVICE.startsWith("CONNECT_")) SignalSource.VEHICLE else SignalSource.EMULATOR
    }
}
