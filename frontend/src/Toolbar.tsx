import type { ResourceType } from './api'

const TYPES: ResourceType[] = ['CLUSTER', 'NODE', 'NAMESPACE', 'POD']
const TYPE_TEXT: Record<ResourceType, string> = {
  CLUSTER: '클러스터',
  NODE: '노드',
  NAMESPACE: '네임스페이스',
  POD: '파드',
}

interface Props {
  query: string
  type: ResourceType | null
  onQuery: (value: string) => void
  onType: (value: ResourceType | null) => void
}

export function Toolbar({ query, type, onQuery, onType }: Props) {
  return (
    <div className="toolbar">
      <input
        className="search-input"
        value={query}
        placeholder="이름으로 검색 (예: api, ns-auth, node-003)"
        onChange={(event) => onQuery(event.target.value)}
      />
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
