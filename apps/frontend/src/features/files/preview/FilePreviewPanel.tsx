import { ArrowLeftRight, ChevronLeft, ChevronRight, Download, Expand, Minus, Plus, RefreshCw } from 'lucide-react'
import { useCallback, useEffect, useMemo, useRef, useState } from 'react'
import type { RefObject } from 'react'
import { useTranslation } from 'react-i18next'
import { getDocument, GlobalWorkerOptions } from 'pdfjs-dist/legacy/build/pdf.mjs'
import pdfWorkerUrl from 'pdfjs-dist/legacy/build/pdf.worker.min.mjs?url'
import type { PDFDocumentProxy } from 'pdfjs-dist/types/src/display/api'

import { FileTypeIcon } from '@/features/files/preview/FileTypeIcon'
import { formatFileSize, formatFileType, getFileExtension } from '@/features/files/preview/filePreviewUtils'
import type { FileAttachmentItem } from '@/features/files/preview/useFilePreview'
import { useFilePreview } from '@/features/files/preview/useFilePreview'
import { formatDateTime } from '@/shared/lib/format'
import { Button } from '@/shared/ui/Button'

interface FilePreviewPanelProps {
  selectedFile: FileAttachmentItem | null
  fetchPreviewBlob: (file: FileAttachmentItem) => Promise<Blob>
  fetchDownloadBlob: (file: FileAttachmentItem) => Promise<Blob>
  onDownload: (file: FileAttachmentItem) => Promise<void> | void
  title?: string
}

const A4_WIDTH_AT_100 = 794

GlobalWorkerOptions.workerSrc = pdfWorkerUrl

export function FilePreviewPanel({
  selectedFile,
  fetchPreviewBlob,
  fetchDownloadBlob,
  onDownload,
  title,
}: FilePreviewPanelProps) {
  const { t } = useTranslation()
  const [pdfFullscreen, setPdfFullscreen] = useState(false)
  const { mode, reload, state } = useFilePreview({
    selectedFile,
    fetchPreviewBlob,
    fetchDownloadBlob,
  })

  const isAudioFile = Boolean(
    selectedFile
    && (
      (selectedFile.contentType ?? '').toLowerCase().startsWith('audio/')
      || ['mp3', 'wav', 'ogg'].includes(getFileExtension(selectedFile.originalFileName))
    ),
  )

  return (
    <div className="space-y-3 rounded-[14px] border border-border bg-surface p-3">
      <div className="flex flex-wrap items-start justify-between gap-3">
        <div className="min-w-0">
          <p className="text-sm font-semibold text-text-primary">{title ?? t('files.preview')}</p>
          {selectedFile ? (
            <>
              <p className="truncate text-sm text-text-secondary">{selectedFile.displayName?.trim() || selectedFile.originalFileName}</p>
              <p className="text-xs text-text-muted">
                {formatFileType(selectedFile.contentType, selectedFile.originalFileName)} · {formatFileSize(selectedFile.sizeBytes)}
                {selectedFile.createdAt ? ` · ${formatDateTime(selectedFile.createdAt)}` : ''}
              </p>
            </>
          ) : null}
        </div>

        {selectedFile ? (
          <div className="flex flex-wrap gap-2">
            {(mode === 'text' || mode === 'code') && state.textContent ? (
              <Button
                variant="ghost"
                onClick={async () => {
                  try {
                    await navigator.clipboard.writeText(state.textContent ?? '')
                  } catch {
                    // Clipboard access may be unavailable in some browser contexts.
                  }
                }}
              >
                {t('files.copy')}
              </Button>
            ) : null}
            {(mode === 'image' || mode === 'media') && state.objectUrl ? (
              <Button
                variant="ghost"
                onClick={() => {
                  window.open(state.objectUrl!, '_blank', 'noopener,noreferrer')
                }}
              >
                <Expand className="mr-2 h-4 w-4" />
                {t('files.fullscreen')}
              </Button>
            ) : null}
            <Button variant="secondary" onClick={() => onDownload(selectedFile)}>
              <Download className="mr-2 h-4 w-4" />
              {t('files.download')}
            </Button>
          </div>
        ) : null}
      </div>

      {!selectedFile ? (
        <div className="rounded-[12px] border border-border bg-surface-muted px-4 py-8 text-sm text-text-secondary">
          {t('files.selectFileToPreview')}
        </div>
      ) : null}

      {selectedFile && state.status === 'loading' ? (
        <div className="rounded-[12px] border border-border bg-surface-muted px-4 py-8 text-sm text-text-secondary">
          {t('files.loadingPreview')}
        </div>
      ) : null}

      {selectedFile && state.status === 'error' ? (
        <div className="space-y-3 rounded-[12px] border border-danger/30 bg-danger/5 px-4 py-4">
          <div>
            <p className="text-sm font-medium text-danger">{t('files.previewError')}</p>
            <p className="text-xs text-text-muted">{selectedFile.displayName?.trim() || selectedFile.originalFileName}</p>
          </div>
          <div className="flex flex-wrap gap-2">
            <Button variant="secondary" onClick={() => onDownload(selectedFile)}>
              <Download className="mr-2 h-4 w-4" />
              {t('files.download')}
            </Button>
            <Button variant="ghost" onClick={reload}>
              <RefreshCw className="mr-2 h-4 w-4" />
              {t('common.actions.retry')}
            </Button>
          </div>
        </div>
      ) : null}

      {selectedFile && state.status === 'ready' && mode === 'image' && state.objectUrl ? (
        <img
          alt={selectedFile.displayName?.trim() || selectedFile.originalFileName}
          className="max-h-[76vh] w-full rounded-[12px] border border-border object-contain"
          src={state.objectUrl}
        />
      ) : null}

      {selectedFile && state.status === 'ready' && mode === 'pdf' && state.pdfData ? (
        <PdfPreviewWorkspace
          key={`inline-${selectedFile.id}-${state.pdfData.length}`}
          fileName={selectedFile.displayName?.trim() || selectedFile.originalFileName}
          pdfData={state.pdfData}
          fullscreen={false}
          onDownload={() => onDownload(selectedFile)}
          onToggleFullscreen={() => setPdfFullscreen(true)}
        />
      ) : null}

      {selectedFile && state.status === 'ready' && mode === 'media' && state.objectUrl ? (
        isAudioFile ? (
          <audio className="w-full" controls src={state.objectUrl} />
        ) : (
          <video className="max-h-[76vh] w-full rounded-[12px] border border-border" controls src={state.objectUrl} />
        )
      ) : null}

      {selectedFile && state.status === 'ready' && (mode === 'text' || mode === 'code') ? (
        <div className="space-y-2">
          <pre className="max-h-[76vh] overflow-auto rounded-[12px] border border-border bg-surface-muted p-4 font-mono text-xs text-text-primary whitespace-pre-wrap break-words">
            {state.textContent ?? ''}
          </pre>
          {state.truncated ? (
            <p className="text-xs text-text-muted">{t('files.textPreviewTruncated')}</p>
          ) : null}
        </div>
      ) : null}

      {selectedFile && state.status === 'ready' && mode === 'office' ? (
        <div className="rounded-[12px] border border-border bg-surface-muted p-4">
          <div className="flex flex-wrap items-start gap-3">
            <span className="rounded-[12px] border border-border bg-surface p-2 text-text-secondary">
              <FileTypeIcon
                extension={getFileExtension(selectedFile.originalFileName)}
                mode="office"
                className="h-5 w-5"
              />
            </span>
            <div className="min-w-0 flex-1">
              <p className="text-sm font-semibold text-text-primary">{t('files.officePreviewUnavailableTitle')}</p>
              <p className="mt-1 text-sm text-text-secondary">{t('files.officePreviewUnavailable')}</p>
              <p className="mt-2 text-xs text-text-muted">{t('files.officePreviewUnavailableNote')}</p>
            </div>
            <Button variant="secondary" onClick={() => onDownload(selectedFile)}>
              <Download className="mr-2 h-4 w-4" />
              {t('files.download')}
            </Button>
          </div>
        </div>
      ) : null}

      {selectedFile && state.status === 'ready' && (mode === 'archive' || mode === 'unsupported') ? (
        <div className="rounded-[12px] border border-border bg-surface-muted px-4 py-5 text-sm text-text-secondary">
          {t('files.previewUnavailable')}
        </div>
      ) : null}

      {selectedFile && state.status === 'ready' && mode === 'pdf' && state.pdfData && pdfFullscreen ? (
        <div className="fixed inset-0 z-50 bg-background/95 p-4 backdrop-blur-sm md:p-8">
          <div className="mx-auto h-full max-w-[1400px]">
            <PdfPreviewWorkspace
              key={`fullscreen-${selectedFile.id}-${state.pdfData.length}`}
              fileName={selectedFile.displayName?.trim() || selectedFile.originalFileName}
              pdfData={state.pdfData}
              fullscreen
              onDownload={() => onDownload(selectedFile)}
              onToggleFullscreen={() => setPdfFullscreen(false)}
            />
          </div>
        </div>
      ) : null}
    </div>
  )
}

function PdfPreviewWorkspace({
  pdfData,
  fileName,
  fullscreen,
  onDownload,
  onToggleFullscreen,
}: {
  pdfData: Uint8Array
  fileName: string
  fullscreen: boolean
  onDownload: () => void
  onToggleFullscreen: () => void
}) {
  const { t } = useTranslation()
  const [pdfDocument, setPdfDocument] = useState<PDFDocumentProxy | null>(null)
  const [pageNumber, setPageNumber] = useState(1)
  const [zoomPercent, setZoomPercent] = useState(100)
  const [fitWidth, setFitWidth] = useState(false)
  const [isRendering, setIsRendering] = useState(false)
  const [loadError, setLoadError] = useState<string | null>(null)
  const viewportRef = useRef<HTMLDivElement | null>(null)
  const canvasRef = useRef<HTMLCanvasElement | null>(null)
  const renderTaskRef = useRef<{ cancel: () => void } | null>(null)
  const viewportWidth = useElementWidth(viewportRef)

  useEffect(() => {
    let cancelled = false

    async function loadPdfDocument() {
      setLoadError(null)

      try {
        const pdfDataCopy = pdfData.slice()
        const loadingTask = getDocument({ data: pdfDataCopy })
        const loadedDocument = await loadingTask.promise
        if (cancelled) {
          return
        }
        setPdfDocument(loadedDocument)
      } catch (error) {
        if (cancelled) {
          return
        }
        if (import.meta.env.DEV && fullscreen) {
          console.error('PDF preview load failed', {
            fileName,
            fullscreen,
            dataBytes: pdfData.byteLength,
            error,
          })
        }
        setLoadError(error instanceof Error ? error.message : 'pdf-load-error')
      }
    }

    void loadPdfDocument()

    return () => {
      cancelled = true
      if (renderTaskRef.current) {
        renderTaskRef.current.cancel()
        renderTaskRef.current = null
      }
    }
  }, [fileName, fullscreen, pdfData])

  useEffect(() => {
    if (!pdfDocument || !canvasRef.current || loadError) {
      return
    }

    let cancelled = false
    const currentDocument = pdfDocument

    async function renderPage() {
      setIsRendering(true)
      setLoadError(null)

      try {
        const page = await currentDocument.getPage(pageNumber)
        if (cancelled || !canvasRef.current) {
          return
        }

        const viewportAtScaleOne = page.getViewport({ scale: 1 })
        const a4BaseScale = A4_WIDTH_AT_100 / viewportAtScaleOne.width
        const scale = fitWidth && viewportWidth > 120
          ? Math.max(0.2, (viewportWidth - 24) / viewportAtScaleOne.width)
          : a4BaseScale * (zoomPercent / 100)

        const viewport = page.getViewport({ scale })
        const canvas = canvasRef.current
        const context = canvas.getContext('2d')

        if (!context) {
          setLoadError('pdf-context-error')
          return
        }

        const pixelRatio = window.devicePixelRatio || 1
        canvas.width = Math.floor(viewport.width * pixelRatio)
        canvas.height = Math.floor(viewport.height * pixelRatio)
        canvas.style.width = `${Math.floor(viewport.width)}px`
        canvas.style.height = `${Math.floor(viewport.height)}px`

        context.setTransform(pixelRatio, 0, 0, pixelRatio, 0, 0)
        context.clearRect(0, 0, viewport.width, viewport.height)

        if (renderTaskRef.current) {
          renderTaskRef.current.cancel()
        }

        const task = page.render({
          canvasContext: context,
          viewport,
        })

        renderTaskRef.current = task
        await task.promise
        if (!cancelled) {
          setIsRendering(false)
        }
      } catch (error) {
        if (cancelled) {
          return
        }
        if (error instanceof Error && error.name === 'RenderingCancelledException') {
          return
        }
        if (import.meta.env.DEV && fullscreen) {
          console.error('PDF preview render failed', {
            fileName,
            fullscreen,
            pageNumber,
            dataBytes: pdfData.byteLength,
            error,
          })
        }
        setIsRendering(false)
        setLoadError(error instanceof Error ? error.message : 'pdf-render-error')
      }
    }

    void renderPage()

    return () => {
      cancelled = true
    }
  }, [fileName, fullscreen, fitWidth, loadError, pageNumber, pdfDocument, pdfData.byteLength, viewportWidth, zoomPercent])

  const totalPages = pdfDocument?.numPages ?? 0
  const canNavigate = Boolean(pdfDocument) && !loadError
  const canGoBack = canNavigate && pageNumber > 1
  const canGoForward = canNavigate && pageNumber < totalPages

  const containerClassName = useMemo(
    () => fullscreen
      ? 'flex h-full flex-col rounded-[16px] border border-border bg-surface p-3'
      : 'space-y-3 rounded-[12px] border border-border bg-surface-muted p-3',
    [fullscreen],
  )

  const viewportClassName = useMemo(
    () => fullscreen
      ? 'flex-1 overflow-auto rounded-[12px] border border-border bg-background/40 p-3'
      : 'max-h-[78vh] overflow-auto rounded-[12px] border border-border bg-background/40 p-3',
    [fullscreen],
  )

  const changeZoom = useCallback((delta: number) => {
    setFitWidth(false)
    setZoomPercent((current) => {
      const next = current + delta
      if (next < 50) {
        return 50
      }
      if (next > 300) {
        return 300
      }
      return next
    })
  }, [])

  return (
    <div className={containerClassName}>
      <div className="flex flex-wrap items-center gap-2">
        <div className="min-w-0 flex-1">
          <p className="truncate text-sm font-semibold text-text-primary">{fileName}</p>
        </div>
        {canNavigate ? (
          <div className="flex items-center gap-1">
            <Button
              aria-label={t('files.previousPage')}
              className="h-9 min-h-9 px-2"
              disabled={!canGoBack}
              title={t('files.previousPage')}
              variant="ghost"
              onClick={() => setPageNumber((current) => Math.max(1, current - 1))}
            >
              <ChevronLeft className="h-4 w-4" />
            </Button>
            <span
              className="min-w-[64px] rounded-full border border-border bg-surface px-3 py-1 text-xs font-semibold text-text-secondary"
              title={t('files.pdfPageOf', { current: pageNumber, total: totalPages || 1 })}
            >
              {pageNumber} / {totalPages || 1}
            </span>
            <Button
              aria-label={t('files.nextPage')}
              className="h-9 min-h-9 px-2"
              disabled={!canGoForward}
              title={t('files.nextPage')}
              variant="ghost"
              onClick={() => setPageNumber((current) => Math.min(totalPages, current + 1))}
            >
              <ChevronRight className="h-4 w-4" />
            </Button>
          </div>
        ) : null}
        {canNavigate ? (
          <div className="flex items-center gap-1">
            <Button
              aria-label={t('files.zoomOut')}
              className="h-9 min-h-9 px-2"
              title={t('files.zoomOut')}
              variant="ghost"
              onClick={() => changeZoom(-10)}
            >
              <Minus className="h-4 w-4" />
            </Button>
            <Button
              aria-label={t('files.actualSize')}
              className="h-9 min-h-9 px-2 text-xs"
              title={t('files.actualSize')}
              variant="ghost"
              onClick={() => {
                setFitWidth(false)
                setZoomPercent(100)
              }}
            >
              100%
            </Button>
            <Button
              aria-label={t('files.zoomIn')}
              className="h-9 min-h-9 px-2"
              title={t('files.zoomIn')}
              variant="ghost"
              onClick={() => changeZoom(10)}
            >
              <Plus className="h-4 w-4" />
            </Button>
            <Button
              aria-label={t('files.fitWidth')}
              className="h-9 min-h-9 px-2"
              title={t('files.fitWidth')}
              variant="ghost"
              onClick={() => {
                setFitWidth(true)
              }}
            >
              <ArrowLeftRight className="h-4 w-4" />
            </Button>
          </div>
        ) : null}
        <Button
          aria-label={t('files.download')}
          className="h-9 min-h-9 px-2"
          title={t('files.download')}
          variant="secondary"
          onClick={onDownload}
        >
          <Download className="h-4 w-4" />
        </Button>
        <Button
          aria-label={fullscreen ? t('files.exitFullscreen') : t('files.fullscreen')}
          className="h-9 min-h-9 px-2"
          title={fullscreen ? t('files.exitFullscreen') : t('files.fullscreen')}
          variant="secondary"
          onClick={onToggleFullscreen}
        >
          <Expand className="h-4 w-4" />
        </Button>
      </div>

      <div className={viewportClassName} ref={viewportRef}>
        {loadError ? (
          <div className="space-y-3 rounded-[12px] border border-danger/30 bg-danger/5 px-4 py-4">
            <p className="text-sm font-medium text-danger">{t('files.previewError')}</p>
            <Button variant="secondary" onClick={onDownload}>
              <Download className="mr-2 h-4 w-4" />
              {t('files.download')}
            </Button>
          </div>
        ) : null}
        {!pdfDocument && !loadError ? (
          <p className="text-sm text-text-secondary">{t('files.loadingPreview')}</p>
        ) : null}
        {pdfDocument && isRendering && !loadError ? (
          <p className="text-sm text-text-secondary">{t('files.renderingPage')}</p>
        ) : null}
        {pdfDocument && !loadError ? (
          <div className="flex min-w-max justify-center">
            <canvas className="rounded-[10px] border border-border bg-white" ref={canvasRef} />
          </div>
        ) : null}
      </div>
    </div>
  )
}

function useElementWidth(ref: RefObject<HTMLElement | null>) {
  const [width, setWidth] = useState(0)

  useEffect(() => {
    const element = ref.current
    if (!element) {
      return
    }

    setWidth(element.clientWidth)

    const observer = new ResizeObserver((entries) => {
      for (const entry of entries) {
        setWidth(entry.contentRect.width)
      }
    })

    observer.observe(element)

    return () => {
      observer.disconnect()
    }
  }, [ref])

  return width
}
