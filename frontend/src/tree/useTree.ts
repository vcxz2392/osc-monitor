import { useCallback, useEffect, useRef, useState } from 'react'
import { api, type Resource, type ResourceStatus } from '../api'
import { markStart, rowRenders } from '../perf'

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
  selectedId: number | null
  /** 검색 결과에서 골랐을 때 그 행으로 스크롤하기 위한 표식 */
  revealId: number | null
  /** 지금 걸린 상태 필터. null 이면 전부 */
  status: ResourceStatus | null
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
  selectedId: null,
  revealId: null,
  status: null,
  rev: 0,
}

const PAGE_SIZE = 200
const POLL_MS = 2000

/** reveal 이 여러 계층을 순차로 받는 동안 모아 두는 곳 */
const loadedNodes = new Map<number, Resource>()

export interface DeltaStats {
  /** 서버가 내려준 변경 건수 */
  changed: number
  /** 그중 우리가 이미 갖고 있어 반영한 건수 */
  merged: number
  /** 실제로 다시 그려진 행 수 */
  rerendered: number
  rev: number
}

export function useTree() {
  const [state, setState] = useState<TreeState>(EMPTY)
  const [error, setError] = useState<string | null>(null)
  const [stats, setStats] = useState<DeltaStats | null>(null)
  // 리비전 커서는 상태로 두지 않는다. 바뀔 때마다 화면이 다시 그려질 이유가 없다.
  const cursor = useRef<{ since: number; sinceId: number | null }>({ since: 0, sinceId: null })

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
      .then(() => {
        cursor.current = { since: latest.current.rev, sinceId: null }
      })
      .catch((cause: Error) => setError(cause.message))
  }, [])

  /**
   * 주기적 폴링 + 서버 델타.
   *
   * <p>타이머를 의존성 없이 한 번만 건다. 상태를 의존성에 넣으면 매 렌더마다 타이머가
   * 다시 걸려, 조작이 잦은 동안에는 갱신이 영영 돌지 않는다.
   */
  useEffect(() => {
    const timer = setInterval(async () => {
      const current = latest.current
      if (current.rootIds.length === 0) return

      // 범위 = 펼쳐 둔 부모 + 상세를 열어 둔 리소스의 부모.
      // 후자를 빼면 그 부모를 접었을 때 상세 패널이 조용히 낡는다.
      const openParentIds = new Set(current.expanded)
      const selectedParent = current.selectedId === null ? null : current.nodes.get(current.selectedId)?.parentId
      if (selectedParent != null) openParentIds.add(selectedParent)

      markStart('delta')
      try {
        const delta = await api.changes(cursor.current.since, [...openParentIds], true, cursor.current.sinceId)
        cursor.current = { since: delta.maxRev, sinceId: delta.maxId }
        applyDelta(delta.changed)
      } catch {
        // 폴링 실패는 다음 주기에 다시 시도한다. 화면에 오류를 띄우지 않는다.
      }
    }, POLL_MS)
    return () => clearInterval(timer)
  }, [])

  const applyDelta = (changed: Resource[]) => {
    const before = rowRenders.count
    let merged = 0
    setState((prev) => {
      const nodes = new Map(prev.nodes)
      for (const item of changed) {
        const existing = nodes.get(item.id)
        // 아직 받지 않은 노드는 버린다. 나중에 그 페이지를 조회하면 서버의 현재 값이 온다.
        if (!existing) continue
        // 같은 리비전이면 덮지 않는다. 덮으면 참조가 바뀌어 그 행이 헛되이 다시 그려진다.
        if (existing.rev === item.rev) continue
        nodes.set(item.id, item)
        merged++
      }
      // 바뀐 것이 하나도 없으면 이전 상태를 그대로 돌려줘 리렌더 자체를 만들지 않는다.
      return merged === 0 ? prev : { ...prev, nodes }
    })
    requestAnimationFrame(() =>
      setTimeout(
        () =>
          setStats({
            changed: changed.length,
            merged,
            rerendered: rowRenders.count - before,
            rev: cursor.current.since,
          }),
        0,
      ),
    )
  }

  const toggle = useCallback(async (id: number) => {
    const current = latest.current
    // 펼치기·축소도 앱이 잰다. 개발자도구 User Timing 트랙에서 검색·필터·갱신과 같은 방식으로 읽힌다.
    markStart('expand')
    if (current.expanded.has(id)) {
      setState((prev) => collapse(prev, id))
      return
    }
    // 이미 받아 둔 계층이면 조회 없이 편다.
    if (current.childrenOf.has(id)) {
      setState((prev) => ({ ...prev, expanded: with_(prev.expanded, id) }))
      return
    }

    setState((prev) => ({ ...prev, loading: with_(prev.loading, id) }))
    try {
      const page = await api.children(id, { status: current.status ?? undefined, size: PAGE_SIZE })
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

  /**
   * 검색 결과의 위치까지 트리를 펼친다.
   *
   * <p>경로에 있는 조상을 위에서부터 차례로 확보한다. 아직 안 받은 계층만 조회하므로
   * 이미 펼쳐 본 경로라면 요청이 하나도 나가지 않는다.
   */
  const reveal = useCallback(async (target: Resource) => {
    const ancestorIds = target.path.split('/').filter(Boolean).map(Number).slice(0, -1)

    const loaded = new Map<number, { ids: number[]; cursor: string | null }>()
    for (const id of ancestorIds) {
      if (latest.current.childrenOf.has(id) || loaded.has(id)) continue
      const page = await api.children(id, { status: latest.current.status ?? undefined, size: PAGE_SIZE })
      loaded.set(id, { ids: page.items.map((item) => item.id), cursor: page.nextCursor })
      for (const item of page.items) loadedNodes.set(item.id, item)
    }

    setState((prev) => {
      const nodes = new Map(prev.nodes)
      for (const [id, item] of loadedNodes) nodes.set(id, item)
      loadedNodes.clear()
      const childrenOf = new Map(prev.childrenOf)
      for (const [id, list] of loaded) childrenOf.set(id, list)
      const expanded = new Set(prev.expanded)
      for (const id of ancestorIds) expanded.add(id)
      return { ...prev, nodes, childrenOf, expanded, selectedId: target.id, revealId: target.id }
    })
  }, [])

  const clearReveal = useCallback(() => {
    setState((prev) => (prev.revealId === null ? prev : { ...prev, revealId: null }))
  }, [])

  /**
   * 상태 필터를 바꾼다.
   *
   * <p>펼쳐 둔 계층을 모두 다시 받아 <b>한 번에 교체</b>한다. 먼저 비우고 나중에 채우면
   * 목록이 사라졌다 다시 차서 깜빡인다. 요청은 병렬로 나가고 화면은 한 번만 바뀐다.
   */
  const applyFilter = useCallback(async (status: ResourceStatus | null) => {
    markStart('filter')
    const openIds = [...latest.current.expanded]
    const [roots, ...pages] = await Promise.all([
      api.roots(status ?? undefined),
      ...openIds.map((id) => api.children(id, { status: status ?? undefined, size: PAGE_SIZE })),
    ])

    const nodes = new Map<number, Resource>()
    for (const item of roots.items) nodes.set(item.id, item)
    const childrenOf = new Map<number, { ids: number[]; cursor: string | null }>()
    openIds.forEach((id, index) => {
      const page = pages[index]
      for (const item of page.items) nodes.set(item.id, item)
      childrenOf.set(id, { ids: page.items.map((item) => item.id), cursor: page.nextCursor })
    })

    setState((prev) => ({
      ...prev,
      nodes,
      childrenOf,
      rootIds: roots.items.map((item) => item.id),
      // 걸러져 사라진 부모의 펼침 상태는 의미가 없다.
      expanded: new Set([...prev.expanded].filter((id) => nodes.has(id))),
      selectedId: prev.selectedId !== null && nodes.has(prev.selectedId) ? prev.selectedId : null,
      status,
      rev: roots.rev,
    }))
  }, [])

  const select = useCallback((id: number) => {
    setState((prev) => ({ ...prev, selectedId: prev.selectedId === id ? null : id }))
  }, [])

  const clearSelection = useCallback(() => {
    setState((prev) => ({ ...prev, selectedId: null }))
  }, [])

  return { state, error, stats, toggle, select, clearSelection, reveal, clearReveal, applyFilter }
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

/**
 * 접으면 그 아래를 버린다.
 *
 * <p>남겨 두면 상주량이 "지금까지 열어 본 총량" 에 비례한다. 버리면 "지금 펼친 양" 에 비례한다.
 * 대가는 다시 펼칠 때의 재요청 한 번이고, 그 비용은 이미 측정돼 있다(② 펼치기).
 */
function collapse(prev: TreeState, id: number): TreeState {
  const nodes = new Map(prev.nodes)
  const childrenOf = new Map(prev.childrenOf)
  const expanded = new Set(prev.expanded)
  const dropped = new Set<number>()

  const drop = (parentId: number) => {
    const list = childrenOf.get(parentId)
    if (!list) return
    childrenOf.delete(parentId)
    expanded.delete(parentId)
    for (const childId of list.ids) {
      drop(childId)
      nodes.delete(childId)
      dropped.add(childId)
    }
  }
  drop(id)
  expanded.delete(id)

  return {
    ...prev,
    nodes,
    childrenOf,
    expanded,
    // 버린 리소스의 상세를 열어 두고 있었다면 함께 닫는다.
    selectedId: prev.selectedId !== null && dropped.has(prev.selectedId) ? null : prev.selectedId,
  }
}

const with_ = (set: Set<number>, id: number) => new Set(set).add(id)

const without = (set: Set<number>, id: number) => {
  const next = new Set(set)
  next.delete(id)
  return next
}
