import { useState, useEffect } from 'react';
import outpassService from '../../services/outpassService';
import LoadingSpinner from '../../components/common/LoadingSpinner';
import toast from 'react-hot-toast';
import { FontAwesomeIcon } from '@fortawesome/react-fontawesome';
import { faUserTie, faHourglass, faCheckCircle, faTimesCircle, faClipboardList } from '@fortawesome/free-solid-svg-icons';

const WardenDashboard = () => {
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
      console.error('Error fetching stats:', error);
      toast.error('Failed to load dashboard data');
    } finally {
      setLoading(false);
    }
  };

  if (loading) return <LoadingSpinner />;

  return (
    <div className="container-lg mt-5 mb-5" style={{ maxWidth: '1200px' }}>
      <div className="row mb-5">
        <div className="col-12">
          <h1 className="mb-2" style={{ fontWeight: '800', fontSize: '2.5rem', color: 'var(--color-primary)' }}>
            <FontAwesomeIcon icon={faUserTie} /> Warden Dashboard
          </h1>
          <p className="text-muted" style={{ fontSize: '1.1rem' }}>Welcome back!</p>
        </div>
      </div>

      {/* Outpass Stats */}
      <div className="row mb-5 g-4">
        <div className="col-6 col-md-3">
          <div className="card shadow h-100">
            <div className="card-body text-center py-4">
              <FontAwesomeIcon icon={faClipboardList} style={{ fontSize: '2rem', color: 'var(--color-info)', marginBottom: '0.75rem' }} />
              <p className="text-muted mb-1" style={{ fontSize: '0.95rem' }}>Total Outpasses</p>
              <h2 className="mb-0 fw-bold" style={{ color: 'var(--color-info)', fontSize: '2.5rem' }}>{stats.total}</h2>
            </div>
          </div>
        </div>
        <div className="col-6 col-md-3">
          <div className="card shadow h-100">
            <div className="card-body text-center py-4">
              <FontAwesomeIcon icon={faHourglass} style={{ fontSize: '2rem', color: 'var(--accent-yellow)', marginBottom: '0.75rem' }} />
              <p className="text-muted mb-1" style={{ fontSize: '0.95rem' }}>Pending</p>
              <h2 className="mb-0 fw-bold" style={{ color: 'var(--accent-yellow)', fontSize: '2.5rem' }}>{stats.pending}</h2>
            </div>
          </div>
        </div>
        <div className="col-6 col-md-3">
          <div className="card shadow h-100">
            <div className="card-body text-center py-4">
              <FontAwesomeIcon icon={faCheckCircle} style={{ fontSize: '2rem', color: 'var(--color-success)', marginBottom: '0.75rem' }} />
              <p className="text-muted mb-1" style={{ fontSize: '0.95rem' }}>Approved</p>
              <h2 className="mb-0 fw-bold" style={{ color: 'var(--color-success)', fontSize: '2.5rem' }}>{stats.approved}</h2>
            </div>
          </div>
        </div>
        <div className="col-6 col-md-3">
          <div className="card shadow h-100">
            <div className="card-body text-center py-4">
              <FontAwesomeIcon icon={faTimesCircle} style={{ fontSize: '2rem', color: 'var(--accent-red)', marginBottom: '0.75rem' }} />
              <p className="text-muted mb-1" style={{ fontSize: '0.95rem' }}>Declined</p>
              <h2 className="mb-0 fw-bold" style={{ color: 'var(--accent-red)', fontSize: '2.5rem' }}>{stats.declined}</h2>
            </div>
          </div>
        </div>
      </div>

    </div>
  );
};

export default WardenDashboard;
