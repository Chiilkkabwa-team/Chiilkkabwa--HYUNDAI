// 치일까봐 웹 모의 차량 콘솔 — 페달 그림과 슬라이더 입력 충돌 방지.
// 화면의 모의 센서에만 영향을 주며 실제 차량을 제어하지 않는다.

/** 누르고 있으면 upMs에 걸쳐 0→100, 놓으면 downMs에 걸쳐 0으로 돌아온다.
 * 반환 함수는 수동 슬라이더 조작 시 활성 애니메이션을 중지한다.
 */
export function holdPedal(el, get, set, upMs, downMs, {
  requestFrame = (cb) => requestAnimationFrame(cb),
  cancelFrame = (id) => cancelAnimationFrame(id),
  now = () => performance.now(),
} = {}) {
  let raf = null;
  const stop = () => {
    if (raf !== null) { cancelFrame(raf); raf = null; }
  };
  el.addEventListener('pointerdown', (e) => {
    e.preventDefault();
    stop();
    el.classList.add('down');
    el.setPointerCapture?.(e.pointerId);
    let last = now();
    const up = (t) => {
      const v = get() + ((t - last) / upMs) * 100;
      last = t;
      set(Math.min(100, v));
      if (v < 100) raf = requestFrame(up);
      else raf = null;
    };
    raf = requestFrame(up);
  });
  const release = () => {
    if (!el.classList.contains('down')) return;
    el.classList.remove('down');
    stop();
    const t0 = now(), v0 = get();
    const down = (t) => {
      const k = Math.min(1, (t - t0) / downMs);
      set(v0 * (1 - k));
      if (k < 1) raf = requestFrame(down);
      else raf = null;
    };
    raf = requestFrame(down);
  };
  ['pointerup', 'pointercancel', 'lostpointercapture'].forEach((ev) => el.addEventListener(ev, release));
  // 슬라이더로 옮겨 조작하는 동안 페달 그림이 값을 다시 덮어쓰지 못하게 한다.
  return () => {
    stop();
    el.classList.remove('down');
  };
}

/** 수동 브레이크 슬라이더의 입력값이 페달 애니메이션보다 우선한다. */
export function bindBrakeSlider(input, cancelBrakeHold, setBrake, tick) {
  input.oninput = (e) => {
    cancelBrakeHold();
    setBrake(+e.target.value);
    tick();
  };
}
