import { useState, useEffect } from 'react';
import { Link } from 'react-router-dom';
import adminService from '../../services/adminService';
import LoadingSpinner from '../../components/common/LoadingSpinner';
import { FontAwesomeIcon } from '@fortawesome/react-fontawesome';
import { faUserShield, faUserTie, faShieldAlt, faBuilding, faGraduationCap } from '@fortawesome/free-solid-svg-icons';

const AdminDashboard = () => {
  const [wardenCount, setWardenCount] = useState(0);
  const [guardCount, setGuardCount] = useState(0);
  const [loading, setLoading] = useState(true);

  useEffect(() => {
    Promise.all([adminService.getWardens(), adminService.getSecurityGuards()])
      .then(([wardens, guards]) => {
        setWardenCount(wardens.data.length);
        setGuardCount(guards.data.length);
      })
      .catch(() => {})
      .finally(() => setLoading(false));
  }, []);

  if (loading) return <LoadingSpinner />;

  return (
    <div className="container mt-4 mb-5">
      <div className="row mb-4">
        <div className="col-12">
          <h2 className="mb-1" style={{ fontWeight: '700', color: 'var(--color-primary)' }}>
            <FontAwesomeIcon icon={faUserShield} /> Admin Dashboard
          </h2>
          <p className="text-muted">Manage wardens, security guards, and room allocation</p>
        </div>
      </div>

      <div className="row g-4">
        <div className="col-md-3">
          <Link to="/admin/wardens" className="text-decoration-none">
            <div className="card h-100 shadow-sm">
              <div className="card-body p-4 text-center">
                <FontAwesomeIcon icon={faUserTie} style={{ fontSize: '2rem', color: 'var(--color-primary)' }} />
                <h3 className="mt-3 mb-0 fw-bold">{wardenCount}</h3>
                <p className="text-muted mb-0">Wardens</p>
              </div>
            </div>
          </Link>
        </div>
        <div className="col-md-3">
          <Link to="/admin/security-guards" className="text-decoration-none">
            <div className="card h-100 shadow-sm">
              <div className="card-body p-4 text-center">
                <FontAwesomeIcon icon={faShieldAlt} style={{ fontSize: '2rem', color: 'var(--color-info)' }} />
                <h3 className="mt-3 mb-0 fw-bold">{guardCount}</h3>
                <p className="text-muted mb-0">Security Guards</p>
              </div>
            </div>
          </Link>
        </div>
        <div className="col-md-3">
          <Link to="/admin/rooms" className="text-decoration-none">
            <div className="card h-100 shadow-sm">
              <div className="card-body p-4 text-center">
                <FontAwesomeIcon icon={faBuilding} style={{ fontSize: '2rem', color: 'var(--color-success)' }} />
                <h3 className="mt-3 mb-0 fw-bold">Rooms</h3>
                <p className="text-muted mb-0">Floor/room departments, bulk allocation</p>
              </div>
            </div>
          </Link>
        </div>
        <div className="col-md-3">
          <Link to="/admin/year-hostels" className="text-decoration-none">
            <div className="card h-100 shadow-sm">
              <div className="card-body p-4 text-center">
                <FontAwesomeIcon icon={faGraduationCap} style={{ fontSize: '2rem', color: 'var(--color-warning)' }} />
                <h3 className="mt-3 mb-0 fw-bold">Year → Hostel</h3>
                <p className="text-muted mb-0">Registration eligibility by academic year</p>
              </div>
            </div>
          </Link>
        </div>
      </div>
    </div>
  );
};

export default AdminDashboard;
