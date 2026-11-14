// 치일까봐 웹 모의 차량 콘솔 — 페달 그림과 슬라이더 입력 충돌 방지.
// 화면의 모의 센서에만 영향을 주며 실제 차량을 제어하지 않는다.

/** 누르고 있으면 upMs에 걸쳐 0→100, 놓으면 downMs에 걸쳐 0으로 돌아온다.
 * 반환 함수는 수동 슬라이더 조작 시 활성 애니메이션을 중지한다.
 */
export function holdPedal(el, get, set, upMs, downMs, {
  requestFrame = (cb) => requestAnimationFrame(cb),
  cancelFrame = (id) => cancelAnimationFrame(id),
  now = () => performance.now(),
  onTakeover = () => {},
} = {}) {
  let raf = null, pointer = null, generation = 0;
  const stop = () => {
    generation++;
    if (raf !== null) { cancelFrame(raf); raf = null; }
  };
  const cancel = () => {
    const active = pointer !== null || raf !== null;
    const id = pointer; pointer = null;
    stop(); el.classList.remove('down');
    if (typeof id === 'number' && el.hasPointerCapture?.(id)) el.releasePointerCapture?.(id);
    onTakeover();
    return active;
  };
  el.addEventListener('pointerdown', (e) => {
    if (pointer !== null || (e.button != null && e.button !== 0) || e.isPrimary === false) return;
    e.preventDefault();
    cancel(); pointer = e.pointerId;
    const ticket = generation;
    el.classList.add('down');
    el.setPointerCapture?.(e.pointerId);
    let last = now();
    const up = (t) => {
      if (ticket !== generation) return;
      const v = get() + (Math.max(0, t - last) / upMs) * 100;
      last = t;
      set(Math.min(100, v));
      if (v < 100) raf = requestFrame(up);
      else raf = null;
    };
    raf = requestFrame(up);
  });
  const release = (e) => {
    if (pointer === null || e.pointerId !== pointer) return;
    pointer = null;
    el.classList.remove('down');
    stop();
    const ticket = generation;
    const t0 = now(), v0 = get();
    const down = (t) => {
      if (ticket !== generation) return;
      const k = Math.max(0, Math.min(1, (t - t0) / downMs));
      set(v0 * (1 - k));
      if (k < 1) raf = requestFrame(down);
      else raf = null;
    };
    raf = requestFrame(down);
  };
  el.addEventListener('pointerup', release);
  const abort = (e) => {
    if (pointer === null || e.pointerId !== pointer) return;
    cancel(); set(0);
  };
  ['pointercancel', 'lostpointercapture'].forEach((ev) => el.addEventListener(ev, abort));
  // 슬라이더로 옮겨 조작하는 동안 페달 그림이 값을 다시 덮어쓰지 못하게 한다.
  return cancel;
}

/** 수동 브레이크 슬라이더의 입력값이 페달 애니메이션보다 우선한다. */
export function bindBrakeSlider(input, cancelBrakeHold, setBrake, tick) {
  input.oninput = (e) => {
    cancelBrakeHold();
    setBrake(+e.target.value);
    tick();
  };
}
