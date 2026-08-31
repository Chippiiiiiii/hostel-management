import api from './api';

const adminService = {
  createWarden: async (warden) => {
    const response = await api.post('/admin/wardens', warden);
    return { data: response.data.data };
  },

  getWardens: async () => {
    const response = await api.get('/admin/wardens');
    return { data: response.data.data || [] };
  },

  setWardenStatus: async (wardenId, enabled) => {
    const response = await api.put(`/admin/wardens/${wardenId}/status`, { enabled });
    return { data: response.data.data };
  },

  // ==================== Warden <-> buildings ====================

  getWardenBuildings: async (wardenId) => {
    const response = await api.get(`/admin/wardens/${wardenId}/buildings`);
    return { data: response.data.data || [] };
  },

  assignWardenBuilding: async (wardenId, buildingId) => {
    const response = await api.post(`/admin/wardens/${wardenId}/buildings`, { buildingId });
    return { data: response.data.data || [] };
  },

  removeWardenBuilding: async (wardenId, buildingId) => {
    const response = await api.delete(`/admin/wardens/${wardenId}/buildings/${buildingId}`);
    return { data: response.data.data || [] };
  },

  createSecurityGuard: async (guard) => {
    const response = await api.post('/admin/security-guards', guard);
    return { data: response.data.data };
  },

  getSecurityGuards: async () => {
    const response = await api.get('/admin/security-guards');
    return { data: response.data.data || [] };
  },

  setSecurityGuardStatus: async (guardId, enabled) => {
    const response = await api.put(`/admin/security-guards/${guardId}/status`, { enabled });
    return { data: response.data.data };
  },

  // ==================== Year -> hostel eligibility ====================

  getYearHostelConfig: async () => {
    const response = await api.get('/admin/year-hostels');
    return { data: response.data.data || {} };
  },

  addYearHostel: async (year, buildingId) => {
    await api.post('/admin/year-hostels', { year, buildingId });
  },

  removeYearHostel: async (year, buildingId) => {
    await api.delete(`/admin/year-hostels/${year}/${buildingId}`);
  },
};

export default adminService;
