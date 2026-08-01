import { useEffect, useState } from 'react'
import { api, type Resource } from '../api'
import { metricsLabel, typeLabel, updatedAt } from './format'

interface Props {
  resource: Resource
  onClose: () => void
}

/** 선택한 리소스의 상세. 목록이 이미 가진 값으로 그리고, 경로만 따로 받아 온다. */
export function DetailPanel({ resource, onClose }: Props) {
  const [ancestors, setAncestors] = useState<Resource[]>([])

  // 선택이 바뀔 때만 부른다. 델타가 들어올 때마다 다시 부르면 폴링마다 요청이 하나씩 는다.
  useEffect(() => {
    let cancelled = false
    api
      .ancestors(resource.id)
      .then((found) => !cancelled && setAncestors(found))
      .catch(() => !cancelled && setAncestors([]))
    return () => {
      cancelled = true
    }
  }, [resource.id])

  const path = [...ancestors.map((item) => item.name), resource.name].join(' › ')

  return (
    <aside className="detail">
      <div className="detail-head">
        <span className={`badge badge-${resource.type.toLowerCase()}`}>{typeLabel(resource.type)}</span>
        <strong>{resource.name}</strong>
        <button className="detail-close" onClick={onClose} aria-label="닫기">
          ✕
        </button>
      </div>
      <dl className="detail-body">
        <dt>상태</dt>
        <dd className={`status status-${resource.status.toLowerCase()}`}>{resource.status}</dd>
        <dt>마지막 갱신</dt>
        <dd>{updatedAt(resource.updatedAt)}</dd>
        <dt>리비전</dt>
        <dd>{resource.rev.toLocaleString()}</dd>
        {resource.type !== 'POD' && (
          <>
            <dt>하위</dt>
            <dd>
              직속 {resource.childCnt.toLocaleString()} · 파드 {resource.leafCnt.toLocaleString()}
            </dd>
            <dt>오류 / 경고</dt>
            <dd>
              <span className="status-error">{resource.errorCnt.toLocaleString()}</span>
              {' / '}
              <span className="status-warning">{resource.warnCnt.toLocaleString()}</span>
            </dd>
          </>
        )}
        <dt>지표</dt>
        <dd>{metricsLabel(resource) || '—'}</dd>
        <dt>경로</dt>
        <dd className="detail-path">{path}</dd>
      </dl>
    </aside>
  )
}
