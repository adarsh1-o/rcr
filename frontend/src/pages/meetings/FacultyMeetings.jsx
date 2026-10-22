import { useState, useEffect, useCallback } from 'react'
import { Link, useNavigate } from 'react-router-dom'
import { getMyFacultyCourses, getMeetingsByCourse, createMeeting, ensureArray } from '../../services/api'
import { Video, Plus, Loader2 } from 'lucide-react'
import { toast } from 'sonner'
import { useForm } from 'react-hook-form'

export default function FacultyMeetings() {
  const navigate = useNavigate()
  const [courses, setCourses] = useState([])
  const [selectedCourseId, setSelectedCourseId] = useState('')
  const [meetings, setMeetings] = useState([])
  const [loading, setLoading] = useState(true)
  const [isCreating, setIsCreating] = useState(false)
  const [saving, setSaving] = useState(false)

  const { register, handleSubmit, reset } = useForm()

  const loadCourses = useCallback(async () => {
    try {
      const data = ensureArray(await getMyFacultyCourses())
      setCourses(data)
      if (data.length > 0) {
        setSelectedCourseId(data[0].id)
      }
    } catch (e) {
      toast.error(e?.response?.data?.message ?? 'Failed to load courses')
    } finally {
      setLoading(false)
    }
  }, [])

  const loadMeetings = useCallback(async (courseId) => {
    try {
      const data = await getMeetingsByCourse(courseId)
      setMeetings(ensureArray(data))
    } catch (e) {
      toast.error(e?.response?.data?.message ?? 'Failed to load meetings')
    }
  }, [])

  useEffect(() => {
    // Loading remote data is the synchronization this effect is responsible for.
    // eslint-disable-next-line react-hooks/set-state-in-effect
    void loadCourses()
  }, [loadCourses])

  useEffect(() => {
    // Loading remote data is the synchronization this effect is responsible for.
    // eslint-disable-next-line react-hooks/set-state-in-effect
    if (selectedCourseId) void loadMeetings(selectedCourseId)
  }, [selectedCourseId, loadMeetings])

  const onSubmit = async (data) => {
    if (!selectedCourseId) {
      toast.error('Create a course before scheduling a class')
      return
    }

    const scheduledStartTime = new Date(data.scheduledStartTime)
    if (Number.isNaN(scheduledStartTime.getTime()) || scheduledStartTime <= new Date()) {
      toast.error('Choose a future start time')
      return
    }

    setSaving(true)
    try {
      await createMeeting({
        title: data.title.trim(),
        description: data.description,
        courseId: selectedCourseId,
        scheduledStartTime: scheduledStartTime.toISOString()
      })
      toast.success('Meeting scheduled')
      setIsCreating(false)
      reset()
      await loadMeetings(selectedCourseId)
    } catch (e) {
      const validationErrors = e?.response?.data?.errors
      const detail = validationErrors
        ? Object.values(validationErrors).join('. ')
        : e?.response?.data?.message ?? e?.response?.data?.detail
      const status = e?.response?.status
      const message = detail
        || (status === 401 ? 'Please sign in again to schedule a class.'
          : status === 403 ? 'You can schedule classes only for courses you teach.'
            : status ? `Could not schedule the class (HTTP ${status}). Please try again.`
              : 'Could not reach the server. Check your connection and try again.')
      console.error('Live class scheduling failed', {
        status: status ?? 'network',
        code: e?.code ?? null,
        message: e?.message ?? null,
        response: e?.response?.data ?? null,
      })
      toast.error(message)
    } finally {
      setSaving(false)
    }
  }

  if (loading) return <div className="flex-center p-8"><Loader2 className="animate-spin" /></div>

  return (
    <div className="fade-in">
      <div className="flex items-center justify-between mb-6">
        <div>
          <h1 className="text-2xl font-bold">Live Classes (WebRTC)</h1>
          <p className="text-sm text-gray-400 mt-1">Schedule and manage live video meetings</p>
        </div>
        <button className="btn btn-primary flex items-center gap-2" onClick={() => setIsCreating(true)} disabled={!courses.length}>
          <Plus size={18} /> Schedule Class
        </button>
      </div>

      <div className="card p-4 mb-6">
        <label className="label">Select Course</label>
        <select
          className="input max-w-md"
          value={selectedCourseId}
          onChange={(e) => setSelectedCourseId(e.target.value)}
          disabled={!courses.length}
        >
          {courses.map(c => (
            <option key={c.id} value={c.id}>{c.title}</option>
          ))}
        </select>
      </div>

      {!courses.length && !loading && (
        <div className="card p-4 mb-6 text-gray-400">
          Create a course before scheduling a live class.{' '}
          <Link to="/faculty/courses" className="underline" style={{ color: 'var(--accent)' }}>Go to Manage Courses</Link>
        </div>
      )}

      {isCreating && (
        <div className="card p-6 mb-6">
          <h3 className="font-bold mb-4">Schedule New Meeting</h3>
          <form onSubmit={handleSubmit(onSubmit)} className="space-y-4 max-w-md">
            <div>
              <label className="label">Title</label>
              <input {...register('title', { required: true })} className="input" placeholder="e.g. Weekly Doubt Session" />
            </div>
            <div>
              <label className="label">Description</label>
              <textarea {...register('description')} className="input" rows="2" />
            </div>
            <div>
              <label className="label">Start Time</label>
              <input type="datetime-local" {...register('scheduledStartTime', { required: true })} className="input" />
            </div>
            <div className="flex gap-2">
              <button type="submit" className="btn btn-primary" disabled={saving || !selectedCourseId}>{saving ? 'Scheduling…' : 'Save Meeting'}</button>
              <button type="button" className="btn" style={{ background: '#333' }} onClick={() => setIsCreating(false)}>Cancel</button>
            </div>
          </form>
        </div>
      )}

      <div className="grid grid-cols-1 md:grid-cols-2 gap-4">
        {meetings.length === 0 ? (
          <div className="col-span-full p-8 text-center text-gray-400 border border-dashed border-gray-700 rounded-lg">
            No meetings scheduled for this course.
          </div>
        ) : (
          meetings.map(m => (
            <div key={m.id} className="card p-4 flex flex-col">
              <h3 className="font-bold text-lg">{m.title}</h3>
              <p className="text-sm text-gray-400 mt-1 mb-4">{new Date(m.scheduledStartTime).toLocaleString()}</p>
              <div className="mt-auto flex justify-between items-center">
                <span className={`text-xs px-2 py-1 rounded ${m.status === 'LIVE' ? 'bg-green-900 text-green-200' : m.status === 'SCHEDULED' ? 'bg-blue-900 text-blue-200' : 'bg-red-900 text-red-200'}`}>
                  {m.status}
                </span>
                {(m.status === 'SCHEDULED' || m.status === 'LIVE') && (
                  <button onClick={() => navigate(`/faculty/live/${m.id}`)} className="btn btn-primary py-1 px-3 text-sm flex items-center gap-2">
                    <Video size={14} /> {m.status === 'SCHEDULED' ? 'Open classroom' : 'Join class'}
                  </button>
                )}
              </div>
            </div>
          ))
        )}
      </div>
    </div>
  )
}
