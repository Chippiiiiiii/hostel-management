import { useState, useEffect } from 'react';
import { Link, useNavigate } from 'react-router-dom';
import outpassService from '../../services/outpassService';
import LoadingSpinner from '../../components/common/LoadingSpinner';
import toast from 'react-hot-toast';
import { FontAwesomeIcon } from '@fortawesome/react-fontawesome';
import { faChartBar, faUser, faPencilAlt, faArrowRight, faDoorOpen, faCalendarCheck, faClock, faCheckCircle, faTimesCircle, faHourglass, faClipboardList, faBell, faExclamationTriangle } from '@fortawesome/free-solid-svg-icons';
import useAttendanceAlert from '../../hooks/useAttendanceAlert';

const StudentDashboard = () => {
  const [profile, setProfile] = useState(null);
  const [recentOutpasses, setRecentOutpasses] = useState([]);
  const [loading, setLoading] = useState(true);
  const navigate = useNavigate();
  const { showAlert, activeSession, dismissAlert } = useAttendanceAlert();

  useEffect(() => {
    fetchData();
  }, []);

  const fetchData = async () => {
    try {
      const [profileRes, outpassRes] = await Promise.all([
        outpassService.getStudentProfile(),
        outpassService.getOutpassHistory(),
      ]);
      setProfile(profileRes.data);
      setRecentOutpasses(outpassRes.data.slice(0, 3));
    } catch (error) {
      console.error('Error fetching data:', error);
      toast.error('Failed to load dashboard data');
    } finally {
      setLoading(false);
    }
  };

  const getStatusBadge = (status) => {
    const badges = {
      PENDING: 'bg-warning',
      APPROVED: 'bg-success',
      DECLINED: 'bg-danger',
      DEPARTED: 'bg-primary',
      COMPLETED: 'bg-info',
      OVERDUE: 'bg-danger',
      EXPIRED: 'bg-secondary',
    };
    return badges[status] || 'bg-secondary';
  };

  if (loading) return <LoadingSpinner />;

  return (
    <div className="container mt-4 mb-5">
      <div className="row mb-4">
        <div className="col-12">
          <h2 className="mb-1" style={{ fontWeight: '700', fontSize: '1.8rem', color: 'var(--color-primary)' }}>
            <FontAwesomeIcon icon={faChartBar} /> Student Dashboard
          </h2>
          <p className="text-muted" style={{ fontSize: '1rem' }}>Welcome back!</p>
        </div>
      </div>

      {/* Attendance Alert */}
      {(showAlert || activeSession) && (
        <div className="row mb-4">
          <div className="col-12">
            <div className="alert alert-warning d-flex align-items-center justify-content-between mb-0" role="alert">
              <div>
                <FontAwesomeIcon icon={faBell} className="me-2" />
                <strong>Attendance is open!</strong> Mark your attendance now.
              </div>
              <div>
                <button className="btn btn-warning btn-sm fw-semibold me-2" onClick={() => navigate('/student/attendance')}>
                  Mark Attendance
                </button>
                {showAlert && (
                  <button type="button" className="btn-close" onClick={dismissAlert} aria-label="Close"></button>
                )}
              </div>
            </div>
          </div>
        </div>
      )}

      {/* Profile Card */}
      {profile && (
        <div className="row mb-4">
          <div className="col-md-12">
            <div className="card shadow-sm">
              <div className="card-body p-4">
                <div className="d-flex justify-content-between align-items-center mb-3">
                  <h5 className="card-title mb-0" style={{ color: 'var(--color-primary)', fontWeight: '600' }}>
                    <FontAwesomeIcon icon={faUser} /> Profile Information
                  </h5>
                  <Link to="/student/edit-profile" className="btn btn-sm btn-outline-primary">
                    <FontAwesomeIcon icon={faPencilAlt} /> Edit Profile
                  </Link>
                </div>
                <div className="d-flex">
                  {profile.profilePicture && (
                    <div className="me-4 flex-shrink-0">
                      <img
                        src={profile.profilePicture}
                        alt={profile.name}
                        style={{
                          width: '80px', height: '80px', borderRadius: '50%',
                          objectFit: 'cover', border: '3px solid var(--color-primary)',
                        }}
                      />
                    </div>
                  )}
                  <div className="row flex-grow-1">
                    <div className="col-md-3">
                      <p className="mb-2"><strong>Name:</strong> {profile.name}</p>
                      <p className="mb-2"><strong>Roll No:</strong> {profile.rollNo}</p>
                    </div>
                    <div className="col-md-3">
                      <p className="mb-2"><strong>Department:</strong> {profile.department}</p>
                      <p className="mb-2"><strong>Email:</strong> {profile.email}</p>
                    </div>
                    <div className="col-md-3">
                      <p className="mb-2"><strong>Hostel:</strong> {profile.hostel}</p>
                      <p className="mb-2"><strong>Room:</strong> {profile.roomNumber}</p>
                    </div>
                    <div className="col-md-3">
                      <p className="mb-2"><strong>Contact:</strong> {profile.contactNumber}</p>
                      <p className="mb-2"><strong>Parent:</strong> {profile.parentNumber}</p>
                    </div>
                  </div>
                </div>
              </div>
            </div>
          </div>
        </div>
      )}

      {/* Quick Stats */}
      <div className="row mb-4 g-4">
        <div className="col-6 col-md-3">
          <div className="card shadow-sm h-100">
            <div className="card-body py-3">
              <p className="text-muted mb-1"><FontAwesomeIcon icon={faClipboardList} style={{ color: '#4299e1' }} /> Total Outpasses</p>
              <h3 className="mb-0 fw-bold" style={{ color: '#4299e1', fontSize: '1.75rem' }}>{recentOutpasses.length}</h3>
            </div>
          </div>
        </div>
        <div className="col-6 col-md-3">
          <div className="card shadow-sm h-100">
            <div className="card-body py-3">
              <p className="text-muted mb-1"><FontAwesomeIcon icon={faHourglass} style={{ color: '#ecc94b' }} /> Pending</p>
              <h3 className="mb-0 fw-bold" style={{ color: '#ecc94b', fontSize: '1.75rem' }}>{recentOutpasses.filter(o => o.status === 'PENDING').length}</h3>
            </div>
          </div>
        </div>
        <div className="col-6 col-md-3">
          <div className="card shadow-sm h-100">
            <div className="card-body py-3">
              <p className="text-muted mb-1"><FontAwesomeIcon icon={faCheckCircle} style={{ color: '#48bb78' }} /> Approved</p>
              <h3 className="mb-0 fw-bold" style={{ color: '#48bb78', fontSize: '1.75rem' }}>{recentOutpasses.filter(o => o.status === 'APPROVED' || o.status === 'COMPLETED').length}</h3>
            </div>
          </div>
        </div>
        <div className="col-6 col-md-3">
          <div className="card shadow-sm h-100">
            <div className="card-body py-3">
              <p className="text-muted mb-1"><FontAwesomeIcon icon={faTimesCircle} style={{ color: '#e53e3e' }} /> Declined</p>
              <h3 className="mb-0 fw-bold" style={{ color: '#e53e3e', fontSize: '1.75rem' }}>{recentOutpasses.filter(o => o.status === 'DECLINED').length}</h3>
            </div>
          </div>
        </div>
      </div>

      {/* Outpass & Attendance */}
      <div className="row mb-4 g-4">
        <div className="col-md-6">
          <div className="card shadow-sm h-100" style={{ cursor: 'pointer', transition: 'transform 0.2s' }} onClick={() => navigate('/student/outpass')}
            onMouseEnter={e => e.currentTarget.style.transform = 'translateY(-3px)'}
            onMouseLeave={e => e.currentTarget.style.transform = 'translateY(0)'}>
            <div className="card-body d-flex justify-content-between align-items-center" style={{ padding: '1.75rem' }}>
              <div className="d-flex align-items-center">
                <FontAwesomeIcon icon={faDoorOpen} style={{ fontSize: '2.75rem', color: 'var(--color-primary)', marginRight: '1.25rem' }} />
                <div>
                  <h4 className="mb-1 fw-bold" style={{ color: 'var(--color-primary)' }}>Outpass</h4>
                  <p className="text-muted mb-0">Create & manage outpass requests</p>
                </div>
              </div>
              <FontAwesomeIcon icon={faArrowRight} className="text-muted" style={{ fontSize: '1.25rem' }} />
            </div>
          </div>
        </div>
        <div className="col-md-6">
          <div className="card shadow-sm h-100" style={{ cursor: 'pointer', transition: 'transform 0.2s' }} onClick={() => navigate('/student/attendance')}
            onMouseEnter={e => e.currentTarget.style.transform = 'translateY(-3px)'}
            onMouseLeave={e => e.currentTarget.style.transform = 'translateY(0)'}>
            <div className="card-body d-flex justify-content-between align-items-center" style={{ padding: '1.75rem' }}>
              <div className="d-flex align-items-center">
                <FontAwesomeIcon icon={faCalendarCheck} style={{ fontSize: '2.75rem', color: 'var(--color-primary)', marginRight: '1.25rem' }} />
                <div>
                  <h4 className="mb-1 fw-bold" style={{ color: 'var(--color-primary)' }}>Attendance</h4>
                  <p className="text-muted mb-0">View your attendance records</p>
                </div>
              </div>
              <FontAwesomeIcon icon={faArrowRight} className="text-muted" style={{ fontSize: '1.25rem' }} />
            </div>
          </div>
        </div>
      </div>

      {/* Complaints Card */}
      <div className="row mb-4 g-4">
        <div className="col-md-6">
          <div className="card shadow-sm h-100" style={{ cursor: 'pointer', transition: 'transform 0.2s' }} onClick={() => navigate('/student/complaints')}
            onMouseEnter={e => e.currentTarget.style.transform = 'translateY(-3px)'}
            onMouseLeave={e => e.currentTarget.style.transform = 'translateY(0)'}>
            <div className="card-body d-flex justify-content-between align-items-center" style={{ padding: '1.75rem' }}>
              <div className="d-flex align-items-center">
                <FontAwesomeIcon icon={faExclamationTriangle} style={{ fontSize: '2.75rem', color: '#e53e3e', marginRight: '1.25rem' }} />
                <div>
                  <h4 className="mb-1 fw-bold" style={{ color: '#e53e3e' }}>Complaints</h4>
                  <p className="text-muted mb-0">File & track complaints</p>
                </div>
              </div>
              <FontAwesomeIcon icon={faArrowRight} className="text-muted" style={{ fontSize: '1.25rem' }} />
            </div>
          </div>
        </div>
      </div>

      {/* Recent Activity */}
      {recentOutpasses.length > 0 && (
        <div className="row">
          <div className="col-12">
            <div className="card shadow-sm">
              <div className="card-header d-flex justify-content-between align-items-center">
                <h5 className="mb-0">
                  <FontAwesomeIcon icon={faClock} /> Recent Activity
                </h5>
                <Link to="/student/outpass" className="btn btn-sm btn-primary">
                  View All <FontAwesomeIcon icon={faArrowRight} />
                </Link>
              </div>
              <div className="card-body">
                <div className="table-responsive">
                  <table className="table table-hover">
                    <thead>
                      <tr>
                        <th>ID</th>
                        <th>Place of Visit</th>
                        <th>Status</th>
                      </tr>
                    </thead>
                    <tbody>
                      {recentOutpasses.map((outpass) => (
                        <tr key={outpass.id}>
                          <td>{outpass.id}</td>
                          <td>{outpass.placeOfVisit}</td>
                          <td>
                            <span className={`badge ${getStatusBadge(outpass.status)}`}>
                              {outpass.status}
                            </span>
                          </td>
                        </tr>
                      ))}
                    </tbody>
                  </table>
                </div>
              </div>
            </div>
          </div>
        </div>
      )}
    </div>
  );
};

export default StudentDashboard;
