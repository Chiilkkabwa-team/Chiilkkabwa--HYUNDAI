# 프로젝트 공용 Claude Code 스킬

이 폴더의 스킬은 레포를 받은 팀원 누구나 Claude Code에서 `/이름`으로 바로 쓸 수 있다. 외부 스킬은 출처·라이선스 파일을 함께 둔다.

| 스킬 | 하는 일 | 출처 · 라이선스 |
|---|---|---|
| `unslop` | 화면의 'AI 티'(색 번짐·과한 굵기·느낌표 등)를 찾아 다듬는다 | [heydamir/unslop](https://github.com/heydamir/unslop) · CC BY 4.0(만든 사람 표기 필수) |
| `ponytail` | 가장 작은 완전한 변경. 이미 있는 코드·플랫폼 기능을 먼저 쓰고, 부탁받지 않은 추상화·옵션을 만들지 않는다 | [DietrichGebert/ponytail](https://github.com/DietrichGebert/ponytail) `96608e0` · MIT |
| `ponytail-review` | 변경분에서 줄일 수 있는 코드를 찾는 리뷰 | 위와 같음 |
| `ponytail-audit` | 레포 전체에서 불필요한 코드·의존성 점검 | 위와 같음 |
| `ponytail-debt` | 남겨 둔 지름길(`shortcut:` 주석) 목록 | 위와 같음 |

- Ponytail은 **스킬만** 넣었다. 원본의 자동 훅(세션 시작 시 항상 켜짐·상태 표시줄)은 팀원 환경을 바꾸므로 넣지 않았다. 쓰고 싶을 때 `/ponytail`로 켠다.
- Ponytail도 **검증·데이터 손실 방지·보안·접근성은 줄이지 않는다**. 이 프로젝트에서는 [SAFETY_RULES](../../docs/SAFETY_RULES.md)의 주차 게이트·출처 라벨·읽기 전용 원칙도 같은 '줄이지 않는 것'에 들어간다.
