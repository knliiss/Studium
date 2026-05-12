import { useCallback, useEffect, useMemo, useRef, useState } from 'react'

import { detectFilePreviewMode, isPreviewFetchPreferred, type FilePreviewMode, type PreviewableFileLike } from '@/features/files/preview/filePreviewUtils'

const MAX_TEXT_PREVIEW_BYTES = 512 * 1024

export interface FileAttachmentItem extends PreviewableFileLike {
  id: string
  fileId?: string | null
  displayName?: string | null
  createdAt?: string | null
}

export interface FilePreviewState {
  status: 'idle' | 'loading' | 'ready' | 'error'
  mode: FilePreviewMode
  objectUrl: string | null
  pdfData: Uint8Array | null
  textContent: string | null
  truncated: boolean
  error: string | null
}

interface UseFilePreviewOptions {
  selectedFile: FileAttachmentItem | null
  fetchPreviewBlob: (file: FileAttachmentItem) => Promise<Blob>
  fetchDownloadBlob: (file: FileAttachmentItem) => Promise<Blob>
}

export function useFilePreview({
  selectedFile,
  fetchPreviewBlob,
  fetchDownloadBlob,
}: UseFilePreviewOptions) {
  const [reloadToken, setReloadToken] = useState(0)
  const [state, setState] = useState<FilePreviewState>({
    status: 'idle',
    mode: 'unsupported',
    objectUrl: null,
    pdfData: null,
    textContent: null,
    truncated: false,
    error: null,
  })
  const objectUrlRef = useRef<string | null>(null)

  const mode = useMemo(
    () => (selectedFile ? detectFilePreviewMode(selectedFile) : 'unsupported'),
    [selectedFile],
  )

  useEffect(() => {
    let cancelled = false

    const revokeObjectUrl = () => {
      if (!objectUrlRef.current) {
        return
      }
      URL.revokeObjectURL(objectUrlRef.current)
      objectUrlRef.current = null
    }

    const safeSetState = (nextState: FilePreviewState) => {
      if (!cancelled) {
        setState(nextState)
      }
    }

    async function load() {
      if (!selectedFile) {
        revokeObjectUrl()
        safeSetState({
          status: 'idle',
          mode: 'unsupported',
          objectUrl: null,
          pdfData: null,
          textContent: null,
          truncated: false,
          error: null,
        })
        return
      }

      safeSetState({
        status: 'loading',
        mode,
        objectUrl: null,
        pdfData: null,
        textContent: null,
        truncated: false,
        error: null,
      })

      if (mode === 'office' || mode === 'archive' || mode === 'unsupported') {
        revokeObjectUrl()
        safeSetState({
          status: 'ready',
          mode,
          objectUrl: null,
          pdfData: null,
          textContent: null,
          truncated: false,
          error: null,
        })
        return
      }

      try {
        const usePreviewFetch = isPreviewFetchPreferred(selectedFile, mode)
        const blob = await (usePreviewFetch ? fetchPreviewBlob(selectedFile) : fetchDownloadBlob(selectedFile))

        if (mode === 'pdf') {
          const pdfData = new Uint8Array(await blob.arrayBuffer())
          revokeObjectUrl()
          safeSetState({
            status: 'ready',
            mode,
            objectUrl: null,
            pdfData,
            textContent: null,
            truncated: false,
            error: null,
          })
          return
        }

        if (mode === 'text' || mode === 'code') {
          const previewBlob = blob.size > MAX_TEXT_PREVIEW_BYTES
            ? blob.slice(0, MAX_TEXT_PREVIEW_BYTES)
            : blob

          const textContent = await previewBlob.text()
          revokeObjectUrl()
          safeSetState({
            status: 'ready',
            mode,
            objectUrl: null,
            pdfData: null,
            textContent,
            truncated: blob.size > MAX_TEXT_PREVIEW_BYTES,
            error: null,
          })
          return
        }

        const objectUrl = URL.createObjectURL(blob)
        revokeObjectUrl()
        objectUrlRef.current = objectUrl
        safeSetState({
          status: 'ready',
          mode,
          objectUrl,
          pdfData: null,
          textContent: null,
          truncated: false,
          error: null,
        })
      } catch (error) {
        revokeObjectUrl()
        safeSetState({
          status: 'error',
          mode,
          objectUrl: null,
          pdfData: null,
          textContent: null,
          truncated: false,
          error: error instanceof Error ? error.message : 'preview-error',
        })
      }
    }

    void load()

    return () => {
      cancelled = true
      revokeObjectUrl()
    }
  }, [fetchDownloadBlob, fetchPreviewBlob, mode, reloadToken, selectedFile])

  const reload = useCallback(() => {
    setReloadToken((current) => current + 1)
  }, [])

  return {
    mode,
    reload,
    state,
  }
}
