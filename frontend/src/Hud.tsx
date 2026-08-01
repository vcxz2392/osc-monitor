import type { DeltaStats } from './tree/useTree'

/**
 * 갱신이 부분적으로 일어났는지 화면에서 바로 보이게 한다.
 *
 * <p>"변경분만 갱신한다" 는 주장은 눈으로 확인되지 않는다. 서버가 몇 건을 내려줬고,
 * 그중 화면에 있던 것이 몇 건이며, 실제로 다시 그려진 행이 몇 개인지를 함께 보여준다.
 */
export function Hud({ stats, rev }: { stats: DeltaStats | null; rev: number }) {
  return (
    <div className="hud">
      <span>rev {(stats?.rev ?? rev).toLocaleString()}</span>
      {stats && (
        <span>
          변경 {stats.changed}건 · 반영 {stats.merged}건 → 리렌더 {stats.rerendered}행
        </span>
      )}
    </div>
  )
}
