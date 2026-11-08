# 08. 제출 체크리스트 — GitHub·APK·README·블라인드

> TL;DR — 1차(11/15 23:59): DOCX + 별지 PDF(필수 아님) + 선택 가산점(영상·GitHub·APK). 2차: 에뮬레이터 동작 앱. 모든 산출물에서 실명·학교·개인 계정 노출 금지.

## 1. GitHub 저장소 (공모전 안내 기준, 11/3 조사)
- 소스 + release APK: `apk/팀명_앱이름_release.apk` (팀명은 01장 결정 후)
- README 공식 4요소(지원·제출 방법 페이지 원문): ① 앱 이름 및 한 줄 소개 ② 실행 방법 ③ 테스트 환경 및 필요한 설정 ④ APK 파일 위치 또는 GitHub Release 링크 — 여기에 사용 SDK·영상·웹 체험 링크를 덧붙인다
- **이 저장소는 비공개(private)다** → 심사용 GitHub 계정을 Collaborator로 초대해야 심사가 가능하다
  - ⚠️ 2026-11-06 확인: 공모전 공지사항은 3건(유의사항·모집공고·설명회)뿐이고 **심사용 계정은 아직 공지되지 않았다**. 제출 전까지 `appdevchallenge.auto-hmg.io/notice`를 계속 확인할 것
  - 개인 계정의 비공개 저장소는 Collaborator에게 **쓰기 권한도** 부여되므로 초대 전 권한을 확인한다
  - 대안: 제출 직전에 공개(public)로 전환 (블라인드 점검을 마친 뒤)
- **커밋 작성자 이름·이메일이 실명이 아닌지** (`git log --format='%an %ae'`)
- 저장소 이름·조직명에 학교명 금지

## 1-1. 팀 협업 설정 (2026-11-06 결정)
조직(Organization)으로 옮기지 않고 **개인 비공개 저장소 + 협업자 초대**로 간다. 저장소 주소·Vercel 연결·CI가 그대로 유지돼 제출 직전 사고가 없다.

**팀원 초대 방법** — 저장소 → Settings → Collaborators → Add people → GitHub 아이디 입력
| 역할 | 권한 | 비고 |
|---|---|---|
| 팀원(개발) | Write | 커밋·푸시 |
| 팀원(기획) | Write 또는 Read | 문서만 보면 Read로 충분 |
| 심사용 계정 | Write(강제) | ⚠️ 개인 저장소는 Read만 줄 수 없다 → 제출 직전 **공개 전환**이 더 안전 |

> 초대에는 **GitHub 아이디**가 필요하다(이메일 초대도 가능하지만 상대가 가입해야 함). 아이디를 모으면 한 번에 초대한다.
> 블라인드 주의: 아이디·프로필에 실명·학교가 드러나면 심사에서 노출될 수 있다. 팀 전용 계정을 권장.

## 2. APK — **2026-11-08 빌드 성공**
빌드가 실제로 되는 것을 확인했다. 그 전까지 `ui/`는 한 번도 컴파일되지 않았다.

| 산출물 | 명령 | 상태 |
|---|---|---|
| 모의 차량(전 흐름 시연) | `./gradlew :app:assembleMockDebug` | ✅ BUILD SUCCESSFUL |
| 실제 신호(에뮬레이터) | `./gradlew :app:assembleVehicleDebug` | ✅ BUILD SUCCESSFUL |
| 제출용 release | `./gradlew :app:assembleRelease` | ✅ BUILD SUCCESSFUL |
| 단위 테스트 | `./gradlew :app:testMockDebugUnitTest` | ✅ **17/17 통과** |

**빌드에 필요한 것**(처음 빌드하는 사람용)
1. Android Studio 또는 commandline tools + **Android SDK `platforms;android-35`, `build-tools;35.0.0`, `platform-tools`**, JDK 17 이상(검증은 21)
2. 저장소 루트 `local.properties`에 `sdk.dir=<SDK 경로>` (이 파일은 `.gitignore`에 있다)
3. 네트워크: `dl.google.com`(Google Maven), `repo.maven.apache.org`, `nexus-playground.pleos.ai`(Pleos SDK)

**서명**
- 기본값은 **디버그 키**다 → 설치는 되지만 스토어 배포용은 아니다. 공모전 제출(설치·실행 확인)에는 충분하다
- 자기 키로 서명하려면 루트에 `keystore.properties`(storeFile/storePassword/keyAlias/keyPassword) → `.gitignore`에 이미 들어 있다
- **키스토어·비밀번호를 저장소에 커밋하지 않는다**

**제출 시**
- 시연용은 vehicle 플레이버. mock 플레이버는 별도 표기(applicationId `.mock`)
- 28MB APK를 git에 커밋하지 않는다(`apk/*.apk`가 `.gitignore`) → **GitHub Release**로 올리고 README에 링크(공식 4요소 ④)
- ⚠️ **빌드된다 ≠ 차에서 동작한다.** 에뮬레이터 설치·신호 수신은 아직 **미시험**이다. 보고서·README에 "빌드·단위 테스트 통과"까지만 쓴다(허위 기재 시 선정 취소)
- 에뮬레이터 v2.1.0에서 설치·실행 확인 영상

## 3. 블라인드 점검
- [ ] 코드 주석·README·문서에 실명·학교·연락처 없음
- [ ] 스크린샷·영상에 계정명·이메일 없음(Android Studio 상단, 브라우저 탭 주의)
- [ ] 별지 PDF 속성(작성자) 비움
- [ ] DOCX 02장 블라인드(이미 점검됨, 수정 시 재점검)

## 4. 제출 메일
- 받는 곳 appdevchallenge@hyundai-partners.com, 제목 `[차량용 앱 개발 공모전] 팀명_대표자명`, 팀원 전원 CC
- 첨부: 서명된 DOCX, 별지 PDF, (영상·GitHub 링크는 DOCX 7번)
- 수정: 같은 발신자, 제목 앞 `[수정제출]`, 전체 재첨부
