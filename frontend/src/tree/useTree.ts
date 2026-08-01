import { useCallback, useEffect, useState } from 'react'
import { api, type Resource } from '../api'

export interface TreeState {
  /** 정규화 저장소. 중첩 객체를 만들지 않아야 부분 갱신이 가능하다. */
  nodes: Map<number, Resource>
  rootIds: number[]
  rev: number
}

const EMPTY: TreeState = { nodes: new Map(), rootIds: [], rev: 0 }

export function useTree() {
  const [state, setState] = useState<TreeState>(EMPTY)
  const [error, setError] = useState<string | null>(null)

  const loadRoots = useCallback(async () => {
    try {
      const page = await api.roots()
      setState({
        nodes: new Map(page.items.map((item) => [item.id, item])),
        rootIds: page.items.map((item) => item.id),
        rev: page.rev,
      })
    } catch (cause) {
      setError(cause instanceof Error ? cause.message : '목록을 불러오지 못했습니다.')
    }
  }, [])

  useEffect(() => {
    void loadRoots()
  }, [loadRoots])

  return { state, error }
}

/** 화면에 그릴 행 목록. 지금은 루트뿐이고, 펼치기가 붙으면 여기서 평탄화한다. */
export function visibleRows(state: TreeState): Resource[] {
  return state.rootIds.map((id) => state.nodes.get(id)!).filter(Boolean)
}
