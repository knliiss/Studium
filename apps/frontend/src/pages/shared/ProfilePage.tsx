import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { useMemo, useState } from 'react'
import { useTranslation } from 'react-i18next'

import { fileService, profileService } from '@/shared/api/services'
import {
  buildApiFieldErrors,
  getLocalizedApiErrorMessage,
  getLocalizedRequestErrorMessage,
  hasApiFieldErrors,
  normalizeApiError,
} from '@/shared/lib/api-errors'
import { Button } from '@/shared/ui/Button'
import { Card } from '@/shared/ui/Card'
import { FormField } from '@/shared/ui/FormField'
import { Input } from '@/shared/ui/Input'
import { PageHeader } from '@/shared/ui/PageHeader'
import { Select } from '@/shared/ui/Select'
import { ErrorState, LoadingState } from '@/shared/ui/StateViews'
import { UserAvatar } from '@/shared/ui/UserAvatar'

interface ProfileForm {
  displayName: string
  locale: string
  timezone: string
}

export function ProfilePage() {
  const { t } = useTranslation(['common', 'errors', 'validation'])
  const queryClient = useQueryClient()
  const profileQuery = useQuery({
    queryKey: ['profile', 'me'],
    queryFn: () => profileService.getMe(),
  })
  const [avatarFile, setAvatarFile] = useState<File | null>(null)
  const [formDraft, setFormDraft] = useState<Partial<ProfileForm>>({})
  const [fieldErrors, setFieldErrors] = useState<Record<string, string>>({})
  const [statusMessage, setStatusMessage] = useState<string | null>(null)
  const displayNameError = fieldErrors.displayName
  const timezoneError = fieldErrors.timezone
  const generalError = fieldErrors.form

  const form = useMemo<ProfileForm>(() => ({
    displayName: formDraft.displayName ?? profileQuery.data?.displayName ?? '',
    locale: formDraft.locale ?? profileQuery.data?.locale ?? 'en',
    timezone: formDraft.timezone ?? profileQuery.data?.timezone ?? 'Europe/Kiev',
  }), [formDraft, profileQuery.data])

  const trimmedDisplayName = useMemo(() => form.displayName.trim(), [form.displayName])
  const trimmedTimezone = useMemo(() => form.timezone.trim(), [form.timezone])

  const updateProfileMutation = useMutation({
    mutationFn: async () => {
      setFieldErrors({})
      setStatusMessage(null)

      if (trimmedDisplayName && trimmedDisplayName.length > 100) {
        setFieldErrors({ displayName: t('validation:displayNameLength') })
        return null
      }

      if (trimmedTimezone && !isValidTimeZone(trimmedTimezone)) {
        setFieldErrors({ timezone: t('validation:invalidTimezone') })
        return null
      }

      const payload: { displayName?: string; locale?: string; timezone?: string } = {}
      if (trimmedDisplayName) {
        payload.displayName = trimmedDisplayName
      }
      if (form.locale) {
        payload.locale = form.locale
      }
      if (trimmedTimezone) {
        payload.timezone = trimmedTimezone
      }

      return profileService.updateMe(payload)
    },
    onSuccess: async (data) => {
      if (!data) {
        return
      }
      await queryClient.invalidateQueries({ queryKey: ['profile', 'me'] })
      setFormDraft({})
      setStatusMessage(t('profile.updateSuccess'))
    },
    onError: (error) => {
      const normalizedError = normalizeApiError(error)
      if (hasApiFieldErrors(normalizedError)) {
        const fallback = getLocalizedApiErrorMessage(normalizedError, t)
        setFieldErrors(buildApiFieldErrors(normalizedError, fallback))
        return
      }

      setFieldErrors({ form: getLocalizedRequestErrorMessage(error, t) })
    },
  })

  const updateAvatarMutation = useMutation({
    mutationFn: async () => {
      if (!avatarFile) {
        return null
      }
      const uploaded = await fileService.uploadFile(avatarFile, 'AVATAR')
      return profileService.updateAvatar(uploaded.id)
    },
    onSuccess: () => {
      void queryClient.invalidateQueries({ queryKey: ['profile', 'me'] })
      setAvatarFile(null)
    },
  })

  const deleteAvatarMutation = useMutation({
    mutationFn: () => profileService.deleteAvatar(),
    onSuccess: () => {
      void queryClient.invalidateQueries({ queryKey: ['profile', 'me'] })
    },
  })

  if (profileQuery.isLoading) {
    return <LoadingState />
  }

  if (profileQuery.isError || !profileQuery.data) {
    return <ErrorState title={t('navigation.shared.profile')} description={t('common.states.error')} />
  }

  const profile = profileQuery.data

  return (
    <div className="space-y-6">
      <PageHeader
        description={profile.email}
        title={t('navigation.shared.profile')}
      />

      <div className="grid gap-6 xl:grid-cols-[0.9fr_1.1fr]">
        <Card className="space-y-4">
          <div className="space-y-1">
            <p className="text-xs font-semibold uppercase tracking-[0.18em] text-text-muted">
              {t('profile.account')}
            </p>
            <div className="flex items-center gap-3">
              <UserAvatar
                alt={t('profile.avatarFor', { name: profile.displayName ?? profile.username })}
                fileId={profile.avatarFileKey}
                displayName={profile.displayName}
                email={profile.email}
                size="lg"
                username={profile.username}
              />
              <div>
                <p className="text-lg font-semibold text-text-primary">{profile.username}</p>
                <p className="text-sm text-text-secondary">{profile.email}</p>
              </div>
            </div>
          </div>
          <div className="space-y-3 text-sm text-text-secondary">
            <p>{t('common.labels.timezone')}: {profile.timezone ?? '-'}</p>
            <p>{t('common.labels.language')}: {profile.locale ?? '-'}</p>
          </div>
        </Card>

        <Card className="space-y-4">
          <FormField error={displayNameError} label={t('profile.displayName')}>
            <Input
              value={form.displayName}
              onChange={(event) => {
                setFieldErrors((current) => ({ ...current, displayName: '', form: '' }))
                setFormDraft((current) => ({ ...current, displayName: event.target.value }))
              }}
            />
          </FormField>
          <div className="grid gap-4 md:grid-cols-2">
            <FormField label={t('common.labels.language')}>
              <Select
                value={form.locale}
                onChange={(event) => setFormDraft((current) => ({ ...current, locale: event.target.value }))}
              >
                <option value="en">{t('common.language.en')}</option>
                <option value="uk">{t('common.language.uk')}</option>
                <option value="pl">{t('common.language.pl')}</option>
              </Select>
            </FormField>
            <FormField error={timezoneError} label={t('common.labels.timezone')}>
              <Input
                value={form.timezone}
                onChange={(event) => {
                  setFieldErrors((current) => ({ ...current, timezone: '', form: '' }))
                  setFormDraft((current) => ({ ...current, timezone: event.target.value }))
                }}
              />
            </FormField>
          </div>
          <div className="flex flex-wrap gap-3">
            <Button onClick={() => updateProfileMutation.mutate()}>
              {t('common.actions.save')}
            </Button>
            {statusMessage ? <p className="text-sm text-success">{statusMessage}</p> : null}
            {generalError ? <p className="text-sm text-danger">{generalError}</p> : null}
          </div>
        </Card>
      </div>

      <Card className="space-y-4">
        <PageHeader title={t('profile.avatar')} />
        <FormField label={t('files.uploadAvatar')}>
          <Input
            accept="image/png,image/jpeg,image/webp"
            type="file"
            onChange={(event) => setAvatarFile(event.target.files?.[0] ?? null)}
          />
        </FormField>
        <div className="flex flex-wrap gap-3">
          <Button disabled={!avatarFile || updateAvatarMutation.isPending} onClick={() => updateAvatarMutation.mutate()}>
            {t('profile.updateAvatar')}
          </Button>
          <Button
            disabled={deleteAvatarMutation.isPending}
            variant="secondary"
            onClick={() => deleteAvatarMutation.mutate()}
          >
            {t('profile.deleteAvatar')}
          </Button>
        </div>
      </Card>
    </div>
  )
}

function isValidTimeZone(value: string) {
  try {
    Intl.DateTimeFormat(undefined, { timeZone: value }).format(new Date())
    return true
  } catch {
    return false
  }
}
