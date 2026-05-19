import type { ChangeEventHandler, ReactNode } from 'react'
import { useMemo, useState } from 'react'
import { useMutation, useQuery } from '@tanstack/react-query'
import { Download } from 'lucide-react'
import { Link } from 'react-router-dom'
import { useTranslation } from 'react-i18next'

import { useAuth } from '@/features/auth/useAuth'
import { gradebookService } from '@/shared/api/services'
import { getLocalizedRequestErrorMessage, normalizeApiError } from '@/shared/lib/api-errors'
import { downloadBlob } from '@/shared/lib/download'
import { formatDateTime } from '@/shared/lib/format'
import { isAdminLike } from '@/shared/lib/roles'
import type {
  GradebookActivityRowResponse,
  GradebookActivityStatus,
  GradebookActivityType,
  GradebookAdminOverviewRowResponse,
  GradebookMatrixCellResponse,
  GradebookMatrixResponse,
  GradebookStudentSubjectResponse,
} from '@/shared/types/api'
import { Button } from '@/shared/ui/Button'
import { Card } from '@/shared/ui/Card'
import { DataTable } from '@/shared/ui/DataTable'
import { EmptyState, ErrorState, LoadingState } from '@/shared/ui/StateViews'
import { PageHeader } from '@/shared/ui/PageHeader'
import { SegmentedControl } from '@/shared/ui/SegmentedControl'
import { Select } from '@/shared/ui/Select'
import { MetricCard } from '@/widgets/common/MetricCard'

const activityFilterOptions: Array<{ value: GradebookActivityType; labelKey: string }> = [
  { value: 'ALL', labelKey: 'gradebook.filters.allActivities' },
  { value: 'ASSIGNMENTS', labelKey: 'gradebook.filters.assignments' },
  { value: 'TESTS', labelKey: 'gradebook.filters.tests' },
]

type StudentTab = 'BY_SUBJECT' | 'ALL' | 'MISSING'
type AdminTab = 'GROUPS' | 'SUBJECTS' | 'DETAILS'

interface GradebookExportParams {
  subjectId?: string | null
  groupId?: string | null
  teacherId?: string | null
  activityType?: GradebookActivityType
  period?: string | null
}

interface GroupRankingRow {
  groupId: string
  groupName: string
  studentCount: number
  subjectCount: number
  averagePercent: number | null
  highCount: number
  goodCount: number
  averageCount: number
  lowCount: number
  missingCount: number
  subjectIds: string[]
}

interface SubjectRankingRow {
  subjectId: string
  subjectName: string
  teacherNames: string[]
  groupCount: number
  studentCount: number
  averagePercent: number | null
  completionPercent: number | null
  missingCount: number
  groupIds: string[]
}

export function GradebookPage() {
  const { t } = useTranslation()
  const { primaryRole, roles } = useAuth()

  if (primaryRole === 'STUDENT') {
    return <StudentGradebookSection />
  }

  if (primaryRole === 'TEACHER') {
    return <TeacherGradebookSection />
  }

  if (isAdminLike(roles)) {
    return <AdminGradebookSection />
  }

  return <ErrorState title={t('gradebook.noPermissionTitle')} description={t('gradebook.noPermissionDescription')} />
}

function StudentGradebookSection() {
  const { t } = useTranslation()
  const [tab, setTab] = useState<StudentTab>('BY_SUBJECT')
  const [selectedSubjectId, setSelectedSubjectId] = useState<string | null>(null)
  const exportMutation = useGradebookExport()
  const query = useQuery({
    queryKey: ['gradebook', 'student'],
    queryFn: () => gradebookService.getMyGradebook(),
  })

  const effectiveSubjectId = selectedSubjectId ?? query.data?.subjects[0]?.subjectId ?? null
  const selectedSubject = query.data?.subjects.find((subject) => subject.subjectId === effectiveSubjectId) ?? query.data?.subjects[0] ?? null
  const flattenedActivities = useMemo(
    () => (query.data?.subjects ?? []).flatMap((subject) => subject.activities.map((activity) => ({ subject, activity }))),
    [query.data],
  )
  const missingActivities = flattenedActivities.filter(({ activity }) => activity.status === 'MISSING')

  if (query.isLoading) {
    return <LoadingState />
  }

  if (query.isError || !query.data) {
    return <ErrorState title={t('gradebook.student.title')} description={t('gradebook.states.error')} onRetry={() => query.refetch()} />
  }

  if (query.data.subjects.length === 0) {
    return <EmptyState title={t('gradebook.student.emptyTitle')} description={t('gradebook.student.emptyDescription')} />
  }

  return (
    <div className="space-y-6">
      <PageHeader
        title={t('gradebook.student.title')}
        description={t('gradebook.student.description')}
        actions={(
          <Button
            disabled={exportMutation.isPending}
            variant="secondary"
            onClick={() => exportMutation.startExport({}, 'my-gradebook.csv')}
          >
            <Download className="mr-2 h-4 w-4" />
            {exportMutation.isPending ? t('gradebook.exportLoading') : t('gradebook.export')}
          </Button>
        )}
      />

      <ExportFeedbackBanner feedback={exportMutation.feedback} />

      <div className="grid gap-4 md:grid-cols-4">
        <MetricCard title={t('gradebook.metrics.average')} value={formatPercent(query.data.averagePercent)} />
        <MetricCard title={t('gradebook.metrics.graded')} value={query.data.gradedActivities} />
        <MetricCard title={t('gradebook.metrics.pending')} value={query.data.pendingActivities} />
        <MetricCard title={t('gradebook.metrics.missing')} value={query.data.missingActivities} />
      </div>

      <div className="flex flex-wrap items-center justify-between gap-3">
        <SegmentedControl
          ariaLabel={t('gradebook.student.tabsLabel')}
          value={tab}
          onChange={setTab}
          options={[
            { value: 'BY_SUBJECT', label: t('gradebook.student.tabs.bySubject') },
            { value: 'ALL', label: t('gradebook.student.tabs.allGrades') },
            { value: 'MISSING', label: t('gradebook.student.tabs.missingPreview') },
          ]}
        />
        {tab === 'BY_SUBJECT' ? (
          <Select
            className="min-w-[240px]"
            value={effectiveSubjectId ?? ''}
            onChange={(event) => setSelectedSubjectId(event.target.value)}
          >
            {query.data.subjects.map((subject) => (
              <option key={subject.subjectId} value={subject.subjectId}>{subject.subjectName}</option>
            ))}
          </Select>
        ) : null}
      </div>

      {tab === 'BY_SUBJECT' ? (
        <div className="grid gap-6 xl:grid-cols-[360px_minmax(0,1fr)]">
          <div className="space-y-4">
            {query.data.subjects.map((subject) => (
              <button
                key={subject.subjectId}
                className={`w-full rounded-[18px] border p-5 text-left transition ${subject.subjectId === selectedSubject?.subjectId ? 'border-accent bg-surface shadow-[var(--shadow-soft)]' : 'border-border bg-surface-muted hover:border-border-strong'}`}
                type="button"
                onClick={() => setSelectedSubjectId(subject.subjectId)}
              >
                <div className="flex items-start justify-between gap-3">
                  <div className="space-y-2">
                    <p className="text-lg font-semibold text-text-primary">{subject.subjectName}</p>
                    <p className="text-sm text-text-secondary">{subject.teacherNames.join(', ') || t('gradebook.labels.noTeacher')}</p>
                    {subject.groupName ? <p className="text-xs uppercase tracking-[0.18em] text-text-muted">{subject.groupName}</p> : null}
                  </div>
                  <span className="rounded-full border border-border bg-surface px-3 py-1 text-xs font-semibold text-accent">
                    {formatPercent(subject.averagePercent)}
                  </span>
                </div>
                <div className="mt-4 grid gap-3 sm:grid-cols-2">
                  <CompactStat label={t('gradebook.labels.assignmentsProgress')} value={`${subject.assignmentsCompleted}/${subject.assignmentsTotal}`} />
                  <CompactStat label={t('gradebook.labels.testsProgress')} value={`${subject.testsCompleted}/${subject.testsTotal}`} />
                  <CompactStat label={t('gradebook.labels.pendingReview')} value={subject.pendingCount} />
                  <CompactStat label={t('gradebook.labels.missing')} value={subject.missingCount} />
                </div>
              </button>
            ))}
          </div>
          {selectedSubject ? <StudentSubjectPanel subject={selectedSubject} /> : null}
        </div>
      ) : null}

      {tab === 'ALL' ? <StudentActivityTable items={flattenedActivities} /> : null}

      {tab === 'MISSING' ? (
        missingActivities.length > 0 ? (
          <div className="grid gap-4 lg:grid-cols-2">
            {missingActivities.map(({ subject, activity }) => (
              <Card key={`${activity.activityType}-${activity.activityId}`} className="space-y-3 border border-danger/20">
                <div className="flex items-start justify-between gap-3">
                  <div>
                    <p className="text-sm font-semibold uppercase tracking-[0.18em] text-danger">{t('gradebook.labels.missing')}</p>
                    <h2 className="mt-2 text-lg font-semibold text-text-primary">{activity.title}</h2>
                    <p className="mt-1 text-sm text-text-secondary">{subject.subjectName}</p>
                  </div>
                  <StatusPill status={activity.status} />
                </div>
                <p className="text-sm text-text-secondary">
                  {activity.activityType === 'ASSIGNMENT' ? t('gradebook.filters.assignments') : t('gradebook.filters.tests')}
                </p>
                <div className="flex items-center justify-between gap-3 text-sm text-text-secondary">
                  <span>{t('gradebook.labels.deadline')}</span>
                  <span>{activity.dueAt ? formatDateTime(activity.dueAt) : '—'}</span>
                </div>
                <Link className="inline-flex text-sm font-semibold text-accent" to={detailLink(activity)}>
                  {t('common.actions.open')}
                </Link>
              </Card>
            ))}
          </div>
        ) : (
          <EmptyState title={t('gradebook.student.noMissingTitle')} description={t('gradebook.student.noMissingDescription')} />
        )
      ) : null}
    </div>
  )
}

function TeacherGradebookSection() {
  const { t } = useTranslation()
  const [subjectId, setSubjectId] = useState<string | null>(null)
  const [groupId, setGroupId] = useState<string | null>(null)
  const [activityType, setActivityType] = useState<GradebookActivityType>('ALL')
  const exportMutation = useGradebookExport()

  const query = useQuery({
    queryKey: ['gradebook', 'teacher', subjectId, groupId, activityType],
    queryFn: () => gradebookService.getTeacher({ subjectId, groupId, activityType }),
  })

  if (query.isLoading) {
    return <LoadingState />
  }

  if (query.isError || !query.data) {
    return <ErrorState title={t('gradebook.teacher.title')} description={t('gradebook.states.error')} onRetry={() => query.refetch()} />
  }

  const effectiveSubjectId = subjectId ?? query.data.selectedSubjectId
  const effectiveGroupId = groupId ?? query.data.selectedGroupId

  return (
    <div className="space-y-6">
      <PageHeader
        title={t('gradebook.teacher.title')}
        description={t('gradebook.teacher.description')}
        actions={(
          <div className="flex flex-wrap gap-3">
            <Button
              disabled={exportMutation.isPending}
              variant="secondary"
              onClick={() => exportMutation.startExport(
                { subjectId: effectiveSubjectId, groupId: effectiveGroupId, activityType },
                'teacher-gradebook.csv',
              )}
            >
              <Download className="mr-2 h-4 w-4" />
              {exportMutation.isPending ? t('gradebook.exportLoading') : t('gradebook.export')}
            </Button>
            <Link className="inline-flex min-h-11 items-center justify-center rounded-[14px] border border-border bg-surface px-4 text-sm font-semibold text-text-primary transition hover:bg-surface-muted" to="/review">
              {t('gradebook.quickLinks.reviewSubmissions')}
            </Link>
            <Link className="inline-flex min-h-11 items-center justify-center rounded-[14px] border border-border bg-surface px-4 text-sm font-semibold text-text-primary transition hover:bg-surface-muted" to="/tests">
              {t('gradebook.quickLinks.reviewTests')}
            </Link>
          </div>
        )}
      />

      <ExportFeedbackBanner feedback={exportMutation.feedback} />

      <TeacherFilterBar
        activityType={activityType}
        groupId={effectiveGroupId}
        groups={query.data.groups}
        subjectId={effectiveSubjectId}
        subjects={query.data.subjects}
        onActivityTypeChange={setActivityType}
        onGroupChange={setGroupId}
        onSubjectChange={(value) => {
          setSubjectId(value)
          setGroupId(null)
        }}
      />

      {query.data.matrix ? <MatrixSummaryCards matrix={query.data.matrix} /> : null}
      {query.data.matrix ? <GradeMatrix matrix={query.data.matrix} mode="teacher" /> : null}
      {!query.data.matrix ? <EmptyState title={t('gradebook.teacher.emptyTitle')} description={t('gradebook.teacher.emptyDescription')} /> : null}
    </div>
  )
}

function AdminGradebookSection() {
  const { t } = useTranslation()
  const [tab, setTab] = useState<AdminTab>('GROUPS')
  const [subjectId, setSubjectId] = useState<string | null>(null)
  const [groupId, setGroupId] = useState<string | null>(null)
  const [teacherId, setTeacherId] = useState<string | null>(null)
  const [activityType, setActivityType] = useState<GradebookActivityType>('ALL')
  const exportMutation = useGradebookExport()

  const overviewQuery = useQuery({
    queryKey: ['gradebook', 'admin', 'overview', teacherId],
    queryFn: () => gradebookService.getAdminOverview({ teacherId }),
  })
  const detailQuery = useQuery({
    queryKey: ['gradebook', 'admin', subjectId, groupId, teacherId, activityType],
    queryFn: () => gradebookService.getAdmin({ subjectId, groupId, teacherId, activityType }),
  })

  const overviewRows = useMemo(
    () => overviewQuery.data?.rows ?? detailQuery.data?.rows ?? [],
    [detailQuery.data?.rows, overviewQuery.data?.rows],
  )
  const groupRankings = useMemo(() => buildGroupRankings(overviewRows), [overviewRows])
  const subjectRankings = useMemo(() => buildSubjectRankings(overviewRows), [overviewRows])
  const allowedGroupIds = useMemo(
    () => new Set(overviewRows.filter((row) => !subjectId || row.subjectId === subjectId).map((row) => row.groupId)),
    [overviewRows, subjectId],
  )
  const allowedSubjectIds = useMemo(
    () => new Set(overviewRows.filter((row) => !groupId || row.groupId === groupId).map((row) => row.subjectId)),
    [overviewRows, groupId],
  )
  const filteredSubjects = useMemo(
    () => (detailQuery.data?.subjects ?? []).filter((option) => !groupId || allowedSubjectIds.has(option.id)),
    [allowedSubjectIds, detailQuery.data?.subjects, groupId],
  )
  const filteredGroups = useMemo(
    () => (detailQuery.data?.groups ?? []).filter((option) => !subjectId || allowedGroupIds.has(option.id)),
    [allowedGroupIds, detailQuery.data?.groups, subjectId],
  )

  if (overviewQuery.isLoading || detailQuery.isLoading) {
    return <LoadingState />
  }

  if (overviewQuery.isError || detailQuery.isError || !overviewQuery.data || !detailQuery.data) {
    return (
      <ErrorState
        title={t('gradebook.admin.title')}
        description={t('gradebook.states.error')}
        onRetry={() => {
          void overviewQuery.refetch()
          void detailQuery.refetch()
        }}
      />
    )
  }

  const detailMatrix = detailQuery.data.matrix
  const hasDetailSelection = Boolean(subjectId && groupId)

  return (
    <div className="space-y-6">
      <PageHeader
        title={t('gradebook.admin.title')}
        description={t('gradebook.admin.description')}
        actions={(
          <Button
            disabled={exportMutation.isPending}
            variant="secondary"
            onClick={() => exportMutation.startExport(
              { subjectId, groupId, teacherId, activityType },
              hasDetailSelection ? 'admin-gradebook-detail.csv' : 'admin-gradebook-overview.csv',
            )}
          >
            <Download className="mr-2 h-4 w-4" />
            {exportMutation.isPending ? t('gradebook.exportLoading') : t('gradebook.export')}
          </Button>
        )}
      />

      <ExportFeedbackBanner feedback={exportMutation.feedback} />

      <div className="grid gap-3 md:grid-cols-2 xl:grid-cols-6">
        <AnalyticMetricCard
          hint={t('gradebook.admin.kpi.averageHint')}
          title={t('gradebook.metrics.average')}
          value={formatPercent(overviewQuery.data.platformAveragePercent)}
        />
        <AnalyticMetricCard
          hint={t('gradebook.admin.kpi.activeStudentsHint')}
          title={t('gradebook.metrics.activeStudents')}
          value={overviewQuery.data.activeStudents}
        />
        <AnalyticMetricCard
          hint={t('gradebook.admin.kpi.subjectsHint')}
          title={t('gradebook.metrics.subjects')}
          value={overviewQuery.data.subjectCount}
        />
        <AnalyticMetricCard
          hint={t('gradebook.admin.kpi.groupsHint')}
          title={t('gradebook.metrics.groups')}
          value={overviewQuery.data.groupCount}
        />
        <AnalyticMetricCard
          hint={t('gradebook.admin.kpi.missingHint')}
          title={t('gradebook.metrics.missing')}
          value={overviewQuery.data.missingWorkCount}
        />
        <AnalyticMetricCard
          hint={t('gradebook.admin.kpi.completionHint')}
          title={t('gradebook.metrics.completion')}
          value={formatPercent(overviewQuery.data.averageCompletionPercent)}
        />
      </div>

      <AdminFilterBar
        activityType={activityType}
        groupId={groupId}
        groups={filteredGroups}
        subjectId={subjectId}
        subjects={filteredSubjects}
        teacherId={teacherId}
        teachers={detailQuery.data.teachers}
        onActivityTypeChange={setActivityType}
        onGroupChange={(nextGroupId) => {
          setGroupId(nextGroupId)
          if (subjectId && nextGroupId && !overviewRows.some((row) => row.subjectId === subjectId && row.groupId === nextGroupId)) {
            setSubjectId(null)
          }
        }}
        onReset={() => {
          setSubjectId(null)
          setGroupId(null)
          setTeacherId(null)
          setActivityType('ALL')
        }}
        onSubjectChange={(nextSubjectId) => {
          setSubjectId(nextSubjectId)
          if (groupId && nextSubjectId && !overviewRows.some((row) => row.subjectId === nextSubjectId && row.groupId === groupId)) {
            setGroupId(null)
          }
        }}
        onTeacherChange={setTeacherId}
      />

      <div className="flex flex-wrap items-center justify-between gap-3">
        <SegmentedControl
          ariaLabel={t('gradebook.admin.title')}
          className="w-full md:w-auto"
          value={tab}
          onChange={setTab}
          options={[
            { value: 'GROUPS', label: t('gradebook.admin.tabs.groups') },
            { value: 'SUBJECTS', label: t('gradebook.admin.tabs.subjects') },
            { value: 'DETAILS', label: t('gradebook.admin.tabs.details') },
          ]}
        />
        <div className="rounded-full border border-border bg-surface px-3 py-2 text-xs font-semibold uppercase tracking-[0.16em] text-text-muted">
          {subjectId && groupId
            ? `${selectedLabel(detailQuery.data.subjects, subjectId)} · ${selectedLabel(detailQuery.data.groups, groupId)}`
            : t('gradebook.admin.detailPromptTitle')}
        </div>
      </div>

      {tab === 'GROUPS' ? (
        <GroupRankingTable
          rows={groupRankings}
          onViewDetails={(row) => {
            setTab('DETAILS')
            setGroupId(row.groupId)
            if (row.subjectIds.length === 1) {
              setSubjectId(row.subjectIds[0] ?? null)
            }
          }}
        />
      ) : null}

      {tab === 'SUBJECTS' ? (
        <SubjectRankingTable
          rows={subjectRankings}
          onViewDetails={(row) => {
            setTab('DETAILS')
            setSubjectId(row.subjectId)
            if (row.groupIds.length === 1) {
              setGroupId(row.groupIds[0] ?? null)
            }
          }}
        />
      ) : null}

      {tab === 'DETAILS' ? (
        hasDetailSelection ? (
          detailMatrix ? (
            <div className="space-y-4">
              <MatrixSummaryCards matrix={detailMatrix} />
              <GradeMatrix matrix={detailMatrix} mode="admin" />
            </div>
          ) : (
            <EmptyState title={t('gradebook.admin.noDetailDataTitle')} description={t('gradebook.admin.noDetailDataDescription')} />
          )
        ) : (
          <DetailPromptPanel
            groupRankings={groupRankings}
            subjectRankings={subjectRankings}
            onChooseGroup={(nextGroupId) => {
              setTab('DETAILS')
              setGroupId(nextGroupId)
            }}
            onChooseSubject={(nextSubjectId) => {
              setTab('DETAILS')
              setSubjectId(nextSubjectId)
            }}
          />
        )
      ) : null}
    </div>
  )
}

function StudentSubjectPanel({ subject }: { subject: GradebookStudentSubjectResponse }) {
  const { t } = useTranslation()

  return (
    <div className="space-y-4">
      <Card className="space-y-4">
        <div className="flex flex-wrap items-start justify-between gap-4">
          <div className="space-y-2">
            <p className="text-xs font-semibold uppercase tracking-[0.2em] text-accent">{t('gradebook.student.subjectOverview')}</p>
            <h2 className="text-2xl font-semibold tracking-[-0.03em] text-text-primary">{subject.subjectName}</h2>
            <p className="text-sm text-text-secondary">{subject.teacherNames.join(', ') || t('gradebook.labels.noTeacher')}</p>
          </div>
          <StatusMetric value={formatPercent(subject.averagePercent)} label={t('gradebook.labels.averageScore')} />
        </div>
        <div className="grid gap-3 md:grid-cols-2 xl:grid-cols-4">
          <CompactStat label={t('gradebook.labels.assignmentsProgress')} value={`${subject.assignmentsCompleted}/${subject.assignmentsTotal}`} />
          <CompactStat label={t('gradebook.labels.testsProgress')} value={`${subject.testsCompleted}/${subject.testsTotal}`} />
          <CompactStat label={t('gradebook.labels.pendingReview')} value={subject.pendingCount} />
          <CompactStat label={t('gradebook.labels.missing')} value={subject.missingCount} />
        </div>
      </Card>
      <StudentActivityTable items={subject.activities.map((activity) => ({ subject, activity }))} />
    </div>
  )
}

function StudentActivityTable({ items }: { items: Array<{ subject: GradebookStudentSubjectResponse; activity: GradebookActivityRowResponse }> }) {
  const { t } = useTranslation()

  return (
    <DataTable
      columns={[
        {
          key: 'subject',
          header: t('gradebook.labels.subject'),
          render: (row) => (
            <div>
              <p className="font-medium text-text-primary">{row.subject.subjectName}</p>
              <p className="text-xs text-text-muted">
                {row.activity.activityType === 'ASSIGNMENT' ? t('gradebook.filters.assignments') : t('gradebook.filters.tests')}
              </p>
            </div>
          ),
        },
        {
          key: 'title',
          header: t('gradebook.labels.activity'),
          render: (row) => <Link className="font-medium text-accent" to={detailLink(row.activity)}>{row.activity.title}</Link>,
        },
        {
          key: 'score',
          header: t('gradebook.labels.score'),
          render: (row) => <ScoreValue score={row.activity.score} maxPoints={row.activity.maxPoints} />,
        },
        {
          key: 'status',
          header: t('common.labels.status'),
          render: (row) => <StatusPill status={row.activity.status} />,
        },
        {
          key: 'dueAt',
          header: t('gradebook.labels.deadline'),
          render: (row) => row.activity.dueAt ? formatDateTime(row.activity.dueAt) : '—',
        },
      ]}
      rows={items}
    />
  )
}

function TeacherFilterBar({
  activityType,
  groupId,
  groups,
  subjectId,
  subjects,
  onActivityTypeChange,
  onGroupChange,
  onSubjectChange,
}: {
  activityType: GradebookActivityType
  groupId: string | null
  groups: Array<{ id: string; label: string }>
  subjectId: string | null
  subjects: Array<{ id: string; label: string }>
  onActivityTypeChange: (value: GradebookActivityType) => void
  onGroupChange: (value: string | null) => void
  onSubjectChange: (value: string | null) => void
}) {
  const { t } = useTranslation()

  return (
    <Card className="space-y-4 p-4">
      <div className="grid gap-3 xl:grid-cols-[minmax(0,1fr)_minmax(0,1fr)_minmax(0,1.4fr)_220px]">
        <FilterSelectField
          label={t('gradebook.labels.subject')}
          value={subjectId ?? ''}
          onChange={(event) => onSubjectChange(event.target.value || null)}
        >
          {subjects.map((option) => (
            <option key={option.id} value={option.id}>{option.label}</option>
          ))}
        </FilterSelectField>
        <FilterSelectField
          label={t('gradebook.labels.group')}
          value={groupId ?? ''}
          onChange={(event) => onGroupChange(event.target.value || null)}
        >
          {groups.map((option) => (
            <option key={option.id} value={option.id}>{option.label}</option>
          ))}
        </FilterSelectField>
        <FilterSegmentField
          activityType={activityType}
          label={t('gradebook.filters.activityType')}
          onActivityTypeChange={onActivityTypeChange}
        />
        <PeriodPlaceholderField />
      </div>
    </Card>
  )
}

function AdminFilterBar({
  activityType,
  groupId,
  groups,
  subjectId,
  subjects,
  teacherId,
  teachers,
  onActivityTypeChange,
  onGroupChange,
  onReset,
  onSubjectChange,
  onTeacherChange,
}: {
  activityType: GradebookActivityType
  groupId: string | null
  groups: Array<{ id: string; label: string }>
  subjectId: string | null
  subjects: Array<{ id: string; label: string }>
  teacherId: string | null
  teachers: Array<{ id: string; label: string }>
  onActivityTypeChange: (value: GradebookActivityType) => void
  onGroupChange: (value: string | null) => void
  onReset: () => void
  onSubjectChange: (value: string | null) => void
  onTeacherChange: (value: string | null) => void
}) {
  const { t } = useTranslation()

  return (
    <Card className="space-y-4 p-4">
      <div className="grid gap-3 2xl:grid-cols-[minmax(0,1fr)_minmax(0,1fr)_minmax(0,1fr)_minmax(0,1.4fr)_220px_120px]">
        <FilterSelectField
          label={t('gradebook.labels.subject')}
          value={subjectId ?? ''}
          onChange={(event) => onSubjectChange(event.target.value || null)}
        >
          <option value="">{t('common.labels.all')}</option>
          {subjects.map((option) => (
            <option key={option.id} value={option.id}>{option.label}</option>
          ))}
        </FilterSelectField>
        <FilterSelectField
          label={t('gradebook.labels.group')}
          value={groupId ?? ''}
          onChange={(event) => onGroupChange(event.target.value || null)}
        >
          <option value="">{t('common.labels.all')}</option>
          {groups.map((option) => (
            <option key={option.id} value={option.id}>{option.label}</option>
          ))}
        </FilterSelectField>
        <FilterSelectField
          label={t('gradebook.labels.teacher')}
          value={teacherId ?? ''}
          onChange={(event) => onTeacherChange(event.target.value || null)}
        >
          <option value="">{t('common.labels.all')}</option>
          {teachers.map((option) => (
            <option key={option.id} value={option.id}>{option.label}</option>
          ))}
        </FilterSelectField>
        <FilterSegmentField
          activityType={activityType}
          label={t('gradebook.filters.activityType')}
          onActivityTypeChange={onActivityTypeChange}
        />
        <PeriodPlaceholderField />
        <div className="flex items-end">
          <Button className="min-h-12 w-full" variant="ghost" onClick={onReset}>
            {t('common.actions.reset')}
          </Button>
        </div>
      </div>
    </Card>
  )
}

function GroupRankingTable({
  rows,
  onViewDetails,
}: {
  rows: GroupRankingRow[]
  onViewDetails: (row: GroupRankingRow) => void
}) {
  const { t } = useTranslation()

  if (rows.length === 0) {
    return <EmptyState title={t('gradebook.admin.emptyTitle')} description={t('gradebook.admin.emptyDescription')} />
  }

  return (
    <Card className="overflow-hidden p-0">
      <div className="flex flex-wrap items-center justify-between gap-3 border-b border-border px-5 py-4">
        <div>
          <p className="text-xs font-semibold uppercase tracking-[0.18em] text-accent">{t('gradebook.admin.tabs.groups')}</p>
          <h2 className="mt-1 text-xl font-semibold text-text-primary">{t('gradebook.admin.rankings.groupsTitle')}</h2>
        </div>
      </div>
      <div className="table-scroll overflow-x-auto">
        <table className="min-w-full border-collapse text-left">
          <thead className="bg-surface-muted">
            <tr>
              <th className="px-4 py-3 text-xs font-semibold uppercase tracking-[0.18em] text-text-muted">{t('gradebook.labels.group')}</th>
              <th className="px-4 py-3 text-xs font-semibold uppercase tracking-[0.18em] text-text-muted">{t('gradebook.labels.studentCount')}</th>
              <th className="px-4 py-3 text-xs font-semibold uppercase tracking-[0.18em] text-text-muted">{t('gradebook.labels.subjectCount')}</th>
              <th className="px-4 py-3 text-xs font-semibold uppercase tracking-[0.18em] text-text-muted">{t('gradebook.labels.averageScore')}</th>
              <th className="px-4 py-3 text-xs font-semibold uppercase tracking-[0.18em] text-text-muted">{t('gradebook.labels.distribution')}</th>
              <th className="px-4 py-3 text-xs font-semibold uppercase tracking-[0.18em] text-text-muted">{t('gradebook.labels.missing')}</th>
              <th className="px-4 py-3 text-xs font-semibold uppercase tracking-[0.18em] text-text-muted">{t('common.labels.actions')}</th>
            </tr>
          </thead>
          <tbody>
            {rows.map((row) => (
              <tr key={row.groupId} className="border-t border-border">
                <td className="px-4 py-4">
                  <p className="font-semibold text-text-primary">{row.groupName}</p>
                </td>
                <td className="px-4 py-4 text-sm text-text-secondary">{row.studentCount}</td>
                <td className="px-4 py-4 text-sm text-text-secondary">{row.subjectCount}</td>
                <td className="px-4 py-4 text-sm font-semibold text-text-primary">{formatPercent(row.averagePercent)}</td>
                <td className="px-4 py-4">
                  <DistributionCluster average={row.averageCount} good={row.goodCount} high={row.highCount} low={row.lowCount} />
                </td>
                <td className="px-4 py-4 text-sm text-text-secondary">{row.missingCount}</td>
                <td className="px-4 py-4">
                  <Button className="min-h-9 px-3 text-xs" variant="secondary" onClick={() => onViewDetails(row)}>
                    {t('common.actions.viewDetails')}
                  </Button>
                </td>
              </tr>
            ))}
          </tbody>
        </table>
      </div>
    </Card>
  )
}

function SubjectRankingTable({
  rows,
  onViewDetails,
}: {
  rows: SubjectRankingRow[]
  onViewDetails: (row: SubjectRankingRow) => void
}) {
  const { t } = useTranslation()

  if (rows.length === 0) {
    return <EmptyState title={t('gradebook.admin.emptyTitle')} description={t('gradebook.admin.emptyDescription')} />
  }

  return (
    <Card className="overflow-hidden p-0">
      <div className="flex flex-wrap items-center justify-between gap-3 border-b border-border px-5 py-4">
        <div>
          <p className="text-xs font-semibold uppercase tracking-[0.18em] text-accent">{t('gradebook.admin.tabs.subjects')}</p>
          <h2 className="mt-1 text-xl font-semibold text-text-primary">{t('gradebook.admin.rankings.subjectsTitle')}</h2>
        </div>
      </div>
      <div className="table-scroll overflow-x-auto">
        <table className="min-w-full border-collapse text-left">
          <thead className="bg-surface-muted">
            <tr>
              <th className="px-4 py-3 text-xs font-semibold uppercase tracking-[0.18em] text-text-muted">{t('gradebook.labels.subject')}</th>
              <th className="px-4 py-3 text-xs font-semibold uppercase tracking-[0.18em] text-text-muted">{t('gradebook.labels.teacher')}</th>
              <th className="px-4 py-3 text-xs font-semibold uppercase tracking-[0.18em] text-text-muted">{t('gradebook.labels.group')}</th>
              <th className="px-4 py-3 text-xs font-semibold uppercase tracking-[0.18em] text-text-muted">{t('gradebook.labels.studentCount')}</th>
              <th className="px-4 py-3 text-xs font-semibold uppercase tracking-[0.18em] text-text-muted">{t('gradebook.labels.averageScore')}</th>
              <th className="px-4 py-3 text-xs font-semibold uppercase tracking-[0.18em] text-text-muted">{t('gradebook.labels.completion')}</th>
              <th className="px-4 py-3 text-xs font-semibold uppercase tracking-[0.18em] text-text-muted">{t('gradebook.labels.missing')}</th>
              <th className="px-4 py-3 text-xs font-semibold uppercase tracking-[0.18em] text-text-muted">{t('common.labels.actions')}</th>
            </tr>
          </thead>
          <tbody>
            {rows.map((row) => (
              <tr key={row.subjectId} className="border-t border-border">
                <td className="px-4 py-4">
                  <p className="font-semibold text-text-primary">{row.subjectName}</p>
                </td>
                <td className="px-4 py-4 text-sm text-text-secondary">
                  {row.teacherNames.join(', ') || t('gradebook.labels.noTeacher')}
                </td>
                <td className="px-4 py-4 text-sm text-text-secondary">{row.groupCount}</td>
                <td className="px-4 py-4 text-sm text-text-secondary">{row.studentCount}</td>
                <td className="px-4 py-4 text-sm font-semibold text-text-primary">{formatPercent(row.averagePercent)}</td>
                <td className="px-4 py-4 text-sm text-text-secondary">{formatPercent(row.completionPercent)}</td>
                <td className="px-4 py-4 text-sm text-text-secondary">{row.missingCount}</td>
                <td className="px-4 py-4">
                  <Button className="min-h-9 px-3 text-xs" variant="secondary" onClick={() => onViewDetails(row)}>
                    {t('common.actions.viewDetails')}
                  </Button>
                </td>
              </tr>
            ))}
          </tbody>
        </table>
      </div>
    </Card>
  )
}

function DetailPromptPanel({
  groupRankings,
  subjectRankings,
  onChooseGroup,
  onChooseSubject,
}: {
  groupRankings: GroupRankingRow[]
  subjectRankings: SubjectRankingRow[]
  onChooseGroup: (groupId: string) => void
  onChooseSubject: (subjectId: string) => void
}) {
  const { t } = useTranslation()

  return (
    <Card className="space-y-5">
      <div className="space-y-2">
        <p className="text-xs font-semibold uppercase tracking-[0.18em] text-accent">{t('gradebook.admin.tabs.details')}</p>
        <h2 className="text-xl font-semibold text-text-primary">{t('gradebook.admin.detailPromptTitle')}</h2>
        <p className="max-w-2xl text-sm leading-6 text-text-secondary">{t('gradebook.admin.detailPromptDescription')}</p>
      </div>
      <div className="grid gap-4 xl:grid-cols-2">
        <div className="space-y-3">
          <h3 className="text-sm font-semibold uppercase tracking-[0.16em] text-text-muted">{t('gradebook.admin.quickPickSubjects')}</h3>
          {subjectRankings.slice(0, 4).map((row) => (
            <QuickPickCard
              key={row.subjectId}
              primary={row.subjectName}
              secondary={`${row.groupCount} ${t('gradebook.labels.group').toLowerCase()}`}
              tertiary={formatPercent(row.averagePercent)}
              onClick={() => onChooseSubject(row.subjectId)}
            />
          ))}
        </div>
        <div className="space-y-3">
          <h3 className="text-sm font-semibold uppercase tracking-[0.16em] text-text-muted">{t('gradebook.admin.quickPickGroups')}</h3>
          {groupRankings.slice(0, 4).map((row) => (
            <QuickPickCard
              key={row.groupId}
              primary={row.groupName}
              secondary={`${row.subjectCount} ${t('gradebook.labels.subjectCount').toLowerCase()}`}
              tertiary={formatPercent(row.averagePercent)}
              onClick={() => onChooseGroup(row.groupId)}
            />
          ))}
        </div>
      </div>
    </Card>
  )
}

function MatrixSummaryCards({ matrix }: { matrix: GradebookMatrixResponse }) {
  const { t } = useTranslation()

  return (
    <div className="grid gap-3 md:grid-cols-2 xl:grid-cols-5">
      <AnalyticMetricCard title={t('gradebook.metrics.groupAverage')} value={formatPercent(matrix.summary.groupAveragePercent)} />
      <AnalyticMetricCard title={t('gradebook.metrics.highest')} value={formatPercent(matrix.summary.highestPercent)} />
      <AnalyticMetricCard title={t('gradebook.metrics.lowest')} value={formatPercent(matrix.summary.lowestPercent)} />
      <AnalyticMetricCard title={t('gradebook.metrics.missing')} value={matrix.summary.missingCount} />
      <AnalyticMetricCard title={t('gradebook.metrics.pending')} value={matrix.summary.pendingCount} />
    </div>
  )
}

function GradeMatrix({ matrix, mode }: { matrix: GradebookMatrixResponse; mode: 'teacher' | 'admin' }) {
  const { t } = useTranslation()

  return (
    <Card className="space-y-4 overflow-hidden p-0">
      <div className="flex flex-wrap items-center justify-between gap-4 border-b border-border px-5 py-4">
        <div>
          <p className="text-xs font-semibold uppercase tracking-[0.2em] text-accent">{matrix.groupName}</p>
          <h2 className="mt-1 text-xl font-semibold text-text-primary">{matrix.subjectName}</h2>
          <p className="mt-1 text-sm text-text-secondary">{matrix.teacherNames.join(', ') || t('gradebook.labels.noTeacher')}</p>
        </div>
        <div className="flex flex-wrap gap-2">
          <LegendPill tone="good" label={t('gradebook.legend.high')} />
          <LegendPill tone="average" label={t('gradebook.legend.average')} />
          <LegendPill tone="missing" label={t('gradebook.legend.missing')} />
          <LegendPill tone="pending" label={t('gradebook.legend.pending')} />
        </div>
      </div>
      <div className="table-scroll overflow-x-auto">
        <table className="min-w-max border-collapse text-left">
          <thead className="bg-surface-muted">
            <tr>
              <th className="sticky left-0 z-20 min-w-[240px] border-r border-border bg-surface-muted px-4 py-3 text-xs font-semibold uppercase tracking-[0.18em] text-text-muted">
                {t('gradebook.labels.student')}
              </th>
              {matrix.activities.map((activity) => (
                <th key={activity.activityId} className="min-w-[150px] border-r border-border px-4 py-3 align-top text-xs font-semibold uppercase tracking-[0.18em] text-text-muted last:border-r-0">
                  <div className="space-y-1">
                    <p className="whitespace-normal text-text-primary">{activity.title}</p>
                    <p>{activity.activityType === 'ASSIGNMENT' ? t('gradebook.filters.assignments') : t('gradebook.filters.tests')}</p>
                    <p>{activity.maxPoints} {t('gradebook.labels.points')}</p>
                  </div>
                </th>
              ))}
              <th className="min-w-[140px] border-l border-border px-4 py-3 text-xs font-semibold uppercase tracking-[0.18em] text-text-muted">
                {t('gradebook.labels.averageScore')}
              </th>
              <th className="min-w-[120px] px-4 py-3 text-xs font-semibold uppercase tracking-[0.18em] text-text-muted">
                {t('gradebook.labels.missing')}
              </th>
            </tr>
          </thead>
          <tbody>
            {matrix.students.map((student) => (
              <tr key={student.studentId} className="border-t border-border">
                <td className="sticky left-0 z-10 border-r border-border bg-surface px-4 py-3 align-top">
                  <p className="font-semibold text-text-primary">{student.studentName}</p>
                  <p className="text-xs text-text-muted">{t('gradebook.labels.studentId')} {student.studentId.slice(0, 8)}</p>
                </td>
                {student.cells.map((cell, index) => (
                  <td key={`${student.studentId}-${cell.activityId}`} className="border-r border-border px-3 py-3 align-top last:border-r-0">
                    <MatrixCell cell={cell} activity={matrix.activities[index]} mode={mode} />
                  </td>
                ))}
                <td className="border-l border-border px-4 py-3 align-top text-sm font-semibold text-text-primary">{formatPercent(student.averagePercent)}</td>
                <td className="px-4 py-3 align-top text-sm text-text-secondary">{student.missingCount}</td>
              </tr>
            ))}
          </tbody>
        </table>
      </div>
    </Card>
  )
}

function MatrixCell({
  cell,
  activity,
  mode,
}: {
  cell: GradebookMatrixCellResponse
  activity: GradebookMatrixResponse['activities'][number]
  mode: 'teacher' | 'admin'
}) {
  const { t } = useTranslation()
  const tone = scoreTone(cell)
  const route = cell.submissionId
    ? `/review/${cell.submissionId}`
    : cell.resultId
      ? `/tests/${activity.activityId}/results/${cell.resultId}/review`
      : null

  const content = (
    <div className={`space-y-2 rounded-[14px] border px-3 py-3 text-sm ${tone}`}>
      <div className="flex items-center justify-between gap-2">
        <span className="font-semibold">{cell.score == null ? '—' : `${cell.score}/${cell.maxPoints}`}</span>
        <StatusPill compact status={cell.status} />
      </div>
      <p className="text-xs text-text-secondary">{cell.percentage == null ? '—' : `${Math.round(cell.percentage)}%`}</p>
      {cell.late ? <p className="text-xs font-semibold text-warning">{t('gradebook.labels.late')}</p> : null}
    </div>
  )

  if (!route || mode === 'admin') {
    return content
  }

  return <Link to={route}>{content}</Link>
}

function FilterSelectField({
  children,
  label,
  value,
  onChange,
}: {
  children: ReactNode
  label: string
  value: string
  onChange: ChangeEventHandler<HTMLSelectElement>
}) {
  return (
    <label className="flex flex-col gap-2 text-sm text-text-secondary">
      <span>{label}</span>
      <Select className="min-h-12" value={value} onChange={onChange}>
        {children}
      </Select>
    </label>
  )
}

function FilterSegmentField({
  activityType,
  label,
  onActivityTypeChange,
}: {
  activityType: GradebookActivityType
  label: string
  onActivityTypeChange: (value: GradebookActivityType) => void
}) {
  const { t } = useTranslation()

  return (
    <div className="space-y-2">
      <span className="text-sm text-text-secondary">{label}</span>
      <SegmentedControl
        ariaLabel={label}
        className="w-full"
        value={activityType}
        onChange={onActivityTypeChange}
        options={activityFilterOptions.map((option) => ({ value: option.value, label: t(option.labelKey) }))}
      />
    </div>
  )
}

function PeriodPlaceholderField() {
  const { t } = useTranslation()

  return (
    <div className="rounded-[16px] border border-border bg-surface-muted px-4 py-3">
      <p className="text-sm text-text-secondary">{t('gradebook.labels.period')}</p>
      <p className="mt-2 text-sm font-semibold text-text-primary">{t('gradebook.filters.periodPlaceholder')}</p>
    </div>
  )
}

function AnalyticMetricCard({
  hint,
  title,
  value,
}: {
  hint?: string
  title: string
  value: string | number
}) {
  return (
    <Card className="space-y-2 rounded-[18px] border border-border bg-[radial-gradient(circle_at_top_left,rgba(255,255,255,0.08),transparent_55%),linear-gradient(180deg,rgba(255,255,255,0.02),rgba(255,255,255,0))] p-4">
      <p className="text-[11px] font-semibold uppercase tracking-[0.2em] text-text-muted">{title}</p>
      <p className="text-3xl font-bold tracking-[-0.04em] text-text-primary">{value}</p>
      {hint ? <p className="text-xs leading-5 text-text-secondary">{hint}</p> : null}
    </Card>
  )
}

function DistributionCluster({
  average,
  good,
  high,
  low,
}: {
  average: number
  good: number
  high: number
  low: number
}) {
  const { t } = useTranslation()

  return (
    <div className="flex flex-wrap gap-2">
      <DistributionPill tone="good" label={t('gradebook.distribution.high')} value={high} />
      <DistributionPill tone="success" label={t('gradebook.distribution.good')} value={good} />
      <DistributionPill tone="average" label={t('gradebook.distribution.average')} value={average} />
      <DistributionPill tone="missing" label={t('gradebook.distribution.low')} value={low} />
    </div>
  )
}

function DistributionPill({
  tone,
  label,
  value,
}: {
  tone: 'average' | 'good' | 'missing' | 'success'
  label: string
  value: number
}) {
  const toneClass = tone === 'good'
    ? 'border-success/25 bg-success/10 text-success'
    : tone === 'success'
      ? 'border-accent/25 bg-accent/10 text-accent'
      : tone === 'average'
        ? 'border-warning/25 bg-warning/10 text-warning'
        : 'border-danger/25 bg-danger/10 text-danger'

  return (
    <span className={`inline-flex items-center gap-1 rounded-full border px-2.5 py-1 text-xs font-semibold ${toneClass}`}>
      <span>{label}</span>
      <span>{value}</span>
    </span>
  )
}

function QuickPickCard({
  primary,
  secondary,
  tertiary,
  onClick,
}: {
  primary: string
  secondary: string
  tertiary: string
  onClick: () => void
}) {
  return (
    <button
      className="flex w-full items-center justify-between rounded-[16px] border border-border bg-surface-muted px-4 py-3 text-left transition hover:border-border-strong hover:bg-surface"
      type="button"
      onClick={onClick}
    >
      <div className="space-y-1">
        <p className="font-semibold text-text-primary">{primary}</p>
        <p className="text-sm text-text-secondary">{secondary}</p>
      </div>
      <span className="rounded-full border border-border bg-surface px-3 py-1 text-xs font-semibold text-accent">{tertiary}</span>
    </button>
  )
}

function ExportFeedbackBanner({ feedback }: { feedback: ExportFeedback | null }) {
  const { t } = useTranslation()

  if (!feedback) {
    return null
  }

  return (
    <div className="rounded-[16px] border border-danger/30 bg-danger/10 px-4 py-3 text-sm text-danger">
      <p className="font-semibold">{feedback.message}</p>
      {feedback.requestId ? <p className="mt-1 text-xs">{t('common.labels.requestId')}: {feedback.requestId}</p> : null}
    </div>
  )
}

function ScoreValue({ score, maxPoints }: { score: number | null; maxPoints: number }) {
  return <span className="font-medium text-text-primary">{score == null ? '—' : `${score}/${maxPoints}`}</span>
}

function StatusPill({ status, compact = false }: { status: GradebookActivityStatus; compact?: boolean }) {
  const { t } = useTranslation()
  const tone = statusTone(status)

  return <span className={`inline-flex items-center rounded-full px-2.5 py-1 text-xs font-semibold ${compact ? '' : 'border'} ${tone}`}>{t(`gradebook.status.${status}`)}</span>
}

function LegendPill({ tone, label }: { tone: 'good' | 'average' | 'missing' | 'pending'; label: string }) {
  return <span className={`inline-flex items-center rounded-full px-3 py-1 text-xs font-semibold ${legendTone[tone]}`}>{label}</span>
}

function CompactStat({ label, value }: { label: string; value: string | number }) {
  return (
    <div className="rounded-[16px] border border-border bg-surface-muted px-4 py-3">
      <p className="text-xs font-semibold uppercase tracking-[0.16em] text-text-muted">{label}</p>
      <p className="mt-2 text-lg font-semibold text-text-primary">{value}</p>
    </div>
  )
}

function StatusMetric({ value, label }: { value: string; label: string }) {
  return (
    <div className="rounded-[18px] border border-accent/30 bg-accent/10 px-4 py-3 text-right">
      <p className="text-xs font-semibold uppercase tracking-[0.16em] text-accent">{label}</p>
      <p className="mt-2 text-2xl font-bold text-text-primary">{value}</p>
    </div>
  )
}

interface ExportFeedback {
  message: string
  requestId: string | null
}

function useGradebookExport() {
  const { t } = useTranslation()
  const [feedback, setFeedback] = useState<ExportFeedback | null>(null)

  const mutation = useMutation({
    mutationFn: async ({ filename, params }: { filename: string; params: GradebookExportParams }) => {
      const blob = await gradebookService.export(params)
      downloadBlob(blob, filename)
    },
    onMutate: () => {
      setFeedback(null)
    },
    onError: (error) => {
      const normalized = normalizeApiError(error)
      setFeedback({
        message: getLocalizedRequestErrorMessage(error, t, 'gradebook.exportFailed'),
        requestId: normalized?.requestId ?? null,
      })
    },
  })

  return {
    startExport: (params: GradebookExportParams, filename: string) => mutation.mutate({ filename, params }),
    isPending: mutation.isPending,
    feedback,
  }
}

function buildGroupRankings(rows: GradebookAdminOverviewRowResponse[]): GroupRankingRow[] {
  const grouped = new Map<string, {
    averageWeight: number
    averageWeightedSum: number
    groupId: string
    groupName: string
    highCount: number
    goodCount: number
    averageCount: number
    lowCount: number
    missingCount: number
    studentCount: number
    subjectIds: Set<string>
  }>()

  rows.forEach((row) => {
    const current = grouped.get(row.groupId) ?? {
      averageWeight: 0,
      averageWeightedSum: 0,
      groupId: row.groupId,
      groupName: row.groupName,
      highCount: 0,
      goodCount: 0,
      averageCount: 0,
      lowCount: 0,
      missingCount: 0,
      studentCount: 0,
      subjectIds: new Set<string>(),
    }

    current.studentCount = Math.max(current.studentCount, row.studentCount)
    current.subjectIds.add(row.subjectId)
    current.highCount += row.highCount
    current.goodCount += row.goodCount
    current.averageCount += row.averageCount
    current.lowCount += row.lowCount
    current.missingCount += row.missingCount

    if (row.averagePercent != null) {
      current.averageWeightedSum += row.averagePercent * row.studentCount
      current.averageWeight += row.studentCount
    }

    grouped.set(row.groupId, current)
  })

  return Array.from(grouped.values())
    .map((row) => ({
      groupId: row.groupId,
      groupName: row.groupName,
      studentCount: row.studentCount,
      subjectCount: row.subjectIds.size,
      averagePercent: row.averageWeight === 0 ? null : round(row.averageWeightedSum / row.averageWeight),
      highCount: row.highCount,
      goodCount: row.goodCount,
      averageCount: row.averageCount,
      lowCount: row.lowCount,
      missingCount: row.missingCount,
      subjectIds: Array.from(row.subjectIds),
    }))
    .sort((left, right) => compareNullableNumbers(right.averagePercent, left.averagePercent) || left.groupName.localeCompare(right.groupName))
}

function buildSubjectRankings(rows: GradebookAdminOverviewRowResponse[]): SubjectRankingRow[] {
  const grouped = new Map<string, {
    averageWeight: number
    averageWeightedSum: number
    completionWeight: number
    completionWeightedSum: number
    subjectId: string
    subjectName: string
    teacherNames: Set<string>
    groupIds: Set<string>
    missingCount: number
    studentCount: number
  }>()

  rows.forEach((row) => {
    const current = grouped.get(row.subjectId) ?? {
      averageWeight: 0,
      averageWeightedSum: 0,
      completionWeight: 0,
      completionWeightedSum: 0,
      subjectId: row.subjectId,
      subjectName: row.subjectName,
      teacherNames: new Set<string>(),
      groupIds: new Set<string>(),
      missingCount: 0,
      studentCount: 0,
    }

    row.teacherNames.forEach((teacherName) => current.teacherNames.add(teacherName))
    current.groupIds.add(row.groupId)
    current.missingCount += row.missingCount
    current.studentCount += row.studentCount

    if (row.averagePercent != null) {
      current.averageWeightedSum += row.averagePercent * row.studentCount
      current.averageWeight += row.studentCount
    }

    if (row.completionPercent != null) {
      current.completionWeightedSum += row.completionPercent * row.studentCount
      current.completionWeight += row.studentCount
    }

    grouped.set(row.subjectId, current)
  })

  return Array.from(grouped.values())
    .map((row) => ({
      subjectId: row.subjectId,
      subjectName: row.subjectName,
      teacherNames: Array.from(row.teacherNames),
      groupCount: row.groupIds.size,
      studentCount: row.studentCount,
      averagePercent: row.averageWeight === 0 ? null : round(row.averageWeightedSum / row.averageWeight),
      completionPercent: row.completionWeight === 0 ? null : round(row.completionWeightedSum / row.completionWeight),
      missingCount: row.missingCount,
      groupIds: Array.from(row.groupIds),
    }))
    .sort((left, right) => compareNullableNumbers(right.averagePercent, left.averagePercent) || left.subjectName.localeCompare(right.subjectName))
}

function compareNullableNumbers(left: number | null, right: number | null) {
  if (left == null && right == null) {
    return 0
  }
  if (left == null) {
    return 1
  }
  if (right == null) {
    return -1
  }
  return left - right
}

function round(value: number) {
  return Math.round(value * 10) / 10
}

function selectedLabel(options: Array<{ id: string; label: string }>, id: string | null) {
  return options.find((option) => option.id === id)?.label ?? '—'
}

function scoreTone(cell: GradebookMatrixCellResponse) {
  if (cell.status === 'MISSING') return legendTone.missing
  if (cell.status === 'PENDING_REVIEW') return legendTone.pending
  if (cell.percentage == null) return 'border-border bg-surface-muted text-text-primary'
  if (cell.percentage >= 90) return legendTone.good
  if (cell.percentage >= 60) return legendTone.average
  return 'border-danger/35 bg-danger/10 text-text-primary'
}

function statusTone(status: GradebookActivityStatus) {
  if (status === 'GRADED') return 'border-success/30 bg-success/10 text-success'
  if (status === 'PENDING_REVIEW') return 'border-warning/30 bg-warning/10 text-warning'
  if (status === 'MISSING') return 'border-danger/30 bg-danger/10 text-danger'
  return 'border-border bg-surface-muted text-text-secondary'
}

const legendTone = {
  average: 'border-warning/30 bg-warning/10 text-warning',
  good: 'border-success/30 bg-success/10 text-success',
  missing: 'border-danger/30 bg-danger/10 text-danger',
  pending: 'border-border-strong bg-surface-muted text-text-primary',
}

function formatPercent(value: number | null) {
  return value == null ? '—' : `${Math.round(value)}%`
}

function detailLink(activity: GradebookActivityRowResponse) {
  return activity.activityType === 'ASSIGNMENT'
    ? `/assignments/${activity.activityId}`
    : `/tests/${activity.activityId}`
}
