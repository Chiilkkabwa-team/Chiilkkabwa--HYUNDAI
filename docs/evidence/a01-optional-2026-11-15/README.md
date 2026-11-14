# A01 선택 센서 오프라인 증거

- sdk-public-signatures.txt: 기존 Vehicle2.3.2 AAR의 classes.jar를 javap로 읽은 관련 public 선언만 추출. 벤더 바이너리/전체 소스는 포함하지 않음.
- verification.json: AAR/sources hash, 실제 XML 집계/신규20개 이름, RED→GREEN, 단위/폴백, 기본복원 및 opt-in 컴파일 결과. AVD 인계 전이므로 접근/설치/실행/종료0, 실수신 NOT_VERIFIED.
- 단위 근거: sources api/Odometer.kt, listener/DistanceDrivenListener.kt, listener/DrivingTimeListener.kt. 전륜각 근거: api/SteeringWheel.kt와 listener/SteeringWheelAngleListener.kt. 매핑은 코드의 PleosOptionalSignalDecode.kt 참조.
- 상세 [보고서](../../A01_OPTIONAL_SIGNALS.md). 실험 로그/SDK추출물/APK는 git 제외 build/a01-optional-sdk에만 존재한다.
