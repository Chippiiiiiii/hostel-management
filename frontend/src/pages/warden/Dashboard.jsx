import { useState, useEffect } from 'react';
import { useNavigate } from 'react-router-dom';
import outpassService from '../../services/outpassService';
import LoadingSpinner from '../../components/common/LoadingSpinner';
import toast from 'react-hot-toast';
import { FontAwesomeIcon } from '@fortawesome/react-fontawesome';
import { faUserTie, faArrowRight, faDoorOpen, faCalendarCheck, faUsers, faHourglass, faCheckCircle, faTimesCircle, faClipboardList, faExclamationTriangle, faUserGraduate, faBuilding, faBell, faBullhorn } from '@fortawesome/free-solid-svg-icons';

const WardenDashboard = () => {
  const [stats, setStats] = useState({ pending: 0, total: 0, approved: 0, declined: 0 });
  const [dashStats, setDashStats] = useState({ totalStudents: 0, allocatedStudents: 0, todayAttendance: 0, pendingComplaints: 0 });
  const [loading, setLoading] = useState(true);
  const navigate = useNavigate();

  useEffect(() => {
    fetchStats();
  }, []);

  const fetchStats = async () => {
    try {
      const [pendingRes, historyRes, dashRes] = await Promise.all([
        outpassService.getPendingOutpasses(),
        outpassService.getWardenHistory(),
        outpassService.getWardenDashboardStats().catch(() => ({ data: {} })),
      ]);

      const history = historyRes.data;
      setStats({
        pending: pendingRes.data.length,
        total: history.length,
        approved: history.filter(o => o.status === 'APPROVED' || o.status === 'COMPLETED').length,
        declined: history.filter(o => o.status === 'DECLINED').length,
      });
      if (dashRes.data) setDashStats(dashRes.data);
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

      {/* Overview Stats */}
      <div className="row mb-4 g-4">
        <div className="col-6 col-md-3">
          <div className="card shadow h-100">
            <div className="card-body text-center py-4">
              <FontAwesomeIcon icon={faUserGraduate} style={{ fontSize: '2rem', color: '#805ad5', marginBottom: '0.75rem' }} />
              <p className="text-muted mb-1" style={{ fontSize: '0.95rem' }}>Total Students</p>
              <h2 className="mb-0 fw-bold" style={{ color: '#805ad5', fontSize: '2.5rem' }}>{dashStats.totalStudents}</h2>
            </div>
          </div>
        </div>
        <div className="col-6 col-md-3">
          <div className="card shadow h-100">
            <div className="card-body text-center py-4">
              <FontAwesomeIcon icon={faBuilding} style={{ fontSize: '2rem', color: '#38b2ac', marginBottom: '0.75rem' }} />
              <p className="text-muted mb-1" style={{ fontSize: '0.95rem' }}>Rooms Allocated</p>
              <h2 className="mb-0 fw-bold" style={{ color: '#38b2ac', fontSize: '2.5rem' }}>{dashStats.allocatedStudents}</h2>
            </div>
          </div>
        </div>
        <div className="col-6 col-md-3">
          <div className="card shadow h-100">
            <div className="card-body text-center py-4">
              <FontAwesomeIcon icon={faCalendarCheck} style={{ fontSize: '2rem', color: '#48bb78', marginBottom: '0.75rem' }} />
              <p className="text-muted mb-1" style={{ fontSize: '0.95rem' }}>Today's Attendance</p>
              <h2 className="mb-0 fw-bold" style={{ color: '#48bb78', fontSize: '2.5rem' }}>{dashStats.todayAttendance}</h2>
            </div>
          </div>
        </div>
        <div className="col-6 col-md-3">
          <div className="card shadow h-100">
            <div className="card-body text-center py-4">
              <FontAwesomeIcon icon={faBell} style={{ fontSize: '2rem', color: '#e53e3e', marginBottom: '0.75rem' }} />
              <p className="text-muted mb-1" style={{ fontSize: '0.95rem' }}>Pending Complaints</p>
              <h2 className="mb-0 fw-bold" style={{ color: '#e53e3e', fontSize: '2.5rem' }}>{dashStats.pendingComplaints}</h2>
            </div>
          </div>
        </div>
      </div>

      {/* Outpass Stats */}
      <div className="row mb-5 g-4">
        <div className="col-6 col-md-3">
          <div className="card shadow h-100">
            <div className="card-body text-center py-4">
              <FontAwesomeIcon icon={faClipboardList} style={{ fontSize: '2rem', color: '#4299e1', marginBottom: '0.75rem' }} />
              <p className="text-muted mb-1" style={{ fontSize: '0.95rem' }}>Total Outpasses</p>
              <h2 className="mb-0 fw-bold" style={{ color: '#4299e1', fontSize: '2.5rem' }}>{stats.total}</h2>
            </div>
          </div>
        </div>
        <div className="col-6 col-md-3">
          <div className="card shadow h-100">
            <div className="card-body text-center py-4">
              <FontAwesomeIcon icon={faHourglass} style={{ fontSize: '2rem', color: '#ecc94b', marginBottom: '0.75rem' }} />
              <p className="text-muted mb-1" style={{ fontSize: '0.95rem' }}>Pending</p>
              <h2 className="mb-0 fw-bold" style={{ color: '#ecc94b', fontSize: '2.5rem' }}>{stats.pending}</h2>
            </div>
          </div>
        </div>
        <div className="col-6 col-md-3">
          <div className="card shadow h-100">
            <div className="card-body text-center py-4">
              <FontAwesomeIcon icon={faCheckCircle} style={{ fontSize: '2rem', color: '#48bb78', marginBottom: '0.75rem' }} />
              <p className="text-muted mb-1" style={{ fontSize: '0.95rem' }}>Approved</p>
              <h2 className="mb-0 fw-bold" style={{ color: '#48bb78', fontSize: '2.5rem' }}>{stats.approved}</h2>
            </div>
          </div>
        </div>
        <div className="col-6 col-md-3">
          <div className="card shadow h-100">
            <div className="card-body text-center py-4">
              <FontAwesomeIcon icon={faTimesCircle} style={{ fontSize: '2rem', color: '#e53e3e', marginBottom: '0.75rem' }} />
              <p className="text-muted mb-1" style={{ fontSize: '0.95rem' }}>Declined</p>
              <h2 className="mb-0 fw-bold" style={{ color: '#e53e3e', fontSize: '2.5rem' }}>{stats.declined}</h2>
            </div>
          </div>
        </div>
      </div>

      {/* Outpass, Attendance & Students Cards */}
      <div className="row g-4">
        <div className="col-md-4">
          <div className="card shadow h-100" style={{ cursor: 'pointer', transition: 'transform 0.2s' }} onClick={() => navigate('/warden/outpass')}
            onMouseEnter={e => e.currentTarget.style.transform = 'translateY(-4px)'}
            onMouseLeave={e => e.currentTarget.style.transform = 'translateY(0)'}>
            <div className="card-body text-center py-5 px-4">
              <div style={{ width: '80px', height: '80px', borderRadius: '50%', backgroundColor: 'rgba(66,153,225,0.1)', display: 'flex', alignItems: 'center', justifyContent: 'center', margin: '0 auto 1.25rem' }}>
                <FontAwesomeIcon icon={faDoorOpen} style={{ fontSize: '2.5rem', color: 'var(--color-primary)' }} />
              </div>
              <h3 className="fw-bold mb-2" style={{ color: 'var(--color-primary)' }}>Outpass</h3>
              <p className="text-muted mb-3" style={{ fontSize: '1rem' }}>Review & manage requests</p>
              <FontAwesomeIcon icon={faArrowRight} className="text-muted" style={{ fontSize: '1.25rem' }} />
            </div>
          </div>
        </div>
        <div className="col-md-4">
          <div className="card shadow h-100" style={{ cursor: 'pointer', transition: 'transform 0.2s' }} onClick={() => navigate('/warden/attendance')}
            onMouseEnter={e => e.currentTarget.style.transform = 'translateY(-4px)'}
            onMouseLeave={e => e.currentTarget.style.transform = 'translateY(0)'}>
            <div className="card-body text-center py-5 px-4">
              <div style={{ width: '80px', height: '80px', borderRadius: '50%', backgroundColor: 'rgba(66,153,225,0.1)', display: 'flex', alignItems: 'center', justifyContent: 'center', margin: '0 auto 1.25rem' }}>
                <FontAwesomeIcon icon={faCalendarCheck} style={{ fontSize: '2.5rem', color: 'var(--color-primary)' }} />
              </div>
              <h3 className="fw-bold mb-2" style={{ color: 'var(--color-primary)' }}>Attendance</h3>
              <p className="text-muted mb-3" style={{ fontSize: '1rem' }}>Start & manage attendance</p>
              <FontAwesomeIcon icon={faArrowRight} className="text-muted" style={{ fontSize: '1.25rem' }} />
            </div>
          </div>
        </div>
        <div className="col-md-4">
          <div className="card shadow h-100" style={{ cursor: 'pointer', transition: 'transform 0.2s' }} onClick={() => navigate('/warden/students')}
            onMouseEnter={e => e.currentTarget.style.transform = 'translateY(-4px)'}
            onMouseLeave={e => e.currentTarget.style.transform = 'translateY(0)'}>
            <div className="card-body text-center py-5 px-4">
              <div style={{ width: '80px', height: '80px', borderRadius: '50%', backgroundColor: 'rgba(66,153,225,0.1)', display: 'flex', alignItems: 'center', justifyContent: 'center', margin: '0 auto 1.25rem' }}>
                <FontAwesomeIcon icon={faUsers} style={{ fontSize: '2.5rem', color: 'var(--color-primary)' }} />
              </div>
              <h3 className="fw-bold mb-2" style={{ color: 'var(--color-primary)' }}>Students</h3>
              <p className="text-muted mb-3" style={{ fontSize: '1rem' }}>View rooms & student info</p>
              <FontAwesomeIcon icon={faArrowRight} className="text-muted" style={{ fontSize: '1.25rem' }} />
            </div>
          </div>
        </div>
        <div className="col-md-4">
          <div className="card shadow h-100" style={{ cursor: 'pointer', transition: 'transform 0.2s' }} onClick={() => navigate('/warden/complaints')}
            onMouseEnter={e => e.currentTarget.style.transform = 'translateY(-4px)'}
            onMouseLeave={e => e.currentTarget.style.transform = 'translateY(0)'}>
            <div className="card-body text-center py-5 px-4">
              <div style={{ width: '80px', height: '80px', borderRadius: '50%', backgroundColor: 'rgba(229,62,62,0.1)', display: 'flex', alignItems: 'center', justifyContent: 'center', margin: '0 auto 1.25rem' }}>
                <FontAwesomeIcon icon={faExclamationTriangle} style={{ fontSize: '2.5rem', color: '#e53e3e' }} />
              </div>
              <h3 className="fw-bold mb-2" style={{ color: '#e53e3e' }}>Complaints</h3>
              <p className="text-muted mb-3" style={{ fontSize: '1rem' }}>Review & respond to complaints</p>
              <FontAwesomeIcon icon={faArrowRight} className="text-muted" style={{ fontSize: '1.25rem' }} />
            </div>
          </div>
        </div>
        <div className="col-md-4">
          <div className="card shadow h-100" style={{ cursor: 'pointer', transition: 'transform 0.2s' }} onClick={() => navigate('/warden/announcements')}
            onMouseEnter={e => e.currentTarget.style.transform = 'translateY(-4px)'}
            onMouseLeave={e => e.currentTarget.style.transform = 'translateY(0)'}>
            <div className="card-body text-center py-5 px-4">
              <div style={{ width: '80px', height: '80px', borderRadius: '50%', backgroundColor: 'rgba(128,90,213,0.1)', display: 'flex', alignItems: 'center', justifyContent: 'center', margin: '0 auto 1.25rem' }}>
                <FontAwesomeIcon icon={faBullhorn} style={{ fontSize: '2.5rem', color: '#805ad5' }} />
              </div>
              <h3 className="fw-bold mb-2" style={{ color: '#805ad5' }}>Announcements</h3>
              <p className="text-muted mb-3" style={{ fontSize: '1rem' }}>Post notices for students</p>
              <FontAwesomeIcon icon={faArrowRight} className="text-muted" style={{ fontSize: '1.25rem' }} />
            </div>
          </div>
        </div>
      </div>
    </div>
  );
};

export default WardenDashboard;
