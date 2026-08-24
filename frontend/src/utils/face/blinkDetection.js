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

// A fixed absolute EAR cutoff (e.g. "closed below 0.23") does not transfer
// well: face-api.js's landmark net, camera quality, face-to-camera distance,
// and even head tilt all shift what a given person's "eyes open" EAR reads
// as. Two different students on two different webcams can have open-eye EAR
// values that don't even overlap the same fixed threshold.
//
// Instead we track a running baseline of *this session's* open-eye EAR (an
// exponential moving average, updated only while we believe the eyes are
// open) and classify closed/open *relative* to that baseline, with
// hysteresis between the two cutoffs so noise near the boundary can't
// flip-flop the state.
const CLOSED_RATIO = 0.72; // EAR must fall below 72% of the open baseline to register as closing
const REOPEN_RATIO = 0.85; // EAR must recover above 85% of the open baseline to register as reopened
const BASELINE_EMA_ALPHA = 0.25; // how fast the baseline adapts to newly observed open-eye EAR
const MIN_BASELINE_SAMPLES = 3; // open-eye samples required before we trust the baseline enough to classify blinks
const MIN_PLAUSIBLE_EAR = 0.08; // guards the baseline against landmark noise/occlusion collapsing it near zero

/**
 * Stateful blink detector. Feed it landmarks on every throttled detection
 * tick; it flags `hasBlinked() === true` (sticky, does not reset itself) the
 * first time it observes a full OPEN -> CLOSED -> OPEN cycle relative to the
 * adaptively-tracked baseline.
 */
export const createBlinkDetector = () => {
  let baseline = null;
  let baselineSamples = 0;
  let eyesClosed = false;
  let blinkDetected = false;

  const updateBaseline = (ear) => {
    if (ear < MIN_PLAUSIBLE_EAR) return;
    baseline = baseline == null ? ear : baseline * (1 - BASELINE_EMA_ALPHA) + ear * BASELINE_EMA_ALPHA;
    baselineSamples += 1;
  };

  return {
    reset() {
      baseline = null;
      baselineSamples = 0;
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
        return { ear: null, baseline, blinkDetected };
      }
      const ear = (leftEAR + rightEAR) / 2;

      if (baseline == null || baselineSamples < MIN_BASELINE_SAMPLES) {
        // Still calibrating what "open" looks like for this face/camera. Only
        // sample while presumed-open (never while mid-blink) so a blink that
        // happens to land in these first frames doesn't get baked into the
        // baseline as if it were the open-eye value.
        if (!eyesClosed) updateBaseline(ear);
        return { ear, baseline, blinkDetected };
      }

      const closedCutoff = baseline * CLOSED_RATIO;
      const openCutoff = baseline * REOPEN_RATIO;

      if (!eyesClosed && ear < closedCutoff) {
        eyesClosed = true;
      } else if (eyesClosed && ear > openCutoff) {
        eyesClosed = false;
        blinkDetected = true; // full CLOSED -> OPEN cycle observed
      } else if (!eyesClosed) {
        // Let the baseline drift slowly with natural lighting/pose changes,
        // but only while the eyes are open — never while closed, or a held
        // eyes-shut pose would drag "open" down toward "closed".
        updateBaseline(ear);
      }

      return { ear, baseline, blinkDetected };
    },
  };
};
