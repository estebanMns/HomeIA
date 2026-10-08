const API_URL = process.env.NEXT_PUBLIC_API_URL || 'http://localhost:8080/api';

export interface ApiResponse<T> {
  data?: T;
  error?: string;
  status: number;
}

class ApiService {
  private accessToken: string | null = null;

  setAccessToken(token: string) {
    this.accessToken = token;
  }

  getAccessToken(): string | null {
    if (typeof window !== 'undefined') {
      return localStorage.getItem('accessToken');
    }
    return this.accessToken;
  }

  setRefreshToken(token: string) {
    if (typeof window !== 'undefined') {
      localStorage.setItem('refreshToken', token);
    }
  }

  private getHeaders(): Headers {
    const headers = new Headers({
      'Content-Type': 'application/json',
    });

    const token = this.getAccessToken();
    if (token) {
      headers.append('Authorization', `Bearer ${token}`);
    }

    return headers;
  }

  async request<T>(
    endpoint: string,
    options: RequestInit = {}
  ): Promise<ApiResponse<T>> {
    try {
      const url = `${API_URL}${endpoint}`;
      const response = await fetch(url, {
        ...options,
        headers: this.getHeaders(),
      });

      const data = await response.json();

      if (!response.ok) {
        return {
          error: data.message || 'Error en la solicitud',
          status: response.status,
        };
      }

      return {
        data,
        status: response.status,
      };
    } catch (error) {
      return {
        error: error instanceof Error ? error.message : 'Error desconocido',
        status: 500,
      };
    }
  }

  // Auth endpoints
  async login(email: string, password: string) {
    return this.request('/auth/login', {
      method: 'POST',
      body: JSON.stringify({
        email,
        password,
        ipAddress: typeof window !== 'undefined' ? window.location.hostname : 'localhost',
      }),
    });
  }

  async refresh(refreshToken: string) {
    return this.request('/auth/refresh', {
      method: 'POST',
      body: JSON.stringify({ refreshToken }),
    });
  }

  async logout() {
    return this.request('/auth/logout', {
      method: 'POST',
    });
  }

  // Device endpoints
  async getDevices() {
    return this.request('/devices', {
      method: 'GET',
    });
  }

  async getDevice(id: string) {
    return this.request(`/devices/${id}`, {
      method: 'GET',
    });
  }

  async toggleDevice(id: string) {
    return this.request(`/devices/${id}/toggle`, {
      method: 'POST',
    });
  }

  async turnOnDevice(id: string) {
    return this.request(`/devices/${id}/turn-on`, {
      method: 'POST',
    });
  }

  async turnOffDevice(id: string) {
    return this.request(`/devices/${id}/turn-off`, {
      method: 'POST',
    });
  }

  async toggleDeviceMqtt(id: string) {
    return this.request(`/devices/mqtt/${id}/toggle`, {
      method: 'POST',
    });
  }

  async turnOnDeviceMqtt(id: string) {
    return this.request(`/devices/mqtt/${id}/on`, {
      method: 'POST',
    });
  }

  async turnOffDeviceMqtt(id: string) {
    return this.request(`/devices/mqtt/${id}/off`, {
      method: 'POST',
    });
  }

  // Alert endpoints
  async getAlerts() {
    return this.request('/alerts', {
      method: 'GET',
    });
  }

  async getAlert(id: string) {
    return this.request(`/alerts/${id}`, {
      method: 'GET',
    });
  }

  async acknowledgeAlert(id: string) {
    return this.request(`/alerts/${id}/acknowledge`, {
      method: 'POST',
    });
  }

  // Automation endpoints
  async getAutomationStatus() {
    return this.request('/automation/status', {
      method: 'GET',
    });
  }

  async enableAutomation() {
    return this.request('/automation/enable', {
      method: 'POST',
    });
  }

  async disableAutomation() {
    return this.request('/automation/disable', {
      method: 'POST',
    });
  }

  // Metrics endpoints
  async getMetrics() {
    return this.request('/metrics/summary', {
      method: 'GET',
    });
  }

  async getHealth() {
    return this.request('/metrics/health', {
      method: 'GET',
    });
  }

  // WebSocket
  async getWebSocketStatus() {
    return this.request('/ws/status', {
      method: 'GET',
    });
  }
}

export default new ApiService();
