import { createContext, useContext, useState, useEffect } from 'react';
import authService from '../services/authService';

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
