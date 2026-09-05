import { useState, useEffect } from 'react';
import { Link, useNavigate } from 'react-router-dom';
import outpassService from '../../services/outpassService';
import LoadingSpinner from '../../components/common/LoadingSpinner';
import toast from 'react-hot-toast';
import { FontAwesomeIcon } from '@fortawesome/react-fontawesome';
import {
  faUserTie, faHourglass, faCheckCircle, faTimesCircle, faClipboardList,
  faArrowRight, faCalendarCheck, faUsers, faExclamationTriangle,
} from '@fortawesome/free-solid-svg-icons';

const WardenDashboard = () => {
  const [stats, setStats] = useState({ pending: 0, total: 0, approved: 0, declined: 0 });
  const [loading, setLoading] = useState(true);
  const navigate = useNavigate();

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
      toast.error('Failed to load dashboard data');
    } finally {
      setLoading(false);
    }
  };

  if (loading) return <LoadingSpinner />;

  const pendingHeadline = stats.pending === 0
    ? 'No requests are waiting'
    : `${stats.pending} ${stats.pending === 1 ? 'request is' : 'requests are'} waiting`;

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

      {/* Needs Your Attention + Daily Operations */}
      <div className="row g-4">
        <div className="col-12 col-lg-8">
          <div className="card shadow h-100">
            <div className="card-body p-4 d-flex flex-column">
              <p
                className="mb-2 fw-bold"
                style={{ color: 'var(--accent-yellow)', fontSize: '0.8rem', letterSpacing: '0.08em' }}
              >
                NEEDS YOUR ATTENTION
              </p>
              <h3 className="mb-3 fw-bold" style={{ color: 'var(--color-primary)' }}>
                {pendingHeadline}
              </h3>
              <p className="mb-4 text-muted" style={{ fontSize: '1rem' }}>
                Open the queue to check dates, contact details, and the student history before deciding.
              </p>
              <div className="mt-auto text-end">
                <button
                  className="btn fw-semibold"
                  style={{ backgroundColor: 'var(--accent-yellow)', color: 'var(--color-primary-dark)' }}
                  onClick={() => navigate('/warden/pending')}
                >
                  Open review queue <FontAwesomeIcon icon={faArrowRight} />
                </button>
              </div>
            </div>
          </div>
        </div>

        <div className="col-12 col-lg-4">
          <div className="card shadow h-100">
            <div className="card-header fw-bold">Daily operations</div>
            <div className="list-group list-group-flush">
              <Link
                to="/warden/attendance"
                className="list-group-item list-group-item-action d-flex justify-content-between align-items-center"
              >
                <span><FontAwesomeIcon icon={faCalendarCheck} className="me-2" style={{ color: 'var(--color-info)' }} /> Attendance session</span>
                <FontAwesomeIcon icon={faArrowRight} className="text-muted" />
              </Link>
              <Link
                to="/warden/students"
                className="list-group-item list-group-item-action d-flex justify-content-between align-items-center"
              >
                <span><FontAwesomeIcon icon={faUsers} className="me-2" style={{ color: 'var(--color-info)' }} /> Find a student</span>
                <FontAwesomeIcon icon={faArrowRight} className="text-muted" />
              </Link>
              <Link
                to="/warden/complaints"
                className="list-group-item list-group-item-action d-flex justify-content-between align-items-center"
              >
                <span><FontAwesomeIcon icon={faExclamationTriangle} className="me-2" style={{ color: 'var(--color-info)' }} /> Open complaints</span>
                <FontAwesomeIcon icon={faArrowRight} className="text-muted" />
              </Link>
            </div>
          </div>
        </div>
      </div>

    </div>
  );
};

export default WardenDashboard;
