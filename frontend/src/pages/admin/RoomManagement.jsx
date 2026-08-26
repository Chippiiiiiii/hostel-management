import { Link } from 'react-router-dom';
import RoomManagementPanel from '../../components/room/RoomManagementPanel';
import { FontAwesomeIcon } from '@fortawesome/react-fontawesome';
import { faArrowLeft, faBuilding } from '@fortawesome/free-solid-svg-icons';

const RoomManagement = () => {
  return (
    <div className="container mt-4 mb-5">
      <div className="row mb-4">
        <div className="col-12">
          <div className="d-flex align-items-center mb-3">
            <Link to="/admin/dashboard" className="btn btn-outline-secondary me-3">
              <FontAwesomeIcon icon={faArrowLeft} />
            </Link>
            <h2 className="mb-0" style={{ fontWeight: '700', color: 'var(--color-primary)' }}>
              <FontAwesomeIcon icon={faBuilding} /> Room Management
            </h2>
          </div>
        </div>
      </div>

      <RoomManagementPanel />
    </div>
  );
};

export default RoomManagement;
