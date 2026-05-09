import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { Plus, RotateCcw } from 'lucide-react'
import { useMemo, useState } from 'react'
import { useTranslation } from 'react-i18next'
import { Link } from 'react-router-dom'

import { useAuth } from '@/features/auth/useAuth'
import { AccessDeniedPage } from '@/pages/shared/AccessDeniedPage'
import { specialtyService } from '@/shared/api/services'
import { buildApiFieldErrors, getLocalizedApiErrorMessage, normalizeApiError } from '@/shared/lib/api-errors'
import { hasAnyRole } from '@/shared/lib/roles'
import type { SpecialtyResponse } from '@/shared/types/api'
import { Button } from '@/shared/ui/Button'
import { Card } from '@/shared/ui/Card'
import { FormField } from '@/shared/ui/FormField'
import { Input } from '@/shared/ui/Input'
import { PageHeader } from '@/shared/ui/PageHeader'
import { Select } from '@/shared/ui/Select'
import { EmptyState, ErrorState, LoadingState } from '@/shared/ui/StateViews'
import { Textarea } from '@/shared/ui/Textarea'
import { Breadcrumbs } from '@/widgets/common/Breadcrumbs'
import { StatusBadge } from '@/widgets/common/StatusBadge'

type ActiveFilter = 'ALL' | 'ACTIVE' | 'ARCHIVED'

interface SpecialtyFormState {
  code: string
  name: string
  description: string
}

const initialFormState: SpecialtyFormState = {
  code: '',
  name: '',
  description: '',
}

export function SpecialtiesPage() {
  const { t } = useTranslation()
  const { roles } = useAuth()
  const queryClient = useQueryClient()
  const canRead = hasAnyRole(roles, ['OWNER', 'ADMIN', 'TEACHER'])
  const canManage = hasAnyRole(roles, ['OWNER', 'ADMIN'])
  const [search, setSearch] = useState('')
  const [activeFilter, setActiveFilter] = useState<ActiveFilter>('ACTIVE')
  const [editingItem, setEditingItem] = useState<SpecialtyResponse | null>(null)
  const [isEditorOpen, setIsEditorOpen] = useState(false)
  const [form, setForm] = useState<SpecialtyFormState>(initialFormState)
  const [formError, setFormError] = useState<string | null>(null)
  const [formRequestId, setFormRequestId] = useState<string | null>(null)
  const [fieldErrors, setFieldErrors] = useState<Record<string, string>>({})

  const specialtiesQuery = useQuery({
    queryKey: ['education', 'specialties', activeFilter],
    queryFn: () => specialtyService.list({
      active: activeFilter === 'ALL' ? undefined : activeFilter === 'ACTIVE',
    }),
    enabled: canRead,
    staleTime: 1000 * 60 * 5,
  })

  const createMutation = useMutation({
    mutationFn: () => specialtyService.create({
      code: form.code.trim(),
      name: form.name.trim(),
      description: form.description.trim() || null,
    }),
    onSuccess: async () => {
      await queryClient.invalidateQueries({ queryKey: ['education', 'specialties'] })
      closeEditor()
    },
    onError: (error) => {
      const normalized = normalizeApiError(error)
      const message = getLocalizedApiErrorMessage(normalized, t)
      setFormError(message)
      setFormRequestId(normalized?.requestId ?? null)
      setFieldErrors(buildApiFieldErrors(normalized, message))
    },
  })

  const updateMutation = useMutation({
    mutationFn: () => specialtyService.update(editingItem!.id, {
      code: form.code.trim(),
      name: form.name.trim(),
      description: form.description.trim() || null,
    }),
    onSuccess: async () => {
      await queryClient.invalidateQueries({ queryKey: ['education', 'specialties'] })
      closeEditor()
    },
    onError: (error) => {
      const normalized = normalizeApiError(error)
      const message = getLocalizedApiErrorMessage(normalized, t)
      setFormError(message)
      setFormRequestId(normalized?.requestId ?? null)
      setFieldErrors(buildApiFieldErrors(normalized, message))
    },
  })

  const archiveMutation = useMutation({
    mutationFn: (id: string) => specialtyService.archive(id),
    onSuccess: async () => {
      await queryClient.invalidateQueries({ queryKey: ['education', 'specialties'] })
    },
  })

  const restoreMutation = useMutation({
    mutationFn: (id: string) => specialtyService.restore(id),
    onSuccess: async () => {
      await queryClient.invalidateQueries({ queryKey: ['education', 'specialties'] })
    },
  })

  const visibleItems = useMemo(() => {
    const query = search.trim().toLowerCase()
    const items = specialtiesQuery.data ?? []
    if (!query) {
      return items
    }

    return items.filter((item) => `${item.code} ${item.name} ${item.description ?? ''}`.toLowerCase().includes(query))
  }, [search, specialtiesQuery.data])

  const disabledReason = resolveFormDisabledReason(form, t)

  if (!canRead) {
    return <AccessDeniedPage />
  }

  return (
    <div className="space-y-6">
      <Breadcrumbs
        items={[
          { label: t('navigation.groups.academicManagement'), to: '/academic' },
          { label: t('navigation.shared.specialties') },
        ]}
      />

      <PageHeader
        description={t('academic.specialties.description')}
        title={t('navigation.shared.specialties')}
      />

      <Card className="space-y-4">
        <div className="grid gap-3 xl:grid-cols-[1.4fr_0.5fr_auto]">
          <FormField label={t('common.actions.search')}>
            <Input
              placeholder={t('academic.specialties.searchPlaceholder')}
              value={search}
              onChange={(event) => setSearch(event.target.value)}
            />
          </FormField>
          <FormField label={t('common.actions.filter')}>
            <Select value={activeFilter} onChange={(event) => setActiveFilter(event.target.value as ActiveFilter)}>
              <option value="ACTIVE">{t('academic.filters.activeOnly')}</option>
              <option value="ARCHIVED">{t('academic.filters.archivedOnly')}</option>
              <option value="ALL">{t('academic.filters.all')}</option>
            </Select>
          </FormField>
          {canManage ? (
            <div className="flex items-end">
              <Button
                className="w-full xl:w-auto"
                onClick={() => {
                  setEditingItem(null)
                  setForm(initialFormState)
                  setIsEditorOpen(true)
                  setFormError(null)
                  setFieldErrors({})
                }}
              >
                <Plus className="mr-2 h-4 w-4" />
                {t('academic.specialties.add')}
              </Button>
            </div>
          ) : null}
        </div>
      </Card>

      {canManage && isEditorOpen ? (
        <Card className="space-y-4 border-accent/30">
          <PageHeader
            className="mb-0"
            description={editingItem ? t('academic.specialties.editDescription') : t('academic.specialties.createDescription')}
            title={editingItem ? t('academic.specialties.edit') : t('academic.specialties.add')}
          />
          <div className="grid gap-4 md:grid-cols-2">
            <FormField error={fieldErrors.code} label={t('academic.specialties.code')}>
              <Input
                value={form.code}
                onChange={(event) => setForm((current) => ({ ...current, code: event.target.value }))}
              />
            </FormField>
            <FormField error={fieldErrors.name} label={t('academic.specialties.name')}>
              <Input
                value={form.name}
                onChange={(event) => setForm((current) => ({ ...current, name: event.target.value }))}
              />
            </FormField>
          </div>
          <FormField error={fieldErrors.description} label={t('common.labels.description')}>
            <Textarea
              rows={3}
              value={form.description}
              onChange={(event) => setForm((current) => ({ ...current, description: event.target.value }))}
            />
          </FormField>
          {formError ? (
            <div className="rounded-[14px] border border-danger/20 bg-danger/5 px-3 py-2 text-sm text-text-primary">
              <p>{formError}</p>
              {formRequestId ? (
                <p className="mt-1 text-xs text-text-muted">{t('common.labels.requestId')}: {formRequestId}</p>
              ) : null}
            </div>
          ) : null}
          {disabledReason ? (
            <p className="text-sm text-text-secondary">{disabledReason}</p>
          ) : null}
          <div className="flex flex-wrap gap-3">
            <Button
              disabled={Boolean(disabledReason) || createMutation.isPending || updateMutation.isPending}
              onClick={() => {
                setFormError(null)
                setFormRequestId(null)
                setFieldErrors({})
                if (editingItem) {
                  updateMutation.mutate()
                  return
                }
                createMutation.mutate()
              }}
            >
              {editingItem ? t('common.actions.update') : t('common.actions.create')}
            </Button>
            <Button variant="secondary" onClick={closeEditor}>
              {t('common.actions.cancel')}
            </Button>
          </div>
        </Card>
      ) : null}

      {specialtiesQuery.isLoading ? <LoadingState /> : null}
      {specialtiesQuery.isError ? (
        <ErrorState
          description={t('education.academicStructureLoadFailed')}
          title={t('navigation.shared.specialties')}
          onRetry={() => void specialtiesQuery.refetch()}
        />
      ) : null}
      {!specialtiesQuery.isLoading && !specialtiesQuery.isError && visibleItems.length === 0 ? (
        <EmptyState
          description={t('academic.specialties.empty')}
          title={t('navigation.shared.specialties')}
          action={canManage ? (
            <Button
              onClick={() => {
                setEditingItem(null)
                setForm(initialFormState)
                setIsEditorOpen(true)
                setFormError(null)
                setFormRequestId(null)
                setFieldErrors({})
              }}
            >
              <Plus className="mr-2 h-4 w-4" />
              {t('academic.specialties.add')}
            </Button>
          ) : null}
        />
      ) : null}

      {visibleItems.length > 0 ? (
        <div className="grid gap-3">
          {visibleItems.map((item) => (
            <Card key={item.id} className="space-y-3">
              <div className="flex flex-wrap items-start justify-between gap-3">
                <div className="space-y-1">
                  <p className="text-xs font-semibold uppercase tracking-[0.16em] text-text-muted">{item.code}</p>
                  <h2 className="text-lg font-semibold text-text-primary">{item.name}</h2>
                </div>
                <StatusBadge value={item.active ? 'ACTIVE' : 'ARCHIVED'} />
              </div>
              <p className="text-sm leading-6 text-text-secondary">{item.description || t('academic.specialties.noDescription')}</p>
              <div className="flex flex-wrap gap-2">
                <Link to={`/academic/specialties/${item.id}`}>
                  <Button variant="secondary">
                    {t('academic.actions.viewSpecialty')}
                  </Button>
                </Link>
                <Link to={`/academic/specialties/${item.id}/groups`}>
                  <Button variant="ghost">
                    {t('academic.actions.viewGroups')}
                  </Button>
                </Link>
              </div>
              {canManage ? (
                <div className="flex flex-wrap gap-2">
                  <Button
                    variant="secondary"
                    onClick={() => {
                      setEditingItem(item)
                      setIsEditorOpen(true)
                      setForm({
                        code: item.code,
                        name: item.name,
                        description: item.description ?? '',
                      })
                      setFormError(null)
                      setFormRequestId(null)
                      setFieldErrors({})
                    }}
                  >
                    {t('common.actions.edit')}
                  </Button>
                  {item.active ? (
                    <Button
                      disabled={archiveMutation.isPending}
                      variant="ghost"
                      onClick={() => archiveMutation.mutate(item.id)}
                    >
                      {t('academic.common.archive')}
                    </Button>
                  ) : (
                    <Button
                      disabled={restoreMutation.isPending}
                      variant="ghost"
                      onClick={() => restoreMutation.mutate(item.id)}
                    >
                      <RotateCcw className="mr-2 h-4 w-4" />
                      {t('academic.common.restore')}
                    </Button>
                  )}
                </div>
              ) : null}
            </Card>
          ))}
        </div>
      ) : null}
    </div>
  )

  function closeEditor() {
    setEditingItem(null)
    setIsEditorOpen(false)
    setForm(initialFormState)
    setFormError(null)
    setFormRequestId(null)
    setFieldErrors({})
  }
}

function resolveFormDisabledReason(
  form: SpecialtyFormState,
  t: (key: string) => string,
) {
  if (!form.code.trim()) {
    return t('academic.validation.specialtyCodeRequired')
  }
  if (!form.name.trim()) {
    return t('academic.validation.specialtyNameRequired')
  }

  return null
}
