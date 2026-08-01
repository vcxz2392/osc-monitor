import { useEffect, useState } from 'react'
import type { Resource, ResourceType } from './api'
import { markPainted, measureAfterPaint } from './perf'
import { SearchResults } from './search/SearchResults'
import { useSearch } from './search/useSearch'
import { Toolbar } from './Toolbar'
import { DetailPanel } from './tree/DetailPanel'
import { TreeList } from './tree/TreeList'
import { flatten, useTree } from './tree/useTree'

export function App() {
  const { state, error, toggle, select, clearSelection, reveal, clearReveal } = useTree()
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
      <Toolbar query={query} type={type} onQuery={setQuery} onType={setType} />
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
