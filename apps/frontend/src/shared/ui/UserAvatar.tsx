import { useEffect, useMemo, useState } from 'react'
import { useQuery } from '@tanstack/react-query'

import { fileService } from '@/shared/api/services'
import { cn } from '@/shared/lib/cn'

const sizeClasses = {
  sm: 'h-8 w-8 text-xs',
  md: 'h-10 w-10 text-sm',
  lg: 'h-16 w-16 text-base',
} as const

type AvatarSize = keyof typeof sizeClasses

interface UserAvatarProps {
  src?: string | null
  fileId?: string | null
  displayName?: string | null
  username?: string | null
  email?: string | null
  alt?: string
  size?: AvatarSize
  className?: string
}

export function UserAvatar({
  alt,
  className,
  displayName,
  email,
  fileId,
  src,
  size = 'md',
  username,
}: UserAvatarProps) {
  const [failedSrc, setFailedSrc] = useState<string | null>(null)
  const previewQuery = useQuery({
    queryKey: ['files', 'avatar-preview', fileId],
    queryFn: () => fileService.previewFile(fileId ?? ''),
    enabled: Boolean(fileId && !src),
  })
  const imageUrl = useMemo(() => {
    if (!previewQuery.data) {
      return null
    }

    return URL.createObjectURL(previewQuery.data)
  }, [previewQuery.data])
  useEffect(() => {
    if (!imageUrl) {
      return undefined
    }

    return () => {
      URL.revokeObjectURL(imageUrl)
    }
  }, [imageUrl])
  const fallbackLabel = displayName ?? username ?? email ?? ''
  const initials = buildInitials(fallbackLabel)
  const resolvedSrc = src ?? imageUrl
  const showImage = Boolean(resolvedSrc && failedSrc !== resolvedSrc)

  return (
    <div
      aria-label={alt ?? fallbackLabel}
      className={cn(
        'inline-flex items-center justify-center rounded-full border border-border bg-surface-muted font-semibold text-text-secondary',
        sizeClasses[size],
        className,
      )}
    >
      {showImage ? (
        <img
          alt={alt ?? fallbackLabel}
          className="h-full w-full rounded-full object-cover"
          src={resolvedSrc ?? undefined}
          onError={() => setFailedSrc(resolvedSrc)}
        />
      ) : (
        <span>{initials}</span>
      )}
    </div>
  )
}

function buildInitials(value: string) {
  const normalized = value.trim()
  if (!normalized) {
    return '?'
  }

  const parts = normalized.split(/\s+/).filter(Boolean)
  if (parts.length === 1) {
    return parts[0].slice(0, 2).toUpperCase()
  }

  return `${parts[0][0]}${parts[1][0]}`.toUpperCase()
}
