import { apiClient } from '@/shared/api/client'
import type {
  AcademicSemesterResponse,
  AdminDashboardOverviewResponse,
  AdminUserPageResponse,
  AdminUserResponse,
  AdminUserStatsResponse,
  AnswerResponse,
  AssignmentGroupAvailabilityResponse,
  AssignmentAttachmentResponse,
  AssignmentResponse,
  AuditEventResponse,
  DashboardOverviewResponse,
  GradeResponse,
  GroupCurriculumOverrideResponse,
  GroupMembershipResponse,
  GroupOverviewResponse,
  GroupResponse,
  GroupStudentMembershipResponse,
  CurriculumPlanResponse,
  LessonSlotResponse,
  NotificationPageResponse,
  NotificationResponse,
  QuestionResponse,
  ResolvedLessonResponse,
  ResolvedGroupSubjectResponse,
  RoomCapabilityResponse,
  RoomResponse,
  ScheduleConflictCheckResponse,
  ScheduleOverrideResponse,
  ScheduleTemplateResponse,
  SearchPageResponse,
  SpecialtyResponse,
  StoredFileResponse,
  StreamResponse,
  StudentAnalyticsResponse,
  StudentDashboardResponse,
  StudentGroupProgressResponse,
  StudentRiskResponse,
  SubjectAnalyticsResponse,
  SubjectResponse,
  SubmissionCommentResponse,
  SubmissionAttachmentResponse,
  SubmissionResponse,
  TeacherAnalyticsResponse,
  TeacherDashboardResponse,
  TestResponse,
  TestGroupAvailabilityResponse,
  TestQuestionStatisticsResponse,
  TestPreviewViewResponse,
  TestResultQuestionResponse,
  TestResultReviewResponse,
  TestResultResponse,
  TestStudentViewResponse,
  TelegramConnectTokenResponse,
  TelegramLinkStatusResponse,
  TopicMaterialResponse,
  TopicResponse,
  UnreadCountResponse,
  UserProfileResponse,
  UserSummaryResponse,
  LectureAttachmentResponse,
  LectureResponse,
} from '@/shared/types/api'

export interface PaginationParams {
  page?: number
  size?: number
  sortBy?: string
  direction?: 'asc' | 'desc'
}

export const profileService = {
  async getMe() {
    const response = await apiClient.get<UserProfileResponse>('/api/profile/me')
    return response.data
  },
  async updateMe(payload: { displayName?: string; locale?: string; timezone?: string }) {
    const response = await apiClient.patch<UserProfileResponse>('/api/profile/me', payload)
    return response.data
  },
  async updateAvatar(fileId: string) {
    const response = await apiClient.put<UserProfileResponse>('/api/profile/me/avatar', { fileId })
    return response.data
  },
  async deleteAvatar() {
    const response = await apiClient.delete<UserProfileResponse>('/api/profile/me/avatar')
    return response.data
  },
}

export const userDirectoryService = {
  async lookup(userIds: string[]) {
    const response = await apiClient.post<UserSummaryResponse[]>('/api/auth/users/lookup', { userIds })
    return response.data
  },
}

export const educationService = {
  async createGroup(payload: {
    name: string
    specialtyId?: string | null
    studyYear?: number | null
    streamId?: string | null
    subgroupMode?: 'NONE' | 'TWO_SUBGROUPS' | null
  }) {
    const response = await apiClient.post<GroupResponse>('/api/v1/education/groups', payload)
    return response.data
  },
  async listGroups(params: PaginationParams & { q?: string } = {}) {
    const response = await apiClient.get('/api/v1/education/groups', { params })
    return normalizePage<GroupResponse>(response.data)
  },
  async getGroup(id: string) {
    const response = await apiClient.get<GroupResponse>(`/api/v1/education/groups/${id}`)
    return response.data
  },
  async updateGroup(id: string, payload: {
    name: string
    specialtyId?: string | null
    studyYear?: number | null
    streamId?: string | null
    subgroupMode?: 'NONE' | 'TWO_SUBGROUPS' | null
  }) {
    const response = await apiClient.put<GroupResponse>(`/api/v1/education/groups/${id}`, payload)
    return response.data
  },
  async getGroupsByUser(userId: string) {
    const response = await apiClient.get<GroupMembershipResponse[]>(`/api/v1/education/groups/by-user/${userId}`)
    return response.data
  },
  async getResolvedGroupSubjects(groupId: string, semesterNumber?: number) {
    const response = await apiClient.get<ResolvedGroupSubjectResponse[]>(`/api/v1/education/groups/${groupId}/resolved-subjects`, {
      params: {
        semesterNumber: typeof semesterNumber === 'number' ? semesterNumber : undefined,
      },
    })
    return response.data
  },
  async listGroupCurriculumOverrides(groupId: string) {
    const response = await apiClient.get<GroupCurriculumOverrideResponse[]>(`/api/v1/education/groups/${groupId}/curriculum-overrides`)
    return response.data
  },
  async createGroupCurriculumOverride(groupId: string, payload: {
    subjectId: string
    enabled: boolean
    lectureCountOverride?: number | null
    practiceCountOverride?: number | null
    labCountOverride?: number | null
    notes?: string | null
  }) {
    const response = await apiClient.post<GroupCurriculumOverrideResponse>(`/api/v1/education/groups/${groupId}/curriculum-overrides`, payload)
    return response.data
  },
  async updateGroupCurriculumOverride(groupId: string, overrideId: string, payload: {
    enabled: boolean
    lectureCountOverride?: number | null
    practiceCountOverride?: number | null
    labCountOverride?: number | null
    notes?: string | null
  }) {
    const response = await apiClient.put<GroupCurriculumOverrideResponse>(`/api/v1/education/groups/${groupId}/curriculum-overrides/${overrideId}`, payload)
    return response.data
  },
  async deleteGroupCurriculumOverride(groupId: string, overrideId: string) {
    await apiClient.delete(`/api/v1/education/groups/${groupId}/curriculum-overrides/${overrideId}`)
  },
  async listSpecialties(params: { active?: boolean } = {}) {
    const response = await apiClient.get<SpecialtyResponse[]>('/api/v1/education/specialties', { params })
    return response.data
  },
  async getSpecialty(id: string) {
    const response = await apiClient.get<SpecialtyResponse>(`/api/v1/education/specialties/${id}`)
    return response.data
  },
  async createSpecialty(payload: { code: string; name: string; description?: string | null }) {
    const response = await apiClient.post<SpecialtyResponse>('/api/v1/education/specialties', payload)
    return response.data
  },
  async updateSpecialty(id: string, payload: { code: string; name: string; description?: string | null }) {
    const response = await apiClient.put<SpecialtyResponse>(`/api/v1/education/specialties/${id}`, payload)
    return response.data
  },
  async archiveSpecialty(id: string) {
    const response = await apiClient.post<SpecialtyResponse>(`/api/v1/education/specialties/${id}/archive`)
    return response.data
  },
  async restoreSpecialty(id: string) {
    const response = await apiClient.post<SpecialtyResponse>(`/api/v1/education/specialties/${id}/restore`)
    return response.data
  },
  async listStreams(params: {
    specialtyId?: string
    studyYear?: number
    active?: boolean
  } = {}) {
    const response = await apiClient.get<StreamResponse[]>('/api/v1/education/streams', { params })
    return response.data
  },
  async getStream(id: string) {
    const response = await apiClient.get<StreamResponse>(`/api/v1/education/streams/${id}`)
    return response.data
  },
  async getStreamGroups(id: string) {
    const response = await apiClient.get<GroupResponse[]>(`/api/v1/education/streams/${id}/groups`)
    return response.data
  },
  async createStream(payload: { name: string; specialtyId: string; studyYear: number }) {
    const response = await apiClient.post<StreamResponse>('/api/v1/education/streams', payload)
    return response.data
  },
  async updateStream(id: string, payload: { name: string; specialtyId: string; studyYear: number }) {
    const response = await apiClient.put<StreamResponse>(`/api/v1/education/streams/${id}`, payload)
    return response.data
  },
  async archiveStream(id: string) {
    const response = await apiClient.post<StreamResponse>(`/api/v1/education/streams/${id}/archive`)
    return response.data
  },
  async restoreStream(id: string) {
    const response = await apiClient.post<StreamResponse>(`/api/v1/education/streams/${id}/restore`)
    return response.data
  },
  async listCurriculumPlans(params: {
    specialtyId?: string
    studyYear?: number
    semesterNumber?: number
    subjectId?: string
    active?: boolean
  } = {}) {
    const response = await apiClient.get<CurriculumPlanResponse[]>('/api/v1/education/curriculum-plans', { params })
    return response.data
  },
  async getCurriculumPlan(id: string) {
    const response = await apiClient.get<CurriculumPlanResponse>(`/api/v1/education/curriculum-plans/${id}`)
    return response.data
  },
  async createCurriculumPlan(payload: {
    specialtyId: string
    studyYear: number
    semesterNumber: number
    subjectId: string
    lectureCount: number
    practiceCount: number
    labCount: number
    supportsStreamLecture: boolean
    requiresSubgroupsForLabs: boolean
  }) {
    const response = await apiClient.post<CurriculumPlanResponse>('/api/v1/education/curriculum-plans', payload)
    return response.data
  },
  async updateCurriculumPlan(id: string, payload: {
    specialtyId: string
    studyYear: number
    semesterNumber: number
    subjectId: string
    lectureCount: number
    practiceCount: number
    labCount: number
    supportsStreamLecture: boolean
    requiresSubgroupsForLabs: boolean
  }) {
    const response = await apiClient.put<CurriculumPlanResponse>(`/api/v1/education/curriculum-plans/${id}`, payload)
    return response.data
  },
  async archiveCurriculumPlan(id: string) {
    const response = await apiClient.post<CurriculumPlanResponse>(`/api/v1/education/curriculum-plans/${id}/archive`)
    return response.data
  },
  async restoreCurriculumPlan(id: string) {
    const response = await apiClient.post<CurriculumPlanResponse>(`/api/v1/education/curriculum-plans/${id}/restore`)
    return response.data
  },
  async listGroupStudents(groupId: string) {
    const response = await apiClient.get<GroupStudentMembershipResponse[]>(`/api/v1/education/groups/${groupId}/students`)
    return response.data
  },
  async addGroupStudent(groupId: string, payload: { userId: string; role?: string; subgroup?: string }) {
    const response = await apiClient.post<GroupStudentMembershipResponse>(`/api/v1/education/groups/${groupId}/students`, payload)
    return response.data
  },
  async updateGroupStudent(groupId: string, userId: string, payload: { role: string; subgroup: string }) {
    const response = await apiClient.patch<GroupStudentMembershipResponse>(`/api/v1/education/groups/${groupId}/students/${userId}`, payload)
    return response.data
  },
  async removeGroupStudent(groupId: string, userId: string) {
    await apiClient.delete(`/api/v1/education/groups/${groupId}/students/${userId}`)
  },
  async createSubject(payload: { name: string; description?: string }) {
    const response = await apiClient.post<SubjectResponse>('/api/v1/education/subjects', payload)
    return response.data
  },
  async listSubjects(params: PaginationParams & { q?: string } = {}) {
    const response = await apiClient.get('/api/v1/education/subjects', { params })
    return normalizePage<SubjectResponse>(response.data)
  },
  async getSubject(id: string) {
    const response = await apiClient.get<SubjectResponse>(`/api/v1/education/subjects/${id}`)
    return response.data
  },
  async updateSubject(id: string, payload: { name: string; description?: string }) {
    const response = await apiClient.put<SubjectResponse>(`/api/v1/education/subjects/${id}`, payload)
    return response.data
  },
  async getSubjectGroups(id: string) {
    const response = await apiClient.get<string[]>(`/api/v1/education/subjects/${id}/groups`)
    return response.data
  },
  async updateSubjectGroups(id: string, payload: { groupIds: string[] }) {
    const response = await apiClient.put<SubjectResponse>(`/api/v1/education/subjects/${id}/groups`, payload)
    return response.data
  },
  async getSubjectTeachers(id: string) {
    const response = await apiClient.get<string[]>(`/api/v1/education/subjects/${id}/teachers`)
    return response.data
  },
  async updateSubjectTeachers(id: string, payload: { teacherIds: string[] }) {
    const response = await apiClient.put<SubjectResponse>(`/api/v1/education/subjects/${id}/teachers`, payload)
    return response.data
  },
  async getSubjectsByGroup(groupId: string, params: PaginationParams = {}) {
    const response = await apiClient.get(`/api/v1/education/subjects/group/${groupId}`, { params })
    return normalizePage<SubjectResponse>(response.data)
  },
  async createTopic(payload: { subjectId: string; title: string; orderIndex: number }) {
    const response = await apiClient.post<TopicResponse>('/api/v1/education/topics', payload)
    return response.data
  },
  async updateTopic(topicId: string, payload: { title: string; orderIndex: number }) {
    const response = await apiClient.patch<TopicResponse>(`/api/v1/education/topics/${topicId}`, payload)
    return response.data
  },
  async reorderTopics(subjectId: string, topics: Array<{ topicId: string; orderIndex: number }>) {
    const response = await apiClient.patch<TopicResponse[]>(`/api/v1/education/topics/subject/${subjectId}/reorder`, { topics })
    return response.data
  },
  async getTopicsBySubject(subjectId: string, params: PaginationParams = {}) {
    const response = await apiClient.get(`/api/v1/education/topics/subject/${subjectId}`, { params })
    return normalizePage<TopicResponse>(response.data)
  },
  async createLecture(subjectId: string, topicId: string, payload: { title: string; content?: string; orderIndex: number }) {
    const response = await apiClient.post<LectureResponse>(
      `/api/v1/education/subjects/${subjectId}/topics/${topicId}/lectures`,
      payload,
    )
    return response.data
  },
  async updateLecture(lectureId: string, payload: { title: string; content?: string; orderIndex: number }) {
    const response = await apiClient.put<LectureResponse>(`/api/v1/education/lectures/${lectureId}`, payload)
    return response.data
  },
  async getLecture(lectureId: string) {
    const response = await apiClient.get<LectureResponse>(`/api/v1/education/lectures/${lectureId}`)
    return response.data
  },
  async getLecturesByTopic(topicId: string, params: PaginationParams = {}) {
    const response = await apiClient.get(`/api/v1/education/topics/${topicId}/lectures`, { params })
    return normalizePage<LectureResponse>(response.data)
  },
  async publishLecture(lectureId: string) {
    const response = await apiClient.post<LectureResponse>(`/api/v1/education/lectures/${lectureId}/publish`)
    return response.data
  },
  async closeLecture(lectureId: string) {
    const response = await apiClient.post<LectureResponse>(`/api/v1/education/lectures/${lectureId}/close`)
    return response.data
  },
  async reopenLecture(lectureId: string) {
    const response = await apiClient.post<LectureResponse>(`/api/v1/education/lectures/${lectureId}/reopen`)
    return response.data
  },
  async archiveLecture(lectureId: string) {
    const response = await apiClient.post<LectureResponse>(`/api/v1/education/lectures/${lectureId}/archive`)
    return response.data
  },
  async restoreLecture(lectureId: string) {
    const response = await apiClient.post<LectureResponse>(`/api/v1/education/lectures/${lectureId}/restore`)
    return response.data
  },
  async deleteLecture(lectureId: string) {
    await apiClient.delete(`/api/v1/education/lectures/${lectureId}`)
  },
  async moveLecture(lectureId: string, payload: { topicId: string; orderIndex: number }) {
    const response = await apiClient.patch<LectureResponse>(`/api/v1/education/lectures/${lectureId}/position`, payload)
    return response.data
  },
  async listLectureAttachments(lectureId: string) {
    const response = await apiClient.get<LectureAttachmentResponse[]>(`/api/v1/education/lectures/${lectureId}/attachments`)
    return response.data
  },
  async addLectureAttachment(lectureId: string, payload: { fileId: string; displayName?: string }) {
    const response = await apiClient.post<LectureAttachmentResponse>(
      `/api/v1/education/lectures/${lectureId}/attachments`,
      payload,
    )
    return response.data
  },
  async removeLectureAttachment(lectureId: string, attachmentId: string) {
    await apiClient.delete(`/api/v1/education/lectures/${lectureId}/attachments/${attachmentId}`)
  },
  async previewLectureAttachment(lectureId: string, attachmentId: string) {
    const response = await apiClient.get<Blob>(
      `/api/v1/education/lectures/${lectureId}/attachments/${attachmentId}/preview`,
      { responseType: 'blob' },
    )
    return response.data
  },
  async downloadLectureAttachment(lectureId: string, attachmentId: string) {
    const response = await apiClient.get<Blob>(
      `/api/v1/education/lectures/${lectureId}/attachments/${attachmentId}/download`,
      { responseType: 'blob' },
    )
    return response.data
  },
  async getTopicMaterials(topicId: string, params: PaginationParams = {}) {
    const response = await apiClient.get(`/api/v1/education/topics/${topicId}/materials`, { params })
    return normalizePage<TopicMaterialResponse>(response.data)
  },
  async createTopicMaterial(topicId: string, payload: Record<string, unknown>) {
    const response = await apiClient.post<TopicMaterialResponse>(`/api/v1/education/topics/${topicId}/materials`, payload)
    return response.data
  },
  async getTopicMaterial(materialId: string) {
    const response = await apiClient.get<TopicMaterialResponse>(`/api/v1/education/materials/${materialId}`)
    return response.data
  },
  async updateTopicMaterial(materialId: string, payload: Record<string, unknown>) {
    const response = await apiClient.put<TopicMaterialResponse>(`/api/v1/education/materials/${materialId}`, payload)
    return response.data
  },
  async publishTopicMaterial(materialId: string) {
    const response = await apiClient.post<TopicMaterialResponse>(`/api/v1/education/materials/${materialId}/publish`)
    return response.data
  },
  async hideTopicMaterial(materialId: string) {
    const response = await apiClient.post<TopicMaterialResponse>(`/api/v1/education/materials/${materialId}/hide`)
    return response.data
  },
  async archiveTopicMaterial(materialId: string) {
    const response = await apiClient.post<TopicMaterialResponse>(`/api/v1/education/materials/${materialId}/archive`)
    return response.data
  },
  async restoreTopicMaterial(materialId: string) {
    const response = await apiClient.post<TopicMaterialResponse>(`/api/v1/education/materials/${materialId}/restore`)
    return response.data
  },
  async deleteTopicMaterial(materialId: string) {
    await apiClient.delete(`/api/v1/education/materials/${materialId}`)
  },
  async moveTopicMaterial(materialId: string, payload: { topicId: string; orderIndex: number }) {
    const response = await apiClient.patch<TopicMaterialResponse>(`/api/v1/education/materials/${materialId}/position`, payload)
    return response.data
  },
  async previewTopicMaterialFile(materialId: string) {
    const response = await apiClient.get<Blob>(`/api/v1/education/materials/${materialId}/preview`, { responseType: 'blob' })
    return response.data
  },
  async downloadTopicMaterialFile(materialId: string) {
    const response = await apiClient.get<Blob>(`/api/v1/education/materials/${materialId}/download`, { responseType: 'blob' })
    return response.data
  },
}

export const scheduleService = {
  async getMyWeek(startDate: string) {
    const response = await apiClient.get<ResolvedLessonResponse[]>('/api/v1/schedule/me/week', {
      params: { startDate },
    })
    return response.data
  },
  async getMyRange(dateFrom: string, dateTo: string) {
    const response = await apiClient.get<ResolvedLessonResponse[]>('/api/v1/schedule/me/range', {
      params: { dateFrom, dateTo },
    })
    return response.data
  },
  async getGroupWeek(groupId: string, startDate: string) {
    const response = await apiClient.get<ResolvedLessonResponse[]>(`/api/v1/schedule/groups/${groupId}/week`, {
      params: { startDate },
    })
    return response.data
  },
  async getTeacherWeek(teacherId: string, startDate: string) {
    const response = await apiClient.get<ResolvedLessonResponse[]>(
      `/api/v1/schedule/teachers/${teacherId}/week`,
      { params: { startDate } },
    )
    return response.data
  },
  async getRoomWeek(roomId: string, startDate: string) {
    const response = await apiClient.get<ResolvedLessonResponse[]>(`/api/v1/schedule/rooms/${roomId}/week`, {
      params: { startDate },
    })
    return response.data
  },
  async search(params: {
    dateFrom: string
    dateTo: string
    groupId?: string
    teacherId?: string
    roomId?: string
    lessonType?: string
  }) {
    const response = await apiClient.get<ResolvedLessonResponse[]>('/api/v1/schedule/search', { params })
    return response.data
  },
  async getGroupRange(groupId: string, dateFrom: string, dateTo: string) {
    const response = await apiClient.get<ResolvedLessonResponse[]>(`/api/v1/schedule/groups/${groupId}/range`, {
      params: { dateFrom, dateTo },
    })
    return response.data
  },
  async getTeacherRange(teacherId: string, dateFrom: string, dateTo: string) {
    const response = await apiClient.get<ResolvedLessonResponse[]>(
      `/api/v1/schedule/teachers/${teacherId}/range`,
      { params: { dateFrom, dateTo } },
    )
    return response.data
  },
  async getRoomRange(roomId: string, dateFrom: string, dateTo: string) {
    const response = await apiClient.get<ResolvedLessonResponse[]>(`/api/v1/schedule/rooms/${roomId}/range`, {
      params: { dateFrom, dateTo },
    })
    return response.data
  },
  async exportMySchedule(dateFrom?: string, dateTo?: string) {
    const response = await apiClient.get<Blob>('/api/v1/schedule/me/export.ics', {
      params: { dateFrom, dateTo },
      responseType: 'blob',
    })
    return response.data
  },
  async exportGroupSchedule(groupId: string, dateFrom?: string, dateTo?: string) {
    const response = await apiClient.get<Blob>(`/api/v1/schedule/groups/${groupId}/export.ics`, {
      params: { dateFrom, dateTo },
      responseType: 'blob',
    })
    return response.data
  },
  async exportTeacherSchedule(teacherId: string, dateFrom?: string, dateTo?: string) {
    const response = await apiClient.get<Blob>(`/api/v1/schedule/teachers/${teacherId}/export.ics`, {
      params: { dateFrom, dateTo },
      responseType: 'blob',
    })
    return response.data
  },
  async getActiveSemester() {
    const response = await apiClient.get<AcademicSemesterResponse>('/api/v1/schedule/semesters/active')
    return response.data
  },
  async listSemesters() {
    const response = await apiClient.get<AcademicSemesterResponse[]>('/api/v1/schedule/semesters')
    return response.data
  },
  async createSemester(payload: {
    name: string
    startDate: string
    endDate: string
    weekOneStartDate: string
    semesterNumber: number
    active: boolean
  }) {
    const response = await apiClient.post<AcademicSemesterResponse>('/api/v1/schedule/semesters', payload)
    return response.data
  },
  async updateSemester(id: string, payload: {
    name: string
    startDate: string
    endDate: string
    weekOneStartDate: string
    semesterNumber: number
    active: boolean
  }) {
    const response = await apiClient.put<AcademicSemesterResponse>(`/api/v1/schedule/semesters/${id}`, payload)
    return response.data
  },
  async listSlots() {
    const response = await apiClient.get<LessonSlotResponse[]>('/api/v1/schedule/slots')
    return response.data
  },
  async createSlot(payload: { number: number; startTime: string; endTime: string; active: boolean }) {
    const response = await apiClient.post<LessonSlotResponse>('/api/v1/schedule/slots', payload)
    return response.data
  },
  async updateSlot(id: string, payload: { number: number; startTime: string; endTime: string; active: boolean }) {
    const response = await apiClient.put<LessonSlotResponse>(`/api/v1/schedule/slots/${id}`, payload)
    return response.data
  },
  async listRooms() {
    const response = await apiClient.get<RoomResponse[]>('/api/v1/schedule/rooms')
    return response.data
  },
  async createRoom(payload: { code: string; building: string; floor: number; capacity: number; active: boolean }) {
    const response = await apiClient.post<RoomResponse>('/api/v1/schedule/rooms', payload)
    return response.data
  },
  async updateRoom(id: string, payload: { code: string; building: string; floor: number; capacity: number; active: boolean }) {
    const response = await apiClient.put<RoomResponse>(`/api/v1/schedule/rooms/${id}`, payload)
    return response.data
  },
  async getRoomCapabilities(roomId: string, includeInactive = false) {
    const response = await apiClient.get<RoomCapabilityResponse[]>(`/api/v1/schedule/rooms/${roomId}/capabilities`, {
      params: { includeInactive },
    })
    return response.data
  },
  async updateRoomCapabilities(roomId: string, capabilities: Array<{
    lessonType: 'LECTURE' | 'PRACTICAL' | 'LABORATORY'
    priority: number
    active: boolean
  }>) {
    const response = await apiClient.put<RoomCapabilityResponse[]>(`/api/v1/schedule/rooms/${roomId}/capabilities`, { capabilities })
    return response.data
  },
  async createTemplate(payload: Record<string, unknown>) {
    const response = await apiClient.post<ScheduleTemplateResponse>('/api/v1/schedule/templates', payload)
    return response.data
  },
  async updateTemplate(id: string, payload: Record<string, unknown>) {
    const response = await apiClient.put<ScheduleTemplateResponse>(`/api/v1/schedule/templates/${id}`, payload)
    return response.data
  },
  async deleteTemplate(id: string) {
    await apiClient.delete(`/api/v1/schedule/templates/${id}`)
  },
  async listTemplatesBySemester(semesterId: string) {
    const response = await apiClient.get<ScheduleTemplateResponse[]>(`/api/v1/schedule/templates/semester/${semesterId}`)
    return response.data
  },
  async listTemplatesByGroup(groupId: string) {
    const response = await apiClient.get<ScheduleTemplateResponse[]>(`/api/v1/schedule/templates/group/${groupId}`)
    return response.data
  },
  async importTemplates(payload: Record<string, unknown>) {
    const response = await apiClient.post('/api/v1/schedule/templates/import', payload)
    return response.data
  },
  async checkConflicts(payload: Record<string, unknown>) {
    const response = await apiClient.post<ScheduleConflictCheckResponse>('/api/v1/schedule/conflicts/check', payload)
    return response.data
  },
  async createOverride(payload: Record<string, unknown>) {
    const response = await apiClient.post<ScheduleOverrideResponse>('/api/v1/schedule/overrides', payload)
    return response.data
  },
  async updateOverride(id: string, payload: Record<string, unknown>) {
    const response = await apiClient.put<ScheduleOverrideResponse>(`/api/v1/schedule/overrides/${id}`, payload)
    return response.data
  },
  async deleteOverride(id: string) {
    await apiClient.delete(`/api/v1/schedule/overrides/${id}`)
  },
  async getOverridesByDate(date: string) {
    const response = await apiClient.get<ScheduleOverrideResponse[]>(`/api/v1/schedule/overrides/date/${date}`)
    return response.data
  },
}

export const assignmentService = {
  async createAssignment(payload: Record<string, unknown>) {
    const response = await apiClient.post<AssignmentResponse>('/api/v1/assignments', payload)
    return response.data
  },
  async updateAssignment(id: string, payload: Record<string, unknown>) {
    const response = await apiClient.put<AssignmentResponse>(`/api/v1/assignments/${id}`, payload)
    return response.data
  },
  async publishAssignment(id: string) {
    const response = await apiClient.post<AssignmentResponse>(`/api/v1/assignments/${id}/publish`)
    return response.data
  },
  async closeAssignment(id: string) {
    const response = await apiClient.post<AssignmentResponse>(`/api/v1/assignments/${id}/close`)
    return response.data
  },
  async reopenAssignment(id: string) {
    const response = await apiClient.post<AssignmentResponse>(`/api/v1/assignments/${id}/reopen`)
    return response.data
  },
  async archiveAssignment(id: string) {
    const response = await apiClient.post<AssignmentResponse>(`/api/v1/assignments/${id}/archive`)
    return response.data
  },
  async restoreAssignment(id: string) {
    const response = await apiClient.post<AssignmentResponse>(`/api/v1/assignments/${id}/restore`)
    return response.data
  },
  async deleteAssignment(id: string) {
    await apiClient.delete(`/api/v1/assignments/${id}`)
  },
  async moveAssignment(id: string, payload: { topicId: string; orderIndex: number }) {
    const response = await apiClient.patch<AssignmentResponse>(`/api/v1/assignments/${id}/position`, payload)
    return response.data
  },
  async getAssignment(id: string) {
    const response = await apiClient.get<AssignmentResponse>(`/api/v1/assignments/${id}`)
    return response.data
  },
  async getAssignmentAvailability(id: string) {
    const response = await apiClient.get<AssignmentGroupAvailabilityResponse[]>(`/api/v1/assignments/${id}/availability`)
    return response.data
  },
  async listAssignmentAttachments(assignmentId: string) {
    const response = await apiClient.get<AssignmentAttachmentResponse[]>(`/api/v1/assignments/${assignmentId}/attachments`)
    return response.data
  },
  async addAssignmentAttachment(assignmentId: string, payload: { fileId: string; displayName?: string }) {
    const response = await apiClient.post<AssignmentAttachmentResponse>(
      `/api/v1/assignments/${assignmentId}/attachments`,
      payload,
    )
    return response.data
  },
  async removeAssignmentAttachment(assignmentId: string, attachmentId: string) {
    await apiClient.delete(`/api/v1/assignments/${assignmentId}/attachments/${attachmentId}`)
  },
  async previewAssignmentAttachment(assignmentId: string, attachmentId: string) {
    const response = await apiClient.get<Blob>(
      `/api/v1/assignments/${assignmentId}/attachments/${attachmentId}/preview`,
      { responseType: 'blob' },
    )
    return response.data
  },
  async downloadAssignmentAttachment(assignmentId: string, attachmentId: string) {
    const response = await apiClient.get<Blob>(
      `/api/v1/assignments/${assignmentId}/attachments/${attachmentId}/download`,
      { responseType: 'blob' },
    )
    return response.data
  },
  async upsertAssignmentAvailability(id: string, payload: Record<string, unknown>) {
    const response = await apiClient.put<AssignmentGroupAvailabilityResponse>(`/api/v1/assignments/${id}/availability`, payload)
    return response.data
  },
  async bulkUpsertAssignmentAvailability(id: string, items: Record<string, unknown>[]) {
    const response = await apiClient.put<AssignmentGroupAvailabilityResponse[]>(
      `/api/v1/assignments/${id}/availability/bulk`,
      { items },
    )
    return response.data
  },
  async getAssignmentsByTopic(topicId: string, params: PaginationParams = {}) {
    const response = await apiClient.get(`/api/v1/assignments/topic/${topicId}`, { params })
    return normalizePage<AssignmentResponse>(response.data)
  },
  async searchAssignments(params: PaginationParams & { q: string }) {
    const response = await apiClient.get('/api/v1/assignments/search', { params })
    return normalizePage<AssignmentResponse>(response.data)
  },
  async submitAssignment(payload: { assignmentId: string; fileId: string }) {
    const response = await apiClient.post<SubmissionResponse>('/api/v1/submissions', payload)
    return response.data
  },
  async getSubmissionsByAssignment(assignmentId: string, params: PaginationParams = {}) {
    const response = await apiClient.get(`/api/v1/submissions/assignment/${assignmentId}`, { params })
    return normalizePage<SubmissionResponse>(response.data)
  },
  async getMySubmissionsByAssignment(assignmentId: string) {
    const response = await apiClient.get<SubmissionResponse[]>(`/api/v1/submissions/assignment/${assignmentId}/mine`)
    return response.data
  },
  async getSubmission(id: string) {
    const response = await apiClient.get<SubmissionResponse>(`/api/v1/submissions/${id}`)
    return response.data
  },
  async listSubmissionAttachments(submissionId: string) {
    const response = await apiClient.get<SubmissionAttachmentResponse[]>(`/api/v1/submissions/${submissionId}/attachments`)
    return response.data
  },
  async addSubmissionAttachment(submissionId: string, payload: { fileId: string; displayName?: string }) {
    const response = await apiClient.post<SubmissionAttachmentResponse>(
      `/api/v1/submissions/${submissionId}/attachments`,
      payload,
    )
    return response.data
  },
  async removeSubmissionAttachment(submissionId: string, attachmentId: string) {
    await apiClient.delete(`/api/v1/submissions/${submissionId}/attachments/${attachmentId}`)
  },
  async previewSubmissionAttachment(submissionId: string, attachmentId: string) {
    const response = await apiClient.get<Blob>(
      `/api/v1/submissions/${submissionId}/attachments/${attachmentId}/preview`,
      { responseType: 'blob' },
    )
    return response.data
  },
  async downloadSubmissionAttachment(submissionId: string, attachmentId: string) {
    const response = await apiClient.get<Blob>(
      `/api/v1/submissions/${submissionId}/attachments/${attachmentId}/download`,
      { responseType: 'blob' },
    )
    return response.data
  },
  async listSubmissionComments(submissionId: string) {
    const response = await apiClient.get<SubmissionCommentResponse[]>(`/api/v1/submissions/${submissionId}/comments`)
    return response.data
  },
  async createSubmissionComment(submissionId: string, body: string) {
    const response = await apiClient.post<SubmissionCommentResponse>(`/api/v1/submissions/${submissionId}/comments`, { body })
    return response.data
  },
  async updateSubmissionComment(submissionId: string, commentId: string, body: string) {
    const response = await apiClient.put<SubmissionCommentResponse>(`/api/v1/submissions/${submissionId}/comments/${commentId}`, { body })
    return response.data
  },
  async deleteSubmissionComment(submissionId: string, commentId: string) {
    await apiClient.delete(`/api/v1/submissions/${submissionId}/comments/${commentId}`)
  },
  async createGrade(payload: { submissionId: string; score: number; feedback?: string }) {
    const response = await apiClient.post<GradeResponse>('/api/v1/grades', payload)
    return response.data
  },
}

export const testingService = {
  async createTest(payload: Record<string, unknown>) {
    const response = await apiClient.post<TestResponse>('/api/v1/testing/tests', payload)
    return response.data
  },
  async getTest(id: string) {
    const response = await apiClient.get<TestResponse>(`/api/v1/testing/tests/${id}`)
    return response.data
  },
  async getTestPreview(id: string) {
    const response = await apiClient.get<TestPreviewViewResponse>(`/api/v1/testing/tests/${id}/preview`)
    return response.data
  },
  async getStudentTestView(id: string) {
    const response = await apiClient.get<TestStudentViewResponse>(`/api/v1/testing/tests/${id}/student-view`)
    return response.data
  },
  async getTestAvailability(id: string) {
    const response = await apiClient.get<TestGroupAvailabilityResponse[]>(`/api/v1/testing/tests/${id}/availability`)
    return response.data
  },
  async upsertTestAvailability(id: string, payload: Record<string, unknown>) {
    const response = await apiClient.put<TestGroupAvailabilityResponse>(`/api/v1/testing/tests/${id}/availability`, payload)
    return response.data
  },
  async createQuestion(payload: Record<string, unknown>) {
    const response = await apiClient.post<QuestionResponse>('/api/v1/testing/questions', payload)
    return response.data
  },
  async updateQuestion(id: string, payload: Record<string, unknown>) {
    const response = await apiClient.patch<QuestionResponse>(`/api/v1/testing/questions/${id}`, payload)
    return response.data
  },
  async deleteQuestion(id: string) {
    await apiClient.delete(`/api/v1/testing/questions/${id}`)
  },
  async getQuestionsByTest(testId: string) {
    const response = await apiClient.get<QuestionResponse[]>(`/api/v1/testing/questions/test/${testId}`)
    return response.data
  },
  async createAnswer(payload: { questionId: string; text: string; isCorrect: boolean }) {
    const response = await apiClient.post<AnswerResponse>('/api/v1/testing/answers', payload)
    return response.data
  },
  async getAnswersByQuestion(questionId: string) {
    const response = await apiClient.get<AnswerResponse[]>(`/api/v1/testing/answers/question/${questionId}`)
    return response.data
  },
  async publishTest(id: string) {
    const response = await apiClient.post<TestResponse>(`/api/v1/testing/tests/${id}/publish`)
    return response.data
  },
  async closeTest(id: string) {
    const response = await apiClient.post<TestResponse>(`/api/v1/testing/tests/${id}/close`)
    return response.data
  },
  async reopenTest(id: string) {
    const response = await apiClient.post<TestResponse>(`/api/v1/testing/tests/${id}/reopen`)
    return response.data
  },
  async archiveTest(id: string) {
    const response = await apiClient.post<TestResponse>(`/api/v1/testing/tests/${id}/archive`)
    return response.data
  },
  async restoreTest(id: string) {
    const response = await apiClient.post<TestResponse>(`/api/v1/testing/tests/${id}/restore`)
    return response.data
  },
  async deleteTest(id: string) {
    await apiClient.delete(`/api/v1/testing/tests/${id}`)
  },
  async moveTest(id: string, payload: { topicId: string; orderIndex: number }) {
    const response = await apiClient.patch<TestResponse>(`/api/v1/testing/tests/${id}/position`, payload)
    return response.data
  },
  async getTestsByTopic(topicId: string, params: PaginationParams = {}) {
    const response = await apiClient.get(`/api/v1/testing/tests/topic/${topicId}`, { params })
    return normalizePage<TestResponse>(response.data)
  },
  async searchTests(params: PaginationParams & { q: string }) {
    const response = await apiClient.get<SearchPageResponse>('/api/v1/testing/tests/search', { params })
    return response.data
  },
  async startTest(id: string) {
    await apiClient.post(`/api/v1/testing/tests/${id}/start`)
  },
  async finishTest(id: string, payload: { answers: Array<{ questionId: string; value: unknown }> }) {
    const response = await apiClient.post<TestResultResponse>(`/api/v1/testing/tests/${id}/finish`, payload)
    return response.data
  },
  async submitTestResult(payload: { testId: string; score: number }) {
    const response = await apiClient.post<TestResultResponse>('/api/v1/testing/results', payload)
    return response.data
  },
  async getTestResult(id: string) {
    const response = await apiClient.get<TestResultResponse>(`/api/v1/testing/results/${id}`)
    return response.data
  },
  async getTestResultsByTest(testId: string, params: PaginationParams = {}) {
    const response = await apiClient.get(`/api/v1/testing/results/test/${testId}`, { params })
    return normalizePage<TestResultResponse>(response.data)
  },
  async getTestResultReview(id: string) {
    const response = await apiClient.get<TestResultReviewResponse>(`/api/v1/testing/results/${id}/review`)
    return response.data
  },
  async getTestQuestionStatistics(testId: string) {
    const response = await apiClient.get<TestQuestionStatisticsResponse[]>(`/api/v1/testing/results/test/${testId}/question-stats`)
    return response.data
  },
  async updateTestResultQuestionScore(
    resultId: string,
    questionId: string,
    payload: { score: number; comment?: string },
  ) {
    const response = await apiClient.patch<TestResultQuestionResponse>(
      `/api/v1/testing/results/${resultId}/questions/${questionId}/score`,
      payload,
    )
    return response.data
  },
  async overrideTestResultScore(id: string, payload: { score: number; reason?: string }) {
    const response = await apiClient.patch<TestResultResponse>(`/api/v1/testing/results/${id}/score`, payload)
    return response.data
  },
  async approveTestResult(id: string) {
    const response = await apiClient.patch<TestResultResponse>(`/api/v1/testing/results/${id}/approve`)
    return response.data
  },
}

export const fileService = {
  async uploadFile(file: File, fileKind: 'AVATAR' | 'DOCUMENT' | 'ATTACHMENT' | 'GENERIC') {
    const formData = new FormData()
    formData.append('file', file)
    const response = await apiClient.post<StoredFileResponse>('/api/files', formData, {
      headers: { 'Content-Type': 'multipart/form-data' },
      params: { fileKind },
    })
    return response.data
  },
  async getMetadata(fileId: string) {
    const response = await apiClient.get<StoredFileResponse>(`/api/files/${fileId}`)
    return response.data
  },
  async listMine(fileKind?: string) {
    const response = await apiClient.get<StoredFileResponse[]>('/api/files/mine', {
      params: fileKind ? { fileKind } : undefined,
    })
    return response.data
  },
  async deleteFile(fileId: string) {
    await apiClient.delete(`/api/files/${fileId}`)
  },
  async previewFile(fileId: string) {
    const response = await apiClient.get<Blob>(`/api/files/${fileId}/preview`, { responseType: 'blob' })
    return response.data
  },
  async downloadFile(fileId: string) {
    const response = await apiClient.get<Blob>(`/api/files/${fileId}/download`, { responseType: 'blob' })
    return response.data
  },
}

export const notificationService = {
  async getMyNotifications(params: PaginationParams & { status?: string } = {}) {
    const response = await apiClient.get<NotificationPageResponse>('/api/notifications/me', { params })
    return response.data
  },
  async getUnreadCount() {
    const response = await apiClient.get<UnreadCountResponse>('/api/notifications/me/unread-count')
    return response.data
  },
  async markRead(notificationId: string) {
    const response = await apiClient.patch<NotificationResponse>(`/api/notifications/${notificationId}/read`)
    return response.data
  },
  async markAllRead() {
    const response = await apiClient.patch<UnreadCountResponse>('/api/notifications/me/read-all')
    return response.data
  },
  async deleteNotification(notificationId: string) {
    await apiClient.delete(`/api/notifications/${notificationId}`)
  },
  async deleteAllNotifications() {
    const response = await apiClient.delete<UnreadCountResponse>('/api/notifications/me')
    return response.data
  },
  async getTelegramStatus() {
    const response = await apiClient.get<TelegramLinkStatusResponse>('/api/notifications/telegram/status')
    return response.data
  },
  async createTelegramConnectToken() {
    const response = await apiClient.post<TelegramConnectTokenResponse>('/api/notifications/telegram/connect-token')
    return response.data
  },
  async disconnectTelegram() {
    const response = await apiClient.post<TelegramLinkStatusResponse>('/api/notifications/telegram/disconnect')
    return response.data
  },
  async sendTelegramTest() {
    await apiClient.post('/api/notifications/telegram/test')
  },
  async updateTelegramPreferences(payload: {
    telegramEnabled?: boolean
    notifyAssignments?: boolean
    notifyTests?: boolean
    notifyGrades?: boolean
    notifySchedule?: boolean
    notifyMaterials?: boolean
    notifySystem?: boolean
  }) {
    const response = await apiClient.patch<TelegramLinkStatusResponse>('/api/notifications/telegram/preferences', payload)
    return response.data
  },
}

export const analyticsService = {
  async getStudent(userId: string) {
    const response = await apiClient.get<StudentAnalyticsResponse>(`/api/v1/analytics/students/${userId}`)
    return response.data
  },
  async getStudentSubjects(userId: string) {
    const response = await apiClient.get<SubjectAnalyticsResponse[]>(`/api/v1/analytics/students/${userId}/subjects`)
    return response.data
  },
  async getStudentRisk(userId: string) {
    const response = await apiClient.get<StudentRiskResponse>(`/api/v1/analytics/students/${userId}/risk`)
    return response.data
  },
  async getTeacher(teacherId: string) {
    const response = await apiClient.get<TeacherAnalyticsResponse>(`/api/v1/analytics/teachers/${teacherId}`)
    return response.data
  },
  async getTeacherGroupsAtRisk(teacherId: string) {
    const response = await apiClient.get<GroupOverviewResponse[]>(`/api/v1/analytics/teachers/${teacherId}/groups-at-risk`)
    return response.data
  },
  async getDashboardOverview() {
    const response = await apiClient.get<DashboardOverviewResponse>('/api/v1/analytics/dashboard/overview')
    return response.data
  },
  async getGroupOverview(groupId: string) {
    const response = await apiClient.get<GroupOverviewResponse>(`/api/v1/analytics/groups/${groupId}/overview`)
    return response.data
  },
  async getGroupStudents(groupId: string, params: PaginationParams = {}) {
    const response = await apiClient.get(`/api/v1/analytics/groups/${groupId}/students`, { params })
    return normalizePage<StudentGroupProgressResponse>(response.data)
  },
  async getSubjectAnalytics(subjectId: string, params: PaginationParams = {}) {
    const response = await apiClient.get(`/api/v1/analytics/subjects/${subjectId}`, { params })
    return normalizePage<SubjectAnalyticsResponse>(response.data)
  },
  async getSubjectGroupAnalytics(subjectId: string, groupId: string) {
    const response = await apiClient.get<SubjectAnalyticsResponse>(
      `/api/v1/analytics/subjects/${subjectId}/groups/${groupId}`,
    )
    return response.data
  },
}

export const dashboardService = {
  async getStudentDashboard() {
    const response = await apiClient.get<StudentDashboardResponse>('/api/v1/dashboard/student/me')
    return response.data
  },
  async getTeacherDashboard() {
    const response = await apiClient.get<TeacherDashboardResponse>('/api/v1/dashboard/teacher/me')
    return response.data
  },
  async getAdminDashboard() {
    const response = await apiClient.get<AdminDashboardOverviewResponse>('/api/v1/dashboard/admin/overview')
    return response.data
  },
}

export const searchService = {
  async search(params: PaginationParams & { q: string }) {
    const response = await apiClient.get<SearchPageResponse>('/api/v1/search', { params })
    return response.data
  },
}

export const auditService = {
  async list(params: PaginationParams & {
    actorId?: string
    entityType?: string
    entityId?: string
    dateFrom?: string
    dateTo?: string
  } = {}) {
    const response = await apiClient.get('/api/v1/audit', { params })
    return normalizePage<AuditEventResponse>(response.data)
  },
  async getById(auditEventId: string) {
    const response = await apiClient.get<AuditEventResponse>(`/api/v1/audit/${auditEventId}`)
    return response.data
  },
}

export const adminUserService = {
  async getStats() {
    const response = await apiClient.get<AdminUserStatsResponse>('/api/admin/users/statistics')
    return response.data
  },
  async list(params: PaginationParams & { search?: string; role?: string; banned?: boolean } = {}) {
    const response = await apiClient.get<AdminUserPageResponse>('/api/admin/users', { params })
    return response.data
  },
  async getById(userId: string) {
    const response = await apiClient.get<AdminUserResponse>(`/api/admin/users/${userId}`)
    return response.data
  },
  async updateRoles(userId: string, roles: string[]) {
    const response = await apiClient.patch<AdminUserResponse>(`/api/admin/users/${userId}/roles`, { roles })
    return response.data
  },
  async ban(userId: string, reason: string, expiresAt?: string) {
    await apiClient.post(`/api/admin/users/${userId}/ban`, { reason, expiresAt })
  },
  async unban(userId: string) {
    await apiClient.post(`/api/admin/users/${userId}/unban`)
  },
  async revokeSessions(userId: string) {
    await apiClient.post(`/api/admin/users/${userId}/revoke-sessions`)
  },
}

export const specialtyService = {
  list: educationService.listSpecialties,
  getById: educationService.getSpecialty,
  create: educationService.createSpecialty,
  update: educationService.updateSpecialty,
  archive: educationService.archiveSpecialty,
  restore: educationService.restoreSpecialty,
}

export const streamService = {
  list: educationService.listStreams,
  getById: educationService.getStream,
  listGroups: educationService.getStreamGroups,
  create: educationService.createStream,
  update: educationService.updateStream,
  archive: educationService.archiveStream,
  restore: educationService.restoreStream,
}

export const curriculumPlanService = {
  list: educationService.listCurriculumPlans,
  getById: educationService.getCurriculumPlan,
  create: educationService.createCurriculumPlan,
  update: educationService.updateCurriculumPlan,
  archive: educationService.archiveCurriculumPlan,
  restore: educationService.restoreCurriculumPlan,
}

export const groupCurriculumOverrideService = {
  list: educationService.listGroupCurriculumOverrides,
  create: educationService.createGroupCurriculumOverride,
  update: educationService.updateGroupCurriculumOverride,
  remove: educationService.deleteGroupCurriculumOverride,
}

export const resolvedSubjectsService = {
  listByGroup: educationService.getResolvedGroupSubjects,
}

export const roomCapabilityService = {
  listByRoom: scheduleService.getRoomCapabilities,
  updateByRoom: scheduleService.updateRoomCapabilities,
}

function normalizePage<T>(data: unknown) {
  const value = data as { items?: T[]; content?: T[] }
  if (value.items) {
    return data as {
      items: T[]
      page: number
      size: number
      totalElements: number
      totalPages: number
      first: boolean
      last: boolean
    }
  }

  return {
    ...(data as Record<string, unknown>),
    items: value.content ?? [],
  } as {
    items: T[]
    page: number
    size: number
    totalElements: number
    totalPages: number
    first: boolean
    last: boolean
  }
}
