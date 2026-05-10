import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { useMemo, useState } from 'react'
import { useTranslation } from 'react-i18next'
import { Link, useNavigate, useParams } from 'react-router-dom'
import { ArrowLeft, Download, ExternalLink, Eye, Save, Trash2 } from 'lucide-react'

import { useAuth } from '@/features/auth/useAuth'
import { educationService } from '@/shared/api/services'
import { isAccessDeniedApiError } from '@/shared/lib/api-errors'
import { downloadBlob } from '@/shared/lib/download'
import { formatDateTime } from '@/shared/lib/format'
import { hasAnyRole } from '@/shared/lib/roles'
import type { TopicMaterialType } from '@/shared/types/api'
import { Button } from '@/shared/ui/Button'
import { Card } from '@/shared/ui/Card'
import { ConfirmDialog } from '@/shared/ui/ConfirmDialog'
import { FormField } from '@/shared/ui/FormField'
import { Input } from '@/shared/ui/Input'
import { PageHeader } from '@/shared/ui/PageHeader'
import { Select } from '@/shared/ui/Select'
import { Textarea } from '@/shared/ui/Textarea'
import { AccessDeniedPage } from '@/pages/shared/AccessDeniedPage'
import { ErrorState, LoadingState } from '@/shared/ui/StateViews'
import { Breadcrumbs } from '@/widgets/common/Breadcrumbs'

export function MaterialPage() {
  const { t } = useTranslation()
  const { materialId = '' } = useParams()
  const { roles } = useAuth()
  const queryClient = useQueryClient()
  const navigate = useNavigate()
  const canManage = hasAnyRole(roles, ['ADMIN', 'OWNER', 'TEACHER'])

  const materialQuery = useQuery({
    queryKey: ['education', 'material', materialId],
    queryFn: () => educationService.getTopicMaterial(materialId),
    enabled: Boolean(materialId),
  })

  const [formState, setFormState] = useState({
    materialId: '',
    title: '',
    description: '',
    type: 'TEXT' as TopicMaterialType,
    url: '',
    visible: true,
  })
  const [showDeleteConfirm, setShowDeleteConfirm] = useState(false)

  const updateMutation = useMutation({
    mutationFn: () => {
      if (!materialQuery.data) {
        throw new Error('missing-material')
      }
      const title = getEditableString(materialQuery.data.id, materialQuery.data.title, formState.materialId, formState.title).trim()
      const description = getEditableString(
        materialQuery.data.id,
        materialQuery.data.description ?? '',
        formState.materialId,
        formState.description,
      )
      const type = getEditableString(materialQuery.data.id, materialQuery.data.type, formState.materialId, formState.type) as TopicMaterialType
      const url = getEditableString(materialQuery.data.id, materialQuery.data.url ?? '', formState.materialId, formState.url).trim()
      const visible = getEditableBoolean(materialQuery.data.id, materialQuery.data.visible, formState.materialId, formState.visible)

      return educationService.updateTopicMaterial(materialQuery.data.id, {
        title,
        description: description.trim() || undefined,
        type,
        url: type === 'LINK' ? url : undefined,
        visible,
        orderIndex: materialQuery.data.orderIndex,
      })
    },
    onSuccess: async () => {
      await queryClient.invalidateQueries({ queryKey: ['education', 'material', materialId] })
      await queryClient.invalidateQueries({ queryKey: ['education', 'subject-materials'] })
    },
  })

  const transitionMutation = useMutation({
    mutationFn: async (action: 'publish' | 'hide' | 'archive' | 'restore') => {
      switch (action) {
        case 'publish':
          return educationService.publishTopicMaterial(materialId)
        case 'hide':
          return educationService.hideTopicMaterial(materialId)
        case 'archive':
          return educationService.archiveTopicMaterial(materialId)
        case 'restore':
          return educationService.restoreTopicMaterial(materialId)
      }
    },
    onSuccess: async () => {
      await queryClient.invalidateQueries({ queryKey: ['education', 'material', materialId] })
      await queryClient.invalidateQueries({ queryKey: ['education', 'subject-materials'] })
    },
  })

  const deleteMutation = useMutation({
    mutationFn: () => educationService.deleteTopicMaterial(materialId),
    onSuccess: async () => {
      await queryClient.invalidateQueries({ queryKey: ['education', 'subject-materials'] })
      navigate('/subjects')
    },
  })

  const previewMutation = useMutation({
    mutationFn: () => educationService.previewTopicMaterialFile(materialId),
    onSuccess: (blob) => {
      const url = URL.createObjectURL(blob)
      window.open(url, '_blank', 'noopener,noreferrer')
      setTimeout(() => URL.revokeObjectURL(url), 60_000)
    },
  })

  const downloadMutation = useMutation({
    mutationFn: () => educationService.downloadTopicMaterialFile(materialId),
    onSuccess: (blob) => {
      const fallbackName = materialQuery.data?.originalFileName ?? 'material-file'
      downloadBlob(blob, fallbackName)
    },
  })

  const statusActions = useMemo(() => {
    if (!materialQuery.data || !canManage) {
      return [] as Array<'publish' | 'hide' | 'archive' | 'restore'>
    }
    if (materialQuery.data.archived) {
      return ['restore'] as const
    }
    return ['publish', 'hide', 'archive'] as const
  }, [canManage, materialQuery.data])

  if (materialQuery.isLoading) {
    return <LoadingState />
  }

  if (materialQuery.isError) {
    if (isAccessDeniedApiError(materialQuery.error)) {
      return <AccessDeniedPage />
    }
    return <ErrorState description={t('common.states.error')} title={t('education.material')} />
  }

  const material = materialQuery.data
  if (!material) {
    return <ErrorState description={t('common.states.notFound')} title={t('education.material')} />
  }

  const editableTitle = getEditableString(material.id, material.title, formState.materialId, formState.title)
  const editableDescription = getEditableString(material.id, material.description ?? '', formState.materialId, formState.description)
  const editableType = getEditableString(material.id, material.type, formState.materialId, formState.type) as TopicMaterialType
  const editableUrl = getEditableString(material.id, material.url ?? '', formState.materialId, formState.url)
  const editableVisible = getEditableBoolean(material.id, material.visible, formState.materialId, formState.visible)

  return (
    <div className="space-y-6">
      <Breadcrumbs
        items={[
          { label: t('navigation.shared.education'), to: '/education' },
          { label: t('navigation.shared.subjects'), to: '/subjects' },
          { label: material.title },
        ]}
      />

      <PageHeader
        actions={(
          <div className="flex flex-wrap gap-3">
            <Button variant="secondary" onClick={() => navigate(-1)}>
              <ArrowLeft className="mr-2 h-4 w-4" />
              {t('common.actions.back')}
            </Button>
            <Link to="/subjects">
              <Button variant="secondary">{t('education.backToSubjects')}</Button>
            </Link>
          </div>
        )}
        description={material.description ?? t('education.materialDescriptionFallback')}
        title={material.title}
      />

      <Card className="space-y-4">
        <PageHeader title={t('education.materialDetails')} />
        <FormField label={t('education.materialTitle')}>
          <Input
            disabled={!canManage || material.archived}
            value={editableTitle}
            onChange={(event) => setFormState((current) => ({
              ...current,
              materialId: material.id,
              title: event.target.value,
            }))}
          />
        </FormField>
        <FormField label={t('education.materialType')}>
          <Select
            disabled={!canManage || material.archived}
            value={editableType}
            onChange={(event) => setFormState((current) => ({
              ...current,
              materialId: material.id,
              type: event.target.value as TopicMaterialType,
            }))}
          >
            <option value="TEXT">{t('education.textMaterial')}</option>
            <option value="LINK">{t('education.linkMaterial')}</option>
            <option value="FILE">{t('education.fileMaterial')}</option>
          </Select>
        </FormField>
        <FormField label={t('education.materialDescription')}>
          <Textarea
            disabled={!canManage || material.archived}
            value={editableDescription}
            onChange={(event) => setFormState((current) => ({
              ...current,
              materialId: material.id,
              description: event.target.value,
            }))}
          />
        </FormField>
        {editableType === 'LINK' ? (
          <FormField label={t('education.materialUrl')}>
            <Input
              disabled={!canManage || material.archived}
              value={editableUrl}
              onChange={(event) => setFormState((current) => ({
                ...current,
                materialId: material.id,
                url: event.target.value,
              }))}
            />
          </FormField>
        ) : null}
        {canManage ? (
          <div className="flex items-center gap-2">
            <input
              id={`material-visible-${material.id}`}
              checked={editableVisible}
              disabled={material.archived}
              type="checkbox"
              onChange={(event) => setFormState((current) => ({
                ...current,
                materialId: material.id,
                visible: event.target.checked,
              }))}
            />
            <label htmlFor={`material-visible-${material.id}`} className="text-sm text-text-secondary">
              {t('education.visibleToStudents')}
            </label>
          </div>
        ) : null}

        <div className="flex flex-wrap gap-3">
          {canManage ? (
            <Button
              disabled={!editableTitle.trim() || (editableType === 'LINK' && !editableUrl.trim()) || updateMutation.isPending}
              onClick={() => updateMutation.mutate()}
            >
              <Save className="mr-2 h-4 w-4" />
              {t('common.actions.save')}
            </Button>
          ) : null}
          {editableType === 'LINK' && editableUrl ? (
            <a href={editableUrl} rel="noopener noreferrer" target="_blank">
              <Button variant="secondary">
                <ExternalLink className="mr-2 h-4 w-4" />
                {t('education.openLink')}
              </Button>
            </a>
          ) : null}
          {material.type === 'FILE' ? (
            <>
              <Button variant="secondary" onClick={() => previewMutation.mutate()}>
                <Eye className="mr-2 h-4 w-4" />
                {t('education.previewFile')}
              </Button>
              <Button variant="secondary" onClick={() => downloadMutation.mutate()}>
                <Download className="mr-2 h-4 w-4" />
                {t('education.downloadFile')}
              </Button>
            </>
          ) : null}
          {canManage ? (
            <Button variant="danger" onClick={() => setShowDeleteConfirm(true)}>
              <Trash2 className="mr-2 h-4 w-4" />
              {t('education.deleteMaterial')}
            </Button>
          ) : null}
        </div>

        <div className="text-sm text-text-secondary">
          {t('education.updatedLabel')}: {formatDateTime(material.updatedAt)}
        </div>
      </Card>

      {canManage ? (
        <Card className="space-y-4">
          <PageHeader title={t('education.materialStatusActions')} />
          <div className="flex flex-wrap gap-3">
            {statusActions.map((action) => (
              <Button key={action} variant="secondary" onClick={() => transitionMutation.mutate(action)}>
                {action === 'publish' ? t('common.actions.publish') : null}
                {action === 'hide' ? t('education.hideMaterial') : null}
                {action === 'archive' ? t('education.archiveMaterial') : null}
                {action === 'restore' ? t('education.restoreMaterial') : null}
              </Button>
            ))}
          </div>
        </Card>
      ) : null}

      <ConfirmDialog
        description={t('education.confirmMaterialDelete')}
        open={showDeleteConfirm}
        title={t('education.deleteMaterial')}
        onCancel={() => setShowDeleteConfirm(false)}
        onConfirm={() => {
          setShowDeleteConfirm(false)
          deleteMutation.mutate()
        }}
      />
    </div>
  )
}

function getEditableString(materialId: string, fallback: string, activeId: string, value: string) {
  if (materialId === activeId) {
    return value
  }
  return fallback
}

function getEditableBoolean(materialId: string, fallback: boolean, activeId: string, value: boolean) {
  if (materialId === activeId) {
    return value
  }
  return fallback
}
