import { useEffect } from 'react'
import { markPainted } from './perf'
import { TreeRow } from './tree/TreeRow'
import { useTree, visibleRows } from './tree/useTree'

export function App() {
  const { state, error } = useTree()
  const rows = visibleRows(state)

  useEffect(() => {
    if (rows.length > 0) markPainted('firstInteractive')
  }, [rows.length])

  return (
    <div className="app">
      <header className="app-header">
        <h1>Infrastructure Monitoring</h1>
        <span className="app-rev">rev {state.rev}</span>
      </header>
      <main className="app-body">
        <div className="tree">
          {error && <div className="notice notice-error">{error}</div>}
          {rows.map((resource) => (
            <TreeRow key={resource.id} resource={resource} />
          ))}
        </div>
      </main>
    </div>
  )
}
