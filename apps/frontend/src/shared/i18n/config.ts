import i18next from 'i18next'
import { initReactI18next } from 'react-i18next'

import enCommon from '@/shared/i18n/locales/en/common.json'
import enErrors from '@/shared/i18n/locales/en/errors.json'
import enValidation from '@/shared/i18n/locales/en/validation.json'
import plCommon from '@/shared/i18n/locales/pl/common.json'
import plErrors from '@/shared/i18n/locales/pl/errors.json'
import plValidation from '@/shared/i18n/locales/pl/validation.json'
import ukCommon from '@/shared/i18n/locales/uk/common.json'
import ukErrors from '@/shared/i18n/locales/uk/errors.json'
import ukValidation from '@/shared/i18n/locales/uk/validation.json'
import { readStoredLocale, writeStoredLocale } from '@/shared/lib/storage'

export type LocaleCode = 'en' | 'uk' | 'pl'

const isDevelopment = import.meta.env.DEV

const resources = {
  en: {
    common: enCommon,
    errors: enErrors,
    validation: enValidation,
  },
  uk: {
    common: ukCommon,
    errors: ukErrors,
    validation: ukValidation,
  },
  pl: {
    common: plCommon,
    errors: plErrors,
    validation: plValidation,
  },
} as const

function readNestedValue(value: unknown, path: string[]) {
  return path.reduce<unknown>((current, part) => {
    if (typeof current !== 'object' || current === null || !(part in current)) {
      return undefined
    }

    return (current as Record<string, unknown>)[part]
  }, value)
}

function humanizeKey(key: string) {
  return key
    .split('.')
    .at(-1)
    ?.replace(/([a-z0-9])([A-Z])/g, '$1 $2')
    .replace(/[_-]+/g, ' ')
    .replace(/^./, (value) => value.toUpperCase())
    ?? ''
}

function resolveEnglishFallback(fullKey: string) {
  const [namespace, rawKey] = fullKey.includes(':') ? fullKey.split(':', 2) : ['common', fullKey]
  const value = readNestedValue(resources.en[namespace as keyof typeof resources.en], rawKey.split('.'))

  return typeof value === 'string' && value.trim() ? value : humanizeKey(rawKey)
}

function detectBrowserLocale(): LocaleCode {
  const language = globalThis.navigator?.language?.toLowerCase() ?? 'en'
  if (language.startsWith('uk')) {
    return 'uk'
  }
  if (language.startsWith('pl')) {
    return 'pl'
  }
  return 'en'
}

const initialLocale = readStoredLocale() ?? detectBrowserLocale()

export const i18n = i18next.createInstance()

void i18n.use(initReactI18next).init({
  lng: initialLocale,
  fallbackLng: 'en',
  defaultNS: 'common',
  ns: ['common', 'errors', 'validation'],
  returnNull: false,
  returnEmptyString: false,
  saveMissing: isDevelopment,
  interpolation: {
    escapeValue: false,
  },
  resources,
  parseMissingKeyHandler: (key) => resolveEnglishFallback(key),
  missingKeyHandler: (_languages, namespace, key) => {
    if (isDevelopment) {
      console.warn(`[i18n] Missing translation`, { namespace, key })
    }
  },
})

i18n.on('languageChanged', (language) => {
  if (language === 'en' || language === 'uk' || language === 'pl') {
    writeStoredLocale(language)
    document.documentElement.lang = language
  }
})
