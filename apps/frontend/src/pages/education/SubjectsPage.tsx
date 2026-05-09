import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { BookOpen, GraduationCap, Plus, SlidersHorizontal, Users, X } from 'lucide-react'
import { useMemo, useState } from 'react'
import { useTranslation } from 'react-i18next'
import { Link, useNavigate } from 'react-router-dom'

import { useAuth } from '@/features/auth/useAuth'
import type { SubjectCardMetrics } from '@/pages/education/helpers'
import { loadAccessibleSubjects, loadStudentScopedSubjects, loadSubjectCardMetrics } from '@/pages/education/helpers'
import { adminUserService, educationService, userDirectoryService } from '@/shared/api/services'
import { hasAnyRole } from '@/shared/lib/roles'
import type { AdminUserResponse, SubjectResponse, UserSummaryResponse } from '@/shared/types/api'
import { Button } from '@/shared/ui/Button'
import { Card } from '@/shared/ui/Card'
import { FormField } from '@/shared/ui/FormField'
import { Input } from '@/shared/ui/Input'
import { PageHeader } from '@/shared/ui/PageHeader'
import { Select } from '@/shared/ui/Select'
import { EmptyState, ErrorState, LoadingState } from '@/shared/ui/StateViews'
import { Textarea } from '@/shared/ui/Textarea'
import { Breadcrumbs } from '@/widgets/common/Breadcrumbs'

type SubjectTeacherSummary = AdminUserResponse | UserSummaryResponse

export function SubjectsPage() {
  const { primaryRole, roles, session } = useAuth()

  if (hasAnyRole(roles, ['ADMIN', 'OWNER'])) {
    return <ManagedSubjectsPage />
  }

  return <AccessibleSubjectsPage role={primaryRole} userId={session?.user.id ?? ''} />
}

function ManagedSubjectsPage() {
  const { t } = useTranslation()
  const navigate = useNavigate()
  const queryClient = useQueryClient()
  const [query, setQuery] = useState('')
  const [page, setPage] = useState(0)
  const [groupSearch, setGroupSearch] = useState('')
  const [teacherSearch, setTeacherSearch] = useState('')
  const [selectedFilterGroupId, setSelectedFilterGroupId] = useState('')
  const [selectedFilterTeacherId, setSelectedFilterTeacherId] = useState('')
  const [filtersOpen, setFiltersOpen] = useState(false)
  const [form, setForm] = useState({ name: '', description: '' })
  const normalizedQuery = query.trim()

  const subjectsQuery = useQuery({
    queryKey: ['education', 'subjects', 'managed', normalizedQuery, page],
    queryFn: () => educationService.listSubjects({
      page,
      size: 12,
      q: normalizedQuery || undefined,
      sortBy: 'name',
      direction: 'asc',
    }),
  })
  const groupsQuery = useQuery({
    queryKey: ['education', 'groups', 'subject-picker', groupSearch],
    queryFn: () => educationService.listGroups({
      page: 0,
      size: 12,
      q: groupSearch.trim() || undefined,
      sortBy: 'name',
      direction: 'asc',
    }),
  })
  const teachersQuery = useQuery({
    queryKey: ['education', 'teachers', 'subject-picker', teacherSearch],
    queryFn: () => adminUserService.list({
      page: 0,
      size: 12,
      role: 'TEACHER',
      search: teacherSearch.trim() || undefined,
      sortBy: 'username',
      direction: 'asc',
    }),
  })
  const teacherIds = useMemo(
    () => Array.from(new Set((subjectsQuery.data?.items ?? []).flatMap((subject) => subject.teacherIds))),
    [subjectsQuery.data?.items],
  )
  const subjectTeachersQuery = useQuery({
    queryKey: ['education', 'subject-teachers', teacherIds.join(',')],
    queryFn: () => userDirectoryService.lookup(teacherIds),
    enabled: teacherIds.length > 0,
  })
  const createMutation = useMutation({
    mutationFn: () => educationService.createSubject({
      name: form.name.trim(),
      description: form.description.trim(),
    }),
    onSuccess: async (subject) => {
      await queryClient.invalidateQueries({ queryKey: ['education', 'subjects'] })
      navigate(`/subjects/${subject.id}`)
    },
  })

  const groupNameById = useMemo(
    () => new Map((groupsQuery.data?.items ?? []).map((group) => [group.id, group.name])),
    [groupsQuery.data?.items],
  )
  const teacherById = useMemo(
    () => new Map([
      ...(teachersQuery.data?.content ?? []).map((teacher) => [teacher.id, teacher] as const),
      ...(subjectTeachersQuery.data ?? []).map((teacher) => [teacher.id, teacher] as const),
    ]),
    [subjectTeachersQuery.data, teachersQuery.data?.content],
  )
  const subjects = useMemo(
    () => (subjectsQuery.data?.items ?? []).filter((subject) => {
      if (selectedFilterGroupId && !subject.groupIds.includes(selectedFilterGroupId)) {
        return false
      }
      if (selectedFilterTeacherId && !subject.teacherIds.includes(selectedFilterTeacherId)) {
        return false
      }
      return true
    }),
    [selectedFilterGroupId, selectedFilterTeacherId, subjectsQuery.data?.items],
  )
  const subjectMetricsQuery = useQuery({
    queryKey: ['education', 'subject-card-metrics', subjects.map((subject) => subject.id).join(',')],
    queryFn: () => loadSubjectCardMetrics(subjects.map((subject) => subject.id)),
    enabled: subjects.length > 0,
  })
  const selectedFilterGroupName = selectedFilterGroupId ? groupNameById.get(selectedFilterGroupId) : null
  const selectedFilterTeacher = selectedFilterTeacherId ? teacherById.get(selectedFilterTeacherId) : null
  const selectedFilterTeacherName = selectedFilterTeacher
    ? getTeacherDisplayName(selectedFilterTeacher)
    : null
  const activeFilterCount = Number(Boolean(selectedFilterGroupId)) + Number(Boolean(selectedFilterTeacherId))

  return (
    <div className="space-y-6">
      <Breadcrumbs items={[{ label: t('navigation.shared.education'), to: '/education' }, { label: t('navigation.shared.subjects') }]} />
      <PageHeader
        description={t('education.subjectManagementDescription')}
        title={t('navigation.shared.subjects')}
      />

      <div className="grid gap-6 xl:grid-cols-[minmax(0,1fr)_360px]">
        <div className="space-y-5">
          <Card className="space-y-4">
            <div className="flex flex-col gap-3 lg:flex-row lg:items-end">
              <FormField label={t('common.actions.search')}>
                <Input
                  placeholder={t('education.subjectSearchPlaceholder')}
                  value={query}
                  onChange={(event) => {
                    setQuery(event.target.value)
                    setPage(0)
                  }}
                />
              </FormField>
              <Button variant="secondary" onClick={() => setFiltersOpen((current) => !current)}>
                <SlidersHorizontal className="mr-2 h-4 w-4" />
                {t('education.filters')}
                {activeFilterCount > 0 ? ` (${activeFilterCount})` : ''}
              </Button>
            </div>

            {activeFilterCount > 0 ? (
              <div className="flex flex-wrap gap-2">
                {selectedFilterGroupId ? (
                  <button
                    className="inline-flex items-center gap-2 rounded-full border border-border bg-surface-muted px-3 py-2 text-sm font-medium text-text-primary"
                    type="button"
                    onClick={() => setSelectedFilterGroupId('')}
                  >
                    {t('education.filterByGroup')}: {selectedFilterGroupName ?? t('education.selectedGroup')}
                    <X className="h-3.5 w-3.5" />
                  </button>
                ) : null}
                {selectedFilterTeacherId ? (
                  <button
                    className="inline-flex items-center gap-2 rounded-full border border-border bg-surface-muted px-3 py-2 text-sm font-medium text-text-primary"
                    type="button"
                    onClick={() => setSelectedFilterTeacherId('')}
                  >
                    {t('education.filterByTeacher')}: {selectedFilterTeacherName ?? t('education.selectedTeacher')}
                    <X className="h-3.5 w-3.5" />
                  </button>
                ) : null}
              </div>
            ) : null}

            {filtersOpen ? (
              <div className="grid gap-4 rounded-[16px] border border-border bg-surface-muted p-4 lg:grid-cols-2">
                <div className="space-y-3">
                  <FormField label={t('education.filterByGroup')}>
                    <Input
                      placeholder={t('education.groupSearchPlaceholder')}
                      value={groupSearch}
                      onChange={(event) => setGroupSearch(event.target.value)}
                    />
                  </FormField>
                  <Select
                    value={selectedFilterGroupId}
                    onChange={(event) => {
                      setSelectedFilterGroupId(event.target.value)
                      setPage(0)
                    }}
                  >
                    <option value="">{t('education.allGroups')}</option>
                    {(groupsQuery.data?.items ?? []).map((group) => (
                      <option key={group.id} value={group.id}>{group.name}</option>
                    ))}
                  </Select>
                </div>
                <div className="space-y-3">
                  <FormField label={t('education.filterByTeacher')}>
                    <Input
                      placeholder={t('education.teacherSearchPlaceholder')}
                      value={teacherSearch}
                      onChange={(event) => setTeacherSearch(event.target.value)}
                    />
                  </FormField>
                  <Select
                    value={selectedFilterTeacherId}
                    onChange={(event) => {
                      setSelectedFilterTeacherId(event.target.value)
                      setPage(0)
                    }}
                  >
                    <option value="">{t('education.allTeachers')}</option>
                    {(teachersQuery.data?.content ?? []).map((teacher) => (
                      <option key={teacher.id} value={teacher.id}>
                        {teacher.displayName?.trim() || teacher.username}
                      </option>
                    ))}
                  </Select>
                </div>
              </div>
            ) : null}
          </Card>

          {subjectsQuery.isLoading ? <LoadingState /> : null}
          {subjectsQuery.isError ? <ErrorState description={t('common.states.error')} title={t('navigation.shared.subjects')} /> : null}
          {!subjectsQuery.isLoading && !subjectsQuery.isError && subjects.length === 0 ? (
            <EmptyState description={t('education.emptySubjectsSearch')} title={t('navigation.shared.subjects')} />
          ) : null}
          {subjects.length > 0 ? (
            <>
              <div className="grid gap-3">
                {subjects.map((subject) => (
                  <SubjectCard
                    key={subject.id}
                    groupNameById={groupNameById}
                    metrics={subjectMetricsQuery.data?.get(subject.id)}
                    subject={subject}
                    teacherById={teacherById}
                  />
                ))}
              </div>
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
                  disabled={subjectsQuery.data?.last ?? true}
                  variant="secondary"
                  onClick={() => setPage((current) => current + 1)}
                >
                  {t('common.actions.next')}
                </Button>
              </div>
            </>
          ) : null}
        </div>

        <Card className="space-y-4">
          <div className="flex items-start gap-3">
            <span className="inline-flex h-11 w-11 shrink-0 items-center justify-center rounded-[16px] bg-accent-muted text-accent">
              <Plus className="h-5 w-5" />
            </span>
            <div>
              <h2 className="text-lg font-semibold text-text-primary">{t('education.createSubject')}</h2>
              <p className="text-sm leading-6 text-text-secondary">{t('education.createSubjectDescription')}</p>
            </div>
          </div>
          <FormField label={t('common.labels.name')}>
            <Input value={form.name} onChange={(event) => setForm((current) => ({ ...current, name: event.target.value }))} />
          </FormField>
          <FormField label={t('common.labels.description')}>
            <Textarea value={form.description} onChange={(event) => setForm((current) => ({ ...current, description: event.target.value }))} />
          </FormField>
          <p className="rounded-[16px] border border-border bg-surface-muted px-4 py-3 text-sm leading-6 text-text-secondary">
            {t('education.createSubjectMinimalHint')}
          </p>
          <Button
            disabled={!form.name.trim() || createMutation.isPending}
            onClick={() => createMutation.mutate()}
          >
            {t('common.actions.create')}
          </Button>
        </Card>
      </div>
    </div>
  )
}

function AccessibleSubjectsPage({
  role,
  userId,
}: {
  role: 'TEACHER' | 'STUDENT' | 'USER' | 'ADMIN' | 'OWNER'
  userId: string
}) {
  const { t } = useTranslation()
  const [query, setQuery] = useState('')
  const studentScopeQuery = useQuery({
    queryKey: ['education', 'subjects', 'student-scope', userId],
    queryFn: () => loadStudentScopedSubjects(userId),
    enabled: role === 'STUDENT' && Boolean(userId),
    staleTime: 60_000,
  })
  const subjectsQuery = useQuery({
    queryKey: ['education', 'subjects', 'accessible', role, userId],
    queryFn: () => loadAccessibleSubjects(role, userId),
    enabled: Boolean(userId),
    staleTime: 60_000,
  })
  const teacherIds = useMemo(
    () => Array.from(new Set((subjectsQuery.data ?? []).flatMap((subject) => subject.teacherIds))),
    [subjectsQuery.data],
  )
  const teachersQuery = useQuery({
    queryKey: ['education', 'accessible-subject-teachers', teacherIds.join(',')],
    queryFn: () => userDirectoryService.lookup(teacherIds),
    enabled: teacherIds.length > 0,
  })
  const teacherById = useMemo(
    () => new Map((teachersQuery.data ?? []).map((teacher) => [teacher.id, teacher])),
    [teachersQuery.data],
  )
  const subjects = useMemo(
    () => (subjectsQuery.data ?? []).filter((subject) => (
      query.trim() ? subject.name.toLowerCase().includes(query.trim().toLowerCase()) : true
    )),
    [query, subjectsQuery.data],
  )
  const subjectMetricsQuery = useQuery({
    queryKey: ['education', 'subject-card-metrics', subjects.map((subject) => subject.id).join(',')],
    queryFn: () => loadSubjectCardMetrics(subjects.map((subject) => subject.id)),
    enabled: subjects.length > 0,
  })

  if (subjectsQuery.isLoading) {
    return <LoadingState />
  }

  if (subjectsQuery.isError) {
    return <ErrorState description={t('common.states.error')} title={t('navigation.shared.subjects')} />
  }

  return (
    <div className="space-y-6">
      <Breadcrumbs items={[{ label: t('navigation.shared.education'), to: '/education' }, { label: t('navigation.shared.subjects') }]} />
      <PageHeader
        description={role === 'TEACHER' ? t('education.teacherSubjectsDescription') : t('education.studentSubjectsDescription')}
        title={t('navigation.shared.subjects')}
      />

      <Card>
        <FormField label={t('common.actions.search')}>
          <Input
            placeholder={t('education.subjectSearchPlaceholder')}
            value={query}
            onChange={(event) => setQuery(event.target.value)}
          />
        </FormField>
      </Card>

      {subjects.length === 0 ? (
        <EmptyState
          description={
            role === 'STUDENT'
              ? (studentScopeQuery.data?.hasGroup
                ? t('education.noSubjectsForGroup')
                : t('education.notAssignedToGroup'))
              : t('education.noSubjects')
          }
          title={t('navigation.shared.subjects')}
        />
      ) : (
        <div className="grid gap-3">
          {subjects.map((subject) => (
            <SubjectCard
              key={subject.id}
              metrics={subjectMetricsQuery.data?.get(subject.id)}
              subject={subject}
              teacherById={teacherById}
            />
          ))}
        </div>
      )}
    </div>
  )
}

function SubjectCard({
  groupNameById,
  metrics,
  subject,
  teacherById,
}: {
  groupNameById?: Map<string, string>
  metrics?: SubjectCardMetrics
  subject: SubjectResponse
  teacherById: Map<string, SubjectTeacherSummary>
}) {
  const { t } = useTranslation()
  const teacherNames = subject.teacherIds
    .map((teacherId) => {
      const teacher = teacherById.get(teacherId)
      return teacher ? getTeacherDisplayName(teacher) : undefined
    })
    .filter(Boolean)

  return (
    <Link to={`/subjects/${subject.id}`} className="group block h-full">
      <Card className="flex h-full flex-col justify-between gap-4 transition hover:-translate-y-0.5 hover:border-accent/40 hover:shadow-[var(--shadow-soft)] lg:flex-row lg:items-center">
        <div className="min-w-0 flex-1 space-y-4">
          <div className="flex items-start gap-3">
            <span className="inline-flex h-11 w-11 shrink-0 items-center justify-center rounded-[16px] bg-accent-muted text-accent">
              <BookOpen className="h-5 w-5" />
            </span>
            <div className="min-w-0">
              <h2 className="break-words text-lg font-semibold text-text-primary">{subject.name}</h2>
              <p className="text-sm text-text-muted">
                {t('education.subjectGroupsCount', { count: subject.groupIds.length })}
                {' · '}
                {t('education.subjectTeachersCount', { count: subject.teacherIds.length })}
              </p>
            </div>
          </div>
          <p className="text-sm leading-6 text-text-secondary">
            {subject.description || t('education.subjectDescriptionFallback')}
          </p>
          <p className="text-sm font-medium text-text-muted">
            {metrics
              ? t('education.subjectCardMetrics', {
                  assignments: metrics.activeAssignmentCount,
                  tests: metrics.activeTestCount,
                  topics: metrics.topicCount,
                })
              : t('education.subjectCardMetricsPending')}
          </p>
          <div className="space-y-1 text-sm text-text-secondary">
            <p>
              <GraduationCap className="mr-2 inline h-4 w-4 text-accent" />
              {teacherNames.length > 0 ? teacherNames.join(', ') : t('education.noTeachersAssigned')}
            </p>
            {groupNameById && subject.groupIds.length > 0 ? (
              <p>
                <Users className="mr-2 inline h-4 w-4 text-accent" />
                {subject.groupIds.map((groupId) => groupNameById.get(groupId)).filter(Boolean).join(', ') || t('education.subjectGroupsCount', { count: subject.groupIds.length })}
              </p>
            ) : null}
          </div>
        </div>
        <span className="inline-flex min-h-11 shrink-0 items-center justify-center rounded-[14px] border border-border bg-surface-muted px-4 text-sm font-medium text-text-primary transition group-hover:border-border-strong">
          {t('common.actions.open')}
        </span>
      </Card>
    </Link>
  )
}

function getTeacherDisplayName(teacher: SubjectTeacherSummary) {
  if ('displayName' in teacher && teacher.displayName?.trim()) {
    return teacher.displayName
  }

  return teacher.username
}
