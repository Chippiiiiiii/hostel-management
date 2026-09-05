import { useState } from 'react';
import PropTypes from 'prop-types';
import { FontAwesomeIcon } from '@fortawesome/react-fontawesome';
import { faIdCard } from '@fortawesome/free-solid-svg-icons';

// Displays a student's ID card photo (from the authorized outpass response's
// `studentIdCardPhoto` field) either as a compact thumbnail (`mode="thumbnail"`,
// default) or as a large, prominent preview (`mode="large"`) with a label above it.
// Both modes share the same click-to-expand full-size modal.
const IdCardPhotoViewer = ({ photo, size = 60, mode = 'thumbnail' }) => {
  const [showModal, setShowModal] = useState(false);

  const openModal = () => photo && setShowModal(true);

  const isLarge = mode === 'large';

  return (
    <>
      {isLarge ? (
        <div className="mb-3">
          <p
            className="text-center text-muted fw-semibold mb-2"
            style={{ fontSize: '0.75rem', letterSpacing: '0.04em', textTransform: 'uppercase' }}
          >
            Student ID Card
          </p>
          {photo ? (
            <div
              className="mx-auto d-flex align-items-center justify-content-center"
              style={{
                width: '100%', maxWidth: '360px', height: 'auto', maxHeight: '220px',
                aspectRatio: '16 / 10', borderRadius: '10px', overflow: 'hidden',
                border: '1px solid var(--color-border, #ccc)',
                backgroundColor: 'var(--color-bg-tertiary)', cursor: 'pointer',
              }}
              onClick={openModal}
              title="Click to enlarge"
            >
              <img
                src={photo}
                alt="Student ID Card"
                style={{ maxWidth: '100%', maxHeight: '100%', width: '100%', height: '100%', objectFit: 'contain' }}
              />
            </div>
          ) : (
            <div
              className="mx-auto d-flex flex-column align-items-center justify-content-center text-muted"
              style={{
                width: '100%', maxWidth: '360px', height: '160px', borderRadius: '10px',
                border: '1px dashed var(--color-border, #ccc)', backgroundColor: 'var(--color-bg-tertiary)',
              }}
              title="ID card not uploaded"
            >
              <FontAwesomeIcon icon={faIdCard} style={{ fontSize: '1.8rem' }} />
              <span className="mt-2" style={{ fontSize: '0.85rem' }}>ID card not uploaded</span>
            </div>
          )}
        </div>
      ) : photo ? (
        <img
          src={photo}
          alt="Student ID Card"
          onClick={openModal}
          style={{
            width: size, height: size * 0.7, objectFit: 'cover', borderRadius: '6px',
            cursor: 'pointer', border: '1px solid var(--color-border, #ccc)',
          }}
          title="Click to enlarge"
        />
      ) : (
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
      )}

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
  mode: PropTypes.oneOf(['thumbnail', 'large']),
};

export default IdCardPhotoViewer;
