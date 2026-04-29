import type { ReactNode } from 'react'

import { cn } from '@/shared/lib/cn'

interface Column<T> {
  key: string
  header: string
  className?: string
  render: (row: T) => ReactNode
}

export function DataTable<T>({
  columns,
  getRowId,
  onRowClick,
  rowClassName,
  rows,
}: {
  columns: Column<T>[]
  getRowId?: (row: T) => string
  onRowClick?: (row: T) => void
  rowClassName?: (row: T) => string | undefined
  rows: T[]
}) {
  return (
    <div className="table-scroll rounded-[20px] border border-border bg-surface">
      <table className="min-w-full border-collapse text-left">
        <thead className="bg-surface-muted">
          <tr>
            {columns.map((column) => (
              <th
                key={column.key}
                className={cn('px-4 py-3 text-xs font-semibold uppercase tracking-[0.18em] text-text-muted', column.className)}
                scope="col"
              >
                {column.header}
              </th>
            ))}
          </tr>
        </thead>
        <tbody>
          {rows.map((row, index) => (
            <tr
              key={getRowId?.(row) ?? index}
              className={cn('border-t border-border', rowClassName?.(row), onRowClick ? 'cursor-pointer hover:bg-surface-muted' : undefined)}
              onClick={() => onRowClick?.(row)}
            >
              {columns.map((column) => (
                <td key={column.key} className={cn('px-4 py-3 align-top text-sm text-text-secondary', column.className)}>
                  {column.render(row)}
                </td>
              ))}
            </tr>
          ))}
        </tbody>
      </table>
    </div>
  )
}
