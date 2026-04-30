export type Role = 'OWNER' | 'ADMIN' | 'TEACHER' | 'STUDENT' | 'USER'
export type LocaleValue = 'en' | 'uk' | 'pl'
export type GroupMemberRole = 'STUDENT' | 'STAROSTA'
export type SubgroupValue = 'ALL' | 'FIRST' | 'SECOND'
export type QuestionType =
  | 'SINGLE_CHOICE'
  | 'MULTIPLE_CHOICE'
  | 'TRUE_FALSE'
  | 'SHORT_ANSWER'
  | 'LONG_TEXT'
  | 'NUMERIC'
  | 'MATCHING'
  | 'ORDERING'
  | 'FILL_IN_THE_BLANK'
  | 'FILE_ANSWER'
  | 'MANUAL_GRADING'

export interface ApiErrorResponse {
  timestamp?: string
  status: number
  error?: string
  code?: string
  errorCode?: string
  message?: string
  path?: string
  requestId?: string
  details?: unknown
}

export interface NormalizedApiError {
  status: number
  code: string
  message?: string
  requestId?: string
  details?: unknown
  fieldErrors?: Record<string, string[]>
}

export interface AuthUser {
  id: string
  username: string
  email: string
  roles: Role[]
  forcePasswordChange: boolean
}

export interface MfaChallengeResponse {
  challengeToken: string
  status: string
  availableMethods: string[]
  selectedMethod: string | null
  expiresAt: string | null
  codeExpiresAt: string | null
  deliveryHint: string | null
}

export interface AuthResponse {
  status: 'AUTHENTICATED' | 'MFA_REQUIRED'
  accessToken: string | null
  refreshToken: string | null
  user: AuthUser | null
  mfaChallenge: MfaChallengeResponse | null
}

export interface AuthSession {
  accessToken: string
  refreshToken: string | null
  user: AuthUser
}

export interface UserProfileResponse {
  userId: string
  username: string
  email: string
  displayName: string | null
  avatarFileKey: string | null
  locale: string | null
  timezone: string | null
  createdAt: string
  updatedAt: string
}

export interface AcceptedActionResponse {
  status: string
  message: string
}

export interface PageResponse<T> {
  items?: T[]
  content?: T[]
  page: number
  size: number
  totalElements: number
  totalPages: number
  first: boolean
  last: boolean
}

export interface GroupResponse {
  id: string
  name: string
  createdAt: string
  updatedAt: string
}

export interface GroupMembershipResponse {
  groupId: string
  role: GroupMemberRole
  subgroup: SubgroupValue
  createdAt: string
  updatedAt: string
}

export interface GroupStudentMembershipResponse {
  userId: string
  role: GroupMemberRole
  subgroup: SubgroupValue
  groupMembershipCount: number
  createdAt: string
  updatedAt: string
}

export interface SubjectResponse {
  id: string
  name: string
  groupId: string | null
  groupIds: string[]
  teacherIds: string[]
  description: string | null
  createdAt: string
  updatedAt: string
}

export interface TopicResponse {
  id: string
  subjectId: string
  title: string
  orderIndex: number
  createdAt: string
  updatedAt: string
}

export interface AcademicSemesterResponse {
  id: string
  name: string
  startDate: string
  endDate: string
  weekOneStartDate: string
  active: boolean
  published: boolean
  createdAt: string
  updatedAt: string
}

export interface LessonSlotResponse {
  id: string
  number: number
  startTime: string
  endTime: string
  active: boolean
  createdAt: string
  updatedAt: string
}

export interface RoomResponse {
  id: string
  code: string
  building: string
  floor: number
  capacity: number
  active: boolean
  createdAt: string
  updatedAt: string
}

export interface ScheduleTemplateResponse {
  id: string
  semesterId: string
  groupId: string
  subjectId: string
  teacherId: string
  dayOfWeek: string
  slotId: string
  weekType: 'ALL' | 'ODD' | 'EVEN'
  subgroup: SubgroupValue
  lessonType: 'LECTURE' | 'PRACTICAL' | 'LABORATORY'
  lessonTypeDisplayName: string
  lessonFormat: 'ONLINE' | 'OFFLINE'
  roomId: string | null
  onlineMeetingUrl: string | null
  notes: string | null
  status: 'DRAFT' | 'ACTIVE' | 'ARCHIVED'
  active: boolean
  createdAt: string
  updatedAt: string
}

export interface ScheduleOverrideResponse {
  id: string
  semesterId: string | null
  templateId: string | null
  overrideType: 'CANCEL' | 'REPLACE' | 'EXTRA'
  date: string
  groupId: string | null
  subjectId: string | null
  teacherId: string | null
  slotId: string | null
  subgroup: SubgroupValue | null
  lessonType: 'LECTURE' | 'PRACTICAL' | 'LABORATORY' | null
  lessonTypeDisplayName: string | null
  lessonFormat: 'ONLINE' | 'OFFLINE' | null
  roomId: string | null
  onlineMeetingUrl: string | null
  notes: string | null
  createdByUserId: string
  createdAt: string
  updatedAt: string
}

export interface ResolvedLessonResponse {
  date: string
  semesterId: string
  templateId: string | null
  groupId: string
  subjectId: string
  teacherId: string
  slotId: string
  subgroup: SubgroupValue
  weekNumber: number
  weekType: 'ALL' | 'ODD' | 'EVEN'
  lessonType: 'LECTURE' | 'PRACTICAL' | 'LABORATORY'
  lessonTypeDisplayName: string
  lessonFormat: 'ONLINE' | 'OFFLINE'
  roomId: string | null
  onlineMeetingUrl: string | null
  notes: string | null
  sourceType: string | null
  overrideType: string | null
}

export interface ScheduleConflictItemResponse {
  type?: string
  message?: string
  conflictingEntityId?: string | null
  conflictingEntityType?: string | null
  date?: string | null
  dayOfWeek?: string | null
  slotId?: string | null
  groupId?: string | null
  subgroup?: SubgroupValue | null
  teacherId?: string | null
  roomId?: string | null
}

export interface ScheduleConflictCheckResponse {
  hasConflicts: boolean
  conflicts: ScheduleConflictItemResponse[]
}

export interface AssignmentResponse {
  id: string
  topicId: string
  title: string
  description: string | null
  deadline: string
  orderIndex: number
  status: 'DRAFT' | 'PUBLISHED' | 'ARCHIVED'
  allowLateSubmissions: boolean
  maxSubmissions: number
  allowResubmit: boolean
  acceptedFileTypes: string[]
  maxFileSizeMb: number | null
  createdAt: string
  updatedAt: string
}

export interface AssignmentGroupAvailabilityResponse {
  id: string
  assignmentId: string
  groupId: string
  visible: boolean
  availableFrom: string | null
  deadline: string
  allowLateSubmissions: boolean
  maxSubmissions: number
  allowResubmit: boolean
  createdAt: string
  updatedAt: string
}

export interface SubmissionResponse {
  id: string
  assignmentId: string
  userId: string
  fileId: string
  submittedAt: string
  updatedAt: string
}

export interface SubmissionCommentResponse {
  id: string
  submissionId: string
  authorUserId: string
  body: string
  deleted: boolean
  createdAt: string
  updatedAt: string
}

export interface GradeResponse {
  id: string
  submissionId: string
  score: number
  feedback: string | null
  createdAt: string
  updatedAt: string
}

export interface TestResponse {
  id: string
  topicId: string
  title: string
  orderIndex: number
  status: 'DRAFT' | 'PUBLISHED' | 'CLOSED' | 'ARCHIVED'
  maxAttempts: number
  maxPoints: number
  timeLimitMinutes: number | null
  availableFrom: string | null
  availableUntil: string | null
  showCorrectAnswersAfterSubmit: boolean
  shuffleQuestions: boolean
  shuffleAnswers: boolean
  createdAt: string
  updatedAt: string
}

export interface TestGroupAvailabilityResponse {
  id: string
  testId: string
  groupId: string
  visible: boolean
  availableFrom: string | null
  availableUntil: string | null
  deadline: string | null
  maxAttempts: number
  createdAt: string
  updatedAt: string
}

export interface QuestionResponse {
  id: string
  testId: string
  text: string
  type: QuestionType
  description: string | null
  points: number
  orderIndex: number
  required: boolean
  feedback: string | null
  createdAt: string
  updatedAt: string
}

export interface AnswerResponse {
  id: string
  questionId: string
  text: string
  isCorrect: boolean
  createdAt: string
  updatedAt: string
}

export interface TestResultResponse {
  id: string
  testId: string
  userId: string
  attemptId: string
  score: number
  autoScore: number
  manualOverrideScore: number | null
  manualOverrideReason: string | null
  reviewedByUserId: string | null
  reviewedAt: string | null
  createdAt: string
  updatedAt: string
}

export interface StoredFileResponse {
  id: string
  fileId: string
  ownerId: string
  originalFileName: string
  contentType: string | null
  sizeBytes: number
  fileKind: 'AVATAR' | 'DOCUMENT' | 'ATTACHMENT' | 'GENERIC'
  access: 'PRIVATE' | 'PUBLIC'
  visibility: 'PRIVATE' | 'PUBLIC'
  status: 'PENDING_SCAN' | 'READY' | 'UPLOADED' | 'ACTIVE' | 'ORPHANED' | 'REJECTED' | 'DELETED'
  previewAvailable: boolean
  createdAt: string
  updatedAt: string
  lastAccessedAt: string | null
  scanCompletedAt: string | null
  scanStatusMessage: string | null
}

export interface NotificationResponse {
  id: string
  userId: string
  type: string
  category: string
  title: string
  body: string
  payloadJson: string | null
  read: boolean
  status: 'UNREAD' | 'READ' | 'ARCHIVED'
  sourceEventId: string | null
  sourceEventType: string | null
  createdAt: string
  updatedAt: string
  readAt: string | null
}

export interface NotificationPageResponse extends PageResponse<NotificationResponse> {
  items: NotificationResponse[]
}

export interface UnreadCountResponse {
  unreadCount: number
}

export interface DashboardDeadlineItemResponse {
  type: string
  id: string
  topicId: string | null
  subjectId: string | null
  title: string
  deadline: string
}

export interface DashboardGradeItemResponse {
  gradeId: string
  submissionId: string
  assignmentId: string
  topicId: string
  subjectId: string
  assignmentTitle: string
  score: number
  feedback: string | null
  gradedAt: string
}

export interface DashboardProgressSummaryResponse {
  averageScore: number | null
  activityScore: number
  disciplineScore: number
  assignmentsSubmittedCount: number
  testsCompletedCount: number
  missedDeadlinesCount: number
}

export interface DashboardAssignmentItemResponse {
  assignmentId: string
  topicId: string
  subjectId: string
  title: string
  deadline: string
  status: string
  submitted: boolean
}

export interface DashboardTestItemResponse {
  testId: string
  topicId: string
  subjectId: string
  title: string
  status: string
  availableFrom: string | null
  availableUntil: string | null
  timeLimitMinutes: number | null
  attemptsUsed: number
  maxAttempts: number
}

export interface StudentDashboardResponse {
  todaySchedule: ResolvedLessonResponse[]
  upcomingDeadlines: DashboardDeadlineItemResponse[]
  recentGrades: DashboardGradeItemResponse[]
  unreadNotificationsCount: number
  progressSummary: DashboardProgressSummaryResponse
  riskLevel: string | null
  pendingAssignments: DashboardAssignmentItemResponse[]
  availableTests: DashboardTestItemResponse[]
}

export interface DashboardSubmissionItemResponse {
  submissionId: string
  assignmentId: string
  studentId: string
  submittedAt: string
}

export interface DashboardGroupRiskResponse {
  groupId: string
  riskLevel: string
  affectedStudentsCount: number
}

export interface DashboardScheduleChangeResponse {
  notificationId: string
  type: string
  title: string
  body: string
  createdAt: string
}

export interface TeacherDashboardResponse {
  todayLessons: ResolvedLessonResponse[]
  pendingSubmissionsToReview: DashboardSubmissionItemResponse[]
  recentSubmissions: DashboardSubmissionItemResponse[]
  groupsAtRisk: DashboardGroupRiskResponse[]
  activeAssignments: DashboardAssignmentItemResponse[]
  activeTests: DashboardTestItemResponse[]
  upcomingScheduleChanges: DashboardScheduleChangeResponse[]
}

export interface DashboardAuditEventResponse {
  id: string
  actorUserId: string | null
  action: string
  entityType: string
  entityId: string | null
  occurredAt: string
  sourceService: string
}

export interface AdminDashboardAnalyticsSummaryResponse {
  totalStudentsTracked: number
  lowRiskStudentsCount: number
  mediumRiskStudentsCount: number
  highRiskStudentsCount: number
  averagePlatformScore: number
  averageDisciplineScore: number
  averageActivityScore: number
  totalMissedDeadlines: number
  totalLateSubmissions: number
}

export interface AdminDashboardOverviewResponse {
  totalStudents: number
  totalTeachers: number
  totalGroups: number
  activeSubjects: number
  highRiskStudentsCount: number
  activeDeadlinesCount: number
  recentAuditEvents: DashboardAuditEventResponse[]
  analyticsSummary: AdminDashboardAnalyticsSummaryResponse
}

export interface StudentAnalyticsResponse {
  userId: string
  averageScore: number | null
  assignmentsCreatedCount: number
  assignmentsSubmittedCount: number
  assignmentsLateCount: number
  testsCompletedCount: number
  missedDeadlinesCount: number
  lectureOpenCount: number
  topicOpenCount: number
  lastActivityAt: string | null
  activityScore: number
  disciplineScore: number
  riskLevel: string
  performanceTrend: string
  updatedAt: string
}

export interface StudentRiskResponse {
  userId: string
  riskLevel: string
  performanceTrend: string
  averageScore: number | null
  activityScore: number
  disciplineScore: number
  missedDeadlinesCount: number
  lastActivityAt: string | null
  updatedAt: string
}

export interface TeacherAnalyticsResponse {
  teacherId: string
  publishedAssignmentsCount: number
  publishedTestsCount: number
  assignedGradesCount: number
  averageReviewTimeHours: number | null
  averageStudentScore: number | null
  failingRate: number
  updatedAt: string
}

export interface GroupOverviewResponse {
  groupId: string
  totalStudentsTracked: number
  lowRiskStudentsCount: number
  mediumRiskStudentsCount: number
  highRiskStudentsCount: number
  averageScore: number | null
  averageActivityScore: number
  averageDisciplineScore: number
  totalMissedDeadlines: number
  totalLateSubmissions: number
  updatedAt: string
}

export interface StudentGroupProgressResponse {
  groupId: string
  averageScore: number | null
  assignmentsCreatedCount: number
  assignmentsSubmittedCount: number
  assignmentsLateCount: number
  testsCompletedCount: number
  missedDeadlinesCount: number
  lectureOpenCount: number
  topicOpenCount: number
  lastActivityAt: string | null
  activityScore: number
  disciplineScore: number
  riskLevel: string
  performanceTrend: string
  updatedAt: string
}

export interface SubjectAnalyticsResponse {
  subjectId: string
  groupId: string
  averageScore: number | null
  completionRate: number
  lateSubmissionRate: number
  missedDeadlineRate: number
  activeStudentsCount: number
  atRiskStudentsCount: number
  lectureOpenCount: number
  testCompletionCount: number
  updatedAt: string
}

export interface DashboardOverviewResponse {
  totalStudentsTracked: number
  lowRiskStudentsCount: number
  mediumRiskStudentsCount: number
  highRiskStudentsCount: number
  averagePlatformScore: number
  averageDisciplineScore: number
  averageActivityScore: number
  totalMissedDeadlines: number
  totalLateSubmissions: number
}

export interface AuditEventResponse {
  id: string
  actorUserId: string | null
  action: string
  entityType: string
  entityId: string | null
  oldValueJson: string | null
  newValueJson: string | null
  occurredAt: string
  requestId: string | null
  sourceService: string
}

export interface SearchItemResponse {
  type: string
  id: string
  title: string
  subtitle: string | null
  sourceService: string
  targetMetadata: Record<string, unknown>
}

export interface SearchPageResponse extends PageResponse<SearchItemResponse> {
  items: SearchItemResponse[]
}

export interface AdminUserResponse {
  id: string
  username: string
  email: string
  displayName?: string | null
  roles: Role[]
  status?: string
  banned?: boolean
  disabled?: boolean
  createdAt: string
  updatedAt: string
}

export interface UserSummaryResponse {
  id: string
  username: string
  email: string
  roles: Role[]
}

export interface AdminUserPageResponse {
  content: AdminUserResponse[]
  page: number
  size: number
  totalElements: number
  totalPages: number
  first: boolean
  last: boolean
}

export interface AdminUserStatsResponse {
  totalUsers: number
  totalEnabledUsers: number
  totalBannedUsers: number
  totalOwners: number
  totalAdmins: number
  totalTeachers: number
  totalStudents: number
  totalRegularUsers: number
}
