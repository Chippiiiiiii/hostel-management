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
  const [activeFloor, setActiveFloor] = useState(null);
  const [showSettings, setShowSettings] = useState(false);
  const [showAutoAllocate, setShowAutoAllocate] = useState(false);
  const [editingRoom, setEditingRoom] = useState(null);
  const [editMaxMembers, setEditMaxMembers] = useState(6);
  const [buildingSearch, setBuildingSearch] = useState('');
  const [loading, setLoading] = useState(true);

  const [editingRoomNumber, setEditingRoomNumber] = useState(null);
  const [editRoomNumberValue, setEditRoomNumberValue] = useState('');

  const [editingFloorDept, setEditingFloorDept] = useState(null);
  const [editFloorDeptValue, setEditFloorDeptValue] = useState('');

  const [editingRoomDept, setEditingRoomDept] = useState(null);
  const [editRoomDeptValue, setEditRoomDeptValue] = useState('');

  const [autoAllocateFloor, setAutoAllocateFloor] = useState('');
  const [autoAllocating, setAutoAllocating] = useState(false);
  const [autoAllocateResult, setAutoAllocateResult] = useState(null);

  const [showAddModal, setShowAddModal] = useState(false);
  const [addToRoom, setAddToRoom] = useState(null);
  const [registeredStudents, setRegisteredStudents] = useState([]);
  const [studentSearch, setStudentSearch] = useState('');
  const [manualEntry, setManualEntry] = useState(false);
  const [manualForm, setManualForm] = useState({ name: '', rollNo: '', department: '', email: '' });

  const [editingBuildingId, setEditingBuildingId] = useState(null);
  const [editBuildingName, setEditBuildingName] = useState('');

  const [showAddBuilding, setShowAddBuilding] = useState(false);
  const [newBuildingName, setNewBuildingName] = useState('');
  const [newBuildingType, setNewBuildingType] = useState('NORMAL');
  const [newBuildingGender, setNewBuildingGender] = useState('BOY');

  useEffect(() => { fetchData(); }, []);

  useEffect(() => {
    const bldg = buildings.find(b => b.id === activeBuilding);
    if (bldg && bldg.floors.length > 0) {
      setActiveFloor(f => bldg.floors.find(fl => fl.floorNumber === f) ? f : bldg.floors[0].floorNumber);
    } else {
      setActiveFloor(null);
    }
  }, [activeBuilding, buildings]);

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
      try {
        const studentsRes = await api.get('/warden/students');
        setRegisteredStudents(studentsRes.data.data || studentsRes.data || []);
      } catch {
        setRegisteredStudents([]);
      }
    } catch {
      toast.error('Failed to load room data');
    } finally {
      setLoading(false);
    }
  };

  const getRoomOccupants = (roomId) => allocations.filter(a => a.roomId === roomId);

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
    if (occupants.length > 0) { toast.error('Cannot remove floor with allocated students'); return; }
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

  const handleEditRoomCapacity = (roomId, currentMax) => { setEditingRoom({ roomId }); setEditMaxMembers(currentMax); };

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

  const handleEditRoomNumber = (roomId, currentNumber) => { setEditingRoomNumber(roomId); setEditRoomNumberValue(currentNumber); };

  const handleSaveRoomNumber = async (roomId) => {
    if (!editRoomNumberValue.trim()) { toast.error('Room number is required'); return; }
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
    if (!editFloorDeptValue.trim()) { toast.error('Department is required'); return; }
    try {
      await roomService.setFloorDepartment(editingFloorDept.buildingId, editingFloorDept.floorNumber, editFloorDeptValue.trim());
      setEditingFloorDept(null);
      fetchData();
      toast.success('Floor default department set');
    } catch (error) {
      toast.error(error.response?.data?.message || 'Failed to set floor department');
    }
  };

  const handleEditRoomDept = (roomId, currentOverride) => { setEditingRoomDept(roomId); setEditRoomDeptValue(currentOverride || ''); };

  const handleSaveRoomDept = async (roomId) => {
    if (!editRoomDeptValue.trim()) { toast.error('Department is required'); return; }
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
    if (!newBuildingName.trim()) { toast.error('Building name is required'); return; }
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
        name: student.name, rollNo: student.rollNo, department: student.department, email: student.email,
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
      toast.error('Please fill in name, roll no, and department'); return;
    }
    try {
      await roomService.allocateStudent(addToRoom.roomId, {
        name: manualForm.name, rollNo: manualForm.rollNo, department: manualForm.department,
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

  const currentBuilding = buildings.find(b => b.id === activeBuilding);
  const currentFloor = currentBuilding?.floors.find(f => f.floorNumber === activeFloor) ?? currentBuilding?.floors[0] ?? null;

  const totalStudents = allocations.length;
  const totalRooms = buildings.reduce((sum, b) => sum + b.floors.reduce((s, f) => s + f.rooms.length, 0), 0);
  const totalFloors = buildings.reduce((s, b) => s + b.floors.length, 0);

  const floorStudents = currentFloor
    ? allocations.filter(a => a.buildingId === currentBuilding.id && a.floor === currentFloor.floorNumber).length
    : 0;
  const floorOccupied = currentFloor
    ? currentFloor.rooms.filter(r => getRoomOccupants(r.id).length > 0).length
    : 0;

  const filteredBuildings = [...buildings]
    .filter(b => !buildingSearch || b.name.toLowerCase().includes(buildingSearch.toLowerCase()))
    .sort((a, b) => {
      if (a.gender !== b.gender) return a.gender === 'GIRL' ? 1 : -1;
      return a.name.localeCompare(b.name);
    });

  const isEditingFloorDept = editingFloorDept
    && currentBuilding
    && editingFloorDept.buildingId === currentBuilding.id
    && editingFloorDept.floorNumber === currentFloor?.floorNumber;

  if (loading) {
    return (
      <div className="d-flex justify-content-center align-items-center" style={{ minHeight: '60vh' }}>
        <div className="spinner-border text-primary" role="status">
          <span className="visually-hidden">Loading...</span>
        </div>
      </div>
    );
  }

  const genderBadgeStyle = (gender) => gender === 'GIRL'
    ? { backgroundColor: 'var(--badge-girls-bg)', color: 'var(--badge-girls-text)', border: '1px solid var(--badge-girls-border)' }
    : { backgroundColor: 'var(--badge-boys-bg)', color: 'var(--badge-boys-text)', border: '1px solid var(--badge-boys-border)' };

  const typeBadgeStyle = (type) => type === 'NRI'
    ? { backgroundColor: 'var(--badge-nri-bg)', color: 'var(--badge-nri-text)', border: '1px solid var(--badge-nri-border)' }
    : { backgroundColor: 'var(--badge-regular-bg)', color: 'var(--badge-regular-text)', border: '1px solid var(--badge-regular-border)' };

  return (
    <>
      {/* Settings Panel */}
      {showSettings && (
        <div className="card shadow-sm border-primary mb-4">
          <div className="card-header bg-primary text-white d-flex justify-content-between align-items-center">
            <h5 className="mb-0"><FontAwesomeIcon icon={faCog} /> Room Settings</h5>
            <button className="btn btn-sm btn-outline-light" onClick={() => setShowSettings(false)}>
              <FontAwesomeIcon icon={faTimes} />
            </button>
          </div>
          <div className="card-body">
            <div className="row g-3">
              <div className="col-md-4">
                <label className="form-label fw-semibold">Default Max Rooms Per Floor</label>
                <input type="number" className="form-control" value={config.maxRoomsPerFloor}
                  onChange={(e) => setConfig({ ...config, maxRoomsPerFloor: parseInt(e.target.value) || 1 })}
                  min="1" max="20" />
                <small className="text-muted">Applies to new floors</small>
              </div>
              <div className="col-md-4">
                <label className="form-label fw-semibold">Default Max Members Per Room</label>
                <input type="number" className="form-control" value={config.maxMembersPerRoom}
                  onChange={(e) => setConfig({ ...config, maxMembersPerRoom: parseInt(e.target.value) || 1 })}
                  min="1" max="12" />
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
      )}

      {/* Stats Bar */}
      <div className="row g-3 mb-4">
        {[
          { icon: faBuilding, color: 'var(--color-info)', label: 'Buildings', value: buildings.length },
          { icon: faDoorOpen, color: 'var(--color-success)', label: 'Total Rooms', value: totalRooms },
          { icon: faUserGraduate, color: 'var(--accent-purple)', label: 'Total Students', value: totalStudents },
          { icon: faLayerGroup, color: 'var(--color-warning)', label: 'Total Floors', value: totalFloors },
        ].map(({ icon, color, label, value }) => (
          <div className="col-6 col-md-3" key={label}>
            <div className="card shadow-sm h-100">
              <div className="card-body py-3">
                <p className="text-muted mb-1" style={{ fontSize: '0.8rem' }}>
                  <FontAwesomeIcon icon={icon} style={{ color }} className="me-1" />{label}
                </p>
                <h3 className="mb-0 fw-bold" style={{ color }}>{value}</h3>
              </div>
            </div>
          </div>
        ))}
      </div>

      {/* Main two-panel layout */}
      <div className="d-flex" style={{
        border: '1px solid var(--color-bg-tertiary)',
        borderRadius: 'var(--border-radius-lg)',
        overflow: 'hidden',
        minHeight: '600px',
        boxShadow: 'var(--shadow-md)',
      }}>

        {/* ── LEFT: Building List ── */}
        <div style={{
          width: '340px',
          flexShrink: 0,
          borderRight: '1px solid var(--color-bg-tertiary)',
          backgroundColor: 'var(--color-bg-primary)',
          display: 'flex',
          flexDirection: 'column',
        }}>
          {/* Search */}
          <div style={{ padding: '0.75rem 1rem', borderBottom: '1px solid var(--color-bg-tertiary)' }}>
            <div className="input-group input-group-sm">
              <span className="input-group-text" style={{ backgroundColor: 'var(--color-bg-secondary)', border: '1px solid var(--color-bg-tertiary)' }}>
                <FontAwesomeIcon icon={faSearch} style={{ color: 'var(--color-text-muted)' }} />
              </span>
              <input
                type="text"
                className="form-control"
                placeholder="Search buildings..."
                value={buildingSearch}
                onChange={(e) => setBuildingSearch(e.target.value)}
                style={{ backgroundColor: 'var(--color-bg-secondary)', border: '1px solid var(--color-bg-tertiary)', color: 'var(--color-text-primary)' }}
              />
              {buildingSearch && (
                <button className="btn btn-outline-secondary btn-sm" onClick={() => setBuildingSearch('')}>
                  <FontAwesomeIcon icon={faTimes} />
                </button>
              )}
            </div>
          </div>

          {/* Table header */}
          <div style={{
            display: 'grid',
            gridTemplateColumns: '1fr 80px 48px 56px',
            padding: '0.4rem 1rem',
            backgroundColor: 'var(--color-bg-tertiary)',
            fontSize: '0.7rem',
            fontWeight: 600,
            color: 'var(--color-text-muted)',
            textTransform: 'uppercase',
            letterSpacing: '0.04em',
            borderBottom: '1px solid var(--color-bg-tertiary)',
          }}>
            <span>Building</span>
            <span style={{ textAlign: 'center' }}>Type</span>
            <span style={{ textAlign: 'center' }}>Rooms</span>
            <span style={{ textAlign: 'center' }}>Students</span>
          </div>

          {/* Building rows */}
          <div style={{ flex: 1, overflowY: 'auto' }}>
            {filteredBuildings.map(b => {
              const isActive = activeBuilding === b.id;
              const bRooms = b.floors.reduce((s, f) => s + f.rooms.length, 0);
              const bStudents = allocations.filter(a => a.buildingId === b.id).length;
              return (
                <div key={b.id}>
                  {editingBuildingId === b.id ? (
                    <div style={{
                      padding: '0.5rem 1rem',
                      backgroundColor: 'var(--row-highlight)',
                      borderBottom: '1px solid var(--color-bg-tertiary)',
                      display: 'flex',
                      alignItems: 'center',
                      gap: '0.4rem',
                    }}>
                      <input
                        type="text"
                        className="form-control form-control-sm"
                        value={editBuildingName}
                        onChange={(e) => setEditBuildingName(e.target.value)}
                        onKeyDown={(e) => e.key === 'Enter' && handleRenameBuilding()}
                        autoFocus
                        style={{ flex: 1 }}
                      />
                      <button className="btn btn-success btn-sm" onClick={handleRenameBuilding}>
                        <FontAwesomeIcon icon={faCheck} />
                      </button>
                      <button className="btn btn-secondary btn-sm" onClick={() => setEditingBuildingId(null)}>
                        <FontAwesomeIcon icon={faTimes} />
                      </button>
                    </div>
                  ) : (
                    <div
                      onClick={() => setActiveBuilding(b.id)}
                      style={{
                        display: 'grid',
                        gridTemplateColumns: '1fr 80px 48px 56px',
                        alignItems: 'center',
                        padding: '0.55rem 1rem',
                        cursor: 'pointer',
                        backgroundColor: isActive ? 'var(--row-highlight)' : 'transparent',
                        borderBottom: '1px solid var(--color-bg-tertiary)',
                        borderLeft: isActive ? '3px solid var(--accent-blue-vivid)' : '3px solid transparent',
                        transition: 'background-color 0.15s',
                      }}
                    >
                      <div className="d-flex align-items-center gap-2" style={{ minWidth: 0 }}>
                        <FontAwesomeIcon
                          icon={faBuilding}
                          style={{ color: b.gender === 'GIRL' ? 'var(--accent-pink)' : 'var(--accent-blue-mid)', flexShrink: 0 }}
                          fontSize="0.85rem"
                        />
                        <span className="fw-semibold text-truncate" style={{
                          fontSize: '0.85rem',
                          color: isActive ? 'var(--accent-blue-vivid)' : 'var(--color-text-primary)',
                        }}>
                          {b.name}
                        </span>
                      </div>
                      <div style={{ textAlign: 'center' }}>
                        <span style={{
                          ...genderBadgeStyle(b.gender),
                          fontSize: '0.65rem',
                          fontWeight: 600,
                          padding: '2px 7px',
                          borderRadius: '12px',
                          display: 'inline-block',
                        }}>
                          {b.gender === 'GIRL' ? 'Girls' : 'Boys'}
                        </span>
                      </div>
                      <div style={{ textAlign: 'center', fontSize: '0.85rem', color: 'var(--color-text-secondary)' }}>
                        {bRooms}
                      </div>
                      <div style={{ textAlign: 'center', fontSize: '0.85rem', color: 'var(--color-text-secondary)' }}>
                        {bStudents}
                      </div>
                    </div>
                  )}
                </div>
              );
            })}
            {filteredBuildings.length === 0 && (
              <div className="text-center text-muted py-4" style={{ fontSize: '0.85rem' }}>
                No buildings found
              </div>
            )}
          </div>

          {/* Add building */}
          <div style={{ borderTop: '1px solid var(--color-bg-tertiary)', padding: '0.75rem 1rem' }}>
            <button
              className="btn btn-outline-success btn-sm w-100"
              onClick={() => setShowAddBuilding(!showAddBuilding)}
            >
              <FontAwesomeIcon icon={faPlus} className="me-1" /> Add Building
            </button>
          </div>

          {/* Add building form */}
          {showAddBuilding && (
            <div style={{
              padding: '0.75rem 1rem',
              borderTop: '1px solid var(--color-bg-tertiary)',
              backgroundColor: 'var(--color-bg-secondary)',
            }}>
              <div className="mb-2">
                <input
                  type="text"
                  className="form-control form-control-sm"
                  placeholder="Building name *"
                  value={newBuildingName}
                  onChange={(e) => setNewBuildingName(e.target.value)}
                />
              </div>
              <div className="d-flex gap-2 mb-2">
                <select className="form-select form-select-sm" value={newBuildingType} onChange={(e) => setNewBuildingType(e.target.value)}>
                  <option value="NORMAL">Regular</option>
                  <option value="NRI">NRI</option>
                </select>
                <select className="form-select form-select-sm" value={newBuildingGender} onChange={(e) => setNewBuildingGender(e.target.value)}>
                  <option value="BOY">Boys</option>
                  <option value="GIRL">Girls</option>
                </select>
              </div>
              <div className="d-flex gap-2">
                <button className="btn btn-success btn-sm flex-fill" onClick={handleAddBuilding}>
                  <FontAwesomeIcon icon={faCheck} /> Add
                </button>
                <button className="btn btn-secondary btn-sm" onClick={() => setShowAddBuilding(false)}>
                  Cancel
                </button>
              </div>
            </div>
          )}
        </div>

        {/* ── RIGHT: Building Detail ── */}
        <div style={{
          flex: 1,
          backgroundColor: 'var(--color-bg-secondary)',
          display: 'flex',
          flexDirection: 'column',
          overflowY: 'auto',
          minWidth: 0,
        }}>
          {!currentBuilding ? (
            <div className="d-flex flex-column justify-content-center align-items-center h-100 text-muted gap-2">
              <FontAwesomeIcon icon={faBuilding} style={{ fontSize: '2rem', color: 'var(--color-bg-tertiary)' }} />
              <span style={{ fontSize: '0.9rem' }}>Select a building to view details</span>
            </div>
          ) : (
            <>
              {/* Building header */}
              <div style={{
                padding: '0.9rem 1.25rem',
                backgroundColor: 'var(--color-bg-primary)',
                borderBottom: '1px solid var(--color-bg-tertiary)',
                display: 'flex',
                alignItems: 'center',
                justifyContent: 'space-between',
                flexWrap: 'wrap',
                gap: '0.5rem',
              }}>
                <div className="d-flex align-items-center gap-3">
                  <div style={{
                    width: '36px',
                    height: '36px',
                    borderRadius: 'var(--border-radius-md)',
                    backgroundColor: currentBuilding.gender === 'GIRL' ? 'var(--badge-girls-bg)' : 'var(--badge-boys-bg)',
                    display: 'flex',
                    alignItems: 'center',
                    justifyContent: 'center',
                    flexShrink: 0,
                  }}>
                    <FontAwesomeIcon
                      icon={faBuilding}
                      style={{ color: currentBuilding.gender === 'GIRL' ? 'var(--accent-pink)' : 'var(--accent-blue-vivid)' }}
                    />
                  </div>
                  <div>
                    <div className="fw-semibold" style={{ fontSize: '1rem', color: 'var(--color-text-primary)' }}>
                      {currentBuilding.name}
                    </div>
                    <div style={{ fontSize: '0.78rem', color: 'var(--color-text-muted)' }}>
                      {currentBuilding.gender === 'GIRL' ? 'Girls' : 'Boys'} &middot;&nbsp;
                      {currentBuilding.floors.reduce((s, f) => s + f.rooms.length, 0)} rooms &middot;&nbsp;
                      {allocations.filter(a => a.buildingId === currentBuilding.id).length} students
                    </div>
                  </div>
                </div>
                <div className="d-flex align-items-center gap-2 flex-wrap">
                  {/* Gender toggle */}
                  <div className="btn-group btn-group-sm" style={{ borderRadius: '12px', overflow: 'hidden' }}>
                    <button
                      className="btn btn-sm"
                      style={currentBuilding.gender !== 'GIRL'
                        ? { ...genderBadgeStyle('BOY'), fontSize: '0.7rem', fontWeight: 600 }
                        : { fontSize: '0.7rem', color: 'var(--color-text-muted)', border: '1px solid var(--color-bg-tertiary)' }}
                      onClick={() => currentBuilding.gender === 'GIRL' && handleToggleBuildingGender(currentBuilding.id, currentBuilding.gender)}
                    >Boys</button>
                    <button
                      className="btn btn-sm"
                      style={currentBuilding.gender === 'GIRL'
                        ? { ...genderBadgeStyle('GIRL'), fontSize: '0.7rem', fontWeight: 600 }
                        : { fontSize: '0.7rem', color: 'var(--color-text-muted)', border: '1px solid var(--color-bg-tertiary)' }}
                      onClick={() => currentBuilding.gender !== 'GIRL' && handleToggleBuildingGender(currentBuilding.id, currentBuilding.gender)}
                    >Girls</button>
                  </div>
                  {/* Type toggle */}
                  <div className="btn-group btn-group-sm" style={{ borderRadius: '12px', overflow: 'hidden' }}>
                    <button
                      className="btn btn-sm"
                      style={currentBuilding.type !== 'NRI'
                        ? { ...typeBadgeStyle('NORMAL'), fontSize: '0.7rem', fontWeight: 600 }
                        : { fontSize: '0.7rem', color: 'var(--color-text-muted)', border: '1px solid var(--color-bg-tertiary)' }}
                      onClick={() => currentBuilding.type === 'NRI' && handleToggleBuildingType(currentBuilding.id, currentBuilding.type)}
                    >Regular</button>
                    <button
                      className="btn btn-sm"
                      style={currentBuilding.type === 'NRI'
                        ? { ...typeBadgeStyle('NRI'), fontSize: '0.7rem', fontWeight: 600 }
                        : { fontSize: '0.7rem', color: 'var(--color-text-muted)', border: '1px solid var(--color-bg-tertiary)' }}
                      onClick={() => currentBuilding.type !== 'NRI' && handleToggleBuildingType(currentBuilding.id, currentBuilding.type)}
                    >NRI</button>
                  </div>
                  <button
                    className="btn btn-outline-secondary btn-sm"
                    style={{ padding: '3px 8px' }}
                    onClick={() => { setEditingBuildingId(currentBuilding.id); setEditBuildingName(currentBuilding.name); }}
                    title="Rename"
                  >
                    <FontAwesomeIcon icon={faEdit} />
                  </button>
                  <button
                    className="btn btn-outline-danger btn-sm"
                    style={{ padding: '3px 8px' }}
                    onClick={() => handleRemoveBuilding(currentBuilding.id, currentBuilding.name)}
                    title="Remove building"
                  >
                    <FontAwesomeIcon icon={faTrash} />
                  </button>
                  <button
                    className={`btn btn-sm ${showAutoAllocate ? 'btn-success' : 'btn-outline-success'}`}
                    onClick={() => { setShowAutoAllocate(!showAutoAllocate); setAutoAllocateResult(null); }}
                  >
                    <FontAwesomeIcon icon={faMagic} className="me-1" /> Auto Allocate
                  </button>
                  <button
                    className={`btn btn-sm ${showSettings ? 'btn-primary' : 'btn-outline-primary'}`}
                    onClick={() => setShowSettings(!showSettings)}
                  >
                    <FontAwesomeIcon icon={faCog} />
                  </button>
                </div>
              </div>

              {/* Auto-allocate panel */}
              {showAutoAllocate && (
                <div style={{
                  margin: '1rem 1.25rem 0',
                  padding: '1rem',
                  backgroundColor: 'var(--color-bg-primary)',
                  borderRadius: 'var(--border-radius-md)',
                  border: '1px solid var(--color-success)',
                }}>
                  <h6 className="fw-bold mb-2" style={{ color: 'var(--color-success)' }}>
                    <FontAwesomeIcon icon={faMagic} className="me-2" />
                    Auto Allocate — {currentBuilding.name}
                  </h6>
                  <p className="text-muted mb-3" style={{ fontSize: '0.8rem' }}>
                    Assigns unallocated students by department, matching room/floor defaults and respecting capacity.
                  </p>
                  <div className="d-flex align-items-end gap-3 flex-wrap">
                    <div>
                      <label className="form-label fw-semibold" style={{ fontSize: '0.8rem' }}>Floor (optional)</label>
                      <select className="form-select form-select-sm" value={autoAllocateFloor} onChange={(e) => setAutoAllocateFloor(e.target.value)} style={{ minWidth: '130px' }}>
                        <option value="">All floors</option>
                        {currentBuilding.floors.map(f => (
                          <option key={f.floorNumber} value={f.floorNumber}>Floor {f.floorNumber}</option>
                        ))}
                      </select>
                    </div>
                    <button
                      className="btn btn-success btn-sm"
                      disabled={autoAllocating}
                      onClick={() => handleAutoAllocate(currentBuilding.id)}
                    >
                      {autoAllocating
                        ? <><span className="spinner-border spinner-border-sm me-1" role="status" /> Running...</>
                        : <><FontAwesomeIcon icon={faMagic} className="me-1" /> Run Allocation</>}
                    </button>
                  </div>

                  {autoAllocateResult && (
                    <div className="mt-3 p-3" style={{ backgroundColor: 'var(--color-bg-tertiary)', borderRadius: 'var(--border-radius-sm)' }}>
                      <h6 className="fw-bold mb-2">Allocation complete</h6>
                      <div className="d-flex gap-4 mb-2 flex-wrap">
                        {[
                          { label: 'Processed', value: autoAllocateResult.studentsProcessed, color: 'var(--color-text-primary)' },
                          { label: 'Assigned', value: autoAllocateResult.assigned, color: 'var(--color-success)' },
                          { label: 'Remaining', value: autoAllocateResult.remaining, color: 'var(--color-danger)' },
                          { label: 'Rooms used', value: autoAllocateResult.roomsUsed, color: 'var(--color-text-primary)' },
                        ].map(({ label, value, color }) => (
                          <div key={label}>
                            <div style={{ fontSize: '0.7rem', color: 'var(--color-text-muted)' }}>{label}</div>
                            <div className="fw-bold" style={{ color }}>{value}</div>
                          </div>
                        ))}
                      </div>
                      {autoAllocateResult.byDepartment?.length > 0 && (
                        <div className="mb-2">
                          {autoAllocateResult.byDepartment.map((d, i) => (
                            <span key={i} className="badge bg-secondary me-1 mb-1" style={{ fontSize: '0.7rem' }}>
                              {d.department || 'No dept'}: {d.assigned}/{d.studentsNeedingRooms}
                            </span>
                          ))}
                        </div>
                      )}
                      {autoAllocateResult.unassigned?.length > 0 && (
                        <div className="table-responsive" style={{ maxHeight: '160px', overflowY: 'auto' }}>
                          <table className="table table-sm table-hover mb-0" style={{ fontSize: '0.8rem' }}>
                            <thead><tr><th>Roll No</th><th>Name</th><th>Dept</th><th>Reason</th></tr></thead>
                            <tbody>
                              {autoAllocateResult.unassigned.map((u, i) => (
                                <tr key={i}>
                                  <td>{u.rollNo}</td><td>{u.name}</td><td>{u.department}</td>
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
              )}

              {/* Floor tabs */}
              <div style={{ padding: '1rem 1.25rem 0' }}>
                <div className="d-flex align-items-center gap-2 flex-wrap">
                  {currentBuilding.floors.map(floor => {
                    const isActive = activeFloor === floor.floorNumber;
                    return (
                      <button
                        key={floor.floorNumber}
                        onClick={() => setActiveFloor(floor.floorNumber)}
                        style={{
                          padding: '0.3rem 0.9rem',
                          borderRadius: '20px',
                          border: isActive ? 'none' : '1px solid var(--color-bg-tertiary)',
                          backgroundColor: isActive ? 'var(--accent-blue-vivid)' : 'var(--color-bg-primary)',
                          color: isActive ? '#fff' : 'var(--color-text-muted)',
                          fontSize: '0.82rem',
                          fontWeight: isActive ? 600 : 400,
                          cursor: 'pointer',
                          transition: 'all 0.15s',
                          boxShadow: isActive ? 'var(--shadow-sm)' : 'none',
                        }}
                      >
                        <FontAwesomeIcon icon={faLayerGroup} className="me-1" style={{ fontSize: '0.75rem' }} />
                        Floor {floor.floorNumber}
                      </button>
                    );
                  })}
                  <button
                    className="btn btn-outline-secondary btn-sm"
                    style={{ borderRadius: '20px', padding: '0.25rem 0.7rem', fontSize: '0.8rem' }}
                    onClick={() => handleAddFloor(currentBuilding.id)}
                    title="Add floor"
                  >
                    <FontAwesomeIcon icon={faPlus} />
                  </button>
                </div>
              </div>

              {/* Floor controls & stats */}
              {currentFloor && (
                <div style={{
                  padding: '0.6rem 1.25rem',
                  display: 'flex',
                  alignItems: 'center',
                  justifyContent: 'space-between',
                  flexWrap: 'wrap',
                  gap: '0.5rem',
                }}>
                  <div className="d-flex align-items-center gap-3">
                    <span style={{ fontSize: '0.82rem', fontWeight: 600, color: 'var(--color-info)' }}>
                      <FontAwesomeIcon icon={faUserGraduate} className="me-1" />{floorStudents} Students
                    </span>
                    <span style={{ fontSize: '0.82rem', fontWeight: 600, color: 'var(--color-success)' }}>
                      <FontAwesomeIcon icon={faDoorOpen} className="me-1" />{currentFloor.rooms.length} Rooms
                    </span>
                    <span style={{ fontSize: '0.82rem', fontWeight: 600, color: 'var(--color-warning)' }}>
                      {floorOccupied} Occupied
                    </span>
                  </div>
                  <div className="d-flex align-items-center gap-1">
                    {isEditingFloorDept ? (
                      <div className="d-flex align-items-center gap-1">
                        <input
                          type="text"
                          className="form-control form-control-sm"
                          style={{ width: '110px' }}
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
                        onClick={() => handleEditFloorDept(currentBuilding.id, currentFloor.floorNumber, currentFloor.department)}
                        title="Set default department"
                      >
                        <FontAwesomeIcon icon={faTag} className="me-1" />
                        {currentFloor.department ? `Dept: ${currentFloor.department}` : 'Set dept'}
                      </button>
                    )}
                    <button
                      className="btn btn-outline-secondary btn-sm"
                      onClick={() => handleRemoveRoom(currentBuilding.id, currentFloor.floorNumber)}
                      disabled={currentFloor.rooms.length <= 1}
                      title="Remove last room"
                    >
                      <FontAwesomeIcon icon={faMinus} />
                    </button>
                    <button
                      className="btn btn-outline-secondary btn-sm"
                      onClick={() => handleAddRoom(currentBuilding.id, currentFloor.floorNumber)}
                      disabled={currentFloor.rooms.length >= 20}
                      title="Add room"
                    >
                      <FontAwesomeIcon icon={faPlus} />
                    </button>
                    <button
                      className="btn btn-outline-danger btn-sm"
                      onClick={() => handleRemoveFloor(currentBuilding.id, currentFloor.floorNumber)}
                      title="Remove this floor"
                    >
                      <FontAwesomeIcon icon={faTimes} />
                    </button>
                  </div>
                </div>
              )}

              {/* Divider */}
              <div style={{ height: '1px', backgroundColor: 'var(--color-bg-tertiary)', margin: '0 1.25rem' }} />

              {/* Room grid */}
              <div style={{ padding: '1rem 1.25rem 1.25rem', flex: 1 }}>
                {!currentFloor ? (
                  <p className="text-muted text-center py-4" style={{ fontSize: '0.9rem' }}>No floors yet — add one above.</p>
                ) : (
                  <div className="row g-3">
                    {currentFloor.rooms.map(room => {
                      const occupants = getRoomOccupants(room.id);
                      const isFull = occupants.length >= room.maxMembers;
                      const isOccupied = occupants.length > 0;
                      const isEditingCapacity = editingRoom?.roomId === room.id;
                      const isEditingNumber = editingRoomNumber === room.id;
                      const isEditingDept = editingRoomDept === room.id;

                      const roomBorderColor = isFull
                        ? 'var(--color-danger)'
                        : isOccupied
                          ? 'var(--color-info)'
                          : 'var(--color-bg-tertiary)';
                      const roomHeaderBg = isFull
                        ? 'var(--room-full-bg)'
                        : isOccupied
                          ? '#e8f4ff'
                          : 'var(--color-bg-tertiary)';

                      return (
                        <div className="col-md-6 col-lg-4 col-xl-3" key={room.id}>
                          <div style={{
                            backgroundColor: 'var(--color-bg-primary)',
                            border: `1.5px solid ${roomBorderColor}`,
                            borderRadius: 'var(--border-radius-md)',
                            overflow: 'hidden',
                            boxShadow: isOccupied ? '0 2px 8px rgba(66,153,225,0.1)' : 'var(--shadow-sm)',
                            height: '100%',
                            display: 'flex',
                            flexDirection: 'column',
                          }}>
                            {/* Room card header */}
                            <div style={{
                              backgroundColor: roomHeaderBg,
                              padding: '0.5rem 0.75rem',
                              display: 'flex',
                              alignItems: 'center',
                              justifyContent: 'space-between',
                              borderBottom: `1px solid ${roomBorderColor}`,
                            }}>
                              <span className="d-flex align-items-center gap-1" style={{ fontSize: '0.82rem', fontWeight: 600, color: isOccupied ? 'var(--accent-blue-vivid)' : 'var(--color-text-muted)' }}>
                                <FontAwesomeIcon icon={faDoorOpen} style={{ fontSize: '0.75rem' }} />
                                {isEditingNumber ? (
                                  <>
                                    <input
                                      type="text"
                                      className="form-control form-control-sm"
                                      style={{ width: '65px' }}
                                      value={editRoomNumberValue}
                                      onChange={(e) => setEditRoomNumberValue(e.target.value)}
                                      onKeyDown={(e) => e.key === 'Enter' && handleSaveRoomNumber(room.id)}
                                      autoFocus
                                    />
                                    <button className="btn btn-success btn-sm" style={{ padding: '1px 5px' }} onClick={() => handleSaveRoomNumber(room.id)}><FontAwesomeIcon icon={faCheck} /></button>
                                    <button className="btn btn-secondary btn-sm" style={{ padding: '1px 5px' }} onClick={() => setEditingRoomNumber(null)}><FontAwesomeIcon icon={faTimes} /></button>
                                  </>
                                ) : (
                                  <>
                                    Room {room.roomNumber}
                                    <button
                                      className="btn btn-link p-0 ms-1"
                                      style={{ fontSize: '0.65rem', color: 'var(--color-text-muted)' }}
                                      onClick={() => handleEditRoomNumber(room.id, room.roomNumber)}
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
                                      style={{ width: '52px' }}
                                      value={editMaxMembers}
                                      onChange={(e) => setEditMaxMembers(parseInt(e.target.value) || 1)}
                                      min="1" max="12"
                                    />
                                    <button className="btn btn-success btn-sm" style={{ padding: '1px 5px' }} onClick={handleSaveRoomCapacity}><FontAwesomeIcon icon={faCheck} /></button>
                                    <button className="btn btn-secondary btn-sm" style={{ padding: '1px 5px' }} onClick={() => setEditingRoom(null)}><FontAwesomeIcon icon={faTimes} /></button>
                                  </>
                                ) : (
                                  <>
                                    <span
                                      className={`badge ${isFull ? 'bg-danger' : isOccupied ? 'bg-primary' : 'bg-secondary'}`}
                                      style={{ fontSize: '0.7rem' }}
                                    >
                                      {occupants.length}/{room.maxMembers}
                                    </span>
                                    <button
                                      className="btn btn-link p-0 ms-1"
                                      style={{ fontSize: '0.65rem', color: 'var(--color-text-muted)' }}
                                      onClick={() => handleEditRoomCapacity(room.id, room.maxMembers)}
                                      title="Edit capacity"
                                    >
                                      <FontAwesomeIcon icon={faEdit} />
                                    </button>
                                  </>
                                )}
                              </div>
                            </div>

                            {/* Department badge */}
                            <div style={{ padding: '0.3rem 0.75rem', display: 'flex', alignItems: 'center', gap: '0.3rem', flexWrap: 'wrap' }}>
                              {isEditingDept ? (
                                <>
                                  <input
                                    type="text"
                                    className="form-control form-control-sm"
                                    style={{ width: '80px' }}
                                    value={editRoomDeptValue}
                                    onChange={(e) => setEditRoomDeptValue(e.target.value)}
                                    placeholder="e.g. ECE"
                                    onKeyDown={(e) => e.key === 'Enter' && handleSaveRoomDept(room.id)}
                                    autoFocus
                                  />
                                  <button className="btn btn-success btn-sm" style={{ padding: '1px 5px' }} onClick={() => handleSaveRoomDept(room.id)}><FontAwesomeIcon icon={faCheck} /></button>
                                  <button className="btn btn-secondary btn-sm" style={{ padding: '1px 5px' }} onClick={() => setEditingRoomDept(null)}><FontAwesomeIcon icon={faTimes} /></button>
                                </>
                              ) : (
                                <>
                                  {room.effectiveDepartment ? (
                                    <span
                                      className={`badge ${room.departmentOverride ? 'bg-warning text-dark' : 'bg-info text-dark'}`}
                                      style={{ fontSize: '0.65rem' }}
                                      title={room.departmentOverride ? 'Room override' : 'Floor default'}
                                    >
                                      <FontAwesomeIcon icon={faTag} className="me-1" />
                                      {room.effectiveDepartment} ({room.departmentOverride ? 'Override' : 'Default'})
                                    </span>
                                  ) : (
                                    <span style={{ fontSize: '0.68rem', color: 'var(--color-text-muted)' }}>No dept</span>
                                  )}
                                  <button
                                    className="btn btn-link p-0"
                                    style={{ fontSize: '0.65rem', color: 'var(--color-text-muted)' }}
                                    onClick={() => handleEditRoomDept(room.id, room.departmentOverride)}
                                  >
                                    <FontAwesomeIcon icon={faEdit} />
                                  </button>
                                  {room.departmentOverride && (
                                    <button
                                      className="btn btn-link p-0 text-danger"
                                      style={{ fontSize: '0.65rem' }}
                                      onClick={() => handleRemoveRoomDeptOverride(room.id)}
                                      title="Remove override"
                                    >
                                      <FontAwesomeIcon icon={faTimes} />
                                    </button>
                                  )}
                                </>
                              )}
                            </div>

                            {/* Occupants / empty state */}
                            <div style={{ flex: 1, padding: '0.25rem 0.75rem 0.5rem' }}>
                              {occupants.length === 0 ? (
                                <p className="text-muted text-center mb-0" style={{ fontSize: '0.8rem', padding: '0.5rem 0' }}>Available</p>
                              ) : (
                                <div>
                                  {occupants.map((student, idx) => (
                                    <div key={idx} style={{
                                      display: 'flex',
                                      alignItems: 'center',
                                      justifyContent: 'space-between',
                                      padding: '0.2rem 0',
                                      borderBottom: idx < occupants.length - 1 ? '1px solid var(--color-bg-tertiary)' : 'none',
                                    }}>
                                      <div style={{ minWidth: 0 }}>
                                        <div className="fw-semibold text-truncate" style={{ fontSize: '0.8rem', color: 'var(--color-text-primary)' }}>
                                          {student.name}
                                        </div>
                                        <div className="text-muted text-truncate" style={{ fontSize: '0.7rem' }}>
                                          {student.rollNo} &middot; {student.department}
                                        </div>
                                      </div>
                                      <button
                                        className="btn btn-outline-danger btn-sm ms-1"
                                        style={{ padding: '1px 5px', fontSize: '0.65rem', flexShrink: 0 }}
                                        onClick={() => handleRemoveStudent(student.studentEmail, student.name)}
                                        title="Remove"
                                      >
                                        <FontAwesomeIcon icon={faTrash} />
                                      </button>
                                    </div>
                                  ))}
                                </div>
                              )}
                            </div>

                            {/* Add student button */}
                            {!isFull && (
                              <div style={{ padding: '0.4rem 0.75rem', borderTop: '1px solid var(--color-bg-tertiary)' }}>
                                <button
                                  className="btn btn-outline-primary btn-sm w-100"
                                  style={{ fontSize: '0.75rem' }}
                                  onClick={() => handleOpenAddModal(room.id, room.roomNumber)}
                                >
                                  <FontAwesomeIcon icon={faUserPlus} className="me-1" /> Add Student
                                </button>
                              </div>
                            )}
                          </div>
                        </div>
                      );
                    })}
                  </div>
                )}
              </div>
            </>
          )}
        </div>
      </div>

      {/* Add Student Modal */}
      {showAddModal && (
        <div className="modal d-block" style={{ backgroundColor: 'rgba(0,0,0,0.5)' }}>
          <div className="modal-dialog modal-lg modal-dialog-centered modal-dialog-scrollable">
            <div className="modal-content">
              <div className="modal-header">
                <h5 className="modal-title">
                  <FontAwesomeIcon icon={faUserPlus} className="me-2" />
                  Add Student to Room {addToRoom?.roomNumber}
                </h5>
                <button type="button" className="btn-close" onClick={() => setShowAddModal(false)} />
              </div>
              <div className="modal-body">
                <div className="btn-group w-100 mb-3" role="group">
                  <button className={`btn ${!manualEntry ? 'btn-primary' : 'btn-outline-primary'}`} onClick={() => setManualEntry(false)}>
                    Select from Registered Students
                  </button>
                  <button className={`btn ${manualEntry ? 'btn-primary' : 'btn-outline-primary'}`} onClick={() => setManualEntry(true)}>
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
                            <tr><th>Roll No</th><th>Name</th><th>Department</th><th>Action</th></tr>
                          </thead>
                          <tbody>
                            {filteredModalStudents().map((student, i) => (
                              <tr key={i}>
                                <td>{student.rollNo}</td>
                                <td>{student.name}</td>
                                <td>{student.department}</td>
                                <td>
                                  <button className="btn btn-primary btn-sm" onClick={() => handleAddStudentFromList(student)}>
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
                      <input type="text" className="form-control" value={manualForm.name}
                        onChange={(e) => setManualForm({ ...manualForm, name: e.target.value })}
                        placeholder="Student name" />
                    </div>
                    <div className="row mb-3">
                      <div className="col-md-6">
                        <label className="form-label fw-semibold">Roll No *</label>
                        <input type="text" className="form-control" value={manualForm.rollNo}
                          onChange={(e) => setManualForm({ ...manualForm, rollNo: e.target.value })}
                          placeholder="e.g. 2024503001" />
                      </div>
                      <div className="col-md-6">
                        <label className="form-label fw-semibold">Department *</label>
                        <input type="text" className="form-control" value={manualForm.department}
                          onChange={(e) => setManualForm({ ...manualForm, department: e.target.value })}
                          placeholder="e.g. CSE" />
                      </div>
                    </div>
                    <div className="mb-3">
                      <label className="form-label fw-semibold">Email (optional)</label>
                      <input type="email" className="form-control" value={manualForm.email}
                        onChange={(e) => setManualForm({ ...manualForm, email: e.target.value })}
                        placeholder="student@college.edu" />
                    </div>
                    <button className="btn btn-primary" onClick={handleAddManualStudent}>
                      <FontAwesomeIcon icon={faUserPlus} className="me-1" /> Add Student
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
