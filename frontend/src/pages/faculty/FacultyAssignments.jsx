import { useState, useEffect } from 'react'
import { Link } from 'react-router-dom'
import { useAuth } from '../../context/AuthContext'
import {
  getCoursesByFaculty,
  getAssignmentsByCourse,
  gradeSubmission,
  createAssignment,
  updateAssignment,
  deleteAssignment,
  getAssignmentTracking,
  getAssignmentResources,
  uploadAssignmentResource,
  downloadAssignmentResource,
  ensureArray,
} from '../../services/api'

function SubmissionBadge({ status }) {
  const cls = status === 'GRADED' ? 'badge badgeSuccess' : status === 'SUBMITTED' || status === 'LATE' ? 'badge badgeAccent' : 'badge badgeDanger'
  return <span className={cls}>{status}</span>
}

function toDateTimeLocal(value) {
  if (!value) return ''
  const date = new Date(value)
  if (Number.isNaN(date.getTime())) return ''
  date.setMinutes(date.getMinutes() - date.getTimezoneOffset())
  return date.toISOString().slice(0, 16)
}

function formatFileSize(bytes) {
  if (bytes < 1024) return `${bytes} B`
  if (bytes < 1024 * 1024) return `${(bytes / 1024).toFixed(1)} KB`
  return `${(bytes / (1024 * 1024)).toFixed(1)} MB`
}

export default function FacultyAssignments() {
  const { user } = useAuth()
  const facultyId = user?.userId ?? user?.id

  const [model, setModel] = useState({ courses: [], assignments: [] })
  const [activeId, setActiveId] = useState(null)
  const [error, setError] = useState(null)
  const active = model.assignments.find((a) => a.id === activeId) ?? model.assignments[0] ?? null

  useEffect(() => {
    let mounted = true
    async function load() {
      if (!facultyId) return
      setError(null)
      try {
        const courses = ensureArray(await getCoursesByFaculty(facultyId))
        const assignments = []
        for (const c of courses) {
          const assigns = ensureArray(await getAssignmentsByCourse(c.id))
          for (const a of assigns) assignments.push(a)
        }
        assignments.sort((a, b) => String(b.createdAt).localeCompare(String(a.createdAt)))
        if (mounted) {
          const selected = assignments[0] ?? null
          setModel({ courses, assignments })
          setActiveId(selected?.id ?? null)
          syncAssignmentForm(selected)
        }
      } catch (e) {
        if (mounted) {
          setModel({ courses: [], assignments: [] })
          setError(e?.response?.data?.message ?? 'Could not load courses and assignments. Please try again.')
        }
      }
    }
    load()
    return () => { mounted = false }
  }, [facultyId])
  const [grade, setGrade] = useState('')
  const [feedback, setFeedback] = useState('')
  const [submissions, setSubmissions] = useState([])
  const [resources, setResources] = useState([])
  const [resourceFiles, setResourceFiles] = useState([])
  const [resourceReloadKey, setResourceReloadKey] = useState(0)
  const [resourceMessage, setResourceMessage] = useState(null)
  const [resourceError, setResourceError] = useState(null)
  const [uploadingResources, setUploadingResources] = useState(false)
  const [downloadingResourceId, setDownloadingResourceId] = useState(null)
  const [editing, setEditing] = useState(false)

  // Form fields derived from active assignment when not editing
  const [editedTitle, setEditedTitle] = useState('')
  const [editedDescription, setEditedDescription] = useState('')
  const [editedCourseId, setEditedCourseId] = useState('')
  const [editedDueDate, setEditedDueDate] = useState('')
  const [maxMarks, setMaxMarks] = useState('100')
  const [allowLateSubmission, setAllowLateSubmission] = useState(false)

  function syncAssignmentForm(assignment) {
    setEditedTitle(assignment?.title ?? '')
    setEditedDescription(assignment?.description ?? '')
    setEditedDueDate(toDateTimeLocal(assignment?.dueDate))
    setEditedCourseId(String(assignment?.courseId ?? ''))
    setMaxMarks(String(assignment?.maxMarks ?? 100))
    setAllowLateSubmission(Boolean(assignment?.allowLateSubmission))
  }

  // Convenience aliases so the rest of the code keeps working unchanged
  const title = editedTitle
  const setTitle = setEditedTitle
  const description = editedDescription
  const setDescription = setEditedDescription
  const courseId = editedCourseId
  const setCourseId = setEditedCourseId
  const dueDate = editedDueDate
  const setDueDate = setEditedDueDate

  async function reloadSubmissions(assignmentId) {
    if (!assignmentId) {
      setSubmissions([])
      return
    }
    const subs = ensureArray(await getAssignmentTracking(assignmentId))
    setSubmissions(
      subs.map((s) => ({
        id: s.id,
        studentName: s.studentName ?? 'Student',
        studentId: s.studentId,
        status: s.status,
        answerText: s.answerText ?? '',
        feedback: s.feedback ?? '',
        grade: s.grade,
        submittedAt: s.submittedAt,
        late: s.late,
      })),
    )
  }

  function ensureActiveCourse() {
    const firstCourseId = model.courses[0]?.id
    const nextCourseId = courseId || firstCourseId || ''
    return nextCourseId
  }

  async function reloadAssignments() {
    const courses = ensureArray(await getCoursesByFaculty(facultyId))
    const assignments = []
    for (const c of courses) {
      const assigns = ensureArray(await getAssignmentsByCourse(c.id))
      for (const a of assigns) assignments.push(a)
    }
    assignments.sort((a, b) => String(b.createdAt).localeCompare(String(a.createdAt)))
    setModel({ courses, assignments })
    return assignments
  }

  useEffect(() => {
    let mounted = true
    async function loadSubs() {
      if (!active?.id) {
        if (mounted) setSubmissions([])
        return
      }
      try {
        const subs = ensureArray(await getAssignmentTracking(active.id))
        if (mounted) {
          setSubmissions(
            subs.map((s) => ({
              id: s.id,
              studentName: s.studentName ?? 'Student',
              studentId: s.studentId,
              status: s.status,
              answerText: s.answerText ?? '',
              feedback: s.feedback ?? '',
              grade: s.grade,
              submittedAt: s.submittedAt,
              late: s.late,
            })),
          )
        }
      } catch {
        if (mounted) setSubmissions([])
      }
    }
    loadSubs()
    return () => { mounted = false }
  }, [active?.id])

  useEffect(() => {
    let mounted = true
    if (!active?.id) {
      return () => { mounted = false }
    }
    getAssignmentResources(active.id)
      .then((items) => {
        if (mounted) {
          setResources(ensureArray(items))
          setResourceError(null)
        }
      })
      .catch((e) => {
        if (mounted) {
          setResources([])
          setResourceError(e?.response?.data?.message ?? 'Could not load assignment files.')
        }
      })
    return () => { mounted = false }
  }, [active?.id, resourceReloadKey])

  async function handleCreateAssignment() {
    const cid = ensureActiveCourse()
    if (!cid) return alert('Create a course first.')

    const t = title.trim()
    if (!t) return alert('Assignment title is required.')

    setError(null)
    try {
      const payload = {
        courseId: Number(cid),
        title: t,
        description: description.trim(),
        dueDate: dueDate ? new Date(dueDate).toISOString() : null,
        maxMarks: Number(maxMarks),
        allowLateSubmission,
      }

      await createAssignment(payload)
      const assignments = await reloadAssignments()
      setActiveId(assignments[0]?.id ?? null)
      setTitle('')
      setDescription('')
      setDueDate('')
      setMaxMarks('100')
      setAllowLateSubmission(false)
      syncAssignmentForm(assignments[0] ?? null)
      setEditing(false)
    } catch (e) {
      setError(e?.response?.data?.message ?? 'Failed to create assignment')
    }
  }

  async function handleUpdateAssignment() {
    if (!active) return
    const t = title.trim()
    if (!t) return alert('Assignment title is required.')
    setError(null)
    try {
      await updateAssignment(active.id, {
        courseId: Number(courseId || active.courseId),
        title: t,
        description: description.trim(),
        dueDate: dueDate ? new Date(dueDate).toISOString() : null,
        maxMarks: Number(maxMarks),
        allowLateSubmission,
      })
      await reloadAssignments()
      setEditing(false)
      setActiveId(active.id)
    } catch (e) {
      setError(e?.response?.data?.message ?? 'Failed to update assignment')
    }
  }

  async function handleGradeSubmission(submission) {
    if (!submission) return
    const marks = Number(grade)
    if (!Number.isFinite(marks) || marks < 0 || marks > Number(active?.maxMarks ?? maxMarks)) {
      return alert(`Enter a grade between 0 and ${active?.maxMarks ?? maxMarks}.`)
    }

    setError(null)
    try {
      await gradeSubmission({
        submissionId: submission.id,
        marks,
        feedback,
      })
      setGrade('')
      setFeedback('')
      await reloadSubmissions(active.id)
    } catch (e) {
      setError(e?.response?.data?.message ?? 'Failed to grade submission')
    }
  }

  async function handleDeleteAssignment(id) {
    if (!confirm('Delete this assignment?')) return
    setError(null)
    try {
      await deleteAssignment(id)
      const assignments = await reloadAssignments()
      setActiveId(null)
      syncAssignmentForm(assignments[0] ?? null)
      setEditing(false)
    } catch (e) {
      setError(e?.response?.data?.message ?? 'Failed to delete assignment')
    }
  }

  async function handleUploadResources() {
    if (!active?.id || !resourceFiles.length) return
    setUploadingResources(true)
    setResourceError(null)
    setResourceMessage(null)
    let uploaded = 0
    try {
      for (const file of resourceFiles) {
        await uploadAssignmentResource(active.id, file)
        uploaded += 1
      }
      setResourceFiles([])
      setResourceMessage(`Uploaded ${uploaded} resource${uploaded === 1 ? '' : 's'}.`)
    } catch (e) {
      const message = e?.response?.data?.message ?? e.message ?? 'Upload failed.'
      setResourceError(uploaded
        ? `Uploaded ${uploaded} file(s), then stopped: ${message}`
        : message)
    } finally {
      setResourceReloadKey((key) => key + 1)
      setUploadingResources(false)
    }
  }

  async function handleDownloadResource(resource) {
    setDownloadingResourceId(resource.id)
    setResourceError(null)
    try {
      await downloadAssignmentResource(active.id, resource)
    } catch (e) {
      setResourceError(e.message || 'Could not download this file.')
    } finally {
      setDownloadingResourceId(null)
    }
  }

  function courseTitle(courseId) {
    return model.courses.find((c) => String(c.id) === String(courseId))?.title ?? '—'
  }

  return (
    <div>
      <div className="card" style={{ padding: 16 }}>
        <div className="badge badgeAccent">Assignments</div>
        <h1 style={{ fontSize: 26, marginTop: 10 }}>Create & Grade Assignments</h1>
        <p style={{ opacity: 0.8, marginTop: 6, lineHeight: 1.7 }}>Manage assignments and grade student submissions via the backend API.</p>
        {error ? <div className="badge badgeDanger" style={{ marginTop: 12 }}>{error}</div> : null}
        {!model.courses.length && (
          <div className="badge" style={{ display: 'block', marginTop: 12 }}>
            Assignments need a course. <Link to="/faculty/courses" style={{ color: 'var(--accent)', textDecoration: 'underline' }}>Create one in Manage Courses</Link> first.
          </div>
        )}
      </div>

      <div className="grid" style={{ marginTop: 14, gridTemplateColumns: '0.95fr 1.05fr', gap: 14 }}>
        <div className="card" style={{ padding: 16 }}>
          <div style={{ fontWeight: 950, fontSize: 18 }}>Assignments</div>
          <div style={{ marginTop: 12, display: 'flex', flexDirection: 'column', gap: 10 }}>
            {model.assignments.length ? (
              model.assignments.map((a) => (
                <button
                  key={a.id}
                  className="card"
                  style={{
                    padding: 12,
                    textAlign: 'left',
                    cursor: 'pointer',
                    background: a.id === activeId ? 'rgba(124,58,237,.10)' : 'rgba(255,255,255,.02)',
                    border: a.id === activeId ? '1px solid rgba(124,58,237,.45)' : '1px solid transparent',
                  }}
                  onClick={() => {
                    setResourceFiles([])
                    setActiveId(a.id)
                    syncAssignmentForm(a)
                  }}
                >
                  <div style={{ fontWeight: 950 }}>{a.title}</div>
                  <div style={{ fontSize: 12, opacity: 0.75, marginTop: 4 }}>Course: {courseTitle(a.courseId)}</div>
                </button>
              ))
            ) : (
              <div className="badge" style={{ opacity: 0.85 }}>No assignments yet. Create one below.</div>
            )}
          </div>

          <div className="hr" style={{ marginTop: 14 }} />

          <div style={{ fontWeight: 950, fontSize: 18 }}>Create Assignment</div>
          <div style={{ marginTop: 10, display: 'grid', gap: 10 }}>
            <div>
              <label className="label">Course</label>
              <select className="input" value={courseId || ensureActiveCourse()} onChange={(e) => setCourseId(e.target.value)} disabled={!model.courses.length}>
                {model.courses.map((c) => (
                  <option key={c.id} value={c.id}>{c.title}</option>
                ))}
                {!model.courses.length && <option value="">No courses</option>}
              </select>
            </div>
            <div>
              <label className="label">Title</label>
              <input className="input" value={title} onChange={(e) => setTitle(e.target.value)} placeholder="e.g., Assignment 1" />
            </div>
            <div>
              <label className="label">Description</label>
              <textarea className="input" style={{ minHeight: 90, resize: 'vertical' }} value={description} onChange={(e) => setDescription(e.target.value)} placeholder="Short description" />
            </div>
            <div style={{ display: 'grid', gridTemplateColumns: '1fr 1fr', gap: 10 }}>
              <div>
                <label className="label">Due date</label>
              <input className="input" type="datetime-local" value={dueDate} onChange={(e) => setDueDate(e.target.value)} />
              </div>
              <div>
                <label className="label">Max marks</label>
                <input className="input" type="number" min={0.01} step="any" value={maxMarks} onChange={(e) => setMaxMarks(e.target.value)} />
              </div>
            </div>
            <label style={{ display: 'flex', gap: 8, alignItems: 'center', fontSize: 13 }}>
              <input type="checkbox" checked={allowLateSubmission} onChange={(e) => setAllowLateSubmission(e.target.checked)} />
              Accept late submissions
            </label>
            <div style={{ display: 'flex', gap: 12, flexWrap: 'wrap' }}>
              {editing ? (
                <>
                  <button type="button" className="btn btnPrimary" onClick={handleUpdateAssignment}>Save Changes</button>
                  <button type="button" className="btn" onClick={() => setEditing(false)}>Cancel</button>
                </>
              ) : (
                <button type="button" className="btn btnPrimary" onClick={handleCreateAssignment} disabled={!model.courses.length}>Create</button>
              )}
              {active && (
                <>
                  <button type="button" className="btn" onClick={() => setEditing(true)}>Edit Active</button>
                  <button type="button" className="btn" onClick={() => handleDeleteAssignment(active.id)}>Delete Active</button>
                </>
              )}
            </div>

            <div className="hr" />
            <div style={{ fontWeight: 900 }}>Assignment resources</div>
            {resourceMessage ? <div role="status" className="badge badgeSuccess">{resourceMessage}</div> : null}
            {resourceError ? <div role="alert" className="badge badgeDanger">{resourceError}</div> : null}
            {active ? (
              <>
                <div style={{ display: 'grid', gap: 8 }}>
                  {resources.map((resource) => (
                    <div key={resource.id} className="card" style={{ padding: 10, display: 'flex', justifyContent: 'space-between', gap: 10, alignItems: 'center', flexWrap: 'wrap' }}>
                      <span>{resource.originalFilename} <span style={{ opacity: 0.7 }}>({formatFileSize(resource.fileSize)})</span></span>
                      <button type="button" className="btn" disabled={downloadingResourceId === resource.id} onClick={() => handleDownloadResource(resource)}>
                        {downloadingResourceId === resource.id ? 'Downloading…' : 'Download'}
                      </button>
                    </div>
                  ))}
                  {!resources.length ? <div style={{ fontSize: 13, opacity: 0.75 }}>No files attached yet.</div> : null}
                </div>
                <label className="label" htmlFor="assignment-resource-files">Add files (PDF, TXT, DOCX, PPTX; up to 10 MB each)</label>
                <input
                  id="assignment-resource-files"
                  className="input"
                  type="file"
                  multiple
                  accept=".pdf,.txt,.docx,.pptx"
                  onChange={(event) => setResourceFiles(Array.from(event.target.files ?? []))}
                />
                <button type="button" className="btn btnPrimary" disabled={!resourceFiles.length || uploadingResources} onClick={handleUploadResources}>
                  {uploadingResources ? 'Uploading…' : resourceFiles.length ? `Upload ${resourceFiles.length} selected file${resourceFiles.length === 1 ? '' : 's'}` : 'Upload selected files'}
                </button>
              </>
            ) : (
              <div style={{ fontSize: 13, opacity: 0.75 }}>Create or select an assignment before adding resources.</div>
            )}
          </div>
        </div>

        <div className="card" style={{ padding: 16 }}>
          <div style={{ fontWeight: 950, fontSize: 18 }}>Submissions</div>
          <div style={{ opacity: 0.8, fontSize: 13, marginTop: 6 }}>{active?.title ?? '—'}</div>

          <div className="hr" style={{ marginTop: 12 }} />

          {active ? (
            <div style={{ marginTop: 12, display: 'flex', flexDirection: 'column', gap: 12 }}>
              {(submissions || []).length ? (
                submissions.map((s) => (
                  <div key={s.id} className="card" style={{ padding: 12, background: 'rgba(255,255,255,.02)' }}>
                    <div style={{ display: 'flex', justifyContent: 'space-between', gap: 12, flexWrap: 'wrap' }}>
                      <div>
                        <div style={{ fontWeight: 950 }}>{s.studentName}</div>
                        <div style={{ fontSize: 12, opacity: 0.75, marginTop: 3 }}>{s.submitted ? `Submitted ${s.submittedAt ? new Date(s.submittedAt).toLocaleString() : ''}${s.late ? ' · Late' : ''}` : 'Not submitted'}</div>
                      </div>
                      <SubmissionBadge status={s.status} />
                    </div>

                    {s.submitted ? <div style={{ marginTop: 10, fontSize: 13, opacity: 0.85, lineHeight: 1.6, whiteSpace: 'pre-wrap' }}>{s.answerText || 'No answer text available for this historical submission.'}</div> : null}

                    {s.submitted ? (
                      <div style={{ marginTop: 10, display: 'flex', gap: 10, flexWrap: 'wrap', alignItems: 'center' }}>
                        <input
                          className="input"
                          type="number"
                          min="0"
                          max={active.maxMarks ?? 100}
                          step="any"
                          placeholder={`Grade (0-${active.maxMarks ?? 100})`}
                          value={grade}
                          onChange={(e) => setGrade(e.target.value)}
                          style={{ width: 170 }}
                        />
                        <input className="input" placeholder="Feedback (optional)" value={feedback} onChange={(e) => setFeedback(e.target.value)} />
                        <button className="btn btnPrimary" onClick={() => handleGradeSubmission(s)}>Save grade</button>
                        {s.grade != null ? <div style={{ width: '100%', fontSize: 13, opacity: 0.82 }}>Current grade: {s.grade}/{active.maxMarks ?? 100}{s.feedback ? ` · ${s.feedback}` : ''}</div> : null}
                      </div>
                    ) : (
                      <div style={{ marginTop: 10, fontSize: 13, opacity: 0.82 }}>Waiting for student submission.</div>
                    )}
                  </div>
                ))
              ) : (
                <div className="badge" style={{ opacity: 0.85 }}>No submissions yet for this assignment.</div>
              )}
            </div>
          ) : (
            <div className="badge" style={{ opacity: 0.85, marginTop: 12 }}>Select/create an assignment.</div>
          )}
        </div>
      </div>
    </div>
  )
}


