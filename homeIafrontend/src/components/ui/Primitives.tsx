'use client';

import { CSSProperties, ReactNode } from 'react';
import { Sparkles } from 'lucide-react';

export type BadgeTone = 'green' | 'gray' | 'red' | 'amber' | 'blue' | 'purple';

export function Button({
  children,
  variant = 'primary',
  size = 'md',
  onClick,
  disabled,
  className = '',
}: {
  children: ReactNode;
  variant?: 'primary' | 'outline' | 'ghost' | 'danger';
  size?: 'sm' | 'md' | 'icon';
  onClick?: () => void;
  disabled?: boolean;
  className?: string;
}) {
  return (
    <button
      disabled={disabled}
      onClick={onClick}
      className={`btn btn-${variant} btn-${size} ${className}`}
    >
      {children}
    </button>
  );
}

export function Badge({ children, tone = 'gray' }: { children: ReactNode; tone?: BadgeTone }) {
  return <span className={`badge badge-${tone}`}>{children}</span>;
}

export function Switch({
  checked,
  onChange,
  disabled = false,
}: {
  checked: boolean;
  onChange?: () => void;
  disabled?: boolean;
}) {
  return (
    <button
      aria-label={checked ? 'Desactivar' : 'Activar'}
      disabled={disabled}
      onClick={onChange}
      className={`switch ${checked ? 'switch-on' : ''}`}
    >
      <span />
    </button>
  );
}

export function AiChip() {
  return (
    <Badge tone="blue">
      <Sparkles size={12} /> IA
    </Badge>
  );
}

export function Card({
  children,
  className = '',
  style,
}: {
  children: ReactNode;
  className?: string;
  style?: CSSProperties;
}) {
  return (
    <section className={`card ${className}`} style={style}>
      {children}
    </section>
  );
}

export function SectionTitle({
  title,
  subtitle,
  action,
}: {
  title: string;
  subtitle?: string;
  action?: ReactNode;
}) {
  return (
    <div className="section-title">
      <div>
        <div className="section-heading">{title}</div>
        {subtitle && <div className="muted">{subtitle}</div>}
      </div>
      {action}
    </div>
  );
}

/** Marca de datos de ejemplo: el diseño de Figma muestra valores que el backend aún no expone. */
export function DemoBadge() {
  return <Badge tone="amber">Demo</Badge>;
}

export function Logo({ compact = false }: { compact?: boolean }) {
  return (
    <div className="logo">
      <div className="logo-mark">
        <svg viewBox="0 0 24 24" aria-hidden="true">
          <path
            d="M19.8 4.3C13.1 4 7.1 5.4 5 10.6c-1 2.5-.1 4.8 1.7 6.2 2.5-4.6 6.2-6.6 10.4-8.2-3.6 2.2-6.4 4.7-8 8.3 3.1.7 6-.7 7.6-3.5 1.4-2.4 1.4-5.6 3.1-9.1Z"
            fill="currentColor"
          />
        </svg>
      </div>
      {!compact && (
        <div>
          Home<span>IA</span>
        </div>
      )}
    </div>
  );
}
