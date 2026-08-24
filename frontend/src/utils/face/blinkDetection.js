// Eye Aspect Ratio (EAR) based blink detection.
//
// EAR = (||p2-p6|| + ||p3-p5||) / (2 * ||p1-p4||)
//
// where p1..p6 are the 6 landmark points face-api.js returns for one eye
// (outer corner, two upper-lid points, inner corner, two lower-lid points).
// EAR stays roughly constant while the eye is open and drops sharply toward
// zero when it closes. Reference: Soukupova & Cech, "Real-Time Eye Blink
// Detection using Facial Landmarks" (2016).
//
// This is a liveness *signal*, not a face-presence check — see the caller
// (FaceVerification) for how it's combined with face matching.

const distance = (a, b) => Math.hypot(a.x - b.x, a.y - b.y);

export const calculateEAR = (eyePoints) => {
  if (!eyePoints || eyePoints.length !== 6) return null;
  const [p1, p2, p3, p4, p5, p6] = eyePoints;
  const horizontal = distance(p1, p4);
  if (horizontal === 0) return null;
  const vertical1 = distance(p2, p6);
  const vertical2 = distance(p3, p5);
  return (vertical1 + vertical2) / (2 * horizontal);
};

// Below this EAR the eyes are considered closed.
export const EAR_CLOSED_THRESHOLD = 0.23;
// Above this EAR the eyes are considered fully open again. The gap between
// the two thresholds (hysteresis) avoids flicker/double-counting around the
// boundary when EAR hovers near a single cutoff.
export const EAR_OPEN_THRESHOLD = 0.28;

/**
 * Stateful blink detector. Feed it landmarks on every throttled detection
 * tick; it flags `blinkDetected = true` (sticky, does not reset itself) the
 * first time it observes a full closed -> open cycle.
 */
export const createBlinkDetector = () => {
  let eyesClosed = false;
  let blinkDetected = false;

  return {
    reset() {
      eyesClosed = false;
      blinkDetected = false;
    },
    hasBlinked() {
      return blinkDetected;
    },
    /** @param {import('face-api.js').FaceLandmarks68} landmarks */
    update(landmarks) {
      const leftEAR = calculateEAR(landmarks.getLeftEye());
      const rightEAR = calculateEAR(landmarks.getRightEye());
      if (leftEAR == null || rightEAR == null) {
        return { ear: null, blinkDetected };
      }
      const ear = (leftEAR + rightEAR) / 2;

      if (ear < EAR_CLOSED_THRESHOLD) {
        eyesClosed = true;
      } else if (ear > EAR_OPEN_THRESHOLD && eyesClosed) {
        eyesClosed = false;
        blinkDetected = true;
      }

      return { ear, blinkDetected };
    },
  };
};
