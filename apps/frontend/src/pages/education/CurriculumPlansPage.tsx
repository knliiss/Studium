import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { Plus, RotateCcw } from 'lucide-react'
import { useMemo, useState } from 'react'
import { useTranslation } from 'react-i18next'
import { Link, useParams, useSearchParams } from 'react-router-dom'

import { useAuth } from '@/features/auth/useAuth'
import { AccessDeniedPage } from '@/pages/shared/AccessDeniedPage'
import { curriculumPlanService, educationService, specialtyService } from '@/shared/api/services'
import { buildApiFieldErrors, getLocalizedApiErrorMessage, normalizeApiError } from '@/shared/lib/api-errors'
import { hasAnyRole } from '@/shared/lib/roles'
import type { CurriculumPlanResponse } from '@/shared/types/api'
import { Button } from '@/shared/ui/Button'
import { Card } from '@/shared/ui/Card'
import { FormField } from '@/shared/ui/FormField'
import { Input } from '@/shared/ui/Input'
import { PageHeader } from '@/shared/ui/PageHeader'
import { Select } from '@/shared/ui/Select'
import { EmptyState, ErrorState, LoadingState } from '@/shared/ui/StateViews'
import { Breadcrumbs } from '@/widgets/common/Breadcrumbs'
import { StatusBadge } from '@/widgets/common/StatusBadge'

type ActiveFilter = 'ALL' | 'ACTIVE' | 'ARCHIVED'

interface CurriculumFormState {
  specialtyId: string
  studyYear: string
  semesterNumber: string
  subjectId: string
  lectureCount: string
  practiceCount: string
  labCount: string
  supportsStreamLecture: boolean
  requiresSubgroupsForLabs: boolean
}

const initialFormState: CurriculumFormState = {
  specialtyId: '',
  studyYear: '',
  semesterNumber: '',
  subjectId: '',
  lectureCount: '0',
  practiceCount: '0',
  labCount: '0',
  supportsStreamLecture: false,
  requiresSubgroupsForLabs: false,
}

export function CurriculumPlansPage() {
  const { t } = useTranslation()
  const { specialtyId: routeSpecialtyId } = useParams()
  const [searchParams] = useSearchParams()
  const { roles } = useAuth()
  const queryClient = useQueryClient()
  const canRead = hasAnyRole(roles, ['OWNER', 'ADMIN', 'TEACHER'])
  const canManage = hasAnyRole(roles, ['OWNER', 'ADMIN'])
  const specialtyContextId = routeSpecialtyId ?? ''
  const contextStudyYear = searchParams.get('studyYear') ?? ''
  const hasSpecialtyContext = Boolean(specialtyContextId)
  const [search, setSearch] = useState('')
  const [activeFilter, setActiveFilter] = useState<ActiveFilter>('ACTIVE')
  const [specialtyFilter, setSpecialtyFilter] = useState('')
  const [studyYearFilter, setStudyYearFilter] = useState(contextStudyYear)
  const [semesterFilter, setSemesterFilter] = useState('')
  const [subjectFilter, setSubjectFilter] = useState('')
  const [editingItem, setEditingItem] = useState<CurriculumPlanResponse | null>(null)
  const [isEditorOpen, setIsEditorOpen] = useState(false)
  const [form, setForm] = useState<CurriculumFormState>(initialFormState)
  const [formError, setFormError] = useState<string | null>(null)
  const [formRequestId, setFormRequestId] = useState<string | null>(null)
  const [fieldErrors, setFieldErrors] = useState<Record<string, string>>({})

  const specialtiesQuery = useQuery({
    queryKey: ['education', 'specialties', 'active-for-curriculum'],
    queryFn: () => specialtyService.list({ active: true }),
    enabled: canRead,
    staleTime: 1000 * 60 * 5,
  })

  const subjectsQuery = useQuery({
    queryKey: ['education', 'subjects', 'for-curriculum'],
    queryFn: () => educationService.listSubjects({
      page: 0,
      size: 200,
      sortBy: 'name',
      direction: 'asc',
    }),
    enabled: canRead,
    staleTime: 1000 * 60 * 5,
  })

  const plansQuery = useQuery({
    queryKey: ['education', 'curriculum-plans', activeFilter, specialtyContextId, specialtyFilter, studyYearFilter, semesterFilter, subjectFilter],
    queryFn: () => curriculumPlanService.list({
      active: activeFilter === 'ALL' ? undefined : activeFilter === 'ACTIVE',
      specialtyId: specialtyContextId || specialtyFilter || undefined,
      studyYear: studyYearFilter ? Number(studyYearFilter) : undefined,
      semesterNumber: semesterFilter ? Number(semesterFilter) : undefined,
      subjectId: subjectFilter || undefined,
    }),
    enabled: canRead,
    staleTime: 1000 * 60 * 5,
  })

  const createMutation = useMutation({
    mutationFn: () => curriculumPlanService.create(toPayload(form, specialtyContextId)),
    onSuccess: async () => {
      await queryClient.invalidateQueries({ queryKey: ['education', 'curriculum-plans'] })
      closeEditor()
    },
    onError: (error) => {
      const normalized = normalizeApiError(error)
      const message = getLocalizedApiErrorMessage(normalized, t)
      setFormError(message)
      setFormRequestId(normalized?.requestId ?? null)
      setFieldErrors(buildApiFieldErrors(normalized, message))
    },
  })

  const updateMutation = useMutation({
    mutationFn: () => curriculumPlanService.update(editingItem!.id, toPayload(form, specialtyContextId)),
    onSuccess: async () => {
      await queryClient.invalidateQueries({ queryKey: ['education', 'curriculum-plans'] })
      closeEditor()
    },
    onError: (error) => {
      const normalized = normalizeApiError(error)
      const message = getLocalizedApiErrorMessage(normalized, t)
      setFormError(message)
      setFormRequestId(normalized?.requestId ?? null)
      setFieldErrors(buildApiFieldErrors(normalized, message))
    },
  })

  const archiveMutation = useMutation({
    mutationFn: (id: string) => curriculumPlanService.archive(id),
    onSuccess: async () => {
      await queryClient.invalidateQueries({ queryKey: ['education', 'curriculum-plans'] })
    },
  })

  const restoreMutation = useMutation({
    mutationFn: (id: string) => curriculumPlanService.restore(id),
    onSuccess: async () => {
      await queryClient.invalidateQueries({ queryKey: ['education', 'curriculum-plans'] })
    },
  })

  const specialtiesById = useMemo(
    () => new Map((specialtiesQuery.data ?? []).map((item) => [item.id, item])),
    [specialtiesQuery.data],
  )
  const subjects = useMemo(
    () => subjectsQuery.data?.items ?? [],
    [subjectsQuery.data?.items],
  )
  const subjectsById = useMemo(
    () => new Map(subjects.map((item) => [item.id, item])),
    [subjects],
  )

  const visibleItems = useMemo(() => {
    const query = search.trim().toLowerCase()
    const items = plansQuery.data ?? []
    if (!query) {
      return items
    }

    return items.filter((item) => {
      const specialtyName = specialtiesById.get(item.specialtyId)?.name ?? ''
      const subjectName = subjectsById.get(item.subjectId)?.name ?? ''
      return `${specialtyName} ${subjectName} ${item.studyYear} ${item.semesterNumber}`.toLowerCase().includes(query)
    })
  }, [plansQuery.data, search, specialtiesById, subjectsById])

  const disabledReason = resolveFormDisabledReason(form, t)

  if (!canRead) {
    return <AccessDeniedPage />
  }

  return (
    <div className="space-y-6">
      <Breadcrumbs
        items={[
          { label: t('navigation.groups.academicManagement'), to: '/academic' },
          { label: t('navigation.shared.specialties'), to: '/academic/specialties' },
          ...(hasSpecialtyContext
            ? [{ label: t('academic.actions.viewSpecialty'), to: `/academic/specialties/${specialtyContextId}` }]
            : []),
          { label: t('navigation.shared.curriculumPlans') },
        ]}
      />

      <PageHeader
        description={t('academic.curriculum.description')}
        title={hasSpecialtyContext ? t('academic.curriculum.forSpecialtyTitle') : t('navigation.shared.curriculumPlans')}
      />

      {!hasSpecialtyContext ? (
        <Card className="space-y-3">
          <p className="text-sm text-text-secondary">{t('academic.selectSpecialtyToContinueCurriculum')}</p>
          <Link to="/academic/specialties">
            <Button variant="secondary">{t('academic.actions.viewSpecialties')}</Button>
          </Link>
        </Card>
      ) : null}

      <Card className="space-y-4">
        <div className="grid gap-3 xl:grid-cols-[1.1fr_0.8fr_0.45fr_0.45fr_0.9fr_0.5fr_auto]">
          <FormField label={t('common.actions.search')}>
            <Input
              placeholder={t('academic.curriculum.searchPlaceholder')}
              value={search}
              onChange={(event) => setSearch(event.target.value)}
            />
          </FormField>
          <FormField label={t('academic.specialties.specialty')}>
            <Select
              disabled={hasSpecialtyContext}
              value={hasSpecialtyContext ? specialtyContextId : specialtyFilter}
              onChange={(event) => setSpecialtyFilter(event.target.value)}
            >
              <option value="">{t('academic.filters.allSpecialties')}</option>
              {(specialtiesQuery.data ?? []).map((specialty) => (
                <option key={specialty.id} value={specialty.id}>
                  {specialty.code} · {specialty.name}
                </option>
              ))}
            </Select>
          </FormField>
          <FormField label={t('academic.studyYear')}>
            <Select value={studyYearFilter} onChange={(event) => setStudyYearFilter(event.target.value)}>
              <option value="">{t('academic.filters.allStudyYears')}</option>
              {Array.from({ length: 8 }, (_, index) => index + 1).map((year) => (
                <option key={year} value={year}>
                  {t('academic.studyYearValue', { year })}
                </option>
              ))}
            </Select>
          </FormField>
          <FormField label={t('academic.curriculum.semester')}>
            <Select value={semesterFilter} onChange={(event) => setSemesterFilter(event.target.value)}>
              <option value="">{t('academic.filters.allSemesters')}</option>
              <option value="1">{t('academic.curriculum.semesterValue', { semester: 1 })}</option>
              <option value="2">{t('academic.curriculum.semesterValue', { semester: 2 })}</option>
            </Select>
          </FormField>
          <FormField label={t('academic.subject')}>
            <Select value={subjectFilter} onChange={(event) => setSubjectFilter(event.target.value)}>
              <option value="">{t('academic.filters.allSubjects')}</option>
              {subjects.map((subject) => (
                <option key={subject.id} value={subject.id}>{subject.name}</option>
              ))}
            </Select>
          </FormField>
          <FormField label={t('common.actions.filter')}>
            <Select value={activeFilter} onChange={(event) => setActiveFilter(event.target.value as ActiveFilter)}>
              <option value="ACTIVE">{t('academic.filters.activeOnly')}</option>
              <option value="ARCHIVED">{t('academic.filters.archivedOnly')}</option>
              <option value="ALL">{t('academic.filters.all')}</option>
            </Select>
          </FormField>
          {canManage ? (
            <div className="flex items-end">
              <Button
                className="w-full xl:w-auto"
                onClick={() => {
                  setEditingItem(null)
                  setForm({
                    ...initialFormState,
                    specialtyId: specialtyContextId,
                    studyYear: contextStudyYear,
                  })
                  setIsEditorOpen(true)
                  setFormError(null)
                  setFieldErrors({})
                }}
              >
                <Plus className="mr-2 h-4 w-4" />
                {t('academic.curriculum.add')}
              </Button>
            </div>
          ) : null}
        </div>
      </Card>

      {canManage && isEditorOpen ? (
        <Card className="space-y-4 border-accent/30">
          <PageHeader
            className="mb-0"
            description={editingItem ? t('academic.curriculum.editDescription') : t('academic.curriculum.createDescription')}
            title={editingItem ? t('academic.curriculum.edit') : t('academic.curriculum.add')}
          />
          <div className="grid gap-4 md:grid-cols-2 xl:grid-cols-4">
            <FormField error={fieldErrors.specialtyId} label={t('academic.specialties.specialty')}>
              <Select
                disabled={hasSpecialtyContext}
                value={hasSpecialtyContext ? specialtyContextId : form.specialtyId}
                onChange={(event) => setForm((current) => ({ ...current, specialtyId: event.target.value }))}
              >
                <option value="">{t('academic.validation.selectSpecialty')}</option>
                {(specialtiesQuery.data ?? []).map((specialty) => (
                  <option key={specialty.id} value={specialty.id}>
                    {specialty.code} · {specialty.name}
                  </option>
                ))}
              </Select>
            </FormField>
            <FormField error={fieldErrors.studyYear} label={t('academic.studyYear')}>
              <Input
                max={8}
                min={1}
                type="number"
                value={form.studyYear}
                onChange={(event) => setForm((current) => ({ ...current, studyYear: event.target.value }))}
              />
            </FormField>
            <FormField error={fieldErrors.semesterNumber} label={t('academic.curriculum.semester')}>
              <Select
                value={form.semesterNumber}
                onChange={(event) => setForm((current) => ({ ...current, semesterNumber: event.target.value }))}
              >
                <option value="">{t('academic.validation.selectSemester')}</option>
                <option value="1">{t('academic.curriculum.semesterValue', { semester: 1 })}</option>
                <option value="2">{t('academic.curriculum.semesterValue', { semester: 2 })}</option>
              </Select>
            </FormField>
            <FormField error={fieldErrors.subjectId} label={t('academic.subject')}>
              <Select
                value={form.subjectId}
                onChange={(event) => setForm((current) => ({ ...current, subjectId: event.target.value }))}
              >
                <option value="">{t('academic.validation.selectSubject')}</option>
                {subjects.map((subject) => (
                  <option key={subject.id} value={subject.id}>{subject.name}</option>
                ))}
              </Select>
            </FormField>
          </div>
          <div className="grid gap-4 md:grid-cols-3">
            <FormField error={fieldErrors.lectureCount} label={t('academic.curriculum.lectureCount')}>
              <Input
                min={0}
                type="number"
                value={form.lectureCount}
                onChange={(event) => setForm((current) => ({ ...current, lectureCount: event.target.value }))}
              />
            </FormField>
            <FormField error={fieldErrors.practiceCount} label={t('academic.curriculum.practiceCount')}>
              <Input
                min={0}
                type="number"
                value={form.practiceCount}
                onChange={(event) => setForm((current) => ({ ...current, practiceCount: event.target.value }))}
              />
            </FormField>
            <FormField error={fieldErrors.labCount} label={t('academic.curriculum.labCount')}>
              <Input
                min={0}
                type="number"
                value={form.labCount}
                onChange={(event) => setForm((current) => ({ ...current, labCount: event.target.value }))}
              />
            </FormField>
          </div>
          <div className="grid gap-3 md:grid-cols-2">
            <label className="flex min-h-11 items-center gap-3 rounded-[14px] border border-border bg-surface-muted px-3 text-sm text-text-primary">
              <input
                checked={form.supportsStreamLecture}
                type="checkbox"
                onChange={(event) => setForm((current) => ({ ...current, supportsStreamLecture: event.target.checked }))}
              />
              {t('academic.curriculum.supportsStreamLecture')}
            </label>
            <label className="flex min-h-11 items-center gap-3 rounded-[14px] border border-border bg-surface-muted px-3 text-sm text-text-primary">
              <input
                checked={form.requiresSubgroupsForLabs}
                type="checkbox"
                onChange={(event) => setForm((current) => ({ ...current, requiresSubgroupsForLabs: event.target.checked }))}
              />
              {t('academic.curriculum.requiresSubgroupsForLabs')}
            </label>
          </div>
          {formError ? (
            <div className="rounded-[14px] border border-danger/20 bg-danger/5 px-3 py-2 text-sm text-text-primary">
              <p>{formError}</p>
              {formRequestId ? (
                <p className="mt-1 text-xs text-text-muted">{t('common.labels.requestId')}: {formRequestId}</p>
              ) : null}
            </div>
          ) : null}
          {disabledReason ? (
            <p className="text-sm text-text-secondary">{disabledReason}</p>
          ) : null}
          <div className="flex flex-wrap gap-3">
            <Button
              disabled={Boolean(disabledReason) || createMutation.isPending || updateMutation.isPending}
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

      {plansQuery.isLoading || specialtiesQuery.isLoading || subjectsQuery.isLoading ? <LoadingState /> : null}
      {plansQuery.isError || specialtiesQuery.isError || subjectsQuery.isError ? (
        <ErrorState
          description={t('education.academicStructureLoadFailed')}
          title={t('navigation.shared.curriculumPlans')}
          onRetry={() => {
            void specialtiesQuery.refetch()
            void subjectsQuery.refetch()
            void plansQuery.refetch()
          }}
        />
      ) : null}
      {!plansQuery.isLoading && !plansQuery.isError && visibleItems.length === 0 ? (
        <EmptyState
          description={t('academic.curriculum.empty')}
          title={t('navigation.shared.curriculumPlans')}
          action={canManage ? (
            <Button
              onClick={() => {
                setEditingItem(null)
                setForm({
                  ...initialFormState,
                  specialtyId: specialtyContextId,
                  studyYear: contextStudyYear,
                })
                setIsEditorOpen(true)
                setFormError(null)
                setFormRequestId(null)
                setFieldErrors({})
              }}
            >
              <Plus className="mr-2 h-4 w-4" />
              {t('academic.curriculum.add')}
            </Button>
          ) : null}
        />
      ) : null}

      {visibleItems.length > 0 ? (
        <div className="grid gap-3">
          {visibleItems.map((item) => (
            <Card key={item.id} className="space-y-3">
              <div className="flex flex-wrap items-start justify-between gap-3">
                <div className="space-y-1">
                  <h2 className="text-lg font-semibold text-text-primary">
                    {subjectsById.get(item.subjectId)?.name ?? t('academic.curriculum.subjectUnknown')}
                  </h2>
                  <p className="text-sm text-text-secondary">
                    {specialtiesById.get(item.specialtyId)?.name ?? t('academic.specialties.specialtyUnknown')}
                    {' · '}
                    {t('academic.studyYearValue', { year: item.studyYear })}
                    {' · '}
                    {t('academic.curriculum.semesterValue', { semester: item.semesterNumber })}
                  </p>
                </div>
                <StatusBadge value={item.active ? 'ACTIVE' : 'ARCHIVED'} />
              </div>
              <div className="grid gap-3 text-sm text-text-secondary md:grid-cols-3">
                <div className="rounded-[14px] border border-border bg-surface-muted px-3 py-2">
                  {t('academic.curriculum.lectureCount')}: <span className="font-semibold text-text-primary">{item.lectureCount}</span>
                </div>
                <div className="rounded-[14px] border border-border bg-surface-muted px-3 py-2">
                  {t('academic.curriculum.practiceCount')}: <span className="font-semibold text-text-primary">{item.practiceCount}</span>
                </div>
                <div className="rounded-[14px] border border-border bg-surface-muted px-3 py-2">
                  {t('academic.curriculum.labCount')}: <span className="font-semibold text-text-primary">{item.labCount}</span>
                </div>
              </div>
              <div className="flex flex-wrap gap-2 text-xs font-semibold uppercase tracking-[0.12em] text-text-muted">
                {item.supportsStreamLecture ? (
                  <span className="rounded-full bg-accent-muted px-2.5 py-1 text-accent">
                    {t('academic.curriculum.supportsStreamLecture')}
                  </span>
                ) : null}
                {item.requiresSubgroupsForLabs ? (
                  <span className="rounded-full bg-warning/10 px-2.5 py-1 text-warning">
                    {t('academic.curriculum.requiresSubgroupsForLabs')}
                  </span>
                ) : null}
              </div>
              {canManage ? (
                <div className="flex flex-wrap gap-2">
                  <Button
                    variant="secondary"
                    onClick={() => {
                      setEditingItem(item)
                      setIsEditorOpen(true)
                      setForm({
                        specialtyId: item.specialtyId,
                        studyYear: String(item.studyYear),
                        semesterNumber: String(item.semesterNumber),
                        subjectId: item.subjectId,
                        lectureCount: String(item.lectureCount),
                        practiceCount: String(item.practiceCount),
                        labCount: String(item.labCount),
                        supportsStreamLecture: item.supportsStreamLecture,
                        requiresSubgroupsForLabs: item.requiresSubgroupsForLabs,
                      })
                      setFormError(null)
                      setFormRequestId(null)
                      setFieldErrors({})
                    }}
                  >
                    {t('common.actions.edit')}
                  </Button>
                  {item.active ? (
                    <Button
                      disabled={archiveMutation.isPending}
                      variant="ghost"
                      onClick={() => archiveMutation.mutate(item.id)}
                    >
                      {t('academic.common.archive')}
                    </Button>
                  ) : (
                    <Button
                      disabled={restoreMutation.isPending}
                      variant="ghost"
                      onClick={() => restoreMutation.mutate(item.id)}
                    >
                      <RotateCcw className="mr-2 h-4 w-4" />
                      {t('academic.common.restore')}
                    </Button>
                  )}
                </div>
              ) : null}
            </Card>
          ))}
        </div>
      ) : null}
    </div>
  )

  function closeEditor() {
    setEditingItem(null)
    setIsEditorOpen(false)
    setForm(initialFormState)
    setFormError(null)
    setFormRequestId(null)
    setFieldErrors({})
  }
}

function resolveFormDisabledReason(
  form: CurriculumFormState,
  t: (key: string, options?: Record<string, unknown>) => string,
) {
  if (!form.specialtyId) {
    return t('academic.validation.selectSpecialty')
  }
  if (!form.subjectId) {
    return t('academic.validation.selectSubject')
  }
  if (!form.studyYear.trim()) {
    return t('academic.validation.enterStudyYear')
  }
  if (!form.semesterNumber.trim()) {
    return t('academic.validation.selectSemester')
  }

  const studyYear = Number(form.studyYear)
  if (!Number.isInteger(studyYear) || studyYear < 1 || studyYear > 8) {
    return t('academic.validation.enterStudyYear')
  }
  const semesterNumber = Number(form.semesterNumber)
  if (!Number.isInteger(semesterNumber) || semesterNumber < 1 || semesterNumber > 2) {
    return t('academic.validation.selectSemester')
  }

  const lectureCount = Number(form.lectureCount)
  const practiceCount = Number(form.practiceCount)
  const labCount = Number(form.labCount)
  if ([lectureCount, practiceCount, labCount].some((value) => !Number.isInteger(value) || value < 0)) {
    return t('academic.validation.countsNonNegative')
  }
  if (lectureCount + practiceCount + labCount <= 0) {
    return t('academic.validation.atLeastOneLessonCount')
  }

  return null
}

function toPayload(form: CurriculumFormState, specialtyContextId: string) {
  return {
    specialtyId: specialtyContextId || form.specialtyId,
    studyYear: Number(form.studyYear),
    semesterNumber: Number(form.semesterNumber),
    subjectId: form.subjectId,
    lectureCount: Number(form.lectureCount),
    practiceCount: Number(form.practiceCount),
    labCount: Number(form.labCount),
    supportsStreamLecture: form.supportsStreamLecture,
    requiresSubgroupsForLabs: form.requiresSubgroupsForLabs,
  }
}
