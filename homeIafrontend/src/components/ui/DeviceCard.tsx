'use client';

import { Device } from '@/types';
import { useState } from 'react';
import apiService from '@/services/api';

interface DeviceCardProps {
  device: Device;
  onStatusChange?: () => void;
}

export default function DeviceCard({ device, onStatusChange }: DeviceCardProps) {
  const [isLoading, setIsLoading] = useState(false);

  const handleToggle = async () => {
    setIsLoading(true);
    try {
      await apiService.toggleDevice(device.id);
      onStatusChange?.();
    } catch (error) {
      console.error('Error toggling device:', error);
    } finally {
      setIsLoading(false);
    }
  };

  const getDeviceIcon = (type: string) => {
    switch (type) {
      case 'LIGHT':
        return '💡';
      case 'SWITCH':
        return '🔘';
      case 'THERMOSTAT':
        return '🌡️';
      case 'SENSOR':
        return '📊';
      case 'PLUG':
        return '🔌';
      default:
        return '📱';
    }
  };

  const getStatusColor = (status: string) => {
    switch (status) {
      case 'ON':
      case 'ONLINE':
        return 'bg-green-100 text-green-800';
      case 'OFF':
      case 'OFFLINE':
        return 'bg-red-100 text-red-800';
      default:
        return 'bg-gray-100 text-gray-800';
    }
  };

  return (
    <div className="bg-white rounded-lg shadow p-4 hover:shadow-lg transition-shadow">
      <div className="flex items-start justify-between mb-4">
        <div className="flex items-center gap-3">
          <span className="text-3xl">{getDeviceIcon(device.type)}</span>
          <div>
            <h3 className="font-semibold text-gray-800">{device.name}</h3>
            <p className="text-sm text-gray-500">{device.roomName}</p>
          </div>
        </div>
        <span className={`px-2 py-1 rounded-full text-xs font-semibold ${getStatusColor(device.status)}`}>
          {device.status}
        </span>
      </div>

      {/* Metrics */}
      {(device.temperature || device.humidity || device.power) && (
        <div className="grid grid-cols-3 gap-2 mb-4 text-center text-sm">
          {device.temperature && (
            <div>
              <p className="text-gray-500">Temp.</p>
              <p className="font-semibold text-gray-800">{device.temperature}°C</p>
            </div>
          )}
          {device.humidity && (
            <div>
              <p className="text-gray-500">Humedad</p>
              <p className="font-semibold text-gray-800">{device.humidity}%</p>
            </div>
          )}
          {device.power && (
            <div>
              <p className="text-gray-500">Potencia</p>
              <p className="font-semibold text-gray-800">{device.power}W</p>
            </div>
          )}
        </div>
      )}

      {/* Control Button */}
      <button
        onClick={handleToggle}
        disabled={isLoading}
        className={`w-full py-2 rounded-lg font-semibold transition ${
          device.status === 'ON' || device.status === 'ONLINE'
            ? 'bg-red-100 text-red-600 hover:bg-red-200'
            : 'bg-green-100 text-green-600 hover:bg-green-200'
        } disabled:opacity-50`}
      >
        {isLoading ? 'Cargando...' : device.status === 'ON' ? 'Apagar' : 'Encender'}
      </button>

      {device.lastUpdate && (
        <p className="text-xs text-gray-400 mt-2 text-center">
          Actualizado: {new Date(device.lastUpdate).toLocaleTimeString()}
        </p>
      )}
    </div>
  );
}
