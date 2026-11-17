'use client';

import type { Room } from '@/types';

/** Campo de texto para la habitación con sugerencias de las que ya existen; si el nombre es nuevo, se crea al guardar. */
export default function RoomField({
  id,
  value,
  rooms,
  onChange,
  className,
  style,
}: {
  id: string;
  value: string;
  rooms: Room[];
  onChange: (value: string) => void;
  className?: string;
  style?: React.CSSProperties;
}) {
  return (
    <>
      <input
        list={`${id}-rooms`}
        placeholder="Habitación (p. ej. Sala)"
        aria-label="Habitación"
        maxLength={80}
        value={value}
        onChange={(e) => onChange(e.target.value)}
        className={className}
        style={style}
      />
      <datalist id={`${id}-rooms`}>
        {rooms.map((r) => <option key={r.id} value={r.name} />)}
      </datalist>
    </>
  );
}
