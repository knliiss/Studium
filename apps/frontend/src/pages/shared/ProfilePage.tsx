import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import type { TFunction } from 'i18next'
import { useEffect, useMemo, useState } from 'react'
import { useTranslation } from 'react-i18next'

import { fileService, notificationService, profileService } from '@/shared/api/services'
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
import type { TelegramLinkStatusResponse } from '@/shared/types/api'

interface ProfileForm {
  displayName: string
  locale: string
  timezone: string
}

interface TelegramConnectDraft {
  token: string
  deepLink: string | null
  expiresAt: string
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
  const [telegramConnectDraft, setTelegramConnectDraft] = useState<TelegramConnectDraft | null>(null)
  const [telegramStatusMessage, setTelegramStatusMessage] = useState<string | null>(null)
  const [telegramErrorMessage, setTelegramErrorMessage] = useState<string | null>(null)
  const [telegramPollingStartedAt, setTelegramPollingStartedAt] = useState<number | null>(null)
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

  const telegramStatusQuery = useQuery({
    queryKey: ['notifications', 'telegram', 'status'],
    queryFn: () => notificationService.getTelegramStatus(),
    refetchInterval: (query) => {
      if (!telegramPollingStartedAt) {
        return false
      }
      const data = query.state.data as TelegramLinkStatusResponse | undefined
      if (data?.connected) {
        return false
      }
      if (Date.now() - telegramPollingStartedAt > 120_000) {
        return false
      }
      return 5_000
    },
    refetchIntervalInBackground: true,
  })
  const createTelegramTokenMutation = useMutation({
    mutationFn: () => notificationService.createTelegramConnectToken(),
    onSuccess: async (response) => {
      if (!response?.token) {
        setTelegramErrorMessage(t('profile.telegram.connectionFailed'))
        return
      }
      setTelegramErrorMessage(null)
      setTelegramStatusMessage(null)
      setTelegramConnectDraft({
        token: response.token,
        deepLink: response.deepLink,
        expiresAt: response.expiresAt,
      })
      setTelegramPollingStartedAt(Date.now())
      await queryClient.invalidateQueries({ queryKey: ['notifications', 'telegram', 'status'] })
    },
    onError: (error) => {
      setTelegramErrorMessage(getLocalizedRequestErrorMessage(error, t))
    },
  })
  const disconnectTelegramMutation = useMutation({
    mutationFn: () => notificationService.disconnectTelegram(),
    onSuccess: async () => {
      setTelegramConnectDraft(null)
      setTelegramPollingStartedAt(null)
      setTelegramStatusMessage(t('profile.telegram.disconnected'))
      setTelegramErrorMessage(null)
      await queryClient.invalidateQueries({ queryKey: ['notifications', 'telegram', 'status'] })
    },
    onError: (error) => {
      setTelegramErrorMessage(getLocalizedRequestErrorMessage(error, t))
    },
  })
  const sendTelegramTestMutation = useMutation({
    mutationFn: () => notificationService.sendTelegramTest(),
    onSuccess: () => {
      setTelegramStatusMessage(t('profile.telegram.testSuccess'))
      setTelegramErrorMessage(null)
    },
    onError: () => {
      setTelegramErrorMessage(t('profile.telegram.testFailed'))
    },
  })
  const updateTelegramPreferencesMutation = useMutation({
    mutationFn: (payload: {
      telegramEnabled?: boolean
      notifyAssignments?: boolean
      notifyTests?: boolean
      notifyGrades?: boolean
      notifySchedule?: boolean
      notifyMaterials?: boolean
      notifySystem?: boolean
    }) => notificationService.updateTelegramPreferences(payload),
    onSuccess: async () => {
      setTelegramStatusMessage(t('profile.telegram.preferencesUpdated'))
      setTelegramErrorMessage(null)
      await queryClient.invalidateQueries({ queryKey: ['notifications', 'telegram', 'status'] })
    },
    onError: (error) => {
      setTelegramErrorMessage(getLocalizedRequestErrorMessage(error, t))
    },
  })

  useEffect(() => {
    if (!telegramStatusQuery.data?.connected || !telegramConnectDraft) {
      return
    }
    const timeoutId = window.setTimeout(() => {
      setTelegramConnectDraft(null)
      setTelegramPollingStartedAt(null)
      setTelegramStatusMessage(t('profile.telegram.connectedNow'))
      setTelegramErrorMessage(null)
    }, 0)
    return () => window.clearTimeout(timeoutId)
  }, [telegramConnectDraft, telegramStatusQuery.data?.connected, t])

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

      <Card className="space-y-4">
        <PageHeader title={t('profile.telegram.title')} />
        {telegramStatusQuery.isLoading ? (
          <p className="text-sm text-text-secondary">{t('common.states.loading')}</p>
        ) : null}
        {!telegramStatusQuery.isLoading && telegramStatusQuery.data ? (
          <TelegramProfileSection
            draft={telegramConnectDraft}
            onCopy={async (value) => {
              try {
                await navigator.clipboard.writeText(value)
                setTelegramStatusMessage(t('profile.telegram.copied'))
                setTelegramErrorMessage(null)
              } catch {
                setTelegramErrorMessage(t('profile.telegram.copyFailed'))
              }
            }}
            onDisconnect={() => {
              if (window.confirm(t('profile.telegram.disconnectConfirm'))) {
                disconnectTelegramMutation.mutate()
              }
            }}
            onGenerate={() => createTelegramTokenMutation.mutate()}
            onOpenTelegram={() => {
              const url = telegramConnectDraft?.deepLink
              if (!url) {
                return
              }
              window.open(url, '_blank', 'noopener,noreferrer')
            }}
            onRefetch={async () => {
              await telegramStatusQuery.refetch()
            }}
            onSendTest={() => sendTelegramTestMutation.mutate()}
            pendingMutation={disconnectTelegramMutation.isPending || createTelegramTokenMutation.isPending}
            generatePending={createTelegramTokenMutation.isPending}
            preferencesPending={updateTelegramPreferencesMutation.isPending}
            sendTelegramTestPending={sendTelegramTestMutation.isPending}
            status={telegramStatusQuery.data}
            statusMessage={telegramStatusMessage}
            t={t}
            telegramErrorMessage={telegramErrorMessage}
            onUpdatePreference={(payload) => updateTelegramPreferencesMutation.mutate(payload)}
          />
        ) : null}
      </Card>
    </div>
  )
}

interface TelegramPreferencesPatch {
  telegramEnabled?: boolean
  notifyAssignments?: boolean
  notifyTests?: boolean
  notifyGrades?: boolean
  notifySchedule?: boolean
  notifyMaterials?: boolean
  notifySystem?: boolean
}

interface TelegramProfileSectionProps {
  status: TelegramLinkStatusResponse
  draft: TelegramConnectDraft | null
  pendingMutation: boolean
  generatePending: boolean
  sendTelegramTestPending: boolean
  preferencesPending: boolean
  statusMessage: string | null
  telegramErrorMessage: string | null
  t: TFunction
  onGenerate: () => void
  onOpenTelegram: () => void
  onCopy: (value: string) => Promise<void>
  onRefetch: () => Promise<void>
  onDisconnect: () => void
  onSendTest: () => void
  onUpdatePreference: (payload: TelegramPreferencesPatch) => void
}

function TelegramProfileSection({
  status,
  draft,
  pendingMutation,
  generatePending,
  sendTelegramTestPending,
  preferencesPending,
  statusMessage,
  telegramErrorMessage,
  t,
  onGenerate,
  onOpenTelegram,
  onCopy,
  onRefetch,
  onDisconnect,
  onSendTest,
  onUpdatePreference,
}: TelegramProfileSectionProps) {
  const isConnected = status.connected
  const hasWaitingDraft = !isConnected && draft !== null
  const pendingWithoutDraft = !isConnected && draft === null && status.pending
  const unavailable = !status.telegramEnabledByConfig || !status.telegramAvailable || !status.botUsername
  const tokenExpiresAt = draft?.expiresAt ?? status.tokenExpiresAt
  const deepLink = draft?.deepLink ?? null
  const hasDeepLink = deepLink !== null && deepLink.trim().length > 0
  const statusBadge = unavailable
    ? t('profile.telegram.unavailable')
    : isConnected
      ? t('profile.telegram.connected')
      : t('profile.telegram.notConnected')

  return (
    <div className="space-y-4">
      <div>
        <span className="inline-flex rounded-full border border-border bg-surface-muted px-3 py-1 text-xs font-semibold text-text-secondary">
          {statusBadge}
        </span>
      </div>
      {statusMessage ? (
        <p className="rounded-[12px] border border-success/20 bg-success/10 px-3 py-2 text-sm text-success">
          {statusMessage}
        </p>
      ) : null}
      {telegramErrorMessage ? (
        <p className="rounded-[12px] border border-danger/20 bg-danger/10 px-3 py-2 text-sm text-danger">
          {telegramErrorMessage}
        </p>
      ) : null}

      {unavailable ? (
        <div className="space-y-3">
          <p className="text-sm text-text-secondary">{t('profile.telegram.unavailable')}</p>
          <p className="text-xs text-text-muted">{t('profile.telegram.unavailableHint')}</p>
        </div>
      ) : null}

      {!unavailable && !isConnected && !hasWaitingDraft ? (
        <div className="space-y-3">
          <p className="text-sm text-text-secondary">{t('profile.telegram.connectExplanation')}</p>
          {pendingWithoutDraft ? (
            <p className="rounded-[12px] border border-border bg-surface-muted px-3 py-2 text-xs text-text-secondary">
              {t('profile.telegram.pendingUnknownToken')}
            </p>
          ) : null}
          <div className="flex flex-wrap gap-3">
            <Button disabled={pendingMutation} onClick={onGenerate}>
              {generatePending ? t('common.states.loading') : t('profile.telegram.generateLink')}
            </Button>
            <Button disabled={pendingMutation} variant="secondary" onClick={() => void onRefetch()}>
              {t('profile.telegram.checkStatus')}
            </Button>
          </div>
        </div>
      ) : null}

      {!unavailable && hasWaitingDraft ? (
        <div className="space-y-3">
          <p className="text-sm text-text-secondary">{t('profile.telegram.waiting')}</p>
          <ol className="space-y-1 text-sm text-text-secondary">
            <li>1. {t('profile.telegram.stepOpenTelegram')}</li>
            <li>2. {t('profile.telegram.stepPressStart')}</li>
            <li>3. {t('profile.telegram.stepReturnProfile')}</li>
          </ol>
          <div className="rounded-[12px] border border-border bg-surface-muted p-3">
            <p className="text-xs text-text-muted">{t('profile.telegram.tokenExpiresAt')}</p>
            <p className="text-xs text-text-secondary">
              {tokenExpiresAt ? new Date(tokenExpiresAt).toLocaleString() : '—'}
            </p>
            {hasDeepLink ? (
              <p className="mt-2 break-all text-xs text-text-secondary">{deepLink}</p>
            ) : (
              <p className="mt-2 text-xs text-text-muted">{t('profile.telegram.linkUnavailable')}</p>
            )}
          </div>
          <div className="flex flex-wrap gap-3">
            <Button disabled={!hasDeepLink} onClick={onOpenTelegram}>
              {t('profile.telegram.openTelegram')}
            </Button>
            <Button
              disabled={!hasDeepLink}
              variant="secondary"
              onClick={() => {
                if (deepLink) {
                  void onCopy(deepLink)
                }
              }}
            >
              {t('profile.telegram.copyLink')}
            </Button>
            <Button
              disabled={!draft?.token}
              variant="secondary"
              onClick={() => {
                if (draft?.token) {
                  void onCopy(draft.token)
                }
              }}
            >
              {t('profile.telegram.copyToken')}
            </Button>
          </div>
          <div className="flex flex-wrap gap-3">
            <Button disabled={pendingMutation} variant="secondary" onClick={onGenerate}>
              {generatePending ? t('common.states.loading') : t('profile.telegram.regenerateLink')}
            </Button>
            <Button disabled={pendingMutation} variant="secondary" onClick={() => void onRefetch()}>
              {t('profile.telegram.checkStatus')}
            </Button>
            <Button disabled={pendingMutation} variant="danger" onClick={onDisconnect}>
              {t('profile.telegram.cancelLink')}
            </Button>
          </div>
        </div>
      ) : null}

      {!unavailable && isConnected ? (
        <div className="space-y-4">
          <p className="text-sm text-text-secondary">
            {t('profile.telegram.connectedAs', { username: status.telegramUsername ?? '-' })}
          </p>
          <p className="text-xs text-text-muted">
            {t('profile.telegram.connectedAt')}: {status.connectedAt ? new Date(status.connectedAt).toLocaleString() : '—'}
          </p>
          <div className="grid gap-2 md:grid-cols-2">
            <label className="flex items-center justify-between gap-3 rounded-[12px] border border-border bg-surface-muted px-3 py-2 text-sm">
              <span>{t('profile.telegram.preferences.enabled')}</span>
              <Input
                checked={status.preferences.telegramEnabled}
                disabled={preferencesPending}
                type="checkbox"
                onChange={(event) => onUpdatePreference({ telegramEnabled: event.target.checked })}
              />
            </label>
            <label className="flex items-center justify-between gap-3 rounded-[12px] border border-border bg-surface-muted px-3 py-2 text-sm">
              <span>{t('profile.telegram.preferences.assignments')}</span>
              <Input
                checked={status.preferences.notifyAssignments}
                disabled={preferencesPending}
                type="checkbox"
                onChange={(event) => onUpdatePreference({ notifyAssignments: event.target.checked })}
              />
            </label>
            <label className="flex items-center justify-between gap-3 rounded-[12px] border border-border bg-surface-muted px-3 py-2 text-sm">
              <span>{t('profile.telegram.preferences.tests')}</span>
              <Input
                checked={status.preferences.notifyTests}
                disabled={preferencesPending}
                type="checkbox"
                onChange={(event) => onUpdatePreference({ notifyTests: event.target.checked })}
              />
            </label>
            <label className="flex items-center justify-between gap-3 rounded-[12px] border border-border bg-surface-muted px-3 py-2 text-sm">
              <span>{t('profile.telegram.preferences.grades')}</span>
              <Input
                checked={status.preferences.notifyGrades}
                disabled={preferencesPending}
                type="checkbox"
                onChange={(event) => onUpdatePreference({ notifyGrades: event.target.checked })}
              />
            </label>
            <label className="flex items-center justify-between gap-3 rounded-[12px] border border-border bg-surface-muted px-3 py-2 text-sm">
              <span>{t('profile.telegram.preferences.schedule')}</span>
              <Input
                checked={status.preferences.notifySchedule}
                disabled={preferencesPending}
                type="checkbox"
                onChange={(event) => onUpdatePreference({ notifySchedule: event.target.checked })}
              />
            </label>
            <label className="flex items-center justify-between gap-3 rounded-[12px] border border-border bg-surface-muted px-3 py-2 text-sm">
              <span>{t('profile.telegram.preferences.materials')}</span>
              <Input
                checked={status.preferences.notifyMaterials}
                disabled={preferencesPending}
                type="checkbox"
                onChange={(event) => onUpdatePreference({ notifyMaterials: event.target.checked })}
              />
            </label>
            <label className="flex items-center justify-between gap-3 rounded-[12px] border border-border bg-surface-muted px-3 py-2 text-sm">
              <span>{t('profile.telegram.preferences.system')}</span>
              <Input
                checked={status.preferences.notifySystem}
                disabled={preferencesPending}
                type="checkbox"
                onChange={(event) => onUpdatePreference({ notifySystem: event.target.checked })}
              />
            </label>
          </div>
          <div className="flex flex-wrap gap-3">
            <Button disabled={sendTelegramTestPending} onClick={onSendTest}>
              {t('profile.telegram.sendTest')}
            </Button>
            <Button disabled={pendingMutation} variant="secondary" onClick={() => void onRefetch()}>
              {t('profile.telegram.checkStatus')}
            </Button>
            <Button disabled={pendingMutation} variant="secondary" onClick={onDisconnect}>
              {t('profile.telegram.disconnect')}
            </Button>
          </div>
        </div>
      ) : null}
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
