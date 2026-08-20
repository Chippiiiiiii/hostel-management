import { useState, useEffect } from 'react';
import { Link } from 'react-router-dom';
import outpassService from '../../services/outpassService';
import toast from 'react-hot-toast';
import { FontAwesomeIcon } from '@fortawesome/react-fontawesome';
import { faBullhorn, faArrowLeft, faPlus, faTrash, faExclamationCircle } from '@fortawesome/free-solid-svg-icons';
import { format } from 'date-fns';

const WardenAnnouncements = () => {
  const [announcements, setAnnouncements] = useState([]);
  const [loading, setLoading] = useState(true);
  const [showForm, setShowForm] = useState(false);
  const [formData, setFormData] = useState({ title: '', content: '', priority: 'NORMAL' });
  const [submitting, setSubmitting] = useState(false);

  useEffect(() => {
    fetchAnnouncements();
  }, []);

  const fetchAnnouncements = async () => {
    try {
      const res = await outpassService.getWardenAnnouncements();
      setAnnouncements(res.data || []);
    } catch {
      toast.error('Failed to load announcements');
    } finally {
      setLoading(false);
    }
  };

  const handleSubmit = async (e) => {
    e.preventDefault();
    if (!formData.title.trim() || !formData.content.trim()) {
      toast.error('Title and content are required');
      return;
    }
    setSubmitting(true);
    try {
      await outpassService.createAnnouncement(formData);
      toast.success('Announcement posted!');
      setFormData({ title: '', content: '', priority: 'NORMAL' });
      setShowForm(false);
      fetchAnnouncements();
    } catch {
      toast.error('Failed to post announcement');
    } finally {
      setSubmitting(false);
    }
  };

  const handleDelete = async (id) => {
    if (!confirm('Delete this announcement?')) return;
    try {
      await outpassService.deleteAnnouncement(id);
      toast.success('Announcement deleted');
      setAnnouncements(prev => prev.filter(a => a.id !== id));
    } catch {
      toast.error('Failed to delete');
    }
  };

  const getPriorityBadge = (priority) => {
    if (priority === 'URGENT') return 'bg-danger';
    if (priority === 'IMPORTANT') return 'bg-warning text-dark';
    return 'bg-info';
  };

  if (loading) {
    return (
      <div className="d-flex justify-content-center align-items-center" style={{ minHeight: '60vh' }}>
        <div className="spinner-border text-primary" role="status" />
      </div>
    );
  }

  return (
    <div className="container mt-4 mb-5">
      <div className="row mb-4">
        <div className="col-12 d-flex align-items-center justify-content-between">
          <div className="d-flex align-items-center">
            <Link to="/warden/dashboard" className="btn btn-outline-secondary btn-sm me-3">
              <FontAwesomeIcon icon={faArrowLeft} />
            </Link>
            <h2 className="mb-0" style={{ fontWeight: '700', color: 'var(--color-primary)' }}>
              <FontAwesomeIcon icon={faBullhorn} /> Announcements
            </h2>
          </div>
          <button className="btn btn-primary" onClick={() => setShowForm(!showForm)}>
            <FontAwesomeIcon icon={faPlus} /> New
          </button>
        </div>
      </div>

      {showForm && (
        <div className="row mb-4">
          <div className="col-12">
            <div className="card shadow-sm border-primary">
              <div className="card-body">
                <form onSubmit={handleSubmit}>
                  <div className="mb-3">
                    <label className="form-label fw-semibold">Title</label>
                    <input type="text" className="form-control" value={formData.title}
                      onChange={e => setFormData({ ...formData, title: e.target.value })}
                      placeholder="Announcement title" required maxLength={200} />
                  </div>
                  <div className="mb-3">
                    <label className="form-label fw-semibold">Content</label>
                    <textarea className="form-control" rows={4} value={formData.content}
                      onChange={e => setFormData({ ...formData, content: e.target.value })}
                      placeholder="Write your announcement..." required maxLength={2000} />
                  </div>
                  <div className="mb-3">
                    <label className="form-label fw-semibold">Priority</label>
                    <select className="form-select" value={formData.priority}
                      onChange={e => setFormData({ ...formData, priority: e.target.value })}>
                      <option value="NORMAL">Normal</option>
                      <option value="IMPORTANT">Important</option>
                      <option value="URGENT">Urgent</option>
                    </select>
                  </div>
                  <div className="d-flex gap-2">
                    <button type="submit" className="btn btn-primary" disabled={submitting}>
                      {submitting ? <span className="spinner-border spinner-border-sm" /> : 'Post Announcement'}
                    </button>
                    <button type="button" className="btn btn-outline-secondary" onClick={() => setShowForm(false)}>
                      Cancel
                    </button>
                  </div>
                </form>
              </div>
            </div>
          </div>
        </div>
      )}

      {announcements.length === 0 ? (
        <div className="card">
          <div className="card-body text-center py-5">
            <FontAwesomeIcon icon={faBullhorn} style={{ fontSize: '3rem', color: 'var(--color-text-muted)' }} />
            <h4 className="text-muted mt-3">No announcements yet</h4>
            <p>Post your first announcement to notify students</p>
          </div>
        </div>
      ) : (
        announcements.map(a => (
          <div key={a.id} className="card shadow-sm mb-3">
            <div className="card-body">
              <div className="d-flex justify-content-between align-items-start">
                <div>
                  <h5 className="mb-1">
                    {a.priority === 'URGENT' && <FontAwesomeIcon icon={faExclamationCircle} className="text-danger me-2" />}
                    {a.title}
                    <span className={`badge ${getPriorityBadge(a.priority)} ms-2`} style={{ fontSize: '0.7rem' }}>
                      {a.priority}
                    </span>
                  </h5>
                  <p className="mb-2" style={{ whiteSpace: 'pre-wrap' }}>{a.content}</p>
                  <small className="text-muted">
                    Posted by {a.postedByName} &bull; {a.createdAt ? format(new Date(a.createdAt), 'dd MMM yyyy, hh:mm a') : ''}
                  </small>
                </div>
                <button className="btn btn-outline-danger btn-sm" onClick={() => handleDelete(a.id)}>
                  <FontAwesomeIcon icon={faTrash} />
                </button>
              </div>
            </div>
          </div>
        ))
      )}
    </div>
  );
};

export default WardenAnnouncements;
