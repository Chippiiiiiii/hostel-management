import { useState, useEffect } from 'react';
import { useNavigate, Link } from 'react-router-dom';
import outpassService from '../../services/outpassService';
import roomService from '../../services/roomService';
import toast from 'react-hot-toast';
import LoadingSpinner from '../../components/common/LoadingSpinner';
import { FontAwesomeIcon } from '@fortawesome/react-fontawesome';
import { faEdit, faLock, faArrowLeft, faBuilding, faLayerGroup, faDoorOpen, faCamera, faIdCard } from '@fortawesome/free-solid-svg-icons';

const EditProfile = () => {
  const [formData, setFormData] = useState({
    hostel: '',
    roomNumber: '',
    contactNumber: '',
    parentNumber: '',
  });
  const [profile, setProfile] = useState(null);
  const [loading, setLoading] = useState(true);
  const [submitting, setSubmitting] = useState(false);
  const [photoPreview, setPhotoPreview] = useState(null);
  const [newPhoto, setNewPhoto] = useState(null);
  const [idCardPreview, setIdCardPreview] = useState(null);
  const [newIdCard, setNewIdCard] = useState(null);
  const [idCardSubmitting, setIdCardSubmitting] = useState(false);
  const navigate = useNavigate();

  const [buildings, setBuildings] = useState([]);
  const [selectedBuilding, setSelectedBuilding] = useState('');
  const [selectedFloor, setSelectedFloor] = useState('');
  const [selectedRoom, setSelectedRoom] = useState('');
  const [currentAllocation, setCurrentAllocation] = useState(null);
  const [availableFloors, setAvailableFloors] = useState([]);
  const [availableRooms, setAvailableRooms] = useState([]);
  const [allocations, setAllocations] = useState([]);

  useEffect(() => {
    fetchData();
  }, []);

  const fetchData = async () => {
    try {
      const [profileRes, buildingsRes, allocationsRes] = await Promise.all([
        outpassService.getStudentProfile(),
        roomService.getBuildingsStudent(),
        roomService.getAllocationsStudent(),
      ]);

      const data = profileRes.data;
      setProfile(data);
      setPhotoPreview(data.profilePicture || null);
      setIdCardPreview(data.idCardPhoto || null);
      setFormData({
        hostel: data.hostel,
        roomNumber: data.roomNumber,
        contactNumber: data.contactNumber,
        parentNumber: data.parentNumber,
      });

      setBuildings(buildingsRes.data);
      setAllocations(allocationsRes.data);

      const allocationRes = await roomService.getStudentAllocation();
      if (allocationRes.data) {
        setCurrentAllocation(allocationRes.data);
        setSelectedBuilding(String(allocationRes.data.buildingId));
        const building = buildingsRes.data.find(b => b.id === allocationRes.data.buildingId);
        if (building) {
          setAvailableFloors(building.floors);
          setSelectedFloor(String(allocationRes.data.floor));
          const floor = building.floors.find(f => f.floorNumber === allocationRes.data.floor);
          if (floor) {
            setAvailableRooms(floor.rooms);
            setSelectedRoom(String(allocationRes.data.roomId));
          }
        }
      }
    } catch {
      toast.error('Failed to load profile');
    } finally {
      setLoading(false);
    }
  };

  const handleBuildingChange = (e) => {
    const buildingId = e.target.value;
    setSelectedBuilding(buildingId);
    setSelectedFloor('');
    setSelectedRoom('');
    setAvailableRooms([]);

    if (buildingId) {
      const building = buildings.find(b => b.id === parseInt(buildingId));
      setAvailableFloors(building ? building.floors : []);
    } else {
      setAvailableFloors([]);
    }
  };

  const handleFloorChange = (e) => {
    const floorNum = e.target.value;
    setSelectedFloor(floorNum);
    setSelectedRoom('');

    if (floorNum && selectedBuilding) {
      const building = buildings.find(b => b.id === parseInt(selectedBuilding));
      const floor = building?.floors.find(f => f.floorNumber === parseInt(floorNum));
      setAvailableRooms(floor ? floor.rooms : []);
    } else {
      setAvailableRooms([]);
    }
  };

  const getRoomOccupantCount = (roomId) => {
    const entry = allocations.find(a => a.roomId === roomId);
    return entry ? entry.occupantCount : 0;
  };

  const handleChange = (e) => {
    const { name, value } = e.target;
    setFormData({ ...formData, [name]: value });
  };

  const handlePhotoUpload = (e) => {
    const file = e.target.files[0];
    if (!file) return;
    if (file.size > 2 * 1024 * 1024) {
      toast.error('Photo must be under 2MB');
      return;
    }
    if (!file.type.startsWith('image/')) {
      toast.error('Please upload an image file');
      return;
    }
    const reader = new FileReader();
    reader.onloadend = () => {
      setPhotoPreview(reader.result);
      setNewPhoto(reader.result);
    };
    reader.readAsDataURL(file);
  };

  const handleIdCardUpload = (e) => {
    const file = e.target.files[0];
    if (!file) return;
    if (file.size > 2 * 1024 * 1024) {
      toast.error('ID card photo must be under 2MB');
      return;
    }
    if (!file.type.startsWith('image/')) {
      toast.error('Please upload an image file');
      return;
    }
    const reader = new FileReader();
    reader.onloadend = () => {
      setIdCardPreview(reader.result);
      setNewIdCard(reader.result);
    };
    reader.readAsDataURL(file);
  };

  const handleIdCardSubmit = async () => {
    if (!newIdCard) return;
    setIdCardSubmitting(true);
    try {
      await outpassService.updateIdCardPhoto(newIdCard);
      setNewIdCard(null);
      toast.success('Student ID card updated successfully!');
    } catch (error) {
      toast.error(error.response?.data?.message || 'Failed to update ID card photo');
    } finally {
      setIdCardSubmitting(false);
    }
  };

  const handleSubmit = async (e) => {
    e.preventDefault();

    if (formData.contactNumber.length !== 10 || formData.parentNumber.length !== 10) {
      toast.error('Contact numbers must be 10 digits');
      return;
    }

    // Room is locked once allocated (see room-locking rules) — only students without an
    // allocation yet (a pre-existing edge case) still pick a room here.
    if (!currentAllocation && (!selectedBuilding || !selectedFloor || !selectedRoom)) {
      toast.error('Please select your building, floor, and room');
      return;
    }

    setSubmitting(true);

    try {
      // Room fields (hostel/roomNumber) are never sent once locked — the backend rejects
      // any attempt to change them through this endpoint regardless, but omitting them
      // keeps the request honest about what this form can actually change.
      const updatedFormData = {
        contactNumber: formData.contactNumber,
        parentNumber: formData.parentNumber,
        ...(newPhoto ? { profilePicture: newPhoto } : {}),
      };

      await outpassService.updateStudentProfile(updatedFormData);
      if (!currentAllocation) {
        await roomService.allocateStudentSelf(parseInt(selectedRoom));
      }

      toast.success('Profile updated successfully!');
      navigate('/student/dashboard');
    } catch (error) {
      toast.error(error.response?.data?.message || 'Failed to update profile');
    } finally {
      setSubmitting(false);
    }
  };

  if (loading) return <LoadingSpinner />;

  return (
    <div className="container mt-4 mb-5">
      <div className="row justify-content-center">
        <div className="col-md-8">
          <div className="card shadow-lg card-fade-in">
            <div className="card-header d-flex align-items-center">
              <Link to="/student/dashboard" className="btn btn-outline-secondary btn-sm me-3">
                <FontAwesomeIcon icon={faArrowLeft} />
              </Link>
              <h4 className="mb-0"><FontAwesomeIcon icon={faEdit} /> Edit Profile</h4>
            </div>
            <div className="card-body p-4">
              {/* Profile Photo */}
              <div className="text-center mb-4">
                <div
                  style={{
                    width: '100px', height: '100px', borderRadius: '50%', margin: '0 auto',
                    border: '3px dashed var(--color-accent)', overflow: 'hidden', cursor: 'pointer',
                    display: 'flex', alignItems: 'center', justifyContent: 'center',
                    backgroundColor: 'var(--color-bg-tertiary)',
                  }}
                  onClick={() => document.getElementById('editPhotoInput').click()}
                >
                  {photoPreview ? (
                    <img src={photoPreview} alt="Profile" style={{ width: '100%', height: '100%', objectFit: 'cover' }} />
                  ) : (
                    <div className="text-center text-muted">
                      <FontAwesomeIcon icon={faCamera} style={{ fontSize: '1.5rem' }} />
                      <div style={{ fontSize: '0.65rem', marginTop: '4px' }}>Add Photo</div>
                    </div>
                  )}
                </div>
                <input
                  type="file"
                  id="editPhotoInput"
                  accept="image/*"
                  onChange={handlePhotoUpload}
                  style={{ display: 'none' }}
                />
                <small className="text-muted d-block mt-1">Click to {photoPreview ? 'change' : 'upload'} photo</small>
              </div>

              {/* Read-only fields */}
              <div className="alert alert-info mb-4">
                <h6 className="alert-heading fw-bold"><FontAwesomeIcon icon={faLock} /> Read-Only Information</h6>
                <div className="row">
                  <div className="col-md-6">
                    <p className="mb-1"><strong>Name:</strong> {profile?.name}</p>
                    <p className="mb-1"><strong>Email:</strong> {profile?.email}</p>
                  </div>
                  <div className="col-md-6">
                    <p className="mb-1"><strong>Roll No:</strong> {profile?.rollNo}</p>
                    <p className="mb-1"><strong>Department:</strong> {profile?.department}</p>
                  </div>
                </div>
                <small className="text-muted">These fields cannot be edited</small>
              </div>

              {/* Student ID Card - separate from the profile photo above; used by Wardens/Security
                  to verify identity when reviewing outpasses */}
              <div className="card mb-4">
                <div className="card-body">
                  <h6 className="fw-bold mb-3"><FontAwesomeIcon icon={faIdCard} /> Student ID Card</h6>
                  <div className="d-flex flex-column flex-sm-row align-items-sm-center gap-3">
                    <div
                      style={{
                        width: '140px', height: '90px', borderRadius: '8px',
                        border: '2px dashed var(--color-accent)', overflow: 'hidden', cursor: 'pointer',
                        display: 'flex', alignItems: 'center', justifyContent: 'center',
                        backgroundColor: 'var(--color-bg-tertiary)', flexShrink: 0,
                      }}
                      onClick={() => document.getElementById('idCardPhotoInput').click()}
                    >
                      {idCardPreview ? (
                        <img src={idCardPreview} alt="Student ID Card" style={{ width: '100%', height: '100%', objectFit: 'cover' }} />
                      ) : (
                        <div className="text-center text-muted">
                          <FontAwesomeIcon icon={faIdCard} style={{ fontSize: '1.3rem' }} />
                          <div style={{ fontSize: '0.65rem', marginTop: '4px' }}>ID card not uploaded</div>
                        </div>
                      )}
                    </div>
                    <input
                      type="file"
                      id="idCardPhotoInput"
                      accept="image/*"
                      onChange={handleIdCardUpload}
                      style={{ display: 'none' }}
                    />
                    <div>
                      <p className="text-muted mb-2" style={{ fontSize: '0.85rem' }}>
                        Upload a clear photo of your college/student ID card. Wardens and Security use this to verify
                        your identity when reviewing your outpass.
                      </p>
                      <div className="d-flex gap-2">
                        <button
                          type="button"
                          className="btn btn-outline-primary btn-sm"
                          onClick={() => document.getElementById('idCardPhotoInput').click()}
                        >
                          {idCardPreview ? 'Change Photo' : 'Upload Photo'}
                        </button>
                        {newIdCard && (
                          <button
                            type="button"
                            className="btn btn-primary btn-sm"
                            onClick={handleIdCardSubmit}
                            disabled={idCardSubmitting}
                          >
                            {idCardSubmitting ? 'Saving...' : 'Save ID Card'}
                          </button>
                        )}
                      </div>
                    </div>
                  </div>
                </div>
              </div>

              <form onSubmit={handleSubmit}>
                {/* Room Allocation */}
                <h6 className="fw-bold mb-3"><FontAwesomeIcon icon={faBuilding} /> Room Allocation</h6>

                {currentAllocation ? (
                  <div className="alert alert-info mb-3" style={{ fontSize: '0.9rem' }}>
                    <FontAwesomeIcon icon={faLock} /> Your room:{' '}
                    <strong>{buildings.find(b => b.id === currentAllocation.buildingId)?.name}</strong>,
                    Floor {currentAllocation.floor}, Room {currentAllocation.roomNumber}
                    <div className="text-muted mt-1" style={{ fontSize: '0.8rem' }}>
                      Rooms are locked after registration. Contact your warden or admin to change your room.
                    </div>
                  </div>
                ) : (
                  <>
                    <p className="text-muted mb-3" style={{ fontSize: '0.85rem' }}>
                      You don't have a room on file yet — select one below. Once saved, this is locked and can only
                      be changed by a warden or admin.
                    </p>
                    <div className="row mb-3">
                      <div className="col-md-4 mb-3 mb-md-0">
                        <label className="form-label"><FontAwesomeIcon icon={faBuilding} /> Building *</label>
                        <select
                          className="form-select"
                          value={selectedBuilding}
                          onChange={handleBuildingChange}
                          required
                        >
                          <option value="">Select Building</option>
                          {buildings.map(b => (
                            <option key={b.id} value={b.id}>{b.name}</option>
                          ))}
                        </select>
                      </div>
                      <div className="col-md-4 mb-3 mb-md-0">
                        <label className="form-label"><FontAwesomeIcon icon={faLayerGroup} /> Floor *</label>
                        <select
                          className="form-select"
                          value={selectedFloor}
                          onChange={handleFloorChange}
                          disabled={!selectedBuilding}
                          required
                        >
                          <option value="">Select Floor</option>
                          {availableFloors.map(f => (
                            <option key={f.floorNumber} value={f.floorNumber}>Floor {f.floorNumber}</option>
                          ))}
                        </select>
                      </div>
                      <div className="col-md-4">
                        <label className="form-label"><FontAwesomeIcon icon={faDoorOpen} /> Room *</label>
                        <select
                          className="form-select"
                          value={selectedRoom}
                          onChange={(e) => setSelectedRoom(e.target.value)}
                          disabled={!selectedFloor}
                          required
                        >
                          <option value="">Select Room</option>
                          {availableRooms.map(r => {
                            const occupied = getRoomOccupantCount(r.id);
                            const isFull = occupied >= r.maxMembers;
                            return (
                              <option key={r.id} value={r.id} disabled={isFull}>
                                Room {r.roomNumber} ({occupied}/{r.maxMembers}){isFull ? ' - Full' : ''}
                              </option>
                            );
                          })}
                        </select>
                      </div>
                    </div>
                  </>
                )}

                <hr className="my-4" />

                <div className="row">
                  <div className="col-md-6 mb-3">
                    <label className="form-label">Contact Number *</label>
                    <input
                      type="tel"
                      className="form-control"
                      name="contactNumber"
                      value={formData.contactNumber}
                      onChange={handleChange}
                      placeholder="10 digits"
                      maxLength="10"
                      required
                    />
                  </div>

                  <div className="col-md-6 mb-3">
                    <label className="form-label">Parent Contact Number *</label>
                    <input
                      type="tel"
                      className="form-control"
                      name="parentNumber"
                      value={formData.parentNumber}
                      onChange={handleChange}
                      placeholder="10 digits"
                      maxLength="10"
                      required
                    />
                  </div>
                </div>

                <div className="d-flex gap-2">
                  <button
                    type="submit"
                    className="btn btn-primary"
                    disabled={submitting}
                  >
                    {submitting ? (
                      <>
                        <span className="spinner-border spinner-border-sm me-2" role="status"></span>
                        Updating...
                      </>
                    ) : (
                      'Update Profile'
                    )}
                  </button>
                  <button
                    type="button"
                    className="btn btn-secondary"
                    onClick={() => navigate('/student/dashboard')}
                  >
                    Cancel
                  </button>
                </div>
              </form>
            </div>
          </div>
        </div>
      </div>
    </div>
  );
};

export default EditProfile;
