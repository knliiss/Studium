import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { BookOpen, GraduationCap, Plus, Users } from 'lucide-react'
import { useMemo, useState } from 'react'
import { useTranslation } from 'react-i18next'
import { Link, useNavigate } from 'react-router-dom'

import { useAuth } from '@/features/auth/useAuth'
import { loadAccessibleSubjects } from '@/pages/education/helpers'
import { adminUserService, educationService, userDirectoryService } from '@/shared/api/services'
import { hasAnyRole } from '@/shared/lib/roles'
import type { SubjectResponse } from '@/shared/types/api'
import { Button } from '@/shared/ui/Button'
import { Card } from '@/shared/ui/Card'
import { CardPicker } from '@/shared/ui/CardPicker'
import type { CardPickerItem } from '@/shared/ui/CardPicker'
import { FormField } from '@/shared/ui/FormField'
import { Input } from '@/shared/ui/Input'
import { PageHeader } from '@/shared/ui/PageHeader'
import { EmptyState, ErrorState, LoadingState } from '@/shared/ui/StateViews'
import { Textarea } from '@/shared/ui/Textarea'
import { UserAvatar } from '@/shared/ui/UserAvatar'

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
  const [form, setForm] = useState({ name: '', description: '' })
  const [selectedGroupIds, setSelectedGroupIds] = useState<string[]>([])
  const [selectedTeacherIds, setSelectedTeacherIds] = useState<string[]>([])
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
      groupIds: selectedGroupIds,
      teacherIds: selectedTeacherIds,
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
  const groupItems = useMemo<CardPickerItem[]>(
    () => (groupsQuery.data?.items ?? []).map((group) => ({
      id: group.id,
      title: group.name,
      description: t('education.groupCardDescription'),
      meta: t('education.groupScheduleStatusUnknown'),
      leading: <Users className="h-5 w-5 text-accent" />,
    })),
    [groupsQuery.data?.items, t],
  )
  const teacherItems = useMemo<CardPickerItem[]>(
    () => (teachersQuery.data?.content ?? []).map((teacher) => ({
      id: teacher.id,
      title: teacher.displayName?.trim() || teacher.username,
      description: teacher.email,
      meta: t('education.teacherLabel'),
      leading: <UserAvatar email={teacher.email} username={teacher.username} size="sm" />,
    })),
    [t, teachersQuery.data?.content],
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

  return (
    <div className="space-y-6">
      <PageHeader
        description={t('education.subjectManagementDescription')}
        title={t('navigation.shared.subjects')}
      />

      <div className="grid gap-6 xl:grid-cols-[1.25fr_0.75fr]">
        <div className="space-y-5">
          <Card className="space-y-4">
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
            <div className="grid gap-4 lg:grid-cols-2">
              <CardPicker
                emptyDescription={t('education.noGroupOptions')}
                emptyTitle={t('navigation.shared.groups')}
                items={groupItems}
                label={t('education.filterByGroup')}
                loading={groupsQuery.isLoading}
                searchLabel={t('common.actions.search')}
                searchPlaceholder={t('education.groupSearchPlaceholder')}
                searchValue={groupSearch}
                selectedIds={selectedFilterGroupId ? [selectedFilterGroupId] : []}
                onSearchChange={setGroupSearch}
                onToggle={(id) => setSelectedFilterGroupId((current) => current === id ? '' : id)}
              />
              <CardPicker
                emptyDescription={t('education.noTeacherOptions')}
                emptyTitle={t('education.subjectTeachers')}
                items={teacherItems}
                label={t('education.filterByTeacher')}
                loading={teachersQuery.isLoading}
                searchLabel={t('common.actions.search')}
                searchPlaceholder={t('education.teacherSearchPlaceholder')}
                searchValue={teacherSearch}
                selectedIds={selectedFilterTeacherId ? [selectedFilterTeacherId] : []}
                onSearchChange={setTeacherSearch}
                onToggle={(id) => setSelectedFilterTeacherId((current) => current === id ? '' : id)}
              />
            </div>
          </Card>

          {subjectsQuery.isLoading ? <LoadingState /> : null}
          {subjectsQuery.isError ? <ErrorState description={t('common.states.error')} title={t('navigation.shared.subjects')} /> : null}
          {!subjectsQuery.isLoading && !subjectsQuery.isError && subjects.length === 0 ? (
            <EmptyState description={t('education.emptySubjectsSearch')} title={t('navigation.shared.subjects')} />
          ) : null}
          {subjects.length > 0 ? (
            <>
              <div className="grid gap-4 md:grid-cols-2">
                {subjects.map((subject) => (
                  <SubjectCard
                    key={subject.id}
                    groupNameById={groupNameById}
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
          <CardPicker
            emptyDescription={t('education.noGroupOptions')}
            emptyTitle={t('navigation.shared.groups')}
            items={groupItems}
            label={t('navigation.shared.groups')}
            loading={groupsQuery.isLoading}
            multiple
            searchLabel={t('common.actions.search')}
            searchPlaceholder={t('education.groupSearchPlaceholder')}
            searchValue={groupSearch}
            selectedIds={selectedGroupIds}
            onSearchChange={setGroupSearch}
            onToggle={(id) => setSelectedGroupIds((current) => toggleId(current, id))}
          />
          <CardPicker
            emptyDescription={t('education.noTeacherOptions')}
            emptyTitle={t('education.subjectTeachers')}
            items={teacherItems}
            label={t('education.subjectTeachers')}
            loading={teachersQuery.isLoading}
            multiple
            searchLabel={t('common.actions.search')}
            searchPlaceholder={t('education.teacherSearchPlaceholder')}
            searchValue={teacherSearch}
            selectedIds={selectedTeacherIds}
            onSearchChange={setTeacherSearch}
            onToggle={(id) => setSelectedTeacherIds((current) => toggleId(current, id))}
          />
          <Button
            disabled={!form.name.trim() || selectedGroupIds.length === 0 || createMutation.isPending}
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
  const subjectsQuery = useQuery({
    queryKey: ['education', 'subjects', 'accessible', role, userId],
    queryFn: () => loadAccessibleSubjects(role, userId),
    enabled: Boolean(userId),
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

  if (subjectsQuery.isLoading) {
    return <LoadingState />
  }

  if (subjectsQuery.isError) {
    return <ErrorState description={t('common.states.error')} title={t('navigation.shared.subjects')} />
  }

  return (
    <div className="space-y-6">
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
          description={t('education.noSubjects')}
          title={t('navigation.shared.subjects')}
        />
      ) : (
        <div className="grid gap-4 md:grid-cols-2 xl:grid-cols-3">
          {subjects.map((subject) => (
            <SubjectCard key={subject.id} subject={subject} teacherById={teacherById} />
          ))}
        </div>
      )}
    </div>
  )
}

function SubjectCard({
  groupNameById,
  subject,
  teacherById,
}: {
  groupNameById?: Map<string, string>
  subject: SubjectResponse
  teacherById: Map<string, { username: string; email: string }>
}) {
  const { t } = useTranslation()
  const teacherNames = subject.teacherIds
    .map((teacherId) => teacherById.get(teacherId)?.username)
    .filter(Boolean)

  return (
    <Card className="gradient-card flex h-full flex-col justify-between gap-4">
      <div className="space-y-4">
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
      <Link to={`/subjects/${subject.id}`}>
        <Button fullWidth variant="secondary">{t('common.actions.open')}</Button>
      </Link>
    </Card>
  )
}

function toggleId(values: string[], id: string) {
  return values.includes(id) ? values.filter((value) => value !== id) : [...values, id]
}
