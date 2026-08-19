import api from './api';

const outpassService = {
  // Student APIs
  createOutpass: async (outpassData) => {
    const response = await api.post('/student/outpass', outpassData);
    return response.data;
  },

  getStudentProfile: async () => {
    const response = await api.get('/student/profile');
    return response.data;
  },

  updateStudentProfile: async (profileData) => {
    const response = await api.put('/student/profile', profileData);
    return response.data;
  },

  getOutpassHistory: async () => {
    const response = await api.get('/student/outpass/history');
    return response.data;
  },

  getOutpassById: async (id) => {
    const response = await api.get(`/student/outpass/${id}`);
    return response.data;
  },

  // Warden APIs
  getPendingOutpasses: async () => {
    const response = await api.get('/warden/outpass/pending');
    return response.data;
  },

  approveOutpass: async (id, data = {}) => {
    const response = await api.put(`/warden/outpass/${id}/approve`, data);
    return response.data;
  },

  declineOutpass: async (id, data) => {
    const response = await api.put(`/warden/outpass/${id}/decline`, data);
    return response.data;
  },

  getWardenHistory: async () => {
    const response = await api.get('/warden/outpass/history');
    return response.data;
  },

  getStudentStats: async (studentId) => {
    const response = await api.get(`/warden/student/${studentId}/stats`);
    return response.data;
  },

  // Security Guard APIs
  getActiveOutpasses: async () => {
    const response = await api.get('/security/outpass/active');
    return response.data;
  },

  getTodayOutpasses: async () => {
    const response = await api.get('/security/outpass/today');
    return response.data;
  },

  getDepartedOutpasses: async () => {
    const response = await api.get('/security/outpass/departed');
    return response.data;
  },

  getSecurityOutpassById: async (id) => {
    const response = await api.get(`/security/outpass/${id}`);
    return response.data;
  },

  markDeparture: async (id) => {
    const response = await api.put(`/security/outpass/${id}/mark-departure`);
    return response.data;
  },

  markReturn: async (id) => {
    const response = await api.put(`/security/outpass/${id}/mark-return`);
    return response.data;
  },
};

export default outpassService;
