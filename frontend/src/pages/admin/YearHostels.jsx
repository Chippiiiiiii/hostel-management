import { useState, useEffect } from 'react';
import { Link } from 'react-router-dom';
import adminService from '../../services/adminService';
import roomService from '../../services/roomService';
import LoadingSpinner from '../../components/common/LoadingSpinner';
import toast from 'react-hot-toast';
import { FontAwesomeIcon } from '@fortawesome/react-fontawesome';
import { faArrowLeft, faGraduationCap, faPlus, faTrash } from '@fortawesome/free-solid-svg-icons';

const YEARS = [1, 2, 3, 4];
const yearLabel = (year) => `${year}${['th', 'st', 'nd', 'rd'][year] || 'th'} Year`;

const YearHostels = () => {
  const [config, setConfig] = useState({});
  const [buildings, setBuildings] = useState([]);
  const [loading, setLoading] = useState(true);
  const [selectedBuildingByYear, setSelectedBuildingByYear] = useState({});

  useEffect(() => {
    fetchData();
  }, []);

  const fetchData = async () => {
    try {
      const [configRes, buildingsRes] = await Promise.all([
        adminService.getYearHostelConfig(),
        roomService.getBuildings(),
      ]);
      setConfig(configRes.data);
      setBuildings(buildingsRes.data);
    } catch {
      toast.error('Failed to load year/hostel configuration');
    } finally {
      setLoading(false);
    }
  };

  const availableBuildingsForYear = (year) => {
    const allowedIds = (config[year] || []).map((h) => h.buildingId);
    return buildings.filter((b) => !allowedIds.includes(b.id));
  };

  const handleAdd = async (year) => {
    const buildingId = selectedBuildingByYear[year];
    if (!buildingId) {
      toast.error('Select a hostel to add');
      return;
    }
    try {
      await adminService.addYearHostel(year, parseInt(buildingId));
      toast.success('Hostel added');
      setSelectedBuildingByYear({ ...selectedBuildingByYear, [year]: '' });
      fetchData();
    } catch (error) {
      toast.error(error.response?.data?.message || 'Failed to add hostel');
    }
  };

  const handleRemove = async (year, buildingId, buildingName) => {
    if (!window.confirm(`Remove ${buildingName} from Year ${year}?`)) return;
    try {
      await adminService.removeYearHostel(year, buildingId);
      toast.success('Hostel removed');
      fetchData();
    } catch {
      toast.error('Failed to remove hostel');
    }
  };

  if (loading) return <LoadingSpinner />;

  return (
    <div className="container mt-4 mb-5">
      <div className="row mb-4">
        <div className="col-12">
          <div className="d-flex align-items-center mb-3">
            <Link to="/admin/dashboard" className="btn btn-outline-secondary me-3">
              <FontAwesomeIcon icon={faArrowLeft} />
            </Link>
            <h2 className="mb-0" style={{ fontWeight: '700', color: 'var(--color-primary)' }}>
              <FontAwesomeIcon icon={faGraduationCap} /> Year → Hostel Eligibility
            </h2>
          </div>
          <p className="text-muted">
            Configure which hostels a student may select during registration, based on their academic year.
          </p>
        </div>
      </div>

      <div className="row g-4">
        {YEARS.map((year) => (
          <div className="col-md-6" key={year}>
            <div className="card shadow-sm h-100">
              <div className="card-header d-flex justify-content-between align-items-center">
                <h5 className="mb-0">{yearLabel(year)}</h5>
              </div>
              <div className="card-body">
                {(config[year] || []).length === 0 ? (
                  <p className="text-muted mb-3" style={{ fontSize: '0.9rem' }}>
                    No hostels configured for this year yet — students in this year cannot register until at least one is added.
                  </p>
                ) : (
                  <ul className="list-group list-group-flush mb-3">
                    {config[year].map((h) => (
                      <li key={h.buildingId} className="list-group-item d-flex justify-content-between align-items-center px-0">
                        {h.buildingName}
                        <button
                          className="btn btn-outline-danger btn-sm"
                          onClick={() => handleRemove(year, h.buildingId, h.buildingName)}
                          title="Remove"
                        >
                          <FontAwesomeIcon icon={faTrash} />
                        </button>
                      </li>
                    ))}
                  </ul>
                )}

                <div className="input-group">
                  <select
                    className="form-select"
                    value={selectedBuildingByYear[year] || ''}
                    onChange={(e) => setSelectedBuildingByYear({ ...selectedBuildingByYear, [year]: e.target.value })}
                  >
                    <option value="">Select hostel to add...</option>
                    {availableBuildingsForYear(year).map((b) => (
                      <option key={b.id} value={b.id}>{b.name}</option>
                    ))}
                  </select>
                  <button className="btn btn-primary" onClick={() => handleAdd(year)}>
                    <FontAwesomeIcon icon={faPlus} /> Add
                  </button>
                </div>
              </div>
            </div>
          </div>
        ))}
      </div>
    </div>
  );
};

export default YearHostels;
