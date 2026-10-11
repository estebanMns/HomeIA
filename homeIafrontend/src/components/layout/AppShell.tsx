'use client';

import { ReactNode, useEffect, useState } from 'react';
import Link from 'next/link';
import { usePathname, useRouter } from 'next/navigation';
import {
  Activity,
  Bell,
  Bot,
  BarChart3,
  BluetoothConnected,
  Building2,
  ChevronDown,
  DoorOpen,
  Home,
  HousePlug,
  History,
  Menu,
  MessageCircle,
  Moon,
  MoreHorizontal,
  Sparkles,
  Sun,
  Users,
} from 'lucide-react';
import { useAuth } from '@/contexts/AuthContext';
import { api } from '@/services/api';
import type { Home as HomeData } from '@/types';
import { Button, Logo } from '@/components/ui/Primitives';

const navMain = [
  { href: '/dashboard', label: 'Resumen', icon: Home },
  { href: '/dashboard/rooms', label: 'Habitaciones', icon: DoorOpen },
  { href: '/dashboard/devices', label: 'Dispositivos', icon: HousePlug },
  { href: '/dashboard/bluetooth', label: 'Bluetooth', icon: BluetoothConnected },
  { href: '/dashboard/analytics', label: 'Consumo y ahorro', icon: BarChart3 },
  { href: '/dashboard/alerts', label: 'Centro de alertas', icon: Bell },
];

const navAdmin = [
  { href: '/dashboard/automation', label: 'Automatización e IA', icon: Bot },
  { href: '/dashboard/whatsapp', label: 'WhatsApp', icon: MessageCircle },
  { href: '/dashboard/history', label: 'Historial', icon: History },
  { href: '/dashboard/home', label: 'Usuarios y casa', icon: Users },
  { href: '/dashboard/system', label: 'Estado del sistema', icon: Activity },
];

const titleMap: Record<string, string> = {
  '/dashboard': 'Resumen',
  '/dashboard/rooms': 'Habitaciones',
  '/dashboard/devices': 'Dispositivos',
  '/dashboard/bluetooth': 'Dispositivos Bluetooth',
  '/dashboard/analytics': 'Consumo y ahorro',
  '/dashboard/alerts': 'Centro de alertas',
  '/dashboard/automation': 'Automatización e IA',
  '/dashboard/whatsapp': 'Notificaciones de WhatsApp',
  '/dashboard/history': 'Historial y auditoría',
  '/dashboard/home': 'Usuarios y casa',
  '/dashboard/system': 'Estado del sistema',
};

export default function AppShell({ children }: { children: ReactNode }) {
  const pathname = usePathname();
  const router = useRouter();
  const { user, logout } = useAuth();
  const [dark, setDark] = useState(false);
  const [home, setHome] = useState<HomeData | null>(null);

  useEffect(() => {
    api.home().then(setHome).catch(() => setHome(null));
  }, []);

  const title = titleMap[pathname] ?? 'HomeIA';
  const initials = user?.fullName
    ? user.fullName.split(' ').map((p) => p[0]).slice(0, 2).join('').toUpperCase()
    : '?';

  const isActive = (href: string) => (href === '/dashboard' ? pathname === href : pathname.startsWith(href));

  const handleLogout = async () => {
    await logout();
    router.push('/auth/login');
  };

  return (
    <div className={dark ? 'app dark' : 'app'}>
      <aside className="sidebar">
        <Logo />
        <div className="home-switch">
          <div className="avatar-home">
            <Building2 size={17} />
          </div>
          <div>
            <strong>{home?.name ?? 'Mi casa'}</strong>
            <span>{user?.role === 'ADMIN' ? 'Administrador' : 'Miembro'}</span>
          </div>
          <ChevronDown size={16} />
        </div>
        <nav>
          <div className="nav-label">PRINCIPAL</div>
          {navMain.map((item) => (
            <Link key={item.href} href={item.href} className={`nav-item ${isActive(item.href) ? 'active' : ''}`}>
              <item.icon size={19} />
              <span>{item.label}</span>
            </Link>
          ))}
          <div className="nav-label">ADMINISTRACIÓN</div>
          {navAdmin.map((item) => (
            <Link key={item.href} href={item.href} className={`nav-item ${isActive(item.href) ? 'active' : ''}`}>
              <item.icon size={19} />
              <span>{item.label}</span>
            </Link>
          ))}
        </nav>
        <div className="sidebar-bottom">
          <div className="support-card">
            <div className="support-icon">
              <Sparkles size={16} />
            </div>
            <strong>Automatización</strong>
            <span>{home ? `Zona horaria ${home.timezone}` : 'Cargando casa…'}</span>
          </div>
          <button className="profile" onClick={handleLogout} title="Cerrar sesión">
            <div className="avatar">{initials}</div>
            <div>
              <strong>{user?.fullName ?? ''}</strong>
              <span>{user?.email ?? ''}</span>
            </div>
            <MoreHorizontal size={18} />
          </button>
        </div>
      </aside>

      <main>
        <header className="topbar">
          <div>
            <div className="page-title">{title}</div>
            <div className="top-subtitle">{home?.name ?? ''}</div>
          </div>
          <div className="top-actions">
            <div className="live-pill">
              <span /> Datos en vivo
            </div>
            <Button variant="ghost" size="icon" onClick={() => setDark(!dark)}>
              {dark ? <Sun size={19} /> : <Moon size={19} />}
            </Button>
            <div className="avatar top-avatar">{initials}</div>
          </div>
        </header>

        {children}
      </main>

      <nav className="mobile-nav">
        {[navMain[0], navMain[1], navMain[2], navMain[4]].map((item) => (
          <Link key={item.href} href={item.href} className={isActive(item.href) ? 'active' : ''}>
            <item.icon size={21} />
            <span>{item.label.replace('Centro de ', '')}</span>
          </Link>
        ))}
        <Link href="/dashboard/automation" className={isActive('/dashboard/automation') ? 'active' : ''}>
          <Menu size={21} />
          <span>Más</span>
        </Link>
      </nav>
    </div>
  );
}
