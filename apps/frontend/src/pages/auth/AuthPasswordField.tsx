import { Eye, EyeOff } from 'lucide-react'
import { useState } from 'react'

import { Input } from '@/shared/ui/Input'

interface AuthPasswordFieldProps {
  value: string
  onChange: (value: string) => void
  placeholder?: string
  autoComplete?: string
  ariaInvalid?: boolean
  ariaDescribedBy?: string
  inputClassName?: string
  showLabel: string
  hideLabel: string
}

export function AuthPasswordField({
  ariaInvalid,
  ariaDescribedBy,
  autoComplete,
  hideLabel,
  inputClassName,
  onChange,
  placeholder,
  showLabel,
  value,
}: AuthPasswordFieldProps) {
  const [visible, setVisible] = useState(false)

  return (
    <div className="relative">
      <Input
        aria-invalid={ariaInvalid}
        aria-describedby={ariaDescribedBy}
        autoComplete={autoComplete}
        className={inputClassName ? `${inputClassName} pr-12` : 'pr-12'}
        placeholder={placeholder}
        type={visible ? 'text' : 'password'}
        value={value}
        onChange={(event) => onChange(event.target.value)}
      />
      <button
        aria-label={visible ? hideLabel : showLabel}
        className="absolute right-1 top-1 inline-flex h-10 w-10 items-center justify-center rounded-[10px] text-text-muted transition hover:bg-surface-muted hover:text-text-primary"
        type="button"
        onClick={() => setVisible((current) => !current)}
      >
        {visible ? <EyeOff className="h-4 w-4" /> : <Eye className="h-4 w-4" />}
      </button>
    </div>
  )
}
