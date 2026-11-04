package com.chiilkkabwa.app.core.signal

/**
 * 차량 신호 한 개의 관측값.
 * - value: 값(없으면 null)
 * - status: 값을 믿어도 되는지
 * - source: 어디서 왔는지(실차/에뮬레이터/모의) → 화면 라벨과 기록에 그대로 표시
 * - receivedAtMs: 앱이 받은 시각(System.currentTimeMillis 또는 테스트 시계)
 *
 * 원칙: '모르면 잠근다'. status != OK 이면 그 값으로 성공·잠금 해제를 판단하지 않는다.
 */
data class Signal<out T>(
    val value: T?,
    val status: SignalStatus,
    val source: SignalSource,
    val receivedAtMs: Long,
) {
    val isUsable: Boolean get() = status == SignalStatus.OK && value != null

    companion object {
        fun <T> unknown(source: SignalSource = SignalSource.MOCK): Signal<T> =
            Signal(null, SignalStatus.UNKNOWN, source, 0L)

        fun <T> ok(value: T, source: SignalSource, atMs: Long): Signal<T> =
            Signal(value, SignalStatus.OK, source, atMs)

        fun <T> failed(status: SignalStatus, source: SignalSource, atMs: Long): Signal<T> =
            Signal(null, status, source, atMs)
    }
}

/** 값의 신뢰 상태. UI 안내 문구가 원인별로 달라진다(5-3, T03·T08). */
enum class SignalStatus {
    OK,
    /** 이 차량/환경이 지원하지 않음(capability 없음, getPropertyList에 없음) */
    UNSUPPORTED,
    /** 권한 거부(런타임 권한 미허용 또는 매니페스트 미선언 → ERROR_SECURITY_PERMISSION) */
    PERMISSION_DENIED,
    /** 서비스 연결 끊김 */
    DISCONNECTED,
    /** 일정 시간 갱신 없음(연속값 신호에만 적용) */
    STALE,
    /** 아직 한 번도 받지 못함 */
    UNKNOWN,
}

/** 데이터 출처. 시연·기록에서 '실차 SDK / 에뮬레이터 SDK / 모의'를 반드시 구분한다. */
enum class SignalSource(val label: String) {
    VEHICLE("실차 신호"),
    EMULATOR("에뮬레이터 신호"),
    MOCK("모의"),
}

enum class Gear { PARK, REVERSE, NEUTRAL, DRIVE, UNKNOWN }

enum class TurnSignalState { NONE, LEFT, RIGHT, HAZARD }

/** 차체 치수(mm). CarInfo에서 받거나, 없으면 차급 기본값을 사용하고 '기본값' 라벨을 붙인다. */
data class CarDimensions(val lengthMm: Int, val widthMm: Int, val wheelbaseMm: Int? = null, val isDefault: Boolean = false) {
    companion object {
        /** 준중형 세단 대략값(가상 주차 기본값). 실제 차량값을 받으면 교체. */
        val COMPACT_SEDAN_DEFAULT = CarDimensions(4700, 1830, 2720, isDefault = true)
    }
}

/**
 * 앱이 보는 차량 상태의 한 순간. 각 필드는 독립적으로 상태를 가진다.
 * 새 신호를 추가할 때는 여기 + VehicleSignals 구현체 + docs/03 표를 함께 고친다.
 */
data class VehicleSnapshot(
    val gear: Signal<Gear> = Signal.unknown(),
    val parkingBrakeOn: Signal<Boolean> = Signal.unknown(),
    /** CarUxRestrictions.isRequiresDistractionOptimization */
    val uxRestricted: Signal<Boolean> = Signal.unknown(),
    val driverBeltBuckled: Signal<Boolean> = Signal.unknown(),
    /** Pleos Brake 페달 상태(밟음/뗌). 연속값이면 brakePedalPct도 함께 채운다 */
    val brakePressed: Signal<Boolean> = Signal.unknown(),
    /** AAOS BRAKE_PEDAL_COMPRESSION_PERCENTAGE 0~100 (지원 시) */
    val brakePedalPct: Signal<Float> = Signal.unknown(),
    /** AAOS ACCELERATOR_PEDAL_COMPRESSION_PERCENTAGE 0~100 (지원 시) */
    val accelPedalPct: Signal<Float> = Signal.unknown(),
    val turnSignal: Signal<TurnSignalState> = Signal.unknown(),
    val wiperOn: Signal<Boolean> = Signal.unknown(),
    val lowBeamOn: Signal<Boolean> = Signal.unknown(),
    /** 핸들 각도(도). 왼쪽 음수/오른쪽 양수로 정규화해 저장 — 실제 부호는 Day1에 확인 */
    val steeringDeg: Signal<Float> = Signal.unknown(),
    val passengerSeated: Signal<Boolean> = Signal.unknown(),
    /** Odometer 트립 누적 거리(km)·시간(초) — 주행 전/후 차이로 '오늘 연습' 계산 */
    val tripDistanceKm: Signal<Float> = Signal.unknown(),
    val tripTimeSec: Signal<Long> = Signal.unknown(),
    val carDimensions: Signal<CarDimensions> = Signal.unknown(),
)
