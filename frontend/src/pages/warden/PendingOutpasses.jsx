import {useState, useEffect } from 'react';
import outpassService from '../../services/outpassService';
import LoadingSpinner from '../../components/common/LoadingSpinner';
import DeclineReasonModal from '../../components/common/DeclineReasonModal';
import ApproveCommentsModal from '../../components/common/ApproveCommentsModal';
import StudentStatsCard from '../../components/common/StudentStatsCard';
import toast from 'react-hot-toast';
import { format } from 'date-fns';
import { FontAwesomeIcon } from '@fortawesome/react-fontawesome';
import { faHourglass, faCheck, faCheckCircle, faChartBar, faArrowLeft } from '@fortawesome/free-solid-svg-icons';
import { Link } from 'react-router-dom';

const PendingOutpasses = () => {
  const [outpasses, setOutpasses] = useState([]);
  const [loading, setLoading] = useState(true);
  const [processingId, setProcessingId] = useState(null);
  const [showDeclineModal, setShowDeclineModal] = useState(false);
  const [showApproveModal, setShowApproveModal] = useState(false);
  const [selectedOutpass, setSelectedOutpass] = useState(null);
  const [viewingStatsFor, setViewingStatsFor] = useState(null);

  useEffect(() => {
    fetchPendingOutpasses();
  }, []);

  const fetchPendingOutpasses = async () => {
    try {
      const response = await outpassService.getPendingOutpasses();
      setOutpasses(response.data);
    } catch (error) {
      console.error('Error fetching pending outpasses:', error);
      toast.error('Failed to load pending outpasses');
    } finally {
      setLoading(false);
    }
  };

  const handleApprove = (outpass) => {
    setSelectedOutpass(outpass);
    setShowApproveModal(true);
  };

  const handleApproveSubmit = async (data) => {
    setProcessingId(selectedOutpass.id);
    try {
      await outpassService.approveOutpass(selectedOutpass.id, data);
      toast.success('Outpass approved successfully!');
      setOutpasses(outpasses.filter(o => o.id !== selectedOutpass.id));
      setShowApproveModal(false);
      setSelectedOutpass(null);
    } catch (error) {
      console.error('Error approving outpass:', error);
      toast.error(error.response?.data?.message || 'Failed to approve outpass');
    } finally {
      setProcessingId(null);
    }
  };

  const handleDecline = (outpass) => {
    setSelectedOutpass(outpass);
    setShowDeclineModal(true);
  };

  const handleDeclineSubmit = async (data) => {
    setProcessingId(selectedOutpass.id);
    try {
      await outpassService.declineOutpass(selectedOutpass.id, data);
      toast.success('Outpass declined successfully!');
      setOutpasses(outpasses.filter(o => o.id !== selectedOutpass.id));
      setShowDeclineModal(false);
      setSelectedOutpass(null);
    } catch (error) {
      console.error('Error declining outpass:', error);
      toast.error(error.response?.data?.message || 'Failed to decline outpass');
    } finally {
      setProcessingId(null);
    }
  };

  const handleViewStats = (studentId) => {
    setViewingStatsFor(studentId);
  };

  if (loading) return <LoadingSpinner />;

  return (
    <>
      <div className="container mt-4 mb-5">
        <div className="row mb-4">
          <div className="col-12">
            <div className="d-flex align-items-center mb-1">
              <Link to="/warden/outpass" className="btn btn-outline-secondary btn-sm me-3">
                <FontAwesomeIcon icon={faArrowLeft} />
              </Link>
              <h2 className="mb-0" style={{ fontWeight: '700', color: 'var(--color-primary)' }}>
                <FontAwesomeIcon icon={faHourglass} /> Pending Outpass Requests
              </h2>
            </div>
            <p className="text-muted ms-5 ps-2">Review and approve/decline student outpass requests</p>
          </div>
        </div>

        {/* Student Stats Card */}
        {viewingStatsFor && (
          <StudentStatsCard
            studentId={viewingStatsFor}
            onClose={() => setViewingStatsFor(null)}
          />
        )}

        <div className="row">
          <div className="col-12">
            {outpasses.length === 0 ? (
              <div className="card">
                <div className="card-body text-center py-5">
                  <FontAwesomeIcon icon={faCheckCircle} style={{ fontSize: '3rem', color: 'var(--color-success)' }} />
                  <h4 className="text-muted mt-3">No pending requests</h4>
                  <p>All outpass requests have been processed</p>
                </div>
              </div>
            ) : (
              <div className="row">
                {outpasses.map((outpass) => (
                  <div key={outpass.id} className="col-md-6 col-lg-4 mb-4">
                    <div className="card h-100 shadow-sm">
                      <div className="card-header bg-warning d-flex justify-content-between align-items-center">
                        <h6 className="mb-0">Request #{outpass.id}</h6>
                        <button
                          className="btn btn-sm btn-outline-dark"
                          onClick={() => handleViewStats(outpass.studentId)}
                          title="View student track record"
                        >
                          <FontAwesomeIcon icon={faChartBar} />
                        </button>
                      </div>
                      <div className="card-body">
                        <h5 className="card-title">{outpass.name}</h5>
                        <hr />
                        <p className="mb-1"><strong>Roll No:</strong> {outpass.rollNo}</p>
                        <p className="mb-1"><strong>Department:</strong> {outpass.department}</p>
                        <p className="mb-1"><strong>Hostel:</strong> {outpass.hostel} - {outpass.roomNumber}</p>
                        <hr />
                        <p className="mb-1"><strong>Place:</strong> {outpass.placeOfVisit}</p>
                        <p className="mb-1"><strong>Departure:</strong> {format(new Date(outpass.date), 'dd/MM/yyyy HH:mm')}</p>
                        <p className="mb-1"><strong>Return:</strong> {format(new Date(outpass.returnDate), 'dd/MM/yyyy HH:mm')}</p>
                        <p className="mb-1"><strong>Days:</strong> {outpass.noOfDays}</p>
                        <hr />
                        <p className="mb-1"><strong>Contact:</strong> {outpass.contactNumber}</p>
                        <p className="mb-1"><strong>Parent:</strong> {outpass.parentNumber}</p>
                        <p className="mb-1"><small className="text-muted">Created: {format(new Date(outpass.createdAt), 'dd/MM/yyyy HH:mm')}</small></p>
                      </div>
                      <div className="card-footer">
                        <div className="d-flex gap-2">
                          <button
                            className="btn btn-success flex-fill"
                            onClick={() => handleApprove(outpass)}
                            disabled={processingId === outpass.id}
                          >
                            {processingId === outpass.id ? (
                              <span className="spinner-border spinner-border-sm"></span>
                            ) : (
                                <><FontAwesomeIcon icon={faCheck} /> Approve</>
                            )}
                          </button>
                          <button
                            className="btn btn-danger flex-fill"
                            onClick={() => handleDecline(outpass)}
                            disabled={processingId === outpass.id}
                          >
                            {processingId === outpass.id ? (
                              <span className="spinner-border spinner-border-sm"></span>
                            ) : (
                              '✗ Decline'
                            )}
                          </button>
                        </div>
                      </div>
                    </div>
                  </div>
                ))}
              </div>
            )}
          </div>
        </div>
      </div>

      {/* Modals */}
      <ApproveCommentsModal
        show={showApproveModal}
        onClose={() => {
          setShowApproveModal(false);
          setSelectedOutpass(null);
        }}
        onSubmit={handleApproveSubmit}
        processingId={processingId}
      />

      <DeclineReasonModal
        show={showDeclineModal}
        onClose={() => {
          setShowDeclineModal(false);
          setSelectedOutpass(null);
        }}
        onSubmit={handleDeclineSubmit}
        processingId={processingId}
      />
    </>
  );
};

export default PendingOutpasses;
