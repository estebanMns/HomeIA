'use client';

import { useId, useMemo, useState } from 'react';
import { Check, Eye, EyeOff, X } from 'lucide-react';
import { PASSWORD_MAX_LENGTH, PASSWORD_RULES, passwordStrength, passwordViolations } from '@/lib/passwordPolicy';

interface Props {
  password: string;
  confirm: string;
  onPasswordChange: (value: string) => void;
  onConfirmChange: (value: string) => void;
  /** Nombre y correo de la persona: la contraseña no puede contenerlos. */
  personalData?: (string | undefined)[];
  label?: string;
  confirmLabel?: string;
}

const STRENGTH_LABEL = ['', 'Débil', 'Aceptable', 'Segura', 'Muy segura'] as const;

/** True si la contraseña cumple todas las reglas y coincide con la confirmación. */
export function isNewPasswordValid(password: string, confirm: string, personalData: (string | undefined)[] = []): boolean {
  return passwordViolations(password, personalData).size === 0 && password === confirm;
}

/**
 * Campos de contraseña nueva + confirmación, con las reglas visibles y su estado en vivo.
 * Se usa en registro, configuración inicial, restablecimiento, cambio de contraseña y creación de usuarios.
 */
export default function NewPasswordFields({
  password,
  confirm,
  onPasswordChange,
  onConfirmChange,
  personalData = [],
  label = 'Contraseña',
  confirmLabel = 'Repite la contraseña',
}: Props) {
  const [visible, setVisible] = useState(false);
  const ids = useId();
  const failed = useMemo(() => passwordViolations(password, personalData), [password, personalData]);
  const strength = passwordStrength(password, failed);
  const touched = password.length > 0;
  const confirmTouched = confirm.length > 0;
  const matches = password === confirm;

  return (
    <div className="new-password">
      <label className="field" htmlFor={`${ids}-pw`}>
        <span>{label}</span>
        <div>
          <input
            id={`${ids}-pw`}
            type={visible ? 'text' : 'password'}
            value={password}
            onChange={(e) => onPasswordChange(e.target.value)}
            maxLength={PASSWORD_MAX_LENGTH}
            autoComplete="new-password"
            aria-describedby={`${ids}-rules`}
            aria-invalid={touched && failed.size > 0}
            required
          />
          <button
            type="button"
            className="pw-toggle"
            onClick={() => setVisible((v) => !v)}
            aria-label={visible ? 'Ocultar contraseña' : 'Mostrar contraseña'}
            aria-pressed={visible}
          >
            {visible ? <EyeOff size={16} /> : <Eye size={16} />}
          </button>
        </div>
      </label>

      <div className="pw-meter" data-level={strength} aria-hidden={!touched}>
        <div className="pw-meter-bar">
          {[1, 2, 3, 4].map((n) => (
            <i key={n} className={strength >= n ? 'on' : ''} />
          ))}
        </div>
        <span>{touched ? STRENGTH_LABEL[strength] : 'Escribe una contraseña'}</span>
      </div>

      <ul className="pw-rules" id={`${ids}-rules`} aria-live="polite">
        {PASSWORD_RULES.map((rule) => {
          const ok = touched && !failed.has(rule.id);
          return (
            <li key={rule.id} className={!touched ? '' : ok ? 'ok' : 'fail'}>
              {ok ? <Check size={13} /> : <X size={13} />}
              <span>{rule.label}</span>
              <span className="sr-only">{!touched ? '' : ok ? '(cumplida)' : '(pendiente)'}</span>
            </li>
          );
        })}
      </ul>

      <label className="field" htmlFor={`${ids}-confirm`}>
        <span>{confirmLabel}</span>
        <div>
          <input
            id={`${ids}-confirm`}
            type={visible ? 'text' : 'password'}
            value={confirm}
            onChange={(e) => onConfirmChange(e.target.value)}
            maxLength={PASSWORD_MAX_LENGTH}
            autoComplete="new-password"
            aria-invalid={confirmTouched && !matches}
            required
          />
        </div>
      </label>
      {confirmTouched && (
        <div className={`pw-match ${matches ? 'ok' : 'fail'}`} role="status">
          {matches ? <Check size={13} /> : <X size={13} />}
          {matches ? 'Las contraseñas coinciden' : 'Las contraseñas no coinciden'}
        </div>
      )}
    </div>
  );
}
