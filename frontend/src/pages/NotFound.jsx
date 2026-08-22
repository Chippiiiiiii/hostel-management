import { Link } from 'react-router-dom';
import { FontAwesomeIcon } from '@fortawesome/react-fontawesome';
import { faHome } from '@fortawesome/free-solid-svg-icons';

const NotFound = () => {
  return (
    <div className="container mt-5">
      <div className="row justify-content-center align-items-center" style={{ minHeight: '60vh' }}>
        <div className="col-md-6 text-center">
          <div className="card shadow-lg card-fade-in">
            <div className="card-body p-5">
              <h1 style={{ fontSize: '5rem', fontWeight: '800', color: 'var(--color-primary)', lineHeight: 1 }}>404</h1>
              <h3 className="mb-3 fw-bold" style={{ color: 'var(--color-text-primary)' }}>Page Not Found</h3>
              <p className="text-muted mb-4">The page you're looking for doesn't exist or has been moved.</p>
              <Link to="/" className="btn btn-primary btn-lg" style={{ minWidth: '200px', fontWeight: '600' }}>
                <FontAwesomeIcon icon={faHome} className="me-2" /> Go to Home
              </Link>
            </div>
          </div>
        </div>
      </div>
    </div>
  );
};

export default NotFound;
