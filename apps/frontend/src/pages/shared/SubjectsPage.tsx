import { useQuery } from '@tanstack/react-query'
import { useMemo } from 'react'
import { useTranslation } from 'react-i18next'
import { Link, useLocation, useParams } from 'react-router-dom'

import { useAuth } from '@/features/auth/useAuth'
import { educationService, scheduleService } from '@/shared/api/services'
import { Button } from '@/shared/ui/Button'
import { Card } from '@/shared/ui/Card'
import { DataTable } from '@/shared/ui/DataTable'
import { PageHeader } from '@/shared/ui/PageHeader'
import { EmptyState, ErrorState, LoadingState } from '@/shared/ui/StateViews'

function buildTeacherDateRange() {
  const today = new Date()
  const nextMonth = new Date(today)
  nextMonth.setDate(today.getDate() + 30)
  return {
    dateFrom: today.toISOString().slice(0, 10),
    dateTo: nextMonth.toISOString().slice(0, 10),
  }
}

export function SubjectsPage() {
  const { t } = useTranslation()
  const { session, primaryRole } = useAuth()
  const { pathname, state } = useLocation()
  const { subjectId, topicId } = useParams()
  const range = useMemo(() => buildTeacherDateRange(), [])

  const subjectsQuery = useQuery({
    queryKey: ['subjects-accessible', primaryRole, session?.user.id],
    enabled: Boolean(session?.user.id),
    queryFn: async () => {
      if (!session?.user.id) {
        return []
      }

      if (primaryRole === 'STUDENT') {
        const memberships = await educationService.getGroupsByUser(session.user.id)
        const subjectPages = await Promise.all(
          memberships.map((membership) =>
            educationService.getSubjectsByGroup(membership.groupId, { page: 0, size: 100 }),
          ),
        )
        return subjectPages.flatMap((page) => page.items)
      }

      const lessons = await scheduleService.getMyRange(range.dateFrom, range.dateTo)
      const uniqueSubjectIds = Array.from(new Set(lessons.map((lesson) => lesson.subjectId)))
      const subjects = await Promise.all(uniqueSubjectIds.map((id) => educationService.getSubject(id)))
      return subjects
    },
  })

  const topicsQuery = useQuery({
    queryKey: ['topics-by-subject', subjectId],
    enabled: Boolean(subjectId),
    queryFn: () => educationService.getTopicsBySubject(subjectId!, { page: 0, size: 100 }),
  })

  const topicAssignmentsQuery = useQuery({
    queryKey: ['topic-context', state?.subjectId, topicId],
    enabled: Boolean(topicId && state && typeof state === 'object' && 'subjectId' in state),
    queryFn: async () => {
      const topicPage = await educationService.getTopicsBySubject((state as { subjectId: string }).subjectId, { page: 0, size: 100 })
      return topicPage.items.find((topic) => topic.id === topicId) ?? null
    },
  })

  if (subjectsQuery.isLoading || (subjectId && topicsQuery.isLoading) || (topicId && topicAssignmentsQuery.isLoading)) {
    return <LoadingState />
  }

  if (subjectsQuery.isError || (subjectId && topicsQuery.isError) || (topicId && topicAssignmentsQuery.isError)) {
    return <ErrorState title={t('navigation.student.subjects')} description={t('common.states.error')} />
  }

  const subjects = subjectsQuery.data ?? []
  const selectedSubject = subjects.find((subject) => subject.id === subjectId)

  if (topicId) {
    return (
      <div className="space-y-6">
        <PageHeader
          description={t('education.topicDetailDescription')}
          title={topicAssignmentsQuery.data?.title ?? t('education.topic')}
        />
        {topicAssignmentsQuery.data ? (
          <Card className="space-y-4">
            <p className="text-sm text-text-secondary">{t('education.orderIndex')}: {topicAssignmentsQuery.data.orderIndex}</p>
            <div className="flex flex-wrap gap-3">
              <Link to={`/${primaryRole.toLowerCase()}/assignments`}>
                <Button variant="secondary">{t('navigation.student.assignments')}</Button>
              </Link>
              <Link to={`/${primaryRole.toLowerCase()}/tests`}>
                <Button>{t('navigation.student.tests')}</Button>
              </Link>
            </div>
          </Card>
        ) : (
          <EmptyState
            description={t('education.topicDirectReadMissing')}
            title={t('education.topicDirectReadMissingTitle')}
          />
        )}
      </div>
    )
  }

  if (subjectId && selectedSubject) {
    return (
      <div className="space-y-6">
        <PageHeader description={selectedSubject.description ?? ''} title={selectedSubject.name} />
        <DataTable
          columns={[
            { key: 'title', header: t('common.labels.title'), render: (item) => <Link className="font-medium text-accent" state={{ subjectId: selectedSubject.id }} to={`/${primaryRole.toLowerCase()}/topics/${item.id}`}>{item.title}</Link> },
            { key: 'orderIndex', header: t('education.orderIndex'), render: (item) => item.orderIndex },
          ]}
          rows={topicsQuery.data?.items ?? []}
        />
      </div>
    )
  }

  return (
    <div className="space-y-6">
      <PageHeader
        description={primaryRole === 'TEACHER' ? t('education.teacherSubjectsDescription') : t('education.studentSubjectsDescription')}
        title={pathname.includes('/teacher/') ? t('navigation.teacher.subjects') : t('navigation.student.subjects')}
      />
      {subjects.length === 0 ? (
        <EmptyState
          description={t('education.noSubjects')}
          title={t('navigation.student.subjects')}
        />
      ) : (
        <DataTable
          columns={[
            { key: 'name', header: t('common.labels.name'), render: (item) => <Link className="font-medium text-accent" to={`/${primaryRole.toLowerCase()}/subjects/${item.id}`}>{item.name}</Link> },
            { key: 'groupId', header: t('education.groupId'), render: (item) => item.groupId },
            { key: 'description', header: t('common.labels.description'), render: (item) => item.description ?? '-' },
          ]}
          rows={subjects}
        />
      )}
    </div>
  )
}
