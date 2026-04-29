import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { useMemo, useState } from 'react'
import { useTranslation } from 'react-i18next'

import { useAuth } from '@/features/auth/useAuth'
import { adminUserService } from '@/shared/api/services'
import { getLocalizedRequestErrorMessage } from '@/shared/lib/api-errors'
import { formatDateTime } from '@/shared/lib/format'
import { hasAnyRole } from '@/shared/lib/roles'
import type { Role } from '@/shared/types/api'
import { Button } from '@/shared/ui/Button'
import { Card } from '@/shared/ui/Card'
import { ConfirmDialog } from '@/shared/ui/ConfirmDialog'
import { DataTable } from '@/shared/ui/DataTable'
import { FormField } from '@/shared/ui/FormField'
import { Input } from '@/shared/ui/Input'
import { PageHeader } from '@/shared/ui/PageHeader'
import { Select } from '@/shared/ui/Select'
import { ErrorState, LoadingState } from '@/shared/ui/StateViews'
import { UserAvatar } from '@/shared/ui/UserAvatar'
import { MetricCard } from '@/widgets/common/MetricCard'
import { RoleBadge } from '@/widgets/common/RoleBadge'
import { StatusBadge } from '@/widgets/common/StatusBadge'

export function AdminUsersPage() {
  const { t } = useTranslation()
  const { roles } = useAuth()
  const queryClient = useQueryClient()
  const [search, setSearch] = useState('')
  const [selectedUserId, setSelectedUserId] = useState('')
  const [selectedRoles, setSelectedRoles] = useState<Role[]>([])
  const [banReason, setBanReason] = useState('')
  const [roleFilter, setRoleFilter] = useState('')
  const [statusFilter, setStatusFilter] = useState<'ALL' | 'ACTIVE' | 'BANNED'>('ALL')
  const [feedbackMessage, setFeedbackMessage] = useState<string | null>(null)
  const [errorMessage, setErrorMessage] = useState<string | null>(null)
  const [showRoleConfirm, setShowRoleConfirm] = useState(false)
  const roleFilterOptions: Role[] = ['OWNER', 'ADMIN', 'TEACHER', 'STUDENT', 'USER']
  const assignableRoleOptions: Role[] = hasAnyRole(roles, ['OWNER'])
    ? ['ADMIN', 'TEACHER', 'STUDENT', 'USER']
    : ['TEACHER', 'STUDENT', 'USER']
  const normalizedSearch = search.trim()
  const bannedFilter = statusFilter === 'ALL' ? undefined : statusFilter === 'BANNED'

  const statsQuery = useQuery({
    queryKey: ['admin-users', 'stats'],
    queryFn: () => adminUserService.getStats(),
  })
  const usersQuery = useQuery({
    queryKey: ['admin-users', 'list', normalizedSearch, roleFilter, bannedFilter],
    queryFn: () =>
      adminUserService.list({
        search: normalizedSearch || undefined,
        role: roleFilter || undefined,
        banned: bannedFilter,
        page: 0,
        size: 50,
      }),
  })
  const bannedUsersQuery = useQuery({
    queryKey: ['admin-users', 'banned', normalizedSearch, roleFilter],
    queryFn: () =>
      adminUserService.list({
        search: normalizedSearch || undefined,
        role: roleFilter || undefined,
        banned: true,
        page: 0,
        size: 200,
      }),
    enabled: statusFilter !== 'ACTIVE',
  })
  const selectedUserQuery = useQuery({
    queryKey: ['admin-users', 'selected', selectedUserId],
    queryFn: () => adminUserService.getById(selectedUserId),
    enabled: Boolean(selectedUserId),
  })
  const selectedUserBanStateQuery = useQuery({
    queryKey: ['admin-users', 'selected-ban-state', selectedUserId, selectedUserQuery.data?.username],
    queryFn: async () => {
      if (!selectedUserQuery.data) {
        return false
      }

      const response = await adminUserService.list({
        search: selectedUserQuery.data.username,
        banned: true,
        page: 0,
        size: 20,
      })

      return response.content.some((user) => user.id === selectedUserQuery.data?.id)
    },
    enabled: Boolean(selectedUserId && selectedUserQuery.data),
  })

  const updateRoleMutation = useMutation({
    mutationFn: () => adminUserService.updateRoles(selectedUserId, selectedRoles),
    onSuccess: async () => {
      await queryClient.invalidateQueries({ queryKey: ['admin-users'] })
      await queryClient.invalidateQueries({ queryKey: ['admin-users', 'selected', selectedUserId] })
      setFeedbackMessage(t('adminUsers.rolesUpdatedSuccess'))
      setErrorMessage(null)
    },
    onError: (error) => {
      setErrorMessage(getLocalizedRequestErrorMessage(error, t))
      setFeedbackMessage(null)
    },
  })
  const banMutation = useMutation({
    mutationFn: () => adminUserService.ban(selectedUserId, banReason),
    onSuccess: async () => {
      await queryClient.invalidateQueries({ queryKey: ['admin-users'] })
      await queryClient.invalidateQueries({ queryKey: ['admin-users', 'selected', selectedUserId] })
      setBanReason('')
      setFeedbackMessage(t('adminUsers.userBannedSuccess'))
      setErrorMessage(null)
    },
    onError: (error) => {
      setErrorMessage(getLocalizedRequestErrorMessage(error, t))
      setFeedbackMessage(null)
    },
  })
  const unbanMutation = useMutation({
    mutationFn: () => adminUserService.unban(selectedUserId),
    onSuccess: async () => {
      await queryClient.invalidateQueries({ queryKey: ['admin-users'] })
      await queryClient.invalidateQueries({ queryKey: ['admin-users', 'selected', selectedUserId] })
      setBanReason('')
      setFeedbackMessage(t('adminUsers.userUnbannedSuccess'))
      setErrorMessage(null)
    },
    onError: (error) => {
      setErrorMessage(getLocalizedRequestErrorMessage(error, t))
      setFeedbackMessage(null)
    },
  })

  const visibleBannedIds = useMemo(
    () => new Set((bannedUsersQuery.data?.content ?? []).map((user) => user.id)),
    [bannedUsersQuery.data],
  )

  if (statsQuery.isLoading || usersQuery.isLoading) {
    return <LoadingState />
  }

  if (statsQuery.isError || usersQuery.isError || !statsQuery.data || !usersQuery.data) {
    return <ErrorState title={t('navigation.admin.users')} description={t('common.states.error')} />
  }

  const selectedUser = selectedUserQuery.data
  const selectedUserIsBanned = statusFilter === 'BANNED'
    ? true
    : Boolean(selectedUserBanStateQuery.data ?? (selectedUser?.status === 'BANNED'))
  const statusLabel = selectedUserIsBanned ? 'BANNED' : selectedUser?.disabled ? 'DISABLED' : 'ACTIVE'
  const hasHighPrivilege = selectedRoles.includes('ADMIN')
  const invalidSelectedRoles = selectedRoles.includes('OWNER') || (!hasAnyRole(roles, ['OWNER']) && selectedRoles.includes('ADMIN'))
  const canSaveRoles = Boolean(selectedUserId && selectedRoles.length > 0 && !invalidSelectedRoles)

  function resolveUserStatus(userId: string) {
    if (statusFilter === 'BANNED' || visibleBannedIds.has(userId)) {
      return 'BANNED'
    }

    return 'ACTIVE'
  }

  return (
    <div className="space-y-6">
      <PageHeader description={t('adminUsers.description')} title={t('navigation.admin.users')} />

      <div className="grid gap-4 md:grid-cols-2 xl:grid-cols-4">
        <MetricCard title={t('adminUsers.totalUsers')} value={statsQuery.data.totalUsers} />
        <MetricCard title={t('adminUsers.totalAdmins')} value={statsQuery.data.totalAdmins} />
        <MetricCard title={t('adminUsers.totalTeachers')} value={statsQuery.data.totalTeachers} />
        <MetricCard title={t('adminUsers.totalStudents')} value={statsQuery.data.totalStudents} />
      </div>

      <div className="grid gap-6 xl:grid-cols-[1.15fr_0.85fr]">
        <Card className="space-y-4">
          <div className="grid gap-4 xl:grid-cols-[minmax(0,1fr)_220px_220px]">
            <FormField label={t('common.actions.search')}>
              <Input
                value={search}
                onChange={(event) => {
                  setSearch(event.target.value)
                  setFeedbackMessage(null)
                  setErrorMessage(null)
                }}
              />
            </FormField>
            <FormField label={t('adminUsers.roleFilter')}>
              <Select value={roleFilter} onChange={(event) => setRoleFilter(event.target.value)}>
                <option value="">{t('adminUsers.allRoles')}</option>
                {roleFilterOptions.map((role) => (
                  <option key={role} value={role}>{t(`common.roles.${role}`)}</option>
                ))}
              </Select>
            </FormField>
            <FormField label={t('adminUsers.statusFilter')}>
              <Select value={statusFilter} onChange={(event) => setStatusFilter(event.target.value as 'ALL' | 'ACTIVE' | 'BANNED')}>
                <option value="ALL">{t('adminUsers.allStatuses')}</option>
                <option value="ACTIVE">{t('common.status.ACTIVE')}</option>
                <option value="BANNED">{t('common.status.BANNED')}</option>
              </Select>
            </FormField>
          </div>
          {feedbackMessage ? (
            <Card className="border-success/30 bg-success/5 px-4 py-3">
              <p className="text-sm font-semibold text-text-primary">{feedbackMessage}</p>
            </Card>
          ) : null}
          {errorMessage ? (
            <Card className="border-danger/30 bg-danger/5 px-4 py-3">
              <p className="text-sm font-semibold text-danger">{errorMessage}</p>
            </Card>
          ) : null}
          <DataTable
            columns={[
              {
                key: 'user',
                header: t('adminUsers.user'),
                render: (item) => (
                  <div className="flex items-center gap-3">
                    <UserAvatar
                      alt={t('adminUsers.avatarFor', { name: item.displayName ?? item.username })}
                      displayName={item.displayName}
                      email={item.email}
                      size="sm"
                      username={item.username}
                    />
                    <div>
                      <p className="text-sm font-semibold text-text-primary">{item.displayName?.trim() || item.username}</p>
                      <p className="text-xs text-text-muted">{item.username}</p>
                      <p className="text-xs text-text-muted">{item.email}</p>
                    </div>
                  </div>
                ),
              },
              {
                key: 'createdAt',
                header: t('adminUsers.createdAt'),
                render: (item) => (
                  <div>
                    <p className="text-sm text-text-secondary">{formatDateTime(item.createdAt)}</p>
                    <p className="text-xs text-text-muted">{item.email}</p>
                  </div>
                ),
              },
              {
                key: 'roles',
                header: t('adminUsers.role'),
                render: (item) => <div className="flex flex-wrap gap-2">{item.roles.map((role) => <RoleBadge key={role} role={role} />)}</div>,
              },
              {
                key: 'status',
                header: t('common.labels.status'),
                render: (item) => <StatusBadge value={resolveUserStatus(item.id)} />,
              },
              {
                key: 'actions',
                header: t('common.actions.open'),
                render: (item) => (
                  <Button
                    className="min-h-9"
                    variant={item.id === selectedUserId ? 'secondary' : 'ghost'}
                    onClick={(event) => {
                      event.stopPropagation()
                      setSelectedUserId(item.id)
                      setSelectedRoles(item.roles)
                    }}
                  >
                    {t('adminUsers.selectUser')}
                  </Button>
                ),
              },
            ]}
            getRowId={(row) => row.id}
            onRowClick={(row) => {
              setSelectedUserId(row.id)
              setSelectedRoles(row.roles)
            }}
            rowClassName={(row) => (row.id === selectedUserId ? 'bg-surface-muted' : undefined)}
            rows={usersQuery.data.content}
          />
        </Card>

        <Card className="space-y-4">
          <PageHeader description={t('adminUsers.selectionHint')} title={t('adminUsers.selectedUser')} />
          {selectedUser ? (
            <div className="space-y-4">
              <div className="flex items-center gap-3">
                <UserAvatar
                  alt={t('adminUsers.avatarFor', { name: selectedUser.displayName ?? selectedUser.username })}
                  displayName={selectedUser.displayName}
                  email={selectedUser.email}
                  size="md"
                  username={selectedUser.username}
                />
                <div>
                  <p className="text-lg font-semibold text-text-primary">{selectedUser.displayName?.trim() || selectedUser.username}</p>
                  <p className="text-sm text-text-secondary">{selectedUser.username}</p>
                  <p className="text-sm text-text-secondary">{selectedUser.email}</p>
                </div>
              </div>
              <div className="flex flex-wrap items-center gap-2">
                <StatusBadge value={statusLabel} />
                <div className="flex flex-wrap gap-2">{selectedUser.roles.map((role) => <RoleBadge key={role} role={role} />)}</div>
              </div>
              <div className="space-y-2 text-xs text-text-muted">
                <div className="flex items-center gap-2">
                  <span className="font-mono">{t('adminUsers.userId')}: {selectedUser.id}</span>
                  <Button
                    className="min-h-8 px-3 text-xs"
                    variant="ghost"
                    onClick={async () => {
                      await navigator.clipboard.writeText(selectedUser.id)
                      setFeedbackMessage(t('adminUsers.userIdCopied'))
                      setErrorMessage(null)
                    }}
                  >
                    {t('common.actions.copy')}
                  </Button>
                </div>
                <p>{t('adminUsers.createdAt')}: {formatDateTime(selectedUser.createdAt)}</p>
              </div>

              <div className="space-y-2">
                <p className="text-sm font-semibold text-text-secondary">{t('adminUsers.manageRoles')}</p>
                <div className="grid gap-2">
                  {assignableRoleOptions.map((role) => (
                    <label key={role} className="flex items-center gap-2 text-sm text-text-secondary">
                      <Input
                        checked={selectedRoles.includes(role)}
                        type="checkbox"
                        onChange={(event) => {
                          setSelectedRoles((current) => {
                            if (event.target.checked) {
                              return Array.from(new Set([...current, role]))
                            }
                            return current.filter((value) => value !== role)
                          })
                        }}
                      />
                      <span>{t(`common.roles.${role}`)}</span>
                    </label>
                  ))}
                </div>
                {invalidSelectedRoles ? (
                  <Card className="border-danger/30 bg-danger/5 px-4 py-3">
                    <p className="text-sm font-semibold text-danger">{t('adminUsers.roleAssignmentUnavailable')}</p>
                  </Card>
                ) : null}
                <Button
                  disabled={!canSaveRoles}
                  onClick={() => {
                    if (hasHighPrivilege) {
                      setShowRoleConfirm(true)
                      return
                    }
                    updateRoleMutation.mutate()
                  }}
                >
                  {t('adminUsers.updateRoles')}
                </Button>
              </div>

              {!selectedUserIsBanned ? (
                <FormField label={t('common.labels.reason')}>
                  <Input
                    value={banReason}
                    onChange={(event) => setBanReason(event.target.value)}
                  />
                </FormField>
              ) : null}

              <Button
                disabled={!selectedUserId || (!selectedUserIsBanned && !banReason.trim())}
                variant="secondary"
                onClick={() => (selectedUserIsBanned ? unbanMutation.mutate() : banMutation.mutate())}
              >
                {selectedUserIsBanned ? t('adminUsers.unban') : t('adminUsers.ban')}
              </Button>
            </div>
          ) : (
            <p className="text-sm text-text-secondary">{t('adminUsers.noSelection')}</p>
          )}
        </Card>
      </div>

      <ConfirmDialog
        description={t('adminUsers.confirmRoleChange')}
        open={showRoleConfirm}
        title={t('adminUsers.confirmRoleChangeTitle')}
        onCancel={() => setShowRoleConfirm(false)}
        onConfirm={() => {
          setShowRoleConfirm(false)
          updateRoleMutation.mutate()
        }}
      />
    </div>
  )
}
