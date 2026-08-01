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
