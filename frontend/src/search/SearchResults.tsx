import type { Resource, SearchResult } from '../api'
import { typeLabel } from '../tree/format'

interface Props {
  result: SearchResult
  onPick: (resource: Resource) => void
}

/** 조상 id 경로를 이름 경로로 바꾼다. 자기 자신은 빼고 위쪽만. */
function ancestorPath(resource: Resource, names: Record<string, string>): string {
  return resource.path
    .split('/')
    .filter(Boolean)
    .slice(0, -1)
    .map((id) => names[id] ?? id)
    .join(' / ')
}

/**
 * 결과를 드롭다운이 아니라 목록 자리에 그린다.
 *
 * <p>검색 결과는 골라서 그 위치로 이동하는 <b>트리로 들어가는 다른 입구</b>다.
 * 좁은 팝업에서는 경로가 보이지 않고, 결과가 많을 때 훑을 수도 없다.
 */
export function SearchResults({ result, onPick }: Props) {
  if (result.items.length === 0) {
    return <div className="notice">검색 결과가 없습니다.</div>
  }
  return (
    <div className="tree">
      {result.truncated && (
        <div className="notice notice-hint">
          {result.items.length}건까지 보여주는 중입니다. 검색어를 더 붙이거나 종류로 좁혀 보세요.
        </div>
      )}
      {result.items.map((resource) => (
        <div key={resource.id} className="row row-result" onClick={() => onPick(resource)}>
          <span className={`badge badge-${resource.type.toLowerCase()}`}>{typeLabel(resource.type)}</span>
          <span className="row-name">{resource.name}</span>
          <span className={`status status-${resource.status.toLowerCase()}`}>{resource.status}</span>
          <span className="row-path">{ancestorPath(resource, result.ancestorNames) || '—'}</span>
        </div>
      ))}
    </div>
  )
}
