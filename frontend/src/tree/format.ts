import type { Resource, ResourceType } from '../api'

const TYPE_LABEL: Record<ResourceType, string> = {
  CLUSTER: 'CLS',
  NODE: 'NOD',
  NAMESPACE: 'NS',
  POD: 'POD',
}

export const typeLabel = (type: ResourceType) => TYPE_LABEL[type]

/** 마지막 갱신 시간. 초 단위로 바뀌는 화면이라 날짜는 접고 시각만 보여준다. */
export function updatedAt(iso: string): string {
  const at = new Date(iso)
  return [at.getHours(), at.getMinutes(), at.getSeconds()]
    .map((part) => String(part).padStart(2, '0'))
    .join(':')
}

/** 리소스 종류에 맞는 지표. 요구사항의 "그 외 표시할 정보"에 해당한다. */
export function metricsLabel(resource: Resource): string {
  const metrics = resource.metrics
  if (!metrics) return ''
  switch (resource.type) {
    case 'CLUSTER':
      return `v${metrics.version} · ${metrics.region}`
    case 'NODE':
      return `CPU ${metrics.cpu}% · MEM ${metrics.mem}%`
    case 'NAMESPACE':
      return `쿼터 ${metrics.quotaCpu}% / ${metrics.quotaMem}%`
    case 'POD':
      return `재시작 ${metrics.restarts}회 · ${metrics.ageHours}h`
  }
}

/**
 * 상위 계층에 보여줄 "오류 N / 전체 M".
 *
 * <p>분모가 없으면 그 수치가 심각한지 알 수 없다. 잎(파드)은 하위가 없어 빈 값이다.
 */
export function aggregateLabel(resource: Resource): string {
  if (resource.type === 'POD') return ''
  return `${resource.errorCnt.toLocaleString()} / ${resource.leafCnt.toLocaleString()}`
}
