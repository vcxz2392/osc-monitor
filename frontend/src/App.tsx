import { useEffect } from 'react'
import { markPainted } from './perf'
import { TreeRow } from './tree/TreeRow'
import { flatten, useTree } from './tree/useTree'

export function App() {
  const { state, error, toggle } = useTree()
  const rows = flatten(state)

  useEffect(() => {
    if (rows.length > 0) markPainted('firstInteractive')
  }, [rows.length])

  return (
    <div className="app">
      <header className="app-header">
        <h1>Infrastructure Monitoring</h1>
        <span className="app-rev">rev {state.rev}</span>
        <span className="app-count">{rows.length.toLocaleString()} 행</span>
      </header>
      <main className="app-body">
        <div className="tree">
          {error && <div className="notice notice-error">{error}</div>}
          {rows.map((row) => (
            <TreeRow
              key={row.resource.id}
              resource={row.resource}
              expanded={row.expanded}
              loading={row.loading}
              expandable={row.expandable}
              onToggle={toggle}
            />
          ))}
        </div>
      </main>
    </div>
  )
}
