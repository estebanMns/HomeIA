import type {
  BillStatus,
  MeterReading,
  TariffInput,
  TariffPeriod,
  Alert,
  AlertSeverity,
  AuditEntry,
  AutomationAction,
  AutomationSettings,
  AutomationStatus,
  Device,
  DeviceCommand,
  DeviceType,
  EnergyBucket,
  EnergySummary,
  Home,
  Page,
  Room,
  RoomEnergy,
  Session,
  SystemHealth,
  SystemMetrics,
  User,
  UserRole,
  WhatsAppMessage,
  WhatsAppRecipient,
  WhatsAppStatus,
} from '@/types';

const API_URL = process.env.NEXT_PUBLIC_API_URL;
if (!API_URL) {
  throw new Error('Falta NEXT_PUBLIC_API_URL en homeIafrontend/.env.local');
}

export class ApiError extends Error {
  constructor(public status: number, message: string) {
    super(message);
  }
}

export const SESSION_EXPIRED_EVENT = 'homeia:session-expired';

const STORAGE = {
  access: 'homeia.accessToken',
  refresh: 'homeia.refreshToken',
  user: 'homeia.user',
};

type Method = 'GET' | 'POST' | 'PUT' | 'DELETE';

function readStorage(key: string): string | null {
  try {
    return typeof window === 'undefined' ? null : window.localStorage.getItem(key);
  } catch {
    return null;
  }
}

function writeStorage(key: string, value: string | null) {
  try {
    if (value === null) window.localStorage.removeItem(key);
    else window.localStorage.setItem(key, value);
  } catch {
    // Almacenamiento no disponible (navegación privada): la sesión dura solo la pestaña.
  }
}

export const tokenStore = {
  access: () => readStorage(STORAGE.access),
  refresh: () => readStorage(STORAGE.refresh),
  user: (): User | null => {
    const raw = readStorage(STORAGE.user);
    try {
      return raw ? (JSON.parse(raw) as User) : null;
    } catch {
      return null;
    }
  },
  save(session: Session) {
    writeStorage(STORAGE.access, session.accessToken);
    writeStorage(STORAGE.refresh, session.refreshToken);
    writeStorage(STORAGE.user, JSON.stringify(session.user));
    if (typeof document !== 'undefined') {
      document.cookie = `accessToken=${session.accessToken}; path=/; max-age=${session.expiresIn}; SameSite=Lax`;
    }
  },
  clear() {
    writeStorage(STORAGE.access, null);
    writeStorage(STORAGE.refresh, null);
    writeStorage(STORAGE.user, null);
    if (typeof document !== 'undefined') {
      document.cookie = 'accessToken=; path=/; max-age=0; SameSite=Lax';
    }
  },
};

let refreshing: Promise<boolean> | null = null;

async function refreshSession(): Promise<boolean> {
  const refreshToken = tokenStore.refresh();
  if (!refreshToken) return false;
  if (!refreshing) {
    refreshing = fetch(`${API_URL}/auth/refresh`, {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify({ refreshToken }),
    })
      .then(async (res) => {
        if (!res.ok) return false;
        tokenStore.save((await res.json()) as Session);
        return true;
      })
      .catch(() => false)
      .finally(() => {
        refreshing = null;
      });
  }
  return refreshing;
}

async function request<T>(method: Method, path: string, body?: unknown, retry = true): Promise<T> {
  const headers: Record<string, string> = { 'Content-Type': 'application/json' };
  const token = tokenStore.access();
  if (token) headers.Authorization = `Bearer ${token}`;

  const res = await fetch(`${API_URL}${path}`, {
    method,
    headers,
    body: body === undefined ? undefined : JSON.stringify(body),
  });

  if (res.status === 401 && retry && token && (await refreshSession())) {
    return request<T>(method, path, body, false);
  }
  if (res.status === 401 && token) {
    tokenStore.clear();
    if (typeof window !== 'undefined') window.dispatchEvent(new Event(SESSION_EXPIRED_EVENT));
  }

  const text = await res.text();
  const data = text ? JSON.parse(text) : undefined;
  if (!res.ok) {
    const message = (data && (data.message as string)) || `Error ${res.status}`;
    throw new ApiError(res.status, message);
  }
  return data as T;
}

function query(params: Record<string, string | number | boolean | undefined | null>): string {
  const entries = Object.entries(params).filter(([, v]) => v !== undefined && v !== null && v !== '');
  if (entries.length === 0) return '';
  return '?' + new URLSearchParams(entries.map(([k, v]) => [k, String(v)])).toString();
}

export const api = {
  // Alta inicial y autenticación
  setupStatus: () => request<{ needsSetup: boolean }>('GET', '/setup/status'),
  setup: (body: unknown) => request<{ homeId: string; name: string }>('POST', '/setup', body),
  login: (email: string, password: string) => request<Session>('POST', '/auth/login', { email, password }),
  register: (body: { fullName: string; email: string; password: string }) =>
    request<{ message: string }>('POST', '/auth/register', body),
  logout: (refreshToken: string) => request<{ success: boolean }>('POST', '/auth/logout', { refreshToken }),
  forgotPassword: (email: string) => request<{ message: string }>('POST', '/auth/password/forgot', { email }),
  resetPassword: (token: string, newPassword: string) =>
    request<{ success: boolean }>('POST', '/auth/password/reset', { token, newPassword }),

  // Usuarios y casa
  me: () => request<User>('GET', '/users/me'),
  users: () => request<User[]>('GET', '/users'),
  createUser: (body: { email: string; password: string; fullName: string; role: UserRole }) =>
    request<User>('POST', '/users', body),
  updateUser: (id: string, body: Partial<{ fullName: string; role: UserRole; active: boolean; password: string }>) =>
    request<User>('PUT', `/users/${id}`, body),
  deleteUser: (id: string) => request<void>('DELETE', `/users/${id}`),
  home: () => request<Home>('GET', '/home'),
  updateHome: (body: Partial<Pick<Home, 'name' | 'currency' | 'electricityPricePerKwh' | 'timezone'>>) =>
    request<Home>('PUT', '/home', body),

  // Habitaciones y dispositivos
  rooms: () => request<Room[]>('GET', '/rooms'),
  createRoom: (body: { name: string; location?: string }) => request<Room>('POST', '/rooms', body),
  updateRoom: (id: string, body: { name?: string; location?: string }) => request<Room>('PUT', `/rooms/${id}`, body),
  deleteRoom: (id: string) => request<void>('DELETE', `/rooms/${id}`),
  roomPresence: (id: string, present: boolean) => request<void>('POST', `/rooms/${id}/presence`, { present }),
  devices: (filters: { roomId?: string; type?: DeviceType } = {}) =>
    request<Device[]>('GET', `/devices${query(filters)}`),
  createDevice: (body: { name: string; type: DeviceType; roomId?: string; critical: boolean }) =>
    request<Device>('POST', '/devices', body),
  updateDevice: (id: string, body: { name?: string; roomId?: string; clearRoom?: boolean; critical?: boolean }) =>
    request<Device>('PUT', `/devices/${id}`, body),
  deleteDevice: (id: string) => request<void>('DELETE', `/devices/${id}`),
  /** confirmCritical: el backend exige confirmación explícita para apagar un dispositivo crítico. */
  deviceCommand: (id: string, command: DeviceCommand, brightness?: number, confirmCritical = false) =>
    request<Device>('POST', `/devices/${id}/commands`, { command, brightness, confirmCritical }),
  registerBluetoothDevice: (body: {
    bluetoothId: string;
    name: string;
    type: DeviceType;
    roomId?: string;
    critical: boolean;
  }) => request<Device>('POST', '/devices/bluetooth', body),
  reportDeviceState: (id: string, state: { powerState?: 'ON' | 'OFF'; consumptionWatts?: number; brightness?: number; presence?: boolean }) =>
    request<Device>('POST', `/devices/${id}/state`, state),

  changeMyPassword: (currentPassword: string, newPassword: string) =>
    request<{ success: boolean }>('PUT', '/users/me/password', { currentPassword, newPassword }),

  // Facturación (recibos de energía)
  billingBills: () => request<BillStatus[]>('GET', '/billing/bills'),
  billingTariffs: () => request<TariffPeriod[]>('GET', '/billing/tariffs'),
  createTariff: (body: TariffInput) => request<TariffPeriod>('POST', '/billing/tariffs', body),
  deleteTariff: (id: string) => request<void>('DELETE', `/billing/tariffs/${id}`),
  meterReadings: () => request<MeterReading[]>('GET', '/billing/meter-readings'),
  addMeterReading: (body: { readingKwh: number; readAt: string }) =>
    request<MeterReading>('POST', '/billing/meter-readings', body),
  deleteMeterReading: (id: string) => request<void>('DELETE', `/billing/meter-readings/${id}`),

  // Energía
  energySummary: (from?: string, to?: string) => request<EnergySummary>('GET', `/energy/summary${query({ from, to })}`),
  energySeries: (from: string, to: string, bucket: 'hour' | 'day') =>
    request<EnergyBucket[]>('GET', `/energy/series${query({ from, to, bucket })}`),
  energyByRoom: (from?: string, to?: string) => request<RoomEnergy[]>('GET', `/energy/rooms${query({ from, to })}`),

  // Alertas
  alerts: (filters: { severity?: AlertSeverity; acknowledged?: boolean; page?: number; size?: number } = {}) =>
    request<Page<Alert>>('GET', `/alerts${query(filters)}`),
  createAlert: (body: { severity: AlertSeverity; title: string; message: string; roomId?: string; deviceId?: string }) =>
    request<Alert>('POST', '/alerts', body),
  acknowledgeAlert: (id: string) => request<Alert>('POST', `/alerts/${id}/acknowledge`),
  retryAlertNotifications: (id: string) => request<{ retried: number }>('POST', `/alerts/${id}/retry-notifications`),

  // Automatización
  automationSettings: () => request<AutomationSettings>('GET', '/automation/settings'),
  updateAutomationSettings: (body: Omit<AutomationSettings, 'updatedAt'>) =>
    request<AutomationSettings>('PUT', '/automation/settings', body),
  setAutomationEnabled: (enabled: boolean) =>
    request<{ enabled: boolean }>('POST', enabled ? '/automation/enable' : '/automation/disable'),
  automationStatus: () => request<AutomationStatus>('GET', '/automation/status'),
  automationActions: (page = 0, size = 20) =>
    request<Page<AutomationAction>>('GET', `/automation/actions${query({ page, size })}`),
  runAutomation: () => request<{ actions: number }>('POST', '/automation/run'),
  roomReturnProbability: (roomId: string) =>
    request<{ roomId: string; probabilityPercent: number }>('GET', `/automation/rooms/${roomId}/return-probability`),

  // WhatsApp
  whatsappStatus: () => request<WhatsAppStatus>('GET', '/whatsapp/status'),
  whatsappRecipients: () => request<WhatsAppRecipient[]>('GET', '/whatsapp/recipients'),
  addWhatsappRecipient: (body: { name: string; phone: string }) =>
    request<WhatsAppRecipient>('POST', '/whatsapp/recipients', body),
  updateWhatsappRecipient: (id: string, body: { name?: string; phone?: string; active?: boolean }) =>
    request<WhatsAppRecipient>('PUT', `/whatsapp/recipients/${id}`, body),
  deleteWhatsappRecipient: (id: string) => request<void>('DELETE', `/whatsapp/recipients/${id}`),
  whatsappMessages: (page = 0, size = 20) =>
    request<Page<WhatsAppMessage>>('GET', `/whatsapp/messages${query({ page, size })}`),
  retryWhatsappMessage: (id: string) => request<WhatsAppMessage>('POST', `/whatsapp/messages/${id}/retry`),
  sendWhatsappTest: (body?: string) =>
    request<{ sentTo: number }>('POST', '/whatsapp/test', body ? { body } : {}),

  // Historial y sistema
  audit: (filters: { action?: string; entityType?: string; from?: string; to?: string; page?: number; size?: number } = {}) =>
    request<Page<AuditEntry>>('GET', `/audit${query(filters)}`),
  systemHealth: () => request<SystemHealth>('GET', '/system/health'),
  systemMetrics: () => request<SystemMetrics>('GET', '/system/metrics'),
};
