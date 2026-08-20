import api from './api';

const complaintService = {
  create: async (data) => {
    const response = await api.post('/student/complaints', data);
    return response.data;
  },

  getMyComplaints: async () => {
    const response = await api.get('/student/complaints');
    return response.data;
  },

  getAllComplaints: async (status) => {
    const params = status ? { status } : {};
    const response = await api.get('/warden/complaints', { params });
    return response.data;
  },

  getStats: async () => {
    const response = await api.get('/warden/complaints/stats');
    return response.data;
  },

  updateComplaint: async (id, data) => {
    const response = await api.put(`/warden/complaints/${id}`, data);
    return response.data;
  },
};

export default complaintService;
