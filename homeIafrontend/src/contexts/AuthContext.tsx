'use client';

import { createContext, ReactNode, useCallback, useContext, useEffect, useState } from 'react';
import { api, ApiError, SESSION_EXPIRED_EVENT, tokenStore } from '@/services/api';
import type { User } from '@/types';

interface AuthContextType {
  user: User | null;
  isLoading: boolean;
  isAuthenticated: boolean;
  login: (email: string, password: string) => Promise<void>;
  logout: () => Promise<void>;
}

const AuthContext = createContext<AuthContextType | undefined>(undefined);

export function AuthProvider({ children }: { children: ReactNode }) {
  const [user, setUser] = useState<User | null>(null);
  const [isLoading, setIsLoading] = useState(true);

  // Restaurar la sesión guardada y confirmarla con el backend: el rol, el nombre o el estado
  // del usuario pudieron cambiar desde el último ingreso.
  useEffect(() => {
    let cancelled = false;
    (async () => {
      const stored = tokenStore.user();
      let current: User | null = null;
      if (stored && tokenStore.access()) {
        try {
          current = await api.me();
          tokenStore.saveUser(current);
        } catch (e) {
          // Sin conexión se conserva la sesión guardada; cualquier rechazo del backend la cierra.
          current = e instanceof ApiError && e.status === 0 ? stored : null;
          if (!current) tokenStore.clear();
        }
      }
      if (!cancelled) {
        setUser(current);
        setIsLoading(false);
      }
    })();
    return () => {
      cancelled = true;
    };
  }, []);

  const clearSession = useCallback(() => {
    tokenStore.clear();
    setUser(null);
  }, []);

  // Si el refresco falla en cualquier llamada, la sesión termina
  useEffect(() => {
    const onExpired = () => clearSession();
    window.addEventListener(SESSION_EXPIRED_EVENT, onExpired);
    return () => window.removeEventListener(SESSION_EXPIRED_EVENT, onExpired);
  }, [clearSession]);

  const login = async (email: string, password: string) => {
    const session = await api.login(email, password);
    tokenStore.save(session);
    setUser(session.user);
  };

  const logout = async () => {
    const refreshToken = tokenStore.refresh();
    if (refreshToken) {
      await api.logout(refreshToken).catch(() => undefined);
    }
    clearSession();
  };

  return (
    <AuthContext.Provider value={{ user, isLoading, isAuthenticated: user !== null, login, logout }}>
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
