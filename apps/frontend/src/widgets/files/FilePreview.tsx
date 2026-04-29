import { useQuery } from '@tanstack/react-query'
import { useMemo } from 'react'
import { useTranslation } from 'react-i18next'

import { fileService } from '@/shared/api/services'
import { Button } from '@/shared/ui/Button'
import { Card } from '@/shared/ui/Card'
import { ErrorState, LoadingState } from '@/shared/ui/StateViews'

export function FilePreview({ fileId }: { fileId: string }) {
  const { t } = useTranslation(['common', 'errors'])
  const previewQuery = useQuery({
    queryKey: ['file-preview', fileId],
    queryFn: () => fileService.previewFile(fileId),
  })

  const previewUrl = useMemo(() => {
    if (!previewQuery.data) {
      return null
    }
    return URL.createObjectURL(previewQuery.data)
  }, [previewQuery.data])

  if (previewQuery.isLoading) {
    return <LoadingState />
  }

  if (previewQuery.isError) {
    return (
      <ErrorState
        description={t('errors:FILE_PREVIEW_NOT_AVAILABLE')}
        title={t('common.states.error')}
      />
    )
  }

  if (!previewUrl) {
    return null
  }

  const contentType = previewQuery.data?.type ?? ''
  if (contentType.startsWith('image/')) {
    return <img alt="" className="rounded-[18px] border border-border" src={previewUrl} />
  }

  if (contentType === 'application/pdf') {
    return <iframe className="min-h-[520px] w-full rounded-[18px] border border-border" src={previewUrl} title={t('common.actions.preview')} />
  }

  return (
    <Card className="space-y-4">
      <p className="text-sm text-text-secondary">{t('errors:FILE_PREVIEW_NOT_AVAILABLE')}</p>
      <Button variant="secondary" onClick={() => void fileService.downloadFile(fileId)}>
        {t('common.actions.download')}
      </Button>
    </Card>
  )
}
