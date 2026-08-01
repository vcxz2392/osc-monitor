import { useEffect, useRef, useState } from 'react'
import { api, type ResourceType, type SearchResult } from '../api'
import { markStart } from '../perf'

const DEBOUNCE_MS = 250

export function useSearch(query: string, type: ResourceType | null) {
  const [result, setResult] = useState<SearchResult | null>(null)
  const [error, setError] = useState<string | null>(null)
  const requestId = useRef(0)

  useEffect(() => {
    const trimmed = query.trim()
    if (trimmed === '') {
      setResult(null)
      return
    }

    const timer = setTimeout(() => {
      // 측정 시작점은 디바운스가 끝나고 실제로 요청이 나가는 지점이다.
      // 원문이 "디바운스 제외" 라고 못박았다.
      markStart('search')
      const id = ++requestId.current
      api
        .search(trimmed, { type: type ?? undefined, size: 50 })
        .then((found) => {
          // 늦게 도착한 이전 요청이 최신 결과를 덮지 않게 한다.
          if (id === requestId.current) {
            setResult(found)
            setError(null)
          }
        })
        .catch((cause: Error) => id === requestId.current && setError(cause.message))
    }, DEBOUNCE_MS)

    return () => clearTimeout(timer)
  }, [query, type])

  return { result, error }
}
