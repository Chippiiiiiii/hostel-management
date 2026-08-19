import { useState, useEffect } from 'react';
import PropTypes from 'prop-types';
import api from '../../services/api';
import { FontAwesomeIcon } from '@fortawesome/react-fontawesome';
import { faChartBar, faTimes, faClock } from '@fortawesome/free-solid-svg-icons';

const StudentStatsCard = ({ studentId, onClose }) => {
  const [stats, setStats] = useState(null);
  const [loading, setLoading] = useState(true);

  useEffect(() => {
    fetchStats();
  }, [studentId]);

  const fetchStats = async () => {
    try {
      const response = await api.get(`/warden/student/${studentId}/stats`);
      setStats(response.data.data);
    } catch (error) {
      console.error('Error fetching student stats:', error);
    } finally {
      setLoading(false);
    }
  };

  const getRiskBadge = (riskLevel) => {
    const badges = {
      LOW: 'success',
      MEDIUM: 'warning',
      HIGH: 'danger'
    };
    return badges[riskLevel] || 'secondary';
  };

  if (loading) {
    return (
      <div className="card mb-3">
        <div className="card-body text-center">
          <div className="spinner-border spinner-border-sm"></div>
          <span className="ms-2">Loading student stats...</span>
        </div>
      </div>
    );
  }

  if (!stats) return null;

  return (
    <div className="card mb-3 shadow-sm" style={{ border: '2px solid var(--color-primary)' }}>
      <div className="card-header d-flex justify-content-between align-items-center">
        <h6 className="mb-0 fw-bold"><FontAwesomeIcon icon={faChartBar} /> Student Track Record</h6>
        <button 
          className="btn btn-sm" 
          onClick={onClose}
          style={{ 
            background: 'rgba(255, 255, 255, 0.2)', 
            color: 'white', 
            fontWeight: '600',
            border: 'none'
          }}
        ><FontAwesomeIcon icon={faTimes} /></button>
      </div>
      <div className="card-body">
        <div className="row g-2">
          {/* Student Info */}
          <div className="col-12">
            <h6 className="fw-bold">{stats.name}</h6>
            <p className="mb-1 small text-muted">
              {stats.rollNo} | {stats.department} | {stats.hostel}
            </p>
            <hr />
          </div>

          {/* Key Metrics */}
          <div className="col-6 col-md-3">
            <div className="text-center p-2 bg-light rounded">
              <h5 className="mb-0">{stats.totalOutpasses}</h5>
              <small className="text-muted">Total Requests</small>
            </div>
          </div>
          <div className="col-6 col-md-3">
            <div className="text-center p-2 bg-light rounded">
              <h5 className="mb-0 text-success">{stats.totalApproved}</h5>
              <small className="text-muted">Approved</small>
            </div>
          </div>
          <div className="col-6 col-md-3">
            <div className="text-center p-2 bg-light rounded">
              <h5 className="mb-0 text-danger">{stats.totalDeclined}</h5>
              <small className="text-muted">Declined</small>
            </div>
          </div>
          <div className="col-6 col-md-3">
            <div className="text-center p-2 bg-light rounded">
              <h5 className="mb-0 text-info">{stats.totalCompleted}</h5>
              <small className="text-muted">Completed</small>
            </div>
          </div>

          {/* Performance */}
          <div className="col-12 mt-2">
            <hr />
            <div className="row g-2">
              <div className="col-6">
                <strong>Approval Rate:</strong>
                <div className="progress mt-1">
                  <div
                    className="progress-bar bg-success"
                    style={{ width: `${stats.approvalRate}%` }}
                  >
                    {stats.approvalRate}%
                  </div>
                </div>
              </div>
              <div className="col-6">
                <strong>On-Time Rate:</strong>
                <div className="progress mt-1">
                  <div
                    className="progress-bar bg-info"
                    style={{ width: `${stats.onTimeCompletionRate}%` }}
                  >
                    {stats.onTimeCompletionRate}%
                  </div>
                </div>
              </div>
            </div>
          </div>

          {/* Warnings */}
          <div className="col-12 mt-2">
            <hr />
            {stats.hasActiveOutpass && (
              <div className="alert alert-danger py-2 mb-2">
                <small>⛔ <strong>Warning:</strong> Student already has an active outpass</small>
              </div>
            )}
            {stats.overdueCount > 0 && (
              <div className="alert alert-warning py-2 mb-2">
                <small>⚠️ <strong>Alert:</strong> {stats.overdueCount} overdue outpass(es)</small>
              </div>
            )}
            {stats.lateReturns > 0 && (
              <div className="alert alert-info py-2 mb-2">
                <small><FontAwesomeIcon icon={faClock} /> {stats.lateReturns} late return(s) in history</small>
              </div>
            )}
          </div>

          {/* Risk Level */}
          <div className="col-12">
            <div className="d-flex justify-content-between align-items-center">
              <strong>Risk Assessment:</strong>
              <span className={`badge bg-${getRiskBadge(stats.riskLevel)}`}>
                {stats.riskLevel} RISK
              </span>
            </div>
          </div>

          {/* Last Activity */}
          {stats.lastOutpassDate && (
            <div className="col-12 mt-2">
              <small className="text-muted">
                Last Outpass: {new Date(stats.lastOutpassDate).toLocaleDateString()} 
                ({stats.lastOutpassStatus})
              </small>
            </div>
          )}
        </div>
      </div>
    </div>
  );
};

StudentStatsCard.propTypes = {
  studentId: PropTypes.number.isRequired,
  onClose: PropTypes.func.isRequired
};

export default StudentStatsCard;
