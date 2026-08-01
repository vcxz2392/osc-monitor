import { memo } from 'react'
import type { Resource } from '../api'
import { DEPTH } from '../api'
import { aggregateLabel, metricsLabel, typeLabel, updatedAt } from './format'

interface Props {
  resource: Resource
}

/**
 * 행 컴포넌트.
 *
 * <p>prop 으로 리소스 객체 자체를 받고 memo 를 씌운다. 델타를 병합할 때 바뀌지 않은 리소스는
 * 객체 참조가 유지되므로 이 행은 다시 그려지지 않는다. "변경된 부분만 갱신"의 실제 구현이다.
 */
export const TreeRow = memo(function TreeRow({ resource }: Props) {
  return (
    <div className="row" style={{ paddingLeft: 8 + DEPTH[resource.type] * 16 }}>
      <span className={`badge badge-${resource.type.toLowerCase()}`}>{typeLabel(resource.type)}</span>
      <span className="row-name">{resource.name}</span>
      <span className={`status status-${resource.status.toLowerCase()}`}>{resource.status}</span>
      <span className="row-aggregate">{aggregateLabel(resource)}</span>
      <span className="row-metrics">{metricsLabel(resource)}</span>
      <span className="row-updated">{updatedAt(resource.updatedAt)}</span>
    </div>
  )
})
