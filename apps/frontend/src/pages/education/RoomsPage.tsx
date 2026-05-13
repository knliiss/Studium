import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { Building2, Plus, RotateCcw } from 'lucide-react'
import { useMemo, useState } from 'react'
import { useTranslation } from 'react-i18next'
import { Link } from 'react-router-dom'

import { useAuth } from '@/features/auth/useAuth'
import { AccessDeniedPage } from '@/pages/shared/AccessDeniedPage'
import { roomCapabilityService, scheduleService } from '@/shared/api/services'
import { buildApiFieldErrors, getLocalizedApiErrorMessage, normalizeApiError } from '@/shared/lib/api-errors'
import { cn } from '@/shared/lib/cn'
import { getLessonTypeLabel } from '@/shared/lib/enum-labels'
import { hasAnyRole } from '@/shared/lib/roles'
import type { RoomCapabilityResponse, RoomResponse } from '@/shared/types/api'
import { Button } from '@/shared/ui/Button'
import { Card } from '@/shared/ui/Card'
import { FormField } from '@/shared/ui/FormField'
import { Input } from '@/shared/ui/Input'
import { PageHeader } from '@/shared/ui/PageHeader'
import { Select } from '@/shared/ui/Select'
import { EmptyState, ErrorState, LoadingState } from '@/shared/ui/StateViews'
import { Breadcrumbs } from '@/widgets/common/Breadcrumbs'
import { MetricCard } from '@/widgets/common/MetricCard'
import { StatusBadge } from '@/widgets/common/StatusBadge'

type RoomStatusFilter = 'ALL' | 'ACTIVE' | 'ARCHIVED'
type RoomTypeFilter = 'ALL' | 'LECTURE' | 'LAB' | 'COMPUTER_LAB' | 'ONLINE' | 'OTHER'

interface RoomFormState {
  active: boolean
  building: string
  capacity: string
  code: string
  floor: string
}

const initialFormState: RoomFormState = {
  active: true,
  building: '',
  capacity: '',
  code: '',
  floor: '',
}

export function RoomsPage() {
  const { roles } = useAuth()
  const canManageRooms = hasAnyRole(roles, ['OWNER', 'ADMIN'])

  if (!canManageRooms) {
    return <AccessDeniedPage />
  }

  return <ManagedRoomsPage />
}

function ManagedRoomsPage() {
  const { t } = useTranslation()
  const queryClient = useQueryClient()
  const [search, setSearch] = useState('')
  const [statusFilter, setStatusFilter] = useState<RoomStatusFilter>('ACTIVE')
  const [buildingFilter, setBuildingFilter] = useState('ALL')
  const [typeFilter, setTypeFilter] = useState<RoomTypeFilter>('ALL')
  const [editingItem, setEditingItem] = useState<RoomResponse | null>(null)
  const [isEditorOpen, setIsEditorOpen] = useState(false)
  const [form, setForm] = useState<RoomFormState>(initialFormState)
  const [formError, setFormError] = useState<string | null>(null)
  const [formRequestId, setFormRequestId] = useState<string | null>(null)
  const [fieldErrors, setFieldErrors] = useState<Record<string, string>>({})

  const roomsQuery = useQuery({
    queryKey: ['schedule', 'rooms', 'management'],
    queryFn: () => scheduleService.listRooms(),
    staleTime: 1000 * 60 * 5,
  })

  const roomCapabilitiesQuery = useQuery({
    queryKey: ['schedule', 'rooms', 'management-capabilities'],
    queryFn: async () => {
      const rooms = await scheduleService.listRooms()
      const rows = await Promise.all(
        rooms.map(async (room) => {
          try {
            const capabilities = await roomCapabilityService.listByRoom(room.id, true)
            return [room.id, capabilities] as const
          } catch {
            return [room.id, [] as RoomCapabilityResponse[]] as const
          }
        }),
      )

      return new Map(rows)
    },
    staleTime: 1000 * 60 * 5,
  })

  const createMutation = useMutation({
    mutationFn: () => scheduleService.createRoom(toRoomPayload(form)),
    onSuccess: async () => {
      await queryClient.invalidateQueries({ queryKey: ['schedule', 'rooms'] })
      closeEditor()
    },
    onError: (error) => {
      applyFormError(error)
    },
  })

  const updateMutation = useMutation({
    mutationFn: () => scheduleService.updateRoom(editingItem!.id, toRoomPayload(form)),
    onSuccess: async () => {
      await queryClient.invalidateQueries({ queryKey: ['schedule', 'rooms'] })
      closeEditor()
    },
    onError: (error) => {
      applyFormError(error)
    },
  })

  const archiveMutation = useMutation({
    mutationFn: (room: RoomResponse) => scheduleService.updateRoom(room.id, {
      active: false,
      building: room.building,
      capacity: room.capacity,
      code: room.code,
      floor: room.floor,
    }),
    onSuccess: async () => {
      await queryClient.invalidateQueries({ queryKey: ['schedule', 'rooms'] })
    },
  })

  const restoreMutation = useMutation({
    mutationFn: (room: RoomResponse) => scheduleService.updateRoom(room.id, {
      active: true,
      building: room.building,
      capacity: room.capacity,
      code: room.code,
      floor: room.floor,
    }),
    onSuccess: async () => {
      await queryClient.invalidateQueries({ queryKey: ['schedule', 'rooms'] })
    },
  })

  const rooms = useMemo(
    () => roomsQuery.data ?? [],
    [roomsQuery.data],
  )
  const roomCapabilitiesByRoomId = useMemo(
    () => roomCapabilitiesQuery.data ?? new Map<string, RoomCapabilityResponse[]>(),
    [roomCapabilitiesQuery.data],
  )

  const buildingOptions = useMemo(
    () => ['ALL', ...Array.from(new Set(rooms.map((room) => room.building))).sort((left, right) => left.localeCompare(right))],
    [rooms],
  )

  const metrics = useMemo(() => {
    const totalRooms = rooms.length
    const activeRooms = rooms.filter((room) => room.active).length
    const archivedRooms = totalRooms - activeRooms
    const activeCapacity = rooms.filter((room) => room.active).reduce((acc, room) => acc + room.capacity, 0)

    return { activeCapacity, activeRooms, archivedRooms, totalRooms }
  }, [rooms])

  const visibleRooms = useMemo(() => {
    const query = search.trim().toLowerCase()

    return rooms.filter((room) => {
      if (statusFilter === 'ACTIVE' && !room.active) {
        return false
      }
      if (statusFilter === 'ARCHIVED' && room.active) {
        return false
      }
      if (buildingFilter !== 'ALL' && room.building !== buildingFilter) {
        return false
      }
      if (typeFilter !== 'ALL' && resolveRoomType(room, roomCapabilitiesByRoomId.get(room.id) ?? []) !== typeFilter) {
        return false
      }

      if (!query) {
        return true
      }

      return `${room.code} ${room.building} ${room.floor}`.toLowerCase().includes(query)
    })
  }, [buildingFilter, roomCapabilitiesByRoomId, rooms, search, statusFilter, typeFilter])

  const formDisabledReason = resolveFormDisabledReason(form, t)

  if (roomsQuery.isLoading || roomCapabilitiesQuery.isLoading) {
    return <LoadingState />
  }

  if (roomsQuery.isError || roomCapabilitiesQuery.isError) {
    return (
      <ErrorState
        description={t('common.states.error')}
        title={t('navigation.shared.rooms')}
        onRetry={() => {
          void roomsQuery.refetch()
          void roomCapabilitiesQuery.refetch()
        }}
      />
    )
  }

  return (
    <div className="space-y-6">
      <Breadcrumbs
        items={[
          { label: t('navigation.groups.academicManagement'), to: '/academic' },
          { label: t('navigation.shared.rooms') },
        ]}
      />

      <PageHeader
        description={t('education.rooms.description')}
        title={t('navigation.shared.rooms')}
      />

      <div className="grid gap-4 md:grid-cols-2 xl:grid-cols-4">
        <MetricCard title={t('education.rooms.metrics.total')} value={metrics.totalRooms} />
        <MetricCard title={t('education.rooms.metrics.active')} value={metrics.activeRooms} />
        <MetricCard title={t('education.rooms.metrics.archived')} value={metrics.archivedRooms} />
        <MetricCard title={t('education.rooms.metrics.capacity')} value={metrics.activeCapacity} />
      </div>

      <Card className="space-y-4">
        <div className="grid gap-3 xl:grid-cols-[1.5fr_0.6fr_0.6fr_0.6fr_auto]">
          <FormField label={t('common.actions.search')}>
            <Input
              placeholder={t('education.rooms.searchPlaceholder')}
              value={search}
              onChange={(event) => setSearch(event.target.value)}
            />
          </FormField>
          <FormField label={t('common.actions.filter')}>
            <Select value={buildingFilter} onChange={(event) => setBuildingFilter(event.target.value)}>
              {buildingOptions.map((building) => (
                <option key={building} value={building}>
                  {building === 'ALL' ? t('education.rooms.allBuildings') : building}
                </option>
              ))}
            </Select>
          </FormField>
          <FormField label={t('education.rooms.type')}>
            <Select value={typeFilter} onChange={(event) => setTypeFilter(event.target.value as RoomTypeFilter)}>
              <option value="ALL">{t('education.rooms.types.ALL')}</option>
              <option value="LECTURE">{t('education.rooms.types.LECTURE')}</option>
              <option value="LAB">{t('education.rooms.types.LAB')}</option>
              <option value="COMPUTER_LAB">{t('education.rooms.types.COMPUTER_LAB')}</option>
              <option value="ONLINE">{t('education.rooms.types.ONLINE')}</option>
              <option value="OTHER">{t('education.rooms.types.OTHER')}</option>
            </Select>
          </FormField>
          <FormField label={t('common.labels.status')}>
            <Select value={statusFilter} onChange={(event) => setStatusFilter(event.target.value as RoomStatusFilter)}>
              <option value="ACTIVE">{t('academic.filters.activeOnly')}</option>
              <option value="ARCHIVED">{t('academic.filters.archivedOnly')}</option>
              <option value="ALL">{t('academic.filters.all')}</option>
            </Select>
          </FormField>
          <div className="flex items-end">
            <Button
              className="w-full xl:w-auto"
              onClick={() => {
                setEditingItem(null)
                setForm(initialFormState)
                setIsEditorOpen(true)
                setFormError(null)
                setFormRequestId(null)
                setFieldErrors({})
              }}
            >
              <Plus className="mr-2 h-4 w-4" />
              {t('education.rooms.create')}
            </Button>
          </div>
        </div>
      </Card>

      {isEditorOpen ? (
        <Card className="space-y-4 border-accent/30">
          <PageHeader
            className="mb-0"
            description={editingItem ? t('education.rooms.editDescription') : t('education.rooms.createDescription')}
            title={editingItem ? t('education.rooms.edit') : t('education.rooms.create')}
          />
          <div className="grid gap-4 md:grid-cols-2">
            <FormField error={fieldErrors.code} label={t('education.rooms.code')}>
              <Input
                value={form.code}
                onChange={(event) => setForm((current) => ({ ...current, code: event.target.value }))}
              />
            </FormField>
            <FormField error={fieldErrors.building} label={t('education.rooms.building')}>
              <Input
                value={form.building}
                onChange={(event) => setForm((current) => ({ ...current, building: event.target.value }))}
              />
            </FormField>
            <FormField error={fieldErrors.floor} label={t('education.rooms.floor')}>
              <Input
                min={0}
                type="number"
                value={form.floor}
                onChange={(event) => setForm((current) => ({ ...current, floor: event.target.value }))}
              />
            </FormField>
            <FormField error={fieldErrors.capacity} label={t('education.rooms.capacity')}>
              <Input
                min={1}
                type="number"
                value={form.capacity}
                onChange={(event) => setForm((current) => ({ ...current, capacity: event.target.value }))}
              />
            </FormField>
          </div>

          <label className="flex min-h-11 items-center gap-3 rounded-[12px] border border-border bg-surface-muted px-3 text-sm text-text-secondary">
            <input
              checked={form.active}
              type="checkbox"
              onChange={(event) => {
                setForm((current) => ({ ...current, active: event.target.checked }))
              }}
            />
            <span>{form.active ? t('common.status.ACTIVE') : t('common.status.ARCHIVED')}</span>
          </label>

          {formError ? (
            <div className="rounded-[14px] border border-danger/20 bg-danger/5 px-3 py-2 text-sm text-text-primary">
              <p>{formError}</p>
              {formRequestId ? (
                <p className="mt-1 text-xs text-text-muted">{t('common.labels.requestId')}: {formRequestId}</p>
              ) : null}
            </div>
          ) : null}

          {formDisabledReason ? (
            <p className="text-sm text-text-secondary">{formDisabledReason}</p>
          ) : null}

          <div className="flex flex-wrap gap-3">
            <Button
              disabled={Boolean(formDisabledReason) || createMutation.isPending || updateMutation.isPending}
              onClick={() => {
                setFormError(null)
                setFormRequestId(null)
                setFieldErrors({})
                if (editingItem) {
                  updateMutation.mutate()
                  return
                }
                createMutation.mutate()
              }}
            >
              {editingItem ? t('common.actions.update') : t('common.actions.create')}
            </Button>
            <Button variant="secondary" onClick={closeEditor}>
              {t('common.actions.cancel')}
            </Button>
          </div>
        </Card>
      ) : null}

      {visibleRooms.length === 0 ? (
        <EmptyState
          description={t('education.rooms.empty')}
          title={t('navigation.shared.rooms')}
          action={(
            <Button
              onClick={() => {
                setEditingItem(null)
                setForm(initialFormState)
                setIsEditorOpen(true)
                setFormError(null)
                setFormRequestId(null)
                setFieldErrors({})
              }}
            >
              <Plus className="mr-2 h-4 w-4" />
              {t('education.rooms.create')}
            </Button>
          )}
        />
      ) : (
        <div className="grid gap-3">
          {visibleRooms.map((room) => {
            const roomType = resolveRoomType(room, roomCapabilitiesByRoomId.get(room.id) ?? [])
            const roomTypeLabel = t(`education.rooms.types.${roomType}`)
            const roomCapabilitySummary = summarizeRoomCapabilities(roomCapabilitiesByRoomId.get(room.id) ?? [], t)

            return (
              <Card key={room.id} className="space-y-4">
                <div className="flex flex-wrap items-start justify-between gap-3">
                  <div className="space-y-2">
                    <div className="flex flex-wrap items-center gap-2">
                      <h2 className="text-lg font-semibold text-text-primary">{room.code}</h2>
                      <StatusBadge value={room.active ? 'ACTIVE' : 'ARCHIVED'} />
                    </div>
                    <p className="text-sm text-text-secondary">{room.building} · {t('education.rooms.floorValue', { floor: room.floor })}</p>
                  </div>
                  <span className="inline-flex items-center gap-2 rounded-full border border-border bg-surface-muted px-3 py-1 text-xs font-semibold uppercase tracking-[0.14em] text-text-secondary">
                    <Building2 className="h-3.5 w-3.5" />
                    {roomTypeLabel}
                  </span>
                </div>

                <div className="grid gap-3 md:grid-cols-3">
                  <div className="rounded-[12px] border border-border bg-surface-muted px-3 py-2">
                    <p className="text-xs font-semibold uppercase tracking-[0.16em] text-text-muted">{t('education.rooms.capacity')}</p>
                    <p className="mt-1 text-sm font-semibold text-text-primary">{room.capacity}</p>
                  </div>
                  <div className="rounded-[12px] border border-border bg-surface-muted px-3 py-2 md:col-span-2">
                    <p className="text-xs font-semibold uppercase tracking-[0.16em] text-text-muted">{t('education.rooms.supportedLessonTypes')}</p>
                    <p className={cn(
                      'mt-1 text-sm font-semibold',
                      roomCapabilitySummary ? 'text-text-primary' : 'text-text-secondary',
                    )}>
                      {roomCapabilitySummary || t('education.rooms.noCapabilities')}
                    </p>
                  </div>
                </div>

                <div className="flex flex-wrap gap-2">
                  <Button
                    variant="secondary"
                    onClick={() => {
                      setEditingItem(room)
                      setIsEditorOpen(true)
                      setForm({
                        active: room.active,
                        building: room.building,
                        capacity: String(room.capacity),
                        code: room.code,
                        floor: String(room.floor),
                      })
                      setFormError(null)
                      setFormRequestId(null)
                      setFieldErrors({})
                    }}
                  >
                    {t('common.actions.edit')}
                  </Button>
                  {room.active ? (
                    <Button disabled={archiveMutation.isPending} variant="ghost" onClick={() => archiveMutation.mutate(room)}>
                      {t('academic.common.archive')}
                    </Button>
                  ) : (
                    <Button disabled={restoreMutation.isPending} variant="ghost" onClick={() => restoreMutation.mutate(room)}>
                      <RotateCcw className="mr-2 h-4 w-4" />
                      {t('academic.common.restore')}
                    </Button>
                  )}
                  <Link to={`/schedule/rooms/${room.id}`}>
                    <Button variant="ghost">{t('education.rooms.openSchedule')}</Button>
                  </Link>
                </div>
              </Card>
            )
          })}
        </div>
      )}
    </div>
  )

  function closeEditor() {
    setIsEditorOpen(false)
    setEditingItem(null)
    setForm(initialFormState)
    setFormError(null)
    setFormRequestId(null)
    setFieldErrors({})
  }

  function applyFormError(error: unknown) {
    const normalized = normalizeApiError(error)
    const message = getLocalizedApiErrorMessage(normalized, t)
    setFormError(message)
    setFormRequestId(normalized?.requestId ?? null)
    setFieldErrors(buildApiFieldErrors(normalized, message))
  }
}

function toRoomPayload(form: RoomFormState) {
  return {
    active: form.active,
    building: form.building.trim(),
    capacity: Number(form.capacity),
    code: form.code.trim(),
    floor: Number(form.floor),
  }
}

function resolveFormDisabledReason(
  form: RoomFormState,
  t: (key: string) => string,
) {
  if (!form.code.trim()) {
    return t('education.rooms.validation.codeRequired')
  }
  if (!form.building.trim()) {
    return t('education.rooms.validation.buildingRequired')
  }
  if (!Number.isInteger(Number(form.floor)) || Number(form.floor) < 0) {
    return t('education.rooms.validation.floorRequired')
  }
  if (!Number.isInteger(Number(form.capacity)) || Number(form.capacity) <= 0) {
    return t('education.rooms.validation.capacityPositive')
  }

  return ''
}

function resolveRoomType(room: RoomResponse, capabilities: RoomCapabilityResponse[]): RoomTypeFilter {
  const normalizedLabel = `${room.code} ${room.building}`.toLowerCase()
  if (normalizedLabel.includes('online') || normalizedLabel.includes('remote')) {
    return 'ONLINE'
  }

  const activeCapabilities = capabilities.filter((item) => item.active)
  if (normalizedLabel.includes('computer') || normalizedLabel.includes('pc')) {
    return 'COMPUTER_LAB'
  }

  if (activeCapabilities.some((item) => item.lessonType === 'LABORATORY')) {
    if (normalizedLabel.includes('lab')) {
      return 'COMPUTER_LAB'
    }
    return 'LAB'
  }

  if (activeCapabilities.some((item) => item.lessonType === 'LECTURE')) {
    return 'LECTURE'
  }

  return 'OTHER'
}

function summarizeRoomCapabilities(
  capabilities: RoomCapabilityResponse[],
  t: (key: string) => string,
) {
  const visible = capabilities
    .filter((capability) => capability.active)
    .sort((left, right) => right.priority - left.priority)

  if (visible.length === 0) {
    return ''
  }

  return visible.map((capability) => getLessonTypeLabel(capability.lessonType)).join(` ${t('education.rooms.capabilityDivider')} `)
}
