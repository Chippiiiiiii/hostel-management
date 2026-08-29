import { createContext, useContext, useState, useEffect } from 'react';
import authService from '../services/authService';
import { STORAGE_KEYS } from '../utils/constants';

const AuthContext = createContext(null);

export const AuthProvider = ({ children }) => {
  const [user, setUser] = useState(null);
  const [loading, setLoading] = useState(true);

  useEffect(() => {
    // Check if user is logged in on mount
    if (authService.isAuthenticated()) {
      const currentUser = authService.getCurrentUser();
      setUser(currentUser);
    }
    setLoading(false);
  }, []);

  useEffect(() => {
    // The native 'storage' event fires in OTHER same-origin tabs only (never the tab
    // that made the change), so this can't interfere with the initiating tab's own
    // logout/login flow. Refresh-token removal (or a full-storage clear) is the one
    // reliable cross-tab signal for "this session was invalidated" — logout removes it,
    // token refresh never touches it (only accessToken is updated), and login sets it to
    // a real value rather than removing it. Reacting only to that key means access-token
    // refreshes and another tab's login don't get misread as a logout.
    const handleStorage = (event) => {
      const isRefreshTokenKey = event.key === STORAGE_KEYS.REFRESH_TOKEN || event.key === null;
      if (isRefreshTokenKey && !event.newValue) {
        setUser(null);
      }
    };

    window.addEventListener('storage', handleStorage);
    return () => window.removeEventListener('storage', handleStorage);
  }, []);

  const login = async (email, password, role) => {
    const response = await authService.login(email, password, role);
    const currentUser = authService.getCurrentUser();
    setUser(currentUser);
    return response;
  };

  const logout = async () => {
    await authService.logout();
    setUser(null);
  };

  const register = async (userData) => {
    return await authService.register(userData);
  };

  const value = {
    user,
    login,
    logout,
    register,
    isAuthenticated: !!user,
    loading,
  };

  return <AuthContext.Provider value={value}>{children}</AuthContext.Provider>;
};

export const useAuth = () => {
  const context = useContext(AuthContext);
  if (!context) {
    throw new Error('useAuth must be used within an AuthProvider');
  }
  return context;
};

export default AuthContext;
