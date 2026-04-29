import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { useState } from 'react'
import { useTranslation } from 'react-i18next'
import { useLocation } from 'react-router-dom'

import { educationService } from '@/shared/api/services'
import { Button } from '@/shared/ui/Button'
import { Card } from '@/shared/ui/Card'
import { DataTable } from '@/shared/ui/DataTable'
import { FormField } from '@/shared/ui/FormField'
import { Input } from '@/shared/ui/Input'
import { PageHeader } from '@/shared/ui/PageHeader'
import { Textarea } from '@/shared/ui/Textarea'
import { EmptyState, ErrorState, LoadingState } from '@/shared/ui/StateViews'

export function AdminEducationPage() {
  const { pathname } = useLocation()
  const mode = pathname.endsWith('/groups') ? 'groups' : pathname.endsWith('/subjects') ? 'subjects' : 'topics'

  if (mode === 'groups') {
    return <GroupsWorkspace />
  }
  if (mode === 'subjects') {
    return <SubjectsWorkspace />
  }
  return <TopicsWorkspace />
}

function GroupsWorkspace() {
  const { t } = useTranslation()
  const queryClient = useQueryClient()
  const [name, setName] = useState('')
  const [userId, setUserId] = useState('')
  const [selectedGroupId, setSelectedGroupId] = useState('')

  const membershipsQuery = useQuery({
    queryKey: ['education', 'group-memberships', userId],
    queryFn: () => educationService.getGroupsByUser(userId),
    enabled: Boolean(userId),
  })
  const groupQuery = useQuery({
    queryKey: ['education', 'group', selectedGroupId],
    queryFn: () => educationService.getGroup(selectedGroupId),
    enabled: Boolean(selectedGroupId),
  })
  const createMutation = useMutation({
    mutationFn: () => educationService.createGroup({ name }),
    onSuccess: async () => {
      await queryClient.invalidateQueries({ queryKey: ['education'] })
      setName('')
    },
  })

  if (membershipsQuery.isLoading || groupQuery.isLoading) {
    return <LoadingState />
  }

  if (membershipsQuery.isError || groupQuery.isError) {
    return <ErrorState title={t('navigation.admin.groups')} description={t('common.states.error')} />
  }

  return (
    <div className="space-y-6">
      <PageHeader description={t('education.adminGroupsDescription')} title={t('navigation.admin.groups')} />

      <Card className="space-y-4">
        <PageHeader title={t('education.createGroup')} />
        <FormField label={t('common.labels.name')}>
          <Input value={name} onChange={(event) => setName(event.target.value)} />
        </FormField>
        <Button onClick={() => createMutation.mutate()}>{t('common.actions.create')}</Button>
      </Card>

      <Card className="grid gap-4 xl:grid-cols-2">
        <FormField label={t('adminUsers.userId')}>
          <Input value={userId} onChange={(event) => setUserId(event.target.value)} />
        </FormField>
        <FormField label={t('education.groupId')}>
          <Input value={selectedGroupId} onChange={(event) => setSelectedGroupId(event.target.value)} />
        </FormField>
      </Card>

      {groupQuery.data ? (
        <DataTable
          columns={[
            { key: 'id', header: t('education.groupId'), render: (item) => item.id },
            { key: 'name', header: t('common.labels.name'), render: (item) => item.name },
          ]}
          rows={[groupQuery.data]}
        />
      ) : membershipsQuery.data && membershipsQuery.data.length > 0 ? (
        <DataTable
          columns={[{ key: 'groupId', header: t('education.groupId'), render: (item) => item.groupId }]}
          rows={membershipsQuery.data}
        />
      ) : (
        <EmptyState description={t('education.groupLookupHint')} title={t('navigation.admin.groups')} />
      )}
    </div>
  )
}

function SubjectsWorkspace() {
  const { t } = useTranslation()
  const queryClient = useQueryClient()
  const [form, setForm] = useState({ name: '', groupId: '', description: '' })
  const [groupId, setGroupId] = useState('')
  const subjectsQuery = useQuery({
    queryKey: ['education', 'subjects-group', groupId],
    queryFn: () => educationService.getSubjectsByGroup(groupId, { page: 0, size: 100 }),
    enabled: Boolean(groupId),
  })
  const createMutation = useMutation({
    mutationFn: () => educationService.createSubject(form),
    onSuccess: async () => {
      await queryClient.invalidateQueries({ queryKey: ['education'] })
    },
  })

  if (subjectsQuery.isLoading) {
    return <LoadingState />
  }

  if (subjectsQuery.isError) {
    return <ErrorState title={t('navigation.admin.subjects')} description={t('common.states.error')} />
  }

  return (
    <div className="space-y-6">
      <PageHeader description={t('education.adminSubjectsDescription')} title={t('navigation.admin.subjects')} />

      <Card className="space-y-4">
        <PageHeader title={t('education.createSubject')} />
        <div className="grid gap-4 xl:grid-cols-2">
          <FormField label={t('common.labels.name')}>
            <Input value={form.name} onChange={(event) => setForm((current) => ({ ...current, name: event.target.value }))} />
          </FormField>
          <FormField label={t('education.groupId')}>
            <Input value={form.groupId} onChange={(event) => setForm((current) => ({ ...current, groupId: event.target.value }))} />
          </FormField>
        </div>
        <FormField label={t('common.labels.description')}>
          <Textarea value={form.description} onChange={(event) => setForm((current) => ({ ...current, description: event.target.value }))} />
        </FormField>
        <Button onClick={() => createMutation.mutate()}>{t('common.actions.create')}</Button>
      </Card>

      <Card className="space-y-4">
        <FormField label={t('education.groupId')}>
          <Input value={groupId} onChange={(event) => setGroupId(event.target.value)} />
        </FormField>
      </Card>

      {subjectsQuery.data?.items?.length ? (
        <DataTable
          columns={[
            { key: 'id', header: t('education.subjectId'), render: (item) => item.id },
            { key: 'name', header: t('common.labels.name'), render: (item) => item.name },
            { key: 'description', header: t('common.labels.description'), render: (item) => item.description ?? '-' },
          ]}
          rows={subjectsQuery.data.items}
        />
      ) : (
        <EmptyState description={t('education.subjectLookupHint')} title={t('navigation.admin.subjects')} />
      )}
    </div>
  )
}

function TopicsWorkspace() {
  const { t } = useTranslation()
  const queryClient = useQueryClient()
  const [form, setForm] = useState({ subjectId: '', title: '', orderIndex: 0 })
  const [subjectId, setSubjectId] = useState('')
  const topicsQuery = useQuery({
    queryKey: ['education', 'topics-subject', subjectId],
    queryFn: () => educationService.getTopicsBySubject(subjectId, { page: 0, size: 100 }),
    enabled: Boolean(subjectId),
  })
  const createMutation = useMutation({
    mutationFn: () => educationService.createTopic(form),
    onSuccess: async () => {
      await queryClient.invalidateQueries({ queryKey: ['education'] })
    },
  })

  if (topicsQuery.isLoading) {
    return <LoadingState />
  }

  if (topicsQuery.isError) {
    return <ErrorState title={t('navigation.admin.topics')} description={t('common.states.error')} />
  }

  return (
    <div className="space-y-6">
      <PageHeader description={t('education.adminTopicsDescription')} title={t('navigation.admin.topics')} />

      <Card className="space-y-4">
        <PageHeader title={t('education.createTopic')} />
        <div className="grid gap-4 xl:grid-cols-3">
          <FormField label={t('education.subjectId')}>
            <Input value={form.subjectId} onChange={(event) => setForm((current) => ({ ...current, subjectId: event.target.value }))} />
          </FormField>
          <FormField label={t('common.labels.title')}>
            <Input value={form.title} onChange={(event) => setForm((current) => ({ ...current, title: event.target.value }))} />
          </FormField>
          <FormField label={t('education.orderIndex')}>
            <Input type="number" value={form.orderIndex} onChange={(event) => setForm((current) => ({ ...current, orderIndex: Number(event.target.value) }))} />
          </FormField>
        </div>
        <Button onClick={() => createMutation.mutate()}>{t('common.actions.create')}</Button>
      </Card>

      <Card className="space-y-4">
        <FormField label={t('education.subjectId')}>
          <Input value={subjectId} onChange={(event) => setSubjectId(event.target.value)} />
        </FormField>
      </Card>

      {topicsQuery.data?.items?.length ? (
        <DataTable
          columns={[
            { key: 'id', header: t('education.topic'), render: (item) => item.id },
            { key: 'title', header: t('common.labels.title'), render: (item) => item.title },
            { key: 'orderIndex', header: t('education.orderIndex'), render: (item) => item.orderIndex },
          ]}
          rows={topicsQuery.data.items}
        />
      ) : (
        <EmptyState description={t('education.topicLookupHint')} title={t('navigation.admin.topics')} />
      )}
    </div>
  )
}
