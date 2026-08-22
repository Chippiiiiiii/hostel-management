import { useState, useEffect } from 'react';
import { Link, useNavigate } from 'react-router-dom';
import { useAuth } from '../../context/AuthContext';
import outpassService from '../../services/outpassService';
import { FontAwesomeIcon } from '@fortawesome/react-fontawesome';
import { faGraduationCap, faChartBar, faMoon, faSun, faSignOutAlt } from '@fortawesome/free-solid-svg-icons';

const Navbar = () => {
  const { user, isAuthenticated, logout } = useAuth();
  const navigate = useNavigate();
  const [darkMode, setDarkMode] = useState(() => localStorage.getItem('theme') === 'dark');
  const [profilePic, setProfilePic] = useState(null);
  const [userName, setUserName] = useState('');

  useEffect(() => {
    if (darkMode) {
      document.documentElement.setAttribute('data-theme', 'dark');
      localStorage.setItem('theme', 'dark');
    } else {
      document.documentElement.removeAttribute('data-theme');
      localStorage.setItem('theme', 'light');
    }
  }, [darkMode]);

  useEffect(() => {
    if (isAuthenticated && user?.role === 'STUDENT') {
      outpassService.getStudentProfile()
        .then(res => {
          if (res.data?.profilePicture) setProfilePic(res.data.profilePicture);
          if (res.data?.name) setUserName(res.data.name);
        })
        .catch(() => {});
    }
  }, [isAuthenticated, user?.role]);

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
    <nav className="navbar navbar-expand-lg navbar-dark sticky-top">
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

                <li className="nav-item ms-2">
                  <button
                    className="btn btn-outline-light btn-sm"
                    onClick={() => setDarkMode(!darkMode)}
                    title={darkMode ? 'Light mode' : 'Dark mode'}
                    style={{ fontWeight: '600', minWidth: '36px', height: '36px' }}
                  >
                    <FontAwesomeIcon icon={darkMode ? faSun : faMoon} />
                  </button>
                </li>

                <li className="nav-item ms-lg-2" style={{ padding: '0 0.5rem' }}>
                  <div style={{ width: '1px', height: '24px', background: 'rgba(255,255,255,0.2)' }} className="d-none d-lg-block"></div>
                </li>

                <li className="nav-item">
                  <div className="d-flex align-items-center gap-2" style={{ padding: '0.25rem 0' }}>
                    {profilePic ? (
                      <img
                        src={profilePic}
                        alt="Profile"
                        style={{
                          width: '34px', height: '34px', borderRadius: '50%',
                          objectFit: 'cover', border: '2px solid rgba(255,255,255,0.5)',
                        }}
                      />
                    ) : (
                      <div style={{
                        width: '34px', height: '34px', borderRadius: '50%',
                        background: '#ed8936', display: 'flex', alignItems: 'center',
                        justifyContent: 'center', color: '#fff', fontWeight: '600',
                        fontSize: '0.85rem', border: '2px solid rgba(255,255,255,0.5)',
                      }}>
                        {(userName || user.email).charAt(0).toUpperCase()}
                      </div>
                    )}
                    <div style={{ lineHeight: '1.2' }}>
                      <div style={{ color: '#fff', fontSize: '0.85rem', fontWeight: '600' }}>
                        {userName || user.email.split('@')[0]}
                      </div>
                      <div style={{ color: 'rgba(255,255,255,0.6)', fontSize: '0.7rem' }}>
                        {user.role.replace('_', ' ')}
                      </div>
                    </div>
                  </div>
                </li>

                <li className="nav-item ms-2">
                  <button
                    className="btn btn-outline-light btn-sm"
                    onClick={handleLogout}
                    title="Logout"
                    style={{ fontWeight: '600', minWidth: '36px', height: '36px' }}
                  >
                    <FontAwesomeIcon icon={faSignOutAlt} />
                  </button>
                </li>
              </>
            ) : (
              <>
                <li className="nav-item">
                  <button
                    className="btn btn-outline-light btn-sm me-2"
                    onClick={() => setDarkMode(!darkMode)}
                    title={darkMode ? 'Light mode' : 'Dark mode'}
                    style={{ fontWeight: '600', minWidth: '36px' }}
                  >
                    <FontAwesomeIcon icon={darkMode ? faSun : faMoon} />
                  </button>
                </li>
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
