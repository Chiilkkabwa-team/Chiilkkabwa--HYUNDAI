// 웹 2D Mock 콘솔 입력만 계산한다. 차량 신호/물리/이벤트 판정은 변경하지 않는다.
const INPUTS = {
  KeyW: ['accel', 1], ArrowUp: ['accel', 1], KeyS: ['brake', 1], ArrowDown: ['brake', 1],
  KeyA: ['steer', -1], ArrowLeft: ['steer', -1], KeyD: ['steer', 1], ArrowRight: ['steer', 1],
};
export const CONTROL_RESPONSE = Object.freeze({ accel: 60, brake: 70, steer: 180, accelRate: 100, brakeRate: 160, steerRate: 1200, returnRate: 1800 });
export class KeyboardDrive {
  constructor() { this.held = new Set(); this.suppressed = new Set(); this.owned = new Map(); }
  key(code, down, initial = 0) {
    const input = INPUTS[code];
    if (!input) return null;
    const [channel] = input;
    if (down) {
      if (this.held.has(code)) return null;
      this.held.add(code);
      if (!this.owned.has(channel)) this.owned.set(channel, Number.isFinite(initial) ? initial : 0);
    } else { this.held.delete(code); this.suppressed.delete(code); }
    return channel;
  }
  channel(code) { return INPUTS[code]?.[0] || null; }
  has(code) { return this.held.has(code); }
  takeover(channel) {
    this.owned.delete(channel);
    for (const code of this.held) if (INPUTS[code][0] === channel) this.suppressed.add(code);
  }
  releaseAll() {
    const out = Object.fromEntries([...this.owned.keys()].map(key => [key, 0]));
    this.owned.clear(); this.held.clear(); this.suppressed.clear();
    return out;
  }
  sample(dt) {
    dt = Number.isFinite(dt) ? Math.max(0, Math.min(0.06, dt)) : 0;
    const down = (channel, dir = 1) => [...this.held].some(code => !this.suppressed.has(code) && INPUTS[code][0] === channel && INPUTS[code][1] === dir);
    const out = {};
    for (const [channel, value] of this.owned) {
      const active = channel === 'steer' ? down(channel, -1) || down(channel, 1) : down(channel);
      const direction = channel === 'steer' ? Number(down(channel, 1)) - Number(down(channel, -1)) : 1;
      const target = !active || (channel === 'accel' && down('brake')) ? 0 : direction * CONTROL_RESPONSE[channel];
      const rate = channel === 'steer' ? (active ? CONTROL_RESPONSE.steerRate : CONTROL_RESPONSE.returnRate) : CONTROL_RESPONSE[channel + 'Rate'];
      const next = value + Math.sign(target - value) * Math.min(Math.abs(target - value), rate * dt);
      out[channel] = next;
      if (!active && next === 0) this.owned.delete(channel);
      else this.owned.set(channel, next);
    }
    return out;
  }
}

export function gearPresentation(gear, parkingBrake) {
  if (!['P', 'R', 'N', 'D'].includes(gear)) return '가상 기어 · 신호 확인 중';
  const meaning = { P: parkingBrake === true ? '주차 선택 · 주차 브레이크 ON' : '주차 선택 · 주차 브레이크 확인', R: '후진 선택', N: '중립 선택', D: '주행 선택' };
  return `${gear} · ${meaning[gear]} (가상)`;
}

export function stopRequestState(requested, speed, wasRequested = false) {
  if (!Number.isFinite(speed) || speed < 0) return { key: 'UNKNOWN', text: '가상 속도 확인 중', requested };
  if (requested) return speed > 0.05
    ? { key: 'DECELERATING', text: '정지 요청 중 · 가상 차량 감속 중', requested }
    : { key: 'STOPPED', text: '가상 정지 확인 · 요청을 해제하면 다시 출발할 수 있어요', requested };
  return wasRequested
    ? { key: 'RELEASED', text: '정지 요청 해제 · 기어와 페달을 확인해 다시 가속할 수 있어요 (가상)', requested }
    : { key: 'AVAILABLE', text: '정지 요청 없음 · 가속 가능 (가상)', requested };
}
