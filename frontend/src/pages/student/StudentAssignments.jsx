import { useState, useEffect } from 'react'
import { useAuth } from '../../context/AuthContext'
import {
  getEnrollmentsByStudent,
  getCourseById,
  getAssignmentsByCourse,
  getSubmissionsByStudent,
  submitAssignment,
  getAssignmentResources,
  downloadAssignmentResource,
  ensureArray,
} from '../../services/api'

function GradeBadge({ status }) {
  const map = {
    NOT_SUBMITTED: { text: 'Not submitted', cls: 'badge badgeDanger' },
    SUBMITTED: { text: 'Submitted', cls: 'badge badgeAccent' },
    GRADED: { text: 'Graded', cls: 'badge badgeSuccess' },
  }
  const v = map[status] ?? { text: status, cls: 'badge' }
  return <span className={v.cls}>{v.text}</span>
}

function formatFileSize(bytes) {
  if (bytes < 1024) return `${bytes} B`
  if (bytes < 1024 * 1024) return `${(bytes / 1024).toFixed(1)} KB`
  return `${(bytes / (1024 * 1024)).toFixed(1)} MB`
}

export default function StudentAssignments() {
  const { user } = useAuth()
  const studentId = user?.userId ?? user?.id

  const [activeId, setActiveId] = useState(null)
  const [answer, setAnswer] = useState('')
  const [model, setModel] = useState({ items: [], active: null })
  const [error, setError] = useState(null)
  const [success, setSuccess] = useState(null)
  const [submitting, setSubmitting] = useState(false)
  const [reloadKey, setReloadKey] = useState(0)
  const [resources, setResources] = useState([])
  const [resourcesForAssignmentId, setResourcesForAssignmentId] = useState(null)
  const [resourceError, setResourceError] = useState(null)
  const [downloadingResourceId, setDownloadingResourceId] = useState(null)

  useEffect(() => {
    let mounted = true
    async function load() {
      if (!studentId) return
      setError(null)
      try {
        const enrollments = ensureArray(await getEnrollmentsByStudent(studentId))
        const submissions = ensureArray(await getSubmissionsByStudent(studentId))
        const allItems = []

        for (const e of enrollments) {
          const assigns = ensureArray(await getAssignmentsByCourse(e.courseId))
          for (const a of assigns) {
            const course = await getCourseById(a.courseId)
            const sub = submissions.find(
              (s) => String(s.assignmentId) === String(a.id) && String(s.studentId) === String(studentId),
            )
            const status = sub?.status ?? 'NOT_SUBMITTED'
            allItems.push({
              id: a.id,
              courseTitle: course?.title ?? 'Course',
              title: a.title,
              description: a.description ?? '',
              due: a.dueDate ? new Date(a.dueDate).toLocaleDateString() : '—',
              dueDateTime: a.dueDate ? new Date(a.dueDate).toLocaleString() : 'No deadline',
              maxMarks: a.maxMarks ?? 100,
              allowLateSubmission: Boolean(a.allowLateSubmission),
              status,
              grade: sub?.grade ?? null,
              answerText: sub?.answerText ?? '',
              feedback: sub?.feedback ?? '',
              late: Boolean(sub?.late),
            })
          }
        }

        allItems.sort((a, b) => String(b.due).localeCompare(String(a.due)))
        const active = allItems.find((x) => String(x.id) === String(activeId)) ?? allItems[0] ?? null
        if (mounted) {
          setModel({ items: allItems, active })
          if (active) setAnswer(active.answerText)
        }
      } catch (e) {
        if (mounted) setError(e?.response?.data?.message ?? 'Failed to load assignments')
      }
    }
    load()
    return () => {
      mounted = false
    }
  }, [studentId, activeId, reloadKey])

  useEffect(() => {
    const assignmentId = model.active?.id
    if (!assignmentId) return
    let mounted = true
    getAssignmentResources(assignmentId)
      .then((items) => {
        if (mounted) {
          setResources(ensureArray(items))
          setResourcesForAssignmentId(assignmentId)
          setResourceError(null)
        }
      })
      .catch((e) => {
        if (mounted) {
          setResources([])
          setResourcesForAssignmentId(assignmentId)
          setResourceError(e?.response?.data?.message ?? 'Could not load assignment files.')
        }
      })
    return () => { mounted = false }
  }, [model.active?.id])

  async function submit() {
    if (!model.active || !studentId || !answer.trim()) return
    setSubmitting(true)
    setError(null)
    setSuccess(null)
    try {
      await submitAssignment({ assignmentId: model.active.id, answerText: answer.trim() })
      setSuccess('Your answer was submitted successfully.')
      setReloadKey((k) => k + 1)
    } catch (e) {
      setError(e?.response?.data?.message ?? 'Submission failed')
    } finally {
      setSubmitting(false)
    }
  }

  async function downloadResource(resource) {
    setDownloadingResourceId(resource.id)
    setResourceError(null)
    try {
      await downloadAssignmentResource(model.active.id, resource)
    } catch (e) {
      setResourceError(e.message || 'Could not download this assignment file.')
    } finally {
      setDownloadingResourceId(null)
    }
  }

  return (
    <div>
      <div className="card" style={{ padding: 16 }}>
        <div className="badge badgeAccent">Assignments</div>
        <h1 style={{ fontSize: 26, marginTop: 10 }}>Submit Assignments</h1>
        <div style={{ opacity: 0.8, marginTop: 6, fontSize: 13 }}>Text-only submissions saved to the backend.</div>
        {error ? <div role="alert" className="badge badgeDanger" style={{ marginTop: 12 }}>{error}</div> : null}
        {success ? <div role="status" className="badge badgeSuccess" style={{ marginTop: 12 }}>{success}</div> : null}
      </div>

      <div className="grid" style={{ marginTop: 14, gridTemplateColumns: '0.95fr 1.05fr', gap: 14 }}>
        <div className="card" style={{ padding: 16 }}>
          <div style={{ fontWeight: 950, fontSize: 18 }}>Assignment List</div>
          <div style={{ marginTop: 12, display: 'flex', flexDirection: 'column', gap: 10 }}>
            {ensureArray(model.items).map((a) => (
              <button
                key={a.id}
                type="button"
                className="card"
                style={{
                  padding: 12,
                  textAlign: 'left',
                  cursor: 'pointer',
                  background: String(a.id) === String(model.active?.id) ? 'rgba(124,58,237,.10)' : 'rgba(255,255,255,.02)',
                  border: String(a.id) === String(model.active?.id) ? '1px solid rgba(124,58,237,.45)' : '1px solid transparent',
                }}
                onClick={() => {
                  setActiveId(a.id)
                  setAnswer(a.answerText ?? '')
                }}
              >
                <div style={{ fontWeight: 900 }}>{a.title}</div>
                <div style={{ fontSize: 12, opacity: 0.75, marginTop: 4 }}>{a.courseTitle} · Due {a.due}</div>
                <div style={{ marginTop: 8 }}><GradeBadge status={a.status} /></div>
              </button>
            ))}
            {!model.items.length && (
              <div className="badge" style={{ opacity: 0.8 }}>No assignments available. Enroll in a course first.</div>
            )}
          </div>
        </div>

        <div className="card" style={{ padding: 16 }}>
          <div style={{ fontWeight: 950, fontSize: 18 }}>{model.active?.title ?? 'Select an assignment'}</div>
          <div style={{ opacity: 0.8, fontSize: 13, marginTop: 6 }}>Course: {model.active?.courseTitle ?? '—'}</div>
          <div style={{ opacity: 0.8, fontSize: 13, marginTop: 4 }}>Due: {model.active?.dueDateTime ?? '—'}</div>
          <div style={{ opacity: 0.8, fontSize: 13, marginTop: 4 }}>Maximum marks: {model.active?.maxMarks ?? '—'}</div>
          {model.active?.allowLateSubmission ? <div style={{ opacity: 0.8, fontSize: 13, marginTop: 4 }}>Late submissions are accepted.</div> : null}
          {model.active?.description ? (
            <div style={{ opacity: 0.85, fontSize: 13, marginTop: 8, lineHeight: 1.6 }}>{model.active.description}</div>
          ) : null}

          <div style={{ marginTop: 16, fontWeight: 850 }}>Assignment files</div>
          {resourceError ? <div role="alert" className="badge badgeDanger" style={{ marginTop: 8 }}>{resourceError}</div> : null}
          <div style={{ display: 'grid', gap: 8, marginTop: 8 }}>
            {resourcesForAssignmentId === model.active?.id ? resources.map((resource) => (
              <div key={resource.id} className="card" style={{ padding: 10, display: 'flex', justifyContent: 'space-between', gap: 10, alignItems: 'center', flexWrap: 'wrap' }}>
                <span>{resource.originalFilename} <span style={{ opacity: 0.7 }}>({formatFileSize(resource.fileSize)})</span></span>
                <button type="button" className="btn" disabled={downloadingResourceId === resource.id} onClick={() => downloadResource(resource)}>
                  {downloadingResourceId === resource.id ? 'Downloading…' : 'Download'}
                </button>
              </div>
            )) : null}
            {model.active && resourcesForAssignmentId === model.active.id && !resources.length && !resourceError ? (
              <div style={{ fontSize: 13, opacity: 0.75 }}>No files are attached to this assignment.</div>
            ) : null}
          </div>

          <div className="hr" />

          <div style={{ fontWeight: 850 }}>Your Answer</div>
          <textarea
            className="input"
            style={{ minHeight: 180, marginTop: 10, resize: 'vertical' }}
            placeholder="Type your solution here..."
            value={answer}
            onChange={(e) => setAnswer(e.target.value)}
            disabled={!model.active}
          />

          <div style={{ display: 'flex', gap: 12, marginTop: 12, flexWrap: 'wrap', alignItems: 'center' }}>
            <button
              type="button"
              className="btn btnPrimary"
              disabled={!answer.trim().length || !model.active || submitting}
              onClick={submit}
            >
              {submitting ? 'Submitting...' : ['SUBMITTED', 'LATE', 'GRADED'].includes(model.active?.status) ? 'Resubmit' : 'Submit'}
            </button>
            <div className="badge" style={{ opacity: 0.8 }}>
              {model.active?.status === 'GRADED'
                ? `Grade: ${model.active.grade ?? '—'}`
                : `Status: ${model.active?.status ?? '—'}${model.active?.late ? ' · Late' : ''}`}
            </div>
          </div>
          {model.active?.status === 'GRADED' ? (
            <div className="card" style={{ marginTop: 12, padding: 12 }}>
              <div style={{ fontWeight: 850 }}>Faculty feedback</div>
              <div style={{ marginTop: 6, whiteSpace: 'pre-wrap', fontSize: 13 }}>{model.active.feedback || 'No feedback provided.'}</div>
            </div>
          ) : null}
        </div>
      </div>
    </div>
  )
}
