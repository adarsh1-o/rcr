import { useMemo, useEffect, useState } from 'react'
import { useNavigate } from 'react-router-dom'
import { useAuth } from '../../context/AuthContext'
import {
  enrollStudent,
  getCourses,
  getEnrollmentsByStudent,
  getProgressByStudent,
  markCourseOpened,
  ensureArray,
} from '../../services/api'

function CourseCard({ course, progress, onEnroll, onOpen, enrolled, busy }) {
  const primaryUrl = course.primaryUrl

  return (
    <div className="card" style={{ padding: 14 }}>
      <div style={{ display: 'flex', justifyContent: 'space-between', gap: 12, alignItems: 'flex-start' }}>
        <div>
          <div
            style={{
              width: 46,
              height: 46,
              borderRadius: 14,
              background: 'rgba(124,58,237,.12)',
              border: '1px solid rgba(124,58,237,.35)',
              display: 'flex',
              alignItems: 'center',
              justifyContent: 'center',
              fontWeight: 950,
            }}
          >
            C
          </div>
          <div style={{ fontWeight: 950, fontSize: 16, marginTop: 10 }}>{course.title}</div>
          <div style={{ fontSize: 12, opacity: 0.75, marginTop: 4 }}>Faculty: {course.facultyName}</div>
        </div>

        <div style={{ textAlign: 'right' }}>
          {progress != null ? <><div className="badge" style={{ opacity: 0.95 }}>{progress}% complete</div><div style={{ fontSize: 12, opacity: 0.75, marginTop: 6 }}>Progress</div></> : null}
        </div>
      </div>

      <div style={{ marginTop: 12, fontSize: 13, opacity: 0.82, lineHeight: 1.55 }}>{course.description}</div>

      <div style={{ display: 'flex', gap: 10, marginTop: 14, flexWrap: 'wrap' }}>
        <button className="btn" onClick={() => onEnroll(course.id)} disabled={enrolled || busy}>
          {enrolled ? 'Enrolled' : busy ? 'Enrolling…' : 'Enroll'}
        </button>
        {enrolled && primaryUrl ? (
          <a className="btn btnPrimary" href={primaryUrl} target="_blank" rel="noreferrer" onClick={() => onOpen(course.id)}>
            Open Course
          </a>
        ) : (
          <button className="btn btnPrimary" disabled={!enrolled || !primaryUrl}>
            Open Course
          </button>
        )}
      </div>
    </div>
  )
}

export default function StudentCoursesAvailable() {
  const { user } = useAuth()
  const navigate = useNavigate()
  const studentId = user?.userId ?? user?.id

  const [query, setQuery] = useState('')
  const [model, setModel] = useState({ courses: [], enrolledCourseIds: new Set() })
  const [loadError, setLoadError] = useState(null)
  const [loading, setLoading] = useState(true)
  const [busyCourseId, setBusyCourseId] = useState(null)
  const [actionError, setActionError] = useState('')
  const [reloadKey, setReloadKey] = useState(0)

  useEffect(() => {
    let mounted = true

    async function load() {
      if (!studentId) return

      setLoadError(null)
      setLoading(true)

      try {
        let coursesRaw = ensureArray(await getCourses())

        let enrolledCourseIds = new Set()
        try {
          const enrolled = await getEnrollmentsByStudent(studentId)
          enrolledCourseIds = new Set(ensureArray(enrolled).map((e) => String(e.courseId)))
        } catch {
          // non-fatal
        }

        let progressRecords = []
        try {
          progressRecords = ensureArray(await getProgressByStudent(studentId))
        } catch {
          progressRecords = []
        }

        const courses = []
        for (const c of coursesRaw) {
          const progress = progressRecords.find((p) => String(p.courseId) === String(c.id))
          const completion = progress?.progressPercent ?? null

          courses.push({
            id: c.id,
            title: c.title,
            description: c.description,
            facultyName: c.facultyName ?? 'Faculty',
            completion: progress ? completion : null,
            learningLinks: ensureArray(c.learningLinks),
            primaryUrl: ensureArray(c.learningLinks)[0]?.url ?? null,
          })
        }

        if (mounted) setModel({ courses, enrolledCourseIds })
      } catch (err) {
        if (mounted) {
          setLoadError(err?.response?.data?.message ?? err?.message ?? 'Failed to load courses')
          setModel({ courses: [], enrolledCourseIds: new Set() })
        }
      } finally {
        if (mounted) setLoading(false)
      }
    }

    load()
    return () => {
      mounted = false
    }
  }, [studentId, reloadKey])

  const visibleCourses = useMemo(() => {
    const q = query.toLowerCase().trim()
    return ensureArray(model.courses).filter((c) => !q || (c.title || '').toLowerCase().includes(q))
  }, [model.courses, query])

  async function handleEnroll(courseId) {
    if (!studentId) return
    setBusyCourseId(String(courseId))
    setActionError('')
    try {
      await enrollStudent({ studentId, courseId })
      navigate('/student/courses/my')
    } catch (err) {
      setActionError(err?.response?.data?.message || 'Could not enroll in this course. Please try again.')
    } finally {
      setBusyCourseId(null)
    }
  }

  async function handleOpenCourse(courseId) {
    if (!studentId || !courseId) return
    try {
      await markCourseOpened({ studentId, courseId })
    } catch {
      // The learning link remains usable when tracking is temporarily unavailable.
    }
  }

  return (
    <div>
      <div className="card" style={{ padding: 16 }}>
        <div style={{ display: 'flex', justifyContent: 'space-between', gap: 12, alignItems: 'center', flexWrap: 'wrap' }}>
          <div>
            <div className="badge badgeAccent">Courses</div>
            <h1 style={{ fontSize: 26, marginTop: 10 }}>Available Courses</h1>
            <div style={{ opacity: 0.8, marginTop: 6, fontSize: 13 }}>Search and enroll to start learning.</div>
          </div>

          <div style={{ minWidth: 260, flex: '0 1 340px' }}>
            <input
              className="input"
              value={query}
              onChange={(e) => setQuery(e.target.value)}
              placeholder="Search courses..."
              aria-label="search courses"
              style={{ width: '100%' }}
            />
          </div>
        </div>

        {loadError ? (
          <div className="course-error" role="alert" style={{ marginTop: 14 }}>{loadError}<button className="btn btnSmall" onClick={() => setReloadKey((key) => key + 1)}>Retry</button></div>
        ) : null}
        {actionError ? <div className="course-error" role="alert" style={{ marginTop: 10 }}>{actionError}</div> : null}
      </div>

      <div className="grid" style={{ marginTop: 14, gridTemplateColumns: 'repeat(3, minmax(0, 1fr))', gap: 14 }}>
        {loading ? <div className="card" aria-live="polite" style={{ padding: 20, gridColumn: '1 / -1' }}>Loading available courses…</div> : visibleCourses.length ? (
          visibleCourses.map((course) => (
            <CourseCard
              key={course.id}
              course={course}
              progress={course.completion}
              enrolled={model.enrolledCourseIds.has(String(course.id))}
              onEnroll={(id) => { void handleEnroll(id) }}
              onOpen={handleOpenCourse}
              busy={busyCourseId === String(course.id)}
            />
          ))
        ) : (
          <div className="card" style={{ padding: 16, gridColumn: '1 / -1' }}>
            <div className="badge" style={{ opacity: 0.85 }}>
              {loadError ? 'Could not load courses.' : query ? 'No courses match your search. Try another name.' : 'No courses available yet. Ask faculty to create a course.'}
            </div>
          </div>
        )}
      </div>
    </div>
  )
}
