import {useState, useEffect } from 'react';
import outpassService from '../../services/outpassService';
import LoadingSpinner from '../../components/common/LoadingSpinner';
import DeclineReasonModal from '../../components/common/DeclineReasonModal';
import ApproveCommentsModal from '../../components/common/ApproveCommentsModal';
import StudentStatsCard from '../../components/common/StudentStatsCard';
import toast from 'react-hot-toast';
import { format } from 'date-fns';
import { FontAwesomeIcon } from '@fortawesome/react-fontawesome';
import { faHourglass, faCheck, faCheckCircle, faChartBar, faArrowLeft, faTimes, faCheckDouble } from '@fortawesome/free-solid-svg-icons';
import { Link } from 'react-router-dom';

const PendingOutpasses = () => {
  const [outpasses, setOutpasses] = useState([]);
  const [loading, setLoading] = useState(true);
  const [processingId, setProcessingId] = useState(null);
  const [showDeclineModal, setShowDeclineModal] = useState(false);
  const [showApproveModal, setShowApproveModal] = useState(false);
  const [selectedOutpass, setSelectedOutpass] = useState(null);
  const [viewingStatsFor, setViewingStatsFor] = useState(null);
  const [selectedIds, setSelectedIds] = useState(new Set());
  const [bulkProcessing, setBulkProcessing] = useState(false);
  const [showBulkDeclineModal, setShowBulkDeclineModal] = useState(false);

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

  const toggleSelect = (id) => {
    setSelectedIds(prev => {
      const next = new Set(prev);
      if (next.has(id)) next.delete(id);
      else next.add(id);
      return next;
    });
  };

  const toggleSelectAll = () => {
    if (selectedIds.size === outpasses.length) {
      setSelectedIds(new Set());
    } else {
      setSelectedIds(new Set(outpasses.map(o => o.id)));
    }
  };

  const handleBulkApprove = async () => {
    if (selectedIds.size === 0) return;
    if (!confirm(`Approve ${selectedIds.size} outpass(es)?`)) return;
    setBulkProcessing(true);
    try {
      const res = await outpassService.bulkApproveOutpasses([...selectedIds]);
      toast.success(`${res.data.approved} outpass(es) approved`);
      setOutpasses(prev => prev.filter(o => !selectedIds.has(o.id)));
      setSelectedIds(new Set());
    } catch (error) {
      toast.error('Bulk approve failed');
    } finally {
      setBulkProcessing(false);
    }
  };

  const handleBulkDeclineSubmit = async (data) => {
    setBulkProcessing(true);
    try {
      const res = await outpassService.bulkDeclineOutpasses([...selectedIds], data.declineReason);
      toast.success(`${res.data.declined} outpass(es) declined`);
      setOutpasses(prev => prev.filter(o => !selectedIds.has(o.id)));
      setSelectedIds(new Set());
      setShowBulkDeclineModal(false);
    } catch (error) {
      toast.error('Bulk decline failed');
    } finally {
      setBulkProcessing(false);
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
      setSelectedIds(prev => { const n = new Set(prev); n.delete(selectedOutpass.id); return n; });
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
      setSelectedIds(prev => { const n = new Set(prev); n.delete(selectedOutpass.id); return n; });
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

        {/* Bulk Action Bar */}
        {outpasses.length > 0 && (
          <div className="row mb-3">
            <div className="col-12">
              <div className="card shadow-sm">
                <div className="card-body py-2 d-flex align-items-center justify-content-between flex-wrap gap-2">
                  <div className="d-flex align-items-center gap-3">
                    <div className="form-check mb-0">
                      <input
                        className="form-check-input"
                        type="checkbox"
                        checked={selectedIds.size === outpasses.length && outpasses.length > 0}
                        onChange={toggleSelectAll}
                        id="selectAll"
                      />
                      <label className="form-check-label fw-semibold" htmlFor="selectAll">
                        Select All
                      </label>
                    </div>
                    {selectedIds.size > 0 && (
                      <span className="badge bg-primary">{selectedIds.size} selected</span>
                    )}
                  </div>
                  {selectedIds.size > 0 && (
                    <div className="d-flex gap-2">
                      <button
                        className="btn btn-success btn-sm"
                        onClick={handleBulkApprove}
                        disabled={bulkProcessing}
                      >
                        {bulkProcessing ? (
                          <span className="spinner-border spinner-border-sm"></span>
                        ) : (
                          <><FontAwesomeIcon icon={faCheckDouble} /> Approve ({selectedIds.size})</>
                        )}
                      </button>
                      <button
                        className="btn btn-danger btn-sm"
                        onClick={() => setShowBulkDeclineModal(true)}
                        disabled={bulkProcessing}
                      >
                        <FontAwesomeIcon icon={faTimes} /> Decline ({selectedIds.size})
                      </button>
                    </div>
                  )}
                </div>
              </div>
            </div>
          </div>
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
                    <div className={`card h-100 shadow-sm ${selectedIds.has(outpass.id) ? 'border-primary border-2' : ''}`}>
                      <div className="card-header bg-warning d-flex justify-content-between align-items-center">
                        <div className="d-flex align-items-center gap-2">
                          <input
                            type="checkbox"
                            className="form-check-input mt-0"
                            checked={selectedIds.has(outpass.id)}
                            onChange={() => toggleSelect(outpass.id)}
                          />
                          <h6 className="mb-0">Request #{outpass.id}</h6>
                        </div>
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
                        {outpass.reason && <p className="mb-1"><strong>Reason:</strong> {outpass.reason}</p>}
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

      {/* Bulk Decline Modal */}
      <DeclineReasonModal
        show={showBulkDeclineModal}
        onClose={() => setShowBulkDeclineModal(false)}
        onSubmit={handleBulkDeclineSubmit}
        processingId={bulkProcessing ? 'bulk' : null}
      />
    </>
  );
};

export default PendingOutpasses;
