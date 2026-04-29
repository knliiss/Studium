import { useState } from 'react'
import { useTranslation } from 'react-i18next'
import { Link, useNavigate } from 'react-router-dom'

import { useAuth } from '@/features/auth/useAuth'
import {
  buildApiFieldErrors,
  getLocalizedApiErrorMessage,
  hasApiFieldErrors,
  normalizeApiError,
} from '@/shared/lib/api-errors'
import { getDashboardPath } from '@/shared/lib/roles'
import { Button } from '@/shared/ui/Button'
import { Card } from '@/shared/ui/Card'
import { FormField } from '@/shared/ui/FormField'
import { Input } from '@/shared/ui/Input'

export function RegisterPage() {
  const { register } = useAuth()
  const navigate = useNavigate()
  const { t } = useTranslation(['common', 'validation', 'errors'])
  const [form, setForm] = useState({ username: '', email: '', password: '' })
  const [fieldErrors, setFieldErrors] = useState<Record<string, string>>({})
  const [error, setError] = useState<string | null>(null)
  const [submitting, setSubmitting] = useState(false)

  async function handleSubmit(event: React.FormEvent<HTMLFormElement>) {
    event.preventDefault()
    setError(null)
    setFieldErrors({})

    if (!form.username.trim() || !form.email.trim() || !form.password.trim()) {
      setError(t('validation:required'))
      return
    }

    setSubmitting(true)
    try {
      const response = await register({
        username: form.username.trim(),
        email: form.email.trim(),
        password: form.password,
      })
      if (response.status === 'AUTHENTICATED' && response.user) {
        navigate(getDashboardPath(response.user.roles), { replace: true })
      } else {
        navigate('/mfa')
      }
    } catch (submissionError: unknown) {
      const normalizedError = normalizeApiError(submissionError)
      const localizedMessage = getLocalizedApiErrorMessage(normalizedError, t)
      const nextFieldErrors = buildApiFieldErrors(normalizedError, localizedMessage)
      const hasVisibleFieldErrors = ['username', 'email', 'password'].some(
        (field) => nextFieldErrors[field],
      )

      setFieldErrors(nextFieldErrors)
      setError(hasApiFieldErrors(normalizedError) && hasVisibleFieldErrors ? null : localizedMessage)
    } finally {
      setSubmitting(false)
    }
  }

  return (
    <Card className="w-full max-w-xl space-y-6 rounded-[24px] p-8">
      <div className="space-y-2">
        <h2 className="text-3xl font-bold tracking-[-0.04em] text-text-primary">
          {t('auth.register.title')}
        </h2>
        <p className="text-sm leading-6 text-text-secondary">{t('auth.register.description')}</p>
      </div>

      <form className="space-y-4" onSubmit={handleSubmit}>
        <FormField error={fieldErrors.username} label={t('common.labels.username')}>
          <Input
            aria-invalid={Boolean(fieldErrors.username)}
            autoComplete="username"
            className={fieldErrors.username ? 'border-danger focus:border-danger focus:shadow-[0_0_0_4px_rgba(197,48,48,0.12)]' : undefined}
            value={form.username}
            onChange={(event) =>
              setForm((current) => ({ ...current, username: event.target.value }))
            }
          />
        </FormField>
        <FormField error={fieldErrors.email} label={t('common.labels.email')}>
          <Input
            aria-invalid={Boolean(fieldErrors.email)}
            autoComplete="email"
            className={fieldErrors.email ? 'border-danger focus:border-danger focus:shadow-[0_0_0_4px_rgba(197,48,48,0.12)]' : undefined}
            type="email"
            value={form.email}
            onChange={(event) => setForm((current) => ({ ...current, email: event.target.value }))}
          />
        </FormField>
        <FormField error={fieldErrors.password} label={t('common.labels.password')}>
          <Input
            aria-invalid={Boolean(fieldErrors.password)}
            autoComplete="new-password"
            className={fieldErrors.password ? 'border-danger focus:border-danger focus:shadow-[0_0_0_4px_rgba(197,48,48,0.12)]' : undefined}
            type="password"
            value={form.password}
            onChange={(event) =>
              setForm((current) => ({ ...current, password: event.target.value }))
            }
          />
        </FormField>
        {error ? <p className="text-sm text-danger">{error}</p> : null}
        <Button disabled={submitting} fullWidth type="submit">
          {submitting ? t('common.states.loading') : t('auth.register.submitAction')}
        </Button>
      </form>

      <p className="text-sm text-text-secondary">
        {t('auth.register.loginPrompt')}{' '}
        <Link className="font-medium text-accent" to="/login">
          {t('auth.register.loginAction')}
        </Link>
      </p>
    </Card>
  )
}
