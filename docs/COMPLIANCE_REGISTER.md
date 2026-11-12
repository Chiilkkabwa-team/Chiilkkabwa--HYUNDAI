# 공모전 · Pleos 규정 근거 대장 (COMPLIANCE_REGISTER)

> 기준일: **2026-11-13 KST** · 담당: 팀 공동 관리 · 상태: **공식 문서 조사 및 원문 대조 완료 / 앱 준수·에뮬레이터 검증은 별개**
>
> 목적: 팀이 조사한 「공모전 규정사항(1).txt」의 17개 영역을 보존하면서, 공식 공모전 규정과 Pleos 문서의 근거·적용 범위·해석상 주의점을 한곳에 관리한다. **이 파일은 현대차·Pleos 문서의 전문 복사본이 아니다.** 짧은 인용과 원문 URL을 포함한 추적 가능한 규정 대장이다. 실제 준수의 실행 기준은 [SAFETY_RULES.md](SAFETY_RULES.md).

## 읽는 법과 우선순위

| 표기 | 의미 |
|---|---|
| **[공모전 필수]** | 현 공모전 참가·제출·기술 범위에 직접 적용되는 공식 조건 |
| **[플랫폼 적용]** | 실제 사용하는 Pleos/AAOS 기능에 적용되는 기술·안전 조건 |
| **[출시 단계]** | Pleos App Market 등록·배포 심사 시 적용; 공모전 직접 실격 조건과 구별 |
| **[팀 자체]** | 치일까봐가 선택한 더 보수적인 제품·안전 제약 |
| **[미확인]** | 문서 차이·에뮬레이터·실제 권한 확인 전에는 PASS나 허용으로 단정 금지 |

**출처 우선:** 최신 공모전 공식 공고·FAQ 및 Pleos 원문 → 실제 SDK/에뮬레이터 관측(기술적 동작 증거) → 최종 접수 DOCX(제품 목표) → 팀 규칙 → 과거 회의·README. SDK 문서와 공모전 특례가 다르면 **공모전 참가 가능 범위는 공모전 공식 공지**, 실제 API 호환·권한·주행 동작은 **Pleos 원문과 실행 결과**를 각각 적용한다. 공식 원문이 서로 모순되면 임의 해석으로 허용 처리하지 않는다.

## 공식 원문 색인

| ID | 문서 | URL |
|---|---|---|
| **O01** | 공모전 제출 방법 | https://appdevchallenge.auto-hmg.io/guide/submission |
| **O02** | 공모전 FAQ | https://appdevchallenge.auto-hmg.io/faq |
| **O03** | 공모전 개발 범위·기술 문의 공지 | https://appdevchallenge.auto-hmg.io/notice/c0a1a000-2026-4923-8000-000000000008 |
| **O04** | 공모전 참가·출품 유의사항 | https://appdevchallenge.auto-hmg.io/notice/c0a1a000-2026-4923-8000-000000000006 |
| **O05** | 심사·시상 | https://appdevchallenge.auto-hmg.io/guide/judging-awards |
| **O06** | 참가 규정 | https://appdevchallenge.auto-hmg.io/guide/rules |
| **O07** | 개발 환경 | https://appdevchallenge.auto-hmg.io/guide/development |
| **P01** | 주행 안전 설계 | https://document.pleos.ai/docs/pleos-only/vehicle-app-planning-guide/driver-distraction-guideline |
| **P02** | 지역별 주행 규제 | https://document.pleos.ai/docs/pleos-only/vehicle-app-planning-guide/driving-restiction-policy |
| **P03** | 주행 규제 개발/CarUxRestrictions | https://document.pleos.ai/docs/pleos-only/vehicle-app-developer-guide/driving-restriction-development-guide |
| **P04** | 하드웨어 접근 정책 | https://document.pleos.ai/docs/pleos-only/vehicle-app-planning-guide/platform-api-architecture |
| **P05** | SDK API 정책 | https://document.pleos.ai/docs/pleos-only/vehicle-app-planning-guide/pleos-connect-sdk-api |
| **P06** | 차량 API 호환·가용성 | https://document.pleos.ai/docs/pleos-only/vehicle-app-planning-guide/api-compatibility-availability |
| **P07** | 보안·개인정보 설계 | https://document.pleos.ai/docs/pleos-only/vehicle-app-planning-guide/security-privacy |
| **P08** | 앱 보안 가이드 | https://document.pleos.ai/docs/pleos-only/vehicle-app-developer-guide/app-security-guide |
| **P09** | 오디오 포커스 개발 | https://document.pleos.ai/docs/pleos-only/vehicle-app-developer-guide/audio-focus-development-guide/audio-focus-development |
| **P10** | 앱 실행 환경·성능 | https://document.pleos.ai/docs/pleos-only/vehicle-app-developer-guide/app-runtime-environment-resources-guide/app-performance-platform-identify |
| **P11** | 성능·리소스 설계 | https://document.pleos.ai/docs/pleos-only/vehicle-app-planning-guide/performance-resource-mgmt |
| **P12** | SDK/Emulator 실무 노트(원문 링크 색인) | [03_PLEOS_SDK_NOTES.md](03_PLEOS_SDK_NOTES.md) |
| **P13** | 공식 NaviHelper 예제 | https://github.com/Pleos-Playground/pleos-sample-navi-app |

## 1. 공모전 참가 및 제출 [공모전 필수]

- **참가:** 대학(원)생·일반인·스타트업, 개인/팀, 대표 포함 최대 5명; 대학생 및 일반 부문. 혼합 팀 부문 처리 등 불명확한 세부사항은 주최 측 확인. [O06]
- **필수:** 공식 접수양식이 기획서이며 팀원 전원이 개인정보 동의 여부·서명·날짜를 작성. 대표자는 팀원 모두 이메일 CC. **CC는 개인별 동의를 대신하지 않는다.** [O01][O02]
- **마감:** 2026-11-15 23:59 KST; 수신 `appdevchallenge@hyundai-partners.com`; 제목 `[차량용 앱 개발 공모전] 팀명_대표자명`. 수정은 마감 전 같은 발신 주소로 **전체 자료와 동의서 재첨부**, 팀원 전원 CC, 수정 제목 규칙 준수. 마지막 유효 제출본을 심사. [O01][O02]
- **선택:** 1차 시연 영상(목업·웹 프로토타입·녹화 인정), GitHub(소스+APK), 숏폼. 시연 영상/개발 산출물은 단계별 가산점; 숏폼은 본상 가산점 아님. [O01][O05]
- **블라인드:** 공식 접수 DOCX의 심사용 소개 영역에 실명·연락처·이메일·소속을 노출하지 않도록 원본 양식 지시 확인. Git 작성자·원격 링크 노출의 적용 범위는 [미확인]이며 공개 전 별도 점검. 이 항목은 팀이 보유한 공식 양식 확인 기록을 토대로 관리.
- **GitHub/제출 APK:** 실행 가능한 release APK를 소스와 함께 `apk/`에 제출, 권장 파일명 `팀명_앱이름_release.apk`; 용량 초과 시 GitHub Releases. URL과 **기준 커밋 SHA**를 메일에 기재. README에는 소개/실행 방법/필요 설정/에뮬레이터 버전·화면 규격/APK 경로 기재. [O01]
- **Private 저장소:** **Collaborator 초대 불필요**. 11/16까지 Public 전환해 11/23까지 유지하거나, 마감 전에 **소스+APK 압축 파일 다운로드 주소**를 제출. [O01][O02]
- **원문 단문:** “심사용 계정을 Collaborator로 초대하실 필요는 없습니다.” [O01]
- 공개 전에 전체 Git 이력·커밋 이메일·키·토큰·개인정보를 감사. `.gitignore`는 이전 커밋의 노출을 제거하지 않음. [팀 자체]

## 2. 심사·선정·현장 시연 [공모전 필수]

- 1차 평가 축: **차량특화성, 창의성, 기술 활용성, 사업성**, 개발 산출물 선택 가산점. 세부 배점·2차 세부 심사기준은 공개되지 않거나 추후 안내. [O05]
- 1차 합격 **20팀**은 2차 PT에서 실행 가능한 앱의 에뮬레이터 또는 기기 시연이 필수; **실차는 필요하지 않음**. 2차 PT 2026-12-03~04, 최종 발표 예정 12/18. [O03][O04][O05]
- 최종 선정 **10팀**은 행사 전시 구동 APK 제출 및 2027-02 현장 시연·시상식 참석 의무. 선정 이후는 새로운 기능 경쟁이 아닌 전시 영상·APK 구동 안정화 기간. [O04][O05]
- 공모전 심사는 **Pleos App Market 입점 심사가 아님**. 입점·배포는 별도 기준·승인이 필요하며 보장되지 않음. [O03][O05]

## 3. 창작물·IP·상금 [공모전 필수]

- 독창적인 출품작만 제출. 표절·기 수상작 재출품·허위 표시·타인의 저작권/디자인권/초상권 침해는 심사 제외·선정 취소·상금 환수 위험. [O04]
- 출품 자료의 권리는 원칙적으로 응모자에게 있음. 수상 아이디어의 **통상실시권**은 이용 범위·기간·대가 등을 협의할 수 있으며, IP 양도 등에 대한 **4개월 우선협상권** 관련 조항이 있음. *자동 전체 양도로 해석 금지.* [O04]
- 현대차그룹 임직원 참가 가능하나 최종 수상 대상 제외. 상금은 대표자에게 일괄 지급·원천징수하며 팀 내 분배 분쟁에 운영진 중재 없음. 제출 자료 보관·폐기 기간은 공식 공지 확인. [O04]
- 외부 이미지·3D 모델·폰트·음원, Typecast/ElevenLabs 생성 음성의 **생성·배포·공개 시연 라이선스**를 각각 확인. [팀 자체; O04의 권리침해 방지 이행]

## 4. 개발 환경·SDK 허용 범위 [공모전 필수 / 플랫폼 적용]

- Android API **28 이상**, Gradle **8.0 이상**, Java 또는 Kotlin. Pleos Emulator **2.1.0** 및 API 34 이미지는 현재 문서·프로젝트 안내 기준이며 SDK/이미지 버전은 실제 설정에서 재확인. [O07][P12]
- 공모전 공식 공지는 **Vehicle, NaviHelper, TextToSpeech, SpeechToText, FusedLocation SDK**를 공개 문서 범위에서 사용할 수 있다고 명시. Playground UI에 `준비 중` 표시가 있어도 **공개 문서가 허용하는 에뮬레이터 개발·시험**과 구분. [O03]
- **문서 비공개 SDK, Pleos LLM SDK 공모전 기간 미제공**; **Gleo Interface(App Schema) 검토·승인 신청 불가**; **차량 마이크 음성을 외부 STT 서비스로 전송 불가**(음성 인식은 공식 SpeechToText SDK). [O03]
- 공개 API 존재 ≠ 해당 차량 지원 ≠ 앱 권한 승인 ≠ 에뮬레이터 수신 성공. 제품 기술 스택에 추가하기 전에 **문서 버전·권한·SDK 실컴파일·에뮬레이터 신호**를 검증. [P06]
- [문서 간 차이] P06의 Fused Location 호환성 표에는 '미지원'이라 표시되어 있고 O03은 공모전 출품작의 SDK 사용 자체를 허용한다. **사용 가능 목록과 실제 기능 가용성은 다르다**. 필요하다면 SDK 버전/에뮬레이터에서 별도 검증.

## 5. 주행 안전·UX 규제 [플랫폼 적용]

- 주행 제한은 `CarUxRestrictionsManager`에서 **실제 전달된 active restrictions**를 기준으로 처리. 지역·속도만으로 허용 여부 자체 판정 금지. [P03]
- **DO 미선언 앱**: 기본 규정 표에서 Parking만 사용 가능, Idling/Driving은 종료·제한. 현재 앱은 DO 미선언. [P02]
- 주행 중 제한 대상 콘텐츠의 영상·애니메이션·긴 텍스트·복잡 입력을 비활성화. 특히 **영상 매체의 영상·음성 출력** 제한을 일반 TTS 전반의 자동 허용/금지로 자의적으로 확장하지 않음. [P01]
- **고위험 미확인:** 앱 화면이 시스템에 의해 가려지거나 종료될 때 **백그라운드 차량 신호 구독·TTS·AudioFocus의 지속 가능성과 정책상 허용 범위는 미검증**. 단지 “영상 금지, 오디오 포커스 준수”라는 문장만으로 주행 음성을 허용 판정하면 안 됨. [P01][P02][P03][P09]
- 주차 전용 학습·복기는 팀 정책상 **기어 P + 주차 브레이크 ON + UX 규제 해제**가 신선·유효하게 확인된 경우만 연다. 이는 공식 지역별 해제 조건보다 보수적인 **[팀 자체]** 정책. 속도 0만으로 판정 금지.

## 6. 지역·카테고리별 기능 제한 [플랫폼 적용]

- 북미/유럽/기타의 키보드·검색·미디어 설정·동영상·QR 등 제한 표가 다름. 표의 검색은 웹 검색이 아니라 차량 시스템 통합 검색. 각 지역/앱 카테고리에 해당하는 제한만 적용. [P02]
- Pleos는 지역별 주행 상태 및 제한값을 공급하므로 앱은 **수신한 CarUxRestrictions**를 반영. 지역 코드를 근거로 제한 신호를 앱이 임의 완화 금지. [P03]
- 카테고리는 실제 주기능과 일치시켜야 함. 단지 TTS를 쓴다고 Music/Audio, NaviHelper를 쓴다고 자체 Navigation 앱으로 분류해 허용 규칙을 우회하지 않음. [플랫폼 적용/출시 단계, P02]
- **[미확인] 치일까봐 앱 카테고리 및 DO 등록 가능 여부**, 주행 중 서비스 유지 조건을 실제 에뮬레이터와 공식 분류 문서로 확인.

## 7. 화면 모델·생명주기·접근성 [플랫폼 적용]

- **Driving view는 시스템 영역**: 임의로 가리거나 종료하지 않음; 다중 앱/가로·세로/창 크기 전환 대응. [Pleos 앱 화면 가이드; P12]
- `onStop`/복귀, 프로세스 재생성, 주차→주행 전환에서 세션·기록의 무결성을 유지. 백그라운드 불필요 애니메이션·폴링을 제거. [플랫폼 적용]
- 큰 터치 영역·적절한 대비·최소 입력·짧은 문구를 사용. 공식 주행 안전 가이드는 **버튼 최소 12.5×12.5 mm, 버튼 사이 4 mm**를 제시. [P01]
- 사용자가 수집한 디자인 노트의 텍스트 높이·대비(4.5:1)·문장 길이 등은 **해당 언어·UI 가이드 적용 범위를 확인**한 뒤 사용; 한국어로 임의 환산하지 않음. [미확인]

## 8. 차량 하드웨어·API·권한 [플랫폼 적용]

- **차량 센서·물리 제어에 직접 접근 금지**, 승인된 Pleos/AAOS 플랫폼 API 경유. 같은 기능의 표준 AAOS API가 있으면 우선 사용을 권장. [P04][P05]
- SDK 도메인별 `checkXXXCapability`(또는 문서상 정식 명칭)로 지원 여부, 권한, area, 상태 확인. 지원 불가·권한 거부·STALE·UNKNOWN은 관측/미션 성공으로 둔갑시키지 않음. [P06]
- P06의 AAOS 가용성: `GEAR_SELECTION`, `PARKING_BRAKE_ON`, `PERF_VEHICLE_SPEED`는 'O'; `PERF_STEERING_ANGLE`, `SEAT_OCCUPANCY`, `INFO_EXTERIOR_DIMENSIONS`, `PERF_ODOMETER`는 'X'. **AAOS X = Pleos SDK 동등 기능까지 부재한다는 뜻은 아님**. [P06]
- [팀 자체] 치일까봐는 **조회 전용**. 차량 가속·제동·조향·ADAS 설정을 제어하는 API나 `CONTROL_CAR_*` 권한을 추가하지 않음. SDK의 제어 API 자체가 일률적으로 불법이라는 의미는 아님.
- 모바일 전화·SMS·센서·주소록 등 일반 모바일 API는 차량에서 미지원일 수 있음. 필요 시 Capability 확인·Optional 설계·오프라인 대비. [P04]

## 9. 지도·NaviHelper·Navigation 카테고리 [플랫폼 적용 / 출시 단계]

- NaviHelper는 기본 내비와 목적지/경유지/경로 요청·리스너 이벤트를 연계하는 SDK. SDK 호출 성공, 경로 검색 응답, 실제 안내 시작은 **서로 다른 검증 단계**. [O03][P13]
- 자체 Navigation 앱의 활성 경로 관리·전환·중복 음성·주행 UI 정책을 **NaviHelper 경로 전달 앱에 기계적으로 일괄 적용하지 않는다**. 실제 카테고리와 사용 범위 확인. [플랫폼 적용/출시 단계]
- 위치·주행 경로를 불필요하게 저장하지 않음. 경유지를 입력했다는 사실만으로 특정 경관 도로를 반드시 경유한다고 단정하지 않음. [팀 자체]
- [미확인] `NAVI_ROUTE_SEARCH` 등 필요한 권한과 에뮬레이터 내 내비 기본 앱·경로 응답 확인.

## 10. 음성·미디어·오디오 포커스 [플랫폼 적용]

- 모든 음성/오디오 재생은 적합한 `AudioAttributes` + `AudioFocusRequest`로 사전 요청, 포커스 획득·변화·상실 이벤트를 처리. 다중 포커스 보유자 조건에서 보수적인 상호작용 적용. [P09]
- `AUDIOFOCUS_GAIN_TRANSIENT_MAY_DUCK`은 **동시 재생을 기대할 때의 후보**이지만, 포커스 획득 자체가 주행 중 앱 실행 허가를 뜻하지 않음. 무조건 얻어야 한다는 가정 금지. [P09]
- 팀 정책상 통화·내비·긴급 알림에 양보하고, 발화할 수 없었던 과거 이벤트를 나중에 몰아서 재생하지 않음. 재생 사실은 리플레이 기록과 일치시킴. [팀 자체]
- **Pleos TTS ON_DEVICE**는 공개 SDK 사용 범위지만 실제 버전·한글 음색·앱 실행 환경은 검증 필요. 서버 모드/외부 사전 제작 음성의 경우 계정·라이선스·배포·네트워크·오디오 정책을 별도 확인. [O03][P09][P12]
- **외부 STT를 위한 차량 마이크 음성 전송은 공모전에서 불가**. Typecast/ElevenLabs로 미리 만든 안내 음성 파일 재생과 혼동 금지. [O03]
- Music/Audio 전용 MediaSession·탐색 UI 요구는 카테고리 해당 시 적용. 음성 코칭이 곧 음악 앱이라는 의미는 아님. [출시 단계]

## 11. 동영상·게임·브라우저 [플랫폼 적용 / 출시 단계]

- 주행 중 위험한 동영상·게임·웹 탐색과 자동 콘텐츠 전환을 유도하지 않음. 영상 재생 정지·입력 차단, 주행 제한 상태를 우선 적용. [P01]
- PIP·3D 하이라이트는 **주차 후만**; WASD와 가상 차량 조작은 **웹 Mock 전용**. [팀 자체]
- 브라우저/WebView의 CSP·파일 접근·쿠키·다운로드·디버깅 등 세부 요구는 실제로 브라우저/WebView를 내장하는 기능에 한해 별도 점검. [출시 단계]

## 12. 개인정보·프로필·동의 [플랫폼 적용 / 출시 단계]

- 데이터 목적·보유·삭제·제3자 제공을 명확히 하고 **필요한 정보만 수집**. 동의 거부를 우회하거나 뒤로 가기·무응답을 동의로 간주하지 않음. [P07][P08]
- 공유 차량이므로 프로필별 세션·기록 분리, 쉽게 삭제/전환. 로그·분석 이벤트에 사용자/차량 식별정보가 포함되면 마스킹. 행동 데이터를 마케팅에 사용하지 않음. [P07]
- [팀 자체] 초기 제품에서는 원본 음성·영상, VIN, 정밀 GPS 궤적, 외부 서버 업로드를 기능 범위에서 제외. 추후 추가 시 별도 사용자 동의·법규·보안 심사 필요.

## 13. 앱 보안 [플랫폼 적용 / 출시 단계]

- 중요한 정보·API 키·인증정보·민감한 주행 기록을 평문으로 저장하거나 Git에 커밋하지 않음. 안전한 키 관리(Keystore 등), 저장·캐시·로그·백업·외부 통신을 설계. [P08]
- 불필요한 권한·외부 공개 컴포넌트 최소화; 비공개 API·임의 VHAL/시스템 권한 우회·인증서 검증 무력화 금지. 배포 앱은 정상 서명하고 디버깅 비활성화. [P04][P08]
- HTTPS/TLS 사용, 취약한 암호화 알고리즘·라이브러리 회피, WebView 사용 시 외부 콘텐츠가 내부 데이터에 임의 접근하지 못하게 제한. [P08]
- [현 상태] Android `PracticeStore.kt`는 `SharedPreferences` JSON. **암호화 Room/Keystore가 구현됐다고 보고하면 안 됨.** 실제 민감도·저장 범위와 안전한 마이그레이션 계획을 검증.

## 14. 성능·리소스·안정성 [플랫폼 적용 / 출시 단계]

- Pleos **앱 실행 환경/성능 가이드의 권장**: **CPU 3% 이하, RAM 400MB 이하**. 자동 공모전 실격 임계값으로 오해 금지. [P10]
- 팀이 조사한 과거/다른 설계 자료에는 내비 주행 15%/비내비 5% 미만/정차 25% 등의 구간별 수치가 있음. 현재 접근 가능한 설계 페이지 P11은 세부 수치 대신 P10을 참조함. **문서 차이를 확인할 때까지 보수적인 내부 목표 3%를 사용**. [미확인]
- 앱 실행·반응·로드 지연, 크래시, 시스템 방해, 그래픽/오디오 불안정 점검. 동작 조건과 측정 방법이 없는 수치를 '통과'라고 주장하지 않음.
- 3D/PIP 구현 시 FPS뿐 아니라 CPU/RAM/배터리·가로/세로 전환 후 리소스 해제를 확인. [팀 자체]

## 15. 콘텐츠·권리 보호 [공모전 필수 / 플랫폼 적용]

- 위험 운전·과속·신호 위반 조장 금지. 정확하지 않은 자동 안전 판정·실력 점수로 운전자가 과신하게 만들지 않음. [팀 자체, P01]
- 이미지·폰트·차량 모델·음성·영상·외부 라이브러리의 사용권 및 재배포 가능성을 증빙. [O04]
- UGC/AI 생성 콘텐츠, 연령등급, 광고 및 유료 요소는 실제 도입할 때 해당 출시 심사 기준 재검토. [출시 단계]

## 16. Pleos App Market 등록·출시 심사 [출시 단계 — 공모전과 구분]

- 공식 앱마켓의 카테고리, 등록정보, 스크린샷, 아이콘 규격, 개인정보처리방침, 서버 접근·검수, 연령등급, 보안 서명과 검증은 실제 입점 시 적용. [O03]
- 팀 사전 조사에 APK 용량 및 이미지 크기 표기 차이가 있음: 공개 최신 제출 콘솔의 유효한 요구로 재확인; 공모전 제출 APK와 무관한 등록 규격을 섣불리 고정하지 않음. [미확인]
- 공모전 수상·전시는 **앱마켓 최종 입점 또는 배포 보장 아님**. [O05]

## 17. 선택 숏폼 챌린지 [공모전 선택]

- 30초~1분 세로 릴스 또는 Shorts, 필수 해시태그 `#HMG차량용앱개발공모전` 포함 총 2~3개 해시태그; 링크를 접수양식에 기재. **본상 개발 가산점과 별도**. [O02]
- 영상의 음원·화면·얼굴·브랜드 사용권 점검. 시상식에서 활용될 수 있다는 공식 고지 확인. [O04]

---

## 미해결 사항·정정 대장 (우선순위 순)

| ID | 쟁점 | 지금 판정 | 확인·후속 조치 |
|---|---|---|---|
| **X01** | DO 미선언 주행 중 음성·신호 구독 지속 | **NOT_VERIFIED / P0** | Pleos Emulator의 주행 규제 ON, onPause/onStop/프로세스, CarUx·TTS·AudioFocus 동작 로그 확보; 정책상 허용 별도 판단 |
| **X02** | `PleosVehicleSource.kt` 실신호 수신 | **NOT_VERIFIED / P0** | 공개 API/Capability/권한/실기동 로그로 증빙. TODO를 성공으로 보고하지 않음 |
| **X03** | 실제 속도·핸들·페달/트립 사용 가능 | **NOT_VERIFIED / P0** | AAOS 지원 속성/권한, Pleos SDK 대응, 단위 및 신호 상태 확인 |
| **X04** | 음성 라이선스 및 서버 모드 사용 | **NOT_VERIFIED / P1** | Typecast/ElevenLabs 이용조건·재배포권; 외부 STT 사용 금지 유지 |
| **X05** | FusedLocation 등 문서 호환성/포털 상태 차이 | **NOT_VERIFIED / 필요 시** | 공모전 공식 허용 목록과 해당 버전 실제 API/계정 지원 분리 |
| **X06** | 성능 기준 문서 차이(3% vs 구간별 5% 등) | **NOT_VERIFIED / 내부 목표 3%** | 공식 원문/측정 방식·현재 버전 확인, 실측 로그 확보 |
| **X07** | 안전벨트 재체결 강요 문구 등 구 문서 충돌 | **OPEN** | `docs/01_PRD.md`, `docs/05_MISSIONS_CONTENT.md`, `web/README.md` 및 앱 UI의 현행 표현을 별도 이슈로 정정 |
| **X08** | 구 SDK 문서의 'DO 미선언은 오디오 허용' 단정 | **OPEN / 위험** | `docs/03_PLEOS_SDK_NOTES.md` §5-0·§6을 추후 정정. **현재는 본 문서·SAFETY_RULES를 우선 적용** |
| **X09** | Private GitHub 제출 과거 잘못된 협업 계정 안내 | **정정됨(본 문서)** | O01·O02: Collaborator 초대 불필요; 공개 전환/압축 제출 규정 적용 |
| **X10** | 앱 카테고리·NaviHelper 권한·실행 환경 | **NOT_VERIFIED / P1** | 공식 API·에뮬레이터 확인 후 적용 |

## 유지관리 규칙

1. 기능마다 영향을 받는 규정 ID·URL과 확인 날짜, 적용 범위([공모전 필수]/[플랫폼 적용]/[출시 단계]/[팀 자체])를 남긴다.
2. 공식 링크가 변경되면 본문을 추정 수정하지 않고 **변경 전후 문구와 확인 일자**를 기록한다.
3. 코드 검증 증거는 [VERIFICATION_LOG.md](VERIFICATION_LOG.md), 구현 상태는 [IMPLEMENTATION_STATUS.md](IMPLEMENTATION_STATUS.md). **이 대장의 문서 확인은 실제 작동 PASS가 아니다.**
4. 담당자·기능 배정은 [TEAM_GUIDE.md](TEAM_GUIDE.md)에 따라 별도 합의. 규정 문서가 자동으로 업무를 배정하지 않는다.
