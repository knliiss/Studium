import { useQuery } from '@tanstack/react-query'
import { useTranslation } from 'react-i18next'

import { useAuth } from '@/features/auth/useAuth'
import { educationService, specialtyService, userDirectoryService } from '@/shared/api/services'
import { formatDate } from '@/shared/lib/format'
import { Card } from '@/shared/ui/Card'
import { DataTable } from '@/shared/ui/DataTable'
import { PageHeader } from '@/shared/ui/PageHeader'
import { EmptyState, ErrorState, LoadingState } from '@/shared/ui/StateViews'
import { UserAvatar } from '@/shared/ui/UserAvatar'
import { Breadcrumbs } from '@/widgets/common/Breadcrumbs'

export function MyGroupPage() {
  const { t } = useTranslation()
  const { session } = useAuth()
  const currentUserId = session?.user.id ?? ''

  const membershipsQuery = useQuery({
    queryKey: ['education', 'my-group', 'memberships', currentUserId],
    queryFn: () => educationService.getGroupsByUser(currentUserId),
    enabled: Boolean(currentUserId),
  })
  const membership = membershipsQuery.data?.[0] ?? null

  const groupQuery = useQuery({
    queryKey: ['education', 'my-group', 'group', membership?.groupId],
    queryFn: () => educationService.getGroup(membership!.groupId),
    enabled: Boolean(membership?.groupId),
  })
  const membersQuery = useQuery({
    queryKey: ['education', 'my-group', 'members', membership?.groupId],
    queryFn: () => educationService.listGroupStudents(membership!.groupId),
    enabled: Boolean(membership?.groupId),
  })
  const usersQuery = useQuery({
    queryKey: ['education', 'my-group', 'users', membership?.groupId],
    queryFn: () => userDirectoryService.lookup((membersQuery.data ?? []).map((row) => row.userId)),
    enabled: Boolean(membership?.groupId) && (membersQuery.data?.length ?? 0) > 0,
  })
  const specialtyQuery = useQuery({
    queryKey: ['education', 'my-group', 'specialty', groupQuery.data?.specialtyId],
    queryFn: () => specialtyService.getById(groupQuery.data!.specialtyId!),
    enabled: Boolean(groupQuery.data?.specialtyId),
  })

  if (membershipsQuery.isLoading) {
    return <LoadingState />
  }

  if (membershipsQuery.isError) {
    return <ErrorState description={t('common.states.error')} title={t('navigation.shared.myGroup')} />
  }

  if (!membership) {
    return (
      <div className="space-y-6">
        <Breadcrumbs items={[{ label: t('navigation.shared.education'), to: '/education' }, { label: t('navigation.shared.myGroup') }]} />
        <PageHeader
          description={t('education.myGroup.description')}
          title={t('navigation.shared.myGroup')}
        />
        <EmptyState
          description={t('education.myGroup.noGroupAssigned')}
          title={t('navigation.shared.myGroup')}
        />
      </div>
    )
  }

  if (groupQuery.isLoading || membersQuery.isLoading || usersQuery.isLoading || specialtyQuery.isLoading) {
    return <LoadingState />
  }

  if (groupQuery.isError || membersQuery.isError || usersQuery.isError || specialtyQuery.isError || !groupQuery.data) {
    return <ErrorState description={t('common.states.error')} title={t('navigation.shared.myGroup')} />
  }

  const group = groupQuery.data
  const members = membersQuery.data ?? []
  const usersById = new Map((usersQuery.data ?? []).map((row) => [row.id, row]))
  const starostaMembership = members.find((row) => row.role === 'STAROSTA') ?? null
  const starostaUser = starostaMembership ? usersById.get(starostaMembership.userId) : null
  const currentMember = members.find((row) => row.userId === currentUserId) ?? membership
  const specialtyName = specialtyQuery.data?.name ?? t('education.myGroup.noSpecialty')

  return (
    <div className="space-y-6">
      <Breadcrumbs items={[{ label: t('navigation.shared.education'), to: '/education' }, { label: t('navigation.shared.myGroup') }]} />

      <PageHeader
        description={t('education.myGroup.description')}
        title={t('navigation.shared.myGroup')}
      />

      <Card className="space-y-4">
        <div className="grid gap-4 md:grid-cols-2 xl:grid-cols-3">
          <SummaryItem label={t('education.group')} value={group.name} />
          <SummaryItem label={t('navigation.shared.specialties')} value={specialtyName} />
          <SummaryItem
            label={t('academic.studyYear')}
            value={group.studyYear ? t('academic.studyYearValue', { year: group.studyYear }) : t('education.myGroup.notAvailable')}
          />
          <SummaryItem label={t('education.subgroup')} value={t(`education.subgroups.${currentMember.subgroup}`)} />
          <SummaryItem
            label={t('education.overview.starosta')}
            value={starostaUser?.username ?? t('education.myGroup.notAssigned')}
          />
          <SummaryItem label={t('education.overview.students')} value={String(members.length)} />
        </div>
      </Card>

      <Card className="space-y-4">
        <PageHeader
          description={t('education.myGroup.membersDescription')}
          title={t('education.myGroup.membersTitle')}
        />

        {members.length === 0 ? (
          <EmptyState description={t('education.noGroupStudents')} title={t('education.myGroup.membersTitle')} />
        ) : (
          <DataTable
            columns={[
              {
                key: 'student',
                header: t('education.student'),
                render: (row) => {
                  const user = usersById.get(row.userId)
                  return (
                    <div className="flex items-start gap-3">
                      <UserAvatar alt={user?.username ?? t('education.unknownStudent')} email={user?.email} username={user?.username} size="sm" />
                      <div className="space-y-1">
                        <p className="font-medium text-text-primary">{user?.username ?? t('education.unknownStudent')}</p>
                        {user?.email ? <p className="text-xs text-text-secondary">{user.email}</p> : null}
                      </div>
                    </div>
                  )
                },
              },
              {
                key: 'role',
                header: t('education.groupRole'),
                render: (row) => <span className="text-sm font-medium text-text-primary">{t(`education.memberRole.${row.role}`)}</span>,
              },
              {
                key: 'subgroup',
                header: t('education.subgroup'),
                render: (row) => <span className="text-sm font-medium text-text-primary">{t(`education.subgroups.${row.subgroup}`)}</span>,
              },
              {
                key: 'createdAt',
                header: t('education.createdAt'),
                render: (row) => formatDate(row.createdAt),
              },
            ]}
            rows={members}
          />
        )}
      </Card>
    </div>
  )
}

function SummaryItem({ label, value }: { label: string; value: string }) {
  return (
    <div className="rounded-[14px] border border-border bg-surface-muted px-4 py-3">
      <p className="text-xs font-semibold uppercase tracking-[0.14em] text-text-muted">{label}</p>
      <p className="mt-2 text-sm font-semibold text-text-primary">{value}</p>
    </div>
  )
}
