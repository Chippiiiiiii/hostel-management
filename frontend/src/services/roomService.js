import api from './api';

const roomService = {
  getBuildings: async () => {
    const response = await api.get('/warden/rooms/buildings');
    return { data: response.data.data || [] };
  },

  getBuildingsStudent: async () => {
    const response = await api.get('/student/rooms/buildings');
    return { data: response.data.data || [] };
  },

  getConfig: async () => {
    const response = await api.get('/warden/rooms/config');
    return { data: response.data.data || { maxRoomsPerFloor: 10, maxMembersPerRoom: 6 } };
  },

  updateConfig: async (config) => {
    await api.put('/warden/rooms/config', config);
    return { data: config };
  },

  renameBuilding: async (buildingId, newName) => {
    const response = await api.put(`/warden/rooms/buildings/${buildingId}/rename`, { name: newName });
    return { data: response.data.data };
  },

  updateBuildingType: async (buildingId, type) => {
    const response = await api.put(`/warden/rooms/buildings/${buildingId}/type`, { type });
    return { data: response.data.data };
  },

  updateRoomMaxMembers: async (roomId, maxMembers) => {
    await api.put(`/warden/rooms/${roomId}/max-members`, { maxMembers });
  },

  addFloor: async (buildingId) => {
    const response = await api.post(`/warden/rooms/buildings/${buildingId}/floors`);
    return { data: response.data.data };
  },

  removeFloor: async (buildingId, floorNumber) => {
    await api.delete(`/warden/rooms/buildings/${buildingId}/floors/${floorNumber}`);
  },

  addRoomToFloor: async (buildingId, floorNumber) => {
    await api.post(`/warden/rooms/buildings/${buildingId}/floors/${floorNumber}/rooms`);
  },

  removeLastRoomFromFloor: async (buildingId, floorNumber) => {
    await api.delete(`/warden/rooms/buildings/${buildingId}/floors/${floorNumber}/rooms/last`);
  },

  getAllocations: async () => {
    const response = await api.get('/warden/rooms/allocations');
    return { data: response.data.data || [] };
  },

  getAllocationsStudent: async () => {
    const response = await api.get('/student/rooms/allocations');
    return { data: response.data.data || [] };
  },

  getStudentAllocation: async () => {
    const response = await api.get('/student/rooms/allocation');
    return { data: response.data.data || null };
  },

  allocateStudent: async (roomId, studentData) => {
    const response = await api.post(`/warden/rooms/${roomId}/allocate`, studentData);
    return { data: response.data.data };
  },

  allocateStudentSelf: async (roomId) => {
    const response = await api.post('/student/rooms/allocate', { roomId });
    return { data: response.data.data };
  },

  removeAllocation: async (studentEmail) => {
    await api.delete(`/warden/rooms/allocations/${encodeURIComponent(studentEmail)}`);
  },

  addBuilding: async (name, type, gender) => {
    const response = await api.post('/warden/rooms/buildings', { name, type, gender });
    return { data: response.data.data };
  },

  removeBuilding: async (buildingId) => {
    await api.delete(`/warden/rooms/buildings/${buildingId}`);
  },

  updateBuildingGender: async (buildingId, gender) => {
    const response = await api.put(`/warden/rooms/buildings/${buildingId}/gender`, { gender });
    return { data: response.data.data };
  },

  // ==================== Floor / room department ====================

  setFloorDepartment: async (buildingId, floorNumber, department) => {
    const response = await api.put(`/warden/rooms/buildings/${buildingId}/floors/${floorNumber}/department`, { department });
    return { data: response.data.data };
  },

  setRoomDepartmentOverride: async (roomId, department) => {
    await api.put(`/warden/rooms/${roomId}/department`, { department });
  },

  removeRoomDepartmentOverride: async (roomId) => {
    await api.delete(`/warden/rooms/${roomId}/department`);
  },

  // ==================== Room number editing ====================

  updateRoomNumber: async (roomId, roomNumber) => {
    await api.put(`/warden/rooms/${roomId}/number`, { roomNumber });
  },

  // ==================== Bulk allocation ====================

  bulkAllocate: async (buildingId, floorNumber) => {
    const params = floorNumber !== null && floorNumber !== undefined ? { floorNumber } : {};
    const response = await api.post(`/warden/rooms/buildings/${buildingId}/auto-allocate`, null, { params });
    return { data: response.data.data };
  },
};

export default roomService;
