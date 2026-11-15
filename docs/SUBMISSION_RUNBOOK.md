# 제출 실행서 (2026-11-15 · 1차 접수 마감 당일)

> 이것만 위에서부터 따라 하면 제출이 끝난다. ☐ = 사람이 할 일, 🤖 = AI가 이미 했거나 할 수 있는 일.
> 근거: 공식 [지원·제출 방법](https://appdevchallenge.auto-hmg.io/guide/submission)(11/15 다시 확인), [08 제출 체크리스트](08_SUBMISSION_CHECKLIST.md).

## 0. 현재 상태 (11/15 오전)

| 항목 | 상태 |
|---|---|
| `dev` | 기능 동결(사용자 결정 11/15). GitHub CI 웹·Android 테스트·두 debug 빌드 통과 |
| `main` | 11/13 릴리스 그대로 — **제출 전에 `dev → main` 필요** |
| Android 주행 중 음성 | 🤖 **mock 앱에만** 모의 음성 길잡이 연결(PR #38). vehicle 앱은 말하지 않음(S05) |
| release APK | ☐ 아직 없음 — 이 클라우드 환경은 Maven Central 429로 빌드 불가 → **PC에서 빌드** |
| 공식 규칙의 APK 위치 | 저장소 `apk/` 폴더에 `팀명_앱이름_release.apk`(용량 초과 시 GitHub Releases). 🤖 `.gitignore`가 `apk/*_release.apk`는 올라가게 수정함 |
| 팀명 | 미정 — 파일명·메일 제목에 필요 |

## 1. PC 준비 (한 번만) ☐

PowerShell을 열고(아무 폴더나), 한 줄씩:

```powershell
cd "C:\Users\user_THKim\Desktop\중요 공모전 관련 폴더"
git clone -b dev https://github.com/Chiilkkabwa-team/Chiilkkabwa--HYUNDAI.git
cd Chiilkkabwa--HYUNDAI
```

이미 받아 둔 폴더가 있으면 clone 대신 그 폴더 안에서:

```powershell
git fetch origin --prune
git switch dev
git pull --ff-only origin dev
```

> `fatal: not a git repository`가 나오면 저장소 폴더 **안**이 아닌 곳에서 실행한 것이다(예: `C:\WINDOWS\System32`). `cd`로 들어간 뒤 다시.

Android Studio로 이 폴더를 한 번 연다(File → Open). `local.properties`(SDK 경로)가 자동으로 생기고 Gradle Sync가 끝날 때까지 기다린다.

## 2. 테스트 + release APK 빌드 ☐

저장소 폴더에서:

```powershell
.\gradlew.bat :app:testMockDebugUnitTest :app:assembleMockRelease :app:assembleVehicleRelease
```

- 끝에 `BUILD SUCCESSFUL`이 나와야 한다. 실패하면 마지막 30줄을 AI에게 붙여 준다.
- 결과 파일
  - `app\build\outputs\apk\mock\release\app-mock-release.apk` — **전체 흐름(모의 신호)**
  - `app\build\outputs\apk\vehicle\release\app-vehicle-release.apk` — 차량 신호용(Pleos 연동은 release에서 꺼짐, README §4)

## 3. 에뮬레이터 확인 ☐ (각 5분)

Pleos Connect Emulator v2.1.0(API 34)를 켠 뒤:

```powershell
adb install -r app\build\outputs\apk\mock\release\app-mock-release.apk
adb shell wm size
```

**mock 앱 확인 순서** — 오른쪽 아래 노란 '모의 ▲'를 눌러 패널을 연다.

1. `P` + `주차브레이크 true` → 안전 확인 3개가 '확인'
2. 시작 → 목표 **'출발·정지'** → 워밍업(벨트·브레이크·깜빡이 버튼)
3. `D` → 잠금 화면(화면이 바뀌지 않는다)
4. **`급가속(모의)`** → 약 2초 뒤 **"속도가 빠르게 늘었어요."가 들린다** ← 시연 영상의 음성 장면
5. `P` + `주차브레이크` → 복기 화면의 속도 시간표에 점이 찍힌다

**vehicle 앱(선택)**:

```powershell
adb install -r app\build\outputs\apk\vehicle\release\app-vehicle-release.apk
```

Virtual Console(`adb forward tcp:8088 tcp:8088` → 브라우저 `http://127.0.0.1:8088/`)을 만졌을 때 어디서 멈추는지 화면을 찍어 둔다. 주차 확인에서 잠기는 것이 예상 결과다(신호를 모르면 잠근다 — S03).

AI에게 알려 줄 것: ① 2번 명령 결과(성공/실패, 테스트 수) ② `adb shell wm size` 값 ③ mock 1~5 중 안 된 번호 ④ 소리가 났는지.

## 4. 팀이 정할 것 ☐

| 결정 | 선택지 | 추천 |
|---|---|---|
| 제출 APK 하나 | mock / vehicle | **mock**(심사위원이 흐름 전체를 볼 수 있음, 화면에 '모의' 표시) |
| 팀명 | — | 파일명 `팀명_치일까봐_release.apk`, 메일 제목에 필요 |
| GitHub 공개 방식 | A 지금 저장소 Public / B 소스+APK 압축본 링크 / C 이력 없는 새 공개 저장소 | **B 또는 C** — Git 이력에 실명 형태 작성자 1개·개인 메일 3개(08 §6) |
| `main` 반영 | `dev → main` 릴리스 승인 | 3번 확인 뒤 승인 |

## 5. 릴리스 (승인 후) 🤖 + ☐

1. ☐ APK 파일 이름 바꾸기: `app-mock-release.apk` → `<팀명>_치일까봐_release.apk`
2. ☐ 저장소 `apk\` 폴더에 넣고 커밋(기능 브랜치 → `dev`):
   ```powershell
   git switch -c chore/release-apk
   copy app\build\outputs\apk\mock\release\app-mock-release.apk "apk\<팀명>_치일까봐_release.apk"
   git add apk
   git commit -m "chore: 제출용 release APK"
   git push -u origin chore/release-apk
   ```
   그다음 AI에게 "dev에 합치고 main 릴리스해줘"라고 말한다.
3. 🤖 `chore/release-apk → dev` 병합, `dev → main` 릴리스 PR·CI·병합, README §3·§5·화면 규격 갱신
4. 🤖 Vercel 운영 주소(https://chiilkkabwa-web.vercel.app)를 새 `main`으로 배포·확인
5. 🤖 **제출 SHA** = 병합 후 `main` 커밋을 알려 준다

공개 방식이 B면 🤖 압축본(`.git` 제외, 소스+APK)을 만들 수 있다. 다운로드 링크(구글 드라이브 등)에 올리는 것은 ☐ 사람이 한다.

## 6. 시연 영상 (선택) ☐

- [07 대본](07_DEMO_SCRIPT.md) 순서. 음성 장면은 **mock 앱 + `급가속(모의)`** 로 Android에서 찍는다. 자막에 '모의 신호'.
- 녹화 전: 브라우저 탭·Android Studio 계정명·바탕화면 파일명·알림이 화면에 안 나오게.

## 7. 메일 보내기 ☐ (밤 말고 낮에)

- 받는 곳: appdevchallenge@hyundai-partners.com · 팀원 전원 CC
- 제목: `[차량용 앱 개발 공모전] 팀명_대표자명`
- 첨부: 공식 DOCX(전원 동의·서명·날짜, 02장 블라인드), (선택) 영상
- 본문:
  ```
  GitHub: https://github.com/Chiilkkabwa-team/Chiilkkabwa--HYUNDAI (공개 방식 C면 새 저장소 주소)
  최종 커밋(commit hash): <5-5에서 받은 SHA>
  APK: apk/<팀명>_치일까봐_release.apk
  웹 체험판(모의 신호): https://chiilkkabwa-web.vercel.app
  (B 방식이면) 소스+APK 압축본: <링크>
  ```
- 고칠 게 생기면 마감 전 같은 발신자로 `[수정제출]` + 전체 재첨부.
- Private로 둔 경우: **11/16(월)까지 Public 전환, 11/23까지 유지**(A 방식일 때만).

## 8. AI가 이 환경에서 못 하는 것

- PC·에뮬레이터 조작(Remote Control 폴더가 'not_available' — PC의 Claude 앱 업데이트 후 다시 시도 가능)
- release APK 빌드(Maven Central 429). GitHub CI에서는 debug 빌드만 돈다
- 메일 발송, DOCX 서명, 영상 녹화, 외부 링크 업로드
