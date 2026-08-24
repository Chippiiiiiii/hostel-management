// Persists a student's enrolled reference face photo (data URL) to
// localStorage, keyed per account, so they only have to capture it once
// per browser/device instead of on every attendance mark. Browser-only —
// nothing here is ever sent to the backend.

const PREFIX = 'face_reference_';

const keyFor = (userKey) => `${PREFIX}${userKey}`;

export const getReferenceImage = (userKey) => {
  if (!userKey) return null;
  return localStorage.getItem(keyFor(userKey));
};

export const saveReferenceImage = (userKey, dataUrl) => {
  if (!userKey || !dataUrl) return;
  localStorage.setItem(keyFor(userKey), dataUrl);
};

export const clearReferenceImage = (userKey) => {
  if (!userKey) return;
  localStorage.removeItem(keyFor(userKey));
};
