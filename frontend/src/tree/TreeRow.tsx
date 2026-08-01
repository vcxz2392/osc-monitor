import { memo } from 'react'
import type { Resource } from '../api'
import { DEPTH } from '../api'
import { rowRenders } from '../perf'
import { aggregateLabel, metricsLabel, typeLabel, updatedAt } from './format'

interface Props {
  resource: Resource
  expanded: boolean
  loading: boolean
  expandable: boolean
  selected: boolean
  onToggle: (id: number) => void
  onSelect: (id: number) => void
}

/**
 * 행 컴포넌트.
 *
 * <p>리소스 객체 자체를 prop 으로 받고 memo 를 씌운다. 델타를 병합할 때 바뀌지 않은 리소스는
 * 객체 참조가 유지되므로 이 행은 다시 그려지지 않는다.
 *
 * <p>펼침 상태는 Set 이 아니라 <b>불린</b>으로 내려온다. Set 을 그대로 넘기면 매번 새 참조라
 * 모든 행이 다시 그려진다.
 */
export const TreeRow = memo(function TreeRow(props: Props) {
  const { resource, expanded, loading, expandable, selected, onToggle, onSelect } = props
  rowRenders.count++
  const arrow = loading ? '⋯' : expanded ? '▾' : '▸'
  return (
    <div
      className={`row${selected ? ' row-selected' : ''}`}
      style={{ paddingLeft: 8 + DEPTH[resource.type] * 16 }}
      onClick={() => onSelect(resource.id)}
      data-expandable={expandable || undefined}
    >
      <span
        className="row-arrow"
        data-collapsed={expandable && !expanded && !loading ? 'true' : undefined}
        onClick={(event) => {
          // 펼치기와 선택은 다른 동작이다. 화살표를 눌렀을 때 상세까지 열리면 의도가 섞인다.
          event.stopPropagation()
          if (expandable) onToggle(resource.id)
        }}
      >
        {expandable ? arrow : ''}
      </span>
      <span className={`badge badge-${resource.type.toLowerCase()}`}>{typeLabel(resource.type)}</span>
      <span className="row-name">{resource.name}</span>
      <span className={`status status-${resource.status.toLowerCase()}`}>{resource.status}</span>
      <span className="row-aggregate">{aggregateLabel(resource)}</span>
      <span className="row-metrics">{metricsLabel(resource)}</span>
      <span className="row-updated">{updatedAt(resource.updatedAt)}</span>
    </div>
  )
})
