import { Download, Eye, Trash2 } from 'lucide-react'
import { useMemo, useState } from 'react'
import { useTranslation } from 'react-i18next'

import { FileTypeIcon } from '@/features/files/preview/FileTypeIcon'
import { detectFilePreviewMode, formatFileSize, formatFileType, getFileExtension } from '@/features/files/preview/filePreviewUtils'
import type { FileAttachmentItem } from '@/features/files/preview/useFilePreview'
import { formatDateTime } from '@/shared/lib/format'
import { Button } from '@/shared/ui/Button'

interface FileAttachmentListProps {
  files: FileAttachmentItem[]
  selectedFileId?: string | null
  onSelect?: (file: FileAttachmentItem) => void
  onDownload: (file: FileAttachmentItem) => Promise<void> | void
  onRemove?: (file: FileAttachmentItem) => Promise<void> | void
  canRemove?: boolean
  removeDisabled?: boolean
  collapsible?: boolean
  defaultExpanded?: boolean
  emptyMessage?: string
}

export function FileAttachmentList({
  files,
  selectedFileId,
  onSelect,
  onDownload,
  onRemove,
  canRemove = false,
  removeDisabled = false,
  collapsible = false,
  defaultExpanded = true,
  emptyMessage,
}: FileAttachmentListProps) {
  const { t } = useTranslation()
  const [expanded, setExpanded] = useState(defaultExpanded)

  const fileCountLabel = useMemo(
    () => t('files.fileCount', { count: files.length }),
    [files.length, t],
  )

  if (files.length === 0) {
    return (
      <div className="rounded-[12px] border border-border bg-surface-muted px-4 py-3 text-sm text-text-secondary">
        {emptyMessage ?? t('files.noFiles')}
      </div>
    )
  }

  return (
    <div className="space-y-3">
      {collapsible ? (
        <div className="flex items-center justify-between gap-3">
          <span className="rounded-full border border-border bg-surface-muted px-2.5 py-1 text-xs font-semibold text-text-secondary">
            {fileCountLabel}
          </span>
          <Button variant="ghost" onClick={() => setExpanded((current) => !current)}>
            {expanded ? t('files.hideFiles') : t('files.showFiles')}
          </Button>
        </div>
      ) : null}

      {!collapsible || expanded ? (
        <div className="space-y-2">
          {files.map((file) => {
            const mode = detectFilePreviewMode(file)
            const isSelected = selectedFileId === file.id

            return (
              <div
                key={file.id}
                className={`rounded-[12px] border px-3 py-2 ${
                  isSelected
                    ? 'border-accent bg-accent/5'
                    : 'border-border bg-surface-muted'
                }`}
              >
                <div className="flex flex-wrap items-center justify-between gap-3">
                  <button
                    className="flex min-w-0 flex-1 items-start gap-3 text-left"
                    type="button"
                    onClick={() => onSelect?.(file)}
                  >
                    <span className="mt-0.5 rounded-[10px] border border-border bg-surface p-2 text-text-secondary">
                      <FileTypeIcon extension={getFileExtension(file.originalFileName)} mode={mode} className="h-4 w-4" />
                    </span>
                    <span className="min-w-0">
                      <span className="block truncate text-sm font-semibold text-text-primary">
                        {file.displayName?.trim() || file.originalFileName}
                      </span>
                      <span className="block text-xs text-text-muted">
                          {formatFileType(file.contentType, file.originalFileName)} · {formatFileSize(file.sizeBytes)}
                        {file.createdAt ? ` · ${formatDateTime(file.createdAt)}` : ''}
                      </span>
                      {isSelected ? (
                        <span className="mt-1 inline-block rounded-full border border-accent/30 bg-accent/10 px-2 py-0.5 text-[11px] font-semibold text-accent">
                          {t('files.selectedFile')}
                        </span>
                      ) : null}
                    </span>
                  </button>

                  <div className="flex flex-wrap gap-2">
                    <Button variant="ghost" onClick={() => onSelect?.(file)}>
                      <Eye className="mr-2 h-4 w-4" />
                      {t('files.preview')}
                    </Button>
                    <Button variant="secondary" onClick={() => onDownload(file)}>
                      <Download className="mr-2 h-4 w-4" />
                      {t('files.download')}
                    </Button>
                    {canRemove && onRemove ? (
                      <Button
                        variant="ghost"
                        disabled={removeDisabled}
                        onClick={() => onRemove(file)}
                      >
                        <Trash2 className="mr-2 h-4 w-4" />
                        {t('files.remove')}
                      </Button>
                    ) : null}
                  </div>
                </div>
              </div>
            )
          })}
        </div>
      ) : null}
    </div>
  )
}
