import { useState, useEffect } from 'react';
import { Link } from 'react-router-dom';
import complaintService from '../../services/complaintService';
import toast from 'react-hot-toast';
import { FontAwesomeIcon } from '@fortawesome/react-fontawesome';
import {
  faArrowLeft, faExclamationTriangle, faPaperPlane, faCamera,
  faClock, faSpinner, faCheckCircle, faTimesCircle, faPlus,
} from '@fortawesome/free-solid-svg-icons';

const CATEGORIES = [
  { value: 'PLUMBING', label: 'Plumbing', icon: '🔧' },
  { value: 'ELECTRICAL', label: 'Electrical', icon: '⚡' },
  { value: 'CLEANLINESS', label: 'Cleanliness', icon: '🧹' },
  { value: 'FURNITURE', label: 'Furniture', icon: '🪑' },
  { value: 'INTERNET', label: 'Internet', icon: '📶' },
  { value: 'NOISE', label: 'Noise', icon: '🔊' },
  { value: 'OTHER', label: 'Other', icon: '📋' },
];

const STATUS_BADGE = {
  PENDING: { bg: 'bg-warning text-dark', label: 'Pending' },
  IN_PROGRESS: { bg: 'bg-info text-white', label: 'In Progress' },
  RESOLVED: { bg: 'bg-success', label: 'Resolved' },
  REJECTED: { bg: 'bg-danger', label: 'Rejected' },
};

const Complaints = () => {
  const [complaints, setComplaints] = useState([]);
  const [loading, setLoading] = useState(true);
  const [showForm, setShowForm] = useState(false);
  const [submitting, setSubmitting] = useState(false);
  const [formData, setFormData] = useState({ category: '', description: '', photo: '' });
  const [photoPreview, setPhotoPreview] = useState(null);

  useEffect(() => {
    fetchComplaints();
  }, []);

  const fetchComplaints = async () => {
    try {
      const res = await complaintService.getMyComplaints();
      setComplaints(res.data || []);
    } catch {
      toast.error('Failed to load complaints');
    } finally {
      setLoading(false);
    }
  };

  const handlePhotoUpload = (e) => {
    const file = e.target.files[0];
    if (!file) return;
    if (file.size > 2 * 1024 * 1024) { toast.error('Photo must be under 2MB'); return; }
    const reader = new FileReader();
    reader.onloadend = () => {
      setPhotoPreview(reader.result);
      setFormData({ ...formData, photo: reader.result });
    };
    reader.readAsDataURL(file);
  };

  const handleSubmit = async (e) => {
    e.preventDefault();
    if (!formData.category) { toast.error('Select a category'); return; }
    if (!formData.description.trim()) { toast.error('Enter a description'); return; }

    setSubmitting(true);
    try {
      await complaintService.create(formData);
      toast.success('Complaint submitted!');
      setFormData({ category: '', description: '', photo: '' });
      setPhotoPreview(null);
      setShowForm(false);
      fetchComplaints();
    } catch {
      toast.error('Failed to submit complaint');
    } finally {
      setSubmitting(false);
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
      <div className="d-flex align-items-center justify-content-between mb-4">
        <div className="d-flex align-items-center">
          <Link to="/student/dashboard" className="btn btn-outline-secondary me-3">
            <FontAwesomeIcon icon={faArrowLeft} />
          </Link>
          <h2 className="mb-0 fw-bold" style={{ color: 'var(--color-primary)' }}>
            <FontAwesomeIcon icon={faExclamationTriangle} /> Complaints
          </h2>
        </div>
        <button className="btn btn-primary" onClick={() => setShowForm(!showForm)}>
          <FontAwesomeIcon icon={faPlus} /> New Complaint
        </button>
      </div>

      {showForm && (
        <div className="card shadow-sm mb-4">
          <div className="card-header bg-primary text-white">
            <h5 className="mb-0">File a Complaint</h5>
          </div>
          <div className="card-body">
            <form onSubmit={handleSubmit}>
              <div className="mb-3">
                <label className="form-label fw-semibold">Category *</label>
                <div className="row g-2">
                  {CATEGORIES.map(cat => (
                    <div className="col-4 col-md-3 col-lg" key={cat.value}>
                      <div
                        className={`card text-center p-2 ${formData.category === cat.value ? 'border-primary bg-primary bg-opacity-10' : ''}`}
                        style={{ cursor: 'pointer' }}
                        onClick={() => setFormData({ ...formData, category: cat.value })}
                      >
                        <div style={{ fontSize: '1.5rem' }}>{cat.icon}</div>
                        <small className="fw-semibold">{cat.label}</small>
                      </div>
                    </div>
                  ))}
                </div>
              </div>

              <div className="mb-3">
                <label className="form-label fw-semibold">Description *</label>
                <textarea
                  className="form-control"
                  rows="4"
                  value={formData.description}
                  onChange={(e) => setFormData({ ...formData, description: e.target.value })}
                  placeholder="Describe the issue in detail..."
                  maxLength={1000}
                />
                <small className="text-muted">{formData.description.length}/1000</small>
              </div>

              <div className="mb-3">
                <label className="form-label fw-semibold">Photo (optional)</label>
                <div className="d-flex align-items-center gap-3">
                  <button type="button" className="btn btn-outline-secondary" onClick={() => document.getElementById('complaintPhoto').click()}>
                    <FontAwesomeIcon icon={faCamera} /> Upload Photo
                  </button>
                  <input type="file" id="complaintPhoto" accept="image/*" onChange={handlePhotoUpload} style={{ display: 'none' }} />
                  {photoPreview && (
                    <div style={{ position: 'relative' }}>
                      <img src={photoPreview} alt="Preview" style={{ width: 80, height: 80, objectFit: 'cover', borderRadius: 8 }} />
                      <button type="button" className="btn btn-sm btn-danger" style={{ position: 'absolute', top: -8, right: -8, borderRadius: '50%', width: 24, height: 24, padding: 0, fontSize: '0.7rem' }}
                        onClick={() => { setPhotoPreview(null); setFormData({ ...formData, photo: '' }); }}>X</button>
                    </div>
                  )}
                </div>
              </div>

              <button type="submit" className="btn btn-primary" disabled={submitting}>
                {submitting ? <FontAwesomeIcon icon={faSpinner} spin /> : <FontAwesomeIcon icon={faPaperPlane} />} Submit Complaint
              </button>
            </form>
          </div>
        </div>
      )}

      <div className="card shadow-sm">
        <div className="card-header d-flex justify-content-between align-items-center">
          <h5 className="mb-0">My Complaints</h5>
          <span className="badge bg-primary">{complaints.length} total</span>
        </div>
        <div className="card-body">
          {complaints.length === 0 ? (
            <p className="text-center text-muted py-4">No complaints filed yet</p>
          ) : (
            <div className="d-flex flex-column gap-3">
              {complaints.map(c => (
                <div key={c.id} className="card">
                  <div className="card-body">
                    <div className="d-flex justify-content-between align-items-start mb-2">
                      <div>
                        <span className="badge bg-secondary me-2">{CATEGORIES.find(cat => cat.value === c.category)?.icon} {c.category}</span>
                        <span className={`badge ${STATUS_BADGE[c.status]?.bg}`}>{STATUS_BADGE[c.status]?.label}</span>
                      </div>
                      <small className="text-muted"><FontAwesomeIcon icon={faClock} /> {c.createdAt}</small>
                    </div>
                    <p className="mb-2">{c.description}</p>
                    {c.photo && (
                      <img src={c.photo} alt="Complaint" style={{ maxWidth: 200, maxHeight: 150, objectFit: 'cover', borderRadius: 8, marginBottom: 8 }} />
                    )}
                    {c.wardenResponse && (
                      <div className="alert alert-info py-2 mb-0 mt-2">
                        <strong>Warden Response:</strong> {c.wardenResponse}
                        {c.respondedAt && <small className="d-block text-muted mt-1">{c.respondedAt}</small>}
                      </div>
                    )}
                  </div>
                </div>
              ))}
            </div>
          )}
        </div>
      </div>
    </div>
  );
};

export default Complaints;
