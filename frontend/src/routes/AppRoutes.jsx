import { BrowserRouter as Router, Routes, Route, Navigate } from 'react-router-dom';
import { useAuth } from '../context/AuthContext';
import PrivateRoute from './PrivateRoute';
import Navbar from '../components/common/Navbar';
import Layout from '../components/common/Layout';

// Auth Pages
import Login from '../pages/auth/Login';
import Register from '../pages/auth/Register';
import ForgotPassword from '../pages/auth/ForgotPassword';

// Student Pages
import StudentDashboard from '../pages/student/Dashboard';
import CreateOutpass from '../pages/student/CreateOutpass';
import OutpassHistory from '../pages/student/OutpassHistory';
import EditProfile from '../pages/student/EditProfile';
import OutpassDashboard from '../pages/student/OutpassDashboard';
import AttendanceDashboard from '../pages/student/AttendanceDashboard';
import StudentComplaints from '../pages/student/Complaints';
import StudentAnnouncements from '../pages/student/Announcements';

// Warden Pages
import WardenDashboard from '../pages/warden/Dashboard';
import PendingOutpasses from '../pages/warden/PendingOutpasses';
import WardenOutpassDashboard from '../pages/warden/OutpassDashboard';
import WardenAttendanceDashboard from '../pages/warden/AttendanceDashboard';
import StudentsList from '../pages/warden/StudentsList';
import WardenComplaints from '../pages/warden/Complaints';
import WardenAnnouncements from '../pages/warden/Announcements';

// Security Pages
import SecurityDashboard from '../pages/security/Dashboard';

// Other Pages
import Unauthorized from '../pages/Unauthorized';
import NotFound from '../pages/NotFound';
import { ROLES } from '../utils/constants';

const AppRoutes = () => {
  const { user, isAuthenticated } = useAuth();

  const getDefaultRoute = () => {
    if (!isAuthenticated) return '/login';

    switch (user?.role) {
      case ROLES.STUDENT:
        return '/student/dashboard';
      case ROLES.WARDEN:
        return '/warden/dashboard';
      case ROLES.SECURITY_GUARD:
        return '/security/dashboard';
      default:
        return '/login';
    }
  };

  return (
    <Router>
      <Routes>
        {/* Public Routes — navbar only, no sidebar */}
        <Route path="/login" element={<><Navbar /><Login /></>} />
        <Route path="/register" element={<><Navbar /><Register /></>} />
        <Route path="/forgot-password" element={<><Navbar /><ForgotPassword /></>} />
        <Route path="/unauthorized" element={<><Navbar /><Unauthorized /></>} />

        {/* Student Routes — with sidebar layout */}
        <Route
          path="/student/dashboard"
          element={
            <PrivateRoute allowedRoles={[ROLES.STUDENT]}>
              <Layout><StudentDashboard /></Layout>
            </PrivateRoute>
          }
        />
        <Route
          path="/student/outpass"
          element={
            <PrivateRoute allowedRoles={[ROLES.STUDENT]}>
              <Layout><OutpassDashboard /></Layout>
            </PrivateRoute>
          }
        />
        <Route
          path="/student/attendance"
          element={
            <PrivateRoute allowedRoles={[ROLES.STUDENT]}>
              <Layout><AttendanceDashboard /></Layout>
            </PrivateRoute>
          }
        />
        <Route
          path="/student/create-outpass"
          element={
            <PrivateRoute allowedRoles={[ROLES.STUDENT]}>
              <Layout><CreateOutpass /></Layout>
            </PrivateRoute>
          }
        />
        <Route
          path="/student/history"
          element={
            <PrivateRoute allowedRoles={[ROLES.STUDENT]}>
              <Layout><OutpassHistory /></Layout>
            </PrivateRoute>
          }
        />
        <Route
          path="/student/complaints"
          element={
            <PrivateRoute allowedRoles={[ROLES.STUDENT]}>
              <Layout><StudentComplaints /></Layout>
            </PrivateRoute>
          }
        />
        <Route
          path="/student/announcements"
          element={
            <PrivateRoute allowedRoles={[ROLES.STUDENT]}>
              <Layout><StudentAnnouncements /></Layout>
            </PrivateRoute>
          }
        />
        <Route
          path="/student/edit-profile"
          element={
            <PrivateRoute allowedRoles={[ROLES.STUDENT]}>
              <Layout><EditProfile /></Layout>
            </PrivateRoute>
          }
        />

        {/* Warden Routes */}
        <Route
          path="/warden/dashboard"
          element={
            <PrivateRoute allowedRoles={[ROLES.WARDEN]}>
              <Layout><WardenDashboard /></Layout>
            </PrivateRoute>
          }
        />
        <Route
          path="/warden/outpass"
          element={
            <PrivateRoute allowedRoles={[ROLES.WARDEN]}>
              <Layout><WardenOutpassDashboard /></Layout>
            </PrivateRoute>
          }
        />
        <Route
          path="/warden/attendance"
          element={
            <PrivateRoute allowedRoles={[ROLES.WARDEN]}>
              <Layout><WardenAttendanceDashboard /></Layout>
            </PrivateRoute>
          }
        />
        <Route
          path="/warden/students"
          element={
            <PrivateRoute allowedRoles={[ROLES.WARDEN]}>
              <Layout><StudentsList /></Layout>
            </PrivateRoute>
          }
        />
        <Route
          path="/warden/complaints"
          element={
            <PrivateRoute allowedRoles={[ROLES.WARDEN]}>
              <Layout><WardenComplaints /></Layout>
            </PrivateRoute>
          }
        />
        <Route
          path="/warden/announcements"
          element={
            <PrivateRoute allowedRoles={[ROLES.WARDEN]}>
              <Layout><WardenAnnouncements /></Layout>
            </PrivateRoute>
          }
        />
        <Route
          path="/warden/pending"
          element={
            <PrivateRoute allowedRoles={[ROLES.WARDEN]}>
              <Layout><PendingOutpasses /></Layout>
            </PrivateRoute>
          }
        />

        {/* Security Guard Routes */}
        <Route
          path="/security/dashboard"
          element={
            <PrivateRoute allowedRoles={[ROLES.SECURITY_GUARD]}>
              <Layout><SecurityDashboard /></Layout>
            </PrivateRoute>
          }
        />

        {/* Default Route */}
        <Route path="/" element={<Navigate to={getDefaultRoute()} replace />} />

        {/* 404 - Catch all */}
        <Route path="*" element={<><Navbar /><NotFound /></>} />
      </Routes>
    </Router>
  );
};

export default AppRoutes;
