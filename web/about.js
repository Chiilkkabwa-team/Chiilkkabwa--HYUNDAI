// 치일까봐 — 서비스 소개(About Us, C05 · 11/12 회의)
// 처음 보는 사람이 '왜 만들었고 무엇을 해 주는지'를 1분 안에 알게. 피치덱처럼 짧게.
// S10·S12: 실제로 된 것과 모의인 것을 표로 나눠 적는다. 확인 안 된 수치는 쓰지 않는다.
// 팀원 실명·학교는 넣지 않는다(공모전 블라인드).

export const STATUS = [
  // [기능, 웹 체험판, Android 앱, 실제 차량·에뮬레이터]
  ['주차 확인 후 조작 연습(벨트·브레이크·깜빡이)', '모의 신호로 동작', '코드 있음(11/8 빌드 기록)', '아직 확인 전'],
  ['주행 중 화면 잠금', '모의 신호로 동작', '코드 있음', '아직 확인 전'],
  ['주행 중 짧은 음성 길잡이', '브라우저 음성으로 동작', '아직 없음', '주행 중 허용 여부 확인 전'],
  ['주차 후 시간표·장면 재현', '모의 기록으로 동작', '아직 없음', '아직 확인 전'],
  ['가상 도로 평가·강습', '가상 시뮬레이션', '해당 없음(체험판 전용)', '해당 없음'],
];

export function aboutHtml() {
  return `<div class="about">
    <span class="tag soft">서비스 소개</span>
    <h2>치일까봐 — 운전석이 아직 낯선 사람을 위한 차 안 연습</h2>
    <p class="sub">면허는 있지만 운전이 두려운 사람이, 주차된 차 안에서 조작을 확인받고, 달릴 땐 화면 대신 짧은 목소리만 듣고, 다시 주차하면 오늘을 돌아보는 앱이에요.</p>
    <section class="about-steps">
      <div><b>1 · 주차 중 5분</b><p>벨트·브레이크·깜빡이를 해 보면 차가 신호로 “했다”고 확인해 줘요.</p></div>
      <div><b>2 · 주행 중 0터치</b><p>화면은 잠기고, 꼭 필요할 때만 한마디. 내비·통화에는 바로 양보해요.</p></div>
      <div><b>3 · 다시 주차 3분</b><p>속도 시간표와 그 순간 장면을 다시 보고, 다음 목표를 골라요.</p></div>
    </section>
    <section class="about-rules"><h3>지키는 약속</h3>
      <ul><li><b>확인은 차가, 판단은 사람이.</b> 운전 실력을 판정하지 않아요.</li>
        <li>차를 <b>읽기만</b> 해요. 브레이크·핸들 같은 제어는 하지 않아요.</li>
        <li>모르면 잠가요. 기어 P + 주차 브레이크 + 주행 제한 없음일 때만 연습을 열어요.</li>
        <li>기록은 기기 안에만. 서버·계정·위치 수집 없음.</li></ul></section>
    <section><h3>지금 어디까지 됐나</h3>
      <div class="about-table" role="table" aria-label="구현 상태">
        <div role="row" class="th"><span role="columnheader">기능</span><span role="columnheader">웹 체험판</span><span role="columnheader">Android 앱</span><span role="columnheader">실제 차·에뮬레이터</span></div>
        ${STATUS.map((r) => `<div role="row">${r.map((c, i) => `<span role="${i ? 'cell' : 'rowheader'}">${c}</span>`).join('')}</div>`).join('')}
      </div>
      <p class="caption">이 웹 체험판의 신호는 전부 모의예요. 실제 차·에뮬레이터에서 확인한 것은 확인한 날짜와 함께 따로 적어요.</p></section>
  </div>`;
}
