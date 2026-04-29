import { useState } from 'react'
import { useTranslation } from 'react-i18next'

import { authApi } from '@/shared/api/auth'
import {
  getLocalizedApiErrorMessage,
  normalizeApiError,
} from '@/shared/lib/api-errors'
import { Button } from '@/shared/ui/Button'
import { Card } from '@/shared/ui/Card'
import { FormField } from '@/shared/ui/FormField'
import { Input } from '@/shared/ui/Input'

export function PasswordResetPage() {
  const { t } = useTranslation(['common', 'validation', 'errors'])
  const [mode, setMode] = useState<'request' | 'confirm'>('request')
  const [message, setMessage] = useState<string | null>(null)
  const [error, setError] = useState<string | null>(null)
  const [requestEmail, setRequestEmail] = useState('')
  const [resetToken, setResetToken] = useState('')
  const [newPassword, setNewPassword] = useState('')
  const [submitting, setSubmitting] = useState(false)

  async function handleRequest(event: React.FormEvent<HTMLFormElement>) {
    event.preventDefault()
    setError(null)
    setMessage(null)
    setSubmitting(true)
    try {
      await authApi.requestPasswordReset({ email: requestEmail.trim() })
      setMessage(t('auth.passwordReset.requestSuccess'))
    } catch (submissionError: unknown) {
      setError(getLocalizedApiErrorMessage(normalizeApiError(submissionError), t))
    } finally {
      setSubmitting(false)
    }
  }

  async function handleConfirm(event: React.FormEvent<HTMLFormElement>) {
    event.preventDefault()
    setError(null)
    setMessage(null)
    setSubmitting(true)
    try {
      await authApi.confirmPasswordReset({ resetToken: resetToken.trim(), newPassword })
      setMessage(t('auth.passwordReset.success'))
    } catch (submissionError: unknown) {
      setError(getLocalizedApiErrorMessage(normalizeApiError(submissionError), t))
    } finally {
      setSubmitting(false)
    }
  }

  return (
    <Card className="w-full max-w-xl space-y-6 rounded-[24px] p-8">
      <div className="space-y-2">
        <h2 className="text-3xl font-bold tracking-[-0.04em] text-text-primary">
          {t('auth.passwordReset.title')}
        </h2>
        <p className="text-sm leading-6 text-text-secondary">{t('auth.passwordReset.description')}</p>
      </div>
      <div className="flex gap-2 rounded-[16px] bg-surface-muted p-1">
        <Button
          className="flex-1"
          variant={mode === 'request' ? 'primary' : 'ghost'}
          onClick={() => setMode('request')}
        >
          {t('auth.passwordReset.requestAction')}
        </Button>
        <Button
          className="flex-1"
          variant={mode === 'confirm' ? 'primary' : 'ghost'}
          onClick={() => setMode('confirm')}
        >
          {t('auth.passwordReset.confirmAction')}
        </Button>
      </div>
      {mode === 'request' ? (
        <form className="space-y-4" onSubmit={handleRequest}>
          <FormField label={t('common.labels.email')}>
            <Input
              autoComplete="email"
              type="email"
              value={requestEmail}
              onChange={(event) => setRequestEmail(event.target.value)}
            />
          </FormField>
          {error ? <p className="text-sm text-danger">{error}</p> : null}
          {message ? <p className="text-sm text-success">{message}</p> : null}
          <Button disabled={submitting} fullWidth type="submit">
            {submitting ? t('common.states.loading') : t('auth.passwordReset.requestAction')}
          </Button>
        </form>
      ) : (
        <form className="space-y-4" onSubmit={handleConfirm}>
          <FormField label={t('auth.passwordReset.resetToken')}>
            <Input value={resetToken} onChange={(event) => setResetToken(event.target.value)} />
          </FormField>
          <FormField label={t('auth.passwordReset.newPassword')}>
            <Input
              autoComplete="new-password"
              type="password"
              value={newPassword}
              onChange={(event) => setNewPassword(event.target.value)}
            />
          </FormField>
          {error ? <p className="text-sm text-danger">{error}</p> : null}
          {message ? <p className="text-sm text-success">{message}</p> : null}
          <Button disabled={submitting} fullWidth type="submit">
            {submitting ? t('common.states.loading') : t('auth.passwordReset.confirmAction')}
          </Button>
        </form>
      )}
    </Card>
  )
}
