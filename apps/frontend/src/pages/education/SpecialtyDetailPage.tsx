import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { Pencil, Plus, RotateCcw } from 'lucide-react'
import { useMemo, useState } from 'react'
import { useTranslation } from 'react-i18next'
import { Link, useLocation, useParams } from 'react-router-dom'

import { useAuth } from '@/features/auth/useAuth'
import { AccessDeniedPage } from '@/pages/shared/AccessDeniedPage'
import { curriculumPlanService, educationService, specialtyService, streamService } from '@/shared/api/services'
import { buildApiFieldErrors, getLocalizedApiErrorMessage, normalizeApiError } from '@/shared/lib/api-errors'
import { hasAnyRole } from '@/shared/lib/roles'
import type { GroupResponse } from '@/shared/types/api'
import { Button } from '@/shared/ui/Button'
import { Card } from '@/shared/ui/Card'
import { FormField } from '@/shared/ui/FormField'
import { Input } from '@/shared/ui/Input'
import { PageHeader } from '@/shared/ui/PageHeader'
import { SectionTabs } from '@/shared/ui/SectionTabs'
import { EmptyState, ErrorState, LoadingState } from '@/shared/ui/StateViews'
import { Textarea } from '@/shared/ui/Textarea'
import { Breadcrumbs } from '@/widgets/common/Breadcrumbs'
import { MetricCard } from '@/widgets/common/MetricCard'
import { StatusBadge } from '@/widgets/common/StatusBadge'

type DetailSection = 'overview' | 'studyYears' | 'streams' | 'curriculum' | 'groups'

interface SpecialtyFormState {
  code: string
  name: string
  description: string
}

export function SpecialtyDetailPage() {
  const { t } = useTranslation()
  const { roles } = useAuth()
  const { specialtyId = '' } = useParams()
  const location = useLocation()
  const queryClient = useQueryClient()
  const canRead = hasAnyRole(roles, ['OWNER', 'ADMIN', 'TEACHER'])
  const canManage = hasAnyRole(roles, ['OWNER', 'ADMIN'])
  const [activeSection, setActiveSection] = useState<DetailSection>('overview')
  const [isEditorOpen, setIsEditorOpen] = useState(false)
  const [form, setForm] = useState<SpecialtyFormState>({ code: '', name: '', description: '' })
  const [formError, setFormError] = useState<string | null>(null)
  const [formRequestId, setFormRequestId] = useState<string | null>(null)
  const [fieldErrors, setFieldErrors] = useState<Record<string, string>>({})

  const specialtyQuery = useQuery({
    queryKey: ['education', 'specialty', specialtyId],
    queryFn: () => specialtyService.getById(specialtyId),
    enabled: canRead && Boolean(specialtyId),
    staleTime: 1000 * 60 * 5,
  })
  const streamsQuery = useQuery({
    queryKey: ['education', 'streams', 'specialty-detail', specialtyId],
    queryFn: () => streamService.list({ specialtyId }),
    enabled: canRead && Boolean(specialtyId),
    staleTime: 1000 * 60 * 5,
  })
  const plansQuery = useQuery({
    queryKey: ['education', 'curriculum-plans', 'specialty-detail', specialtyId],
    queryFn: () => curriculumPlanService.list({ specialtyId }),
    enabled: canRead && Boolean(specialtyId),
    staleTime: 1000 * 60 * 5,
  })
  const groupsQuery = useQuery({
    queryKey: ['education', 'groups', 'specialty-detail', specialtyId],
    queryFn: () => loadGroupsBySpecialty(specialtyId),
    enabled: canRead && Boolean(specialtyId),
    staleTime: 1000 * 60 * 5,
  })
  const subjectsQuery = useQuery({
    queryKey: ['education', 'subjects', 'specialty-detail'],
    queryFn: () => educationService.listSubjects({
      page: 0,
      size: 200,
      sortBy: 'name',
      direction: 'asc',
    }),
    enabled: canRead,
    staleTime: 1000 * 60 * 5,
  })
  const groupCountsQuery = useQuery({
    queryKey: ['education', 'group-student-counts', specialtyId, (groupsQuery.data ?? []).map((group) => group.id).join(',')],
    queryFn: async () => {
      const entries = await Promise.all((groupsQuery.data ?? []).map(async (group) => {
        const students = await educationService.listGroupStudents(group.id)
        return [group.id, students.length] as const
      }))
      return new Map(entries)
    },
    enabled: (groupsQuery.data?.length ?? 0) > 0,
    staleTime: 1000 * 60 * 3,
  })

  const updateMutation = useMutation({
    mutationFn: (payload: SpecialtyFormState) => specialtyService.update(specialtyId, {
      code: payload.code.trim(),
      name: payload.name.trim(),
      description: payload.description.trim() || null,
    }),
    onSuccess: async () => {
      await queryClient.invalidateQueries({ queryKey: ['education', 'specialty', specialtyId] })
      await queryClient.invalidateQueries({ queryKey: ['education', 'specialties'] })
      setIsEditorOpen(false)
      setFormError(null)
      setFormRequestId(null)
      setFieldErrors({})
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
    mutationFn: () => specialtyService.archive(specialtyId),
    onSuccess: async () => {
      await queryClient.invalidateQueries({ queryKey: ['education', 'specialty', specialtyId] })
      await queryClient.invalidateQueries({ queryKey: ['education', 'specialties'] })
    },
  })
  const restoreMutation = useMutation({
    mutationFn: () => specialtyService.restore(specialtyId),
    onSuccess: async () => {
      await queryClient.invalidateQueries({ queryKey: ['education', 'specialty', specialtyId] })
      await queryClient.invalidateQueries({ queryKey: ['education', 'specialties'] })
    },
  })

  const subjectsById = useMemo(
    () => new Map((subjectsQuery.data?.items ?? []).map((subject) => [subject.id, subject])),
    [subjectsQuery.data?.items],
  )
  const groups = useMemo(() => groupsQuery.data ?? [], [groupsQuery.data])
  const streams = useMemo(() => streamsQuery.data ?? [], [streamsQuery.data])
  const plans = useMemo(() => plansQuery.data ?? [], [plansQuery.data])
  const streamById = new Map(streams.map((stream) => [stream.id, stream]))
  const yearSummaries = useMemo(() => {
    const values = new Map<number, { groups: number; streams: number; plans: number }>()
    for (const stream of streams) {
      const current = values.get(stream.studyYear) ?? { groups: 0, streams: 0, plans: 0 }
      current.streams += 1
      values.set(stream.studyYear, current)
    }
    for (const plan of plans) {
      const current = values.get(plan.studyYear) ?? { groups: 0, streams: 0, plans: 0 }
      current.plans += 1
      values.set(plan.studyYear, current)
    }
    for (const group of groups) {
      if (!group.studyYear) {
        continue
      }
      const current = values.get(group.studyYear) ?? { groups: 0, streams: 0, plans: 0 }
      current.groups += 1
      values.set(group.studyYear, current)
    }
    return Array.from(values.entries())
      .map(([studyYear, counters]) => ({ studyYear, ...counters }))
      .sort((left, right) => left.studyYear - right.studyYear)
  }, [groups, plans, streams])

  if (!canRead) {
    return <AccessDeniedPage />
  }

  if (specialtyQuery.isLoading || streamsQuery.isLoading || plansQuery.isLoading || groupsQuery.isLoading || subjectsQuery.isLoading) {
    return <LoadingState />
  }

  if (specialtyQuery.isError || streamsQuery.isError || plansQuery.isError || groupsQuery.isError || subjectsQuery.isError) {
    return (
      <ErrorState
        description={t('education.academicStructureLoadFailed')}
        title={t('academic.specialties.detailTitle')}
      />
    )
  }

  if (!specialtyQuery.data) {
    return <ErrorState description={t('common.states.notFound')} title={t('academic.specialties.detailTitle')} />
  }

  const specialty = specialtyQuery.data
  const effectiveTab = resolveActiveSection(location.pathname, activeSection)

  return (
    <div className="space-y-6">
      <Breadcrumbs
        items={[
          { label: t('navigation.groups.academicManagement'), to: '/academic' },
          { label: t('navigation.shared.specialties'), to: '/academic/specialties' },
          { label: specialty.name },
        ]}
      />

      <PageHeader
        description={t('academic.specialties.detailDescription')}
        title={`${specialty.code} · ${specialty.name}`}
        actions={canManage ? (
          <div className="flex flex-wrap gap-2">
            <Button
              variant="secondary"
              onClick={() => {
                setIsEditorOpen(true)
                setForm({
                  code: specialty.code,
                  name: specialty.name,
                  description: specialty.description ?? '',
                })
                setFormError(null)
                setFormRequestId(null)
                setFieldErrors({})
              }}
            >
              <Pencil className="mr-2 h-4 w-4" />
              {t('common.actions.edit')}
            </Button>
            {specialty.active ? (
              <Button disabled={archiveMutation.isPending} variant="ghost" onClick={() => archiveMutation.mutate()}>
                {t('academic.common.archive')}
              </Button>
            ) : (
              <Button disabled={restoreMutation.isPending} variant="ghost" onClick={() => restoreMutation.mutate()}>
                <RotateCcw className="mr-2 h-4 w-4" />
                {t('academic.common.restore')}
              </Button>
            )}
          </div>
        ) : null}
      />

      {isEditorOpen ? (
        <Card className="space-y-4 border-accent/30">
          <div className="grid gap-4 md:grid-cols-2">
            <FormField error={fieldErrors.code} label={t('academic.specialties.code')}>
              <Input value={form.code} onChange={(event) => setForm((current) => ({ ...current, code: event.target.value }))} />
            </FormField>
            <FormField error={fieldErrors.name} label={t('academic.specialties.name')}>
              <Input value={form.name} onChange={(event) => setForm((current) => ({ ...current, name: event.target.value }))} />
            </FormField>
          </div>
          <FormField error={fieldErrors.description} label={t('common.labels.description')}>
            <Textarea rows={3} value={form.description} onChange={(event) => setForm((current) => ({ ...current, description: event.target.value }))} />
          </FormField>
          {formError ? (
            <div className="rounded-[14px] border border-danger/20 bg-danger/5 px-3 py-2 text-sm text-text-primary">
              <p>{formError}</p>
              {formRequestId ? (
                <p className="mt-1 text-xs text-text-muted">{t('common.labels.requestId')}: {formRequestId}</p>
              ) : null}
            </div>
          ) : null}
          <div className="flex flex-wrap gap-3">
            <Button
              disabled={!form.code.trim() || !form.name.trim() || updateMutation.isPending}
              onClick={() => updateMutation.mutate(form)}
            >
              {t('common.actions.save')}
            </Button>
            <Button variant="secondary" onClick={() => setIsEditorOpen(false)}>
              {t('common.actions.cancel')}
            </Button>
          </div>
        </Card>
      ) : null}

      <Card className="space-y-4">
        <div className="flex flex-wrap items-start justify-between gap-3">
          <div className="space-y-2">
            <p className="text-xs font-semibold uppercase tracking-[0.16em] text-text-muted">{t('academic.specialties.code')}</p>
            <p className="text-lg font-semibold text-text-primary">{specialty.code}</p>
            <p className="text-sm text-text-secondary">{specialty.description || t('academic.specialties.noDescription')}</p>
          </div>
          <StatusBadge value={specialty.active ? 'ACTIVE' : 'ARCHIVED'} />
        </div>
      </Card>

      <SectionTabs
        activeId={effectiveTab}
        items={[
          { id: 'overview', label: t('academic.sections.overview') },
          { id: 'studyYears', label: t('academic.sections.studyYears') },
          { id: 'streams', label: t('academic.sections.streams') },
          { id: 'curriculum', label: t('academic.sections.curriculum') },
          { id: 'groups', label: t('academic.sections.groups') },
        ]}
        onChange={(tabId) => setActiveSection(tabId as DetailSection)}
      />

      {effectiveTab === 'overview' ? (
        <div className="grid gap-4 md:grid-cols-2 xl:grid-cols-4">
          <MetricCard title={t('academic.sections.studyYears')} value={yearSummaries.length} />
          <MetricCard title={t('navigation.shared.streams')} value={streams.length} />
          <MetricCard title={t('navigation.shared.curriculumPlans')} value={plans.length} />
          <MetricCard title={t('navigation.shared.groups')} value={groups.length} />
        </div>
      ) : null}

      {effectiveTab === 'studyYears' ? (
        yearSummaries.length === 0 ? (
          <EmptyState description={t('academic.studyYears.empty')} title={t('academic.sections.studyYears')} />
        ) : (
          <div className="grid gap-3 md:grid-cols-2 xl:grid-cols-3">
            {yearSummaries.map((item) => (
              <Card key={item.studyYear} className="space-y-3">
                <h3 className="text-lg font-semibold text-text-primary">{t('academic.studyYearValue', { year: item.studyYear })}</h3>
                <div className="grid gap-2 text-sm text-text-secondary">
                  <p>{t('navigation.shared.groups')}: <span className="font-semibold text-text-primary">{item.groups}</span></p>
                  <p>{t('navigation.shared.streams')}: <span className="font-semibold text-text-primary">{item.streams}</span></p>
                  <p>{t('navigation.shared.curriculumPlans')}: <span className="font-semibold text-text-primary">{item.plans}</span></p>
                </div>
                <div className="flex flex-wrap gap-2">
                  <Link to={`/academic/specialties/${specialtyId}/streams?studyYear=${item.studyYear}`}>
                    <Button variant="secondary">
                      <Plus className="mr-2 h-4 w-4" />
                      {t('academic.actions.createStreamInSpecialty')}
                    </Button>
                  </Link>
                  <Link to={`/academic/specialties/${specialtyId}/curriculum-plans?studyYear=${item.studyYear}`}>
                    <Button variant="secondary">
                      <Plus className="mr-2 h-4 w-4" />
                      {t('academic.actions.createCurriculumInSpecialty')}
                    </Button>
                  </Link>
                  <Link to={`/academic/specialties/${specialtyId}/groups?studyYear=${item.studyYear}`}>
                    <Button variant="ghost">{t('academic.actions.viewGroups')}</Button>
                  </Link>
                </div>
              </Card>
            ))}
          </div>
        )
      ) : null}

      {effectiveTab === 'streams' ? (
        streams.length === 0 ? (
          <EmptyState description={t('academic.streams.emptyForSpecialty')} title={t('navigation.shared.streams')} />
        ) : (
          <div className="space-y-3">
            {streams.map((stream) => (
              <Card key={stream.id} className="space-y-2">
                <div className="flex flex-wrap items-center justify-between gap-3">
                  <div>
                    <h3 className="text-base font-semibold text-text-primary">{stream.name}</h3>
                    <p className="text-sm text-text-secondary">{t('academic.studyYearValue', { year: stream.studyYear })}</p>
                  </div>
                  <StatusBadge value={stream.active ? 'ACTIVE' : 'ARCHIVED'} />
                </div>
              </Card>
            ))}
            <div>
              <Link to={`/academic/specialties/${specialtyId}/streams`}>
                <Button variant="secondary">{t('academic.actions.viewStreams')}</Button>
              </Link>
            </div>
          </div>
        )
      ) : null}

      {effectiveTab === 'curriculum' ? (
        plans.length === 0 ? (
          <EmptyState description={t('academic.curriculum.emptyForSpecialty')} title={t('navigation.shared.curriculumPlans')} />
        ) : (
          <div className="space-y-3">
            {plans.map((plan) => (
              <Card key={plan.id} className="space-y-2">
                <h3 className="text-base font-semibold text-text-primary">
                  {subjectsById.get(plan.subjectId)?.name ?? t('academic.curriculum.subjectUnknown')}
                </h3>
                <p className="text-sm text-text-secondary">
                  {t('academic.studyYearValue', { year: plan.studyYear })}
                  {' · '}
                  {t('academic.curriculum.semesterValue', { semester: plan.semesterNumber })}
                </p>
                <p className="text-sm text-text-secondary">
                  {t('academic.curriculum.lectureCount')}: {plan.lectureCount}
                  {' · '}
                  {t('academic.curriculum.practiceCount')}: {plan.practiceCount}
                  {' · '}
                  {t('academic.curriculum.labCount')}: {plan.labCount}
                </p>
              </Card>
            ))}
            <div>
              <Link to={`/academic/specialties/${specialtyId}/curriculum-plans`}>
                <Button variant="secondary">{t('academic.actions.viewCurriculum')}</Button>
              </Link>
            </div>
          </div>
        )
      ) : null}

      {effectiveTab === 'groups' ? (
        groups.length === 0 ? (
          <EmptyState description={t('academic.groups.emptyForSpecialty')} title={t('navigation.shared.groups')} />
        ) : (
          <div className="grid gap-3">
            {groups.map((group) => {
              const stream = group.streamId ? streamById.get(group.streamId) : null
              return (
                <Card key={group.id} className="space-y-3">
                  <div className="flex flex-wrap items-start justify-between gap-3">
                    <div className="space-y-1">
                      <h3 className="text-base font-semibold text-text-primary">{group.name}</h3>
                      <p className="text-sm text-text-secondary">
                        {group.studyYear ? t('academic.studyYearValue', { year: group.studyYear }) : t('academic.groupSettings.noStudyYear')}
                        {' · '}
                        {stream?.name ?? t('academic.groupSettings.noStream')}
                      </p>
                    </div>
                    <span className="rounded-full bg-surface-muted px-3 py-1 text-xs font-semibold text-text-primary">
                      {t(`academic.subgroupMode.${group.subgroupMode ?? 'NONE'}`)}
                    </span>
                  </div>
                  <p className="text-sm text-text-secondary">
                    {t('education.overview.students')}: {groupCountsQuery.data?.get(group.id) ?? '—'}
                  </p>
                  <div className="flex flex-wrap gap-2">
                    <Link to={`/academic/groups/${group.id}`}>
                      <Button variant="secondary">{t('academic.actions.viewGroups')}</Button>
                    </Link>
                    {canManage ? (
                      <>
                        <Link to={`/academic/groups/${group.id}?tab=settings`}>
                          <Button variant="ghost">{t('academic.actions.assignToStream')}</Button>
                        </Link>
                        <Link to={`/academic/groups/${group.id}?tab=settings`}>
                          <Button variant="ghost">{t('academic.actions.viewResolvedSubjects')}</Button>
                        </Link>
                      </>
                    ) : null}
                  </div>
                </Card>
              )
            })}
          </div>
        )
      ) : null}
    </div>
  )
}

async function loadGroupsBySpecialty(specialtyId: string) {
  const groups: GroupResponse[] = []
  let page = 0
  while (true) {
    const response = await educationService.listGroups({
      page,
      size: 100,
      sortBy: 'name',
      direction: 'asc',
    })
    groups.push(...response.items.filter((group) => group.specialtyId === specialtyId))
    if (response.last || page + 1 >= response.totalPages) {
      break
    }
    page += 1
  }
  return groups
}

function resolveActiveSection(pathname: string, selectedTab: DetailSection): DetailSection {
  if (pathname.endsWith('/streams')) {
    return 'streams'
  }
  if (pathname.endsWith('/curriculum-plans')) {
    return 'curriculum'
  }
  if (pathname.endsWith('/groups')) {
    return 'groups'
  }
  return selectedTab
}
