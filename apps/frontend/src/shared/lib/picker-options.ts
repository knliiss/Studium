import type {
  AdminUserResponse,
  GroupResponse,
  LessonSlotResponse,
  RoomResponse,
  SearchItemResponse,
  SubjectResponse,
  TopicResponse,
  UserSummaryResponse,
} from '@/shared/types/api'
import type { EntityOption } from '@/shared/ui/EntityPicker'

export function toGroupOption(group: GroupResponse | SearchItemResponse): EntityOption {
  if ('name' in group) {
    return {
      value: group.id,
      label: group.name,
    }
  }

  return {
    value: group.id,
    label: group.title,
    description: group.subtitle ?? undefined,
  }
}

export function toSubjectOption(subject: SubjectResponse, groupName?: string, groupsLabel?: string): EntityOption {
  const description = groupName
    ?? (subject.groupIds.length > 1 && groupsLabel ? `${subject.groupIds.length} ${groupsLabel}` : undefined)

  return {
    value: subject.id,
    label: subject.name,
    description,
  }
}

export function toTopicOption(topic: TopicResponse, subjectName?: string): EntityOption {
  return {
    value: topic.id,
    label: topic.title,
    description: subjectName,
  }
}

export function toTeacherOption(user: AdminUserResponse | UserSummaryResponse): EntityOption {
  const preferredLabel = ('displayName' in user ? user.displayName?.trim() : '') || user.username
  const secondaryParts = [user.username !== preferredLabel ? user.username : null, user.email].filter(Boolean)

  return {
    value: user.id,
    label: preferredLabel,
    description: secondaryParts.join(' · '),
  }
}

export function toUserOption(user: AdminUserResponse | UserSummaryResponse): EntityOption {
  return toTeacherOption(user)
}

export function toRoomOption(room: RoomResponse): EntityOption {
  return {
    value: room.id,
    label: `${room.building} ${room.code}`,
    description: `${room.capacity}`,
  }
}

export function toSlotOption(slot: LessonSlotResponse, pairLabel: string): EntityOption {
  return {
    value: slot.id,
    label: `${pairLabel} ${slot.number}`,
    description: `${slot.startTime}–${slot.endTime}`,
  }
}
