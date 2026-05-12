import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { useMemo, useState } from 'react'
import { useTranslation } from 'react-i18next'
import { Link, useNavigate, useParams } from 'react-router-dom'
import { ArrowLeft, Save, Trash2, Upload } from 'lucide-react'

import { useAuth } from '@/features/auth/useAuth'
import { FileAttachmentList, FilePreviewPanel } from '@/features/files/preview'
import type { FileAttachmentItem } from '@/features/files/preview'
import { educationService, fileService } from '@/shared/api/services'
import { isAccessDeniedApiError, normalizeApiError } from '@/shared/lib/api-errors'
import { downloadBlob } from '@/shared/lib/download'
import { formatDateTime } from '@/shared/lib/format'
import { hasAnyRole } from '@/shared/lib/roles'
import { Button } from '@/shared/ui/Button'
import { Card } from '@/shared/ui/Card'
import { ConfirmDialog } from '@/shared/ui/ConfirmDialog'
import { FormField } from '@/shared/ui/FormField'
import { Input } from '@/shared/ui/Input'
import { PageHeader } from '@/shared/ui/PageHeader'
import { Textarea } from '@/shared/ui/Textarea'
import { AccessDeniedPage } from '@/pages/shared/AccessDeniedPage'
import { ErrorState, LoadingState } from '@/shared/ui/StateViews'
import { Breadcrumbs } from '@/widgets/common/Breadcrumbs'
import { StatusBadge } from '@/widgets/common/StatusBadge'

export function LecturePage() {
  const { t } = useTranslation()
  const { lectureId = '' } = useParams()
  const { roles } = useAuth()
  const queryClient = useQueryClient()
  const navigate = useNavigate()
  const canManage = hasAnyRole(roles, ['ADMIN', 'OWNER', 'TEACHER'])

  const lectureQuery = useQuery({
    queryKey: ['education', 'lecture', lectureId],
    queryFn: () => educationService.getLecture(lectureId),
    enabled: Boolean(lectureId),
  })
  const attachmentsQuery = useQuery({
    queryKey: ['education', 'lecture-attachments', lectureId],
    queryFn: () => educationService.listLectureAttachments(lectureId),
    enabled: Boolean(lectureId),
  })

  const [formState, setFormState] = useState({ lectureId: '', title: '', content: '' })
  const [attachmentName, setAttachmentName] = useState('')
  const [attachmentFile, setAttachmentFile] = useState<File | null>(null)
  const [attachmentUploadError, setAttachmentUploadError] = useState('')
  const [selectedAttachmentId, setSelectedAttachmentId] = useState('')
  const [showDeleteConfirm, setShowDeleteConfirm] = useState(false)
  const [attachmentToRemove, setAttachmentToRemove] = useState<{ id: string; name: string } | null>(null)

  const updateLectureMutation = useMutation({
    mutationFn: () => {
      if (!lectureQuery.data) {
        throw new Error('missing-lecture')
      }

      const title = getEditableTitle(lectureQuery.data, formState).trim()
      const content = getEditableContent(lectureQuery.data, formState).trim()

      return educationService.updateLecture(lectureQuery.data.id, {
        title,
        content: content || undefined,
        orderIndex: lectureQuery.data.orderIndex,
      })
    },
    onSuccess: async () => {
      await queryClient.invalidateQueries({ queryKey: ['education', 'lecture', lectureId] })
      await queryClient.invalidateQueries({ queryKey: ['education', 'subject-lectures'] })
    },
  })

  const attachmentUploadMutation = useMutation({
    mutationFn: async () => {
      if (!attachmentFile) {
        throw new Error('missing-file')
      }

      const uploaded = await fileService.uploadFile(attachmentFile, 'ATTACHMENT')
      return educationService.addLectureAttachment(lectureId, {
        fileId: uploaded.fileId,
        displayName: attachmentName.trim() || undefined,
      })
    },
    onSuccess: async () => {
      await queryClient.invalidateQueries({ queryKey: ['education', 'lecture-attachments', lectureId] })
      setAttachmentUploadError('')
      setAttachmentName('')
      setAttachmentFile(null)
    },
    onError: (error) => {
      const normalized = normalizeApiError(error)
      if (normalized?.status === 415) {
        setAttachmentUploadError(t('files.unsupportedUploadType'))
        return
      }
      setAttachmentUploadError(t('files.uploadFailed'))
    },
  })

  const removeAttachmentMutation = useMutation({
    mutationFn: ({ attachmentId }: { attachmentId: string }) => educationService.removeLectureAttachment(lectureId, attachmentId),
    onSuccess: async () => {
      await queryClient.invalidateQueries({ queryKey: ['education', 'lecture-attachments', lectureId] })
    },
  })

  const deleteLectureMutation = useMutation({
    mutationFn: () => educationService.deleteLecture(lectureId),
    onSuccess: async () => {
      await queryClient.invalidateQueries({ queryKey: ['education', 'subject-lectures'] })
      navigate('/subjects')
    },
  })

  const transitionMutation = useMutation({
    mutationFn: async (action: 'publish' | 'close' | 'reopen' | 'archive' | 'restore') => {
      switch (action) {
        case 'publish':
          return educationService.publishLecture(lectureId)
        case 'close':
          return educationService.closeLecture(lectureId)
        case 'reopen':
          return educationService.reopenLecture(lectureId)
        case 'archive':
          return educationService.archiveLecture(lectureId)
        case 'restore':
          return educationService.restoreLecture(lectureId)
        default:
          return lectureQuery.data
      }
    },
    onSuccess: async () => {
      await queryClient.invalidateQueries({ queryKey: ['education', 'lecture', lectureId] })
      await queryClient.invalidateQueries({ queryKey: ['education', 'subject-lectures'] })
    },
  })

  const actions = useMemo(() => {
    const status = lectureQuery.data?.status
    if (!status) {
      return []
    }

    if (status === 'DRAFT') {
      return ['publish', 'archive'] as const
    }
    if (status === 'PUBLISHED') {
      return ['close', 'archive'] as const
    }
    if (status === 'CLOSED') {
      return ['reopen', 'archive'] as const
    }
    if (status === 'ARCHIVED') {
      return ['restore'] as const
    }
    return []
  }, [lectureQuery.data?.status])

  if (lectureQuery.isLoading || attachmentsQuery.isLoading) {
    return <LoadingState />
  }

  if (lectureQuery.isError || attachmentsQuery.isError) {
    if (isAccessDeniedApiError(lectureQuery.error) || isAccessDeniedApiError(attachmentsQuery.error)) {
      return <AccessDeniedPage />
    }

    return <ErrorState description={t('common.states.error')} title={t('education.lectureTitle')} />
  }

  const lecture = lectureQuery.data
  if (!lecture) {
    return <ErrorState description={t('common.states.notFound')} title={t('education.lectureTitle')} />
  }

  const isPublishedOrClosed = lecture.status === 'PUBLISHED' || lecture.status === 'CLOSED'
  const isArchived = lecture.status === 'ARCHIVED'
  const canEditLecture = canManage && !isArchived
  const editableTitle = getEditableTitle(lecture, formState)
  const editableContent = getEditableContent(lecture, formState)
  const attachments = attachmentsQuery.data ?? []
  const attachmentItems: FileAttachmentItem[] = attachments.map((attachment) => ({
    id: attachment.id,
    fileId: attachment.fileId,
    displayName: attachment.displayName,
    originalFileName: attachment.originalFileName,
    contentType: attachment.contentType,
    sizeBytes: attachment.sizeBytes,
    previewAvailable: attachment.previewAvailable,
    createdAt: attachment.createdAt,
  }))
  const selectedAttachment = attachmentItems.find((attachment) => attachment.id === selectedAttachmentId)
    ?? attachmentItems[0]
    ?? null

  return (
    <div className="space-y-6">
      <Breadcrumbs
        items={[
          { label: t('navigation.shared.education'), to: '/education' },
          { label: t('navigation.shared.subjects'), to: '/subjects' },
          { label: lecture.title },
        ]}
      />

      <PageHeader
        actions={(
          <div className="flex flex-wrap gap-3">
            <Link to={`/subjects/${lecture.subjectId}`}>
              <Button variant="secondary">
                <ArrowLeft className="mr-2 h-4 w-4" />
                {t('common.actions.back')}
              </Button>
            </Link>
            {lecture.status ? <StatusBadge value={lecture.status} /> : null}
          </div>
        )}
        description={lecture.content ?? t('education.lectureContentPlaceholder')}
        title={lecture.title}
      />

      <div className="grid gap-6 xl:grid-cols-[minmax(0,1fr)_320px]">
        <main className="space-y-6">
          <Card className="space-y-4">
            <PageHeader title={t('education.lectureDetails')} />
            {canManage && isPublishedOrClosed ? (
              <div className="rounded-[12px] border border-warning/40 bg-warning/10 px-4 py-3 text-sm text-text-primary">
                {t('education.publishedLectureEditWarning')}
              </div>
            ) : null}
            {canManage && isArchived ? (
              <div className="rounded-[12px] border border-border bg-surface-muted px-4 py-3 text-sm text-text-secondary">
                {t('education.archivedLectureEditingDisabled')}
              </div>
            ) : null}
            <FormField label={t('education.lectureTitle')}>
              <Input
                disabled={!canEditLecture}
                value={editableTitle}
                onChange={(event) => setFormState((current) => ({
                  lectureId: lecture.id,
                  title: event.target.value,
                  content: current.lectureId === lecture.id ? current.content : lecture.content ?? '',
                }))}
              />
            </FormField>
            <FormField label={t('education.lectureContent')}>
              <Textarea
                disabled={!canEditLecture}
                value={editableContent}
                onChange={(event) => setFormState((current) => ({
                  lectureId: lecture.id,
                  title: current.lectureId === lecture.id ? current.title : lecture.title,
                  content: event.target.value,
                }))}
                rows={8}
              />
            </FormField>
            <div className="text-sm text-text-secondary">
              {t('education.updatedLabel')}: {formatDateTime(lecture.updatedAt)}
            </div>
            {canManage ? (
              <div className="flex flex-wrap gap-3">
                <Button
                  disabled={!canEditLecture || !editableTitle.trim() || updateLectureMutation.isPending}
                  onClick={() => updateLectureMutation.mutate()}
                >
                  <Save className="mr-2 h-4 w-4" />
                  {t('common.actions.save')}
                </Button>
                <Button
                  variant="secondary"
                  disabled={!canEditLecture}
                  onClick={() => setFormState({
                    lectureId: lecture.id,
                    title: lecture.title,
                    content: lecture.content ?? '',
                  })}
                >
                  {t('common.actions.reset')}
                </Button>
              </div>
            ) : null}
          </Card>

          <Card className="space-y-4">
            <PageHeader title={t('education.lectureAttachments')} />
            <div className="grid gap-4 xl:grid-cols-[minmax(0,360px)_minmax(0,1fr)]">
              <FileAttachmentList
                canRemove={canManage}
                collapsible
                defaultExpanded
                emptyMessage={t('education.noLectureAttachments')}
                files={attachmentItems}
                removeDisabled={!canEditLecture || removeAttachmentMutation.isPending}
                selectedFileId={selectedAttachment?.id ?? null}
                onDownload={async (attachment) => {
                  const blob = await educationService.downloadLectureAttachment(lectureId, attachment.id)
                  downloadBlob(blob, attachment.originalFileName)
                }}
                onRemove={(attachment) => {
                  if (isPublishedOrClosed) {
                    setAttachmentToRemove({
                      id: attachment.id,
                      name: attachment.displayName?.trim() || attachment.originalFileName,
                    })
                    return
                  }
                  removeAttachmentMutation.mutate({ attachmentId: attachment.id })
                }}
                onSelect={(attachment) => setSelectedAttachmentId(attachment.id)}
              />
              <FilePreviewPanel
                fetchDownloadBlob={(attachment) => educationService.downloadLectureAttachment(lectureId, attachment.id)}
                fetchPreviewBlob={(attachment) => educationService.previewLectureAttachment(lectureId, attachment.id)}
                selectedFile={selectedAttachment}
                title={t('assignments.filePreview')}
                onDownload={async (attachment) => {
                  const blob = await educationService.downloadLectureAttachment(lectureId, attachment.id)
                  downloadBlob(blob, attachment.originalFileName)
                }}
              />
            </div>
            {canManage ? (
              <div className="space-y-3">
                <FormField label={t('education.attachmentDisplayName')}>
                  <Input
                    disabled={!canEditLecture}
                    value={attachmentName}
                    onChange={(event) => setAttachmentName(event.target.value)}
                    placeholder={t('education.attachmentDisplayNamePlaceholder')}
                  />
                </FormField>
                <FormField label={t('education.attachmentFileLabel')}>
                  <Input
                    disabled={!canEditLecture}
                    type="file"
                    onChange={(event) => {
                      setAttachmentFile(event.target.files?.[0] ?? null)
                      setAttachmentUploadError('')
                    }}
                  />
                  <p className="text-xs text-text-muted">{t('files.allowedFileTypesHint')}</p>
                </FormField>
                <Button
                  disabled={!canEditLecture || !attachmentFile || attachmentUploadMutation.isPending}
                  onClick={() => attachmentUploadMutation.mutate()}
                >
                  <Upload className="mr-2 h-4 w-4" />
                  {t('education.addAttachment')}
                </Button>
                {attachmentUploadError ? (
                  <p className="text-sm text-danger">{attachmentUploadError}</p>
                ) : null}
              </div>
            ) : null}
          </Card>
        </main>

        <aside className="space-y-4">
          <Card className="space-y-3">
            <PageHeader title={t('education.lectureStatusActions')} />
            {actions.length === 0 ? (
              <div className="text-sm text-text-secondary">{t('education.noStatusActions')}</div>
            ) : (
              actions.map((action) => (
                <Button
                  key={action}
                  fullWidth
                  variant="secondary"
                  onClick={() => transitionMutation.mutate(action)}
                >
                  {t(`education.lectureActions.${action}`)}
                </Button>
              ))
            )}
            {canManage ? (
              <Button
                fullWidth
                variant="ghost"
                onClick={() => setShowDeleteConfirm(true)}
              >
                <Trash2 className="mr-2 h-4 w-4" />
                {t('common.actions.delete')}
              </Button>
            ) : null}
          </Card>
        </aside>
      </div>

      <ConfirmDialog
        open={showDeleteConfirm}
        title={t('education.deleteLectureTitle')}
        description={t('education.deleteLectureDescription')}
        onConfirm={() => {
          setShowDeleteConfirm(false)
          deleteLectureMutation.mutate()
        }}
        onCancel={() => setShowDeleteConfirm(false)}
      />
      <ConfirmDialog
        open={Boolean(attachmentToRemove)}
        title={t('education.removeLectureFileTitle')}
        description={t('education.removeLectureFileWarning', { name: attachmentToRemove?.name ?? '' })}
        onConfirm={() => {
          if (!attachmentToRemove) {
            return
          }
          removeAttachmentMutation.mutate({ attachmentId: attachmentToRemove.id })
          setAttachmentToRemove(null)
        }}
        onCancel={() => setAttachmentToRemove(null)}
      />
    </div>
  )
}

function getEditableTitle(
  lecture: { id: string, title: string },
  formState: { lectureId: string, title: string }
) {
  return formState.lectureId === lecture.id ? formState.title : lecture.title
}

function getEditableContent(
  lecture: { id: string, content?: string | null },
  formState: { lectureId: string, content: string }
) {
  return formState.lectureId === lecture.id ? formState.content : lecture.content ?? ''
}
