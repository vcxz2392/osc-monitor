import { useEffect } from 'react'
import { markPainted } from './perf'
import { TreeList } from './tree/TreeList'
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
        {error && <div className="notice notice-error">{error}</div>}
        <TreeList rows={rows} onToggle={toggle} />
      </main>
    </div>
  )
}
