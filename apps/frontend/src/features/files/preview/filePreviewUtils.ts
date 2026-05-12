export type FilePreviewMode = 'image' | 'pdf' | 'text' | 'code' | 'office' | 'archive' | 'media' | 'unsupported'

const IMAGE_EXTENSIONS = new Set(['png', 'jpg', 'jpeg', 'webp', 'gif', 'svg'])
const PDF_EXTENSIONS = new Set(['pdf'])
const OFFICE_EXTENSIONS = new Set(['doc', 'docx', 'xls', 'xlsx', 'ppt', 'pptx', 'odt', 'ods', 'odp'])
const ARCHIVE_EXTENSIONS = new Set(['zip', 'rar', '7z', 'tar', 'gz'])
const MEDIA_EXTENSIONS = new Set(['mp4', 'webm', 'mov', 'mp3', 'wav', 'ogg'])
const TEXT_EXTENSIONS = new Set([
  'txt', 'md', 'csv', 'json', 'xml', 'yaml', 'yml', 'log',
  'java', 'kt', 'js', 'jsx', 'ts', 'tsx', 'html', 'css', 'scss',
  'sql', 'py', 'cs', 'cpp', 'c', 'h', 'php', 'go', 'rs',
  'sh', 'bash', 'zsh', 'dockerfile', 'gradle', 'properties', 'env.example',
])

const CODE_EXTENSIONS = new Set([
  'java', 'kt', 'js', 'jsx', 'ts', 'tsx', 'html', 'css', 'scss',
  'sql', 'py', 'cs', 'cpp', 'c', 'h', 'php', 'go', 'rs',
  'sh', 'bash', 'zsh', 'dockerfile', 'gradle', 'properties',
])

const OFFICE_MIME_PREFIXES = [
  'application/vnd.openxmlformats-officedocument',
  'application/vnd.ms-',
  'application/msword',
  'application/vnd.oasis.opendocument',
]

const ARCHIVE_MIME_PREFIXES = [
  'application/zip',
  'application/x-zip',
  'application/x-rar',
  'application/x-7z',
  'application/gzip',
  'application/x-gzip',
  'application/x-tar',
  'application/x-gtar',
]

const TEXT_MIME_PREFIXES = [
  'text/',
  'application/json',
  'application/xml',
  'application/x-yaml',
  'application/yaml',
  'application/x-sh',
  'application/javascript',
  'application/x-javascript',
]

export interface PreviewableFileLike {
  originalFileName: string
  contentType: string | null
  previewAvailable?: boolean
  sizeBytes?: number | null
}

export function detectFilePreviewMode(file: PreviewableFileLike): FilePreviewMode {
  const mime = normalizeMime(file.contentType)
  const extension = getFileExtension(file.originalFileName)

  if (mime.startsWith('image/') || IMAGE_EXTENSIONS.has(extension)) {
    return 'image'
  }

  if (mime === 'application/pdf' || PDF_EXTENSIONS.has(extension)) {
    return 'pdf'
  }

  if (mime.startsWith('video/') || mime.startsWith('audio/') || MEDIA_EXTENSIONS.has(extension)) {
    return 'media'
  }

  if (isOfficeMime(mime) || OFFICE_EXTENSIONS.has(extension)) {
    return 'office'
  }

  if (isArchiveMime(mime) || ARCHIVE_EXTENSIONS.has(extension)) {
    return 'archive'
  }

  if (isTextMime(mime) || TEXT_EXTENSIONS.has(extension)) {
    return CODE_EXTENSIONS.has(extension) ? 'code' : 'text'
  }

  return 'unsupported'
}

export function getFileExtension(fileName: string) {
  const normalized = fileName.trim().toLowerCase()
  if (!normalized) {
    return ''
  }

  if (normalized === 'dockerfile') {
    return 'dockerfile'
  }

  if (normalized.endsWith('.env.example')) {
    return 'env.example'
  }

  const extensionIndex = normalized.lastIndexOf('.')
  if (extensionIndex <= 0 || extensionIndex === normalized.length - 1) {
    return ''
  }

  return normalized.slice(extensionIndex + 1)
}

export function formatFileSize(sizeBytes?: number | null) {
  if (!sizeBytes || sizeBytes <= 0) {
    return '0 B'
  }

  const units = ['B', 'KB', 'MB', 'GB']
  let value = sizeBytes
  let unitIndex = 0

  while (value >= 1024 && unitIndex < units.length - 1) {
    value /= 1024
    unitIndex += 1
  }

  const precision = unitIndex === 0 ? 0 : value >= 10 ? 1 : 2
  return `${value.toFixed(precision)} ${units[unitIndex]}`
}

export function formatFileType(contentType: string | null, originalFileName: string) {
  if (contentType && contentType.trim().length > 0) {
    return contentType
  }

  const extension = getFileExtension(originalFileName)
  if (!extension) {
    return 'application/octet-stream'
  }

  return extension.toUpperCase()
}

export function isPreviewFetchPreferred(file: PreviewableFileLike, mode: FilePreviewMode) {
  if (mode === 'image' || mode === 'pdf') {
    return Boolean(file.previewAvailable)
  }

  return false
}

function normalizeMime(contentType: string | null) {
  const normalized = (contentType ?? '').trim().toLowerCase()
  if (!normalized) {
    return ''
  }

  const delimiterIndex = normalized.indexOf(';')
  return delimiterIndex >= 0 ? normalized.slice(0, delimiterIndex).trim() : normalized
}

function isOfficeMime(mime: string) {
  return OFFICE_MIME_PREFIXES.some((prefix) => mime.startsWith(prefix))
}

function isArchiveMime(mime: string) {
  return ARCHIVE_MIME_PREFIXES.some((prefix) => mime.startsWith(prefix))
}

function isTextMime(mime: string) {
  return TEXT_MIME_PREFIXES.some((prefix) => mime.startsWith(prefix))
}
