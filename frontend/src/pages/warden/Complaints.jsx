import { useState, useEffect } from 'react';
import { Link } from 'react-router-dom';
import complaintService from '../../services/complaintService';
import toast from 'react-hot-toast';
import { FontAwesomeIcon } from '@fortawesome/react-fontawesome';
import {
  faArrowLeft, faExclamationTriangle, faClock, faCheckCircle,
  faTimesCircle, faSpinner, faReply, faFilter,
} from '@fortawesome/free-solid-svg-icons';

const CATEGORIES = {
  PLUMBING: { label: 'Plumbing', icon: '🔧' },
  ELECTRICAL: { label: 'Electrical', icon: '⚡' },
  CLEANLINESS: { label: 'Cleanliness', icon: '🧹' },
  FURNITURE: { label: 'Furniture', icon: '🪑' },
  INTERNET: { label: 'Internet', icon: '📶' },
  NOISE: { label: 'Noise', icon: '🔊' },
  OTHER: { label: 'Other', icon: '📋' },
};

const STATUS_CONFIG = {
  PENDING: { bg: 'bg-warning text-dark', label: 'Pending', icon: faClock },
  IN_PROGRESS: { bg: 'bg-info text-white', label: 'In Progress', icon: faSpinner },
  RESOLVED: { bg: 'bg-success', label: 'Resolved', icon: faCheckCircle },
  REJECTED: { bg: 'bg-danger', label: 'Rejected', icon: faTimesCircle },
};

const WardenComplaints = () => {
  const [complaints, setComplaints] = useState([]);
  const [stats, setStats] = useState({ total: 0, pending: 0, inProgress: 0, resolved: 0, rejected: 0 });
  const [loading, setLoading] = useState(true);
  const [filter, setFilter] = useState('');
  const [respondingTo, setRespondingTo] = useState(null);
  const [responseText, setResponseText] = useState('');
  const [newStatus, setNewStatus] = useState('');
  const [updating, setUpdating] = useState(false);

  useEffect(() => {
    fetchData();
  }, [filter]);

  const fetchData = async () => {
    try {
      const [complaintsRes, statsRes] = await Promise.all([
        complaintService.getAllComplaints(filter),
        complaintService.getStats(),
      ]);
      setComplaints(complaintsRes.data || []);
      setStats(statsRes.data || {});
    } catch {
      toast.error('Failed to load complaints');
    } finally {
      setLoading(false);
    }
  };

  const handleRespond = (complaint) => {
    setRespondingTo(complaint.id);
    setResponseText(complaint.wardenResponse || '');
    setNewStatus(complaint.status);
  };

  const handleSubmitResponse = async (complaintId) => {
    if (!newStatus) { toast.error('Select a status'); return; }
    setUpdating(true);
    try {
      await complaintService.updateComplaint(complaintId, {
        status: newStatus,
        wardenResponse: responseText,
      });
      toast.success('Complaint updated');
      setRespondingTo(null);
      setResponseText('');
      setNewStatus('');
      fetchData();
    } catch {
      toast.error('Failed to update complaint');
    } finally {
      setUpdating(false);
    }
  };

  if (loading) {
    return (
      <div className="d-flex justify-content-center align-items-center" style={{ minHeight: '60vh' }}>
        <div className="spinner-border text-primary"><span className="visually-hidden">Loading...</span></div>
      </div>
    );
  }

  return (
    <div className="container mt-4 mb-5">
      <div className="d-flex align-items-center mb-4">
        <Link to="/warden/dashboard" className="btn btn-outline-secondary me-3">
          <FontAwesomeIcon icon={faArrowLeft} />
        </Link>
        <h2 className="mb-0 fw-bold" style={{ color: 'var(--color-primary)' }}>
          <FontAwesomeIcon icon={faExclamationTriangle} /> Complaints
        </h2>
      </div>

      {/* Stats */}
      <div className="row mb-4 g-3">
        {[
          { label: 'Total', value: stats.total, color: 'var(--color-info)' },
          { label: 'Pending', value: stats.pending, color: 'var(--accent-yellow)' },
          { label: 'In Progress', value: stats.inProgress, color: 'var(--accent-teal-light)' },
          { label: 'Resolved', value: stats.resolved, color: 'var(--color-success)' },
          { label: 'Rejected', value: stats.rejected, color: 'var(--accent-red)' },
        ].map(s => (
          <div className="col-6 col-md" key={s.label}>
            <div className="card shadow-sm">
              <div className="card-body text-center py-2">
                <small className="text-muted">{s.label}</small>
                <h4 className="mb-0 fw-bold" style={{ color: s.color }}>{s.value}</h4>
              </div>
            </div>
          </div>
        ))}
      </div>

      {/* Filter */}
      <div className="d-flex gap-2 mb-4 flex-wrap">
        <FontAwesomeIcon icon={faFilter} className="align-self-center text-muted" />
        {['', 'PENDING', 'IN_PROGRESS', 'RESOLVED', 'REJECTED'].map(f => (
          <button
            key={f}
            className={`btn btn-sm ${filter === f ? 'btn-primary' : 'btn-outline-secondary'}`}
            onClick={() => setFilter(f)}
          >
            {f === '' ? 'All' : STATUS_CONFIG[f]?.label}
          </button>
        ))}
      </div>

      {/* Complaints List */}
      {complaints.length === 0 ? (
        <div className="card shadow-sm">
          <div className="card-body text-center py-5 text-muted">No complaints found</div>
        </div>
      ) : (
        <div className="d-flex flex-column gap-3">
          {complaints.map(c => (
            <div key={c.id} className="card shadow-sm">
              <div className="card-body">
                <div className="d-flex justify-content-between align-items-start mb-2">
                  <div>
                    <span className="badge bg-secondary me-1">{CATEGORIES[c.category]?.icon} {c.category}</span>
                    <span className={`badge ${STATUS_CONFIG[c.status]?.bg}`}>
                      <FontAwesomeIcon icon={STATUS_CONFIG[c.status]?.icon} /> {STATUS_CONFIG[c.status]?.label}
                    </span>
                  </div>
                  <small className="text-muted">{c.createdAt}</small>
                </div>

                <div className="row mb-2">
                  <div className="col-auto">
                    <small className="text-muted">Student:</small> <strong>{c.studentName}</strong> ({c.studentRollNo})
                  </div>
                  <div className="col-auto">
                    <small className="text-muted">Hostel:</small> <strong>{c.hostel}</strong> - Room {c.roomNumber}
                  </div>
                </div>

                <p className="mb-2">{c.description}</p>

                {c.photo && (
                  <img src={c.photo} alt="Complaint" style={{ maxWidth: 200, maxHeight: 150, objectFit: 'cover', borderRadius: 8, marginBottom: 8 }} />
                )}

                {c.wardenResponse && respondingTo !== c.id && (
                  <div className="alert alert-info py-2 mb-2">
                    <strong>Your Response:</strong> {c.wardenResponse}
                  </div>
                )}

                {respondingTo === c.id ? (
                  <div className="border rounded p-3 mt-2" style={{ backgroundColor: 'var(--color-bg-tertiary)' }}>
                    <div className="mb-2">
                      <label className="form-label fw-semibold">Update Status</label>
                      <div className="d-flex gap-2 flex-wrap">
                        {['PENDING', 'IN_PROGRESS', 'RESOLVED', 'REJECTED'].map(s => (
                          <button
                            key={s}
                            className={`btn btn-sm ${newStatus === s ? STATUS_CONFIG[s].bg : 'btn-outline-secondary'}`}
                            onClick={() => setNewStatus(s)}
                          >
                            {STATUS_CONFIG[s].label}
                          </button>
                        ))}
                      </div>
                    </div>
                    <div className="mb-2">
                      <label className="form-label fw-semibold">Response to Student</label>
                      <textarea
                        className="form-control"
                        rows="2"
                        value={responseText}
                        onChange={(e) => setResponseText(e.target.value)}
                        placeholder="Write your response..."
                        maxLength={1000}
                      />
                    </div>
                    <div className="d-flex gap-2">
                      <button className="btn btn-primary btn-sm" onClick={() => handleSubmitResponse(c.id)} disabled={updating}>
                        {updating ? <FontAwesomeIcon icon={faSpinner} spin /> : 'Save'}
                      </button>
                      <button className="btn btn-outline-secondary btn-sm" onClick={() => setRespondingTo(null)}>Cancel</button>
                    </div>
                  </div>
                ) : (
                  <button className="btn btn-outline-primary btn-sm mt-1" onClick={() => handleRespond(c)}>
                    <FontAwesomeIcon icon={faReply} /> Respond
                  </button>
                )}
              </div>
            </div>
          ))}
        </div>
      )}
    </div>
  );
};

export default WardenComplaints;
