import api from './api';

const ALERT_KEY = 'attendance_alert';

let broadcastChannel = null;
try {
  broadcastChannel = new BroadcastChannel('attendance_channel');
} catch (e) {
  // BroadcastChannel not supported
}

const attendanceService = {
  getActiveSession: async () => {
    const response = await api.get('/student/attendance/session');
    return { data: response.data.data || null };
  },

  // buildingId is required for every warden-facing call below -- a warden may now be
  // assigned to more than one hostel, so there is no implicit "my building" the backend
  // can infer (see backend/AGENTS.md). Student-facing calls further down are unchanged:
  // the server resolves the student's own hostel itself.
  getActiveSessionWarden: async (buildingId) => {
    const response = await api.get('/warden/attendance/session', { params: { buildingId } });
    return { data: response.data.data || null };
  },

  startSession: async (buildingId) => {
    const response = await api.post('/warden/attendance/start', { buildingId });
    return { data: response.data.data };
  },

  stopSession: async (buildingId) => {
    await api.post('/warden/attendance/stop', { buildingId });
  },

  getSessionRecords: async (sessionId) => {
    const response = await api.get(`/warden/attendance/session/${sessionId}/records`);
    return { data: response.data.data || [] };
  },

  markAttendance: async (method, locationData) => {
    const response = await api.post('/student/attendance/mark', {
      method,
      latitude: locationData?.latitude || null,
      longitude: locationData?.longitude || null,
      distance: locationData?.distance || null,
    });
    return { data: response.data.data };
  },

  getAttendanceHistory: async () => {
    const response = await api.get('/student/attendance/history');
    return { data: response.data.data || [] };
  },

  getTodayStatus: async () => {
    const response = await api.get('/student/attendance/today');
    return { data: response.data.data || null };
  },

  getStats: async () => {
    const response = await api.get('/student/attendance/stats');
    return { data: response.data.data || { total: 0, present: 0, absent: 0, percentage: 0 } };
  },

  checkWifiConnection: async () => {
    try {
      const response = await api.get('/student/attendance/verify-wifi');
      return { connected: response.data.data?.connected || false, ip: response.data.data?.ip };
    } catch {
      return { connected: false };
    }
  },

  getAttendanceConfig: async (buildingId) => {
    const response = await api.get('/warden/attendance/config', { params: { buildingId } });
    return { data: response.data.data };
  },

  updateAttendanceConfig: async (buildingId, config) => {
    await api.put('/warden/attendance/config', { ...config, buildingId });
  },

  getHostelLocation: async () => {
    const response = await api.get('/student/attendance/location');
    return { data: response.data.data };
  },

  checkGeolocation: async () => {
    if (!navigator.geolocation) {
      throw new Error('Geolocation not supported');
    }

    const location = await api.get('/student/attendance/location').then(r => r.data.data);

    return new Promise((resolve, reject) => {
      navigator.geolocation.getCurrentPosition(
        (position) => {
          const distance = getDistanceMeters(
            position.coords.latitude,
            position.coords.longitude,
            location.latitude,
            location.longitude
          );

          resolve({
            withinRange: distance <= location.radius,
            distance: Math.round(distance),
            latitude: position.coords.latitude,
            longitude: position.coords.longitude,
          });
        },
        () => {
          reject(new Error('Location access denied'));
        },
        { enableHighAccuracy: true, timeout: 10000 }
      );
    });
  },

  getAttendanceReport: async (from, to) => {
    const response = await api.get(`/warden/attendance/report?from=${from}&to=${to}`);
    return { data: response.data.data || [] };
  },

  // buildingName is optional and cosmetic only (which hostel's session this is, useful
  // for a warden managing more than one) -- omit it and the notification reads as before.
  notifySessionStart: (buildingName) => {
    const alert = { type: 'SESSION_STARTED', timestamp: Date.now() };
    localStorage.setItem(ALERT_KEY, JSON.stringify(alert));
    if (broadcastChannel) {
      broadcastChannel.postMessage(alert);
    }
    if ('Notification' in window && Notification.permission === 'granted') {
      new Notification('Hostel Management', {
        body: buildingName
          ? `Attendance is now open for ${buildingName}! Mark your attendance.`
          : 'Attendance is now open! Mark your attendance.',
        icon: '/vite.svg',
      });
    }
  },

  requestNotificationPermission: async () => {
    if ('Notification' in window && Notification.permission === 'default') {
      return Notification.requestPermission();
    }
    return 'Notification' in window ? Notification.permission : 'denied';
  },

  onSessionAlert: (callback) => {
    const handleBroadcast = (event) => {
      if (event.data?.type === 'SESSION_STARTED') {
        callback(event.data);
      }
    };

    const handleStorage = (event) => {
      if (event.key === ALERT_KEY) {
        try {
          const data = JSON.parse(event.newValue);
          if (data?.type === 'SESSION_STARTED') {
            callback(data);
          }
        } catch (e) {
          // ignore parse errors
        }
      }
    };

    if (broadcastChannel) {
      broadcastChannel.addEventListener('message', handleBroadcast);
    }
    window.addEventListener('storage', handleStorage);

    return () => {
      if (broadcastChannel) {
        broadcastChannel.removeEventListener('message', handleBroadcast);
      }
      window.removeEventListener('storage', handleStorage);
    };
  },

  verifyBiometric: async () => {
    if (!window.PublicKeyCredential) {
      throw new Error('Biometric verification not supported on this device');
    }

    try {
      const credential = await navigator.credentials.create({
        publicKey: {
          challenge: crypto.getRandomValues(new Uint8Array(32)),
          rp: { name: 'Hostel Management' },
          user: {
            id: crypto.getRandomValues(new Uint8Array(16)),
            name: 'student',
            displayName: 'Student',
          },
          pubKeyCredParams: [{ alg: -7, type: 'public-key' }],
          authenticatorSelection: {
            authenticatorAttachment: 'platform',
            userVerification: 'required',
          },
          timeout: 60000,
        },
      });
      return { verified: !!credential };
    } catch (error) {
      throw new Error('Biometric verification failed or cancelled');
    }
  },
};

function getDistanceMeters(lat1, lon1, lat2, lon2) {
  const R = 6371000;
  const dLat = ((lat2 - lat1) * Math.PI) / 180;
  const dLon = ((lon2 - lon1) * Math.PI) / 180;
  const a =
    Math.sin(dLat / 2) * Math.sin(dLat / 2) +
    Math.cos((lat1 * Math.PI) / 180) *
      Math.cos((lat2 * Math.PI) / 180) *
      Math.sin(dLon / 2) *
      Math.sin(dLon / 2);
  const c = 2 * Math.atan2(Math.sqrt(a), Math.sqrt(1 - a));
  return R * c;
}

export default attendanceService;
