# CLAUDE.md — 치일까봐(Chiilkkabwa) Pleos Connect 앱

> Claude Code가 이 저장소에서 일할 때 가장 먼저 읽는 파일. 길게 설명하지 말고 이 규칙대로 움직인다.
> 사용자: 한국어, TL;DR 먼저, 전문 용어는 쉬운 말로, 담백한 문체. 결론부터.

## 1. 무엇을 만드나 (한 문장)
면허는 있지만 운전이 두려운 초보·장롱면허 운전자가 **주차된 차 안에서** 조작을 차량 신호로 확인받고(연습 전 5분),
**주행 중에는 화면이 잠기고**(0터치), **다시 주차하면** 오늘 연습을 돌아보는(연습 후 3분) Pleos Connect(AAOS) 앱.
원칙: **확인은 차가, 판단은 사람이.** 앱은 차량을 제어하지 않고(읽기 전용), 운전 실력을 판정하지 않는다.

## 2. 마감과 목표
- 1차 접수 2026-11-15(일) 23:59 (기획서 DOCX + 선택: 시연 영상·GitHub·APK = 가산점)
- 1차 통과 시 2차 PT 2026-12-03~04: **에뮬레이터 또는 기기에서 실제 동작 시연 필수**
- 이 저장소의 목표: mock 플레이버로 전 흐름 시연 → vehicle 플레이버로 에뮬레이터 실제 신호 연결 → release APK

## 3. 지금 상태
- `core/`(순수 Kotlin): 안전 게이트, 미션 판정 7종, 미션 러너, 세션 상태 기계, 요약, 배지, 가상 주차 물리 — **단위 테스트 17개 통과**(2026-11-05, JVM Gradle로 core만 실행. Android 빌드는 아직 안 돌려봄)
- `data/`: AaosVehicleSource(실제 AAOS API), PleosVehicleSource(**TODO(sdk) 의사코드**), MockVehicleSource, PracticeStore(SharedPreferences JSON)
- `ui/`: Compose 화면 8개 + 디버그 패널(mock 전용). **한 번도 컴파일 안 됨** → 첫 작업은 빌드 오류 수정
- Gradle wrapper 8.10.2, AGP 8.8.2, Kotlin 2.0.0, compileSdk 35, targetSdk 34, minSdk 28

## 4. 명령
```bash
./gradlew :app:testMockDebugUnitTest          # 순수 로직 테스트
./gradlew :app:assembleMockDebug              # 모의 차량 APK (SDK 없이 전체 흐름)
./gradlew :app:assembleVehicleDebug           # 실제 신호 APK (에뮬레이터)
adb install -r app/build/outputs/apk/mock/debug/app-mock-debug.apk
adb forward tcp:8088 tcp:8088                 # Virtual Console → http://127.0.0.1:8088
adb logcat -s AaosVehicleSource PleosVehicleSource   # 지원 속성 목록·실패 로그
```

## 5. 반드시 지킬 규칙 (어기면 시연 보류) — 이것만 지키면 나머지는 자유(§8)
1. **모르면 잠근다.** 기어 P + 주차 브레이크 + 주행 제한 없음이 모두 OK일 때만 미션을 연다. 속도 0만으로 주차라고 보지 않는다.
2. **미션은 시작 이후의 새 관측만 성공으로 센다.** 이미 밟힌 브레이크·이전 세션 값으로 완료되면 버그.
3. **출처 라벨**: 실차 신호 / 에뮬레이터 신호 / 모의 — 화면·기록·배지에서 섞지 않는다.
4. **주행 중 화면은 정적**: 애니메이션·소리·알림·입력 없음. `distractionOptimized` 선언 금지.
5. **차량 제어 API 호출 금지**(Door/Seat/Wiper set 등 CONTROL_CAR_* 권한 선언 금지). 화면 제동 버튼·출력 제한은 **구현하지 않는다**(제조사 협업 제안일 뿐). 가짜 제동 API·패킷을 만들지 않는다.
6. **Pleos SDK 시그니처를 지어내지 않는다.** Gradle Sync 후 자동완성·공식 문서로 확인한 것만 쓰고, 확인 전에는 TODO(sdk)로 둔다.
7. 개인정보: 서버·계정·위치·VIN 없음. 기기 내 저장만. 실명·학교를 코드·README·커밋·스크린샷에 넣지 않는다(공모전 블라인드).
8. 성능: 정차 CPU ≤25%, 주행 중 <5%, RAM ≤400MB, 터치 응답 0.25초 이내.
9. 게임형 화면(가상 주차·페달)은 정차 중에만.
10. 새 신호·화면·문구를 추가하면 `docs/` 해당 문서도 같이 고친다.

## 6. 어디를 보면 되나
| 문서 | 내용 |
|---|---|
| `docs/00_BUILD_START_HERE.md` | 첫날 순서, 마일스톤, 완료 기준 |
| `docs/01_PRD.md` | 기능 요구사항·사용자 이야기·인수 기준 |
| `docs/02_ARCHITECTURE.md` | 패키지 구조, 상태 기계, 데이터 흐름, 결정 사항 |
| `docs/03_PLEOS_SDK_NOTES.md` | Pleos SDK·AAOS API·권한·에뮬레이터·Virtual Console — 확인/미확인 표 |
| `docs/04_UI_SPEC.md` | 화면 8개, 문구, 디자인 토큰, 레이아웃 규칙 |
| `docs/05_MISSIONS_CONTENT.md` | 미션·약속·복기·배지 콘텐츠 |
| `docs/06_TEST_PLAN.md` | T01~T12, 단위 테스트, 로그 양식 |
| `docs/07_DEMO_SCRIPT.md` | 시연 영상·2차 PT 대본 |
| `docs/08_SUBMISSION_CHECKLIST.md` | GitHub·APK·README·블라인드 점검 |
| `docs/09_BACKLOG.md` | 할 일 목록(우선순위) |
| `docs/10_CREATIVE_BRIEF.md` | **한 장 기획안 + 자유 영역 + 아이디어 창고** |
| `docs/11_SERVICE_FLOW_UX_REVIEW.md` | 서비스 플로우, 화면별 흐름표, 여정, 장단점, 개선 우선순위 |
| `docs/12_SUBMISSION_STRATEGY.md` | 심사위원 관점 전면 점검, 힘줄 곳, 아이디어 평가, 논리 점검, D-10 일정, 팀 결정 사항 |
| `web/` | 웹 프로토타입(모의 신호 전용) — 흐름·문구를 브라우저에서 먼저 시험 |
| `docs/reference/` | 기획서 완성본(HTML), 별지, 근거 대장 — 제품 의도 확인용 |

## 7. 작업 방식
- 큰 변경 전 `docs/09_BACKLOG.md`의 항목 번호를 기준으로 작업하고, 끝나면 체크.
- 에뮬레이터에서 신호를 확인하면 `docs/03`의 '확인 상태' 열을 `문서 → 계정 → 에뮬레이터 수신`으로 갱신하고 날짜·SDK 버전을 적는다.
- 확인 못 한 것을 완료라고 쓰지 않는다(공모전: 허위 기재 시 선정 취소).

## 8. 창의성에 대해
스캐폴드(화면·구조·문구)는 출발점이다. §5 규칙을 지키는 한 화면 디자인, 연출, 미션 추가, 구조 개선은 더 좋다고 판단하면 바꿔도 된다.
방향은 `docs/10_CREATIVE_BRIEF.md`. 감정 목표는 '평가받는다'가 아니라 **'해냈다'**. 바꾼 이유는 커밋 메시지나 docs에 한 줄 남긴다.
