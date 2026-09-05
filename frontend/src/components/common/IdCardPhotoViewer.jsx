import { useState } from 'react';
import PropTypes from 'prop-types';
import { FontAwesomeIcon } from '@fortawesome/react-fontawesome';
import { faIdCard } from '@fortawesome/free-solid-svg-icons';

// Compact thumbnail for a student's ID card photo; click opens a larger preview modal.
// Used by both the Warden and Security Guard outpass screens against the same
// `studentIdCardPhoto` field on the outpass response.
const IdCardPhotoViewer = ({ photo, size = 60 }) => {
  const [showModal, setShowModal] = useState(false);

  if (!photo) {
    return (
      <div
        className="d-flex flex-column align-items-center justify-content-center text-muted"
        style={{
          width: size, height: size * 0.7, borderRadius: '6px',
          border: '1px dashed var(--color-border, #ccc)', fontSize: '0.6rem',
        }}
        title="ID card not uploaded"
      >
        <FontAwesomeIcon icon={faIdCard} />
        <span>Not uploaded</span>
      </div>
    );
  }

  return (
    <>
      <img
        src={photo}
        alt="Student ID Card"
        onClick={() => setShowModal(true)}
        style={{
          width: size, height: size * 0.7, objectFit: 'cover', borderRadius: '6px',
          cursor: 'pointer', border: '1px solid var(--color-border, #ccc)',
        }}
        title="Click to enlarge"
      />

      {showModal && (
        <div
          className="modal show d-block"
          style={{ backgroundColor: 'rgba(0,0,0,0.75)' }}
          onClick={() => setShowModal(false)}
        >
          <div className="modal-dialog modal-dialog-centered" onClick={(e) => e.stopPropagation()}>
            <div className="modal-content" style={{ borderRadius: 'var(--border-radius-lg)' }}>
              <div className="modal-header">
                <h6 className="modal-title fw-bold"><FontAwesomeIcon icon={faIdCard} /> Student ID Card</h6>
                <button type="button" className="btn-close" onClick={() => setShowModal(false)}></button>
              </div>
              <div className="modal-body text-center">
                <img src={photo} alt="Student ID Card" style={{ maxWidth: '100%', borderRadius: '6px' }} />
              </div>
            </div>
          </div>
        </div>
      )}
    </>
  );
};

IdCardPhotoViewer.propTypes = {
  photo: PropTypes.string,
  size: PropTypes.number,
};

export default IdCardPhotoViewer;
