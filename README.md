# 치일까봐 (Chiilkkabwa)

주차된 내 차에서 시작하는 운전 강습모드 — Pleos Connect(Android Automotive) 앱.

- 연습 전 5분: 안전벨트·브레이크·방향지시등 같은 조작을 하면 **차량 신호가 확인**해 줍니다.
- 주행 중 0터치: 차가 움직이면 화면이 스스로 잠깁니다.
- 연습 후 3분: 다시 주차하면 오늘 연습(거리·시간)을 돌아보고 다음 목표 하나를 정합니다.

원칙: 확인은 차가, 판단은 사람이. 앱은 차량을 제어하지 않습니다(읽기 전용).

## 실행
| 플레이버 | 내용 |
|---|---|
| `mock` | 모의 차량 + 디버그 패널. SDK·에뮬레이터 신호 없이 전체 흐름 |
| `vehicle` | AAOS Car API + Pleos Vehicle SDK. Pleos Connect Emulator v2.1.0(API 34) |

```bash
./gradlew :app:testMockDebugUnitTest
./gradlew :app:assembleMockDebug
./gradlew :app:assembleVehicleDebug
```

## 사용 SDK/API
- AAOS: CarUxRestrictionsManager, CarPropertyManager(GEAR_SELECTION, PARKING_BRAKE_ON, *_PEDAL_COMPRESSION_PERCENTAGE)
- Pleos Vehicle SDK(조회): Seat, Brake, TurnSignal, Wiper, Light, SteeringWheel, Odometer, CarInfo

## 데모 영상
[준비 중]

> 개발 문서: `CLAUDE.md`, `docs/`
