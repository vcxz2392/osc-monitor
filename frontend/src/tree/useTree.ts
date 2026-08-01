import { useCallback, useEffect, useRef, useState } from 'react'
import { api, type Resource } from '../api'

interface ChildList {
  ids: number[]
  cursor: string | null
}

export interface TreeState {
  /** 정규화 저장소. 중첩 객체를 만들지 않아야 부분 갱신이 가능하다. */
  nodes: Map<number, Resource>
  /** 부모 id → 이미 받아 둔 자식 목록 */
  childrenOf: Map<number, ChildList>
  rootIds: number[]
  expanded: Set<number>
  loading: Set<number>
  rev: number
}

export interface Row {
  resource: Resource
  expanded: boolean
  loading: boolean
  expandable: boolean
}

const EMPTY: TreeState = {
  nodes: new Map(),
  childrenOf: new Map(),
  rootIds: [],
  expanded: new Set(),
  loading: new Set(),
  rev: 0,
}

const PAGE_SIZE = 200

export function useTree() {
  const [state, setState] = useState<TreeState>(EMPTY)
  const [error, setError] = useState<string | null>(null)

  // 콜백이 매 렌더 새로 만들어지면 행의 memo 가 무력화된다.
  // 상태를 의존성에 넣는 대신 ref 를 거울로 둬서 콜백을 한 번만 만든다.
  const latest = useRef(state)
  latest.current = state

  useEffect(() => {
    api
      .roots()
      .then((page) =>
        setState((prev) => ({
          ...prev,
          nodes: new Map(page.items.map((item) => [item.id, item])),
          rootIds: page.items.map((item) => item.id),
          rev: page.rev,
        })),
      )
      .catch((cause: Error) => setError(cause.message))
  }, [])

  const toggle = useCallback(async (id: number) => {
    const current = latest.current
    if (current.expanded.has(id)) {
      setState((prev) => ({ ...prev, expanded: without(prev.expanded, id) }))
      return
    }
    // 이미 받아 둔 계층이면 조회 없이 편다.
    if (current.childrenOf.has(id)) {
      setState((prev) => ({ ...prev, expanded: with_(prev.expanded, id) }))
      return
    }

    setState((prev) => ({ ...prev, loading: with_(prev.loading, id) }))
    try {
      const page = await api.children(id, { size: PAGE_SIZE })
      // 데이터를 받은 뒤에 한 번에 바꾼다. 먼저 펼치고 나중에 채우면 목록이 두 번 밀린다.
      setState((prev) => {
        const nodes = new Map(prev.nodes)
        for (const item of page.items) nodes.set(item.id, item)
        const childrenOf = new Map(prev.childrenOf)
        childrenOf.set(id, { ids: page.items.map((item) => item.id), cursor: page.nextCursor })
        return {
          ...prev,
          nodes,
          childrenOf,
          expanded: with_(prev.expanded, id),
          loading: without(prev.loading, id),
        }
      })
    } catch (cause) {
      setState((prev) => ({ ...prev, loading: without(prev.loading, id) }))
      setError((cause as Error).message)
    }
  }, [])

  return { state, error, toggle }
}

/** 펼쳐진 경로만 따라 내려가며 평탄한 행 배열을 만든다. 접혀 있으면 순회 비용도 없다. */
export function flatten(state: TreeState): Row[] {
  const rows: Row[] = []
  const walk = (ids: number[]) => {
    for (const id of ids) {
      const resource = state.nodes.get(id)
      if (!resource) continue
      const expanded = state.expanded.has(id)
      rows.push({
        resource,
        expanded,
        loading: state.loading.has(id),
        expandable: resource.childCnt > 0,
      })
      if (expanded) walk(state.childrenOf.get(id)?.ids ?? [])
    }
  }
  walk(state.rootIds)
  return rows
}

const with_ = (set: Set<number>, id: number) => new Set(set).add(id)

const without = (set: Set<number>, id: number) => {
  const next = new Set(set)
  next.delete(id)
  return next
}
