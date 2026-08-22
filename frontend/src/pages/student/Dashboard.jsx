import { useState, useEffect } from 'react';
import { Link, useNavigate } from 'react-router-dom';
import outpassService from '../../services/outpassService';
import attendanceService from '../../services/attendanceService';
import LoadingSpinner from '../../components/common/LoadingSpinner';
import toast from 'react-hot-toast';
import { FontAwesomeIcon } from '@fortawesome/react-fontawesome';
import { faChartBar, faUser, faPencilAlt, faArrowRight, faClock, faCheckCircle, faHourglass, faClipboardList, faBell, faPercentage, faUsers } from '@fortawesome/free-solid-svg-icons';
import useAttendanceAlert from '../../hooks/useAttendanceAlert';
import useOutpassNotifications from '../../hooks/useOutpassNotifications';

const StudentDashboard = () => {
  const [profile, setProfile] = useState(null);
  const [allOutpasses, setAllOutpasses] = useState([]);
  const [recentOutpasses, setRecentOutpasses] = useState([]);
  const [attendanceStats, setAttendanceStats] = useState({ total: 0, present: 0, absent: 0, percentage: 0 });
  const [roommates, setRoommates] = useState([]);
  const [loading, setLoading] = useState(true);
  const navigate = useNavigate();
  const { showAlert, activeSession, dismissAlert } = useAttendanceAlert();
  useOutpassNotifications(allOutpasses);

  useEffect(() => {
    fetchData();
  }, []);

  const fetchData = async () => {
    try {
      const [profileRes, outpassRes, attendanceRes, roommatesRes] = await Promise.all([
        outpassService.getStudentProfile(),
        outpassService.getOutpassHistory(),
        attendanceService.getStats(),
        outpassService.getRoommates().catch(() => ({ data: [] })),
      ]);
      setProfile(profileRes.data);
      setAllOutpasses(outpassRes.data);
      setRecentOutpasses(outpassRes.data.slice(0, 3));
      setAttendanceStats(attendanceRes.data);
      setRoommates(roommatesRes.data || []);
    } catch (error) {
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
              <p className="text-muted mb-1"><FontAwesomeIcon icon={faClipboardList} style={{ color: 'var(--color-info)' }} /> Total Outpasses</p>
              <h3 className="mb-0 fw-bold" style={{ color: 'var(--color-info)', fontSize: '1.75rem' }}>{recentOutpasses.length}</h3>
            </div>
          </div>
        </div>
        <div className="col-6 col-md-3">
          <div className="card shadow-sm h-100">
            <div className="card-body py-3">
              <p className="text-muted mb-1"><FontAwesomeIcon icon={faHourglass} style={{ color: 'var(--accent-yellow)' }} /> Pending</p>
              <h3 className="mb-0 fw-bold" style={{ color: 'var(--accent-yellow)', fontSize: '1.75rem' }}>{recentOutpasses.filter(o => o.status === 'PENDING').length}</h3>
            </div>
          </div>
        </div>
        <div className="col-6 col-md-3">
          <div className="card shadow-sm h-100">
            <div className="card-body py-3">
              <p className="text-muted mb-1"><FontAwesomeIcon icon={faCheckCircle} style={{ color: 'var(--color-success)' }} /> Approved</p>
              <h3 className="mb-0 fw-bold" style={{ color: 'var(--color-success)', fontSize: '1.75rem' }}>{recentOutpasses.filter(o => o.status === 'APPROVED' || o.status === 'COMPLETED').length}</h3>
            </div>
          </div>
        </div>
        <div className="col-6 col-md-3">
          <div className="card shadow-sm h-100">
            <div className="card-body py-3">
              <p className="text-muted mb-1"><FontAwesomeIcon icon={faPercentage} style={{ color: 'var(--accent-purple)' }} /> Attendance</p>
              <h3 className="mb-0 fw-bold" style={{ color: 'var(--accent-purple)', fontSize: '1.75rem' }}>{attendanceStats.percentage}%</h3>
            </div>
          </div>
        </div>
      </div>

      {/* Roommates */}
      {roommates.length > 0 && (
        <div className="row mb-4">
          <div className="col-12">
            <div className="card shadow-sm">
              <div className="card-header">
                <h5 className="mb-0">
                  <FontAwesomeIcon icon={faUsers} /> My Roommates
                </h5>
              </div>
              <div className="card-body p-0">
                <div className="table-responsive">
                  <table className="table table-hover mb-0">
                    <thead>
                      <tr>
                        <th>Name</th>
                        <th>Roll No</th>
                        <th>Department</th>
                      </tr>
                    </thead>
                    <tbody>
                      {roommates.map((mate) => (
                        <tr key={mate.id}>
                          <td>{mate.name}</td>
                          <td>{mate.rollNo}</td>
                          <td>{mate.department}</td>
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
