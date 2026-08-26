import { Link } from 'react-router-dom';
import RoomManagementPanel from '../../components/room/RoomManagementPanel';
import { FontAwesomeIcon } from '@fortawesome/react-fontawesome';
import { faArrowLeft, faUsers } from '@fortawesome/free-solid-svg-icons';

const StudentsList = () => {
  return (
    <div className="container mt-4 mb-5">
      <div className="row mb-4">
        <div className="col-12">
          <div className="d-flex align-items-center mb-3">
            <Link to="/warden/dashboard" className="btn btn-outline-secondary me-3">
              <FontAwesomeIcon icon={faArrowLeft} />
            </Link>
            <h2 className="mb-0" style={{ fontWeight: '700', color: 'var(--color-primary)' }}>
              <FontAwesomeIcon icon={faUsers} /> Students & Rooms
            </h2>
          </div>
        </div>
      </div>

      <RoomManagementPanel />
    </div>
  );
};

export default StudentsList;
