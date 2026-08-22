import { useState, useEffect, Fragment } from 'react';
import { Link } from 'react-router-dom';
import outpassService from '../../services/outpassService';
import LoadingSpinner from '../../components/common/LoadingSpinner';
import toast from 'react-hot-toast';
import { format } from 'date-fns';
import { FontAwesomeIcon } from '@fortawesome/react-fontawesome';
import { faHistory, faBook, faHourglass, faCheck, faTimes, faTimesCircle, faClock, faComment, faArrowLeft } from '@fortawesome/free-solid-svg-icons';
import useOutpassNotifications from '../../hooks/useOutpassNotifications';

const OutpassHistory = () => {
  const [outpasses, setOutpasses] = useState([]);
  useOutpassNotifications(outpasses);
  const [loading, setLoading] = useState(true);
  const [filter, setFilter] = useState('ALL');
  const [expandedRow, setExpandedRow] = useState(null);

  useEffect(() => {
    fetchOutpasses();
  }, []);

  const fetchOutpasses = async () => {
    try {
      const response = await outpassService.getOutpassHistory();
      setOutpasses(response.data);
    } catch (error) {
      toast.error('Failed to load outpass history');
    } finally {
      setLoading(false);
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
      EXPIRED: 'bg-secondary',
    };
    return badges[status] || 'bg-secondary';
  };

  const filteredOutpasses = filter === 'ALL' 
    ? outpasses 
    : outpasses.filter(o => o.status === filter);

  if (loading) return <LoadingSpinner />;

  return (
    <div className="container mt-4 mb-5">
      <div className="row mb-4">
        <div className="col-12">
          <div className="d-flex align-items-center mb-1">
            <Link to="/student/outpass" className="btn btn-outline-secondary btn-sm me-3">
              <FontAwesomeIcon icon={faArrowLeft} />
            </Link>
            <h2 className="mb-0" style={{ fontWeight: '700', color: 'var(--color-primary)' }}>
              <FontAwesomeIcon icon={faHistory} /> Outpass History
            </h2>
          </div>
          <p className="text-muted ms-5 ps-2">View and track all your outpass requests</p>
        </div>
      </div>

      {/* Filter Buttons */}
      <div className="row mb-4">
        <div className="col-12">
          <div className="btn-group" role="group" style={{ flexWrap: 'wrap' }}>
            <button
              className={`btn ${filter === 'ALL' ? 'btn-primary' : 'btn-outline-primary'}`}
              onClick={() => setFilter('ALL')}
              style={{ fontWeight: '600' }}
            >
              <FontAwesomeIcon icon={faBook} /> All
            </button>
            <button
              className={`btn ${filter === 'PENDING' ? 'btn-warning' : 'btn-outline-warning'}`}
              onClick={() => setFilter('PENDING')}
              style={{ fontWeight: '600', color: filter === 'PENDING' ? 'white' : 'var(--color-warning)' }}
            >
              <FontAwesomeIcon icon={faHourglass} /> Pending
            </button>
            <button
              className={`btn ${filter === 'APPROVED' ? 'btn-success' : 'btn-outline-success'}`}
              onClick={() => setFilter('APPROVED')}
              style={{ fontWeight: '600' }}
            >
              <FontAwesomeIcon icon={faCheck} /> Approved
            </button>
            <button
              className={`btn ${filter === 'DECLINED' ? 'btn-danger' : 'btn-outline-danger'}`}
              onClick={() => setFilter('DECLINED')}
              style={{ fontWeight: '600' }}
            >
              <FontAwesomeIcon icon={faTimes} /> Declined
            </button>
          </div>
        </div>
      </div>

      {/* Outpass Table */}
      <div className="row">
        <div className="col-12">
          <div className="card shadow-sm">
            <div className="card-body">
              {filteredOutpasses.length === 0 ? (
                <div className="text-center py-5">
                  <h5 className="text-muted">No outpass requests found</h5>
                </div>
              ) : (
                <div className="table-responsive">
                  <table className="table table-hover">
                    <thead className="table-light">
                      <tr>
                        <th>ID</th>
                        <th>Place</th>
                        <th>Departure</th>
                        <th>Return</th>
                        <th>Days</th>
                        <th>Contact</th>
                        <th>Status</th>
                        <th>Created</th>
                        <th>Details</th>
                      </tr>
                    </thead>
                    <tbody>
                      {filteredOutpasses.map((outpass) => (
                        <Fragment key={outpass.id}>
                          <tr>
                            <td>{outpass.id}</td>
                            <td>{outpass.placeOfVisit}</td>
                            <td>{format(new Date(outpass.date), 'dd/MM/yyyy HH:mm')}</td>
                            <td>{format(new Date(outpass.returnDate), 'dd/MM/yyyy HH:mm')}</td>
                            <td>{outpass.noOfDays}</td>
                            <td>{outpass.contactNumber}</td>
                            <td>
                              <span className={`badge ${getStatusBadge(outpass.status)}`}>
                                {outpass.status}
                              </span>
                              {outpass.isLateReturn && (
                                <span className="badge bg-warning text-dark ms-1" title="Late Return">
                                  <FontAwesomeIcon icon={faClock} />
                                </span>
                              )}
                            </td>
                            <td>{format(new Date(outpass.createdAt), 'dd/MM/yyyy')}</td>
                            <td>
                              {outpass.status === 'PENDING' && (
                                <button
                                  className="btn btn-sm btn-outline-danger me-1"
                                  onClick={async () => {
                                    if (!window.confirm('Cancel this outpass request?')) return;
                                    try {
                                      await outpassService.cancelOutpass(outpass.id);
                                      toast.success('Outpass cancelled');
                                      fetchOutpasses();
                                    } catch (err) {
                                      toast.error(err.response?.data?.message || 'Failed to cancel');
                                    }
                                  }}
                                >
                                  <FontAwesomeIcon icon={faTimes} /> Cancel
                                </button>
                              )}
                              {(outpass.declineReason || outpass.wardenComments) && (
                                <button
                                  className="btn btn-sm btn-outline-info"
                                  onClick={() => setExpandedRow(expandedRow === outpass.id ? null : outpass.id)}
                                >
                                  {expandedRow === outpass.id ? '▲' : '▼'}
                                </button>
                              )}
                            </td>
                          </tr>
                          {expandedRow === outpass.id && (outpass.declineReason || outpass.wardenComments) && (
                            <tr>
                              <td colSpan="9" className="bg-light">
                                <div className="p-3">
                                  {outpass.declineReason && (
                                    <div className="mb-2">
                                      <strong className="text-danger"><FontAwesomeIcon icon={faTimesCircle} /> Decline Reason:</strong>
                                      <p className="mb-0 mt-1">{outpass.declineReason}</p>
                                    </div>
                                  )}
                                  {outpass.wardenComments && (
                                    <div>
                                      <strong className="text-primary"><FontAwesomeIcon icon={faComment} /> Warden Comments:</strong>
                                      <p className="mb-0 mt-1">{outpass.wardenComments}</p>
                                    </div>
                                  )}
                                  {outpass.processedAt && (
                                    <small className="text-muted">
                                      Processed on: {format(new Date(outpass.processedAt), 'dd/MM/yyyy HH:mm')}
                                    </small>
                                  )}
                                </div>
                              </td>
                            </tr>
                          )}
                        </Fragment>
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

export default OutpassHistory;
