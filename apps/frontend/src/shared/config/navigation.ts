import type { LucideIcon } from 'lucide-react'
import {
  BarChart3,
  Bell,
  BookOpen,
  Building2,
  CalendarDays,
  ClipboardCheck,
  FileSearch,
  GraduationCap,
  LayoutDashboard,
  ListChecks,
  Search,
  Settings,
  Shield,
  UserRoundCheck,
  Users,
} from 'lucide-react'

import type { Role } from '@/shared/types/api'

export type NavigationNestedSource = 'subjects' | 'assignments' | 'tests' | 'groups' | 'teachers' | 'rooms'

export interface NavigationItem {
  id: string
  to: string
  labelKey: string
  icon: LucideIcon
  roles: Role[]
  matchPrefixes?: string[]
  nestedSource?: NavigationNestedSource
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
        id: 'dashboard',
        to: '/dashboard',
        labelKey: 'navigation.shared.dashboard',
        icon: LayoutDashboard,
        roles: ['STUDENT'],
        matchPrefixes: ['/dashboard'],
      },
      {
        id: 'schedule',
        to: '/schedule',
        labelKey: 'navigation.shared.schedule',
        icon: CalendarDays,
        roles: ['STUDENT'],
        matchPrefixes: ['/schedule'],
      },
      {
        id: 'notifications',
        to: '/notifications',
        labelKey: 'navigation.shared.notifications',
        icon: Bell,
        roles: ['STUDENT'],
        matchPrefixes: ['/notifications'],
      },
    ],
  },
  {
    id: 'student-learning',
    labelKey: 'navigation.groups.learning',
    roles: ['STUDENT'],
    items: [
      {
        id: 'subjects',
        to: '/subjects',
        labelKey: 'navigation.shared.mySubjects',
        icon: BookOpen,
        roles: ['STUDENT'],
        matchPrefixes: ['/subjects', '/education'],
        nestedSource: 'subjects',
      },
      {
        id: 'assignments',
        to: '/assignments',
        labelKey: 'navigation.shared.assignments',
        icon: ClipboardCheck,
        roles: ['STUDENT'],
        matchPrefixes: ['/assignments'],
        nestedSource: 'assignments',
      },
      {
        id: 'tests',
        to: '/tests',
        labelKey: 'navigation.shared.tests',
        icon: ListChecks,
        roles: ['STUDENT'],
        matchPrefixes: ['/tests'],
        nestedSource: 'tests',
      },
      {
        id: 'grades',
        to: '/grades',
        labelKey: 'navigation.shared.grades',
        icon: GraduationCap,
        roles: ['STUDENT'],
        matchPrefixes: ['/grades'],
      },
      {
        id: 'my-group',
        to: '/my-group',
        labelKey: 'navigation.shared.myGroup',
        icon: Users,
        roles: ['STUDENT'],
        matchPrefixes: ['/my-group', '/groups/my'],
      },
      {
        id: 'materials',
        to: '/education',
        labelKey: 'navigation.shared.materials',
        icon: FileSearch,
        roles: ['STUDENT'],
        matchPrefixes: ['/education', '/materials', '/lectures'],
      },
    ],
  },
  {
    id: 'student-personal',
    labelKey: 'navigation.groups.personal',
    roles: ['STUDENT'],
    items: [
      {
        id: 'settings',
        to: '/profile',
        labelKey: 'navigation.shared.settings',
        icon: Settings,
        roles: ['STUDENT'],
        matchPrefixes: ['/profile'],
      },
    ],
  },
  {
    id: 'teacher-today',
    labelKey: 'navigation.groups.today',
    roles: ['TEACHER'],
    items: [
      {
        id: 'dashboard',
        to: '/dashboard',
        labelKey: 'navigation.shared.dashboard',
        icon: LayoutDashboard,
        roles: ['TEACHER'],
        matchPrefixes: ['/dashboard'],
      },
      {
        id: 'schedule',
        to: '/schedule',
        labelKey: 'navigation.shared.schedule',
        icon: CalendarDays,
        roles: ['TEACHER'],
        matchPrefixes: ['/schedule'],
      },
      {
        id: 'notifications',
        to: '/notifications',
        labelKey: 'navigation.shared.notifications',
        icon: Bell,
        roles: ['TEACHER'],
        matchPrefixes: ['/notifications'],
      },
    ],
  },
  {
    id: 'teacher-work',
    labelKey: 'navigation.groups.work',
    roles: ['TEACHER'],
    items: [
      {
        id: 'review',
        to: '/review',
        labelKey: 'navigation.shared.reviewQueue',
        icon: ClipboardCheck,
        roles: ['TEACHER'],
        matchPrefixes: ['/review', '/submissions'],
      },
      {
        id: 'subjects',
        to: '/subjects',
        labelKey: 'navigation.shared.mySubjects',
        icon: BookOpen,
        roles: ['TEACHER'],
        matchPrefixes: ['/subjects', '/education'],
        nestedSource: 'subjects',
      },
      {
        id: 'gradebook',
        to: '/assignments',
        labelKey: 'navigation.shared.gradebook',
        icon: GraduationCap,
        roles: ['TEACHER'],
        matchPrefixes: ['/assignments'],
      },
      {
        id: 'group-rosters',
        to: '/groups',
        labelKey: 'navigation.shared.groupRosters',
        icon: Users,
        roles: ['TEACHER'],
        matchPrefixes: ['/groups'],
      },
    ],
  },
  {
    id: 'teacher-create',
    labelKey: 'navigation.groups.create',
    roles: ['TEACHER'],
    items: [
      {
        id: 'create-assignment',
        to: '/assignments',
        labelKey: 'navigation.shared.createAssignment',
        icon: ClipboardCheck,
        roles: ['TEACHER'],
        matchPrefixes: ['/assignments'],
      },
      {
        id: 'create-test',
        to: '/tests',
        labelKey: 'navigation.shared.createTest',
        icon: ListChecks,
        roles: ['TEACHER'],
        matchPrefixes: ['/tests'],
      },
      {
        id: 'create-material',
        to: '/education',
        labelKey: 'navigation.shared.createMaterial',
        icon: FileSearch,
        roles: ['TEACHER'],
        matchPrefixes: ['/education', '/materials', '/lectures'],
      },
    ],
  },
  {
    id: 'teacher-personal',
    labelKey: 'navigation.groups.personal',
    roles: ['TEACHER'],
    items: [
      {
        id: 'settings',
        to: '/profile',
        labelKey: 'navigation.shared.settings',
        icon: Settings,
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
        id: 'dashboard',
        to: '/dashboard',
        labelKey: 'navigation.shared.dashboard',
        icon: LayoutDashboard,
        roles: ['ADMIN', 'OWNER'],
        matchPrefixes: ['/dashboard', '/admin/dashboard', '/owner/dashboard'],
      },
      {
        id: 'search',
        to: '/search',
        labelKey: 'navigation.shared.search',
        icon: Search,
        roles: ['ADMIN', 'OWNER'],
        matchPrefixes: ['/search', '/admin/search'],
      },
      {
        id: 'notifications',
        to: '/notifications',
        labelKey: 'navigation.shared.notifications',
        icon: Bell,
        roles: ['ADMIN', 'OWNER'],
        matchPrefixes: ['/notifications'],
      },
    ],
  },
  {
    id: 'admin-academic',
    labelKey: 'navigation.groups.academicManagement',
    roles: ['ADMIN', 'OWNER'],
    items: [
      {
        id: 'subjects',
        to: '/subjects',
        labelKey: 'navigation.shared.subjects',
        icon: BookOpen,
        roles: ['ADMIN', 'OWNER'],
        matchPrefixes: ['/subjects', '/education'],
        nestedSource: 'subjects',
      },
      {
        id: 'groups',
        to: '/groups',
        labelKey: 'navigation.shared.groups',
        icon: Users,
        roles: ['ADMIN', 'OWNER'],
        matchPrefixes: ['/groups', '/academic/groups'],
        nestedSource: 'groups',
      },
      {
        id: 'teachers',
        to: '/teachers',
        labelKey: 'navigation.shared.teachers',
        icon: UserRoundCheck,
        roles: ['ADMIN', 'OWNER'],
        matchPrefixes: ['/teachers'],
        nestedSource: 'teachers',
      },
      {
        id: 'specialties',
        to: '/academic/specialties',
        labelKey: 'navigation.shared.specialties',
        icon: FileSearch,
        roles: ['ADMIN', 'OWNER'],
        matchPrefixes: ['/academic/specialties', '/specialties', '/streams', '/curriculum-plans'],
      },
      {
        id: 'rooms',
        to: '/rooms',
        labelKey: 'navigation.shared.rooms',
        icon: Building2,
        roles: ['ADMIN', 'OWNER'],
        matchPrefixes: ['/rooms', '/academic/rooms', '/admin/rooms'],
        nestedSource: 'rooms',
      },
      {
        id: 'schedule',
        to: '/schedule',
        labelKey: 'navigation.shared.schedule',
        icon: CalendarDays,
        roles: ['ADMIN', 'OWNER'],
        matchPrefixes: ['/schedule', '/admin/schedule'],
      },
    ],
  },
  {
    id: 'admin-platform',
    labelKey: 'navigation.groups.platform',
    roles: ['ADMIN', 'OWNER'],
    items: [
      {
        id: 'users',
        to: '/admin/users',
        labelKey: 'navigation.admin.users',
        icon: Users,
        roles: ['ADMIN', 'OWNER'],
        matchPrefixes: ['/admin/users'],
      },
      {
        id: 'analytics',
        to: '/analytics',
        labelKey: 'navigation.shared.analytics',
        icon: BarChart3,
        roles: ['ADMIN', 'OWNER'],
        matchPrefixes: ['/analytics', '/admin/analytics'],
      },
      {
        id: 'audit',
        to: '/admin/audit',
        labelKey: 'navigation.admin.audit',
        icon: Shield,
        roles: ['ADMIN', 'OWNER'],
        matchPrefixes: ['/admin/audit'],
      },
    ],
  },
  {
    id: 'admin-personal',
    labelKey: 'navigation.groups.personal',
    roles: ['ADMIN', 'OWNER'],
    items: [
      {
        id: 'settings',
        to: '/profile',
        labelKey: 'navigation.shared.settings',
        icon: Settings,
        roles: ['ADMIN', 'OWNER'],
        matchPrefixes: ['/profile'],
      },
    ],
  },
]
