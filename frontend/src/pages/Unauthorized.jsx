import { FontAwesomeIcon } from '@fortawesome/react-fontawesome';
import { faBan, faArrowLeft } from '@fortawesome/free-solid-svg-icons';

const Unauthorized = () => {
  return (
    <div className="container mt-5">
      <div className="row justify-content-center align-items-center" style={{ minHeight: '60vh' }}>
        <div className="col-md-6 text-center">
          <div className="card shadow-lg card-fade-in">
            <div className="card-body p-5">
              <FontAwesomeIcon icon={faBan} style={{ fontSize: '5rem', color: 'var(--color-danger)' }} />
              <h2 className="mb-3" style={{ fontWeight: '700', color: 'var(--color-danger)' }}>Unauthorized Access</h2>
              <p className="text-muted mb-4" style={{ fontSize: '1.1rem' }}>
                You don't have permission to access this page.
              </p>
              <a 
                href="/" 
                className="btn btn-primary btn-lg"
                style={{ minWidth: '200px', fontWeight: '600' }}
              >
                <FontAwesomeIcon icon={faArrowLeft} /> Go to Home
              </a>
            </div>
          </div>
        </div>
      </div>
    </div>
  );
};

export default Unauthorized;
