/** 백엔드 응답 계약. 서버 DTO 와 이름을 그대로 맞춘다. */

export type ResourceType = 'CLUSTER' | 'NODE' | 'NAMESPACE' | 'POD'
export type ResourceStatus = 'HEALTHY' | 'WARNING' | 'ERROR'

export interface Resource {
  id: number
  parentId: number | null
  type: ResourceType
  name: string
  status: ResourceStatus
  /** 자기 포함 조상 id 경로 '/1/1001/100001/10000001/' */
  path: string
  updatedAt: string
  rev: number
  errorCnt: number
  warnCnt: number
  childCnt: number
  /** 하위 파드 총수. 오류·경고의 분모다. */
  leafCnt: number
  metrics: Record<string, string | number> | null
}

export interface ChildrenPage {
  items: Resource[]
  nextCursor: string | null
  rev: number
}

export interface SearchResult {
  items: Resource[]
  truncated: boolean
  /** 조상 id → 이름. 결과마다 경로 문자열을 붙이지 않고 사전으로 한 번만 받는다. */
  ancestorNames: Record<string, string>
}

export interface Changes {
  maxRev: number
  maxId: number | null
  changed: Resource[]
  truncated: boolean
}

/** 계층 깊이는 종류에서 나온다. 서버가 따로 내려주지 않는다. */
export const DEPTH: Record<ResourceType, number> = {
  CLUSTER: 0,
  NODE: 1,
  NAMESPACE: 2,
  POD: 3,
}

const BASE = '/api/resources'

class ApiError extends Error {
  constructor(readonly status: number, readonly code: string, message: string) {
    super(message)
  }
}

async function get<T>(path: string, params: Record<string, string | number | undefined> = {}): Promise<T> {
  const query = new URLSearchParams()
  for (const [key, value] of Object.entries(params)) {
    if (value !== undefined && value !== null && value !== '') query.set(key, String(value))
  }
  const suffix = query.toString()
  return unwrap(await fetch(`${BASE}${path}${suffix ? `?${suffix}` : ''}`))
}

async function unwrap<T>(response: Response): Promise<T> {
  if (response.ok) return response.json() as Promise<T>
  // 서버가 { code, message, timestamp } 로 통일해 두었으므로 그대로 읽는다.
  const body = await response.json().catch(() => null)
  throw new ApiError(response.status, body?.code ?? 'UNKNOWN', body?.message ?? response.statusText)
}

export const api = {
  roots: (status?: ResourceStatus) => get<ChildrenPage>('/roots', { status }),

  children: (id: number, options: { cursor?: string; status?: ResourceStatus; size?: number } = {}) =>
    get<ChildrenPage>(`/${id}/children`, options),

  ancestors: (id: number) => get<Resource[]>(`/${id}/ancestors`),

  search: (q: string, options: { type?: ResourceType; size?: number } = {}) =>
    get<SearchResult>('/search', { q, ...options }),

  changes: async (since: number, openParentIds: number[], includeRoots: boolean, sinceId?: number | null) =>
    unwrap<Changes>(
      await fetch(`${BASE}/changes`, {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify({ since, sinceId: sinceId ?? null, openParentIds, includeRoots }),
      }),
    ),
}

export { ApiError }
