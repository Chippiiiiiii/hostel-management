import { useState, useEffect } from 'react';
import { useNavigate, Link } from 'react-router-dom';
import { useAuth } from '../../context/AuthContext';
import api from '../../services/api';
import toast from 'react-hot-toast';
import { FontAwesomeIcon } from '@fortawesome/react-fontawesome';
import {
  faGraduationCap, faArrowRight, faCamera,
  faDoorOpen, faCheckCircle, faTimesCircle,
} from '@fortawesome/free-solid-svg-icons';

const Register = () => {
  const [formData, setFormData] = useState({
    name: '',
    email: '',
    password: '',
    confirmPassword: '',
    rollNo: '',
    department: '',
    hostel: '',
    roomNumber: '',
    contactNumber: '',
    parentNumber: '',
    profilePicture: '',
  });
  const [studentType, setStudentType] = useState('');
  const [buildings, setBuildings] = useState([]);
  const [selectedBuilding, setSelectedBuilding] = useState(null);
  const [selectedFloor, setSelectedFloor] = useState('');
  const [selectedRoom, setSelectedRoom] = useState('');
  const [loading, setLoading] = useState(false);
  const [photoPreview, setPhotoPreview] = useState(null);
  const { register } = useAuth();
  const navigate = useNavigate();

  useEffect(() => {
    fetchBuildings();
  }, []);

  const fetchBuildings = async () => {
    try {
      const response = await api.get('/auth/buildings');
      setBuildings(response.data.data || []);
    } catch {
      setBuildings([]);
    }
  };

  const filteredBuildings = buildings.filter(b =>
    studentType === 'NRI' ? b.type === 'NRI' : b.type === 'NORMAL'
  );

  const currentBuilding = buildings.find(b => b.id === selectedBuilding);
  const currentFloor = currentBuilding?.floors?.find(f => f.floorNumber === parseInt(selectedFloor));

  const handleChange = (e) => {
    setFormData({ ...formData, [e.target.name]: e.target.value });
  };

  const handleStudentTypeChange = (type) => {
    setStudentType(type);
    setSelectedBuilding(null);
    setSelectedFloor('');
    setSelectedRoom('');
    setFormData({ ...formData, hostel: '', roomNumber: '' });
  };

  const handleBuildingSelect = (buildingId) => {
    const building = buildings.find(b => b.id === buildingId);
    setSelectedBuilding(buildingId);
    setSelectedFloor('');
    setSelectedRoom('');
    setFormData({ ...formData, hostel: building?.name || '', roomNumber: '' });
  };

  const handleFloorSelect = (floor) => {
    setSelectedFloor(floor);
    setSelectedRoom('');
    setFormData({ ...formData, roomNumber: '' });
  };

  const handleRoomSelect = (room) => {
    setSelectedRoom(room.id);
    setFormData({ ...formData, roomNumber: room.roomNumber });
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
      setFormData({ ...formData, profilePicture: reader.result });
    };
    reader.readAsDataURL(file);
  };

  const handleSubmit = async (e) => {
    e.preventDefault();

    if (!studentType) {
      toast.error('Please select student type');
      return;
    }
    if (!formData.profilePicture) {
      toast.error('Please upload your photo');
      return;
    }
    if (formData.password !== formData.confirmPassword) {
      toast.error('Passwords do not match!');
      return;
    }
    if (formData.contactNumber.length !== 10 || formData.parentNumber.length !== 10) {
      toast.error('Contact numbers must be 10 digits!');
      return;
    }
    if (!formData.roomNumber) {
      toast.error('Please select a room');
      return;
    }

    setLoading(true);
    try {
      const { confirmPassword, ...registerData } = formData;
      await register(registerData);
      toast.success('Registration successful! Please login.');
      navigate('/login');
    } catch (error) {
      toast.error(error.response?.data?.message || 'Registration failed. Please try again.');
    } finally {
      setLoading(false);
    }
  };

  return (
    <div className="container mt-5 mb-5">
      <div className="row justify-content-center">
        <div className="col-md-10 col-lg-8">
          <div className="card shadow-lg card-fade-in">
            <div className="card-body p-5">
              <div className="text-center mb-4">
                <FontAwesomeIcon icon={faGraduationCap} style={{ fontSize: '3rem', color: 'var(--color-primary)' }} />
                <h2 className="mb-2" style={{ fontWeight: '700', color: 'var(--color-primary)' }}>
                  Student Registration
                </h2>
                <p className="text-muted">Create your account to get started</p>
              </div>

              <form onSubmit={handleSubmit}>
                {/* Photo Upload */}
                <div className="text-center mb-4">
                  <div
                    style={{
                      width: '120px', height: '120px', borderRadius: '50%', margin: '0 auto',
                      border: '3px dashed #cbd5e0', overflow: 'hidden', cursor: 'pointer',
                      display: 'flex', alignItems: 'center', justifyContent: 'center',
                      backgroundColor: '#f7fafc', position: 'relative',
                    }}
                    onClick={() => document.getElementById('photoInput').click()}
                  >
                    {photoPreview ? (
                      <img src={photoPreview} alt="Preview" style={{ width: '100%', height: '100%', objectFit: 'cover' }} />
                    ) : (
                      <div className="text-center text-muted">
                        <FontAwesomeIcon icon={faCamera} style={{ fontSize: '1.5rem' }} />
                        <div style={{ fontSize: '0.7rem', marginTop: '4px' }}>Upload Photo</div>
                      </div>
                    )}
                  </div>
                  <input
                    type="file"
                    id="photoInput"
                    accept="image/*"
                    onChange={handlePhotoUpload}
                    style={{ display: 'none' }}
                  />
                  <small className="text-muted d-block mt-1">Click to upload (max 2MB)</small>
                </div>

                {/* Student Type Selection */}
                <div className="mb-4">
                  <label className="form-label">Student Type *</label>
                  <select
                    className="form-select"
                    value={studentType}
                    onChange={(e) => handleStudentTypeChange(e.target.value)}
                    required
                  >
                    <option value="">Select student type</option>
                    <option value="NRI">NRI Student</option>
                    <option value="NORMAL">Regular Student</option>
                  </select>
                </div>

                {/* Personal Details */}
                <div className="row">
                  <div className="col-md-6 mb-4">
                    <label className="form-label">Full Name *</label>
                    <input type="text" className="form-control" name="name" value={formData.name} onChange={handleChange} required />
                  </div>
                  <div className="col-md-6 mb-4">
                    <label className="form-label">Email *</label>
                    <input type="email" className="form-control" name="email" value={formData.email} onChange={handleChange} required />
                  </div>
                </div>

                <div className="row">
                  <div className="col-md-6 mb-4">
                    <label className="form-label">Password *</label>
                    <input type="password" className="form-control" name="password" value={formData.password} onChange={handleChange} required />
                  </div>
                  <div className="col-md-6 mb-4">
                    <label className="form-label">Confirm Password *</label>
                    <input type="password" className="form-control" name="confirmPassword" value={formData.confirmPassword} onChange={handleChange} required />
                  </div>
                </div>

                <div className="row">
                  <div className="col-md-6 mb-3">
                    <label className="form-label">Roll Number *</label>
                    <input type="text" className="form-control" name="rollNo" value={formData.rollNo} onChange={handleChange} required />
                  </div>
                  <div className="col-md-6 mb-3">
                    <label className="form-label">Department *</label>
                    <input type="text" className="form-control" name="department" value={formData.department} onChange={handleChange} placeholder="e.g., Computer Science" required />
                  </div>
                </div>

                {/* Building & Room Selection */}
                {studentType && (
                  <>
                    <hr className="my-4" />
                    <h5 className="fw-bold mb-3"><FontAwesomeIcon icon={faDoorOpen} /> Select Your Room</h5>

                    {/* Building Selection */}
                    <div className="mb-3">
                      <label className="form-label fw-semibold">Hostel Building *</label>
                      <div className="row g-2">
                        {filteredBuildings.length === 0 ? (
                          <div className="col-12">
                            <p className="text-muted">No buildings available for {studentType === 'NRI' ? 'NRI' : 'Regular'} students</p>
                          </div>
                        ) : (
                          filteredBuildings.map(b => (
                            <div className="col-md-4 col-6" key={b.id}>
                              <div
                                className={`card text-center p-2 ${selectedBuilding === b.id ? 'border-primary bg-primary bg-opacity-10' : ''}`}
                                style={{ cursor: 'pointer' }}
                                onClick={() => handleBuildingSelect(b.id)}
                              >
                                <div className="fw-semibold">{b.name}</div>
                                <small className="text-muted">{b.floors?.length || 0} floors</small>
                              </div>
                            </div>
                          ))
                        )}
                      </div>
                    </div>

                    {/* Floor Selection */}
                    {currentBuilding && (
                      <div className="mb-3">
                        <label className="form-label fw-semibold">Floor *</label>
                        <div className="d-flex flex-wrap gap-2">
                          {currentBuilding.floors.map(f => (
                            <button
                              type="button"
                              key={f.floorNumber}
                              className={`btn ${selectedFloor === String(f.floorNumber) ? 'btn-primary' : 'btn-outline-primary'} btn-sm`}
                              onClick={() => handleFloorSelect(String(f.floorNumber))}
                            >
                              Floor {f.floorNumber}
                            </button>
                          ))}
                        </div>
                      </div>
                    )}

                    {/* Room Selection with Availability */}
                    {currentFloor && (
                      <div className="mb-3">
                        <label className="form-label fw-semibold">Room *</label>
                        <div className="row g-2">
                          {currentFloor.rooms.map(room => {
                            const isFull = room.available <= 0;
                            const isSelected = selectedRoom === room.id;
                            return (
                              <div className="col-4 col-md-3 col-lg-2" key={room.id}>
                                <div
                                  className={`card text-center p-2 ${isSelected ? 'border-primary bg-primary bg-opacity-10' : isFull ? 'border-danger bg-danger bg-opacity-10' : ''}`}
                                  style={{ cursor: isFull ? 'not-allowed' : 'pointer', opacity: isFull ? 0.6 : 1 }}
                                  onClick={() => !isFull && handleRoomSelect(room)}
                                >
                                  <div className="fw-semibold" style={{ fontSize: '0.9rem' }}>{room.roomNumber}</div>
                                  <div style={{ fontSize: '0.7rem' }}>
                                    {isFull ? (
                                      <span className="text-danger"><FontAwesomeIcon icon={faTimesCircle} /> Full</span>
                                    ) : (
                                      <span className="text-success">{room.available}/{room.maxMembers} free</span>
                                    )}
                                  </div>
                                </div>
                              </div>
                            );
                          })}
                        </div>
                      </div>
                    )}

                    {formData.roomNumber && (
                      <div className="alert alert-info py-2 mb-3">
                        <FontAwesomeIcon icon={faCheckCircle} /> Selected: <strong>{formData.hostel}</strong> — Room <strong>{formData.roomNumber}</strong>
                      </div>
                    )}
                  </>
                )}

                {/* Contact Numbers */}
                <div className="row mt-3">
                  <div className="col-md-6 mb-3">
                    <label className="form-label">Contact Number *</label>
                    <input type="tel" className="form-control" name="contactNumber" value={formData.contactNumber} onChange={handleChange} placeholder="10 digits" maxLength="10" required />
                  </div>
                  <div className="col-md-6 mb-3">
                    <label className="form-label">Parent Number *</label>
                    <input type="tel" className="form-control" name="parentNumber" value={formData.parentNumber} onChange={handleChange} placeholder="10 digits" maxLength="10" required />
                  </div>
                </div>

                <button
                  type="submit"
                  className="btn btn-primary w-100 mb-3"
                  disabled={loading}
                  style={{ minHeight: '50px', fontSize: '1rem', fontWeight: '600' }}
                >
                  {loading ? (
                    <>
                      <span className="spinner-border spinner-border-sm me-2" role="status"></span>
                      Registering...
                    </>
                  ) : (
                    <><FontAwesomeIcon icon={faArrowRight} /> Create Account</>
                  )}
                </button>

                <div className="text-center pt-3" style={{ borderTop: '1px solid #e2e8f0' }}>
                  <p className="mb-0 text-muted">
                    Already have an account?{' '}
                    <Link to="/login" className="text-decoration-none fw-bold" style={{ color: 'var(--color-primary)' }}>
                      Login here
                    </Link>
                  </p>
                </div>
              </form>
            </div>
          </div>
        </div>
      </div>
    </div>
  );
};

export default Register;
