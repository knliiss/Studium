import type { PropsWithChildren } from 'react'
import { useState } from 'react'
import { useTranslation } from 'react-i18next'

import { Sidebar } from '@/widgets/shell/Sidebar'
import { Topbar } from '@/widgets/shell/Topbar'

export function ShellFrame({ children }: PropsWithChildren) {
  const { t } = useTranslation()
  const [sidebarOpen, setSidebarOpen] = useState(false)

  return (
    <div className="app-grid">
      <Sidebar open={sidebarOpen} onToggle={() => setSidebarOpen((current) => !current)} />
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
