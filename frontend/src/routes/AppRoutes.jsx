import { BrowserRouter as Router, Routes, Route, Navigate } from 'react-router-dom';
import { useAuth } from '../context/AuthContext';
import PrivateRoute from './PrivateRoute';
import Navbar from '../components/common/Navbar';

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
      <Navbar />
      <Routes>
        {/* Public Routes */}
        <Route path="/login" element={<Login />} />
        <Route path="/register" element={<Register />} />
        <Route path="/forgot-password" element={<ForgotPassword />} />
        <Route path="/unauthorized" element={<Unauthorized />} />

        {/* Student Routes */}
        <Route
          path="/student/dashboard"
          element={
            <PrivateRoute allowedRoles={[ROLES.STUDENT]}>
              <StudentDashboard />
            </PrivateRoute>
          }
        />
        <Route
          path="/student/outpass"
          element={
            <PrivateRoute allowedRoles={[ROLES.STUDENT]}>
              <OutpassDashboard />
            </PrivateRoute>
          }
        />
        <Route
          path="/student/attendance"
          element={
            <PrivateRoute allowedRoles={[ROLES.STUDENT]}>
              <AttendanceDashboard />
            </PrivateRoute>
          }
        />
        <Route
          path="/student/create-outpass"
          element={
            <PrivateRoute allowedRoles={[ROLES.STUDENT]}>
              <CreateOutpass />
            </PrivateRoute>
          }
        />
        <Route
          path="/student/history"
          element={
            <PrivateRoute allowedRoles={[ROLES.STUDENT]}>
              <OutpassHistory />
            </PrivateRoute>
          }
        />
        <Route
          path="/student/complaints"
          element={
            <PrivateRoute allowedRoles={[ROLES.STUDENT]}>
              <StudentComplaints />
            </PrivateRoute>
          }
        />
        <Route
          path="/student/announcements"
          element={
            <PrivateRoute allowedRoles={[ROLES.STUDENT]}>
              <StudentAnnouncements />
            </PrivateRoute>
          }
        />
        <Route
          path="/student/edit-profile"
          element={
            <PrivateRoute allowedRoles={[ROLES.STUDENT]}>
              <EditProfile />
            </PrivateRoute>
          }
        />

        {/* Warden Routes */}
        <Route
          path="/warden/dashboard"
          element={
            <PrivateRoute allowedRoles={[ROLES.WARDEN]}>
              <WardenDashboard />
            </PrivateRoute>
          }
        />
        <Route
          path="/warden/outpass"
          element={
            <PrivateRoute allowedRoles={[ROLES.WARDEN]}>
              <WardenOutpassDashboard />
            </PrivateRoute>
          }
        />
        <Route
          path="/warden/attendance"
          element={
            <PrivateRoute allowedRoles={[ROLES.WARDEN]}>
              <WardenAttendanceDashboard />
            </PrivateRoute>
          }
        />
        <Route
          path="/warden/students"
          element={
            <PrivateRoute allowedRoles={[ROLES.WARDEN]}>
              <StudentsList />
            </PrivateRoute>
          }
        />
        <Route
          path="/warden/complaints"
          element={
            <PrivateRoute allowedRoles={[ROLES.WARDEN]}>
              <WardenComplaints />
            </PrivateRoute>
          }
        />
        <Route
          path="/warden/announcements"
          element={
            <PrivateRoute allowedRoles={[ROLES.WARDEN]}>
              <WardenAnnouncements />
            </PrivateRoute>
          }
        />
        <Route
          path="/warden/pending"
          element={
            <PrivateRoute allowedRoles={[ROLES.WARDEN]}>
              <PendingOutpasses />
            </PrivateRoute>
          }
        />

        {/* Security Guard Routes */}
        <Route
          path="/security/dashboard"
          element={
            <PrivateRoute allowedRoles={[ROLES.SECURITY_GUARD]}>
              <SecurityDashboard />
            </PrivateRoute>
          }
        />

        {/* Default Route */}
        <Route path="/" element={<Navigate to={getDefaultRoute()} replace />} />
        
        {/* 404 - Catch all */}
        <Route path="*" element={<Navigate to="/" replace />} />
      </Routes>
    </Router>
  );
};

export default AppRoutes;
