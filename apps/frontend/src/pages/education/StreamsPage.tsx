import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { Plus, RotateCcw, Users } from 'lucide-react'
import { useMemo, useState } from 'react'
import { useTranslation } from 'react-i18next'
import { Link, useParams, useSearchParams } from 'react-router-dom'

import { useAuth } from '@/features/auth/useAuth'
import { AccessDeniedPage } from '@/pages/shared/AccessDeniedPage'
import { specialtyService, streamService } from '@/shared/api/services'
import { buildApiFieldErrors, getLocalizedApiErrorMessage, normalizeApiError } from '@/shared/lib/api-errors'
import { hasAnyRole } from '@/shared/lib/roles'
import type { StreamResponse } from '@/shared/types/api'
import { Button } from '@/shared/ui/Button'
import { Card } from '@/shared/ui/Card'
import { FormField } from '@/shared/ui/FormField'
import { Input } from '@/shared/ui/Input'
import { PageHeader } from '@/shared/ui/PageHeader'
import { Select } from '@/shared/ui/Select'
import { EmptyState, ErrorState, LoadingState } from '@/shared/ui/StateViews'
import { Breadcrumbs } from '@/widgets/common/Breadcrumbs'
import { StatusBadge } from '@/widgets/common/StatusBadge'

type ActiveFilter = 'ALL' | 'ACTIVE' | 'ARCHIVED'

interface StreamFormState {
  name: string
  specialtyId: string
  studyYear: string
}

const initialFormState: StreamFormState = {
  name: '',
  specialtyId: '',
  studyYear: '',
}

export function StreamsPage() {
  const { t } = useTranslation()
  const { specialtyId: routeSpecialtyId } = useParams()
  const [searchParams] = useSearchParams()
  const { roles } = useAuth()
  const queryClient = useQueryClient()
  const canRead = hasAnyRole(roles, ['OWNER', 'ADMIN', 'TEACHER'])
  const canManage = hasAnyRole(roles, ['OWNER', 'ADMIN'])
  const specialtyContextId = routeSpecialtyId ?? ''
  const contextStudyYear = searchParams.get('studyYear') ?? ''
  const hasSpecialtyContext = Boolean(specialtyContextId)
  const [search, setSearch] = useState('')
  const [activeFilter, setActiveFilter] = useState<ActiveFilter>('ACTIVE')
  const [specialtyFilter, setSpecialtyFilter] = useState('')
  const [studyYearFilter, setStudyYearFilter] = useState(contextStudyYear)
  const [editingItem, setEditingItem] = useState<StreamResponse | null>(null)
  const [isEditorOpen, setIsEditorOpen] = useState(false)
  const [form, setForm] = useState<StreamFormState>(initialFormState)
  const [formError, setFormError] = useState<string | null>(null)
  const [formRequestId, setFormRequestId] = useState<string | null>(null)
  const [fieldErrors, setFieldErrors] = useState<Record<string, string>>({})

  const specialtiesQuery = useQuery({
    queryKey: ['education', 'specialties', 'active-for-streams'],
    queryFn: () => specialtyService.list({ active: true }),
    enabled: canRead,
    staleTime: 1000 * 60 * 5,
  })

  const streamsQuery = useQuery({
    queryKey: ['education', 'streams', activeFilter, specialtyContextId, specialtyFilter, studyYearFilter],
    queryFn: () => streamService.list({
      active: activeFilter === 'ALL' ? undefined : activeFilter === 'ACTIVE',
      specialtyId: specialtyContextId || specialtyFilter || undefined,
      studyYear: studyYearFilter ? Number(studyYearFilter) : undefined,
    }),
    enabled: canRead,
    staleTime: 1000 * 60 * 5,
  })

  const streamGroupsQuery = useQuery({
    queryKey: ['education', 'stream-groups-counts', (streamsQuery.data ?? []).map((item) => item.id).join(',')],
    queryFn: async () => {
      const streams = streamsQuery.data ?? []
      const entries = await Promise.all(streams.map(async (stream) => {
        const groups = await streamService.listGroups(stream.id)
        return [stream.id, groups.length] as const
      }))
      return new Map(entries)
    },
    enabled: canRead && (streamsQuery.data?.length ?? 0) > 0,
  })

  const createMutation = useMutation({
    mutationFn: () => streamService.create({
      name: form.name.trim(),
      specialtyId: specialtyContextId || form.specialtyId,
      studyYear: Number(form.studyYear),
    }),
    onSuccess: async () => {
      await queryClient.invalidateQueries({ queryKey: ['education', 'streams'] })
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
    mutationFn: () => streamService.update(editingItem!.id, {
      name: form.name.trim(),
      specialtyId: specialtyContextId || form.specialtyId,
      studyYear: Number(form.studyYear),
    }),
    onSuccess: async () => {
      await queryClient.invalidateQueries({ queryKey: ['education', 'streams'] })
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
    mutationFn: (id: string) => streamService.archive(id),
    onSuccess: async () => {
      await queryClient.invalidateQueries({ queryKey: ['education', 'streams'] })
    },
  })

  const restoreMutation = useMutation({
    mutationFn: (id: string) => streamService.restore(id),
    onSuccess: async () => {
      await queryClient.invalidateQueries({ queryKey: ['education', 'streams'] })
    },
  })

  const specialtiesById = useMemo(
    () => new Map((specialtiesQuery.data ?? []).map((item) => [item.id, item])),
    [specialtiesQuery.data],
  )
  const visibleItems = useMemo(() => {
    const query = search.trim().toLowerCase()
    const items = streamsQuery.data ?? []
    if (!query) {
      return items
    }

    return items.filter((item) => {
      const specialtyName = specialtiesById.get(item.specialtyId)?.name ?? ''
      return `${item.name} ${specialtyName} ${item.studyYear}`.toLowerCase().includes(query)
    })
  }, [search, specialtiesById, streamsQuery.data])

  const disabledReason = resolveFormDisabledReason(form, t)

  if (!canRead) {
    return <AccessDeniedPage />
  }

  return (
    <div className="space-y-6">
      <Breadcrumbs
        items={[
          { label: t('navigation.groups.academicManagement'), to: '/academic' },
          { label: t('navigation.shared.specialties'), to: '/academic/specialties' },
          ...(hasSpecialtyContext
            ? [{ label: t('academic.actions.viewSpecialty'), to: `/academic/specialties/${specialtyContextId}` }]
            : []),
          { label: t('navigation.shared.streams') },
        ]}
      />

      <PageHeader
        description={t('academic.streams.description')}
        title={hasSpecialtyContext ? t('academic.streams.forSpecialtyTitle') : t('navigation.shared.streams')}
      />

      {!hasSpecialtyContext ? (
        <Card className="space-y-3">
          <p className="text-sm text-text-secondary">{t('academic.selectSpecialtyToContinueStreams')}</p>
          <Link to="/academic/specialties">
            <Button variant="secondary">{t('academic.actions.viewSpecialties')}</Button>
          </Link>
        </Card>
      ) : null}

      <Card className="space-y-4">
        <div className="grid gap-3 xl:grid-cols-[1.3fr_0.8fr_0.5fr_0.5fr_auto]">
          <FormField label={t('common.actions.search')}>
            <Input
              placeholder={t('academic.streams.searchPlaceholder')}
              value={search}
              onChange={(event) => setSearch(event.target.value)}
            />
          </FormField>
          <FormField label={t('academic.specialties.specialty')}>
            <Select
              disabled={hasSpecialtyContext}
              value={hasSpecialtyContext ? specialtyContextId : specialtyFilter}
              onChange={(event) => setSpecialtyFilter(event.target.value)}
            >
              <option value="">{t('academic.filters.allSpecialties')}</option>
              {(specialtiesQuery.data ?? []).map((specialty) => (
                <option key={specialty.id} value={specialty.id}>
                  {specialty.code} · {specialty.name}
                </option>
              ))}
            </Select>
          </FormField>
          <FormField label={t('academic.studyYear')}>
            <Select value={studyYearFilter} onChange={(event) => setStudyYearFilter(event.target.value)}>
              <option value="">{t('academic.filters.allStudyYears')}</option>
              {Array.from({ length: 8 }, (_, index) => index + 1).map((year) => (
                <option key={year} value={year}>
                  {t('academic.studyYearValue', { year })}
                </option>
              ))}
            </Select>
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
                  setForm({
                    ...initialFormState,
                    specialtyId: specialtyContextId,
                    studyYear: contextStudyYear,
                  })
                  setIsEditorOpen(true)
                  setFormError(null)
                  setFieldErrors({})
                }}
              >
                <Plus className="mr-2 h-4 w-4" />
                {t('academic.streams.add')}
              </Button>
            </div>
          ) : null}
        </div>
      </Card>

      {canManage && isEditorOpen ? (
        <Card className="space-y-4 border-accent/30">
          <PageHeader
            className="mb-0"
            description={editingItem ? t('academic.streams.editDescription') : t('academic.streams.createDescription')}
            title={editingItem ? t('academic.streams.edit') : t('academic.streams.add')}
          />
          <div className="grid gap-4 md:grid-cols-3">
            <FormField error={fieldErrors.name} label={t('common.labels.name')}>
              <Input
                value={form.name}
                onChange={(event) => setForm((current) => ({ ...current, name: event.target.value }))}
              />
            </FormField>
            <FormField error={fieldErrors.specialtyId} label={t('academic.specialties.specialty')}>
              <Select
                disabled={hasSpecialtyContext}
                value={hasSpecialtyContext ? specialtyContextId : form.specialtyId}
                onChange={(event) => setForm((current) => ({ ...current, specialtyId: event.target.value }))}
              >
                <option value="">{t('academic.validation.selectSpecialty')}</option>
                {(specialtiesQuery.data ?? []).map((specialty) => (
                  <option key={specialty.id} value={specialty.id}>
                    {specialty.code} · {specialty.name}
                  </option>
                ))}
              </Select>
            </FormField>
            <FormField error={fieldErrors.studyYear} label={t('academic.studyYear')}>
              <Input
                max={8}
                min={1}
                type="number"
                value={form.studyYear}
                onChange={(event) => setForm((current) => ({ ...current, studyYear: event.target.value }))}
              />
            </FormField>
          </div>
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

      {streamsQuery.isLoading || specialtiesQuery.isLoading ? <LoadingState /> : null}
      {streamsQuery.isError || specialtiesQuery.isError ? (
        <ErrorState
          description={t('education.academicStructureLoadFailed')}
          title={t('navigation.shared.streams')}
          onRetry={() => {
            void specialtiesQuery.refetch()
            void streamsQuery.refetch()
          }}
        />
      ) : null}
      {!streamsQuery.isLoading && !streamsQuery.isError && visibleItems.length === 0 ? (
        <EmptyState
          description={t('academic.streams.empty')}
          title={t('navigation.shared.streams')}
          action={canManage ? (
            <Button
              onClick={() => {
                setEditingItem(null)
                setForm({
                  ...initialFormState,
                  specialtyId: specialtyContextId,
                  studyYear: contextStudyYear,
                })
                setIsEditorOpen(true)
                setFormError(null)
                setFormRequestId(null)
                setFieldErrors({})
              }}
            >
              <Plus className="mr-2 h-4 w-4" />
              {t('academic.streams.add')}
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
                  <h2 className="text-lg font-semibold text-text-primary">{item.name}</h2>
                  <p className="text-sm text-text-secondary">
                    {specialtiesById.get(item.specialtyId)?.name ?? t('academic.specialties.specialtyUnknown')}
                    {' · '}
                    {t('academic.studyYearValue', { year: item.studyYear })}
                  </p>
                </div>
                <StatusBadge value={item.active ? 'ACTIVE' : 'ARCHIVED'} />
              </div>
              <div className="flex flex-wrap items-center gap-3 text-sm text-text-secondary">
                <span className="inline-flex items-center gap-1 rounded-full bg-surface-muted px-3 py-1">
                  <Users className="h-4 w-4 text-text-muted" />
                  {t('academic.streams.groupCount', {
                    count: streamGroupsQuery.data?.get(item.id) ?? 0,
                  })}
                </span>
              </div>
              {canManage ? (
                <div className="flex flex-wrap gap-2">
                  <Button
                    variant="secondary"
                    onClick={() => {
                      setEditingItem(item)
                      setIsEditorOpen(true)
                      setForm({
                        name: item.name,
                        specialtyId: item.specialtyId,
                        studyYear: String(item.studyYear),
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
  form: StreamFormState,
  t: (key: string, options?: Record<string, unknown>) => string,
) {
  if (!form.name.trim()) {
    return t('academic.validation.streamNameRequired')
  }
  if (!form.specialtyId) {
    return t('academic.validation.selectSpecialty')
  }
  if (!form.studyYear.trim()) {
    return t('academic.validation.enterStudyYear')
  }

  const studyYear = Number(form.studyYear)
  if (!Number.isInteger(studyYear) || studyYear < 1 || studyYear > 8) {
    return t('academic.validation.enterStudyYear')
  }

  return null
}
