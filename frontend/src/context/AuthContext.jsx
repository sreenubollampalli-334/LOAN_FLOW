import React, { createContext, useState, useContext, useEffect, useCallback, useMemo } from 'react';
import { authApi } from '../api';

const AuthContext = createContext(null);

export const AuthProvider = ({ children }) => {
  const [token, setToken] = useState(() => localStorage.getItem('token'));
  const [user, setUser] = useState(() => {
    try {
      const storedUser = localStorage.getItem('user');
      if (storedUser) {
        const parsedUser = JSON.parse(storedUser);
        // Validate usable structure
        if (parsedUser && Array.isArray(parsedUser.roles)) {
          return parsedUser;
        }
      }
      return null;
    } catch (e) {
      console.error('Failed to parse stored user', e);
      return null;
    }
  });
  const [loading, setLoading] = useState(false);

  const logout = useCallback(() => {
    setToken(null);
    setUser(null);
    localStorage.removeItem('token');
    localStorage.removeItem('user');
  }, []);

  useEffect(() => {
    const handleAuthError = () => logout();
    window.addEventListener('auth-error', handleAuthError);
    return () => window.removeEventListener('auth-error', handleAuthError);
  }, [logout]);

  const login = async (email, password) => {
    try {
      setLoading(true);
      const response = await authApi.login({ email, password });
      const { token: newToken, user: newUser } = response.data;
      
      setToken(newToken);
      setUser(newUser);
      
      localStorage.setItem('token', newToken);
      localStorage.setItem('user', JSON.stringify(newUser));
      
      return { success: true, user: newUser };
    } catch (error) {
      console.error('Login failed', error);
      return { 
        success: false, 
        error: error.response?.data?.message || 'Login failed. Please check your credentials.' 
      };
    } finally {
      setLoading(false);
    }
  };

  // Role normalization: removes 'ROLE_' prefix if present and ensures uppercase
  const normalizeRole = (role) => {
    if (!role) return '';
    return role.replace(/^ROLE_/i, '').toUpperCase();
  };

  const normalizedUserRoles = useMemo(() => {
    return user?.roles?.map(normalizeRole) || [];
  }, [user?.roles]);

  const hasRole = useCallback((role) => {
    if (!user) return false;
    return normalizedUserRoles.includes(normalizeRole(role));
  }, [user, normalizedUserRoles]);

  const hasAnyRole = useCallback((roles) => {
    if (!user || !roles || !Array.isArray(roles)) return false;
    const targetRoles = roles.map(normalizeRole);
    return normalizedUserRoles.some(r => targetRoles.includes(r));
  }, [user, normalizedUserRoles]);

  const value = {
    user,
    token,
    isAuthenticated: !!token,
    loading,
    login,
    logout,
    hasRole,
    hasAnyRole
  };

  return <AuthContext.Provider value={value}>{children}</AuthContext.Provider>;
};

// eslint-disable-next-line react-refresh/only-export-components
export const useAuth = () => {
  const context = useContext(AuthContext);
  if (!context) {
    throw new Error('useAuth must be used within an AuthProvider');
  }
  return context;
};
