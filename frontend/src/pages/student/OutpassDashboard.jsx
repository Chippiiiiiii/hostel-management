import { useState, useEffect } from 'react';
import { Link } from 'react-router-dom';
import outpassService from '../../services/outpassService';
import LoadingSpinner from '../../components/common/LoadingSpinner';
import toast from 'react-hot-toast';
import { format } from 'date-fns';
import { FontAwesomeIcon } from '@fortawesome/react-fontawesome';
import { faDoorOpen, faFileAlt, faPencilAlt, faClipboardList, faChartPie, faArrowRight, faArrowLeft, faPlus, faHistory } from '@fortawesome/free-solid-svg-icons';

const OutpassDashboard = () => {
  const [recentOutpasses, setRecentOutpasses] = useState([]);
  const [loading, setLoading] = useState(true);

  useEffect(() => {
    fetchData();
  }, []);

  const fetchData = async () => {
    try {
      const outpassRes = await outpassService.getOutpassHistory();
      setRecentOutpasses(outpassRes.data.slice(0, 5));
    } catch (error) {
      console.error('Error fetching outpass data:', error);
      toast.error('Failed to load outpass data');
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
      {/* Page Header with Sub-Nav */}
      <div className="row mb-4">
        <div className="col-12">
          <div className="d-flex align-items-center justify-content-between mb-3">
            <div className="d-flex align-items-center">
              <Link to="/student/dashboard" className="btn btn-outline-secondary me-3" style={{ borderRadius: '10px' }}>
                <FontAwesomeIcon icon={faArrowLeft} />
              </Link>
              <div>
                <h2 className="mb-0" style={{ fontWeight: '700', color: 'var(--color-primary)' }}>
                  <FontAwesomeIcon icon={faDoorOpen} /> Outpass
                </h2>
              </div>
            </div>
            <div className="d-flex gap-2">
              <Link to="/student/create-outpass" className="btn btn-primary" style={{ borderRadius: '10px', fontWeight: '600' }}>
                <FontAwesomeIcon icon={faPlus} /> Create Outpass
              </Link>
              <Link to="/student/history" className="btn btn-outline-primary" style={{ borderRadius: '10px', fontWeight: '600' }}>
                <FontAwesomeIcon icon={faHistory} /> History
              </Link>
            </div>
          </div>
        </div>
      </div>

      {/* Quick Actions */}
      <div className="row mb-4 g-4">
        <div className="col-md-4">
          <div className="card text-center h-100 shadow-sm" style={{ backgroundColor: 'var(--color-bg-dark)', border: 'none', transition: 'all 0.2s' }}>
            <div className="card-body d-flex flex-column justify-content-center p-4">
              <FontAwesomeIcon icon={faFileAlt} style={{ fontSize: '3.5rem', color: '#fff', marginBottom: '1rem' }} />
              <h5 className="mb-3 fw-bold" style={{ color: '#fff' }}>Create New Outpass</h5>
              <Link to="/student/create-outpass" className="btn btn-light mt-2 fw-semibold">
                <FontAwesomeIcon icon={faPencilAlt} /> Create Now
              </Link>
            </div>
          </div>
        </div>
        <div className="col-md-4">
          <div className="card text-center h-100 shadow-sm" style={{ backgroundColor: 'var(--color-info)', border: 'none', transition: 'all 0.2s' }}>
            <div className="card-body d-flex flex-column justify-content-center p-4">
              <FontAwesomeIcon icon={faClipboardList} style={{ fontSize: '3.5rem', color: '#fff', marginBottom: '1rem' }} />
              <h5 className="mb-3 fw-bold" style={{ color: '#fff' }}>View All History</h5>
              <Link to="/student/history" className="btn btn-light mt-2 fw-semibold">
                <FontAwesomeIcon icon={faClipboardList} /> Open History
              </Link>
            </div>
          </div>
        </div>
        <div className="col-md-4">
          <div className="card text-center h-100 shadow-sm" style={{ backgroundColor: 'var(--color-success)', border: 'none', transition: 'all 0.2s' }}>
            <div className="card-body d-flex flex-column justify-content-center p-4">
              <FontAwesomeIcon icon={faChartPie} style={{ fontSize: '3.5rem', color: '#fff', marginBottom: '1rem' }} />
              <h5 className="mb-3 fw-bold" style={{ color: '#fff' }}>Outpass Statistics</h5>
              <p className="mb-0 mt-2" style={{ fontSize: '1.2rem', fontWeight: '600', color: '#fff' }}>Total: {recentOutpasses.length}</p>
            </div>
          </div>
        </div>
      </div>

      {/* Recent Outpasses */}
      <div className="row">
        <div className="col-12">
          <div className="card shadow-sm">
            <div className="card-header d-flex justify-content-between align-items-center">
              <h5 className="mb-0"><FontAwesomeIcon icon={faClipboardList} /> Recent Outpass Requests</h5>
              <Link to="/student/history" className="btn btn-sm btn-primary">
                View All <FontAwesomeIcon icon={faArrowRight} />
              </Link>
            </div>
            <div className="card-body">
              {recentOutpasses.length === 0 ? (
                <p className="text-center text-muted">No outpass requests yet</p>
              ) : (
                <div className="table-responsive">
                  <table className="table table-hover">
                    <thead>
                      <tr>
                        <th>ID</th>
                        <th>Place of Visit</th>
                        <th>Date</th>
                        <th>Return Date</th>
                        <th>Status</th>
                      </tr>
                    </thead>
                    <tbody>
                      {recentOutpasses.map((outpass) => (
                        <tr key={outpass.id}>
                          <td>{outpass.id}</td>
                          <td>{outpass.placeOfVisit}</td>
                          <td>{format(new Date(outpass.date), 'dd/MM/yyyy HH:mm')}</td>
                          <td>{format(new Date(outpass.returnDate), 'dd/MM/yyyy HH:mm')}</td>
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
              )}
            </div>
          </div>
        </div>
      </div>
    </div>
  );
};

export default OutpassDashboard;
