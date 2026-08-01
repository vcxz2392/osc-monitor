/**
 * 성능 목표를 재기 위한 계측점.
 *
 * <p>측정 스크립트가 붙잡을 신호를 화면에 남긴다. 페인트 이후에 표시해야 실제와 맞는다 —
 * useLayoutEffect 나 effect 안에서 바로 찍으면 아직 그려지지 않은 시점이다.
 */
export function markPainted(name: string): void {
  requestAnimationFrame(() => {
    setTimeout(() => {
      performance.mark(name)
      document.body.dataset[name] = 'true'
    }, 0)
  })
}

/** 상호작용 측정 시작. 디바운스를 빼려면 "실제로 일이 시작되는 지점"에서 찍어야 한다. */
export function markStart(name: string): void {
  performance.mark(`${name}:start`)
}

/** 그려진 다음 페인트에서 끝낸다. 시작 마크가 없으면 아무 일도 하지 않는다. */
export function measureAfterPaint(name: string): void {
  if (performance.getEntriesByName(`${name}:start`).length === 0) return
  requestAnimationFrame(() => {
    setTimeout(() => {
      performance.mark(`${name}:end`)
      performance.measure(name, `${name}:start`, `${name}:end`)
      performance.clearMarks(`${name}:start`)
    }, 0)
  })
}
