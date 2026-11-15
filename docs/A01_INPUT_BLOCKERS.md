# A01 후속 — 공식 입력 제약과 워밍업 회귀

2026-11-14 KST / Issue #16 / A 파트 Codex3
기준 dev: 2a70830a83b1f1ae2a1453cbf2131bb0b52e96b8
브랜치: fix/a01-emulator-input-blockers
전용 Worktree: Chiilkkabwa-Codex3-A01-Inputs/a01-pleos

**앱의 워밍업 판정 결함은 최소 수정했다. 벨트 체결 전환·실제 주차 브레이크 ON·전체 Emulator 워밍업은 여전히 BLOCKED/NOT_VERIFIED다.** Mock 성공을 실제 입력 성공으로 바꾸지 않는다. main·기존 A05/C02 작업·다른 AVD 보존.

## 1. 공식 입력 경로 재조사

| 공식 출처(이번 재열람) | 확인 및 적용 범위 |
|---|---|
| [전체 콘솔 카드](https://document.pleos.ai/docs/pleos-only/app-test-guide/virtual-console-guide), [셋업](https://document.pleos.ai/docs/connect/guide/getting-started/application-development/use-vehicle-emulator) | 플랫폼 시험 UI. Telltales는 경고등, Seat Occupancy는 착석 여부. 버클 체결 및 실제 PARKING_BRAKE_ON 입력 카드가 목록에 없음. |
| [Emulator 릴리즈](https://document.pleos.ai/docs/pleos-only/release-note/emulator-release-note), [설치](https://document.pleos.ai/docs/connect/guide/getting-started/application-development/setup-connect-sdk-emulator) | 현재 게시 버전2.1.0/API34,10월19일 릴리즈. 최소 console2.1.0. 기존 AVD 삭제/교체 안내는 이번 보존 지시 때문에 실행하지 않음. |
| [SDK 릴리즈](https://document.pleos.ai/docs/pleos-only/release-note/sdk-release-note), [Vehicle 소개](https://document.pleos.ai/docs/pleos-only/connect-sdk-pleos/Vehicle/intro) | SDK2.3.2. 소개의 Service 기준은2.3.21. 도메인 개요와 개별 배포 메서드를 구분. |
| [공식 Sample App](https://document.pleos.ai/docs/pleos-only/tutorial/vehicle-sdk-sample-app) | SDK2.3.2/Service2.3.22/Android14 전용AVD가 문서의 샘플 기준과 동일. 기능별 가용성 보장은 별도. 샘플은 typed API 확인 도구이며 존재하지 않는 버클/주차브레이크 쓰기 API를 만들어 주지 않음. 설치/제어권한 복사/샘플 set 호출 없음. |
| [권한](https://document.pleos.ai/docs/pleos-only/connect-sdk-pleos/Vehicle/vehicle-permission) | CAR_SEATS/CAR_DRIVE/CAR_LIGHTS 조회. CONTROL_CAR_*는 사용하지 않음. |
| [공식 Windows 이미지 메타데이터](https://nexus-playground.pleos.ai/repository/raw-releases/connect-avd/2.x/crp-sys-img.xml) | 현재 remotePackage 1개: android34/pleos_car_emulator/x86_64,revision2,표시2.1.0. ZIP4,087,751,964bytes/SHA1 b4f858d5bcc6c71506d7edfc872bcb9e5a11acaa로 기존 설치 기록과 같음. 새 이미지를 내려받거나 덮어쓰지 않음. |

공식 가이드/릴리즈/이미지 저장소와 설치 UI 범위에서 **새로운 허용 입력 도구·지원 이미지를 확인하지 못했다**. 모든 미래 버전/실차가 미지원이라는 결론은 아니다. 공식 [ADB FAQ](https://document.pleos.ai/en/docs/pleos-only/vehicle-app-developer-guide/adb-command-reference-guide)도 기어는 console 사용을 안내하며 버클/주차브레이크의 허용 대체 입력을 확인하지 못함. 디바이스 파일 쓰기·VHAL/비공개endpoint·권한우회·차량제어는 수행하지 않았다.

공모전 O03 재조회는 접근 오류였다. COMPLIANCE_REGISTER의 기존 O03 근거를 참고하며 이번에 새 승인을 받았다고 주장하지 않는다. 새 SDK/권한은 추가0.

## 2. 설치 환경·콘솔 비교

기존 A01 전용 AVD의 비실행 상태 및 점유 없음 확인 후 port5554에서 cold boot. Emulator2.1.0/API34/x86_64,VehicleService2.3.22,connectwebconsole versionName14/versionCode34. host GPU/RAM4096MB/4cores 등 기존 설정 유지. -no-snapshot-load/-no-snapshot-save로 기존 snapshot을 덮어쓰지 않고 data 초기화 없이 실행. C02 emulator5560에는 명령·설치·종료0.

Chrome 공개 UI의 DDS ONLINE,48개 카드/7domains를 직접 확인하고 전체 제목을 [console-observation.json](evidence/a01-2026-11-14-inputs/console-observation.json)에 보존했다. P/표시속도0/브레이크페달0/핸들0이 시험 전후 동일. 콘솔의 차량 입력은 바꾸지 않았다. 기존과 같은 경고등 토글을 반복해 해결로 주장하지 않음.

| 입력 | 지원/검증 범위 |
|---|---|
| Gear P,Brake Pedal,Turn Signal State | 공식 UI 존재. 이번에는 초기 P/false/OFF 수신만 확인. 변화 수신의 이전 PASS는 A01_RELIABILITY_FOLLOWUP과 SDK_NOTES 참조; 이번 신규 전환시험으로 대체하지 않음. |
| Seat Belt Buckled | 전용 입력 없음. 초기 false SDK 콜백/앱 소비 PASS. false→true BLOCKED. |
| PARKING_BRAKE_ON | 전용 입력 없음. AAOS 지원목록/권한과 실제 false 수신 PASS. true 전환 BLOCKED. |
| Seat Belt/Parking Brake/Electronic Parking Brake Telltale | 경고등 입력. 버클/실제 주차브레이크 증거로 사용 금지. |
| Seat Occupancy | 착석 입력. 벨트 대체 금지. |

로컬 Vehicle2.3.2 AAR SHA256 E11FDFE498CAD3ECC0D5A2A6992B0C4A4D135195F1455018127BC069FD5EF937. javap+sources를 다시 확인: Seat의 checkSeatBeltBuckledCapabilities(config,areas),getCurrentSeatBeltBuckledState(area,...),register/unregisterSeatBeltBuckledState는 기존 코드와 일치. **실제 Brake 인터페이스는 checkBrakePedalStatusCapabilities/getCurrentBrakePedalStatus/register/unregisterBrakePedalStatus 네 메서드뿐이며 주차브레이크 메서드는 없음.** 소개 문서의 넓은 Brake 설명을 시그니처로 가정하지 않았다. 주차브레이크는 기존 표준 AAOS PARKING_BRAKE_ON 경로 유지. 새로운 Pleos API·폴백 추측0.

## 3. 실제 SDK → 앱 소비 추적

[initial-signals.log](evidence/a01-2026-11-14-inputs/initial-signals.log)는 코드50bddf178594785b51817d98216267b0a8f451ed의 -PenablePleosSignals=true vehicleDebug를 A01에 install -r로 업데이트 후 새 COLD launch한 PID31744 로그다. APK SHA256 BA87BE458E304A1F1686290711B82E424DFCCEBEB6BF85AEA4E82C8306D83115. 이후 벨트 중단시각 보강은 JVM 검증이며, 이 실환경 로그의 코드SHA를 바꾸지 않는다.

| 단계 | 실제 증거/판정 |
|---|---|
| Console | DDS ONLINE,공개카드48개,P/표시0. 버클/주차브레이크 입력 없음. |
| SDK callback | epoch1794652905.702 BRAKE=false,.754 BELT=false,.812 TURN=OFF(초기 구독). |
| permission/capability | 실제 앱 CAR_SEATS/CAR_DRIVE/CAR_LIGHTS/CAR_POWERTRAIN granted=true. AAOS 지원목록에 PARKING_BRAKE_ON. 정확한 config/area 응답 원본은 이번 로그 미포함; 기존 capability gating을 거쳐 콜백 수신한 사실까지만 확인. 새 capability/쓰기승인 확정 아님. |
| VehicleSnapshot 소비 |1794652906.334 앱 trace:gear=OK/PARK,parkingBrake=OK/false,ux=OK/false,belt=OK/false,brake=OK/false,turn=OK/NONE,source=EMULATOR. |
| 시간 | logcat epoch/PID로 새 실행의 관측 순서 보존. Signal.receivedAtMs는 기존 수신시각 계약,개별 원본 receivedAtMs 필드는 이번 trace 미출력. 임의 역산해 센서 발생시각으로 쓰지 않음. JVM에서 실제 필드 검증. |
| SafetyGate/워밍업 | Locked(PARKING_BRAKE_OFF),화면 P/UX확인·주차브레이크대기·시작비활성. 전체워밍업 미진입/BLOCKED. P+속도0으로 대체 안 함. |

기존 A01Session/Repository 테스트는 등록/중단/재등록·옛세션·늦은true·권한/미지원/타입오류·timeout 후 복구 금지를 계속 실행한다. 이 합성 테스트를 위 실제 콜백과 구별한다.

검은 화면은 이번 cold boot에서 미재현. Awake/Display ON/resumedActivity 및 실제 잠금 화면을 확인했다. 화면은 git 제외 build/a01-inputs/app-gate.png. 과거 검은 화면의 원인·해결 완료는 NOT_VERIFIED이며 GPU변경 없음. 시험 종료 시 우리 앱force-stop/포워딩18088제거/우리AVD emu kill 정상종료. 기존userdata/snapshot 보존.

## 4. 해결한 코드 문제와 Mock 회귀

변경 제품 파일은 core/mission/Missions.kt의 **A01 필수3종만**이다. B/C 화면·음성·기록·저장소/runner·게이트·Vehicle SDK 구독·A02/A03·웹·Manifest/Gradle은 변경0.

- SeatBeltJudge: 이미 체결된 유효 초기값은 그대로 유지해 완료. 미체결/불명 시작은 시작 이후 관측 필요,미래시각 거부. 초기 체결 예외는 신호불명/false 시 폐기하고,중단 이전 관측은 새 체결로 사용하지 않도록 최소 관측시각을 갱신.
- BrakePressReleaseJudge: 미지원/권한거부/끊김/STALE/UNKNOWN에서 부분 눌림·압력metric·edge 폐기. 복구 후 새 누름→해제 필요.
- TurnSignalJudge: 동일 불명 상태에서 부분순서·edge 폐기. 복구 후 새 좌→우→OFF 필요.
- 실패를 성공/skip으로 바꾸지 않음. 기존 Progress 계약상 미완료는 InProgress,미지원/권한/끊김은 Unavailable이며 별도 Failed enum을 새로 만들지 않음.

신규 A01WarmupInputTest는 실제 Android MockVehicleSource+VehicleRepository+MissionCatalog/Runner를 JVM에서 실행한다. 초기 SystemClock not mocked는 기존 주입 가능한 speedNowNanos 포트로 테스트에서만 해결했으며 A02 코드는 무변경. B/C ViewModel 화면을 직접 시험했다고 주장하지 않고 기존 pause/resume 소유자 계약을 테스트 harness에서 적용한다.

필수3+선택3(벨트/브레이크/깜빡이/와이퍼/전조등/핸들)6개 정상완료와 결과 source=MOCK를 확인. 이미 체결 유지,시작 전 누른브레이크 불인정,잘못된 깜빡이 순서 미완료,신호중단/복귀,도중 주차브레이크해제/재주차 및 repository중단/재등록을 검증. 가속페달 미션은 수행하지 않음.

## 5. 실행 명령·판정

환경 Windows/Temurin17.0.20/Gradle8.10.2/Node24.22.1/Android SDK35,실제 이미지API34. JAVA_HOME/ANDROID_HOME은 프로세스에만 지정.

- 기준 양JVM164/164 PASS.
- 신규13개 중 기존코드 assertion6개 RED→수정후13/13 GREEN. 중단전 true 경계 회귀1개 추가 RED→시각보강. 최종14개 포함 전체 결과는 [test-summary.json](evidence/a01-2026-11-14-inputs/test-summary.json) 및 PR/CI.
- .\gradlew.bat --offline --rerun-tasks :app:testMockDebugUnitTest :app:testVehicleDebugUnitTest :app:assembleMockDebug :app:assembleVehicleDebug — 양JVM/양debug 실제 실행. 기존 deprecated/native strip 경고와 실패를 구분.
- node --test web/test/*.test.mjs, git diff --check,최신dev대비 보호파일diff. 실제 결과는 검증대장/summary.
- opt-in compile/실행과 기본debug/release PLEOS_SIGNALS_ENABLED=false 확인을 구분. A02/A03 opt-in 기본OFF 유지. 실제Emulator 전체미션/벨트true/주차브레이크true/실차는 NOT_VERIFIED/BLOCKED.

## 6. 안전 규정·통합 Gate

적용근거/범위: SAFETY_RULES와COMPLIANCE_REGISTER의 O03(공모전),P02/P03(플랫폼 UX),P04(직접하드웨어금지),P06(API지원),P07/P08(보안/최소정보),팀S08/S10. 위 공식입력/SDK 문서 재열람은 공식승인 인증이 아님.

| ID | 이번 변경 범위 판정 |
|---|---|
| S01 | PASS:신규SDK/제어/권한/직접VHAL0,콘솔신호변경0. |
| S02 | PASS:기존공개SDK2.3.2 실물 재확인·새API0. O03재조회실패/새플랫폼승인NOT_VERIFIED. |
| S03 | PASS JVM/관측:PB false/UNKNOWN/UNSUPPORTED/STALE/DISCONNECTED/PERMISSION_DENIED 차단,P/속도0 대체금지. 실제ON게이트/전체워밍업BLOCKED. |
| S04 | PASS변경범위:UI/DO/주행터치 변경0,정차의 실제시작잠금 확인. 주행중 정책전체NOT_VERIFIED. |
| S05/S06 | NOT_APPLICABLE:음성/주행서비스 변경·시험0. 기존 미검증 유지. |
| S07 | PASS:원본callback/앱소비/합성JVM/시각 한계 구분,관측외추론0. |
| S08 | PASS JVM:이미체결 유지·이전/미래/중단값 차단·부분조작폐기. 실제미션BLOCKED. |
| S09 | PASS변경범위:새저장/프로필/서버/위치/영상·음성수집0. 선택신호로그만 추적,화면은로컬git제외. |
| S10 | PASS보고:Mock/실환경·빌드/실수신·callback/완료 등급분리. |
| S11 | 새자산/라이브러리0. 장시간CPU/RAM성능NOT_VERIFIED. |
| S12 | NOT_APPLICABLE:제출/release배포/공개전환0,main보존. |

G1 PASS(배정·독립Worktree·A01범위),G2 PASS(공식문서/실물/외부제약과미확인명시),G3 PASS(strict게이트유지·최소읽기·실차/release/defaultOFF),G4 실제실행등급만PASS/실체결·PBON·전체Emulator BLOCKED,G5 최신dev·충돌·검증·CI 확인후 A01 수정/테스트/문서만 dev통합. 안전 FAIL이 남으면 병합하지 않음. 최종 PR HEAD/merge/사후CI는 PR/Actions로 추적.

## 7. 재현·공식 지원 질문과 완료의 외부 조건

1. 전용2.1.0 AVD를 다른 작업과 비동시 실행(기존data/snapshot보존),adb -s 지정,console 포트전달→Emulation Controls 전체카드 대조.
2. 공개UI에 버클/실PB입력이 없으면 멈추고위로그/이전경고등대조를 제시. 경고등·SeatOccupancy·P/0으로 대체하지 않음.
3. 공식입력 확보 후에만Console입력시각→SDK원본/capability/permission→snapshot(source/status/receivedAt)→strict게이트→신규미션상태를 같은실행에서 추적. 이미체결 사용자는 그대로 유지.
4. 게이트PBON이 없으면 워밍업 미진입. 각실입력3항목은 독립판정. 끝나면 P/속도0 등 시험초기조건 복원,구독/앱/전용AVD정리.

공식개발자 지원에 전달할 질문(초안만 작성,이메일 전송안함):
- Service2.3.22/SDK2.3.2/Emulator2.1.0의 Seat ROW_1_LEFT buckled=false→true와 AAOS PARKING_BRAKE_ON=false→true를 넣는 **문서화된 공개UI/이미지**가 있는가? 정확한메뉴/버전/가이드 요청.
- Telltales와실제상태가분리된현재 동작이 의도된제약인가? 알려진문제/공식패치/지원일정 요청.
- 새이미지가있다면별도AVD로기존data보존설치가능한공식package/revision/checksum·호환서비스버전 요청.
- Vehicle소개에주차브레이크조회가서술됐으나실제2.3.2 Brake엔페달4메서드만있음. 실제주차브레이크는AAOS를쓰는것이맞는가? 읽기capability/권한/콜백문서 요청.
- 공식샘플에서이두신호의read-only확인과허용되는외부입력연동을어떻게재현하는가?

**완료 외부조건:** 문서화된버클/실PB입력제공 또는공식수정이미지/안내. 확보후true전환 실수신과정차조건충족전체워밍업을재검증해야 Issue#16 전체완료로판정가능. 현재Issue는열어둔다.

최종 코드커밋: 8469119f48c5ed60db9b3d5eb6d721dacf536f80. 최종 로컬 양JVM 각각178/178(신규14)·양debug빌드 PASS,86tasks실행/20초,웹126/126 PASS. 이후 문서 커밋은 제품코드를 변경하지 않는다. 실환경 관측은 위 명시한50bddf1에 한정한다.
