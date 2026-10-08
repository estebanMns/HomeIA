'use client';

import React, { createContext, useContext, useState, useEffect, ReactNode } from 'react';
import { User } from '@/types';
import apiService from '@/services/api';

interface AuthContextType {
  user: User | null;
  isLoading: boolean;
  isAuthenticated: boolean;
  login: (email: string, password: string) => Promise<boolean>;
  logout: () => Promise<void>;
  refreshToken: () => Promise<boolean>;
}

const AuthContext = createContext<AuthContextType | undefined>(undefined);

export function AuthProvider({ children }: { children: ReactNode }) {
  const [user, setUser] = useState<User | null>(null);
  const [isLoading, setIsLoading] = useState(true);
  const [isAuthenticated, setIsAuthenticated] = useState(false);

  // Restaurar sesión al cargar
  useEffect(() => {
    const restoreSession = async () => {
      const token = localStorage.getItem('accessToken');
      const userId = localStorage.getItem('userId');
      const email = localStorage.getItem('email');

      if (token && userId && email) {
        setUser({ id: userId, email });
        setIsAuthenticated(true);
        apiService.setAccessToken(token);
      }
      setIsLoading(false);
    };

    restoreSession();
  }, []);

  const login = async (email: string, password: string): Promise<boolean> => {
    try {
      setIsLoading(true);
      const response = await apiService.login(email, password);

      if (response.error || !response.data?.accessToken) {
        console.error('Login error:', response.error);
        return false;
      }

      const data = response.data as any;

      // Guardar tokens
      localStorage.setItem('accessToken', data.accessToken);
      localStorage.setItem('refreshToken', data.refreshToken);
      localStorage.setItem('userId', data.userId);
      localStorage.setItem('email', data.email);
      localStorage.setItem('expiresIn', data.expiresIn);
      localStorage.setItem('loginTime', Date.now().toString());

      apiService.setAccessToken(data.accessToken);
      apiService.setRefreshToken(data.refreshToken);

      setUser({
        id: data.userId,
        email: data.email,
      });
      setIsAuthenticated(true);

      return true;
    } catch (error) {
      console.error('Login error:', error);
      return false;
    } finally {
      setIsLoading(false);
    }
  };

  const logout = async (): Promise<void> => {
    try {
      await apiService.logout();
    } catch (error) {
      console.error('Logout error:', error);
    } finally {
      localStorage.removeItem('accessToken');
      localStorage.removeItem('refreshToken');
      localStorage.removeItem('userId');
      localStorage.removeItem('email');
      localStorage.removeItem('expiresIn');
      localStorage.removeItem('loginTime');

      setUser(null);
      setIsAuthenticated(false);
    }
  };

  const refreshToken = async (): Promise<boolean> => {
    try {
      const refreshTokenValue = localStorage.getItem('refreshToken');
      if (!refreshTokenValue) {
        return false;
      }

      const response = await apiService.refresh(refreshTokenValue);

      if (response.error || !response.data?.accessToken) {
        return false;
      }

      const data = response.data as any;
      localStorage.setItem('accessToken', data.accessToken);
      localStorage.setItem('loginTime', Date.now().toString());
      apiService.setAccessToken(data.accessToken);

      return true;
    } catch (error) {
      console.error('Token refresh error:', error);
      return false;
    }
  };

  // Auto-refresh token antes de expirar
  useEffect(() => {
    if (!isAuthenticated) return;

    const interval = setInterval(async () => {
      const loginTime = parseInt(localStorage.getItem('loginTime') || '0');
      const expiresIn = parseInt(localStorage.getItem('expiresIn') || '3600');
      const now = Date.now();

      // Si token va a expirar en menos de 5 minutos, refrescar
      if (now - loginTime > (expiresIn - 300) * 1000) {
        const success = await refreshToken();
        if (!success) {
          await logout();
        }
      }
    }, 60000); // Verificar cada minuto

    return () => clearInterval(interval);
  }, [isAuthenticated]);

  return (
    <AuthContext.Provider value={{ user, isLoading, isAuthenticated, login, logout, refreshToken }}>
      {children}
    </AuthContext.Provider>
  );
}

export function useAuth() {
  const context = useContext(AuthContext);
  if (context === undefined) {
    throw new Error('useAuth debe ser usado dentro de AuthProvider');
  }
  return context;
}
