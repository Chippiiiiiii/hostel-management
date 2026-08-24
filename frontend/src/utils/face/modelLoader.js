import * as faceapi from 'face-api.js';

// Model weights are vendored under public/models (see public/models/README not
// required — files are fetched relative to the deployed app's base URL, so this
// works unchanged on Vercel).
const MODEL_URL = `${import.meta.env.BASE_URL}models`;

let loadPromise = null;

/**
 * Loads the three nets needed for face match + blink detection:
 * a lightweight face detector, the 68-point landmark model (for EAR/blink),
 * and the face recognition model (128-d descriptors for matching).
 * Idempotent — safe to call from multiple mounted components; only fetches once.
 */
export const loadFaceApiModels = () => {
  if (loadPromise) return loadPromise;

  loadPromise = Promise.all([
    faceapi.nets.tinyFaceDetector.loadFromUri(MODEL_URL),
    faceapi.nets.faceLandmark68Net.loadFromUri(MODEL_URL),
    faceapi.nets.faceRecognitionNet.loadFromUri(MODEL_URL),
  ]).catch((err) => {
    loadPromise = null; // allow a retry (e.g. after the user fixes their connection)
    throw err;
  });

  return loadPromise;
};

export const areModelsLoaded = () =>
  faceapi.nets.tinyFaceDetector.isLoaded &&
  faceapi.nets.faceLandmark68Net.isLoaded &&
  faceapi.nets.faceRecognitionNet.isLoaded;

// Small input size keeps the tiny face detector fast enough to run on a
// throttled interval on modest hardware.
export const getDetectorOptions = () =>
  new faceapi.TinyFaceDetectorOptions({ inputSize: 224, scoreThreshold: 0.5 });
