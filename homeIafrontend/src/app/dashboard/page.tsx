'use client';

import { useEffect, useState } from 'react';
import { useAuth } from '@/contexts/AuthContext';
import Navbar from '@/components/layout/Navbar';
import DeviceCard from '@/components/ui/DeviceCard';
import apiService from '@/services/api';
import { Device, Alert, Metrics } from '@/types';

export default function DashboardPage() {
  const { isAuthenticated, isLoading: authLoading } = useAuth();
  const [devices, setDevices] = useState<Device[]>([]);
  const [alerts, setAlerts] = useState<Alert[]>([]);
  const [metrics, setMetrics] = useState<Metrics | null>(null);
  const [isLoading, setIsLoading] = useState(true);

  const loadData = async () => {
    try {
      // Load devices
      const devicesRes = await apiService.getDevices();
      if (!devicesRes.error) {
        setDevices(devicesRes.data as Device[] || []);
      }

      // Load alerts
      const alertsRes = await apiService.getAlerts();
      if (!alertsRes.error) {
        setAlerts((alertsRes.data as Alert[]) || []);
      }

      // Load metrics
      const metricsRes = await apiService.getMetrics();
      if (!metricsRes.error) {
        setMetrics(metricsRes.data as Metrics);
      }
    } catch (error) {
      console.error('Error loading data:', error);
    } finally {
      setIsLoading(false);
    }
  };

  useEffect(() => {
    if (isAuthenticated && !authLoading) {
      loadData();
      const interval = setInterval(loadData, 30000); // Refresh every 30s
      return () => clearInterval(interval);
    }
  }, [isAuthenticated, authLoading]);

  if (authLoading || isLoading) {
    return (
      <div className="min-h-screen bg-gray-100 flex items-center justify-center">
        <div className="text-center">
          <div className="inline-block animate-spin rounded-full h-12 w-12 border-b-2 border-blue-600"></div>
          <p className="mt-4 text-gray-600">Cargando...</p>
        </div>
      </div>
    );
  }

  return (
    <div className="min-h-screen bg-gray-100">
      <Navbar />

      <div className="max-w-7xl mx-auto px-4 py-8">
        {/* Header */}
        <div className="mb-8">
          <h1 className="text-3xl font-bold text-gray-900">Dashboard</h1>
          <p className="text-gray-600">Bienvenido a tu hogar inteligente</p>
        </div>

        {/* Metrics */}
        {metrics && (
          <div className="grid grid-cols-1 md:grid-cols-3 gap-4 mb-8">
            <div className="bg-white rounded-lg shadow p-6">
              <h3 className="text-gray-600 text-sm font-semibold mb-2">Dispositivos Activos</h3>
              <p className="text-3xl font-bold text-blue-600">{metrics.activeDevices}</p>
            </div>
            <div className="bg-white rounded-lg shadow p-6">
              <h3 className="text-gray-600 text-sm font-semibold mb-2">Conexiones MQTT</h3>
              <p className="text-3xl font-bold text-green-600">{metrics.activeMqttConnections}</p>
            </div>
            <div className="bg-white rounded-lg shadow p-6">
              <h3 className="text-gray-600 text-sm font-semibold mb-2">Conexiones WebSocket</h3>
              <p className="text-3xl font-bold text-purple-600">{metrics.activeWebSocketConnections}</p>
            </div>
          </div>
        )}

        {/* Alerts */}
        {alerts.length > 0 && (
          <div className="mb-8">
            <h2 className="text-xl font-bold text-gray-900 mb-4">Alertas Recientes</h2>
            <div className="space-y-3">
              {alerts.slice(0, 3).map((alert) => (
                <div
                  key={alert.id}
                  className={`p-4 rounded-lg ${
                    alert.severity === 'CRITICAL'
                      ? 'bg-red-100 border-l-4 border-red-600'
                      : alert.severity === 'WARNING'
                      ? 'bg-yellow-100 border-l-4 border-yellow-600'
                      : 'bg-blue-100 border-l-4 border-blue-600'
                  }`}
                >
                  <h3 className="font-semibold text-gray-900">{alert.title}</h3>
                  <p className="text-sm text-gray-700 mt-1">{alert.message}</p>
                  {alert.deviceName && (
                    <p className="text-xs text-gray-600 mt-1">Dispositivo: {alert.deviceName}</p>
                  )}
                </div>
              ))}
            </div>
          </div>
        )}

        {/* Devices */}
        <div>
          <h2 className="text-xl font-bold text-gray-900 mb-4">Dispositivos</h2>
          {devices.length === 0 ? (
            <div className="bg-white rounded-lg shadow p-8 text-center text-gray-500">
              No hay dispositivos disponibles
            </div>
          ) : (
            <div className="grid grid-cols-1 md:grid-cols-2 lg:grid-cols-3 gap-6">
              {devices.map((device) => (
                <DeviceCard key={device.id} device={device} onStatusChange={loadData} />
              ))}
            </div>
          )}
        </div>
      </div>
    </div>
  );
}
