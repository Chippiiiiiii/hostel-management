import { useState, useEffect } from 'react';
import { Link, useNavigate } from 'react-router-dom';
import { useAuth } from '../../context/AuthContext';
import outpassService from '../../services/outpassService';
import { FontAwesomeIcon } from '@fortawesome/react-fontawesome';
import { faGraduationCap, faMoon, faSun, faSignOutAlt, faBars } from '@fortawesome/free-solid-svg-icons';

const Navbar = ({ onToggleSidebar }) => {
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
      case 'ADMIN':
        return '/admin/dashboard';
      default:
        return '/';
    }
  };

  return (
    <nav className="navbar navbar-dark sticky-top">
      <div className="container-fluid px-3">
        <div className="d-flex align-items-center">
          {isAuthenticated && onToggleSidebar && (
            <button
              className="btn btn-link text-white d-lg-none me-2 p-0"
              onClick={onToggleSidebar}
              style={{ fontSize: '1.25rem' }}
            >
              <FontAwesomeIcon icon={faBars} />
            </button>
          )}
          <Link className="navbar-brand fw-bold mb-0" to={getDashboardLink()}>
            <FontAwesomeIcon icon={faGraduationCap} style={{ fontSize: '1.3rem', marginRight: '0.5rem' }} /> Hostel Management
          </Link>
        </div>

        <div className="d-flex align-items-center gap-2">
          {isAuthenticated ? (
            <>
              <button
                className="btn btn-outline-light btn-sm"
                onClick={() => setDarkMode(!darkMode)}
                title={darkMode ? 'Light mode' : 'Dark mode'}
                style={{ fontWeight: '600', minWidth: '36px', height: '36px' }}
              >
                <FontAwesomeIcon icon={darkMode ? faSun : faMoon} />
              </button>

              <div style={{ width: '1px', height: '24px', background: 'rgba(255,255,255,0.2)' }} className="d-none d-sm-block"></div>

              <div className="d-flex align-items-center gap-2">
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
                    {(userName || user?.email || '?').charAt(0).toUpperCase()}
                  </div>
                )}
                <div style={{ lineHeight: '1.2' }} className="d-none d-sm-block">
                  <div style={{ color: '#fff', fontSize: '0.85rem', fontWeight: '600' }}>
                    {userName || user.email.split('@')[0]}
                  </div>
                  <div style={{ color: 'rgba(255,255,255,0.6)', fontSize: '0.7rem' }}>
                    {user.role.replace('_', ' ')}
                  </div>
                </div>
              </div>

              <button
                className="btn btn-outline-light btn-sm"
                onClick={handleLogout}
                title="Logout"
                style={{ fontWeight: '600', minWidth: '36px', height: '36px' }}
              >
                <FontAwesomeIcon icon={faSignOutAlt} />
              </button>
            </>
          ) : (
            <>
              <button
                className="btn btn-outline-light btn-sm"
                onClick={() => setDarkMode(!darkMode)}
                title={darkMode ? 'Light mode' : 'Dark mode'}
                style={{ fontWeight: '600', minWidth: '36px' }}
              >
                <FontAwesomeIcon icon={darkMode ? faSun : faMoon} />
              </button>
              <Link className="nav-link text-white" to="/login">Login</Link>
              <Link
                className="nav-link text-white"
                to="/register"
                style={{
                  background: 'rgba(255, 255, 255, 0.1)',
                  borderRadius: '0.375rem',
                  fontWeight: '600',
                  padding: '0.5rem 1rem',
                }}
              >
                Register
              </Link>
            </>
          )}
        </div>
      </div>
    </nav>
  );
};

export default Navbar;
