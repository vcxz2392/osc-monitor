import type { ResourceStatus, ResourceType } from './api'

const TYPES: ResourceType[] = ['CLUSTER', 'NODE', 'NAMESPACE', 'POD']
const TYPE_TEXT: Record<ResourceType, string> = {
  CLUSTER: '클러스터',
  NODE: '노드',
  NAMESPACE: '네임스페이스',
  POD: '파드',
}

const STATUSES: ResourceStatus[] = ['HEALTHY', 'WARNING', 'ERROR']
const STATUS_TEXT: Record<ResourceStatus, string> = { HEALTHY: '정상', WARNING: '경고', ERROR: '오류' }

interface Props {
  query: string
  type: ResourceType | null
  status: ResourceStatus | null
  onQuery: (value: string) => void
  onType: (value: ResourceType | null) => void
  onStatus: (value: ResourceStatus | null) => void
}

export function Toolbar({ query, type, status, onQuery, onType, onStatus }: Props) {
  return (
    <div className="toolbar">
      <input
        className="search-input"
        value={query}
        placeholder="이름으로 검색 (예: api, ns-auth, node-003)"
        onChange={(event) => onQuery(event.target.value)}
      />
      <div className="filter">
        {/* 무엇의 상태인지 밝힌다. "오류" 라고만 쓰면 어느 계층의 오류인지 알 수 없다. */}
        <span className="filter-label">파드 상태</span>
        {STATUSES.map((candidate) => (
          <button
            key={candidate}
            className={`chip chip-${candidate.toLowerCase()}${status === candidate ? ' chip-on' : ''}`}
            onClick={() => onStatus(status === candidate ? null : candidate)}
          >
            {STATUS_TEXT[candidate]}
          </button>
        ))}
      </div>
      {query.trim() !== '' && (
        <div className="chips">
          {TYPES.map((candidate) => (
            <button
              key={candidate}
              className={`chip${type === candidate ? ' chip-on' : ''}`}
              onClick={() => onType(type === candidate ? null : candidate)}
            >
              {TYPE_TEXT[candidate]}
            </button>
          ))}
        </div>
      )}
    </div>
  )
}
