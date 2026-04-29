import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { FolderKanban, Plus, Users } from 'lucide-react'
import { useMemo, useState } from 'react'
import { useTranslation } from 'react-i18next'
import { Link } from 'react-router-dom'

import { useAuth } from '@/features/auth/useAuth'
import { loadAccessibleGroups } from '@/pages/education/helpers'
import { educationService } from '@/shared/api/services'
import { formatDate } from '@/shared/lib/format'
import { hasAnyRole } from '@/shared/lib/roles'
import { Button } from '@/shared/ui/Button'
import { Card } from '@/shared/ui/Card'
import { FormField } from '@/shared/ui/FormField'
import { Input } from '@/shared/ui/Input'
import { PageHeader } from '@/shared/ui/PageHeader'
import { EmptyState, ErrorState, LoadingState } from '@/shared/ui/StateViews'

export function GroupsPage() {
  const { primaryRole, roles, session } = useAuth()
  const canManageGroups = hasAnyRole(roles, ['ADMIN', 'OWNER'])

  if (canManageGroups) {
    return <ManagedGroupsPage />
  }

  return <AccessibleGroupsPage role={primaryRole} userId={session?.user.id ?? ''} />
}

function ManagedGroupsPage() {
  const { t } = useTranslation()
  const queryClient = useQueryClient()
  const [query, setQuery] = useState('')
  const [page, setPage] = useState(0)
  const [groupName, setGroupName] = useState('')
  const normalizedQuery = query.trim()

  const groupsQuery = useQuery({
    queryKey: ['education', 'groups', 'managed', normalizedQuery, page],
    queryFn: () => educationService.listGroups({
      page,
      size: 12,
      q: normalizedQuery || undefined,
      sortBy: 'name',
      direction: 'asc',
    }),
  })
  const createMutation = useMutation({
    mutationFn: () => educationService.createGroup({ name: groupName.trim() }),
    onSuccess: async () => {
      setGroupName('')
      setPage(0)
      await queryClient.invalidateQueries({ queryKey: ['education', 'groups'] })
    },
  })

  const groups = groupsQuery.data?.items ?? []

  return (
    <div className="space-y-6">
      <PageHeader
        description={t('education.groupsLandingDescription')}
        title={t('navigation.shared.groups')}
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
        </div>
      </Card>

      {groupsQuery.isLoading ? <LoadingState /> : null}
      {groupsQuery.isError ? <ErrorState description={t('common.states.error')} title={t('navigation.shared.groups')} /> : null}
      {!groupsQuery.isLoading && !groupsQuery.isError && groups.length === 0 ? (
        <EmptyState description={t('education.emptyGroupsSearch')} title={t('navigation.shared.groups')} />
      ) : null}
      {groups.length > 0 ? (
        <>
          <div className="grid gap-4 md:grid-cols-2 xl:grid-cols-3">
            {groups.map((group) => (
              <GroupCard
                key={group.id}
                createdAt={group.createdAt}
                name={group.name}
                to={`/groups/${group.id}`}
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
              disabled={groupsQuery.data?.last ?? true}
              variant="secondary"
              onClick={() => setPage((current) => current + 1)}
            >
              {t('common.actions.next')}
            </Button>
          </div>
        </>
      ) : null}
    </div>
  )
}

function AccessibleGroupsPage({
  role,
  userId,
}: {
  role: 'TEACHER' | 'STUDENT' | 'USER' | 'ADMIN' | 'OWNER'
  userId: string
}) {
  const { t } = useTranslation()
  const [query, setQuery] = useState('')
  const groupsQuery = useQuery({
    queryKey: ['education', 'groups', 'accessible', role, userId],
    queryFn: () => loadAccessibleGroups(role, userId),
    enabled: Boolean(userId),
  })
  const filteredGroups = useMemo(
    () => (groupsQuery.data ?? []).filter((group) => (
      query.trim() ? group.name.toLowerCase().includes(query.trim().toLowerCase()) : true
    )),
    [groupsQuery.data, query],
  )

  if (groupsQuery.isLoading) {
    return <LoadingState />
  }

  if (groupsQuery.isError) {
    return <ErrorState description={t('common.states.error')} title={t('navigation.shared.groups')} />
  }

  return (
    <div className="space-y-6">
      <PageHeader
        description={role === 'TEACHER' ? t('education.teacherGroupsDescription') : t('education.studentGroupsDescription')}
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
        <div className="grid gap-4 md:grid-cols-2 xl:grid-cols-3">
          {filteredGroups.map((group) => (
            <GroupCard
              key={group.id}
              createdAt={group.createdAt}
              name={group.name}
              to={`/groups/${group.id}`}
            />
          ))}
        </div>
      )}
    </div>
  )
}

function GroupCard({
  createdAt,
  name,
  to,
}: {
  createdAt: string
  name: string
  to: string
}) {
  const { t } = useTranslation()

  return (
    <Card className="gradient-card flex h-full flex-col justify-between gap-4">
      <div className="space-y-4">
        <div className="flex items-start gap-3">
          <span className="inline-flex h-11 w-11 shrink-0 items-center justify-center rounded-[16px] bg-accent-muted text-accent">
            <FolderKanban className="h-5 w-5" />
          </span>
          <div className="min-w-0">
            <h2 className="break-words text-lg font-semibold text-text-primary">{name}</h2>
            <p className="text-sm text-text-muted">{t('education.groupHubLabel')}</p>
          </div>
        </div>
        <div className="grid gap-2 text-sm text-text-secondary">
          <p>{t('education.groupScheduleStatusUnknown')}</p>
          <p>{t('education.groupRiskUnknown')}</p>
          <p>{t('education.createdAt')}: {formatDate(createdAt)}</p>
        </div>
      </div>
      <Link to={to}>
        <Button fullWidth variant="secondary">
          <Users className="mr-2 h-4 w-4" />
          {t('common.actions.open')}
        </Button>
      </Link>
    </Card>
  )
}
