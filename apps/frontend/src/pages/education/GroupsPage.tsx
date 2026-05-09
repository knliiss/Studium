import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { FolderKanban, Plus, Users } from 'lucide-react'
import { useMemo, useState } from 'react'
import { useTranslation } from 'react-i18next'
import { Link, useLocation, useParams, useSearchParams } from 'react-router-dom'

import { useAuth } from '@/features/auth/useAuth'
import type { GroupCardSummary } from '@/pages/education/helpers'
import { loadAccessibleGroups, loadGroupCardSummaries } from '@/pages/education/helpers'
import { educationService, specialtyService, streamService } from '@/shared/api/services'
import { formatDate } from '@/shared/lib/format'
import { hasAnyRole } from '@/shared/lib/roles'
import type { GroupResponse } from '@/shared/types/api'
import { Button } from '@/shared/ui/Button'
import { Card } from '@/shared/ui/Card'
import { FormField } from '@/shared/ui/FormField'
import { Input } from '@/shared/ui/Input'
import { PageHeader } from '@/shared/ui/PageHeader'
import { EmptyState, ErrorState, LoadingState } from '@/shared/ui/StateViews'
import { Breadcrumbs } from '@/widgets/common/Breadcrumbs'
import { RiskBadge } from '@/widgets/common/RiskBadge'

export function GroupsPage() {
  const { primaryRole, roles, session } = useAuth()
  const location = useLocation()
  const { specialtyId: routeSpecialtyId } = useParams()
  const [searchParams] = useSearchParams()
  const canManageGroups = hasAnyRole(roles, ['ADMIN', 'OWNER'])

  if (canManageGroups) {
    return <ManagedGroupsPage />
  }

  return (
    <AccessibleGroupsPage
      role={primaryRole}
      userId={session?.user.id ?? ''}
      specialtyId={routeSpecialtyId ?? ''}
      studyYear={searchParams.get('studyYear') ? Number(searchParams.get('studyYear')) : undefined}
      academicMode={location.pathname.startsWith('/academic')}
    />
  )
}

function ManagedGroupsPage() {
  const { t } = useTranslation()
  const { specialtyId: routeSpecialtyId } = useParams()
  const [searchParams] = useSearchParams()
  const queryClient = useQueryClient()
  const [query, setQuery] = useState('')
  const [page, setPage] = useState(0)
  const [groupName, setGroupName] = useState('')
  const specialtyContextId = routeSpecialtyId ?? ''
  const studyYearContext = searchParams.get('studyYear')
  const hasSpecialtyContext = Boolean(specialtyContextId)
  const normalizedQuery = query.trim()

  const groupsQuery = useQuery({
    queryKey: ['education', 'groups', 'managed', specialtyContextId, studyYearContext ?? '', normalizedQuery, page],
    queryFn: () => hasSpecialtyContext
      ? loadAllGroups({
          specialtyId: specialtyContextId,
          studyYear: studyYearContext ? Number(studyYearContext) : undefined,
          query: normalizedQuery,
        })
      : educationService.listGroups({
          page,
          size: 12,
          q: normalizedQuery || undefined,
          sortBy: 'name',
          direction: 'asc',
        }),
    staleTime: 1000 * 60 * 5,
  })
  const specialtiesQuery = useQuery({
    queryKey: ['education', 'specialties', 'groups-page'],
    queryFn: () => specialtyService.list({ active: true }),
    enabled: hasSpecialtyContext,
    staleTime: 1000 * 60 * 5,
  })
  const streamsQuery = useQuery({
    queryKey: ['education', 'streams', 'groups-page', specialtyContextId],
    queryFn: () => specialtyContextId ? streamService.list({ specialtyId: specialtyContextId }) : Promise.resolve([]),
    enabled: hasSpecialtyContext,
    staleTime: 1000 * 60 * 5,
  })
  const createMutation = useMutation({
    mutationFn: () => educationService.createGroup({ name: groupName.trim() }),
    onSuccess: async () => {
      setGroupName('')
      setPage(0)
      await queryClient.invalidateQueries({ queryKey: ['education', 'groups'] })
    },
  })

  const groups = useMemo(
    () => groupsQuery.data?.items ?? [],
    [groupsQuery.data?.items],
  )
  const unassignedGroups = useMemo(
    () => hasSpecialtyContext ? [] : groups.filter((group) => !group.specialtyId || !group.studyYear),
    [groups, hasSpecialtyContext],
  )
  const groupSummariesQuery = useQuery({
    queryKey: ['education', 'group-card-summaries', groups.map((group) => group.id).join(',')],
    queryFn: () => loadGroupCardSummaries(groups.map((group) => group.id)),
    enabled: groups.length > 0,
    staleTime: 1000 * 60 * 3,
  })
  const specialtyName = specialtiesQuery.data?.find((specialty) => specialty.id === specialtyContextId)?.name ?? null
  const streamById = new Map((streamsQuery.data ?? []).map((stream) => [stream.id, stream]))

  return (
    <div className="space-y-6">
      <Breadcrumbs
        items={[
          { label: t('navigation.groups.academicManagement'), to: '/academic' },
          { label: t('navigation.shared.groups') },
        ]}
      />
      <PageHeader
        description={hasSpecialtyContext ? t('academic.groups.forSpecialtyDescription') : t('education.groupsLandingDescription')}
        title={hasSpecialtyContext
          ? t('academic.groups.forSpecialtyTitle', {
              specialty: specialtyName ?? t('academic.specialties.specialtyUnknown'),
              studyYear: studyYearContext ? t('academic.studyYearValue', { year: Number(studyYearContext) }) : t('academic.studyYears.allYears'),
            })
          : t('navigation.shared.groups')}
      />

      <Card className="space-y-4">
        <div className="flex flex-col gap-3 lg:flex-row lg:items-end lg:justify-between">
          <FormField label={t('common.actions.search')}>
            <Input
              placeholder={t('education.groupSearchPlaceholder')}
              value={query}
              onChange={(event) => {
                setQuery(event.target.value)
                setPage(0)
              }}
            />
          </FormField>
          {!hasSpecialtyContext ? (
            <div className="rounded-[16px] border border-border bg-surface-muted p-4">
              <div className="flex items-center gap-3">
                <span className="inline-flex h-10 w-10 items-center justify-center rounded-[14px] bg-accent-muted text-accent">
                  <Plus className="h-5 w-5" />
                </span>
                <div className="min-w-0">
                  <p className="text-sm font-semibold text-text-primary">{t('education.createGroup')}</p>
                  <p className="text-xs text-text-muted">{t('education.createGroupDescription')}</p>
                </div>
              </div>
              <div className="mt-4 flex flex-col gap-3 sm:flex-row">
                <Input
                  placeholder={t('common.labels.name')}
                  value={groupName}
                  onChange={(event) => setGroupName(event.target.value)}
                />
                <Button
                  disabled={!groupName.trim() || createMutation.isPending}
                  onClick={() => createMutation.mutate()}
                >
                  {t('common.actions.create')}
                </Button>
              </div>
            </div>
          ) : (
            <div className="rounded-[16px] border border-border bg-surface-muted p-4 text-sm text-text-secondary">
              {t('academic.groups.specialtyContextHint')}
            </div>
          )}
        </div>
      </Card>

      {groupsQuery.isLoading ? <LoadingState /> : null}
      {groupsQuery.isError ? <ErrorState description={t('common.states.error')} title={t('navigation.shared.groups')} /> : null}
      {!groupsQuery.isLoading && !groupsQuery.isError && groups.length === 0 ? (
        <EmptyState description={t('education.emptyGroupsSearch')} title={t('navigation.shared.groups')} />
      ) : null}
      {groups.length > 0 ? (
        <>
          <div className="grid gap-3">
            {groups.map((group) => (
              <GroupCard
                key={group.id}
                createdAt={group.createdAt}
                name={group.name}
                summary={groupSummariesQuery.data?.get(group.id)}
                meta={resolveGroupMeta(group, streamById, t)}
                to={`/academic/groups/${group.id}`}
              />
            ))}
          </div>
          {!hasSpecialtyContext ? (
            <div className="flex items-center justify-between gap-3">
              <Button
                disabled={page <= 0}
                variant="secondary"
                onClick={() => setPage((current) => Math.max(0, current - 1))}
              >
                {t('common.actions.previous')}
              </Button>
              <span className="text-sm font-medium text-text-secondary">
                {t('common.labels.page')} {page + 1}
              </span>
              <Button
                disabled={groupsQuery.data?.last ?? true}
                variant="secondary"
                onClick={() => setPage((current) => current + 1)}
              >
                {t('common.actions.next')}
              </Button>
            </div>
          ) : null}
        </>
      ) : null}

      {!hasSpecialtyContext ? (
        <Card className="space-y-3">
          <PageHeader
            className="mb-0"
            description={t('academic.groups.unassignedDescription')}
            title={t('academic.groups.unassignedTitle')}
          />
          {unassignedGroups.length === 0 ? (
            <p className="text-sm text-text-secondary">{t('academic.groups.unassignedEmpty')}</p>
          ) : (
            <div className="grid gap-2">
              {unassignedGroups.map((group) => (
                <Link key={group.id} className="text-sm text-accent" to={`/academic/groups/${group.id}`}>
                  {group.name}
                </Link>
              ))}
            </div>
          )}
        </Card>
      ) : null}
    </div>
  )
}

function AccessibleGroupsPage({
  academicMode,
  role,
  specialtyId,
  studyYear,
  userId,
}: {
  academicMode: boolean
  role: 'TEACHER' | 'STUDENT' | 'USER' | 'ADMIN' | 'OWNER'
  specialtyId?: string
  studyYear?: number
  userId: string
}) {
  const { t } = useTranslation()
  const [query, setQuery] = useState('')
  const groupsQuery = useQuery({
    queryKey: ['education', 'groups', 'accessible', role, userId],
    queryFn: () => loadAccessibleGroups(role, userId),
    enabled: Boolean(userId),
    staleTime: 1000 * 60 * 3,
  })
  const filteredGroups = useMemo(
    () => (groupsQuery.data ?? []).filter((group) => {
      if (specialtyId && group.specialtyId !== specialtyId) {
        return false
      }
      if (typeof studyYear === 'number' && group.studyYear !== studyYear) {
        return false
      }
      return query.trim() ? group.name.toLowerCase().includes(query.trim().toLowerCase()) : true
    }),
    [groupsQuery.data, query, specialtyId, studyYear],
  )
  const groupSummariesQuery = useQuery({
    queryKey: ['education', 'group-card-summaries', filteredGroups.map((group) => group.id).join(',')],
    queryFn: () => loadGroupCardSummaries(filteredGroups.map((group) => group.id)),
    enabled: filteredGroups.length > 0,
    staleTime: 1000 * 60 * 3,
  })

  if (groupsQuery.isLoading) {
    return <LoadingState />
  }

  if (groupsQuery.isError) {
    return <ErrorState description={t('common.states.error')} title={t('navigation.shared.groups')} />
  }

  return (
    <div className="space-y-6">
      <Breadcrumbs
        items={[
          { label: academicMode ? t('navigation.groups.academicManagement') : t('navigation.shared.education'), to: academicMode ? '/academic' : '/education' },
          { label: t('navigation.shared.groups') },
        ]}
      />
      <PageHeader
        description={specialtyId
          ? t('academic.groups.forSpecialtyDescription')
          : (role === 'TEACHER' ? t('education.teacherGroupsDescription') : t('education.studentGroupsDescription'))}
        title={t('navigation.shared.groups')}
      />

      <Card>
        <FormField label={t('common.actions.search')}>
          <Input
            placeholder={t('education.groupSearchPlaceholder')}
            value={query}
            onChange={(event) => setQuery(event.target.value)}
          />
        </FormField>
      </Card>

      {filteredGroups.length === 0 ? (
        <EmptyState
          description={t('education.noGroups')}
          title={t('navigation.shared.groups')}
        />
      ) : (
        <div className="grid gap-3">
          {filteredGroups.map((group) => (
            <GroupCard
              key={group.id}
              createdAt={group.createdAt}
              name={group.name}
              summary={groupSummariesQuery.data?.get(group.id)}
              meta={null}
              to={academicMode ? `/academic/groups/${group.id}` : `/groups/${group.id}`}
            />
          ))}
        </div>
      )}
    </div>
  )
}

function GroupCard({
  createdAt,
  meta,
  name,
  summary,
  to,
}: {
  createdAt: string
  meta?: string | null
  name: string
  summary?: GroupCardSummary
  to: string
}) {
  const { t } = useTranslation()

  return (
    <Link to={to} className="group block h-full">
      <Card className="flex h-full flex-col justify-between gap-4 transition hover:-translate-y-0.5 hover:border-accent/40 hover:shadow-[var(--shadow-soft)] xl:flex-row xl:items-center">
        <div className="min-w-0 flex-1 space-y-4">
          <div className="flex items-start gap-3">
            <span className="inline-flex h-11 w-11 shrink-0 items-center justify-center rounded-[16px] bg-accent-muted text-accent">
              <FolderKanban className="h-5 w-5" />
            </span>
            <div className="min-w-0">
              <h2 className="break-words text-lg font-semibold text-text-primary">{name}</h2>
              <p className="text-sm text-text-muted">{meta ?? t('education.groupHubLabel')}</p>
            </div>
          </div>
          <div className="grid gap-3 md:grid-cols-2">
            <div className="rounded-[16px] border border-border bg-surface-muted px-4 py-3">
              <p className="text-xs font-semibold uppercase tracking-[0.16em] text-text-muted">
                {t('education.overview.students')}
              </p>
              <p className="mt-2 text-lg font-semibold text-text-primary">
                {summary?.studentCount ?? '—'}
              </p>
              <p className="mt-1 text-sm text-text-secondary">
                {t('education.groupStudentCountSummary')}
              </p>
            </div>
            <div className="rounded-[16px] border border-border bg-surface-muted px-4 py-3">
              <p className="text-xs font-semibold uppercase tracking-[0.16em] text-text-muted">
                {t('analytics.riskLevel.label')}
              </p>
              <div className="mt-2">
                {summary?.riskLevel ? (
                  <RiskBadge value={summary.riskLevel} />
                ) : (
                  <p className="text-sm text-text-secondary">{t('education.groupRiskUnknown')}</p>
                )}
              </div>
            </div>
          </div>
          <div className="grid gap-2 text-sm text-text-secondary">
            <p>{resolveScheduleStatusLabel(summary?.hasUpcomingLessons, t)}</p>
            <p>{t('education.createdAt')}: {formatDate(createdAt)}</p>
          </div>
        </div>
        <span className="inline-flex min-h-11 shrink-0 items-center justify-center rounded-[14px] border border-border bg-surface-muted px-4 text-sm font-medium text-text-primary transition group-hover:border-border-strong">
          <Users className="mr-2 h-4 w-4" />
          {t('common.actions.open')}
        </span>
      </Card>
    </Link>
  )
}

function resolveGroupMeta(
  group: GroupResponse,
  streamById: Map<string, { name: string }>,
  t: (key: string, options?: Record<string, unknown>) => string,
) {
  const studyYearLabel = group.studyYear ? t('academic.studyYearValue', { year: group.studyYear }) : t('academic.groupSettings.noStudyYear')
  const streamLabel = group.streamId ? (streamById.get(group.streamId)?.name ?? t('academic.groupSettings.noStream')) : t('academic.groupSettings.noStream')
  return `${studyYearLabel} · ${streamLabel}`
}

async function loadAllGroups(options: { specialtyId?: string; studyYear?: number; query?: string }) {
  const items: GroupResponse[] = []
  let page = 0

  while (true) {
    const pageResponse = await educationService.listGroups({
      page,
      size: 100,
      q: options.query || undefined,
      sortBy: 'name',
      direction: 'asc',
    })
    items.push(...pageResponse.items)
    if (pageResponse.last || page + 1 >= pageResponse.totalPages) {
      break
    }
    page += 1
  }

  const filtered = items.filter((group) => {
    if (options.specialtyId && group.specialtyId !== options.specialtyId) {
      return false
    }
    if (typeof options.studyYear === 'number' && group.studyYear !== options.studyYear) {
      return false
    }
    return true
  })

  return {
    items: filtered,
    page: 0,
    size: filtered.length,
    totalElements: filtered.length,
    totalPages: 1,
    first: true,
    last: true,
  }
}

function resolveScheduleStatusLabel(
  hasUpcomingLessons: boolean | null | undefined,
  t: (key: string) => string,
) {
  if (hasUpcomingLessons === true) {
    return t('education.groupScheduleStatusPlanned')
  }

  if (hasUpcomingLessons === false) {
    return t('education.groupScheduleStatusEmpty')
  }

  return t('education.groupScheduleStatusUnknown')
}
