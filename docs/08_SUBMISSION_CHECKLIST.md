# 08. 제출 체크리스트 — GitHub·APK·README·블라인드

> TL;DR — 1차(11/15 23:59): DOCX + 별지 PDF(필수 아님) + 선택 가산점(영상·GitHub·APK). 2차: 에뮬레이터 동작 앱. 모든 산출물에서 실명·학교·개인 계정 노출 금지.

## 1. GitHub 저장소 (공모전 안내 기준, 11/3 조사)
- 소스 + release APK: `apk/팀명_앱이름_release.apk` (팀명은 01장 결정 후)
- README 4요소: ① 앱 소개 ② 실행 방법(에뮬레이터 버전·플레이버) ③ 사용 SDK/API ④ 데모 영상 링크 — 형식은 공식 안내 재확인
- 비공개라면 심사 계정 초대(계정은 공지 확인)
- **커밋 작성자 이름·이메일이 실명이 아닌지** (`git log --format='%an %ae'`)
- 저장소 이름·조직명에 학교명 금지

## 2. APK
- `./gradlew :app:assembleVehicleRelease`(서명 필요) 또는 공지 기준
- 시연용은 vehicle 플레이버. mock 플레이버는 별도 표기(applicationId `.mock`)
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
