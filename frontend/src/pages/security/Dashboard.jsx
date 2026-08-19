import { useState, useEffect } from 'react';
import outpassService from '../../services/outpassService';
import LoadingSpinner from '../../components/common/LoadingSpinner';
import toast from 'react-hot-toast';
import { format } from 'date-fns';
import { FontAwesomeIcon } from '@fortawesome/react-fontawesome';
import { faShieldAlt, faCheck, faDoorOpen, faCalendarDay, faSignOutAlt, faSignInAlt } from '@fortawesome/free-solid-svg-icons';

const SecurityDashboard = () => {
  const [approvedOutpasses, setApprovedOutpasses] = useState([]);
  const [departedOutpasses, setDepartedOutpasses] = useState([]);
  const [todayOutpasses, setTodayOutpasses] = useState([]);
  const [loading, setLoading] = useState(true);
  const [activeTab, setActiveTab] = useState('approved');

  useEffect(() => {
    fetchData();
  }, []);

  const fetchData = async () => {
    try {
      const [activeRes, departedRes, todayRes] = await Promise.all([
        outpassService.getActiveOutpasses(),
        outpassService.getDepartedOutpasses(),
        outpassService.getTodayOutpasses(),
      ]);
      
      setApprovedOutpasses(activeRes.data);
      setDepartedOutpasses(departedRes.data);
      setTodayOutpasses(todayRes.data);
    } catch (error) {
      console.error('Error fetching data:', error);
      toast.error('Failed to load dashboard data');
    } finally {
      setLoading(false);
    }
  };

  const handleMarkDeparture = async (id) => {
    if (!window.confirm('Confirm student departure?')) return;

    try {
      await outpassService.markDeparture(id);
      toast.success('Departure verified successfully!');
      fetchData(); // Refresh data
    } catch (error) {
      console.error('Error marking departure:', error);
      toast.error(error.response?.data?.message || 'Failed to mark departure');
    }
  };

  const handleMarkReturn = async (id) => {
    if (!window.confirm('Confirm student return?')) return;

    try {
      await outpassService.markReturn(id);
      toast.success('Return verified successfully!');
      fetchData(); // Refresh data
    } catch (error) {
      console.error('Error marking return:', error);
      toast.error(error.response?.data?.message || 'Failed to mark return');
    }
  };

  const getStatusBadge = (status) => {
    const badges = {
      PENDING: 'bg-warning text-dark',
      APPROVED: 'bg-success',
      DECLINED: 'bg-danger',
      DEPARTED: 'bg-primary',
      COMPLETED: 'bg-info',
      OVERDUE: 'bg-danger',
    };
    return badges[status] || 'bg-secondary';
  };

  if (loading) return <LoadingSpinner />;

  const getDisplayOutpasses = () => {
    switch (activeTab) {
      case 'approved':
        return approvedOutpasses;
      case 'departed':
        return departedOutpasses;
      case 'today':
        return todayOutpasses;
      default:
        return approvedOutpasses;
    }
  };

  const displayOutpasses = getDisplayOutpasses();

  return (
    <div className="container mt-4 mb-5">
      <div className="row mb-4">
        <div className="col-12">
          <h2 className="mb-1" style={{ fontWeight: '700', color: 'var(--color-primary)' }}>
            <FontAwesomeIcon icon={faShieldAlt} /> Security Guard Dashboard
          </h2>
          <p className="text-muted">Verify student entry and exit</p>
        </div>
      </div>

      {/* Statistics Cards */}
      <div className="row mb-4 g-4">
        <div className="col-md-4">
          <div className="card text-center h-100 shadow-sm" style={{ backgroundColor: '#48bb78', border: 'none' }}>
            <div className="card-body p-4">
              <h1 className="display-3" style={{ color: 'white', fontWeight: '700' }}>{approvedOutpasses.length}</h1>
              <h5 style={{ color: 'white', fontWeight: '600' }}>Approved - Ready to Exit</h5>
              <p className="mb-0" style={{ color: 'rgba(255, 255, 255, 0.9)' }}>Awaiting departure verification</p>
            </div>
          </div>
        </div>
        <div className="col-md-4">
          <div className="card text-center h-100 shadow-sm" style={{ backgroundColor: '#2d3748', border: 'none' }}>
            <div className="card-body p-4">
              <h1 className="display-3" style={{ color: 'white', fontWeight: '700' }}>{departedOutpasses.length}</h1>
              <h5 style={{ color: 'white', fontWeight: '600' }}>Departed - Outside</h5>
              <p className="mb-0" style={{ color: 'rgba(255, 255, 255, 0.9)' }}>Awaiting return verification</p>
            </div>
          </div>
        </div>
        <div className="col-md-4">
          <div className="card text-center h-100 shadow-sm" style={{ backgroundColor: '#4299e1', border: 'none' }}>
            <div className="card-body p-4">
              <h1 className="display-3" style={{ color: 'white', fontWeight: '700' }}>{todayOutpasses.length}</h1>
              <h5 style={{ color: 'white', fontWeight: '600' }}>Today's Schedule</h5>
              <p className="mb-0" style={{ color: 'rgba(255, 255, 255, 0.9)' }}>All outpasses for today</p>
            </div>
          </div>
        </div>
      </div>

      {/* Tabs */}
      <div className="row mb-3">
        <div className="col-12">
          <ul className="nav nav-tabs">
            <li className="nav-item">
              <button
                className={`nav-link ${activeTab === 'approved' ? 'active' : ''}`}
                onClick={() => setActiveTab('approved')}
              >
                <FontAwesomeIcon icon={faCheck} /> Ready to Exit ({approvedOutpasses.length})
              </button>
            </li>
            <li className="nav-item">
              <button
                className={`nav-link ${activeTab === 'departed' ? 'active' : ''}`}
                onClick={() => setActiveTab('departed')}
              >
                <FontAwesomeIcon icon={faDoorOpen} /> Outside - Pending Return ({departedOutpasses.length})
              </button>
            </li>
            <li className="nav-item">
              <button
                className={`nav-link ${activeTab === 'today' ? 'active' : ''}`}
                onClick={() => setActiveTab('today')}
              >
                <FontAwesomeIcon icon={faCalendarDay} /> Today's Schedule ({todayOutpasses.length})
              </button>
            </li>
          </ul>
        </div>
      </div>

      {/* Outpass Table */}
      <div className="row">
        <div className="col-12">
          <div className="card">
            <div className="card-body">
              {displayOutpasses.length === 0 ? (
                <div className="text-center py-5">
                  <h5 className="text-muted">
                    {activeTab === 'approved' && 'No approved outpasses ready for exit'}
                    {activeTab === 'departed' && 'No students currently outside'}
                    {activeTab === 'today' && 'No outpasses scheduled for today'}
                  </h5>
                </div>
              ) : (
                <div className="table-responsive">
                  <table className="table table-hover">
                    <thead className="table-light">
                      <tr>
                        <th>Student</th>
                        <th>Roll No</th>
                        <th>Hostel/Room</th>
                        <th>Place</th>
                        <th>Schedule</th>
                        <th>Contact</th>
                        <th>Status</th>
                        <th>Action</th>
                      </tr>
                    </thead>
                    <tbody>
                      {displayOutpasses.map((outpass) => (
                        <tr key={outpass.id}>
                          <td>
                            <strong>{outpass.name}</strong>
                            <br />
                            <small className="text-muted">{outpass.department}</small>
                          </td>
                          <td>{outpass.rollNo}</td>
                          <td>
                            {outpass.hostel}
                            <br />
                            <small>Room: {outpass.roomNumber}</small>
                          </td>
                          <td>{outpass.placeOfVisit}</td>
                          <td>
                            <small>
                              <strong>Out:</strong> {format(new Date(outpass.date), 'dd/MM HH:mm')}
                              <br />
                              <strong>Return:</strong> {format(new Date(outpass.returnDate), 'dd/MM HH:mm')}
                              {outpass.actualDepartureTime && (
                                <>
                                  <br />
                                  <span className="text-success">
                                    <FontAwesomeIcon icon={faCheck} /> Left: {format(new Date(outpass.actualDepartureTime), 'dd/MM HH:mm')}
                                  </span>
                                </>
                              )}
                              {outpass.actualReturnTime && (
                                <>
                                  <br />
                                  <span className={outpass.isLateReturn ? 'text-danger' : 'text-success'}>
                                    <FontAwesomeIcon icon={faCheck} /> Returned: {format(new Date(outpass.actualReturnTime), 'dd/MM HH:mm')}
                                    {outpass.isLateReturn && ' (LATE)'}
                                  </span>
                                </>
                              )}
                            </small>
                          </td>
                          <td>
                            <small>
                              {outpass.contactNumber}
                              <br />
                              <span className="text-muted">P: {outpass.parentNumber}</span>
                            </small>
                          </td>
                          <td>
                            <span className={`badge ${getStatusBadge(outpass.status)}`}>
                              {outpass.status}
                            </span>
                          </td>
                          <td>
                            {outpass.status === 'APPROVED' && (
                              <button
                                className="btn btn-sm btn-success"
                                onClick={() => handleMarkDeparture(outpass.id)}
                              >
                                ✓ Mark Exit
                              </button>
                            )}
                            {outpass.status === 'DEPARTED' && (
                              <button
                                className="btn btn-sm btn-primary"
                                onClick={() => handleMarkReturn(outpass.id)}
                              >
                                ✓ Mark Return
                              </button>
                            )}
                            {(outpass.status === 'COMPLETED' || outpass.status === 'OVERDUE') && (
                              <span className="text-muted">Completed</span>
                            )}
                          </td>
                        </tr>
                      ))}
                    </tbody>
                  </table>
                </div>
              )}
            </div>
          </div>
        </div>
      </div>
    </div>
  );
};

export default SecurityDashboard;
