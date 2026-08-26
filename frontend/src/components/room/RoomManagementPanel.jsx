import { useState, useEffect } from 'react';
import roomService from '../../services/roomService';
import api from '../../services/api';
import toast from 'react-hot-toast';
import { FontAwesomeIcon } from '@fortawesome/react-fontawesome';
import {
  faBuilding, faLayerGroup, faDoorOpen,
  faCog, faPlus, faMinus, faEdit, faCheck, faTimes, faSearch,
  faUserGraduate, faUserPlus, faTrash, faTag, faMagic,
} from '@fortawesome/free-solid-svg-icons';

// Shared by the Warden "Students & Rooms" page and the Admin "Room Management" page —
// both roles can call the same /warden/rooms/** endpoints (see SecurityConfig), so this
// component is entirely self-contained and role-agnostic.
const RoomManagementPanel = () => {
  const [buildings, setBuildings] = useState([]);
  const [allocations, setAllocations] = useState([]);
  const [config, setConfig] = useState({ maxRoomsPerFloor: 10, maxMembersPerRoom: 6 });
  const [activeBuilding, setActiveBuilding] = useState(null);
  const [showSettings, setShowSettings] = useState(false);
  const [editingRoom, setEditingRoom] = useState(null);
  const [editMaxMembers, setEditMaxMembers] = useState(6);
  const [searchTerm, setSearchTerm] = useState('');
  const [loading, setLoading] = useState(true);

  // Room number editing
  const [editingRoomNumber, setEditingRoomNumber] = useState(null);
  const [editRoomNumberValue, setEditRoomNumberValue] = useState('');

  // Floor department editing
  const [editingFloorDept, setEditingFloorDept] = useState(null);
  const [editFloorDeptValue, setEditFloorDeptValue] = useState('');

  // Room department override editing
  const [editingRoomDept, setEditingRoomDept] = useState(null);
  const [editRoomDeptValue, setEditRoomDeptValue] = useState('');

  // Auto allocate
  const [autoAllocateFloor, setAutoAllocateFloor] = useState('');
  const [autoAllocating, setAutoAllocating] = useState(false);
  const [autoAllocateResult, setAutoAllocateResult] = useState(null);

  // Add student modal state
  const [showAddModal, setShowAddModal] = useState(false);
  const [addToRoom, setAddToRoom] = useState(null);
  const [registeredStudents, setRegisteredStudents] = useState([]);
  const [studentSearch, setStudentSearch] = useState('');
  const [manualEntry, setManualEntry] = useState(false);
  const [manualForm, setManualForm] = useState({ name: '', rollNo: '', department: '', email: '' });

  // Building rename state
  const [editingBuildingId, setEditingBuildingId] = useState(null);
  const [editBuildingName, setEditBuildingName] = useState('');

  // Add building state
  const [showAddBuilding, setShowAddBuilding] = useState(false);
  const [newBuildingName, setNewBuildingName] = useState('');
  const [newBuildingType, setNewBuildingType] = useState('NORMAL');
  const [newBuildingGender, setNewBuildingGender] = useState('BOY');

  useEffect(() => {
    fetchData();
  }, []);

  const fetchData = async () => {
    try {
      const [buildingsRes, allocationsRes, configRes] = await Promise.all([
        roomService.getBuildings(),
        roomService.getAllocations(),
        roomService.getConfig(),
      ]);
      setBuildings(buildingsRes.data);
      setAllocations(allocationsRes.data);
      setConfig(configRes.data);
      if (!activeBuilding && buildingsRes.data.length > 0) {
        setActiveBuilding(buildingsRes.data[0].id);
      }

      // Fetch registered students from API
      try {
        const studentsRes = await api.get('/warden/students');
        setRegisteredStudents(studentsRes.data.data || studentsRes.data || []);
      } catch {
        setRegisteredStudents([]);
      }
    } catch {
      // silent — individual operations below surface their own errors via toast
    } finally {
      setLoading(false);
    }
  };

  const getRoomOccupants = (roomId) => {
    return allocations.filter(a => a.roomId === roomId);
  };

  const handleUpdateConfig = async () => {
    try {
      await roomService.updateConfig(config);
      toast.success('Settings saved');
      setShowSettings(false);
    } catch {
      toast.error('Failed to save settings');
    }
  };

  const handleAddFloor = async (buildingId) => {
    try {
      await roomService.addFloor(buildingId);
      toast.success('Floor added');
      fetchData();
    } catch {
      toast.error('Failed to add floor');
    }
  };

  const handleRemoveFloor = async (buildingId, floorNumber) => {
    const occupants = allocations.filter(a => a.buildingId === buildingId && a.floor === floorNumber);
    if (occupants.length > 0) {
      toast.error('Cannot remove floor with allocated students');
      return;
    }
    try {
      await roomService.removeFloor(buildingId, floorNumber);
      toast.success('Floor removed');
      fetchData();
    } catch {
      toast.error('Failed to remove floor');
    }
  };

  const handleAddRoom = async (buildingId, floorNumber) => {
    try {
      await roomService.addRoomToFloor(buildingId, floorNumber);
      fetchData();
    } catch {
      toast.error('Failed to add room');
    }
  };

  const handleRemoveRoom = async (buildingId, floorNumber) => {
    try {
      await roomService.removeLastRoomFromFloor(buildingId, floorNumber);
      fetchData();
    } catch (error) {
      toast.error(error.response?.data?.message || 'Failed to remove room');
    }
  };

  const handleEditRoomCapacity = (roomId, currentMax) => {
    setEditingRoom({ roomId });
    setEditMaxMembers(currentMax);
  };

  const handleSaveRoomCapacity = async () => {
    if (!editingRoom) return;
    try {
      await roomService.updateRoomMaxMembers(editingRoom.roomId, editMaxMembers);
      setEditingRoom(null);
      fetchData();
      toast.success('Room capacity updated');
    } catch {
      toast.error('Failed to update');
    }
  };

  const handleEditRoomNumber = (roomId, currentNumber) => {
    setEditingRoomNumber(roomId);
    setEditRoomNumberValue(currentNumber);
  };

  const handleSaveRoomNumber = async (roomId) => {
    if (!editRoomNumberValue.trim()) {
      toast.error('Room number is required');
      return;
    }
    try {
      await roomService.updateRoomNumber(roomId, editRoomNumberValue.trim());
      setEditingRoomNumber(null);
      fetchData();
      toast.success('Room number updated');
    } catch (error) {
      toast.error(error.response?.data?.message || 'Failed to update room number');
    }
  };

  const handleEditFloorDept = (buildingId, floorNumber, currentDept) => {
    setEditingFloorDept({ buildingId, floorNumber });
    setEditFloorDeptValue(currentDept || '');
  };

  const handleSaveFloorDept = async () => {
    if (!editingFloorDept) return;
    if (!editFloorDeptValue.trim()) {
      toast.error('Department is required');
      return;
    }
    try {
      await roomService.setFloorDepartment(
        editingFloorDept.buildingId, editingFloorDept.floorNumber, editFloorDeptValue.trim());
      setEditingFloorDept(null);
      fetchData();
      toast.success('Floor default department set');
    } catch (error) {
      toast.error(error.response?.data?.message || 'Failed to set floor department');
    }
  };

  const handleEditRoomDept = (roomId, currentOverride) => {
    setEditingRoomDept(roomId);
    setEditRoomDeptValue(currentOverride || '');
  };

  const handleSaveRoomDept = async (roomId) => {
    if (!editRoomDeptValue.trim()) {
      toast.error('Department is required');
      return;
    }
    try {
      await roomService.setRoomDepartmentOverride(roomId, editRoomDeptValue.trim());
      setEditingRoomDept(null);
      fetchData();
      toast.success('Room department override set');
    } catch (error) {
      toast.error(error.response?.data?.message || 'Failed to set room department');
    }
  };

  const handleRemoveRoomDeptOverride = async (roomId) => {
    try {
      await roomService.removeRoomDepartmentOverride(roomId);
      fetchData();
      toast.success('Override removed — room now inherits the floor default');
    } catch {
      toast.error('Failed to remove override');
    }
  };

  const handleAutoAllocate = async (buildingId) => {
    setAutoAllocating(true);
    setAutoAllocateResult(null);
    try {
      const floorNumber = autoAllocateFloor === '' ? null : parseInt(autoAllocateFloor);
      const { data } = await roomService.bulkAllocate(buildingId, floorNumber);
      setAutoAllocateResult(data);
      fetchData();
      toast.success(`Bulk allocation completed — ${data.assigned} student(s) assigned`);
    } catch (error) {
      toast.error(error.response?.data?.message || 'Bulk allocation failed');
    } finally {
      setAutoAllocating(false);
    }
  };

  const handleRenameBuilding = async () => {
    if (!editingBuildingId || !editBuildingName.trim()) return;
    try {
      await roomService.renameBuilding(editingBuildingId, editBuildingName.trim());
      setEditingBuildingId(null);
      fetchData();
      toast.success('Building renamed');
    } catch {
      toast.error('Failed to rename building');
    }
  };

  const handleAddBuilding = async () => {
    if (!newBuildingName.trim()) {
      toast.error('Building name is required');
      return;
    }
    try {
      await roomService.addBuilding(newBuildingName.trim(), newBuildingType, newBuildingGender);
      toast.success('Building added');
      setShowAddBuilding(false);
      setNewBuildingName('');
      setNewBuildingType('NORMAL');
      setNewBuildingGender('BOY');
      fetchData();
    } catch (error) {
      toast.error(error.response?.data?.message || 'Failed to add building');
    }
  };

  const handleRemoveBuilding = async (buildingId, buildingName) => {
    if (!window.confirm(`Remove "${buildingName}" and all its rooms? This cannot be undone.`)) return;
    try {
      await roomService.removeBuilding(buildingId);
      toast.success(`${buildingName} removed`);
      if (activeBuilding === buildingId) setActiveBuilding(null);
      fetchData();
    } catch (error) {
      toast.error(error.response?.data?.message || 'Failed to remove building');
    }
  };

  const handleToggleBuildingGender = async (buildingId, currentGender) => {
    const newGender = currentGender === 'BOY' ? 'GIRL' : 'BOY';
    try {
      await roomService.updateBuildingGender(buildingId, newGender);
      fetchData();
      toast.success(`Building set to ${newGender === 'BOY' ? 'Boys' : 'Girls'}`);
    } catch {
      toast.error('Failed to update building gender');
    }
  };

  const handleToggleBuildingType = async (buildingId, currentType) => {
    const newType = currentType === 'NRI' ? 'NORMAL' : 'NRI';
    try {
      await roomService.updateBuildingType(buildingId, newType);
      fetchData();
      toast.success(`Building set to ${newType === 'NRI' ? 'NRI' : 'Regular'}`);
    } catch {
      toast.error('Failed to update building type');
    }
  };

  const handleOpenAddModal = (roomId, roomNumber) => {
    setAddToRoom({ roomId, roomNumber });
    setStudentSearch('');
    setManualEntry(false);
    setManualForm({ name: '', rollNo: '', department: '', email: '' });
    setShowAddModal(true);
  };

  const handleAddStudentFromList = async (student) => {
    if (!addToRoom) return;
    try {
      await roomService.allocateStudent(addToRoom.roomId, {
        name: student.name,
        rollNo: student.rollNo,
        department: student.department,
        email: student.email,
      });
      toast.success(`${student.name} added to Room ${addToRoom.roomNumber}`);
      setShowAddModal(false);
      fetchData();
    } catch (error) {
      toast.error(error.response?.data?.message || 'Failed to add student');
    }
  };

  const handleAddManualStudent = async () => {
    if (!addToRoom) return;
    if (!manualForm.name || !manualForm.rollNo || !manualForm.department) {
      toast.error('Please fill in name, roll no, and department');
      return;
    }
    try {
      await roomService.allocateStudent(addToRoom.roomId, {
        name: manualForm.name,
        rollNo: manualForm.rollNo,
        department: manualForm.department,
        email: manualForm.email || `${manualForm.rollNo}@hostel.local`,
      });
      toast.success(`${manualForm.name} added to Room ${addToRoom.roomNumber}`);
      setShowAddModal(false);
      fetchData();
    } catch (error) {
      toast.error(error.response?.data?.message || 'Failed to add student');
    }
  };

  const handleRemoveStudent = async (studentEmail, studentName) => {
    if (!window.confirm(`Remove ${studentName} from their room?`)) return;
    try {
      await roomService.removeAllocation(studentEmail);
      toast.success(`${studentName} removed`);
      fetchData();
    } catch {
      toast.error('Failed to remove student');
    }
  };

  const getUnallocatedStudents = () => {
    const allocatedEmails = allocations.map(a => a.studentEmail);
    return registeredStudents.filter(s => !allocatedEmails.includes(s.email));
  };

  const filteredModalStudents = () => {
    const unallocated = getUnallocatedStudents();
    if (!studentSearch) return unallocated;
    return unallocated.filter(s =>
      s.name?.toLowerCase().includes(studentSearch.toLowerCase()) ||
      s.rollNo?.toLowerCase().includes(studentSearch.toLowerCase()) ||
      s.department?.toLowerCase().includes(studentSearch.toLowerCase())
    );
  };

  const filteredAllocations = searchTerm
    ? allocations.filter(a =>
        a.name?.toLowerCase().includes(searchTerm.toLowerCase()) ||
        a.rollNo?.toLowerCase().includes(searchTerm.toLowerCase()) ||
        a.department?.toLowerCase().includes(searchTerm.toLowerCase()) ||
        a.roomNumber?.includes(searchTerm)
      )
    : null;

  const currentBuilding = buildings.find(b => b.id === activeBuilding);

  const totalStudents = allocations.length;
  const totalRooms = buildings.reduce((sum, b) => sum + b.floors.reduce((s, f) => s + f.rooms.length, 0), 0);

  if (loading) {
    return (
      <div className="d-flex justify-content-center align-items-center" style={{ minHeight: '60vh' }}>
        <div className="spinner-border text-primary" role="status">
          <span className="visually-hidden">Loading...</span>
        </div>
      </div>
    );
  }

  return (
    <>
      <div className="d-flex align-items-center justify-content-end mb-3">
        <button
          className={`btn ${showSettings ? 'btn-primary' : 'btn-outline-primary'} btn-sm`}
          onClick={() => setShowSettings(!showSettings)}
        >
          <FontAwesomeIcon icon={faCog} /> Settings
        </button>
      </div>

      {/* Settings Panel */}
      {showSettings && (
        <div className="row mb-4">
          <div className="col-12">
            <div className="card shadow-sm border-primary">
              <div className="card-header bg-primary text-white">
                <h5 className="mb-0"><FontAwesomeIcon icon={faCog} /> Room Settings</h5>
              </div>
              <div className="card-body">
                <div className="row g-3">
                  <div className="col-md-4">
                    <label className="form-label fw-semibold">Default Max Rooms Per Floor</label>
                    <input
                      type="number"
                      className="form-control"
                      value={config.maxRoomsPerFloor}
                      onChange={(e) => setConfig({ ...config, maxRoomsPerFloor: parseInt(e.target.value) || 1 })}
                      min="1"
                      max="20"
                    />
                    <small className="text-muted">Applies to new floors</small>
                  </div>
                  <div className="col-md-4">
                    <label className="form-label fw-semibold">Default Max Members Per Room</label>
                    <input
                      type="number"
                      className="form-control"
                      value={config.maxMembersPerRoom}
                      onChange={(e) => setConfig({ ...config, maxMembersPerRoom: parseInt(e.target.value) || 1 })}
                      min="1"
                      max="12"
                    />
                    <small className="text-muted">Applies to new rooms</small>
                  </div>
                </div>
                <div className="mt-3">
                  <button className="btn btn-primary" onClick={handleUpdateConfig}>
                    <FontAwesomeIcon icon={faCheck} /> Save Settings
                  </button>
                </div>
              </div>
            </div>
          </div>
        </div>
      )}

      {/* Stats */}
      <div className="row mb-4 g-4">
        <div className="col-6 col-md-3">
          <div className="card shadow-sm">
            <div className="card-body">
              <p className="text-muted mb-1"><FontAwesomeIcon icon={faBuilding} style={{ color: 'var(--color-info)' }} /> Buildings</p>
              <h3 className="mb-0 fw-bold" style={{ color: 'var(--color-info)' }}>{buildings.length}</h3>
            </div>
          </div>
        </div>
        <div className="col-6 col-md-3">
          <div className="card shadow-sm">
            <div className="card-body">
              <p className="text-muted mb-1"><FontAwesomeIcon icon={faDoorOpen} style={{ color: 'var(--color-success)' }} /> Total Rooms</p>
              <h3 className="mb-0 fw-bold" style={{ color: 'var(--color-success)' }}>{totalRooms}</h3>
            </div>
          </div>
        </div>
        <div className="col-6 col-md-3">
          <div className="card shadow-sm">
            <div className="card-body">
              <p className="text-muted mb-1"><FontAwesomeIcon icon={faUserGraduate} style={{ color: 'var(--accent-purple)' }} /> Total Students</p>
              <h3 className="mb-0 fw-bold" style={{ color: 'var(--accent-purple)' }}>{totalStudents}</h3>
            </div>
          </div>
        </div>
        <div className="col-6 col-md-3">
          <div className="card shadow-sm">
            <div className="card-body">
              <p className="text-muted mb-1"><FontAwesomeIcon icon={faLayerGroup} style={{ color: 'var(--color-warning)' }} /> Total Floors</p>
              <h3 className="mb-0 fw-bold" style={{ color: 'var(--color-warning)' }}>{buildings.reduce((s, b) => s + b.floors.length, 0)}</h3>
            </div>
          </div>
        </div>
      </div>

      {/* Search */}
      <div className="row mb-4">
        <div className="col-12">
          <div className="input-group">
            <span className="input-group-text"><FontAwesomeIcon icon={faSearch} /></span>
            <input
              type="text"
              className="form-control"
              placeholder="Search by name, roll no, department, or room number..."
              value={searchTerm}
              onChange={(e) => setSearchTerm(e.target.value)}
            />
            {searchTerm && (
              <button className="btn btn-outline-secondary" onClick={() => setSearchTerm('')}>
                <FontAwesomeIcon icon={faTimes} />
              </button>
            )}
          </div>
        </div>
      </div>

      {/* Search Results */}
      {filteredAllocations && (
        <div className="row mb-4">
          <div className="col-12">
            <div className="card shadow-sm">
              <div className="card-header">
                <h5 className="mb-0">Search Results ({filteredAllocations.length})</h5>
              </div>
              <div className="card-body">
                {filteredAllocations.length === 0 ? (
                  <p className="text-center text-muted mb-0">No students found</p>
                ) : (
                  <div className="table-responsive">
                    <table className="table table-hover mb-0">
                      <thead>
                        <tr>
                          <th>Roll No</th>
                          <th>Name</th>
                          <th>Department</th>
                          <th>Building</th>
                          <th>Floor</th>
                          <th>Room</th>
                        </tr>
                      </thead>
                      <tbody>
                        {filteredAllocations.map((a, i) => (
                          <tr key={i}>
                            <td>{a.rollNo}</td>
                            <td>{a.name}</td>
                            <td>{a.department}</td>
                            <td>{buildings.find(b => b.id === a.buildingId)?.name || '-'}</td>
                            <td>Floor {a.floor}</td>
                            <td>Room {a.roomNumber}</td>
                          </tr>
                        ))}
                      </tbody>
                    </table>
                  </div>
                )}
              </div>
            </div>
          </div>
        </div>
      )}

      {/* Building Tabs */}
      {!searchTerm && (
        <>
          <div className="row mb-3">
            <div className="col-12">
              <div className="d-flex align-items-center justify-content-end mb-3">
                <button
                  className="btn btn-outline-success btn-sm"
                  onClick={() => setShowAddBuilding(!showAddBuilding)}
                >
                  <FontAwesomeIcon icon={faPlus} /> Add Building
                </button>
              </div>

              {/* Buildings Table */}
              <div className="card shadow-sm mb-3">
                <div className="table-responsive">
                  <table className="table table-hover mb-0" style={{ fontSize: '0.85rem' }}>
                    <thead style={{ backgroundColor: 'var(--color-bg-tertiary)' }}>
                      <tr>
                        <th style={{ paddingLeft: '1rem' }}>Building</th>
                        <th style={{ textAlign: 'center' }}>Gender</th>
                        <th style={{ textAlign: 'center' }}>Type</th>
                        <th style={{ textAlign: 'right', paddingRight: '1rem' }}>Actions</th>
                      </tr>
                    </thead>
                    <tbody>
                      {[...buildings].sort((a, b) => {
                        if (a.gender !== b.gender) return a.gender === 'GIRL' ? 1 : -1;
                        return a.name.localeCompare(b.name);
                      }).map(b => (
                        <tr key={b.id} style={{ backgroundColor: activeBuilding === b.id ? 'var(--row-highlight)' : 'transparent' }}>
                          <td style={{ paddingLeft: '1rem' }}>
                            {editingBuildingId === b.id ? (
                              <div className="d-flex align-items-center">
                                <input type="text" className="form-control form-control-sm" style={{ width: '140px' }}
                                  value={editBuildingName} onChange={(e) => setEditBuildingName(e.target.value)}
                                  onKeyDown={(e) => e.key === 'Enter' && handleRenameBuilding()} autoFocus />
                                <button className="btn btn-success btn-sm ms-1" onClick={handleRenameBuilding}><FontAwesomeIcon icon={faCheck} /></button>
                                <button className="btn btn-secondary btn-sm ms-1" onClick={() => setEditingBuildingId(null)}><FontAwesomeIcon icon={faTimes} /></button>
                              </div>
                            ) : (
                              <button
                                className="btn btn-link text-decoration-none p-0 fw-semibold"
                                style={{ color: activeBuilding === b.id ? 'var(--accent-blue-vivid)' : 'var(--color-text-primary)' }}
                                onClick={() => setActiveBuilding(b.id)}
                              >
                                <FontAwesomeIcon icon={faBuilding} className="me-2" style={{ color: b.gender === 'GIRL' ? 'var(--accent-pink)' : 'var(--accent-blue-mid)' }} />
                                {b.name}
                              </button>
                            )}
                          </td>
                          <td style={{ textAlign: 'center' }}>
                            <div className="btn-group btn-group-sm" role="group" style={{ borderRadius: '12px', overflow: 'hidden' }}>
                              <button
                                className={`btn btn-sm ${b.gender !== 'GIRL' ? '' : 'btn-outline-secondary'}`}
                                style={b.gender !== 'GIRL'
                                  ? { backgroundColor: 'var(--badge-boys-bg)', color: 'var(--badge-boys-text)', border: '1px solid var(--badge-boys-border)', fontSize: '0.7rem', fontWeight: 600 }
                                  : { fontSize: '0.7rem', color: 'var(--color-text-muted)' }}
                                onClick={() => b.gender === 'GIRL' && handleToggleBuildingGender(b.id, b.gender)}
                              >
                                Boys
                              </button>
                              <button
                                className={`btn btn-sm ${b.gender === 'GIRL' ? '' : 'btn-outline-secondary'}`}
                                style={b.gender === 'GIRL'
                                  ? { backgroundColor: 'var(--badge-girls-bg)', color: 'var(--badge-girls-text)', border: '1px solid var(--badge-girls-border)', fontSize: '0.7rem', fontWeight: 600 }
                                  : { fontSize: '0.7rem', color: 'var(--color-text-muted)' }}
                                onClick={() => b.gender !== 'GIRL' && handleToggleBuildingGender(b.id, b.gender)}
                              >
                                Girls
                              </button>
                            </div>
                          </td>
                          <td style={{ textAlign: 'center' }}>
                            <div className="btn-group btn-group-sm" role="group" style={{ borderRadius: '12px', overflow: 'hidden' }}>
                              <button
                                className={`btn btn-sm ${b.type !== 'NRI' ? '' : 'btn-outline-secondary'}`}
                                style={b.type !== 'NRI'
                                  ? { backgroundColor: 'var(--badge-regular-bg)', color: 'var(--badge-regular-text)', border: '1px solid var(--badge-regular-border)', fontSize: '0.7rem', fontWeight: 600 }
                                  : { fontSize: '0.7rem', color: 'var(--color-text-muted)' }}
                                onClick={() => b.type === 'NRI' && handleToggleBuildingType(b.id, b.type)}
                              >
                                Regular
                              </button>
                              <button
                                className={`btn btn-sm ${b.type === 'NRI' ? '' : 'btn-outline-secondary'}`}
                                style={b.type === 'NRI'
                                  ? { backgroundColor: 'var(--badge-nri-bg)', color: 'var(--badge-nri-text)', border: '1px solid var(--badge-nri-border)', fontSize: '0.7rem', fontWeight: 600 }
                                  : { fontSize: '0.7rem', color: 'var(--color-text-muted)' }}
                                onClick={() => b.type !== 'NRI' && handleToggleBuildingType(b.id, b.type)}
                              >
                                NRI
                              </button>
                            </div>
                          </td>
                          <td style={{ textAlign: 'right', paddingRight: '1rem' }}>
                            <button
                              className="btn btn-outline-secondary btn-sm me-1"
                              style={{ fontSize: '0.75rem', padding: '2px 8px' }}
                              onClick={() => { setEditingBuildingId(b.id); setEditBuildingName(b.name); }}
                              title="Rename"
                            >
                              <FontAwesomeIcon icon={faEdit} />
                            </button>
                            <button
                              className="btn btn-outline-danger btn-sm"
                              style={{ fontSize: '0.75rem', padding: '2px 8px' }}
                              onClick={() => handleRemoveBuilding(b.id, b.name)}
                              title="Remove"
                            >
                              <FontAwesomeIcon icon={faTrash} />
                            </button>
                          </td>
                        </tr>
                      ))}
                    </tbody>
                  </table>
                </div>
              </div>

              {/* Add Building Form */}
              {showAddBuilding && (
                <div className="card shadow-sm mb-3 border-success">
                  <div className="card-body">
                    <h6 className="fw-bold mb-3">Add New Building</h6>
                    <div className="row g-2 align-items-end">
                      <div className="col-md-3">
                        <label className="form-label">Building Name *</label>
                        <input
                          type="text"
                          className="form-control form-control-sm"
                          value={newBuildingName}
                          onChange={(e) => setNewBuildingName(e.target.value)}
                          placeholder="e.g. Hostel C"
                        />
                      </div>
                      <div className="col-md-3">
                        <label className="form-label">Type</label>
                        <select className="form-select form-select-sm" value={newBuildingType} onChange={(e) => setNewBuildingType(e.target.value)}>
                          <option value="NORMAL">Regular</option>
                          <option value="NRI">NRI</option>
                        </select>
                      </div>
                      <div className="col-md-3">
                        <label className="form-label">Gender</label>
                        <select className="form-select form-select-sm" value={newBuildingGender} onChange={(e) => setNewBuildingGender(e.target.value)}>
                          <option value="BOY">Boys</option>
                          <option value="GIRL">Girls</option>
                        </select>
                      </div>
                      <div className="col-md-3 d-flex gap-2">
                        <button className="btn btn-success btn-sm" onClick={handleAddBuilding}>
                          <FontAwesomeIcon icon={faCheck} /> Add
                        </button>
                        <button className="btn btn-secondary btn-sm" onClick={() => setShowAddBuilding(false)}>
                          Cancel
                        </button>
                      </div>
                    </div>
                  </div>
                </div>
              )}
            </div>
          </div>

          {/* Auto Allocate */}
          {currentBuilding && (
            <div className="row mb-4">
              <div className="col-12">
                <div className="card shadow-sm border-success">
                  <div className="card-header bg-success text-white d-flex justify-content-between align-items-center">
                    <h6 className="mb-0"><FontAwesomeIcon icon={faMagic} /> Auto Allocate Rooms — {currentBuilding.name}</h6>
                  </div>
                  <div className="card-body">
                    <p className="text-muted mb-3" style={{ fontSize: '0.85rem' }}>
                      Assigns only students who currently have no room, matching each student's department against
                      the room's effective department (room override, else floor default) and respecting capacity.
                      Students who already have a room are never moved.
                    </p>
                    <div className="row g-2 align-items-end">
                      <div className="col-md-4">
                        <label className="form-label fw-semibold">Floor (optional)</label>
                        <select className="form-select form-select-sm" value={autoAllocateFloor} onChange={(e) => setAutoAllocateFloor(e.target.value)}>
                          <option value="">All floors</option>
                          {currentBuilding.floors.map(f => (
                            <option key={f.floorNumber} value={f.floorNumber}>Floor {f.floorNumber}</option>
                          ))}
                        </select>
                      </div>
                      <div className="col-md-4">
                        <button
                          className="btn btn-success btn-sm"
                          disabled={autoAllocating}
                          onClick={() => handleAutoAllocate(currentBuilding.id)}
                        >
                          {autoAllocating ? (
                            <><span className="spinner-border spinner-border-sm me-1" role="status"></span> Running...</>
                          ) : (
                            <><FontAwesomeIcon icon={faMagic} /> Auto Allocate Rooms</>
                          )}
                        </button>
                      </div>
                    </div>

                    {autoAllocateResult && (
                      <div className="mt-3 p-3" style={{ backgroundColor: 'var(--color-bg-tertiary)', borderRadius: '0.5rem' }}>
                        <h6 className="fw-bold mb-2">Bulk allocation completed</h6>
                        <div className="row g-2 mb-2">
                          <div className="col-6 col-md-3">
                            <div className="text-muted" style={{ fontSize: '0.75rem' }}>Students processed</div>
                            <div className="fw-bold">{autoAllocateResult.studentsProcessed}</div>
                          </div>
                          <div className="col-6 col-md-3">
                            <div className="text-muted" style={{ fontSize: '0.75rem' }}>Assigned</div>
                            <div className="fw-bold text-success">{autoAllocateResult.assigned}</div>
                          </div>
                          <div className="col-6 col-md-3">
                            <div className="text-muted" style={{ fontSize: '0.75rem' }}>Remaining</div>
                            <div className="fw-bold text-danger">{autoAllocateResult.remaining}</div>
                          </div>
                          <div className="col-6 col-md-3">
                            <div className="text-muted" style={{ fontSize: '0.75rem' }}>Rooms used</div>
                            <div className="fw-bold">{autoAllocateResult.roomsUsed}</div>
                          </div>
                        </div>
                        {autoAllocateResult.byDepartment?.length > 0 && (
                          <div className="mb-2" style={{ fontSize: '0.8rem' }}>
                            {autoAllocateResult.byDepartment.map((d, i) => (
                              <span key={i} className="badge bg-secondary me-2 mb-1">
                                {d.department || 'No department'}: {d.assigned}/{d.studentsNeedingRooms} assigned
                              </span>
                            ))}
                          </div>
                        )}
                        {autoAllocateResult.unassigned?.length > 0 && (
                          <div className="table-responsive mt-2" style={{ maxHeight: '220px', overflowY: 'auto' }}>
                            <table className="table table-sm table-hover mb-0">
                              <thead>
                                <tr>
                                  <th>Roll No</th>
                                  <th>Name</th>
                                  <th>Department</th>
                                  <th>Reason</th>
                                </tr>
                              </thead>
                              <tbody>
                                {autoAllocateResult.unassigned.map((u, i) => (
                                  <tr key={i}>
                                    <td>{u.rollNo}</td>
                                    <td>{u.name}</td>
                                    <td>{u.department}</td>
                                    <td className="text-muted">{u.reason}</td>
                                  </tr>
                                ))}
                              </tbody>
                            </table>
                          </div>
                        )}
                      </div>
                    )}
                  </div>
                </div>
              </div>
            </div>
          )}

          {/* Floors & Rooms */}
          {currentBuilding && currentBuilding.floors.map(floor => {
            const floorOccupants = allocations.filter(
              a => a.buildingId === currentBuilding.id && a.floor === floor.floorNumber
            );
            const isEditingFloorDept = editingFloorDept
              && editingFloorDept.buildingId === currentBuilding.id
              && editingFloorDept.floorNumber === floor.floorNumber;

            return (
              <div className="row mb-4" key={floor.floorNumber}>
                <div className="col-12">
                  <div className="card shadow-sm">
                    <div className="card-header d-flex flex-wrap justify-content-between align-items-center gap-2">
                      <h5 className="mb-0">
                        <FontAwesomeIcon icon={faLayerGroup} /> Floor {floor.floorNumber}
                        <span className="badge bg-primary ms-2">{floorOccupants.length} students</span>
                        <span className="badge bg-secondary ms-1">{floor.rooms.length} rooms</span>
                      </h5>
                      <div className="d-flex align-items-center gap-2 flex-wrap">
                        {isEditingFloorDept ? (
                          <div className="d-flex align-items-center gap-1">
                            <input
                              type="text"
                              className="form-control form-control-sm"
                              style={{ width: '120px' }}
                              value={editFloorDeptValue}
                              onChange={(e) => setEditFloorDeptValue(e.target.value)}
                              placeholder="e.g. CT"
                              onKeyDown={(e) => e.key === 'Enter' && handleSaveFloorDept()}
                              autoFocus
                            />
                            <button className="btn btn-success btn-sm" onClick={handleSaveFloorDept}><FontAwesomeIcon icon={faCheck} /></button>
                            <button className="btn btn-secondary btn-sm" onClick={() => setEditingFloorDept(null)}><FontAwesomeIcon icon={faTimes} /></button>
                          </div>
                        ) : (
                          <button
                            className="btn btn-outline-info btn-sm"
                            style={{ fontSize: '0.75rem' }}
                            onClick={() => handleEditFloorDept(currentBuilding.id, floor.floorNumber, floor.department)}
                            title="Set default department for this floor"
                          >
                            <FontAwesomeIcon icon={faTag} /> {floor.department ? `Default: ${floor.department}` : 'Set default department'}
                          </button>
                        )}
                        <button
                          className="btn btn-outline-secondary btn-sm"
                          onClick={() => handleRemoveRoom(currentBuilding.id, floor.floorNumber)}
                          disabled={floor.rooms.length <= 1}
                          title="Remove last room"
                        >
                          <FontAwesomeIcon icon={faMinus} />
                        </button>
                        <button
                          className="btn btn-outline-secondary btn-sm"
                          onClick={() => handleAddRoom(currentBuilding.id, floor.floorNumber)}
                          disabled={floor.rooms.length >= 20}
                          title="Add room"
                        >
                          <FontAwesomeIcon icon={faPlus} />
                        </button>
                        <button
                          className="btn btn-outline-danger btn-sm"
                          onClick={() => handleRemoveFloor(currentBuilding.id, floor.floorNumber)}
                          title="Remove floor"
                        >
                          <FontAwesomeIcon icon={faTimes} />
                        </button>
                      </div>
                    </div>
                    <div className="card-body">
                      <div className="row g-3">
                        {floor.rooms.map(room => {
                          const occupants = getRoomOccupants(room.id);
                          const isFull = occupants.length >= room.maxMembers;
                          const isEditingCapacity = editingRoom && editingRoom.roomId === room.id;
                          const isEditingNumber = editingRoomNumber === room.id;
                          const isEditingDept = editingRoomDept === room.id;

                          return (
                            <div className="col-md-6 col-lg-4" key={room.id}>
                              <div className={`card h-100 ${isFull ? 'border-danger' : occupants.length > 0 ? 'border-success' : ''}`}>
                                <div className="card-header d-flex justify-content-between align-items-center py-2" style={{ backgroundColor: isFull ? 'var(--room-full-bg)' : occupants.length > 0 ? 'var(--room-occupied-bg)' : 'var(--color-bg-tertiary)' }}>
                                  <span className="fw-semibold d-flex align-items-center gap-1">
                                    <FontAwesomeIcon icon={faDoorOpen} />
                                    {isEditingNumber ? (
                                      <>
                                        <input
                                          type="text"
                                          className="form-control form-control-sm"
                                          style={{ width: '70px' }}
                                          value={editRoomNumberValue}
                                          onChange={(e) => setEditRoomNumberValue(e.target.value)}
                                          onKeyDown={(e) => e.key === 'Enter' && handleSaveRoomNumber(room.id)}
                                          autoFocus
                                        />
                                        <button className="btn btn-success btn-sm" style={{ padding: '0.1rem 0.35rem' }} onClick={() => handleSaveRoomNumber(room.id)}><FontAwesomeIcon icon={faCheck} /></button>
                                        <button className="btn btn-secondary btn-sm" style={{ padding: '0.1rem 0.35rem' }} onClick={() => setEditingRoomNumber(null)}><FontAwesomeIcon icon={faTimes} /></button>
                                      </>
                                    ) : (
                                      <>
                                        Room {room.roomNumber}
                                        <button
                                          className="btn btn-link p-0 ms-1"
                                          style={{ fontSize: '0.7rem' }}
                                          onClick={() => handleEditRoomNumber(room.id, room.roomNumber)}
                                          title="Edit room number"
                                        >
                                          <FontAwesomeIcon icon={faEdit} />
                                        </button>
                                      </>
                                    )}
                                  </span>
                                  <div className="d-flex align-items-center gap-1">
                                    {isEditingCapacity ? (
                                      <>
                                        <input
                                          type="number"
                                          className="form-control form-control-sm"
                                          style={{ width: '60px' }}
                                          value={editMaxMembers}
                                          onChange={(e) => setEditMaxMembers(parseInt(e.target.value) || 1)}
                                          min="1"
                                          max="12"
                                        />
                                        <button className="btn btn-success btn-sm" onClick={handleSaveRoomCapacity}>
                                          <FontAwesomeIcon icon={faCheck} />
                                        </button>
                                        <button className="btn btn-secondary btn-sm" onClick={() => setEditingRoom(null)}>
                                          <FontAwesomeIcon icon={faTimes} />
                                        </button>
                                      </>
                                    ) : (
                                      <>
                                        <span className={`badge ${isFull ? 'bg-danger' : 'bg-secondary'}`}>
                                          {occupants.length}/{room.maxMembers}
                                        </span>
                                        <button
                                          className="btn btn-outline-secondary btn-sm"
                                          style={{ padding: '0.1rem 0.35rem', fontSize: '0.7rem' }}
                                          onClick={() => handleEditRoomCapacity(room.id, room.maxMembers)}
                                          title="Edit max capacity"
                                        >
                                          <FontAwesomeIcon icon={faEdit} />
                                        </button>
                                      </>
                                    )}
                                  </div>
                                </div>

                                {/* Department: room override vs inherited floor default */}
                                <div className="px-2 pt-2 d-flex align-items-center gap-1 flex-wrap">
                                  {isEditingDept ? (
                                    <>
                                      <input
                                        type="text"
                                        className="form-control form-control-sm"
                                        style={{ width: '90px' }}
                                        value={editRoomDeptValue}
                                        onChange={(e) => setEditRoomDeptValue(e.target.value)}
                                        placeholder="e.g. ECE"
                                        onKeyDown={(e) => e.key === 'Enter' && handleSaveRoomDept(room.id)}
                                        autoFocus
                                      />
                                      <button className="btn btn-success btn-sm" style={{ padding: '0.1rem 0.35rem' }} onClick={() => handleSaveRoomDept(room.id)}><FontAwesomeIcon icon={faCheck} /></button>
                                      <button className="btn btn-secondary btn-sm" style={{ padding: '0.1rem 0.35rem' }} onClick={() => setEditingRoomDept(null)}><FontAwesomeIcon icon={faTimes} /></button>
                                    </>
                                  ) : (
                                    <>
                                      {room.effectiveDepartment ? (
                                        <span
                                          className={`badge ${room.departmentOverride ? 'bg-warning text-dark' : 'bg-info text-dark'}`}
                                          style={{ fontSize: '0.7rem' }}
                                          title={room.departmentOverride ? 'Room override' : 'Inherited from floor default'}
                                        >
                                          <FontAwesomeIcon icon={faTag} /> {room.effectiveDepartment}
                                          {' '}({room.departmentOverride ? 'Room Override' : 'Floor Default'})
                                        </span>
                                      ) : (
                                        <span className="text-muted" style={{ fontSize: '0.7rem' }}>No department set</span>
                                      )}
                                      <button
                                        className="btn btn-link p-0"
                                        style={{ fontSize: '0.7rem' }}
                                        onClick={() => handleEditRoomDept(room.id, room.departmentOverride)}
                                        title="Set room department override"
                                      >
                                        <FontAwesomeIcon icon={faEdit} />
                                      </button>
                                      {room.departmentOverride && (
                                        <button
                                          className="btn btn-link p-0 text-danger"
                                          style={{ fontSize: '0.7rem' }}
                                          onClick={() => handleRemoveRoomDeptOverride(room.id)}
                                          title="Remove override (inherit floor default)"
                                        >
                                          <FontAwesomeIcon icon={faTimes} /> Remove override
                                        </button>
                                      )}
                                    </>
                                  )}
                                </div>

                                <div className="card-body p-2">
                                  {occupants.length === 0 ? (
                                    <p className="text-muted text-center mb-0" style={{ fontSize: '0.85rem' }}>Empty</p>
                                  ) : (
                                    <div className="list-group list-group-flush">
                                      {occupants.map((student, idx) => (
                                        <div key={idx} className="list-group-item px-2 py-1 d-flex justify-content-between align-items-center" style={{ fontSize: '0.85rem' }}>
                                          <div>
                                            <div className="fw-semibold">{student.name}</div>
                                            <div className="text-muted">
                                              {student.rollNo} &middot; {student.department}
                                            </div>
                                          </div>
                                          <button
                                            className="btn btn-outline-danger btn-sm"
                                            style={{ padding: '0.1rem 0.3rem', fontSize: '0.7rem' }}
                                            onClick={() => handleRemoveStudent(student.studentEmail, student.name)}
                                            title="Remove student"
                                          >
                                            <FontAwesomeIcon icon={faTrash} />
                                          </button>
                                        </div>
                                      ))}
                                    </div>
                                  )}
                                  {!isFull && (
                                    <div className="text-center mt-2">
                                      <button
                                        className="btn btn-outline-primary btn-sm"
                                        style={{ fontSize: '0.8rem' }}
                                        onClick={() => handleOpenAddModal(room.id, room.roomNumber)}
                                      >
                                        <FontAwesomeIcon icon={faUserPlus} /> Add Student
                                      </button>
                                    </div>
                                  )}
                                </div>
                              </div>
                            </div>
                          );
                        })}
                      </div>
                    </div>
                  </div>
                </div>
              </div>
            );
          })}

          {/* Add Floor Button */}
          {currentBuilding && (
            <div className="row mb-4">
              <div className="col-12 text-center">
                <button
                  className="btn btn-outline-primary"
                  onClick={() => handleAddFloor(currentBuilding.id)}
                >
                  <FontAwesomeIcon icon={faPlus} /> Add Floor to {currentBuilding.name}
                </button>
              </div>
            </div>
          )}
        </>
      )}
      {/* Add Student Modal */}
      {showAddModal && (
        <div className="modal d-block" style={{ backgroundColor: 'rgba(0,0,0,0.5)' }}>
          <div className="modal-dialog modal-lg modal-dialog-centered modal-dialog-scrollable">
            <div className="modal-content">
              <div className="modal-header">
                <h5 className="modal-title">
                  <FontAwesomeIcon icon={faUserPlus} /> Add Student to Room {addToRoom?.roomNumber}
                </h5>
                <button type="button" className="btn-close" onClick={() => setShowAddModal(false)}></button>
              </div>
              <div className="modal-body">
                {/* Toggle: pick from list or manual */}
                <div className="btn-group w-100 mb-3" role="group">
                  <button
                    className={`btn ${!manualEntry ? 'btn-primary' : 'btn-outline-primary'}`}
                    onClick={() => setManualEntry(false)}
                  >
                    Select from Registered Students
                  </button>
                  <button
                    className={`btn ${manualEntry ? 'btn-primary' : 'btn-outline-primary'}`}
                    onClick={() => setManualEntry(true)}
                  >
                    Add Manually
                  </button>
                </div>

                {!manualEntry ? (
                  <>
                    <div className="input-group mb-3">
                      <span className="input-group-text"><FontAwesomeIcon icon={faSearch} /></span>
                      <input
                        type="text"
                        className="form-control"
                        placeholder="Search by name, roll no, or department..."
                        value={studentSearch}
                        onChange={(e) => setStudentSearch(e.target.value)}
                      />
                    </div>
                    {filteredModalStudents().length === 0 ? (
                      <p className="text-center text-muted">
                        {registeredStudents.length === 0
                          ? 'No registered students found. Use "Add Manually" to enter student details.'
                          : 'No unallocated students match your search.'}
                      </p>
                    ) : (
                      <div className="table-responsive" style={{ maxHeight: '300px', overflowY: 'auto' }}>
                        <table className="table table-hover mb-0">
                          <thead className="table-light sticky-top">
                            <tr>
                              <th>Roll No</th>
                              <th>Name</th>
                              <th>Department</th>
                              <th>Action</th>
                            </tr>
                          </thead>
                          <tbody>
                            {filteredModalStudents().map((student, i) => (
                              <tr key={i}>
                                <td>{student.rollNo}</td>
                                <td>{student.name}</td>
                                <td>{student.department}</td>
                                <td>
                                  <button
                                    className="btn btn-primary btn-sm"
                                    onClick={() => handleAddStudentFromList(student)}
                                  >
                                    <FontAwesomeIcon icon={faPlus} /> Add
                                  </button>
                                </td>
                              </tr>
                            ))}
                          </tbody>
                        </table>
                      </div>
                    )}
                  </>
                ) : (
                  <>
                    <div className="mb-3">
                      <label className="form-label fw-semibold">Name *</label>
                      <input
                        type="text"
                        className="form-control"
                        value={manualForm.name}
                        onChange={(e) => setManualForm({ ...manualForm, name: e.target.value })}
                        placeholder="Student name"
                      />
                    </div>
                    <div className="row mb-3">
                      <div className="col-md-6">
                        <label className="form-label fw-semibold">Roll No *</label>
                        <input
                          type="text"
                          className="form-control"
                          value={manualForm.rollNo}
                          onChange={(e) => setManualForm({ ...manualForm, rollNo: e.target.value })}
                          placeholder="e.g. 2024503001"
                        />
                      </div>
                      <div className="col-md-6">
                        <label className="form-label fw-semibold">Department *</label>
                        <input
                          type="text"
                          className="form-control"
                          value={manualForm.department}
                          onChange={(e) => setManualForm({ ...manualForm, department: e.target.value })}
                          placeholder="e.g. CSE"
                        />
                      </div>
                    </div>
                    <div className="mb-3">
                      <label className="form-label fw-semibold">Email (optional)</label>
                      <input
                        type="email"
                        className="form-control"
                        value={manualForm.email}
                        onChange={(e) => setManualForm({ ...manualForm, email: e.target.value })}
                        placeholder="student@college.edu"
                      />
                    </div>
                    <button className="btn btn-primary" onClick={handleAddManualStudent}>
                      <FontAwesomeIcon icon={faUserPlus} /> Add Student
                    </button>
                  </>
                )}
              </div>
            </div>
          </div>
        </div>
      )}
    </>
  );
};

export default RoomManagementPanel;
