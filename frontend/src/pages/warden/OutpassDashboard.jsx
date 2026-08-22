import { useState, useEffect } from 'react';
import { Link } from 'react-router-dom';
import outpassService from '../../services/outpassService';
import LoadingSpinner from '../../components/common/LoadingSpinner';
import toast from 'react-hot-toast';
import { FontAwesomeIcon } from '@fortawesome/react-fontawesome';
import { faDoorOpen, faArrowLeft, faHourglass, faClipboardList, faCheckCircle, faTimesCircle, faArrowRight, faSearch } from '@fortawesome/free-solid-svg-icons';

const WardenOutpassDashboard = () => {
  const [stats, setStats] = useState({ pending: 0, total: 0, approved: 0, declined: 0 });
  const [loading, setLoading] = useState(true);

  useEffect(() => {
    fetchStats();
  }, []);

  const fetchStats = async () => {
    try {
      const [pendingRes, historyRes] = await Promise.all([
        outpassService.getPendingOutpasses(),
        outpassService.getWardenHistory(),
      ]);

      const history = historyRes.data;
      setStats({
        pending: pendingRes.data.length,
        total: history.length,
        approved: history.filter(o => o.status === 'APPROVED' || o.status === 'COMPLETED').length,
        declined: history.filter(o => o.status === 'DECLINED').length,
      });
    } catch (error) {
      toast.error('Failed to load outpass data');
    } finally {
      setLoading(false);
    }
  };

  if (loading) return <LoadingSpinner />;

  return (
    <div className="container mt-4 mb-5">
      {/* Header */}
      <div className="row mb-4">
        <div className="col-12">
          <div className="d-flex align-items-center justify-content-between mb-3">
            <div className="d-flex align-items-center">
              <Link to="/warden/dashboard" className="btn btn-outline-secondary me-3">
                <FontAwesomeIcon icon={faArrowLeft} />
              </Link>
              <div>
                <h2 className="mb-0" style={{ fontWeight: '700', color: 'var(--color-primary)' }}>
                  <FontAwesomeIcon icon={faDoorOpen} /> Outpass
                </h2>
              </div>
            </div>
            <div className="d-flex gap-2">
              <Link to="/warden/pending" className="btn btn-primary" style={{ fontWeight: '600' }}>
                <FontAwesomeIcon icon={faSearch} /> Pending Requests
              </Link>
            </div>
          </div>
        </div>
      </div>

      {/* Stats */}
      <div className="row mb-4 g-4">
        <div className="col-md-3">
          <div className="card text-center h-100 shadow-sm" style={{ backgroundColor: 'var(--color-warning)', border: 'none' }}>
            <div className="card-body d-flex flex-column justify-content-center p-4">
              <h1 className="display-2 fw-bold" style={{ color: '#fff', marginBottom: '1rem' }}>{stats.pending}</h1>
              <h5 className="mb-3 fw-bold" style={{ color: '#fff' }}>Pending</h5>
              <Link to="/warden/pending" className="btn btn-light mt-2 fw-semibold">
                <FontAwesomeIcon icon={faSearch} /> Review
              </Link>
            </div>
          </div>
        </div>
        <div className="col-md-3">
          <div className="card text-center h-100 shadow-sm" style={{ backgroundColor: 'var(--color-info)', border: 'none' }}>
            <div className="card-body d-flex flex-column justify-content-center p-4">
              <h1 className="display-2 fw-bold" style={{ color: '#fff', marginBottom: '1rem' }}>{stats.total}</h1>
              <h5 className="mb-0 fw-bold" style={{ color: '#fff' }}>Total Processed</h5>
            </div>
          </div>
        </div>
        <div className="col-md-3">
          <div className="card text-center h-100 shadow-sm" style={{ backgroundColor: 'var(--color-success)', border: 'none' }}>
            <div className="card-body d-flex flex-column justify-content-center p-4">
              <h1 className="display-2 fw-bold" style={{ color: '#fff', marginBottom: '1rem' }}>{stats.approved}</h1>
              <h5 className="mb-0 fw-bold" style={{ color: '#fff' }}>Approved</h5>
            </div>
          </div>
        </div>
        <div className="col-md-3">
          <div className="card text-center h-100 shadow-sm" style={{ backgroundColor: 'var(--color-danger)', border: 'none' }}>
            <div className="card-body d-flex flex-column justify-content-center p-4">
              <h1 className="display-2 fw-bold" style={{ color: '#fff', marginBottom: '1rem' }}>{stats.declined}</h1>
              <h5 className="mb-0 fw-bold" style={{ color: '#fff' }}>Declined</h5>
            </div>
          </div>
        </div>
      </div>

      {/* Info */}
      <div className="row">
        <div className="col-12">
          <div className="card shadow-sm">
            <div className="card-header">
              <h5 className="mb-0"><FontAwesomeIcon icon={faClipboardList} /> Outpass Management</h5>
            </div>
            <div className="card-body">
              <ul>
                <li>Review and approve/decline student outpass requests</li>
                <li>Monitor pending requests regularly</li>
                <li>Ensure all requests have valid information</li>
                <li>Contact students or parents if needed</li>
              </ul>
              {stats.pending > 0 && (
                <div className="alert alert-warning mb-0">
                  <strong>Note:</strong> You have {stats.pending} pending request{stats.pending !== 1 ? 's' : ''} waiting for your review.
                </div>
              )}
            </div>
          </div>
        </div>
      </div>
    </div>
  );
};

export default WardenOutpassDashboard;
