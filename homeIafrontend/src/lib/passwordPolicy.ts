/**
 * Reglas de contraseña de HomeIA, iguales a las del backend
 * (src/main/java/com/homeia/service/PasswordPolicy.java). El backend es quien decide:
 * esto solo guía al usuario mientras escribe. Si cambias una regla, cámbiala en los dos lados.
 */

export const PASSWORD_MIN_LENGTH = 8;
/** Tope técnico, no una regla de negocio: BCrypt solo usa los primeros 72 bytes. */
export const PASSWORD_MAX_LENGTH = 64;
const MAX_BYTES = 72;

export type PasswordRuleId = 'LENGTH' | 'UPPERCASE' | 'SYMBOL';

export const PASSWORD_RULES: { id: PasswordRuleId; label: string }[] = [
  { id: 'LENGTH', label: `Mínimo ${PASSWORD_MIN_LENGTH} caracteres` },
  { id: 'UPPERCASE', label: 'Al menos una letra mayúscula' },
  { id: 'SYMBOL', label: 'Al menos un carácter especial, por ejemplo . , # ! - _' },
];

/** Reglas que la contraseña no cumple (vacío si es válida). */
export function passwordViolations(password: string): Set<PasswordRuleId> {
  const failed = new Set<PasswordRuleId>();
  const pw = password ?? '';
  const length = [...pw].length;
  if (length < PASSWORD_MIN_LENGTH || length > PASSWORD_MAX_LENGTH || new TextEncoder().encode(pw).length > MAX_BYTES) {
    failed.add('LENGTH');
  }
  if (!/\p{Lu}/u.test(pw)) failed.add('UPPERCASE');
  // Especial = cualquier carácter que no sea letra, número ni espacio: . , # ! - _ @ $ % & * ? etc.
  if (!/[^\p{L}\p{N}\s]/u.test(pw)) failed.add('SYMBOL');
  return failed;
}

/** Fortaleza orientativa de 0 a 4, solo para el medidor visual. */
export function passwordStrength(password: string, failed: Set<PasswordRuleId>): 0 | 1 | 2 | 3 | 4 {
  if (!password) return 0;
  if (failed.size >= 2) return 1;
  if (failed.size === 1) return 2;
  const length = [...password].length;
  const variety = [/\p{Ll}/u, /\p{Nd}/u].filter((r) => r.test(password)).length;
  return length >= 12 && variety === 2 ? 4 : 3;
}
