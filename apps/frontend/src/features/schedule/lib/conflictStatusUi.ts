export type ConflictStatus = 'idle' | 'checking' | 'clear' | 'conflict' | 'error'
export type ConflictStatusTone = 'neutral' | 'success' | 'danger' | 'warning'

export function getConflictStatusTone(status: ConflictStatus): ConflictStatusTone {
  if (status === 'clear') {
    return 'success'
  }
  if (status === 'conflict') {
    return 'danger'
  }
  if (status === 'error') {
    return 'warning'
  }
  return 'neutral'
}

export function getConflictStatusClasses(status: ConflictStatus) {
  const tone = getConflictStatusTone(status)
  if (tone === 'success') {
    return 'border-success/30 bg-success/5'
  }
  if (tone === 'danger') {
    return 'border-danger/30 bg-danger/5'
  }
  if (tone === 'warning') {
    return 'border-warning/30 bg-warning/5'
  }
  return 'border-border bg-surface-muted'
}
