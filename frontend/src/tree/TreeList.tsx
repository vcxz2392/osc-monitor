import { useRef } from 'react'
import { useVirtualizer } from '@tanstack/react-virtual'
import { TreeRow } from './TreeRow'
import type { Row } from './useTree'

const ROW_HEIGHT = 36
const OVERSCAN = 8

interface Props {
  rows: Row[]
  onToggle: (id: number) => void
}

/**
 * 가상 스크롤.
 *
 * <p>행 높이를 고정하면 스크롤 위치에서 인덱스를 나누기 한 번으로 구한다.
 * 그래서 목록이 10만 행이어도 DOM 에 존재하는 행은 화면에 보이는 만큼(+오버스캔)이다.
 */
export function TreeList({ rows, onToggle }: Props) {
  const scrollRef = useRef<HTMLDivElement>(null)
  const virtualizer = useVirtualizer({
    count: rows.length,
    getScrollElement: () => scrollRef.current,
    estimateSize: () => ROW_HEIGHT,
    overscan: OVERSCAN,
  })

  return (
    <div className="tree" ref={scrollRef}>
      <div className="tree-canvas" style={{ height: virtualizer.getTotalSize() }}>
        {virtualizer.getVirtualItems().map((item) => {
          const row = rows[item.index]
          return (
            <div
              key={row.resource.id}
              className="tree-slot"
              style={{ transform: `translateY(${item.start}px)` }}
            >
              <TreeRow
                resource={row.resource}
                expanded={row.expanded}
                loading={row.loading}
                expandable={row.expandable}
                onToggle={onToggle}
              />
            </div>
          )
        })}
      </div>
    </div>
  )
}
