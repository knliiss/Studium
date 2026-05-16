import type { CSSProperties, PropsWithChildren } from 'react'
import { useEffect, useState } from 'react'
import { useTranslation } from 'react-i18next'

import { Sidebar } from '@/widgets/shell/Sidebar'
import { Topbar } from '@/widgets/shell/Topbar'

const SIDEBAR_COLLAPSED_STORAGE_KEY = 'studium:sidebar-collapsed'

export function ShellFrame({ children }: PropsWithChildren) {
  const { t } = useTranslation()
  const [sidebarOpen, setSidebarOpen] = useState(false)
  const [sidebarCollapsed, setSidebarCollapsed] = useState(() => {
    if (typeof window === 'undefined') {
      return false
    }
    return window.localStorage.getItem(SIDEBAR_COLLAPSED_STORAGE_KEY) === '1'
  })

  useEffect(() => {
    if (typeof window === 'undefined') {
      return
    }
    window.localStorage.setItem(SIDEBAR_COLLAPSED_STORAGE_KEY, sidebarCollapsed ? '1' : '0')
  }, [sidebarCollapsed])

  return (
    <div className="app-grid" style={{ '--sidebar-width': sidebarCollapsed ? '72px' : '264px' } as CSSProperties}>
      <Sidebar
        collapsed={sidebarCollapsed}
        open={sidebarOpen}
        onToggle={() => setSidebarOpen((current) => !current)}
        onToggleCollapsed={() => setSidebarCollapsed((current) => !current)}
      />
      <div className="page-shell">
        <Topbar />
        <main className="page-content">{children}</main>
      </div>
      {sidebarOpen ? (
        <button
          aria-label={t('common.actions.closeNavigation')}
          className="fixed inset-0 z-30 bg-overlay lg:hidden"
          type="button"
          onClick={() => setSidebarOpen(false)}
        />
      ) : null}
    </div>
  )
}
