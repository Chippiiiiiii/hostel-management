import { useCallback, useEffect, useRef, useState } from 'react';
import PropTypes from 'prop-types';
import { FontAwesomeIcon } from '@fortawesome/react-fontawesome';
import {
  faIdCard, faEye, faUserCheck, faCheckCircle, faTimesCircle,
  faSpinner, faVideoSlash, faExclamationTriangle, faRedo,
} from '@fortawesome/free-solid-svg-icons';
import {
  loadFaceApiModels, getDetectorOptions,
} from '../../utils/face/modelLoader';
import { createBlinkDetector } from '../../utils/face/blinkDetection';
import {
  FACE_MATCH_THRESHOLD, isFaceMatch, getFaceDistance,
  detectFacesWithDescriptors, computeReferenceDescriptorFromImage,
} from '../../utils/face/faceMatcher';

// Everything below runs entirely in the browser: the webcam stream, the
// models, and every computed descriptor stay on-device. Nothing is uploaded.

const PHASE = {
  LOADING_MODELS: 'LOADING_MODELS',
  MODELS_ERROR: 'MODELS_ERROR',
  LOADING_REFERENCE: 'LOADING_REFERENCE',
  REFERENCE_ERROR: 'REFERENCE_ERROR',
  REQUESTING_CAMERA: 'REQUESTING_CAMERA',
  CAMERA_ERROR: 'CAMERA_ERROR',
  VERIFYING: 'VERIFYING',
  SUCCESS: 'SUCCESS',
  FAILED: 'FAILED',
};

const FACE_STATUS = {
  NONE: 'NONE',
  NO_FACE: 'NO_FACE',
  MULTIPLE_FACES: 'MULTIPLE_FACES',
  TOO_FAR: 'TOO_FAR',
  OK: 'OK',
};

// Sampled, not per animation frame (~16ms) — but fast enough that a natural
// blink (typically ~100-400ms closed-to-open) reliably lands at least one
// sample during the closed-eye moment. The previous 350ms interval was slow
// enough to routinely skip over blinks entirely.
const DETECTION_INTERVAL_MS = 100;
const MIN_FACE_WIDTH_RATIO = 0.18; // face bounding box must fill at least this much of frame width

const stopStream = (streamRef, videoRef) => {
  if (streamRef.current) {
    streamRef.current.getTracks().forEach((track) => track.stop());
    streamRef.current = null;
  }
  if (videoRef.current) {
    videoRef.current.srcObject = null;
  }
};

/**
 * Browser-only face verification: live webcam face vs. a reference photo
 * (e.g. the student's profile photo), gated by a landmark-based blink
 * (liveness) check.
 *
 * IMPORTANT — security scope: the blink check is a *basic* liveness signal
 * (it defeats a static printed/displayed photo held up to the camera). It is
 * NOT a substitute for real anti-spoofing (which needs depth/IR sensors,
 * texture/frequency analysis, challenge-response, etc.) and can be defeated
 * by more sophisticated presentation attacks (e.g. a video replay of a
 * blinking person). Do not present this as a high-security biometric system.
 *
 * Usage:
 *   <FaceVerification referenceImage={profilePhotoDataUrl} onSuccess={...} onFailure={...} />
 *
 * `referenceImage` is required — a data URL or same-origin URL of an
 * existing photo of the person's face. This component never captures or
 * enrolls a new reference photo itself; that keeps it usable anywhere a
 * trusted reference photo already exists (e.g. the student's profile photo)
 * without a second, competing "enrollment" concept.
 */
const FaceVerification = ({
  referenceImage = null,
  matchThreshold = FACE_MATCH_THRESHOLD,
  onSuccess = null,
  onFailure = null,
  onCancel = null,
  verificationTimeoutMs = 45000,
}) => {
  const [phase, setPhase] = useState(PHASE.LOADING_MODELS);
  const [errorMessage, setErrorMessage] = useState('');
  const [faceStatus, setFaceStatus] = useState(FACE_STATUS.NONE);
  const [blinkDetected, setBlinkDetected] = useState(false);
  const [faceMatch, setFaceMatch] = useState(false);
  const [matchDistance, setMatchDistance] = useState(null);

  const videoRef = useRef(null);
  const canvasRef = useRef(null);
  const streamRef = useRef(null);
  const referenceDescriptorRef = useRef(null);
  const blinkDetectorRef = useRef(createBlinkDetector());
  const intervalRef = useRef(null);
  const timeoutRef = useRef(null);
  const tickRunningRef = useRef(false);
  const mountedRef = useRef(true);
  const settledRef = useRef(false); // guards against double-firing onSuccess/onFailure

  const clearTimers = () => {
    if (intervalRef.current) {
      clearInterval(intervalRef.current);
      intervalRef.current = null;
    }
    if (timeoutRef.current) {
      clearTimeout(timeoutRef.current);
      timeoutRef.current = null;
    }
  };

  const startCamera = useCallback(async () => {
    setPhase(PHASE.REQUESTING_CAMERA);
    try {
      const stream = await navigator.mediaDevices.getUserMedia({
        video: { width: { ideal: 480 }, height: { ideal: 360 }, facingMode: 'user' },
        audio: false,
      });
      if (!mountedRef.current) {
        stream.getTracks().forEach((t) => t.stop());
        return;
      }
      streamRef.current = stream;
      if (videoRef.current) {
        videoRef.current.srcObject = stream;
        await videoRef.current.play();
      }
      setPhase(PHASE.VERIFYING);
    } catch (err) {
      let message = 'Could not access the camera.';
      if (err.name === 'NotAllowedError' || err.name === 'PermissionDeniedError') {
        message = 'Camera permission was denied. Please allow camera access and try again.';
      } else if (err.name === 'NotFoundError' || err.name === 'DevicesNotFoundError') {
        message = 'No camera was found on this device.';
      } else if (err.name === 'NotReadableError') {
        message = 'The camera is already in use by another application.';
      }
      setErrorMessage(message);
      setPhase(PHASE.CAMERA_ERROR);
    }
  }, []);

  // 1. Load models, 2. process the reference photo into a descriptor once,
  // 3. start the camera. The reference descriptor is computed exactly once
  // here — never recomputed from live camera frames.
  useEffect(() => {
    mountedRef.current = true;
    let cancelled = false;

    (async () => {
      if (!referenceImage) {
        setErrorMessage('Profile photo is required for face verification. Please update your profile photo.');
        setPhase(PHASE.REFERENCE_ERROR);
        return;
      }

      try {
        await loadFaceApiModels();
      } catch {
        if (!cancelled) {
          setErrorMessage('Failed to load face verification models. Check your connection and try again.');
          setPhase(PHASE.MODELS_ERROR);
        }
        return;
      }

      setPhase(PHASE.LOADING_REFERENCE);
      try {
        const descriptor = await computeReferenceDescriptorFromImage(referenceImage, getDetectorOptions());
        if (cancelled) return;
        if (!descriptor) {
          setErrorMessage('No face could be detected in your profile photo. Please update your profile photo.');
          setPhase(PHASE.REFERENCE_ERROR);
          return;
        }
        referenceDescriptorRef.current = descriptor;
      } catch {
        if (!cancelled) {
          setErrorMessage('Failed to load your profile photo for face verification. Please try again.');
          setPhase(PHASE.REFERENCE_ERROR);
        }
        return;
      }

      if (!cancelled) await startCamera();
    })();

    return () => {
      cancelled = true;
      mountedRef.current = false;
      clearTimers();
      stopStream(streamRef, videoRef);
    };
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, []);

  const finish = useCallback((result) => {
    if (settledRef.current) return;
    settledRef.current = true;
    clearTimers();
    stopStream(streamRef, videoRef);
    if (result.success) {
      setPhase(PHASE.SUCCESS);
      onSuccess?.(result);
    } else {
      setErrorMessage(result.message);
      setPhase(PHASE.FAILED);
      onFailure?.(result);
    }
  }, [onSuccess, onFailure]);

  // Live detection loop — runs on a fixed interval (not per rAF) once verifying.
  useEffect(() => {
    if (phase !== PHASE.VERIFYING) return undefined;

    settledRef.current = false;
    blinkDetectorRef.current.reset();

    timeoutRef.current = setTimeout(() => {
      finish({
        success: false,
        message: !blinkDetectorRef.current.hasBlinked()
          ? 'No blink was detected in time. Please look at the camera and blink naturally.'
          : 'Face did not match your profile photo within the time limit.',
        blinkDetected: blinkDetectorRef.current.hasBlinked(),
        faceMatch: false,
      });
    }, verificationTimeoutMs);

    intervalRef.current = setInterval(async () => {
      if (tickRunningRef.current || !mountedRef.current) return;
      const video = videoRef.current;
      if (!video || video.readyState < 2) return;

      tickRunningRef.current = true;
      try {
        const results = await detectFacesWithDescriptors(video, getDetectorOptions());
        if (!mountedRef.current) return;

        if (results.length === 0) {
          setFaceStatus(FACE_STATUS.NO_FACE);
          setFaceMatch(false);
          drawOverlay(canvasRef.current, video, null, null);
          return;
        }
        if (results.length > 1) {
          setFaceStatus(FACE_STATUS.MULTIPLE_FACES);
          setFaceMatch(false);
          drawOverlay(canvasRef.current, video, null, null);
          return;
        }

        const result = results[0];
        const box = result.detection.box;
        const tooFar = box.width / video.videoWidth < MIN_FACE_WIDTH_RATIO;

        if (tooFar) {
          setFaceStatus(FACE_STATUS.TOO_FAR);
          setFaceMatch(false);
          drawOverlay(canvasRef.current, video, box, 'amber');
          return;
        }

        setFaceStatus(FACE_STATUS.OK);

        blinkDetectorRef.current.update(result.landmarks);
        const hasBlinked = blinkDetectorRef.current.hasBlinked();
        setBlinkDetected(hasBlinked);

        // Compared against the descriptor computed once from referenceImage
        // above — never recomputed from a live frame.
        const distance = getFaceDistance(referenceDescriptorRef.current, result.descriptor);
        const matched = isFaceMatch(referenceDescriptorRef.current, result.descriptor, matchThreshold);
        setMatchDistance(distance);
        setFaceMatch(matched);
        drawOverlay(canvasRef.current, video, box, matched ? 'green' : 'red');

        // Success requires both conditions true together, per the spec.
        if (matched && hasBlinked) {
          finish({
            success: true,
            distance,
            blinkDetected: true,
            faceMatch: true,
            capturedAt: new Date().toISOString(),
          });
        }
      } catch {
        // transient detection error on a single frame — ignore and retry next tick
      } finally {
        tickRunningRef.current = false;
      }
    }, DETECTION_INTERVAL_MS);

    return () => {
      clearTimers();
    };
  }, [phase, matchThreshold, verificationTimeoutMs, finish]);

  const retry = () => {
    settledRef.current = false;
    setErrorMessage('');
    setFaceStatus(FACE_STATUS.NONE);
    setBlinkDetected(false);
    setFaceMatch(false);
    setMatchDistance(null);
    // referenceDescriptorRef is kept — it was computed once from
    // referenceImage and doesn't need recomputing on a retry.
    startCamera();
  };

  const cancel = () => {
    clearTimers();
    stopStream(streamRef, videoRef);
    onCancel?.();
  };

  const faceStatusMessage = (() => {
    if (faceStatus === FACE_STATUS.NONE) return 'Looking for your face...';
    if (faceStatus === FACE_STATUS.NO_FACE) return 'No face detected. Look at the camera.';
    if (faceStatus === FACE_STATUS.MULTIPLE_FACES) return 'Multiple faces detected. Only one person should be in frame.';
    if (faceStatus === FACE_STATUS.TOO_FAR) return 'Move closer to the camera.';
    // faceStatus === OK
    if (blinkDetected && faceMatch) return 'Blink detected ✓  Face verified ✓';
    if (blinkDetected && !faceMatch) return 'Blink detected ✓ — checking your face against your profile photo...';
    if (faceMatch && !blinkDetected) return 'Face matched. Blink naturally to confirm liveness.';
    return 'Look at the camera and blink naturally.';
  })();

  const showVideo = phase === PHASE.VERIFYING;

  return (
    <div className="face-verification">
      {/* Always mounted (never conditionally rendered) so videoRef is attached
          to a real <video> element before startCamera() ever runs — otherwise
          the very first stream would have nowhere to attach to. Visibility is
          toggled with CSS instead of mount/unmount. */}
      <div style={{ position: 'relative', width: 480, maxWidth: '100%', margin: '0 auto', display: showVideo ? 'block' : 'none' }}>
        <video
          ref={videoRef}
          muted
          playsInline
          style={{ width: '100%', borderRadius: 12, background: '#000', transform: 'scaleX(-1)' }}
          onLoadedMetadata={() => {
            if (canvasRef.current && videoRef.current) {
              canvasRef.current.width = videoRef.current.videoWidth;
              canvasRef.current.height = videoRef.current.videoHeight;
            }
          }}
        />
        <canvas
          ref={canvasRef}
          style={{
            position: 'absolute', top: 0, left: 0, width: '100%', height: '100%',
            pointerEvents: 'none', transform: 'scaleX(-1)',
          }}
        />
      </div>

      {(phase === PHASE.LOADING_MODELS || phase === PHASE.LOADING_REFERENCE || phase === PHASE.REQUESTING_CAMERA) && (
        <div className="text-center py-5">
          <FontAwesomeIcon icon={faSpinner} spin style={{ fontSize: '2.5rem', color: 'var(--color-primary)' }} />
          <p className="text-muted mt-3 mb-0">
            {phase === PHASE.LOADING_MODELS && 'Loading face verification models...'}
            {phase === PHASE.LOADING_REFERENCE && 'Loading your profile photo...'}
            {phase === PHASE.REQUESTING_CAMERA && 'Requesting camera access...'}
          </p>
        </div>
      )}

      {(phase === PHASE.MODELS_ERROR || phase === PHASE.REFERENCE_ERROR || phase === PHASE.CAMERA_ERROR) && (
        <div className="text-center py-5">
          <FontAwesomeIcon icon={phase === PHASE.CAMERA_ERROR ? faVideoSlash : faExclamationTriangle}
            style={{ fontSize: '2.5rem', color: 'var(--color-danger)' }} />
          <p className="text-muted mt-3 mb-3">{errorMessage}</p>
          {phase !== PHASE.REFERENCE_ERROR && (
            <button type="button" className="btn btn-primary" onClick={phase === PHASE.MODELS_ERROR ? () => window.location.reload() : startCamera}>
              <FontAwesomeIcon icon={faRedo} /> Try Again
            </button>
          )}
          {onCancel && (
            <button type="button" className="btn btn-outline-secondary ms-2" onClick={cancel}>
              Cancel
            </button>
          )}
        </div>
      )}

      {showVideo && (
        <div>
          <p className="text-center text-muted mb-3" style={{ fontSize: '0.85rem' }}>
            Verifying your face against your profile photo.
          </p>
          <div className="d-flex justify-content-center gap-4 mb-3">
            <StepIcon icon={faIdCard} label="Position" active={faceStatus === FACE_STATUS.OK} />
            <StepIcon icon={faEye} label="Blink" active={blinkDetected} />
            <StepIcon icon={faUserCheck} label="Match" active={faceMatch} />
          </div>

          <p className="text-center text-muted mt-3 mb-1">{faceStatusMessage}</p>
          {matchDistance !== null && (
            <p className="text-center mb-2" style={{ fontSize: '0.8rem', color: 'var(--color-text-light)' }}>
              match distance: {matchDistance.toFixed(3)} (threshold {matchThreshold})
            </p>
          )}

          {onCancel && (
            <div className="text-center mt-3">
              <button type="button" className="btn btn-outline-secondary" onClick={cancel}>
                Cancel
              </button>
            </div>
          )}
        </div>
      )}

      {phase === PHASE.SUCCESS && (
        <div className="text-center py-5">
          <FontAwesomeIcon icon={faCheckCircle} style={{ fontSize: '3rem', color: 'var(--color-success)' }} />
          <h5 className="fw-bold mt-3" style={{ color: 'var(--color-success)' }}>Verification Successful</h5>
          <p className="text-muted mb-0">Face matched your profile photo and blink liveness confirmed.</p>
        </div>
      )}

      {phase === PHASE.FAILED && (
        <div className="text-center py-5">
          <FontAwesomeIcon icon={faTimesCircle} style={{ fontSize: '3rem', color: 'var(--color-danger)' }} />
          <h5 className="fw-bold mt-3" style={{ color: 'var(--color-danger)' }}>Verification Failed</h5>
          <p className="text-muted mb-3">{errorMessage}</p>
          <div className="d-flex justify-content-center gap-2">
            <button type="button" className="btn btn-primary" onClick={retry}>
              <FontAwesomeIcon icon={faRedo} /> Try Again
            </button>
            {onCancel && (
              <button type="button" className="btn btn-outline-secondary" onClick={cancel}>
                Cancel
              </button>
            )}
          </div>
        </div>
      )}
    </div>
  );
};

const StepIcon = ({ icon, label, active }) => (
  <div className="text-center">
    <FontAwesomeIcon icon={icon} style={{ fontSize: '1.4rem', color: active ? 'var(--color-success)' : 'var(--color-text-light)' }} />
    <small className="d-block text-muted">{label}</small>
  </div>
);
StepIcon.propTypes = { icon: PropTypes.object.isRequired, label: PropTypes.string.isRequired, active: PropTypes.bool };

function drawOverlay(canvas, video, box, color) {
  if (!canvas) return;
  const ctx = canvas.getContext('2d');
  ctx.clearRect(0, 0, canvas.width, canvas.height);
  if (!box) return;
  const colors = { green: '#22c55e', amber: '#f59e0b', red: '#ef4444' };
  ctx.strokeStyle = colors[color] || '#22c55e';
  ctx.lineWidth = 3;
  ctx.strokeRect(box.x, box.y, box.width, box.height);
}

FaceVerification.propTypes = {
  /** Required: an existing photo of the person's face (data URL or same-origin URL) — e.g. the student's profile photo. Never captured/enrolled by this component. */
  referenceImage: PropTypes.string,
  /** Euclidean descriptor distance below which two faces are considered a match. */
  matchThreshold: PropTypes.number,
  /** Called once with { success: true, distance, blinkDetected, faceMatch, capturedAt }. */
  onSuccess: PropTypes.func,
  /** Called once with { success: false, message, blinkDetected, faceMatch }. */
  onFailure: PropTypes.func,
  /** If provided, a Cancel button is shown and this is called on cancel. */
  onCancel: PropTypes.func,
  /** Overall time budget for the live verification step before it fails. */
  verificationTimeoutMs: PropTypes.number,
};

export default FaceVerification;
