import { useEffect, useState } from 'react'
import type { Resource, ResourceStatus, ResourceType } from './api'
import { markPainted, measureAfterPaint } from './perf'
import { SearchResults } from './search/SearchResults'
import { useSearch } from './search/useSearch'
import { Toolbar } from './Toolbar'
import { DetailPanel } from './tree/DetailPanel'
import { TreeList } from './tree/TreeList'
import { flatten, useTree } from './tree/useTree'

const STATUS_TEXT: Record<ResourceStatus, string> = { HEALTHY: '정상', WARNING: '경고', ERROR: '오류' }

export function App() {
  const { state, error, toggle, select, clearSelection, reveal, clearReveal, applyFilter } = useTree()
  const [query, setQuery] = useState('')
  const [type, setType] = useState<ResourceType | null>(null)
  const { result: searchResult, error: searchError } = useSearch(query, type)
  const searching = query.trim() !== '' && searchResult !== null
  const rows = flatten(state)
  const selected = state.selectedId === null ? null : state.nodes.get(state.selectedId) ?? null

  useEffect(() => {
    if (rows.length > 0) markPainted('firstInteractive')
  }, [rows.length])

  useEffect(() => {
    if (searchResult) measureAfterPaint('search')
  }, [searchResult])

  useEffect(() => {
    measureAfterPaint('filter')
  }, [state.status, state.rootIds])

  const pick = (resource: Resource) => {
    setQuery('')
    setType(null)
    void reveal(resource)
  }

  return (
    <div className="app">
      <header className="app-header">
        <h1>Infrastructure Monitoring</h1>
        <span className="app-rev">rev {state.rev}</span>
        <span className="app-count">{rows.length.toLocaleString()} 행</span>
      </header>
      <Toolbar
        query={query}
        type={type}
        status={state.status}
        onQuery={setQuery}
        onType={setType}
        onStatus={(next) => void applyFilter(next)}
      />
      {state.status && (
        <div className="notice notice-hint">
          {STATUS_TEXT[state.status]} 상태인 파드만 표시 중입니다. 상위 계층은 해당 상태를 하위에 가진 것만 경로로 남습니다.
        </div>
      )}
      <main className="app-body">
        {(error || searchError) && <div className="notice notice-error">{error ?? searchError}</div>}
        {searching ? (
          <SearchResults result={searchResult!} onPick={pick} />
        ) : (
          <TreeList
            rows={rows}
            selectedId={state.selectedId}
            revealId={state.revealId}
            onRevealed={clearReveal}
            onToggle={toggle}
            onSelect={select}
          />
        )}
        {selected && <DetailPanel resource={selected} onClose={clearSelection} />}
      </main>
    </div>
  )
}
