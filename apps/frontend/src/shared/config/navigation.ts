import type { LucideIcon } from 'lucide-react'
import {
  BarChart3,
  Bell,
  BookOpen,
  CalendarDays,
  ClipboardCheck,
  FileChartColumn,
  FileSearch,
  GraduationCap,
  LayoutDashboard,
  Shield,
  SquareUserRound,
  Users,
} from 'lucide-react'

import type { Role } from '@/shared/types/api'

export interface NavigationItem {
  to: string
  labelKey: string
  icon: LucideIcon
  roles: Role[]
  matchPrefixes?: string[]
}

export interface NavigationGroup {
  id: string
  labelKey: string
  roles: Role[]
  items: NavigationItem[]
}

export const navigationGroups: NavigationGroup[] = [
  {
    id: 'student-main',
    labelKey: 'navigation.groups.main',
    roles: ['STUDENT'],
    items: [
      {
        to: '/dashboard',
        labelKey: 'navigation.shared.dashboard',
        icon: LayoutDashboard,
        roles: ['STUDENT'],
        matchPrefixes: ['/dashboard'],
      },
      {
        to: '/schedule',
        labelKey: 'navigation.shared.schedule',
        icon: CalendarDays,
        roles: ['STUDENT'],
        matchPrefixes: ['/schedule'],
      },
    ],
  },
  {
    id: 'student-learning',
    labelKey: 'navigation.groups.learning',
    roles: ['STUDENT'],
    items: [
      {
        to: '/education',
        labelKey: 'navigation.shared.education',
        icon: BookOpen,
        roles: ['STUDENT'],
        matchPrefixes: ['/education', '/subjects', '/groups', '/teachers'],
      },
      {
        to: '/assignments',
        labelKey: 'navigation.shared.assignments',
        icon: ClipboardCheck,
        roles: ['STUDENT'],
        matchPrefixes: ['/assignments'],
      },
      {
        to: '/tests',
        labelKey: 'navigation.shared.tests',
        icon: FileChartColumn,
        roles: ['STUDENT'],
        matchPrefixes: ['/tests'],
      },
      {
        to: '/grades',
        labelKey: 'navigation.shared.grades',
        icon: GraduationCap,
        roles: ['STUDENT'],
        matchPrefixes: ['/grades'],
      },
    ],
  },
  {
    id: 'student-personal',
    labelKey: 'navigation.groups.personal',
    roles: ['STUDENT'],
    items: [
      {
        to: '/notifications',
        labelKey: 'navigation.shared.notifications',
        icon: Bell,
        roles: ['STUDENT'],
        matchPrefixes: ['/notifications'],
      },
      {
        to: '/analytics',
        labelKey: 'navigation.shared.analytics',
        icon: BarChart3,
        roles: ['STUDENT'],
        matchPrefixes: ['/analytics'],
      },
      {
        to: '/profile',
        labelKey: 'navigation.shared.profile',
        icon: SquareUserRound,
        roles: ['STUDENT'],
        matchPrefixes: ['/profile'],
      },
    ],
  },
  {
    id: 'teacher-main',
    labelKey: 'navigation.groups.main',
    roles: ['TEACHER'],
    items: [
      {
        to: '/dashboard',
        labelKey: 'navigation.shared.dashboard',
        icon: LayoutDashboard,
        roles: ['TEACHER'],
        matchPrefixes: ['/dashboard'],
      },
      {
        to: '/schedule',
        labelKey: 'navigation.shared.schedule',
        icon: CalendarDays,
        roles: ['TEACHER'],
        matchPrefixes: ['/schedule'],
      },
    ],
  },
  {
    id: 'teacher-teaching',
    labelKey: 'navigation.groups.teaching',
    roles: ['TEACHER'],
    items: [
      {
        to: '/education',
        labelKey: 'navigation.shared.education',
        icon: BookOpen,
        roles: ['TEACHER'],
        matchPrefixes: ['/education', '/subjects', '/groups', '/teachers'],
      },
      {
        to: '/assignments',
        labelKey: 'navigation.shared.assignments',
        icon: ClipboardCheck,
        roles: ['TEACHER'],
        matchPrefixes: ['/assignments'],
      },
      {
        to: '/review',
        labelKey: 'navigation.shared.review',
        icon: FileChartColumn,
        roles: ['TEACHER'],
        matchPrefixes: ['/review', '/submissions'],
      },
      {
        to: '/tests',
        labelKey: 'navigation.shared.tests',
        icon: FileChartColumn,
        roles: ['TEACHER'],
        matchPrefixes: ['/tests'],
      },
    ],
  },
  {
    id: 'teacher-insights',
    labelKey: 'navigation.groups.insights',
    roles: ['TEACHER'],
    items: [
      {
        to: '/analytics',
        labelKey: 'navigation.shared.analytics',
        icon: BarChart3,
        roles: ['TEACHER'],
        matchPrefixes: ['/analytics'],
      },
    ],
  },
  {
    id: 'teacher-personal',
    labelKey: 'navigation.groups.personal',
    roles: ['TEACHER'],
    items: [
      {
        to: '/notifications',
        labelKey: 'navigation.shared.notifications',
        icon: Bell,
        roles: ['TEACHER'],
        matchPrefixes: ['/notifications'],
      },
      {
        to: '/profile',
        labelKey: 'navigation.shared.profile',
        icon: SquareUserRound,
        roles: ['TEACHER'],
        matchPrefixes: ['/profile'],
      },
    ],
  },
  {
    id: 'admin-main',
    labelKey: 'navigation.groups.main',
    roles: ['ADMIN', 'OWNER'],
    items: [
      {
        to: '/dashboard',
        labelKey: 'navigation.shared.dashboard',
        icon: LayoutDashboard,
        roles: ['ADMIN', 'OWNER'],
        matchPrefixes: ['/dashboard', '/admin/dashboard', '/owner/dashboard'],
      },
    ],
  },
  {
    id: 'admin-academics',
    labelKey: 'navigation.groups.academicManagement',
    roles: ['ADMIN', 'OWNER'],
    items: [
      {
        to: '/academic',
        labelKey: 'navigation.shared.education',
        icon: BookOpen,
        roles: ['ADMIN', 'OWNER'],
        matchPrefixes: ['/academic', '/education'],
      },
      {
        to: '/subjects',
        labelKey: 'navigation.shared.subjects',
        icon: FileSearch,
        roles: ['ADMIN', 'OWNER'],
        matchPrefixes: ['/subjects'],
      },
      {
        to: '/groups',
        labelKey: 'navigation.shared.groups',
        icon: Users,
        roles: ['ADMIN', 'OWNER'],
        matchPrefixes: ['/groups', '/academic/groups'],
      },
      {
        to: '/teachers',
        labelKey: 'navigation.shared.teachers',
        icon: GraduationCap,
        roles: ['ADMIN', 'OWNER'],
        matchPrefixes: ['/teachers'],
      },
      {
        to: '/academic/specialties',
        labelKey: 'navigation.shared.specialties',
        icon: BookOpen,
        roles: ['ADMIN', 'OWNER'],
        matchPrefixes: ['/academic/specialties', '/specialties', '/streams', '/curriculum-plans'],
      },
      {
        to: '/schedule',
        labelKey: 'navigation.shared.scheduleManagement',
        icon: CalendarDays,
        roles: ['ADMIN', 'OWNER'],
        matchPrefixes: ['/schedule'],
      },
    ],
  },
  {
    id: 'admin-platform',
    labelKey: 'navigation.groups.platform',
    roles: ['ADMIN', 'OWNER'],
    items: [
      {
        to: '/admin/users',
        labelKey: 'navigation.admin.users',
        icon: Users,
        roles: ['ADMIN', 'OWNER'],
        matchPrefixes: ['/admin/users'],
      },
      {
        to: '/analytics',
        labelKey: 'navigation.shared.analytics',
        icon: BarChart3,
        roles: ['ADMIN', 'OWNER'],
        matchPrefixes: ['/analytics'],
      },
      {
        to: '/admin/audit',
        labelKey: 'navigation.admin.audit',
        icon: Shield,
        roles: ['ADMIN', 'OWNER'],
        matchPrefixes: ['/admin/audit'],
      },
      {
        to: '/search',
        labelKey: 'navigation.shared.search',
        icon: FileSearch,
        roles: ['ADMIN', 'OWNER'],
        matchPrefixes: ['/search', '/admin/search'],
      },
    ],
  },
  {
    id: 'admin-personal',
    labelKey: 'navigation.groups.personal',
    roles: ['ADMIN', 'OWNER'],
    items: [
      {
        to: '/notifications',
        labelKey: 'navigation.shared.notifications',
        icon: Bell,
        roles: ['ADMIN', 'OWNER'],
        matchPrefixes: ['/notifications'],
      },
      {
        to: '/profile',
        labelKey: 'navigation.shared.profile',
        icon: SquareUserRound,
        roles: ['ADMIN', 'OWNER'],
        matchPrefixes: ['/profile'],
      },
    ],
  },
]
