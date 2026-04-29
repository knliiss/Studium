import type { ReactNode } from 'react'

import { FormField } from '@/shared/ui/FormField'
import { Input } from '@/shared/ui/Input'
import { Select } from '@/shared/ui/Select'

export interface EntityOption {
  value: string
  label: string
  description?: string
}

interface EntityPickerProps {
  label: string
  value: string
  onChange: (value: string) => void
  options: EntityOption[]
  placeholder: string
  emptyLabel?: string
  loadingLabel?: string
  disabled?: boolean
  loading?: boolean
  error?: string | null
  hint?: ReactNode
  searchValue?: string
  onSearchChange?: (value: string) => void
  searchLabel?: string
  searchPlaceholder?: string
}

export function EntityPicker({
  disabled,
  emptyLabel,
  error,
  hint,
  label,
  loading,
  loadingLabel,
  onChange,
  onSearchChange,
  options,
  placeholder,
  searchLabel,
  searchPlaceholder,
  searchValue,
  value,
}: EntityPickerProps) {
  const showSearch = typeof onSearchChange === 'function'
  const selectHint = !loading && options.length === 0 && emptyLabel ? emptyLabel : hint

  return (
    <FormField error={error} hint={selectHint} label={label}>
      {showSearch ? (
        <Input
          value={searchValue ?? ''}
          placeholder={searchPlaceholder}
          aria-label={searchLabel ?? label}
          onChange={(event) => onSearchChange(event.target.value)}
        />
      ) : null}
      <Select
        disabled={disabled || loading}
        value={value}
        onChange={(event) => onChange(event.target.value)}
      >
        <option value="">{loading ? (loadingLabel ?? placeholder) : placeholder}</option>
        {options.map((option) => (
          <option key={option.value} value={option.value}>
            {option.description ? `${option.label} · ${option.description}` : option.label}
          </option>
        ))}
      </Select>
    </FormField>
  )
}
