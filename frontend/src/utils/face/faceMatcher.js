import * as faceapi from 'face-api.js';

// face-api.js's own docs/examples use 0.6 euclidean distance as the default
// cutoff for "same person" on its 128-d recognition descriptors. Lower = stricter.
export const FACE_MATCH_THRESHOLD = 0.6;

export const getFaceDistance = (descriptorA, descriptorB) =>
  faceapi.euclideanDistance(descriptorA, descriptorB);

export const isFaceMatch = (descriptorA, descriptorB, threshold = FACE_MATCH_THRESHOLD) =>
  getFaceDistance(descriptorA, descriptorB) < threshold;

/**
 * Detects faces in a single video/image frame and returns landmarks +
 * descriptor for each. Callers decide what to do with 0 / 1 / many results.
 */
export const detectFacesWithDescriptors = async (input, detectorOptions) =>
  faceapi.detectAllFaces(input, detectorOptions).withFaceLandmarks().withFaceDescriptors();

/**
 * Computes a single reference descriptor from a static image (e.g. an
 * already-uploaded profile photo). Returns null if no face was found.
 */
export const computeReferenceDescriptorFromImage = async (imageSrc, detectorOptions) => {
  const img = await faceapi.fetchImage(imageSrc);
  const result = await faceapi
    .detectSingleFace(img, detectorOptions)
    .withFaceLandmarks()
    .withFaceDescriptor();
  return result ? result.descriptor : null;
};
