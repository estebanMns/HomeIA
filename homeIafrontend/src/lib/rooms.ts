import { api } from '@/services/api';
import type { Room } from '@/types';

const normalize = (name: string) => name.trim().toLocaleLowerCase('es-CO');

/** Habitación existente con ese nombre (sin distinguir mayúsculas ni espacios de más). */
export function findRoom(name: string, rooms: Room[]): Room | undefined {
  const wanted = normalize(name);
  return rooms.find((r) => normalize(r.name) === wanted);
}

/**
 * Convierte el nombre escrito en el id de la habitación: usa la existente o la crea.
 * Vacío = sin habitación (undefined). Las habitaciones ya no tienen página propia: nacen al asignar dispositivos.
 */
export async function resolveRoomId(name: string, rooms: Room[]): Promise<string | undefined> {
  if (!name.trim()) return undefined;
  const existing = findRoom(name, rooms);
  if (existing) return existing.id;
  const created = await api.createRoom({ name: name.trim() });
  return created.id;
}
