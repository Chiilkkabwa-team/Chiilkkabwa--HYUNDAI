# A02 Pleos 주행 생명주기 검증

2026-11-15 KST / Codex1 / `test/a02-driving-lifecycle`
기준 dev `01eff12a2ecdbd4ae3e0fdb78f6031504aa13540`. 전용 worktree `chiilkkabwa-codex1-a02-lifecycle/a01-pleos`.

**이 Pleos 2.1.0 이미지에서는 주행 차단 화면이 나타나도 MainActivity가 RESUMED로 남아 A02 속도 콜백을 받았다.** 실제 pause에서는 구독이 중단되고 재진입 시 새 세션과 Baseline이 생겼다. 기술적 수신과 공식 허용은 다르며, 현재 A02를 주행 제품 경로로 활성화할 근거는 확보하지 못했다. 기본 OFF·debug EMULATOR opt-in·실차/release 차단을 유지한다.

## 1. 정책과 판정 경계

2026-11-15 KST에 현재 공식 원문을 재열람했다.

- [P02 지역별 주행 규제 정책](https://document.pleos.ai/docs/pleos-only/vehicle-app-planning-guide/driving-restiction-policy): DO 미선언 앱은 Parking에서 사용 가능, Drive에서 사용 불가다. 플랫폼 버전·양산 차량마다 정책 차이가 있을 수 있다. 현재 표에 과거 문서의 Idling 행을 임의로 추가하지 않는다.
- [P03 주행 규제 개발 가이드](https://document.pleos.ai/docs/pleos-only/vehicle-app-developer-guide/driving-restriction-development-guide): 플랫폼이 전달하는 CarUxRestrictions 및 ActiveRestrictions를 따른다. 지역이나 기어만으로 제한을 자체 대체하지 않는다.
- [공식 Virtual Console 가이드](https://document.pleos.ai/en/docs/pleos-only/app-test-guide/virtual-console-guide): 콘솔의 기어·가속/브레이크 시뮬레이션만 사용했다. Safety Telltales를 실제 주차 브레이크 신호로 해석하지 않았다.

문서는 특정 onPause/onStop/onDestroy 순서를 보장하지 않으며, 이 앱의 주행 중 데이터 수집·서비스 예외를 승인하지 않는다. **DO 미선언 UI의 주행 사용은 NOT_ALLOWED**, 백그라운드 지속 수집에 대한 공식 허용은 **NOT_VERIFIED**다. 주행 중 UI 차단 뒤 데이터가 들어왔다는 실측을 수집 허가로 바꾸지 않는다. 공모전 공개 SDK 사용 가능 범위(O03)도 실행 수명·주행 정책의 예외 승인이 아니다. 팀 S03/S05의 보수적 차단은 그대로다.

## 2. 정적 코드 추적

| 경로 | 확인한 동작 |
|---|---|
| MainActivity.onResume | vm.startSignals() 호출 |
| MainActivity.onPause | preview 취소, vm.stopSignals(), super.onPause |
| ChiilViewModel | stopSignals가 runner.pause → repo.stop → 잠긴 snapshot 소비. onCleared에서도 repo.stop |
| VehicleRepository (VehicleSignals.kt) | start의 running 중복 방지, stop에서 generation을 먼저 폐기하고 모든 source.stop 후 speed를 DISCONNECTED/null로 삭제. 이전 sink는 새 generation에 쓸 수 없음 |
| AaosVehicleSource | AaosSpeedSource에 위임; stop에서 속도 소스 중지, property/UX 리스너 해제, Car.disconnect. 이전 callback은 이전 repo sink에 격리 |
| AaosSpeedSource | start마다 이전 stop, SpeedSignalSession 새 시작, 200ms heartbeat. stop에서 Handler callback 제거 후 session.stop |
| SpeedSignalSession | stop 시 generation/live를 먼저 폐기하고 client.close·bridge.stop; publisher 해제. 다음 시작은 새 UUID·새 bridge. terminal 연결·권한 오류를 callback으로 임의 복구하지 않음 |
| AaosSpeedReadClient | closed를 먼저 true로 변경, 공개 unregisterCallback(speed) 후 finally에서 callback/manager 제거 및 Car.disconnect. 비공개/VHAL/제어 호출 없음 |

stop 후 SDK가 자원을 완전히 회수했다는 외부 ACK는 별도 확인하지 않았다. `source_stop_returned`는 앱 함수 반환 마커이며 자원 회수 인증이 아니다. 정적 해제 호출·JVM fake close·실제 콜백 중지 증거를 각각 구분한다.

기존 ViewModel에는 200ms delay coroutine이 살아 있다. PREPARING 때만 snapshot 작업을 수행하지만 delay 자체는 프로세스 생존 중 반복된다. 따라서 A02 중단과 프로세스 CPU 0을 동일시하지 않는다. 이번에 ViewModel을 재작성하지 않았다.

## 3. 환경·입력·실측

새 AVD `Chiilkkabwa_Pleos_2_1_0_A02_Lifecycle`, serial `emulator-5558`, 사용자10. 공식 설치 이미지 `system-images;android-34;pleos_car_emulator;x86_64`, fingerprint `release_connect_v2.1.0`, Android14/API34, Car API34/Platform34.1, hardware ranchu. Emulator37.2.13, WHPX, RAM4096MB/4코어/GPUhost, cold boot/no snapshot/no window/no audio. 새 AVD는 ignored `.gradle/a02-lifecycle/avd`에 생성했고 기존 A01/C02 데이터는 복제·수정하지 않았다.

첫 점유 확인 때 기기는 없었고 여유 RAM 약12GB였다. 우리 AVD를 시작한 뒤 다른 작업의 `emulator-5562`(A01 read-only)가 나타났다. 그 장치에는 설치/권한/종료/forward 명령을 보내지 않았다. 병행 실행과 첫 cold launch의 지연이 있으므로 성능 인증으로 일반화하지 않는다.

시험 APK SHA256 `6FD8228174FC8583B8353CD5B475EBC7B6B3E0E375D7ECCE33A40B36A6830FE6`, `enableAaosSpeed=true`, USE_MOCK=false, A01/A03 opt-in=false. 코드 범위는 기준 dev + 이 브랜치의 A02 로그 마커 두 개이며 최종 커밋/PR로 추적한다. SDK·모델·시계·등록 동작은 변경하지 않았다. APK/AVD/SDK 바이너리는 커밋하지 않는다.

| 시나리오 | 실제 관측·판정 |
|---|---|
| P 최초 실행 | PID12030, MainActivity create/start/resume, source_start, EMULATOR/UNKNOWN→OK0m/s, FIRST_SAMPLE Baseline. 초기에 am start -W는 timeout을 반환했으므로 즉시 실행 성공으로 적지 않음. 이후 실제 RESUMED·수신으로 실행 확인 |
| 속도 권한 | fresh 사용자10 CAR_SPEED false 확인 → 표준 debug pm grant로 true 구성. 사용자 Allow/Deny 조작 성공을 뜻하지 않음 |
| D/0 | CarDrivingState 0→1, 실제 display0 UX DO=true/UxR80(0x50). Activity RESUMED·같은 세션·0m/s 수신 유지 |
| 공식 ACCEL 입력 | CarDrivingState1→2, 실제 비영 속도 수신. 전체 시험 OK3416관측, 최대0.8333334m/s. moving checkpoint는0.2777778m/s. SDK 표본과 콘솔 표시를 구분 |
| 주행 차단 화면 | 플랫폼 화면에 사용 불가 안내. MainActivity pause/stop/destroy는 발생하지 않았고 같은 세션에서 콜백/Evaluated가 계속됨. 화면 차단 ≠ 수집 종료 |
| 주행 중 후보 이벤트 | 이번 제한된 입력에서 RAPID_SPEED_*는 **0건**. Evaluated까지 확인, 새 급변 후보 발행은 NOT_VERIFIED. 이전 A02 시험의 후보 발행을 이번 동시 UX 실측으로 대체하지 않음 |
| P 복귀 | 실제UX false/0·DrivingState0 복원. pause가 없었으므로 **동일 sessionId 유지**. P 복귀만으로 새 session/Baseline이 생긴다고 하지 않음 |
| HOME 시도 | 이 셸에서 pause 없음, 약10초간 수신 지속. 후속 am start도 기존 top instance에 전달. 성공한 중단 시험으로 집계하지 않음 |
| P에서 시스템 설정 화면 열기 | MainActivity onPause/onStop, DISCONNECTED/null, source_stop_returned. 설정값 변경 없음 |
| 실제 중단 구간 | guest log epoch1794671236.158→1794671258.151 사이 A02 observation/consumed **0건**(약21.993초). OS lifecycle와 앱 마커를 대조 |
| 앱 재진입 | 동일PID12030, 새 sessionId와 FIRST_SAMPLE Baseline. 전 세션 anchor를 이어 계산하지 않음. start 이후 초기값/느린 처리로 STALE이 되면 재기준을 잡는 기존 동작 유지 |
| 권한 철회·재시작 | pm revoke 후 이전 프로세스 종료; 새PID17603에서 PERMISSION_DENIED/null·Rejected, 권한창 관측. 강제 프로세스 종료에서 정상 onPause를 주장하지 않음 |
| 테스트 재부여·cold restart | pm grant 후 am force-stop/명시 실행, 새PID18172·새 sessionId·OK0/Baseline. 권한창 직접 조작에 의한 복구는 NOT_VERIFIED |
| 기본 복원 | 기본OFF APK 재설치, CAR_SPEED 철회, 새PID15536에서5초 관측 A02 trace0건. A01/A02/A03 flags=false, release는 opt-in에도false. 우리forward28088/로그/AVD만 종료, 데이터 보존 |

`source_start` 앞의 `source_stop_returned`는 start가 기존 상태를 정리하는 호출이다. 이 마커 수만으로 실제 중복 구독을 세지 않는다. 첫/복귀 세션에서 같은 세션의 STALE 이후 FIRST_SAMPLE이 추가로 나올 수 있고, 그것을 새 UUID나 중복 구독으로 오인하지 않는다.

CPU: pause된 PID12030의 uptime521.07→535.20초 사이 utime1146→1160, stime1712→1732, CLK_TCK100이었다. 합계34ticks=0.34 CPU초, 약14.13초 구간의 단일 코어 기준 약2.4%다. 샘플 수집 시각 차이를 포함한 대략값이며 A02 자체 CPU 사용량이나 P10 성능 합격 판정이 아니다. A02 callback/result는 0이지만 전체 프로세스 작업은 0이 아니다.

[원본 발췌·서비스 상태·화면·검증 요약](evidence/a02-lifecycle-2026-11-15/README.md). 로그 prefix는 guest epoch이고 speed propertyNs/receiptNs 및 lifecycle marker elapsedNs는 boot monotonic ns다. 서로 바꿔 넣지 않는다. 서비스 change log는 guest 현지 시각, phase header는 hostUTC다. 이미지의 기본 부팅 시각에서 시계가 동기화된 초기 이력도 그대로 남겼다.

## 4. 회귀와 변경 범위

새로운 동작 보정이 필요한 L3 결함은 이번 시험에서 재현되지 않았다. **기능 동작 수정 없음**. AaosSpeedSource의 기존 enabled 조건 안에 `source_start/source_stop_returned` 진단만 추가했다. MainActivity/ViewModel/repository, 기존 engine/Shadow, A01/A03/B/C/Web/manifest/Gradle/안전 게이트 무변경. DO/서비스/주행 persistence/음성·제어·VHAL 추가0.

새 JVM4개는 명시적 합성 fake client + 실제 repository/session 조합이다:

- repeated repo.start/ready 중복 구독 방지·중복 stop 안정성
- stop의 client.close 중 재진입 callback 및 이후 지연 callback/heartbeat 차단
- 재진입의 새 ID/Baseline·이전 timestamp STALE·원본 근거와 이벤트 ID/세션/출처/단위 유지
- 권한 오류 terminal/늦은 정상 callback 거부·명시 재시작 복구

세션 경계/늦은 이벤트 차단 **PASS(JVM)**. 실기에서는 실제 pause 후 수신 공백/새 세션·Baseline **PASS**, 실제 지연 Binder callback을 고의 주입하지 않았으므로 해당 경합의 실기 재현은 NOT_VERIFIED다. fake 시험을 실제 Binder 경쟁 성공으로 적지 않는다.

Windows/PowerShell/Temurin17.0.20.101/Gradle8.10.2/SDK35/Node24.18.1. 기준 양JVM206, 변경 후 **양JVM210/210**, 두 debug 빌드 PASS(14초), 웹126/126 PASS, diff check PASS. `-PenableAaosSpeed=true :app:generateVehicleReleaseBuildConfig`도false 확인. 기능 추가 없이 기존 동작을 확인하는 회귀이므로 새 기능 RED를 만들었다고 주장하지 않는다. 기존 캐시 lock 접근은 승인 실행으로 수행했고 승인 검토 크레딧 부족으로 복원 명령이 한 차례 실행 전 거절됐으나 사용자 재개 후 실제 복원을 완료했다.

## 5. A→B/C 계약과 제품 제약

실제 B/C 연결은 없다. `Baseline`은 기준만 세움, `Rejected`는 계산 실패/연속성 단절, `Evaluated`도 event=null일 수 있다. `SpeedChangeEvent`만 후보 사건이다. eventId는 sessionId+sequence, sessionId는 구독 수명이며 제품의 여행 세션과 같다고 가정하지 않는다. type은 속도 증가/감소 관측이며 가속/브레이크 페달 조작·위험·사고 정답이 아니다. evidence는 두 signed 원본 sample·METERS_PER_SECOND 단위·상태·source·normalized값·dt/가속도를 보존한다. occurredAtMs는 현 부팅 elapsed ms이고 epoch 날짜가 아니다. propertyNs/receiptNs는 원본 ns로 별도 보존한다.

| 완료 항목 | 판정 |
|---|---|
| Activity 주행 상태 전환 | PASS(관측): UX/화면 차단 확인, 자동 pause/stop/destroy **없음** |
| 실제 구독 중단·복귀 | PASS(설정 화면 전환에서 실제 pause/stop/새 세션). 주행 overlay 자체의 중단은 관측되지 않음 |
| 세션·늦은 이벤트 차단 | PASS JVM; 실기 자연 pause/복귀 PASS, 강제 Binder 지연 재현 NOT_VERIFIED |
| 주행 중 실제 수집 가능성 | VERIFIED는 이 debug AVD의 RESUMED 구간 수신에 한정. 전체 주행·백그라운드/양산 지속성 NOT_VERIFIED |
| 공식 허용 | DO 미선언 주행 UI NOT_ALLOWED. 이 앱의 주행 데이터 지속 수집/서비스 예외 NOT_VERIFIED |
| 제품 A02 주행 이벤트 활용 | BLOCKED: 공식 실행 범위와 수명 보장·정확도 근거 부족. 이번 급변 후보도0건으로 새 발행 NOT_VERIFIED |

화면 가림만으로 onPause가 온다고 가정하면 제품 설계가 성립하지 않는다. A02 기본 활성화·B/C 연결 전 (1) 실제 앱 카테고리·정책상 허용 실행 경로 확인, (2) UX/Activity/프로세스 상태에 따른 구독 범위와 종료 계약 확정, (3) 양산 버전별 시험, (4) 정답 레이블 기반 표본 품질/지연·누락 검증, (5) 동일 monotonic 시계의 제품 세션·중복 방지/개인정보 계약이 필요하다. 승인 없이 DO나 FGS를 붙여 해결하지 않는다. Shadow는 계속 독립 EXPERIMENTAL이다.

## 6. S01–S12 / G1–G5

근거와 적용 범위는 [COMPLIANCE_REGISTER](COMPLIANCE_REGISTER.md), [SAFETY_RULES](SAFETY_RULES.md) 및 §1 현재 원문을 따른다. PASS는 이번 diff/시험 범위이며 공식 승인 인증이 아니다.

| ID | 판정·증거 |
|---|---|
| S01 | PASS: 공개 AAOS 읽기만, 차량제어/CONTROL 권한/직접 VHAL0. 플랫폼 P04 |
| S02 | 기존 공개 API 실행·빌드 PASS, 신규SDK0. 공모전 O03을 주행 수집 승인으로 확대하지 않음 |
| S03 | 기존 gate 무변경·속도0으로 주차 판정0, 회귀 PASS. 실제 주차브레이크ON/학습 gate 시험은 NOT_VERIFIED. 팀 규칙/P02 |
| S04 | PASS(관측): 주행 차단 화면 확인·조작 우회0. 앱 lifecycle 자동중단 보장 없음. 전체 UI 인증 NOT_VERIFIED. 플랫폼 P01/P02/P03 |
| S05 | **NOT_VERIFIED**: 이 앱의 주행 수집 허용·서비스 예외·양산 수명. 기술수신만 확인; 기본OFF/실차·release차단/서비스0 유지. DO미선언 주행 UI는 P02상 사용 불가 |
| S06 | NOT_APPLICABLE: 음성/오디오 연결0. 플랫폼 P09 |
| S07 | PASS: 출처/원본ns·Float·세션·상태/발행0·동시UX 근거 분리. 실제 판정 정확도 NOT_VERIFIED. P06/팀 |
| S08 | NOT_APPLICABLE: 미션/벨트/운전 연습 변경0. 팀 규칙 |
| S09 | PASS(변경): 전용 가상속도·임시PID/세션/OS상태만, 새 위치/VIN/계정/음성/외부업로드0. P07/P08 |
| S10 | PASS: OS화면/Activity/SDK수신/계산/후보/정책/합성·실차를 구분. 팀 규칙 |
| S11 | CPU 표본 관측만 PASS, 자원최적화·장시간·P10성능 인증 NOT_VERIFIED. 신규자산/의존성0 |
| S12 | main/다른worktree/AVD 보존 PASS. APK 로컬 시험만, 제출/릴리스/공개전환 NOT_APPLICABLE. O01 |

G1 PASS: 배정·기준dev·전용범위·인수판정 정의. G2 PASS: 현재P02/P03 원문 및 플랫폼/공모전/팀 규칙 구별. G3 PASS: 읽기·기본OFF·원본상태·중단·새세션·B/C미연결. G4: JVM/빌드/웹/위의 제한된 실측만 PASS, 미수행 시나리오는 NOT_VERIFIED. G5: 안전 FAIL 없이 **비활성 진단·검증 자료만** 통합하며 PR/최신dev/CI 결과는 VERIFICATION_LOG와 해당 PR을 정본으로 기록한다. 정책 미확인을 실제 사용 가능 상태로 승격하지 않는다.
