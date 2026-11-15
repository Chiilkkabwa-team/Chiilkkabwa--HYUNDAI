# 지금 바로 제출하기 (치일까봐)

> 공식 안내(https://appdevchallenge.auto-hmg.io/guide/submission) 기준, 2026-11-15 정리. ✅ = 준비 끝, 🙋 = 사람이 할 일.
> 마감: 공식 페이지는 2026-11-15 23:59. 팀이 메일로 받은 **7일 연장**이 있다면 그 메일의 날짜를 따른다.

## 1. 준비물

| 제출물 | 필수? | 상태 | 어디에 |
|---|---|---|---|
| 접수양식(기획서) DOCX | **필수** | 🙋 팀원 전원 동의·서명·날짜 | 공식 양식: https://appdevchallenge.auto-hmg.io/downloads/contest-application-consent.docx |
| 시연 영상 | 선택(가산점) | ✅ 웹 자동 시연 녹화(1920×1080, 소리 없음) · 🙋 소리 있는 판은 PC에서 OBS로(아래 4) | 대화로 받은 `chiilkkabwa_web_demo.mp4` |
| GitHub 저장소(소스+APK) | 선택(가산점) | ✅ | https://github.com/Chiilkkabwa-team/Chiilkkabwa--HYUNDAI |
| APK | 선택(저장소에 포함) | ✅ | `apk/Chiilkkabwa_Chiilkkabwa_release.apk`(제출본, 모의 신호 전체 흐름) |
| README 필수 항목 5개 | — | ✅ 앱 이름·소개 / 실행 방법(§1) / 테스트 환경(§2) / 에뮬레이터 버전·화면 규격(§2: Pleos Connect v2.1.0, 2560×1440, 160dpi) / APK 위치(§3) | README.md |
| 웹 체험판 | (영상·기획서에 링크) | ✅ | https://chiilkkabwa-web.vercel.app |
| 숏폼 링크 | 선택(가산점 없음) | — | 기획서에 적기 |

## 2. 메일 (그대로 복사)

- 받는 사람: `appdevchallenge@hyundai-partners.com`
- 참조(CC): **팀원 전원**
- 제목: `[차량용 앱 개발 공모전] 치일까봐_대표자명` ← 대표자 이름으로 바꾸기
- 첨부: 서명한 접수양식(기획서) DOCX, 시연 영상(용량이 크면 드라이브 링크)

본문:

```
안녕하세요. 차량용 앱 개발 공모전에 '치일까봐' 팀으로 지원합니다.

1. 접수양식(기획서): 첨부(팀원 전원 동의·서명)
2. 시연 영상: 첨부 (또는 링크: ____ )
3. GitHub 저장소: https://github.com/Chiilkkabwa-team/Chiilkkabwa--HYUNDAI
   - 기준 커밋(main): ____________  ← AI가 알려 준 값
   - APK: apk/Chiilkkabwa_Chiilkkabwa_release.apk
4. 웹 체험판(설치 없이 전체 흐름): https://chiilkkabwa-web.vercel.app

감사합니다.
```

## 3. 저장소 공개 (🙋 둘 중 하나)

- **A. 저장소를 Public으로**(가장 간단): GitHub 저장소 → **Settings** → 맨 아래 **Danger Zone** → **Change visibility** → **Public**. **11/16(월)까지** 바꾸고 **11/23까지** 유지. 안 바꾸면 가산점이 없음
  - 주의: 공개하면 **Git 기록의 작성자 이름·이메일도 보인다**(실명 형태 1개·개인 메일 3개 확인됨, [08 §6](08_SUBMISSION_CHECKLIST.md)). 괜찮으면 A
- **B. 공개 대신 압축 파일 링크**: 대화로 받은 `chiilkkabwa_source_apk.zip`(소스+APK, Git 기록 없음)을 구글 드라이브 등에 올리고 '링크가 있는 모든 사용자' 보기 권한 → 그 링크를 메일 3번에 함께 적기. **마감 전까지**

## 4. 소리 있는 시연 영상 (선택, PC에서)

웹의 음성 길잡이는 보는 사람 컴퓨터의 한국어 음성으로 나와서 클라우드 녹화에는 소리가 없다. 소리까지 담으려면:

1. OBS 설치(https://obsproject.com) → 실행
2. 크롬에서 https://chiilkkabwa-web.vercel.app/?demo=1
3. OBS **소스 +** → **창 캡처** → 크롬 창
4. **소스 +** → **응용 프로그램 오디오 캡처** → `chrome.exe` (크롬 소리만)
5. **오디오 믹서**에서 **데스크톱 오디오**·**마이크** 음소거 → 디스코드 소리 안 들어감
6. **설정 → 비디오**: 1920×1080
7. **녹화 시작** → 사이트 **자동 시연** → 연습이 끝나면 위쪽 **평가** → 다시 자동 시연 → **녹화 중지**

## 5. 보내기 전 마지막 확인

- [ ] 기획서 심사용 소개 칸에 실명·연락처·학교가 없는가(블라인드)
- [ ] 팀원 전원 서명·날짜, 전원 CC
- [ ] 메일에 기준 커밋 번호
- [ ] 저장소 공개(A) 또는 압축 링크(B)
- 고칠 게 생기면: 마감 전 같은 주소로 `[수정제출] [차량용 앱 개발 공모전] 치일까봐_대표자명`, **모든 자료와 서명 동의서를 다시 첨부**, 전원 CC
