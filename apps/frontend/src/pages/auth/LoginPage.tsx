import { useState } from 'react'
import { useTranslation } from 'react-i18next'
import { Link, useNavigate, useLocation } from 'react-router-dom'

import { useAuth } from '@/features/auth/useAuth'
import { AuthPasswordField } from '@/pages/auth/AuthPasswordField'
import {
  getLocalizedApiErrorMessage,
  normalizeApiError,
} from '@/shared/lib/api-errors'
import { getDashboardPath } from '@/shared/lib/roles'
import { Button } from '@/shared/ui/Button'
import { Card } from '@/shared/ui/Card'
import { FormField } from '@/shared/ui/FormField'
import { Input } from '@/shared/ui/Input'

export function LoginPage() {
  const { login } = useAuth()
  const navigate = useNavigate()
  const location = useLocation()
  const { t } = useTranslation(['common', 'errors', 'validation'])
  const [username, setUsername] = useState('')
  const [password, setPassword] = useState('')
  const [error, setError] = useState<string | null>(() => {
    const reason = (location.state as { reason?: string } | null)?.reason
    return reason === 'session-expired' ? t('errors:SESSION_EXPIRED') : null
  })
  const [submitting, setSubmitting] = useState(false)

  async function handleSubmit(event: React.FormEvent<HTMLFormElement>) {
    event.preventDefault()
    setError(null)

    if (!username.trim() || !password.trim()) {
      setError(t('validation:required'))
      return
    }

    setSubmitting(true)
    try {
      const response = await login({ username: username.trim(), password })
      if (response.status === 'AUTHENTICATED' && response.user) {
        navigate(getDashboardPath(response.user.roles), { replace: true })
      } else {
        navigate('/mfa')
      }
    } catch (submissionError: unknown) {
      const normalizedError = normalizeApiError(submissionError)

      if (!normalizedError) {
        setError(t('errors:fallback'))
      } else if (
        normalizedError.code === 'INVALID_CREDENTIALS' ||
        normalizedError.code === 'BAD_CREDENTIALS' ||
        normalizedError.code === 'USER_NOT_FOUND'
      ) {
        setError(t('errors:INVALID_CREDENTIALS'))
      } else {
        setError(getLocalizedApiErrorMessage(normalizedError, t))
      }
    } finally {
      setSubmitting(false)
    }
  }

  return (
    <Card className="w-full space-y-5 rounded-[18px] border border-border bg-surface p-5 sm:p-6">
      <div className="space-y-2">
        <h2 className="text-2xl font-bold tracking-[-0.03em] text-text-primary sm:text-[1.75rem]">
          {t('auth.login.title')}
        </h2>
        <p className="text-sm leading-6 text-text-secondary">{t('auth.login.description')}</p>
      </div>

      <form className="space-y-4" onSubmit={handleSubmit}>
        <FormField label={t('common.labels.username')}>
          <Input
            aria-invalid={Boolean(error)}
            autoComplete="username"
            className={error ? 'border-danger focus:border-danger focus:shadow-[0_0_0_4px_rgba(197,48,48,0.12)]' : undefined}
            placeholder={t('auth.login.usernamePlaceholder')}
            value={username}
            onChange={(event) => setUsername(event.target.value)}
          />
        </FormField>
        <FormField label={t('common.labels.password')}>
          <AuthPasswordField
            aria-invalid={Boolean(error)}
            autoComplete="current-password"
            hideLabel={t('auth.common.hidePassword')}
            inputClassName={error ? 'border-danger focus:border-danger focus:shadow-[0_0_0_4px_rgba(197,48,48,0.12)]' : undefined}
            placeholder={t('auth.login.passwordPlaceholder')}
            showLabel={t('auth.common.showPassword')}
            value={password}
            onChange={setPassword}
          />
        </FormField>
        {error ? <p className="text-sm text-danger">{error}</p> : null}
        <Button disabled={submitting} fullWidth type="submit">
          {submitting ? t('common.states.loading') : t('auth.login.submitAction')}
        </Button>
      </form>

      <div className="flex flex-wrap items-center justify-between gap-3 text-sm text-text-secondary">
        <p>
          {t('auth.login.registerPrompt')}{' '}
          <Link className="font-medium text-accent" to="/register">
            {t('auth.login.registerAction')}
          </Link>
        </p>
        <Link className="font-medium text-accent" to="/password-reset">
          {t('auth.login.passwordResetAction')}
        </Link>
      </div>
    </Card>
  )
}
