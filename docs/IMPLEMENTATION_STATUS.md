# 구현 상태 대장

Android 구현 정적 점검일: 2026-11-11 KST (당시 기준 main: de612636cc2c13f6d4397626fdf24cf8a70cb2e6). **웹 확장과 브랜치 병합 상태 확인: 2026-11-13, dev 9ca4316507481e3e56e50815e90d237da2fe1c0f / main c4c15686a30e788143ccc88d8ebaa52500d3187b.** 새 분업/결함 정리: [3인 기능 분업 v3](THREE_PERSON_FEATURE_PLAN_V3.md).
요구사항 원본: 사용자가 제공한 **공식접수기획서 최종 DOCX**, 02-3(주요 기능) 및 02-5(개발 계획).

**이 표는 정적 코드 점검 결과이다.** 이 작성 시점에는 Android Studio, CI, Pleos Emulator, 실차를 새로 실행해 검증한 것이 아니다.

| 기능 | Android main 코드 | 웹 모의 시연 | 실제 에뮬레이터/차량 검증 |
|---|---|---|---|
| 주차 안전 게이트 | SafetyGate 및 AAOS 소스 코드 존재. 신호 최신성/복기 입력 정책 보완 필요 | 모의 구현 | 미검증 |
| 목표·혼자/멘토·약속 | ViewModel/Compose 구현 | 모의 구현 | 미검증 |
| 벨트/브레이크/방향지시등 워밍업 | Pleos 읽기 어댑터 연결·기본 off | 모의 구현 | 초기 벨트/브레이크/방향지시등 수신 확인, 전체 미션 미검증 |
| 가상 주차/조건부 페달 | Kotlin 로직·화면 존재, 실신호는 조건부 | 모의 구현 | 미검증 |
| AAOS PERF_VEHICLE_SPEED | VehicleSnapshot/구독/권한 선언 없음 | 가상 속도 | 미검증 |
| 속도 기반 주행 이벤트 | Android 미구현 | 가상 이벤트 | 미검증 |
| 주행 중 음성 및 AudioFocus | Android 미구현 | Web Speech 구현 | 플랫폼 허용·실환경 미검증 |
| 주차 후 시계열 이벤트 리플레이 | Android 미구현 | 웹 모의 타임라인 | 미검증 |
| 복기 요약·배지·다음 목표 | Kotlin 코드 일부 구현 | 모의 구현 | 반복 세션 미검증 |
| NaviHelper 경로 전달 | Android 미구현 | 제품 계획 | 미검증 |
| 프로필 선택/삭제 UX | Store API 존재, 화면 구현 불완전 | 별도 | 미검증 |
| 암호화 Room 저장 | 미구현. 현재 SharedPreferences JSON | 브라우저 localStorage | 미검증 |
| Pleos Vehicle 신호 구독 | Seat 벨트·Brake·TurnSignal 구현, 나머지 미연동 | 해당 없음 | API34 Pleos2.1.0에서 일부 실수신 확인, 실차 미검증 |

### 2026-11-13 확인: 웹 모의 추가 (Claude 브랜치 → PR #7 dev, PR #8 main 병합 완료)
11/12 회의 + 3인 기능 배정안 v2의 웹 부분. **전부 웹 모의**이고 Android·에뮬레이터·실차는 손대지 않았다. **브랜치 병합은 기능 완료를 뜻하지 않는다.** 웹 64/64 단위 테스트 PASS 보고와 별개로 브레이크 슬라이더, 벨트 미션, 복기 게이트, 음성 우선순위/이벤트 연결 오류가 확인되었으므로 [분업안 v3](THREE_PERSON_FEATURE_PLAN_V3.md)에 수정 과제로 기록했다.

| 배정안 ID | 기능 | 웹 모의 | Android | 에뮬레이터/실차 |
|---|---|---|---|---|
| B01 | 겹친 상황 우선순위·칭찬·회전 주의 대사 | 구현 + 단위 테스트 | 미구현 | 미검증(S05) |
| A04 | 가상 교차로 좌·우회전(판정: 깜빡이·차로·속도) | 구현 + 단위 테스트 | 해당 없음 | 해당 없음 |
| C01 | 주차 후 속도 시간표(사건 점·브레이크 띠·말한 표시). **E3 수정(11/13): 복기 중 주차 확인이 깨지면 복기·장면 재현 즉시 잠금, 재주차 시 이어서** | 구현 + 단위 테스트 | 게이트 잠금만 수정+단위 테스트, 시계열 미구현 | 미검증 |
| C03 | 장면 재현 작은 창(시뮬레이션 재현 표기) | 구현 + 단위 테스트 | 미구현 | 미검증 |
| A05 | 콘솔 핸들 그림·누른 만큼 깊어지는 페달·WASD·벨트 표시 | 구현(브라우저 조작 확인) | 해당 없음(웹 콘솔 전용) | 해당 없음 |
| B04 | 미션 도움말(O/X)·운전 노트 | 구현 + 단위 테스트 | 미구현 | 해당 없음 |
| C04 | 알림 남은 시간 테두리·온보딩 버전 | 구현 | 미구현 | 해당 없음 |
| C05 | 서비스 소개(About Us)·구현 상태 표 | 구현 + 단위 테스트 | 해당 없음 | 해당 없음 |
| B03 | Typecast·ElevenLabs 음성 | **안 함** — API 키·사용/배포 권리 확인 필요(S11) | — | — |
| A06 | 3D 차량 | **안 함** — 자산 라이선스·성능 측정 먼저(S11) | — | — |
| A01·A02·A03·B02 | Pleos 신호·속도 이벤트·NaviHelper·Android TTS | **안 함** — Android/에뮬레이터 작업 | — | — |

주요 코드 근거:
- app/src/main/java/com/chiilkkabwa/app/core/{signal,safety,mission,session}/
- app/src/main/java/com/chiilkkabwa/app/data/vehicle/
- app/src/main/java/com/chiilkkabwa/app/data/store/PracticeStore.kt
- app/src/main/java/com/chiilkkabwa/app/ui/
- web/{app,voice,sim}.js

**과거 기록(재검증 아님)**: 기존 CLAUDE.md와 docs/14에는 2026-11-08 기준 Android mock/vehicle 빌드 및 JVM 테스트 17개 성공 기록이 있다. 이번 작성 시점에 재실행하지 않았다.

작성 원칙: '코드 존재' / '빌드 성공' / '단위 테스트 통과' / '모의 시연' / '에뮬레이터 수신' / '실차 검증'을 각각 별개로 표시한다. 해당 기능의 검증 로그가 없으면 완료로 승격하지 않는다.

### A01 Issue #16 후속 (2026-11-13 / 코드 dd0a5a1)

- `PleosSdkReadClient`에 실제 Vehicle 2.3.2의 Seat 벨트·Brake·TurnSignal 공개 읽기 capability/get/register/unregister를 연결했다. `PleosSignalSession`은 오류/타입/null/타임아웃, 초기 읽기와 이벤트 경쟁, 종료·재시작 후 콜백, 해제 배출을 관리한다. `VehicleSnapshot` 필드·단위·출처 계약과 매니페스트 권한은 변경하지 않았다.
- Activity pause/resume에 구독을 연결하고 repository 중지 시 값을 폐기한다. AAOS callback은 시작 당시 sink를 캡처하여 이전 연결 이벤트를 격리한다. 동기 중지 스냅샷 반영으로 warmup 재개를 보완했다.
- JVM 테스트 mock **44/44**, vehicle **44/44**, 양 debug APK 빌드 및 웹 **79/79** PASS. SDK 클래스/시그니처 확인과 컴파일을 통과한 상태이며 실제 callback 수신 완료를 뜻하지 않는다.
- 기본 비활성화. 명시적 debug opt-in + EMULATOR에서만 시험 가능하고 실차/release는 차단한다. SDK 미응답 상태에는 추가 client 생성을 차단한다. 리소스 회수·실제 생명주기·권한·Pleos Emulator/실차는 **NOT_VERIFIED**. 자세한 근거/재현 명령은 SDK 노트 A01 절과 검증 로그 참고.

### A01 Pleos Emulator 실측 갱신 (2026-11-13 / 코드258e4da)

공식 Pleos2.1.0 이미지 설치·해시 검증과 API34 AVD 부팅/앱 설치를 완료했다. Vehicle SDK2.3.2, 실제 서비스2.3.22 환경에서 **초기 벨트false, 콘솔 브레이크false/true/false, 방향지시등LEFT/RIGHT/OFF → 앱 소비 snapshot**을 실측했다. 카메라 화면에 의한 pause의 unregister/release·값 폐기와 복귀도 관측했다.

단, 콘솔에 belt-buckled 전용 제어가 없어 벨트true 전환은 미검증이며 Parking Brake 경고등 On이 실제 PARKING_BRAKE_ON을 켜지 않아 안전 게이트는 잠긴다. **전체 미션 완료/게이트 해제/실차는 검증되지 않았다.** 장시간 중단 후 검은 캡처도 관측해 GPU·화면 안정성은 미검증이다. 실기에서 발견한 '정의 없는 페달 권한 요청'을 차단하고 opt-in debug EMULATOR 진단을 보완했다. 기본 off/실차/release 차단 유지. 근거: SDK 노트의 최신 A01 절 및 docs/evidence/a01-2026-11-13.

### A02 속도 변화 이벤트 코어 (2026-11-13 / 11d43ab)

- 최신 dev `b47e96d` 기반 `feature/a02-speed-events`에서 독립 Kotlin `SpeedEventEngine`/`SpeedEvents`와 JVM 테스트를 추가했다. 기존 VehicleSnapshot·A01 어댑터·UI·권한·Gradle 의존성은 수정하지 않았다. [A02 입력/출력·판정·연결 계약](A02_SPEED_EVENTS.md).
- 명시적 속도 단위·타임스탬프·출처·상태에서 유효한 표본 쌍의 Δv/Δt를 계산한다. 급격한 속도 증가/감소와 원본 근거를 반환하며, 브레이크 조작·주변 위험을 판단하지 않는다. 중복 구간/쿨다운·히스테리시스·오류/신선성·출처/시간 단절을 처리한다.
- 로컬 mock/vehicle JVM 각각 **78/78**, 두 debug 빌드, 웹 **79/79 PASS**. 원격 push CI에서도 Web unit tests 및 Android tests/both debug flavors SUCCESS 확인. PR #19로 dev 대상 검토 중이며 아직 병합하지 않았다.
- 실제 차량/SDK·Emulator 입력·주행 수집 정책·노이즈/임계값 보정·B/C 연결은 **NOT_VERIFIED**. 앱 실신호 경로는 미연결이며 A01의 실수신 검증을 A02 실행 증거로 사용하지 않는다.

### A04 웹 가상 교차로 보완 (2026-11-13 / def35eb)

- `feature/a04-intersection-turns`, 기반 dev `1140c74`. 기존 2D 교차로 배치를 재사용해 가상 진행과 깜빡이 독립 선택, 접근/진입 분리, 진입 속도·가상 차로·구간/신호 불일치, 중복 억제와 다음 교차로 처리를 보완했다. 모든 교차로 기록과 복기에 방향·시뮬레이션 시각·근거·MOCK 출처를 보존한다.
- 기존 네 교차로 이벤트 계약·음성 우선순위는 유지했다. 새 신호/구간 불일치 및 직진/판정 보류 기록은 [A04 계약·실측](A04_INTERSECTION_TURNS.md)에 정리했다. 웹 외 Kotlin·Manifest·Gradle은 무변경이다.
- 웹 전체 **105/105 PASS**, headless Chrome 평가·강습·연습 자동 시연 회귀 PASS, 1440px/390px 콘솔의 독립 조작·넘침 없음·브라우저 오류0 확인. 시연의 늦은 감속 결함도 수정 후 재검증했다.
- S07/S10 PASS는 **웹 2D Mock 범위**다. 실제 회전 물리·3D·보행자/통행권·실제 차선 위험은 구현/검증하지 않았다. Android/Pleos Emulator/실차 검증으로 표시하지 않는다.

### A01 신뢰성 후속 재검증 (2026-11-13 KST23시 / dev1140c74)

[상세 보고서](A01_RELIABILITY_FOLLOWUP.md). 앱 코드 변경 없음. 공식 콘솔48개 카드·경고등 비교에서 벨트true 및 실제 주차브레이크ON 입력 경로가 없어 전체 워밍업은 계속 BLOCKED/NOT_VERIFIED다. 안전 조건을 완화하지 않았다. Settings에서325.96초 중단 후 같은 PID로 화면·새 신호 복귀는 PASS. Sleep/Display OFF에서 전체 검은 캡처 및 Wake 복구를 관측했지만 기존 장시간 검은 화면 사건의 원인/해결 완료는 NOT_VERIFIED다. 두 JVM 각각78/78, 양debug빌드, 웹79/79 PASS. 기본off·debug EMULATOR opt-in·실차/release 차단 유지. 증거는 `docs/evidence/a01-2026-11-13-reliability/`.
