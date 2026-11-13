# 치일까봐 필수 제품·안전 원칙 (SAFETY_RULES)

> **팀의 강행 기준 / 모든 AI·개발자 필독** · 2026-11-13 KST
>
> 법규나 공모전 요강의 전문이 아니라, [COMPLIANCE_REGISTER.md](COMPLIANCE_REGISTER.md)의 **공식 근거에 연결된 제품별 실행 규칙**이다. 충돌 시 최신 공식 공모전/Pleos 원문을 먼저 확인하고, 불명확한 사항은 임의 허용으로 간주하지 않는다.
>
> **3인 기능 분업에는 영향을 주지 않는다.** 모두가 담당 기능에 필요한 UI·Core·Data·테스트를 수정하되, 공유 계약을 먼저 합의한다.

## 안전 불변조건

| ID | 반드시 지킬 원칙 | 구현 시 체크 / 확인 증거 | 근거 |
|---|---|---|---|
| **S01** | **읽기 전용 차량 앱.** 가속·제동·조향·ADAS·차량 설정 제어 금지. `CONTROL_CAR_*` 권한, 직접 VHAL/하드웨어 접근, 비공개 API 우회 금지. | 변경 매니페스트·SDK 호출·권한 diff 확인. 차량 실제 제어 호출 0건. | 대장 §4, §8; [P04](https://document.pleos.ai/docs/pleos-only/vehicle-app-planning-guide/platform-api-architecture) |
| **S02** | **허용된 공개 SDK만.** 공모전 기간 Pleos LLM SDK·문서 비공개 SDK·Gleo Interface 승인 신청 기능 구현 금지. 차량 마이크의 외부 STT 전송 금지. | 새 SDK·권한·서비스마다 공식 링크·버전·공모전 허용·차종 지원 증거 확인. | 대장 §4; [공식 개발 범위 공지](https://appdevchallenge.auto-hmg.io/notice/c0a1a000-2026-4923-8000-000000000008) |
| **S03** | **안전 불명 시 잠금.** 정차 전용 학습/복기는 기어 P + 주차 브레이크 ON + UX 제한 해제가 유효하고 신선할 때만. 속도 0은 불충분. | UNKNOWN/DISCONNECTED/STALE/UNSUPPORTED, 백그라운드 복귀, 주행 제한 변경마다 잠금 테스트. **공식 지역 기준보다 보수적인 팀 정책**. | 대장 §5~6; [P02](https://document.pleos.ai/docs/pleos-only/vehicle-app-planning-guide/driving-restiction-policy) |
| **S04** | **주행 중 터치·학습·영상/PIP/3D 애니메이션 비활성화.** 플랫폼 CarUxRestrictions를 따르고 Driving view를 침범하지 않음. | 주행 제한 ON/OFF·화면 전환·앱 포그라운드/백그라운드 관찰, 정적 화면과 입력 차단 확인. | 대장 §5, §7, §11; [P01](https://document.pleos.ai/docs/pleos-only/vehicle-app-planning-guide/driver-distraction-guideline) |
| **S05** | **주행 중 음성·데이터 기록은 미검증 상태를 유지.** DO 미선언 앱의 Activity 중단/종료 가능성이 있음. 오디오 포커스 준수만으로 실행·허용을 단정 금지. | 앱 카테고리·DO·onPause/onStop·주행 이벤트 구독·포커스/재생·재주차 복원을 Pleos Emulator에서 검증. 미통과 시 해당 경로 비활성화/Mock 시연 한정. | 대장 X01; [P02](https://document.pleos.ai/docs/pleos-only/vehicle-app-planning-guide/driving-restiction-policy), [P03](https://document.pleos.ai/docs/pleos-only/vehicle-app-developer-guide/driving-restriction-development-guide) |
| **S06** | **최소·짧은 음성 코칭.** 유효 관측 근거와 연습 목표에만 반응; 내비·통화·안전 안내에 양보. 발화 실패를 뒤늦게 재생하지 않음. | 적절한 AudioAttributes·AudioFocusRequest, 포커스 거부/상실, 중복/간격/길이 제한, 재생 로그 테스트. 사전 제작 음성도 동일 적용. | 대장 §10; [P09](https://document.pleos.ai/docs/pleos-only/vehicle-app-developer-guide/audio-focus-development-guide/audio-focus-development) |
| **S07** | **관측한 사실만 기록·설명.** 신호 지원 여부·권한·시각·단위·출처를 표시. 속도·핸들만으로 실제 주변 차량의 위험·차선 이탈을 확정하지 않음. | 이벤트마다 근거 센서·타임스탬프·판정 가능성 확인. Mock 이벤트는 가상임을 명시; 데이터가 없으면 억지 추론 금지. | 대장 §8; [P06](https://document.pleos.ai/docs/pleos-only/vehicle-app-planning-guide/api-compatibility-availability) |
| **S08** | **미션이 안전을 해치지 않아야 함.** 이미 맨 안전벨트를 풀었다 다시 매도록 강요 금지. 페달·핸들 연습은 공식 신호/안전 조건 충족 전 실차 활성화 금지. | 기존 벨트 상태에서 완료/건너뛰기 경로, unsupported 폴백, 정차·주차 조건, 페달 위험 방지 테스트. | 최종 DOCX 제품 안전 목표 / 대장 §5·§8 / 팀 자체 |
| **S09** | **데이터·프로필 보호.** 원본 음성·영상, 정밀 GPS 궤적, VIN, 서버 업로드 등 범위 밖 수집 금지. 공유 차량의 다른 프로필 기록 비노출. | 권한·데이터 흐름·로컬 저장·삭제·백업·로그·암호화/키 관리 검증. `SharedPreferences`를 암호화 Room으로 허위 설명 금지. | 대장 §12~13; [P07](https://document.pleos.ai/docs/pleos-only/vehicle-app-planning-guide/security-privacy), [P08](https://document.pleos.ai/docs/pleos-only/vehicle-app-developer-guide/app-security-guide) |
| **S10** | **출처와 검증 등급을 분리.** 문서/웹 Mock/Android Mock/Pleos Emulator/실차를 상호 대체하지 않음. 시연에서도 가상 센서·가상 제어는 가상으로 표시. | 배지·화면·영상·README·보고서·검증 로그의 출처 일치. 빌드 성공만으로 실행/실수신 PASS 금지. | 대장 §1·§2·§8 / 팀 자체 |
| **S11** | **권리와 성능.** 외부 음성·폰트·3D·이미지·라이브러리 사용권 확인. 3D/PIP는 주차 중만; CPU/RAM·렌더링 측정 후 도입. | 라이선스 출처·배포 조건·측정 환경과 로그 보관. P10 권장 3% CPU / 400MB RAM은 내부 목표로 관리. | 대장 §3·§14~15; [P10](https://document.pleos.ai/docs/pleos-only/vehicle-app-developer-guide/app-runtime-environment-resources-guide/app-performance-platform-identify) |
| **S12** | **공식 제출·보안 사실 표시.** 실제 사용 가능한 APK와 Mock만 가능한 기능을 구분, Private 저장소 규정과 기준 SHA 준수. 비밀정보·개인정보를 Git 이력에 노출하지 않음. | 제출 관련 변경 시 공식 O01/O02 재확인, 릴리스 설치/실행 증거, git history/비밀정보 점검. | 대장 §1·§3; [공식 제출 안내](https://appdevchallenge.auto-hmg.io/guide/submission) |

## 개발 착수 전 필수 Gate (기능별로 작성)

| Gate | 반드시 확인할 사항 | 하나라도 확인 안 되면 |
|---|---|---|
| **G1 범위** | 기능 담당자·사용자 흐름·공유 인터페이스·수정 파일·완료 기준 정의 | 임의 기능/공통 계약 변경 금지 |
| **G2 근거** | 해당 S-ID, 공식 원문 URL, 적용 구분(공모전/플랫폼/출시/팀)을 목록화 | 공식 SDK·주행 제한을 추측하지 않음 |
| **G3 설계** | 권한 최소화, 상태별 안전 폴백, 데이터 출처, 개인정보 흐름, 음성/영상 제한 설계 | 위험한 실차 경로 비활성화, Mock/Stub로만 진행 |
| **G4 테스트** | 단위/빌드/Mock/Emulator/실차를 구분한 실제 결과와 커밋 SHA | 수행하지 않은 테스트는 `NOT_VERIFIED` |
| **G5 통합** | 관련 규정의 FAIL/NOT_VERIFIED 확인, 공유 계약 및 병합 충돌, 검증 로그 | 안전에 필수인 경로는 미검증 상태로 실사용 가능하게 병합하지 않음 |

### 기능별 결과 표준

- `PASS`: 어떤 공식 조항·실행 환경·시나리오에서 확인했는지 **재현 가능한 증거 있음**.
- `FAIL`: 위반 또는 실패 재현. 안전·규정 관련 FAIL은 **병합 금지**, 수정 후 재검증.
- `NOT_VERIFIED`: 자료/권한/실행 시험 부재. PASS 처리 금지. **실차나 주행 경로는 기본 차단**하며, 명확히 구분된 Mock 전용 코드·문서는 **담당자가 차단 조건·출처를 자체 확인한 뒤 `dev`에 병합 가능**하다. 다른 팀원 컨펌은 필요하지 않다.
- `NOT_APPLICABLE`: 해당 기능에 적용되지 않는 조항. **이유를 기록**(예: NaviHelper 호출만 하는 앱에 자체 내비 심사 규칙 전부를 일괄 적용하지 않음).

**AI가 할 수 없는 것:** 문서를 읽고 자체 판단만으로 공식 승인·SDK 지원·정책 적합성 인증하기. 담당자는 차단 이유와 근거를 기록·공유하고 범위 밖 구현을 멈춘다. **배정받은 기능의 `dev` 자체 병합은 가능**하지만 `main` 병합은 팀의 명시적 릴리스 승인 없이는 불가하다.

### 작업 보고 템플릿 (담당 개발자·AI 모두 동일)

~~~text
기능/브랜치/기준 SHA:
적용 규정: S##, 공식 문서 URL, 확인일, 범위
공유 인터페이스 변경: 없음 / 내용·합의 대상
구현: 파일·기능 요약
검증:
  - 단위/빌드: 실행 명령과 결과
  - Mock: 결과
  - Pleos Emulator: PASS / FAIL / NOT_VERIFIED (기기/OS/SDK 버전)
  - 실차: PASS / FAIL / NOT_VERIFIED
규정: S##별 PASS/FAIL/NOT_VERIFIED/NOT_APPLICABLE + 근거
남은 위험과 차단·폴백:
공유할 변경·위험 사항(컨펌 대기 불필요):
~~~

검증 로그는 [VERIFICATION_LOG.md](VERIFICATION_LOG.md)에 사실대로 기록하고, 구현 상태 변경은 [IMPLEMENTATION_STATUS.md](IMPLEMENTATION_STATUS.md)에 반영한다.

## 현재 우선 확인할 안전 이슈

1. **X01:** DO 미선언 앱의 주행 중 음성·이벤트 기록 허용 여부. P02/P03/P09의 실제 조합을 에뮬레이터에서 검증 전 **NOT_VERIFIED**.
2. **X02/X03:** `PleosVehicleSource` TODO, `PERF_VEHICLE_SPEED` Android 미연동, 개별 신호 권한과 값 단위 검증.
3. **X07:** 기존 벨트 탈착 안내와 안전 원칙 충돌. 워밍업 로직/UI 변경 시 즉시 개선.
4. **X08:** `docs/03_PLEOS_SDK_NOTES.md` §5-0·§6은 주행 중 오디오 허용을 확정적으로 표현. **최신 검증 전에는 그 문장을 구현 허용 근거로 사용하지 않는다.**
5. **제출:** 1차 제출 마감(2026-11-15 23:59 KST)과 Private 저장소 선택 제출 규정은 [COMPLIANCE_REGISTER.md](COMPLIANCE_REGISTER.md) §1 참고.

## 경계

- 이 문서는 **공모전·플랫폼 규정 자체를 대신하지 않는다**. 원문 개정 시 즉시 재검증.
- 기존 `AGENTS.md` · `CLAUDE.md` · `TEAM_GUIDE.md`의 **기능별 1인 책임, 브랜치 독립, 개발자 자신의 `dev` 병합, `main` 별도 릴리스 승인** 규칙을 따른다.
- 팀원별 기능 배정은 별도 결정한다. 본 안전 문서가 개발 책임자를 임의 지정하지 않는다.
