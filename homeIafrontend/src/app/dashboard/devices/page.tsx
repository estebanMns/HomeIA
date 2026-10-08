'use client';

import { useEffect, useState } from 'react';
import Navbar from '@/components/layout/Navbar';
import DeviceCard from '@/components/ui/DeviceCard';
import apiService from '@/services/api';
import { Device } from '@/types';

export default function DevicesPage() {
  const [devices, setDevices] = useState<Device[]>([]);
  const [isLoading, setIsLoading] = useState(true);
  const [filterRoom, setFilterRoom] = useState<string>('all');

  const loadDevices = async () => {
    try {
      const res = await apiService.getDevices();
      if (!res.error) {
        setDevices((res.data as Device[]) || []);
      }
    } catch (error) {
      console.error('Error loading devices:', error);
    } finally {
      setIsLoading(false);
    }
  };

  useEffect(() => {
    loadDevices();
    const interval = setInterval(loadDevices, 30000);
    return () => clearInterval(interval);
  }, []);

  const rooms = Array.from(new Set(devices.map((d) => d.roomName)));
  const filteredDevices =
    filterRoom === 'all' ? devices : devices.filter((d) => d.roomName === filterRoom);

  return (
    <div className="min-h-screen bg-gray-100">
      <Navbar />

      <div className="max-w-7xl mx-auto px-4 py-8">
        {/* Header */}
        <div className="mb-8">
          <h1 className="text-3xl font-bold text-gray-900">Dispositivos</h1>
          <p className="text-gray-600">Gestiona todos tus dispositivos inteligentes</p>
        </div>

        {/* Filters */}
        {rooms.length > 0 && (
          <div className="mb-6 flex gap-2 flex-wrap">
            <button
              onClick={() => setFilterRoom('all')}
              className={`px-4 py-2 rounded-lg font-semibold transition ${
                filterRoom === 'all'
                  ? 'bg-blue-600 text-white'
                  : 'bg-white text-gray-700 hover:bg-gray-100'
              }`}
            >
              Todos ({devices.length})
            </button>
            {rooms.map((room) => (
              <button
                key={room}
                onClick={() => setFilterRoom(room)}
                className={`px-4 py-2 rounded-lg font-semibold transition ${
                  filterRoom === room
                    ? 'bg-blue-600 text-white'
                    : 'bg-white text-gray-700 hover:bg-gray-100'
                }`}
              >
                {room} ({devices.filter((d) => d.roomName === room).length})
              </button>
            ))}
          </div>
        )}

        {/* Devices Grid */}
        {isLoading ? (
          <div className="flex justify-center">
            <div className="animate-spin rounded-full h-12 w-12 border-b-2 border-blue-600"></div>
          </div>
        ) : filteredDevices.length === 0 ? (
          <div className="bg-white rounded-lg shadow p-8 text-center text-gray-500">
            No hay dispositivos en esta habitación
          </div>
        ) : (
          <div className="grid grid-cols-1 md:grid-cols-2 lg:grid-cols-3 xl:grid-cols-4 gap-6">
            {filteredDevices.map((device) => (
              <DeviceCard key={device.id} device={device} onStatusChange={loadDevices} />
            ))}
          </div>
        )}
      </div>
    </div>
  );
}
