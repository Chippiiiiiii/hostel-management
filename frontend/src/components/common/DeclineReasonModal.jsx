import { useState } from 'react';
import PropTypes from 'prop-types';
import { FontAwesomeIcon } from '@fortawesome/react-fontawesome';
import { faTimesCircle } from '@fortawesome/free-solid-svg-icons';

const DeclineReasonModal = ({ show, onClose, onSubmit, processingId }) => {
  const [reason, setReason] = useState('');
  const [comments, setComments] = useState('');

  const predefinedReasons = [
    'Insufficient justification',
    'Overlapping outpass dates',
    'Too many pending outpasses',
'Safety concerns',
    'Academic/Exam period',
    'Disciplinary issues',
    'Invalid contact information',
    'Other (specify in comments)'
  ];

  const handleSubmit = (e) => {
    e.preventDefault();
    if (!reason.trim()) {
      alert('Please select or enter a decline reason');
      return;
    }
    onSubmit({ declineReason: reason, comments });
    // Reset form
    setReason('');
    setComments('');
  };

  const handleClose = () => {
    setReason('');
    setComments('');
    onClose();
  };

  if (!show) return null;

  return (
    <div className="modal show d-block" style={{ backgroundColor: 'rgba(0,0,0,0.6)' }}>
      <div className="modal-dialog modal-dialog-centered">
        <div className="modal-content" style={{ borderRadius: 'var(--border-radius-lg)' }}>
          <div className="modal-header" style={{ background: 'var(--color-danger)', color: 'white', borderRadius: 'var(--border-radius-lg) var(--border-radius-lg) 0 0' }}>
            <h5 className="modal-title fw-bold"><FontAwesomeIcon icon={faTimesCircle} /> Decline Outpass Request</h5>
            <button
              type="button"
              className="btn-close btn-close-white"
              onClick={handleClose}
              disabled={processingId}
            ></button>
          </div>
          <form onSubmit={handleSubmit}>
            <div className="modal-body">
              <div className="mb-3">
                <label className="form-label fw-bold">
                  Reason for Decline <span className="text-danger">*</span>
                </label>
                <select
                  className="form-select"
                  value={reason}
                  onChange={(e) => setReason(e.target.value)}
                  required
                >
                  <option value="">-- Select a reason --</option>
                  {predefinedReasons.map((r, idx) => (
                    <option key={idx} value={r}>
                      {r}
                    </option>
                  ))}
                </select>
              </div>

              <div className="mb-3">
                <label className="form-label fw-bold">Additional Comments (Optional)</label>
                <textarea
                  className="form-control"
                  rows="4"
                  value={comments}
                  onChange={(e) => setComments(e.target.value)}
                  placeholder="Provide additional details or guidance for the student..."
                  maxLength={500}
                ></textarea>
                <small className="text-muted">{comments.length}/500 characters</small>
              </div>

              <div className="alert alert-warning">
                <small>
                  <strong>Note:</strong> The student will be able to see the decline reason and comments.
                  Please be professional and constructive.
                </small>
              </div>
            </div>
            <div className="modal-footer">
              <button
                type="button"
                className="btn btn-secondary"
                onClick={handleClose}
                disabled={processingId}
              >
                Cancel
              </button>
              <button
                type="submit"
                className="btn btn-danger"
                disabled={processingId}
              >
                {processingId ? (
                  <>
                    <span className="spinner-border spinner-border-sm me-2"></span>
                    Declining...
                  </>
                ) : (
                  '✗ Confirm Decline'
                )}
              </button>
            </div>
          </form>
        </div>
      </div>
    </div>
  );
};

DeclineReasonModal.propTypes = {
  show: PropTypes.bool.isRequired,
  onClose: PropTypes.func.isRequired,
  onSubmit: PropTypes.func.isRequired,
  processingId: PropTypes.number
};

export default DeclineReasonModal;
