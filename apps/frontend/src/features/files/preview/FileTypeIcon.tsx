import { FileArchive, FileCode2, FileImage, FileQuestion, FileSpreadsheet, FileText, FileVideo } from 'lucide-react'

import type { FilePreviewMode } from '@/features/files/preview/filePreviewUtils'
import { cn } from '@/shared/lib/cn'

interface FileTypeIconProps {
  mode: FilePreviewMode
  extension?: string
  className?: string
}

export function FileTypeIcon({ mode, extension, className }: FileTypeIconProps) {
  const iconClassName = cn(className, resolveIconTone(mode, extension))

  if (mode === 'image') {
    return <FileImage className={iconClassName} />
  }
  if (mode === 'pdf') {
    return <FileText className={iconClassName} />
  }
  if (mode === 'text') {
    return <FileText className={iconClassName} />
  }
  if (mode === 'code') {
    return <FileCode2 className={iconClassName} />
  }
  if (mode === 'office') {
    return <FileSpreadsheet className={iconClassName} />
  }
  if (mode === 'archive') {
    return <FileArchive className={iconClassName} />
  }
  if (mode === 'media') {
    return <FileVideo className={iconClassName} />
  }

  return <FileQuestion className={iconClassName} />
}

function resolveIconTone(mode: FilePreviewMode, extension?: string) {
  if (mode === 'pdf') {
    return 'text-danger'
  }
  if (mode === 'image') {
    return 'text-success'
  }
  if (mode === 'code' || mode === 'text') {
    return 'text-text-secondary'
  }
  if (mode === 'archive') {
    return 'text-warning'
  }
  if (mode === 'media') {
    return 'text-accent'
  }
  if (mode === 'office') {
    if (extension === 'doc' || extension === 'docx') {
      return 'text-info'
    }
    if (extension === 'xls' || extension === 'xlsx' || extension === 'ods') {
      return 'text-success'
    }
    if (extension === 'ppt' || extension === 'pptx' || extension === 'odp') {
      return 'text-warning'
    }
    return 'text-text-secondary'
  }

  return 'text-text-muted'
}
