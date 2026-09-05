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

  updateIdCardPhoto: async (idCardPhoto) => {
    const response = await api.put('/student/profile/id-card-photo', { idCardPhoto });
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

  cancelOutpass: async (id) => {
    const response = await api.delete(`/student/outpass/${id}`);
    return response.data;
  },

  getWardenDashboardStats: async () => {
    const response = await api.get('/warden/dashboard/stats');
    return response.data;
  },

  getRoommates: async () => {
    const response = await api.get('/student/rooms/roommates');
    return response.data;
  },

  getStudentAnnouncements: async () => {
    const response = await api.get('/student/announcements');
    return response.data;
  },

  getWardenAnnouncements: async () => {
    const response = await api.get('/warden/announcements');
    return response.data;
  },

  createAnnouncement: async (data) => {
    const response = await api.post('/warden/announcements', data);
    return response.data;
  },

  deleteAnnouncement: async (id) => {
    const response = await api.delete(`/warden/announcements/${id}`);
    return response.data;
  },

  bulkApproveOutpasses: async (ids) => {
    const response = await api.put('/warden/outpass/bulk-approve', { ids });
    return response.data;
  },

  bulkDeclineOutpasses: async (ids, reason) => {
    const response = await api.put('/warden/outpass/bulk-decline', { ids, reason });
    return response.data;
  },
};

export default outpassService;
