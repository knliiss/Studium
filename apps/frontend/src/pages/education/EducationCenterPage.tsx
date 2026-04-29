import { BookOpen, GraduationCap, Users } from 'lucide-react'
import { useTranslation } from 'react-i18next'
import { Link } from 'react-router-dom'

import { Card } from '@/shared/ui/Card'
import { PageHeader } from '@/shared/ui/PageHeader'

const academicBlocks = [
  {
    to: '/subjects',
    titleKey: 'navigation.shared.subjects',
    descriptionKey: 'education.academicCenter.subjectsDescription',
    icon: BookOpen,
  },
  {
    to: '/groups',
    titleKey: 'navigation.shared.groups',
    descriptionKey: 'education.academicCenter.groupsDescription',
    icon: Users,
  },
  {
    to: '/teachers',
    titleKey: 'navigation.shared.teachers',
    descriptionKey: 'education.academicCenter.teachersDescription',
    icon: GraduationCap,
  },
]

export function EducationCenterPage() {
  const { t } = useTranslation()

  return (
    <div className="space-y-6">
      <PageHeader
        description={t('education.academicCenter.description')}
        title={t('navigation.shared.education')}
      />

      <div className="grid gap-5 lg:grid-cols-3">
        {academicBlocks.map((block) => {
          const Icon = block.icon

          return (
            <Link key={block.to} to={block.to}>
              <Card className="group h-full space-y-5 transition hover:-translate-y-0.5 hover:border-accent/40 hover:shadow-[var(--shadow-soft)]">
                <span className="inline-flex h-12 w-12 items-center justify-center rounded-[16px] bg-accent-muted text-accent transition group-hover:bg-accent group-hover:text-accent-foreground">
                  <Icon className="h-5 w-5" />
                </span>
                <div className="space-y-2">
                  <h2 className="text-xl font-semibold tracking-[-0.03em] text-text-primary">
                    {t(block.titleKey)}
                  </h2>
                  <p className="text-sm leading-6 text-text-secondary">
                    {t(block.descriptionKey)}
                  </p>
                </div>
                <p className="text-sm font-semibold text-accent">{t('common.actions.open')}</p>
              </Card>
            </Link>
          )
        })}
      </div>
    </div>
  )
}
