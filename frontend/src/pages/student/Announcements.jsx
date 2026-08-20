import { useState, useEffect } from 'react';
import { Link } from 'react-router-dom';
import outpassService from '../../services/outpassService';
import toast from 'react-hot-toast';
import { FontAwesomeIcon } from '@fortawesome/react-fontawesome';
import { faBullhorn, faArrowLeft, faExclamationCircle } from '@fortawesome/free-solid-svg-icons';
import { format } from 'date-fns';

const StudentAnnouncements = () => {
  const [announcements, setAnnouncements] = useState([]);
  const [loading, setLoading] = useState(true);

  useEffect(() => {
    const fetch = async () => {
      try {
        const res = await outpassService.getStudentAnnouncements();
        setAnnouncements(res.data || []);
      } catch {
        toast.error('Failed to load announcements');
      } finally {
        setLoading(false);
      }
    };
    fetch();
  }, []);

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
        <div className="col-12 d-flex align-items-center">
          <Link to="/student/dashboard" className="btn btn-outline-secondary btn-sm me-3">
            <FontAwesomeIcon icon={faArrowLeft} />
          </Link>
          <h2 className="mb-0" style={{ fontWeight: '700', color: 'var(--color-primary)' }}>
            <FontAwesomeIcon icon={faBullhorn} /> Announcements
          </h2>
        </div>
      </div>

      {announcements.length === 0 ? (
        <div className="card">
          <div className="card-body text-center py-5">
            <FontAwesomeIcon icon={faBullhorn} style={{ fontSize: '3rem', color: 'var(--color-text-muted)' }} />
            <h4 className="text-muted mt-3">No announcements</h4>
            <p>Check back later for updates from your warden</p>
          </div>
        </div>
      ) : (
        announcements.map(a => (
          <div key={a.id} className={`card shadow-sm mb-3 ${a.priority === 'URGENT' ? 'border-danger' : ''}`}>
            <div className="card-body">
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
          </div>
        ))
      )}
    </div>
  );
};

export default StudentAnnouncements;
