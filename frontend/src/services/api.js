import axios from 'axios'

const api = axios.create({
  baseURL: '/api',
  headers: { 'Content-Type': 'application/json' },
})

api.interceptors.request.use((config) => {
  try {
    const raw = localStorage.getItem('rcs_user')
    if (!raw) return config
    const user = JSON.parse(raw)
    if (user?.token) {
      config.headers = config.headers || {}
      config.headers['Authorization'] = `Bearer ${user.token}`
    }
  } catch {
    // ignore
  }
  return config
})

api.interceptors.response.use(
  (response) => response,
  (error) => {
    if (error?.response?.status === 401) {
      localStorage.removeItem('rcs_user')
      if (!window.location.pathname.startsWith('/login') && !window.location.pathname.startsWith('/register')) {
        window.location.href = '/login'
      }
    }
    return Promise.reject(error)
  },
)

function normalizeArrayPayload(payload) {
  if (!payload) return []
  if (Array.isArray(payload)) return payload
  return payload.data ?? payload.content ?? []
}

export function ensureArray(payload) {
  return normalizeArrayPayload(payload)
}

function normalizeObjectPayload(payload, defaultObj = {}) {
  if (!payload) return { ...defaultObj }
  const candidate = payload.data ?? payload.content ?? payload
  if (typeof candidate !== 'object') return { ...defaultObj }
  return { ...defaultObj, ...candidate }
}

/** Parse doubt reply appended to message by backend */
export function mapDoubtFromApi(d) {
  if (!d) return null
  const message = d.message ?? ''
  const parts = message.split('\n\nReply:\n')
  const reply = parts.length > 1 ? parts.slice(1).join('\n\nReply:\n') : null
  const hasReply = Boolean(reply && reply.trim())
  const status = d.status ?? 'OPEN'
  let statusLabel = 'Pending'
  if (status === 'RESOLVED') statusLabel = 'Resolved'
  else if (hasReply) statusLabel = 'Replied'

  return {
    ...d,
    question: d.title ?? parts[0] ?? message,
    reply,
    statusLabel,
    courseTitle: d.courseTitle ?? 'Course',
    studentName: d.studentName ?? 'Student',
  }
}

export function mapNotificationFromApi(n) {
  if (!n) return null
  return {
    ...n,
    body: n.message ?? n.body ?? '',
    read: n.readStatus ?? n.read ?? false,
  }
}

// Auth
export async function login({ email, password }) {
  const resp = await api.post('/auth/login', { email, password })
  return resp.data
}

export async function register(payload) {
  const resp = await api.post('/auth/register', payload)
  return resp.data
}

// Users (admin)
export async function getUsers() {
  const resp = await api.get('/users')
  return normalizeArrayPayload(resp.data)
}

export async function getUsersByRole(role) {
  const resp = await api.get(`/users/role/${role}`)
  return normalizeArrayPayload(resp.data)
}

export async function setUserActive(userId, active) {
  const resp = await api.patch(`/users/${userId}/active`, null, { params: { active } })
  return resp.data
}

// Courses
export async function getCourses() {
  const resp = await api.get('/courses')
  return normalizeArrayPayload(resp.data)
}

export async function getCourseById(id) {
  const resp = await api.get(`/courses/${id}`)
  return resp.data
}

export async function createCourse(payload) {
  const resp = await api.post('/courses', payload)
  return resp.data
}

export async function updateCourse(courseId, payload) {
  const resp = await api.put(`/courses/${courseId}`, payload)
  return resp.data
}

export async function deleteCourse(courseId) {
  const resp = await api.delete(`/courses/${courseId}`)
  return resp.data
}

export async function addCourseLink(courseId, payload) {
  const resp = await api.post(`/courses/${courseId}/links`, payload)
  return resp.data
}

export async function getCoursesByFaculty(facultyId) {
  const resp = await api.get(`/courses/faculty/${facultyId}`)
  return normalizeArrayPayload(resp.data)
}

export async function getMyFacultyCourses() {
  const resp = await api.get('/courses/faculty/me')
  return normalizeArrayPayload(resp.data)
}


// Enrollments
export async function enrollStudent(payload) {
  const resp = await api.post('/enrollments', payload)
  return resp.data
}

export async function getEnrollmentsByStudent(studentId) {
  const resp = await api.get(`/enrollments/student/${studentId}`)
  return normalizeArrayPayload(resp.data)
}

export async function getEnrollmentsByCourse(courseId) {
  const resp = await api.get(`/enrollments/course/${courseId}`)
  return normalizeArrayPayload(resp.data)
}

// Course Progress
export async function getProgressByStudent(studentId) {
  const resp = await api.get(`/progress/student/${studentId}`)
  return normalizeArrayPayload(resp.data)
}

// Course progress is based on course work the student has actually finished:
// submitted assignments and attempted quizzes. Opening a link only updates recency.
export async function getStudentCourseActivityProgress(studentId, courseId) {
  const [assignments, quizzes, submissions, attempts] = await Promise.all([
    getAssignmentsByCourse(courseId),
    getQuizzesByCourse(courseId),
    getSubmissionsByStudent(studentId),
    getAttemptsByStudent(studentId),
  ])
  const assignmentRows = ensureArray(assignments)
  const quizRows = ensureArray(quizzes)
  const submissionIds = new Set(ensureArray(submissions).map((item) => String(item.assignmentId)))
  const attemptedQuizIds = new Set(ensureArray(attempts).map((item) => String(item.quizId)))
  const completed = assignmentRows.filter((item) => submissionIds.has(String(item.id))).length
    + quizRows.filter((item) => attemptedQuizIds.has(String(item.id))).length
  const total = assignmentRows.length + quizRows.length
  return {
    progressPercent: total ? Math.round((completed / total) * 100) : 0,
    completedActivities: completed,
    totalActivities: total,
  }
}

export async function getProgressByStudentCourse(studentId, courseId) {
  const resp = await api.get(`/progress/student/${studentId}/course/${courseId}`)
  return normalizeObjectPayload(resp.data, {
    studentId,
    courseId,
    progressPercent: 0,
    lastAccessed: null,
  })
}

export async function markCourseOpened({ studentId, courseId }) {
  const resp = await api.post('/progress/open', { studentId, courseId })
  return resp.data
}

// Assignments & Submissions
export async function getAssignmentsByCourse(courseId) {
  const resp = await api.get(`/assignments/course/${courseId}`)
  return normalizeArrayPayload(resp.data)
}

export async function getAssignmentResources(assignmentId) {
  const resp = await api.get(`/assignments/${assignmentId}/resources`)
  return normalizeArrayPayload(resp.data)
}

export async function uploadAssignmentResource(assignmentId, file) {
  const form = new FormData()
  form.append('file', file)
  const resp = await api.post(`/assignments/${assignmentId}/resources`, form, {
    headers: { 'Content-Type': 'multipart/form-data' },
  })
  return resp.data
}

export async function downloadAssignmentResource(assignmentId, resource) {
  try {
    const resp = await api.get(`/assignments/${assignmentId}/resources/${resource.id}/download`, {
      responseType: 'blob',
    })
    const url = URL.createObjectURL(resp.data)
    const link = document.createElement('a')
    link.href = url
    link.download = resource.originalFilename
    document.body.appendChild(link)
    link.click()
    link.remove()
    window.setTimeout(() => URL.revokeObjectURL(url), 1000)
  } catch (error) {
    let message = error?.response?.data?.message
    if (error?.response?.data instanceof Blob) {
      try {
        const body = JSON.parse(await error.response.data.text())
        message = body.message
      } catch {
        // Keep the useful fallback for non-JSON error responses.
      }
    }
    throw new Error(message || 'Could not download this assignment file. Please try again.', { cause: error })
  }
}

export async function createAssignment(payload) {
  const resp = await api.post('/assignments', payload)
  return resp.data
}

export async function updateAssignment(id, payload) {
  const resp = await api.put(`/assignments/${id}`, payload)
  return resp.data
}

export async function deleteAssignment(id) {
  const resp = await api.delete(`/assignments/${id}`)
  return resp.data
}

export async function submitAssignment(payload) {
  const resp = await api.post('/submissions', {
    assignmentId: payload.assignmentId,
    answerText: payload.answerText ?? payload.text ?? '',
  })
  return resp.data
}

export async function getSubmissionsByAssignment(assignmentId) {
  const resp = await api.get(`/submissions/assignment/${assignmentId}`)
  return normalizeArrayPayload(resp.data)
}

export async function getAssignmentTracking(assignmentId) {
  const resp = await api.get(`/submissions/assignment/${assignmentId}/tracking`)
  return normalizeArrayPayload(resp.data)
}

export async function getSubmissionsByStudent(studentId) {
  const resp = await api.get(`/submissions/student/${studentId}`)
  return normalizeArrayPayload(resp.data)
}

export async function gradeSubmission({ submissionId, marks, feedback }) {
  const resp = await api.put(`/submissions/${submissionId}/grade`, null, {
    params: { grade: marks, feedback: feedback ?? '' },
  })
  return resp.data
}

// Attendance
export async function markAttendance(payload) {
  const resp = await api.post('/attendance', payload)
  return resp.data
}

export async function markAttendanceBulk({ facultyId, courseId, date, records }) {
  const results = []
  for (const record of records) {
    const resp = await api.post('/attendance', {
      facultyId,
      courseId,
      date,
      present: record.present,
      studentId: record.studentId,
    })
    results.push(resp.data)
  }
  return results
}

export async function getAttendanceByStudent(studentId) {
  const resp = await api.get(`/attendance/student/${studentId}`)
  const records = normalizeArrayPayload(resp.data)
  return records.map((r) => ({
    ...r,
    sessionId: r.id,
    sessionDate: r.date,
  }))
}

export async function getAttendancePercentByStudentCourse(studentId, courseId) {
  const records = normalizeArrayPayload(await getAttendanceByStudent(studentId))
  const courseRecords = records.filter((r) => String(r.courseId) === String(courseId))
  const presentCount = courseRecords.filter((r) => r.present).length
  return {
    percent: courseRecords.length ? Math.round((presentCount / courseRecords.length) * 100) : 0,
    total: courseRecords.length,
    presentCount,
  }
}

export async function getAttendancePercentByStudent(studentId) {
  const records = normalizeArrayPayload(await getAttendanceByStudent(studentId))
  const presentCount = records.filter((r) => r.present).length
  return {
    percent: records.length ? Math.round((presentCount / records.length) * 100) : 0,
    total: records.length,
    presentCount,
  }
}

export async function getAttendanceByCourse(courseId) {
  const resp = await api.get(`/attendance/course/${courseId}`)
  return normalizeArrayPayload(resp.data)
}

// Doubts
export async function createDoubt(payload) {
  const resp = await api.post('/doubts', {
    title: payload.title ?? payload.question ?? 'Doubt',
    message: payload.message ?? payload.question ?? '',
    studentId: payload.studentId,
    courseId: payload.courseId,
  })
  return mapDoubtFromApi(resp.data)
}

export async function replyToDoubt(id, message) {
  const resp = await api.put(`/doubts/${id}/reply`, null, { params: { message } })
  return mapDoubtFromApi(resp.data)
}

export async function resolveDoubt(id) {
  const resp = await api.put(`/doubts/${id}/resolve`)
  return mapDoubtFromApi(resp.data)
}

export async function getDoubtsByStudent(studentId) {
  const resp = await api.get(`/doubts/student/${studentId}`)
  return normalizeArrayPayload(resp.data).map(mapDoubtFromApi)
}

export async function getDoubtsByFaculty(facultyId) {
  const resp = await api.get(`/doubts/faculty/${facultyId}`)
  return normalizeArrayPayload(resp.data).map(mapDoubtFromApi)
}

export async function getDoubtsByCourse(courseId) {
  const resp = await api.get(`/doubts/course/${courseId}`)
  return normalizeArrayPayload(resp.data).map(mapDoubtFromApi)
}

// Notifications
export async function getNotificationsForUser(userId) {
  const resp = await api.get(`/notifications/user/${userId}`)
  return normalizeArrayPayload(resp.data).map(mapNotificationFromApi)
}

export async function markNotificationRead(id) {
  const resp = await api.put(`/notifications/${id}/read`)
  return mapNotificationFromApi(resp.data)
}




export async function createNotification(payload) {
  const resp = await api.post('/notifications', payload)
  return mapNotificationFromApi(resp.data)
}

// Dashboard
export async function getStudentDashboard(studentId) {
  const resp = await api.get(`/dashboard/student/${studentId}`)
  return normalizeObjectPayload(resp.data, {
    totalEnrolled: 0,
    attendancePercent: 0,
    overallCompletion: 0,
    pendingAssignments: 0,
    courseProgress: [],
    recentAnnouncements: [],
    recentReplies: [],
  })
}

export async function getFacultyDashboard(facultyId) {
  const resp = await api.get(`/dashboard/faculty/${facultyId}`)
  return normalizeObjectPayload(resp.data, {
    totalCourses: 0,
    totalStudents: 0,
    attendanceAvg: 0,
    assignmentRate: 0,
    studentProgress: [],
    recentDoubts: [],
  })
}

export async function getAdminDashboard() {
  const resp = await api.get('/dashboard/admin')
  return normalizeObjectPayload(resp.data, {
    totalStudents: 0,
    totalFaculty: 0,
    totalCourses: 0,
    totalEnrollments: 0,
    activeStudents: 0,
    activeFaculty: 0,
    enrollmentRate: 0,
  })
}

// Quizzes
export async function getQuizzesByCourse(courseId) {
  const resp = await api.get(`/quizzes/course/${courseId}`)
  return normalizeArrayPayload(resp.data)
}

export async function getQuizById(id) {
  const resp = await api.get(`/quizzes/${id}`)
  return resp.data
}

export async function createQuiz(payload) {
  const resp = await api.post('/quizzes', payload)
  return resp.data
}

export async function updateQuiz(id, payload) {
  const resp = await api.put(`/quizzes/${id}`, payload)
  return resp.data
}

export async function deleteQuiz(id) {
  const resp = await api.delete(`/quizzes/${id}`)
  return resp.data
}

// Questions
export async function getQuestionsByQuiz(quizId) {
  const resp = await api.get(`/quizzes/${quizId}/questions`)
  return normalizeArrayPayload(resp.data)
}

export async function addQuizQuestion(quizId, payload) {
  const resp = await api.post(`/quizzes/${quizId}/questions`, payload)
  return resp.data
}

export async function deleteQuizQuestion(questionId) {
  const resp = await api.delete(`/quizzes/questions/${questionId}`)
  return resp.data
}

// Attempts
export async function submitQuizAttempt(quizId, payload) {
  const resp = await api.post(`/quizzes/${quizId}/attempt`, {
    answersJson: payload.answersJson,
    studentId: payload.studentId,
  })
  return resp.data
}

export async function getAttemptsByStudent(studentId) {
  const resp = await api.get(`/quizzes/student/${studentId}/attempts`)
  return normalizeArrayPayload(resp.data)
}

export async function getAttemptsByQuiz(quizId) {
  const resp = await api.get(`/quizzes/${quizId}/attempts`)
  return normalizeArrayPayload(resp.data)
}

export async function getAttemptsByQuizAndStudent(quizId, studentId) {
  const resp = await api.get(`/quizzes/${quizId}/student/${studentId}/attempts`)
  return normalizeArrayPayload(resp.data)
}

// Chatbot
export async function sendMessageToChatbot(payload) {
  const resp = await api.post('/chatbot/message', {
    message: payload.message,
  })
  return resp.data
}

export async function getChatbotHistory() {
  const resp = await api.get('/chatbot/history')
  return normalizeArrayPayload(resp.data)
}

export async function clearChatbotHistory() {
  const resp = await api.delete('/chatbot/history')
  return resp.data
}

// Meetings
export async function getMeetingsByCourse(courseId) {
  const resp = await api.get(`/meetings/course/${courseId}`)
  return normalizeArrayPayload(resp.data)
}

export async function getMeetingById(id) {
  const resp = await api.get(`/meetings/${id}`)
  return resp.data
}

export async function joinMeeting(id) {
  const resp = await api.post(`/meetings/${id}/join`)
  return resp.data
}

export async function createMeeting(payload) {
  const resp = await api.post('/meetings', payload)
  return resp.data
}

export async function updateMeetingStatus(id, status) {
  const resp = await api.patch(`/meetings/${id}/status`, null, { params: { status } })
  return resp.data
}

export default api
