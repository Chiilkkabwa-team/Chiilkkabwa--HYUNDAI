# 구현 상태 대장

점검일: 2026-11-11 KST. 기준 main: de612636cc2c13f6d4397626fdf24cf8a70cb2e6.
요구사항 원본: 사용자가 제공한 **공식접수기획서 최종 DOCX**, 02-3(주요 기능) 및 02-5(개발 계획).

**이 표는 정적 코드 점검 결과이다.** 이 작성 시점에는 Android Studio, CI, Pleos Emulator, 실차를 새로 실행해 검증한 것이 아니다.

| 기능 | Android main 코드 | 웹 모의 시연 | 실제 에뮬레이터/차량 검증 |
|---|---|---|---|
| 주차 안전 게이트 | SafetyGate 및 AAOS 소스 코드 존재. 신호 최신성/복기 입력 정책 보완 필요 | 모의 구현 | 미검증 |
| 목표·혼자/멘토·약속 | ViewModel/Compose 구현 | 모의 구현 | 미검증 |
| 벨트/브레이크/방향지시등 워밍업 | 미션 판정 코드 존재, Pleos 실제 어댑터 미연동 | 모의 구현 | 미검증 |
| 가상 주차/조건부 페달 | Kotlin 로직·화면 존재, 실신호는 조건부 | 모의 구현 | 미검증 |
| AAOS PERF_VEHICLE_SPEED | VehicleSnapshot/구독/권한 선언 없음 | 가상 속도 | 미검증 |
| 속도 기반 주행 이벤트 | Android 미구현 | 가상 이벤트 | 미검증 |
| 주행 중 음성 및 AudioFocus | Android 미구현 | Web Speech 구현 | 플랫폼 허용·실환경 미검증 |
| 주차 후 시계열 이벤트 리플레이 | Android 미구현 | 웹 모의 타임라인 | 미검증 |
| 복기 요약·배지·다음 목표 | Kotlin 코드 일부 구현 | 모의 구현 | 반복 세션 미검증 |
| NaviHelper 경로 전달 | Android 미구현 | 제품 계획 | 미검증 |
| 프로필 선택/삭제 UX | Store API 존재, 화면 구현 불완전 | 별도 | 미검증 |
| 암호화 Room 저장 | 미구현. 현재 SharedPreferences JSON | 브라우저 localStorage | 미검증 |
| Pleos Vehicle 신호 구독 | PleosVehicleSource의 TODO/UNSUPPORTED 상태 | 해당 없음 | 미검증 |

주요 코드 근거:
- app/src/main/java/com/chiilkkabwa/app/core/{signal,safety,mission,session}/
- app/src/main/java/com/chiilkkabwa/app/data/vehicle/
- app/src/main/java/com/chiilkkabwa/app/data/store/PracticeStore.kt
- app/src/main/java/com/chiilkkabwa/app/ui/
- web/{app,voice,sim}.js

**과거 기록(재검증 아님)**: 기존 CLAUDE.md와 docs/14에는 2026-11-08 기준 Android mock/vehicle 빌드 및 JVM 테스트 17개 성공 기록이 있다. 이번 작성 시점에 재실행하지 않았다.

작성 원칙: '코드 존재' / '빌드 성공' / '단위 테스트 통과' / '모의 시연' / '에뮬레이터 수신' / '실차 검증'을 각각 별개로 표시한다. 해당 기능의 검증 로그가 없으면 완료로 승격하지 않는다.
