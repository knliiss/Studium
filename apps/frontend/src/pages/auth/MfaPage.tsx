import { useEffect, useState } from 'react'
import { useTranslation } from 'react-i18next'
import { useNavigate } from 'react-router-dom'

import { useAuth } from '@/features/auth/useAuth'
import { authApi } from '@/shared/api/auth'
import {
  getLocalizedApiErrorMessage,
  normalizeApiError,
} from '@/shared/lib/api-errors'
import { getDashboardPath } from '@/shared/lib/roles'
import { Button } from '@/shared/ui/Button'
import { Card } from '@/shared/ui/Card'
import { FormField } from '@/shared/ui/FormField'
import { Input } from '@/shared/ui/Input'
import { Select } from '@/shared/ui/Select'

export function MfaPage() {
  const { acceptAuthResponse, mfaChallenge } = useAuth()
  const navigate = useNavigate()
  const { t } = useTranslation(['common', 'errors'])
  const [method, setMethod] = useState('')
  const [code, setCode] = useState('')
  const [error, setError] = useState<string | null>(null)
  const [submitting, setSubmitting] = useState(false)
  const [dispatching, setDispatching] = useState(false)

  useEffect(() => {
    if (!mfaChallenge) {
      navigate('/login', { replace: true })
    }
  }, [mfaChallenge, navigate])

  if (!mfaChallenge) {
    return null
  }

  const selectedMethod = method || mfaChallenge.selectedMethod || mfaChallenge.availableMethods[0] || ''
  const challengeToken = mfaChallenge.challengeToken

  async function handleDispatch() {
    if (!selectedMethod) {
      return
    }

    setDispatching(true)
    try {
      await authApi.dispatchMfa({
        challengeToken,
        method: selectedMethod,
      })
    } catch (submissionError: unknown) {
      setError(getLocalizedApiErrorMessage(normalizeApiError(submissionError), t))
    } finally {
      setDispatching(false)
    }
  }

  async function handleSubmit(event: React.FormEvent<HTMLFormElement>) {
    event.preventDefault()
    setError(null)
    setSubmitting(true)
    try {
      const response = await authApi.verifyMfa({
        challengeToken,
        method: selectedMethod,
        code,
      })
      acceptAuthResponse(response)
      if (response.user) {
        navigate(getDashboardPath(response.user.roles), { replace: true })
      }
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
          {t('auth.mfa.title')}
        </h2>
        <p className="text-sm leading-6 text-text-secondary">{t('auth.mfa.description')}</p>
      </div>
      <form className="space-y-4" onSubmit={handleSubmit}>
        <FormField label={t('auth.mfa.method')}>
          <Select value={selectedMethod} onChange={(event) => setMethod(event.target.value)}>
            {mfaChallenge.availableMethods.map((availableMethod) => (
              <option key={availableMethod} value={availableMethod}>
                {t(`auth.mfa.methods.${availableMethod}`, { defaultValue: availableMethod })}
              </option>
            ))}
          </Select>
        </FormField>
        <FormField label={t('auth.mfa.code')}>
          <Input value={code} onChange={(event) => setCode(event.target.value)} />
        </FormField>
        {error ? <p className="text-sm text-danger">{error}</p> : null}
        <div className="flex flex-wrap gap-3">
          <Button
            disabled={dispatching}
            type="button"
            variant="secondary"
            onClick={handleDispatch}
          >
            {dispatching ? t('common.states.loading') : t('auth.mfa.dispatchAction')}
          </Button>
          <Button disabled={submitting} type="submit">
            {submitting ? t('common.states.loading') : t('common.actions.submit')}
          </Button>
        </div>
      </form>
    </Card>
  )
}
