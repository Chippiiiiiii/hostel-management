import { useState } from 'react';
import { Link } from 'react-router-dom';
import toast from 'react-hot-toast';
import { FontAwesomeIcon } from '@fortawesome/react-fontawesome';
import { faIdCard, faArrowLeft, faRedo } from '@fortawesome/free-solid-svg-icons';
import FaceVerificationWidget from '../../components/common/FaceVerification';

// Demo/integration page for the browser-only face match + blink liveness
// check. Nothing here talks to the backend — it's a self-contained showcase
// of the <FaceVerification /> component that other flows (e.g. attendance)
// can embed directly once a product decision is made on how the reference
// face should be captured/stored server-side.
const FaceVerificationPage = () => {
  const [result, setResult] = useState(null);
  const [key, setKey] = useState(0); // bump to remount the widget for a fresh attempt

  const handleSuccess = (res) => {
    setResult({ success: true, ...res });
    toast.success('Face verification successful!');
  };

  const handleFailure = (res) => {
    setResult({ success: false, ...res });
    toast.error(res.message || 'Face verification failed');
  };

  const reset = () => {
    setResult(null);
    setKey((k) => k + 1);
  };

  return (
    <div className="container mt-4 mb-5">
      <div className="row mb-4">
        <div className="col-12">
          <div className="d-flex align-items-center mb-3">
            <Link to="/student/dashboard" className="btn btn-outline-secondary me-3">
              <FontAwesomeIcon icon={faArrowLeft} />
            </Link>
            <div>
              <h2 className="mb-0" style={{ fontWeight: '700', color: 'var(--color-primary)' }}>
                <FontAwesomeIcon icon={faIdCard} /> Face Verification
              </h2>
            </div>
          </div>
        </div>
      </div>

      <div className="row">
        <div className="col-12 col-lg-8 mx-auto">
          <div className="card shadow-sm">
            <div className="card-header d-flex justify-content-between align-items-center">
              <h5 className="mb-0">Live Face Match + Blink Check</h5>
              {result && (
                <button type="button" className="btn btn-sm btn-outline-secondary" onClick={reset}>
                  <FontAwesomeIcon icon={faRedo} /> Restart
                </button>
              )}
            </div>
            <div className="card-body">
              {result ? (
                <div className="text-center py-4">
                  {result.success ? (
                    <p className="text-success fw-semibold mb-0">
                      Verified — match distance {result.distance?.toFixed(3)}, blink confirmed.
                    </p>
                  ) : (
                    <p className="text-danger fw-semibold mb-0">{result.message}</p>
                  )}
                </div>
              ) : (
                <FaceVerificationWidget key={key} onSuccess={handleSuccess} onFailure={handleFailure} />
              )}
            </div>
          </div>

          <div className="alert alert-warning mt-3 mb-0" role="alert">
            <strong>Note:</strong> this is a browser-only demo — no webcam frame or biometric
            data ever leaves your device. The blink check is a basic liveness signal only
            (it stops a static photo, not a video replay or a mask) and is not a substitute
            for a certified anti-spoofing system.
          </div>
        </div>
      </div>
    </div>
  );
};

export default FaceVerificationPage;
