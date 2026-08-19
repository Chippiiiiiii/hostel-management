import { Link, useNavigate } from 'react-router-dom';
import { useAuth } from '../../context/AuthContext';
import { FontAwesomeIcon } from '@fortawesome/react-fontawesome';
import { faGraduationCap, faChartBar } from '@fortawesome/free-solid-svg-icons';

const Navbar = () => {
  const { user, isAuthenticated, logout } = useAuth();
  const navigate = useNavigate();

  const handleLogout = async () => {
    await logout();
    navigate('/login');
  };

  const getDashboardLink = () => {
    if (!user) return '/';
    switch (user.role) {
      case 'STUDENT':
        return '/student/dashboard';
      case 'WARDEN':
        return '/warden/dashboard';
      case 'SECURITY_GUARD':
        return '/security/dashboard';
      default:
        return '/';
    }
  };

  return (
    <nav className="navbar navbar-expand-lg navbar-dark sticky-top" style={{ 
      background: 'linear-gradient(135deg, #1a202c 0%, #2d3748 100%)',
      boxShadow: '0 4px 6px -1px rgba(0, 0, 0, 0.1)'
    }}>
      <div className="container-fluid px-4">
        <Link className="navbar-brand fw-bold" to={getDashboardLink()}>
          <FontAwesomeIcon icon={faGraduationCap} style={{ fontSize: '1.3rem', marginRight: '0.5rem' }} /> Hostel Management
        </Link>
        
        <button
          className="navbar-toggler border-0"
          type="button"
          data-bs-toggle="collapse"
          data-bs-target="#navbarNav"
          style={{ padding: '0.5rem' }}
        >
          <span className="navbar-toggler-icon"></span>
        </button>

        <div className="collapse navbar-collapse" id="navbarNav">
          <ul className="navbar-nav ms-auto align-items-lg-center">
            {isAuthenticated ? (
              <>
                <li className="nav-item">
                  <Link className="nav-link" to={getDashboardLink()}>
                    <FontAwesomeIcon icon={faChartBar} style={{ marginRight: '0.5rem' }} /> Dashboard
                  </Link>
                </li>
                
<li className="nav-item">
                  <span className="nav-link" style={{ 
                    background: 'rgba(255, 255, 255, 0.1)', 
                    borderRadius: '0.375rem',
                    padding: '0.5rem 1rem'
                  }}>
                    <strong style={{ color: 'white' }}>{user.email}</strong>
                    <span style={{ 
                      marginLeft: '0.5rem', 
                      padding: '0.2rem 0.5rem', 
                      background: 'rgba(255, 255, 255, 0.2)',
                      borderRadius: '0.25rem',
                      fontSize: '0.8rem',
                      fontWeight: '600'
                    }}>
                      {user.role.replace('_', ' ')}
                    </span>
                  </span>
                </li>
                
                <li className="nav-item ms-2">
                  <button 
                    className="btn btn-outline-light btn-sm" 
                    onClick={handleLogout}
                    style={{ fontWeight: '600' }}
                  >
                    Logout
                  </button>
                </li>
              </>
            ) : (
              <>
                <li className="nav-item">
                  <Link className="nav-link" to="/login">
                    Login
                  </Link>
                </li>
                <li className="nav-item">
                  <Link 
                    className="nav-link" 
                    to="/register"
                    style={{
                      background: 'rgba(255, 255, 255, 0.1)',
                      borderRadius: '0.375rem',
                      fontWeight: '600'
                    }}
                  >
                    Register
                  </Link>
                </li>
              </>
            )}
          </ul>
        </div>
      </div>
    </nav>
  );
};

export default Navbar;
