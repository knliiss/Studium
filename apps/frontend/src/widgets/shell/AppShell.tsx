import { Outlet } from 'react-router-dom'

import { ShellFrame } from '@/widgets/shell/ShellFrame'

export function AppShell() {
  return (
    <ShellFrame>
      <Outlet />
    </ShellFrame>
  )
}
