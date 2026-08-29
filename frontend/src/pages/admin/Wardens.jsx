import { useState, useEffect } from 'react';
import { Link } from 'react-router-dom';
import adminService from '../../services/adminService';
import roomService from '../../services/roomService';
import LoadingSpinner from '../../components/common/LoadingSpinner';
import toast from 'react-hot-toast';
import { FontAwesomeIcon } from '@fortawesome/react-fontawesome';
import { faArrowLeft, faUserTie, faPlus, faCheck, faBan } from '@fortawesome/free-solid-svg-icons';

const emptyForm = { name: '', email: '', password: '', hostel: '', phone: '' };

const Wardens = () => {
  const [wardens, setWardens] = useState([]);
  const [buildings, setBuildings] = useState([]);
  const [loading, setLoading] = useState(true);
  const [showForm, setShowForm] = useState(false);
  const [form, setForm] = useState(emptyForm);
  const [submitting, setSubmitting] = useState(false);

  useEffect(() => {
    fetchData();
  }, []);

  const fetchData = async () => {
    try {
      const [{ data: wardenData }, { data: buildingData }] = await Promise.all([
        adminService.getWardens(),
        roomService.getBuildings(),
      ]);
      setWardens(wardenData);
      setBuildings(buildingData);
    } catch {
      toast.error('Failed to load wardens');
    } finally {
      setLoading(false);
    }
  };

  const handleChange = (e) => setForm({ ...form, [e.target.name]: e.target.value });

  const handleCreate = async (e) => {
    e.preventDefault();
    setSubmitting(true);
    try {
      await adminService.createWarden(form);
      toast.success('Warden created');
      setForm(emptyForm);
      setShowForm(false);
      fetchData();
    } catch (error) {
      toast.error(error.response?.data?.message || 'Failed to create warden');
    } finally {
      setSubmitting(false);
    }
  };

  const handleToggleStatus = async (warden) => {
    try {
      await adminService.setWardenStatus(warden.id, !warden.enabled);
      toast.success(warden.enabled ? 'Warden disabled' : 'Warden enabled');
      fetchData();
    } catch {
      toast.error('Failed to update status');
    }
  };

  if (loading) return <LoadingSpinner />;

  return (
    <div className="container mt-4 mb-5">
      <div className="row mb-4">
        <div className="col-12">
          <div className="d-flex align-items-center justify-content-between mb-3">
            <div className="d-flex align-items-center">
              <Link to="/admin/dashboard" className="btn btn-outline-secondary me-3">
                <FontAwesomeIcon icon={faArrowLeft} />
              </Link>
              <h2 className="mb-0" style={{ fontWeight: '700', color: 'var(--color-primary)' }}>
                <FontAwesomeIcon icon={faUserTie} /> Wardens
              </h2>
            </div>
            <button className="btn btn-primary btn-sm" onClick={() => setShowForm(!showForm)}>
              <FontAwesomeIcon icon={faPlus} /> Add Warden
            </button>
          </div>
        </div>
      </div>

      {showForm && (
        <div className="row mb-4">
          <div className="col-12">
            <div className="card shadow-sm border-primary">
              <div className="card-body">
                <h6 className="fw-bold mb-3">Create Warden</h6>
                <form onSubmit={handleCreate}>
                  <div className="row g-3">
                    <div className="col-md-4">
                      <label className="form-label">Name *</label>
                      <input type="text" className="form-control" name="name" value={form.name} onChange={handleChange} required />
                    </div>
                    <div className="col-md-4">
                      <label className="form-label">Email *</label>
                      <input type="email" className="form-control" name="email" value={form.email} onChange={handleChange} required />
                    </div>
                    <div className="col-md-4">
                      <label className="form-label">Password *</label>
                      <input type="password" className="form-control" name="password" value={form.password} onChange={handleChange} minLength={6} required />
                    </div>
                    <div className="col-md-4">
                      <label className="form-label">Hostel</label>
                      <select className="form-select" name="hostel" value={form.hostel} onChange={handleChange}>
                        <option value="">Select a building</option>
                        {buildings.map(b => (
                          <option key={b.id} value={b.name}>{b.name}</option>
                        ))}
                      </select>
                    </div>
                    <div className="col-md-4">
                      <label className="form-label">Phone</label>
                      <input type="tel" className="form-control" name="phone" value={form.phone} onChange={handleChange} placeholder="10 digits" maxLength={10} />
                    </div>
                  </div>
                  <div className="mt-3 d-flex gap-2">
                    <button type="submit" className="btn btn-primary" disabled={submitting}>
                      {submitting ? 'Creating...' : 'Create Warden'}
                    </button>
                    <button type="button" className="btn btn-secondary" onClick={() => setShowForm(false)}>Cancel</button>
                  </div>
                </form>
              </div>
            </div>
          </div>
        </div>
      )}

      <div className="row">
        <div className="col-12">
          <div className="card shadow-sm">
            <div className="table-responsive">
              <table className="table table-hover mb-0">
                <thead className="table-light">
                  <tr>
                    <th>Name</th>
                    <th>Email</th>
                    <th>Hostel</th>
                    <th>Phone</th>
                    <th>Status</th>
                    <th>Action</th>
                  </tr>
                </thead>
                <tbody>
                  {wardens.length === 0 ? (
                    <tr><td colSpan={6} className="text-center text-muted py-4">No wardens yet</td></tr>
                  ) : (
                    wardens.map(w => (
                      <tr key={w.id}>
                        <td>{w.name}</td>
                        <td>{w.email}</td>
                        <td>{w.hostel || '-'}</td>
                        <td>{w.phone || '-'}</td>
                        <td>
                          <span className={`badge ${w.enabled ? 'bg-success' : 'bg-secondary'}`}>
                            {w.enabled ? 'Enabled' : 'Disabled'}
                          </span>
                        </td>
                        <td>
                          <button
                            className={`btn btn-sm ${w.enabled ? 'btn-outline-danger' : 'btn-outline-success'}`}
                            onClick={() => handleToggleStatus(w)}
                          >
                            <FontAwesomeIcon icon={w.enabled ? faBan : faCheck} /> {w.enabled ? 'Disable' : 'Enable'}
                          </button>
                        </td>
                      </tr>
                    ))
                  )}
                </tbody>
              </table>
            </div>
          </div>
        </div>
      </div>
    </div>
  );
};

export default Wardens;
