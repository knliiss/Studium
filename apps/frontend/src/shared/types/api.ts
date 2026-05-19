export type Role = 'OWNER' | 'ADMIN' | 'TEACHER' | 'STUDENT' | 'USER'
export type LocaleValue = 'en' | 'uk' | 'pl'
export type GroupMemberRole = 'STUDENT' | 'STAROSTA'
export type SubgroupValue = 'ALL' | 'FIRST' | 'SECOND'
export type GroupSubgroupMode = 'NONE' | 'TWO_SUBGROUPS'
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
  specialtyId: string | null
  studyYear: number | null
  streamId: string | null
  subgroupMode: GroupSubgroupMode
  createdAt: string
  updatedAt: string
}

export interface SpecialtyResponse {
  id: string
  code: string
  name: string
  description: string | null
  active: boolean
  createdAt: string
  updatedAt: string
}

export interface StreamResponse {
  id: string
  name: string
  specialtyId: string
  studyYear: number
  active: boolean
  createdAt: string
  updatedAt: string
}

export interface CurriculumPlanResponse {
  id: string
  specialtyId: string
  studyYear: number
  semesterNumber: number
  subjectId: string
  lectureCount: number
  practiceCount: number
  labCount: number
  supportsStreamLecture: boolean
  requiresSubgroupsForLabs: boolean
  active: boolean
  createdAt: string
  updatedAt: string
}

export interface GroupCurriculumOverrideResponse {
  id: string
  groupId: string
  subjectId: string
  enabled: boolean
  lectureCountOverride: number | null
  practiceCountOverride: number | null
  labCountOverride: number | null
  notes: string | null
  createdAt: string
  updatedAt: string
}

export type ResolvedGroupSubjectSource = 'DIRECT_BINDING' | 'CURRICULUM_PLAN' | 'GROUP_OVERRIDE'

export interface ResolvedGroupSubjectResponse {
  subjectId: string
  subjectName: string
  source: ResolvedGroupSubjectSource
  lectureCount: number
  practiceCount: number
  labCount: number
  teacherIds: string[]
  supportsStreamLecture: boolean
  requiresSubgroupsForLabs: boolean
  disabledByOverride: boolean
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

export interface LectureResponse {
  id: string
  subjectId: string
  topicId: string
  title: string
  content: string | null
  status: 'DRAFT' | 'PUBLISHED' | 'CLOSED' | 'ARCHIVED'
  orderIndex: number
  createdByUserId: string | null
  createdAt: string
  updatedAt: string
}

export interface LectureAttachmentResponse {
  id: string
  lectureId: string
  fileId: string
  displayName: string | null
  originalFileName: string
  contentType: string | null
  sizeBytes: number
  previewAvailable: boolean
  uploadedByUserId: string | null
  createdAt: string
}

export type TopicMaterialType = 'FILE' | 'LINK' | 'TEXT'

export interface TopicMaterialResponse {
  id: string
  topicId: string
  title: string
  description: string | null
  type: TopicMaterialType
  url: string | null
  fileId: string | null
  originalFileName: string | null
  contentType: string | null
  sizeBytes: number | null
  visible: boolean
  archived: boolean
  orderIndex: number
  createdByUserId: string | null
  createdAt: string
  updatedAt: string
}

export interface AcademicSemesterResponse {
  id: string
  name: string
  startDate: string
  endDate: string
  weekOneStartDate: string
  semesterNumber: number | null
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

export interface RoomCapabilityResponse {
  id: string
  roomId: string
  lessonType: 'LECTURE' | 'PRACTICAL' | 'LABORATORY'
  priority: number
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
  status: 'DRAFT' | 'PUBLISHED' | 'CLOSED' | 'ARCHIVED'
  allowLateSubmissions: boolean
  maxSubmissions: number
  allowResubmit: boolean
  acceptedFileTypes: string[]
  maxFileSizeMb: number | null
  maxPoints: number
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

export interface AssignmentAttachmentResponse {
  id: string
  assignmentId: string
  fileId: string
  displayName: string | null
  originalFileName: string
  contentType: string | null
  sizeBytes: number
  previewAvailable: boolean
  uploadedByUserId: string
  createdAt: string
}

export interface SubmissionFileResponse {
  id: string
  originalFileName: string
  contentType: string | null
  sizeBytes: number
  status: string
}

export interface SubmissionAttachmentResponse {
  id: string
  submissionId: string
  fileId: string
  displayName: string | null
  originalFileName: string
  contentType: string | null
  sizeBytes: number
  previewAvailable: boolean
  uploadedByUserId: string
  createdAt: string
}

export interface SubmissionResponse {
  id: string
  assignmentId: string
  userId: string
  fileId: string
  file: SubmissionFileResponse | null
  score: number | null
  feedback: string | null
  gradedAt: string | null
  reviewed: boolean
  status: string
  attemptNumber: number
  maxAttempts: number
  remainingAttempts: number
  submittedLate: boolean
  reviewComment: string | null
  reviewedAt: string | null
  reviewedByUserId: string | null
  returnedForRevisionAt: string | null
  revisionDeadline: string | null
  submittedAt: string
  updatedAt: string
}

export interface SubmissionAttemptDetailResponse {
  id: string
  assignmentId: string
  userId: string
  fileId: string
  file: SubmissionFileResponse | null
  score: number | null
  feedback: string | null
  gradedAt: string | null
  reviewed: boolean
  status: string
  attemptNumber: number
  maxAttempts: number
  remainingAttempts: number
  submittedLate: boolean
  reviewComment: string | null
  reviewedAt: string | null
  reviewedByUserId: string | null
  returnedForRevisionAt: string | null
  revisionDeadline: string | null
  submittedAt: string
  updatedAt: string
  attachments: SubmissionAttachmentResponse[]
}

export interface SubmissionDetailResponse {
  assignmentId: string
  studentId: string
  currentStatus: string
  currentAttemptNumber: number
  maxAttempts: number
  remainingAttempts: number
  revisionDeadline: string | null
  latestTeacherComment: string | null
  finalScore: number | null
  maxScore: number
  currentAttempt: SubmissionAttemptDetailResponse | null
  attempts: SubmissionAttemptDetailResponse[]
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
  configurationJson: string | null
  answers: AnswerResponse[]
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

export interface TestQuestionAnswerResponse {
  id: string
  questionId: string
  text: string
  isCorrect: boolean
  createdAt: string
  updatedAt: string
}

export interface TestPreviewQuestionViewResponse {
  id: string
  testId: string
  text: string
  type: QuestionType
  description: string | null
  points: number
  orderIndex: number
  required: boolean
  feedback: string | null
  configurationJson: string | null
  answers: TestQuestionAnswerResponse[]
  createdAt: string
  updatedAt: string
}

export interface TestStudentAnswerOptionResponse {
  id: string
  questionId: string
  text: string
  createdAt: string
  updatedAt: string
}

export interface TestStudentQuestionViewResponse {
  id: string
  testId: string
  text: string
  type: QuestionType
  description: string | null
  points: number
  orderIndex: number
  required: boolean
  answers: TestStudentAnswerOptionResponse[]
  presentationJson: string
  createdAt: string
  updatedAt: string
}

export interface TestStudentViewResponse {
  test: TestResponse
  questions: TestStudentQuestionViewResponse[]
  preview: boolean
  totalPoints: number
  timeLimitMinutes: number | null
  generatedAt: string
}

export interface TestPreviewViewResponse {
  test: TestResponse
  questions: TestPreviewQuestionViewResponse[]
  preview: boolean
  totalPoints: number
  timeLimitMinutes: number | null
  generatedAt: string
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

export interface TestResultQuestionResponse {
  id: string
  resultId: string
  questionId: string
  questionType: QuestionType
  questionText: string
  questionOrderIndex: number
  maxPoints: number
  submittedValueJson: string | null
  correctValueJson: string | null
  autoScore: number
  score: number
  reviewComment: string | null
  reviewedByUserId: string | null
  reviewedAt: string | null
  timeSpentSeconds: number | null
  createdAt: string
  updatedAt: string
}

export interface TestResultReviewResponse {
  resultId: string
  testId: string
  attemptId: string | null
  userId: string
  testTitle: string
  testStatus: string
  maxPoints: number
  score: number
  autoScore: number
  attemptStartedAt: string | null
  attemptCompletedAt: string | null
  totalTimeSpentSeconds: number | null
  submittedAt: string
  reviewedByUserId: string | null
  reviewedAt: string | null
  questions: TestResultQuestionResponse[]
}

export interface TestQuestionStatisticsResponse {
  questionId: string
  questionType: QuestionType
  questionText: string
  orderIndex: number
  maxPoints: number
  attemptsCount: number
  averageScore: number
  zeroScoreCount: number
  fullScoreCount: number
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

export interface TelegramPreferencesResponse {
  telegramEnabled: boolean
  notifyAssignments: boolean
  notifyTests: boolean
  notifyGrades: boolean
  notifySchedule: boolean
  notifyMaterials: boolean
  notifySystem: boolean
}

export interface TelegramLinkStatusResponse {
  telegramEnabledByConfig: boolean
  telegramAvailable: boolean
  connected: boolean
  pending: boolean
  botUsername: string | null
  deepLink: string | null
  tokenExpiresAt: string | null
  telegramUsername: string | null
  telegramUserId: number | null
  chatId: number | null
  connectedAt: string | null
  disconnectedAt: string | null
  preferences: TelegramPreferencesResponse
}

export interface TelegramConnectTokenResponse {
  token: string
  deepLink: string | null
  expiresAt: string
  telegramAvailable: boolean
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
  status: string
  attemptNumber: number
  maxAttempts: number
  score: number | null
  revisionDeadline: string | null
  reviewedAt: string | null
  submittedLate: boolean
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
  email: string | null
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

export type GradebookActivityType = 'ALL' | 'ASSIGNMENTS' | 'TESTS'
export type GradebookActivityStatus = 'GRADED' | 'PENDING_REVIEW' | 'MISSING' | 'NOT_SUBMITTED'

export interface GradebookFilterOptionResponse {
  id: string
  label: string
}

export interface GradebookActivityRowResponse {
  activityId: string
  activityType: 'ASSIGNMENT' | 'TEST'
  subjectId: string
  topicId: string
  title: string
  score: number | null
  maxPoints: number
  percentage: number | null
  status: GradebookActivityStatus
  submittedAt: string | null
  reviewedAt: string | null
  dueAt: string | null
  late: boolean
  submissionId: string | null
  resultId: string | null
}

export interface GradebookStudentSubjectResponse {
  subjectId: string
  subjectName: string
  groupName: string | null
  teacherNames: string[]
  averagePercent: number | null
  scoredPoints: number | null
  maxPoints: number | null
  assignmentsCompleted: number
  assignmentsTotal: number
  testsCompleted: number
  testsTotal: number
  pendingCount: number
  missingCount: number
  activities: GradebookActivityRowResponse[]
}

export interface StudentGradebookResponse {
  averagePercent: number | null
  gradedActivities: number
  pendingActivities: number
  missingActivities: number
  subjects: GradebookStudentSubjectResponse[]
}

export interface GradebookMatrixActivityResponse {
  activityId: string
  activityType: 'ASSIGNMENT' | 'TEST'
  title: string
  maxPoints: number
  dueAt: string | null
}

export interface GradebookMatrixCellResponse {
  activityId: string
  score: number | null
  maxPoints: number
  percentage: number | null
  status: GradebookActivityStatus
  late: boolean
  submittedAt: string | null
  reviewedAt: string | null
  submissionId: string | null
  resultId: string | null
}

export interface GradebookMatrixStudentResponse {
  studentId: string
  studentName: string
  averagePercent: number | null
  missingCount: number
  cells: GradebookMatrixCellResponse[]
}

export interface GradebookMatrixSummaryResponse {
  studentCount: number
  groupAveragePercent: number | null
  highestPercent: number | null
  lowestPercent: number | null
  missingCount: number
  pendingCount: number
}

export interface GradebookMatrixResponse {
  subjectId: string
  subjectName: string
  groupId: string
  groupName: string
  teacherNames: string[]
  activityType: GradebookActivityType
  summary: GradebookMatrixSummaryResponse
  activities: GradebookMatrixActivityResponse[]
  students: GradebookMatrixStudentResponse[]
}

export interface TeacherGradebookResponse {
  subjects: GradebookFilterOptionResponse[]
  groups: GradebookFilterOptionResponse[]
  selectedSubjectId: string | null
  selectedGroupId: string | null
  activityType: GradebookActivityType
  matrix: GradebookMatrixResponse | null
}

export interface GradebookAdminOverviewRowResponse {
  subjectId: string
  subjectName: string
  groupId: string
  groupName: string
  teacherIds: string[]
  teacherNames: string[]
  studentCount: number
  averagePercent: number | null
  highCount: number
  goodCount: number
  averageCount: number
  lowCount: number
  missingCount: number
  completionPercent: number | null
}

export interface AdminGradebookOverviewResponse {
  platformAveragePercent: number | null
  activeStudents: number
  subjectCount: number
  groupCount: number
  missingWorkCount: number
  averageCompletionPercent: number | null
  subjects: GradebookFilterOptionResponse[]
  groups: GradebookFilterOptionResponse[]
  teachers: GradebookFilterOptionResponse[]
  rows: GradebookAdminOverviewRowResponse[]
}

export interface AdminGradebookResponse {
  subjects: GradebookFilterOptionResponse[]
  groups: GradebookFilterOptionResponse[]
  teachers: GradebookFilterOptionResponse[]
  selectedSubjectId: string | null
  selectedGroupId: string | null
  selectedTeacherId: string | null
  rows: GradebookAdminOverviewRowResponse[]
  matrix: GradebookMatrixResponse | null
}
