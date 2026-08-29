import { Link, useLocation } from 'react-router-dom';
import { useAuth } from '../../context/AuthContext';
import { FontAwesomeIcon } from '@fortawesome/react-fontawesome';
import {
  faChartBar, faDoorOpen, faCalendarCheck, faExclamationTriangle,
  faBullhorn, faUserEdit, faUsers, faClipboardList, faShieldAlt,
  faUserTie, faBuilding, faGraduationCap,
} from '@fortawesome/free-solid-svg-icons';

const navItems = {
  STUDENT: [
    { path: '/student/dashboard', icon: faChartBar, label: 'Dashboard' },
    { path: '/student/outpass', icon: faDoorOpen, label: 'Outpass' },
    { path: '/student/attendance', icon: faCalendarCheck, label: 'Attendance' },
    { path: '/student/complaints', icon: faExclamationTriangle, label: 'Complaints' },
    { path: '/student/announcements', icon: faBullhorn, label: 'Announcements' },
    { path: '/student/edit-profile', icon: faUserEdit, label: 'Edit Profile' },
  ],
  WARDEN: [
    { path: '/warden/dashboard', icon: faChartBar, label: 'Dashboard' },
    { path: '/warden/outpass', icon: faDoorOpen, label: 'Outpass' },
    { path: '/warden/attendance', icon: faCalendarCheck, label: 'Attendance' },
    { path: '/warden/students', icon: faUsers, label: 'Students' },
    { path: '/warden/complaints', icon: faExclamationTriangle, label: 'Complaints' },
    { path: '/warden/announcements', icon: faBullhorn, label: 'Announcements' },
  ],
  SECURITY_GUARD: [
    { path: '/security/dashboard', icon: faChartBar, label: 'Dashboard' },
  ],
  ADMIN: [
    { path: '/admin/dashboard', icon: faChartBar, label: 'Dashboard' },
    { path: '/admin/wardens', icon: faUserTie, label: 'Wardens' },
    { path: '/admin/security-guards', icon: faShieldAlt, label: 'Security Guards' },
    { path: '/admin/rooms', icon: faBuilding, label: 'Room Management' },
    { path: '/admin/year-hostels', icon: faGraduationCap, label: 'Year → Hostel Eligibility' },
  ],
};

const Sidebar = ({ isOpen, onClose }) => {
  const { user } = useAuth();
  const location = useLocation();

  if (!user) return null;

  const items = navItems[user.role] || [];

  const isActive = (path) => {
    if (path === `/${user.role.toLowerCase().replace('_', '-')}/dashboard`) {
      return location.pathname === path;
    }
    return location.pathname.startsWith(path);
  };

  return (
    <>
      {isOpen && <div className="sidebar-overlay" onClick={onClose} />}
      <aside className={`app-sidebar ${isOpen ? 'open' : ''}`}>
        <nav className="sidebar-nav">
          {items.map((item) => (
            <Link
              key={item.path}
              to={item.path}
              className={`sidebar-link ${isActive(item.path) ? 'active' : ''}`}
              onClick={onClose}
            >
              <FontAwesomeIcon icon={item.icon} className="sidebar-icon" />
              <span>{item.label}</span>
            </Link>
          ))}
        </nav>
      </aside>
    </>
  );
};

export default Sidebar;
