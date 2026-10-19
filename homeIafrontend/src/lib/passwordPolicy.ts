/**
 * Reglas de contraseña de HomeIA, iguales a las del backend
 * (src/main/java/com/homeia/service/PasswordPolicy.java). El backend es quien decide:
 * esto solo guía al usuario mientras escribe. Si cambias una regla, cámbiala en los dos lados.
 */

export const PASSWORD_MIN_LENGTH = 12;
export const PASSWORD_MAX_LENGTH = 64;
const MAX_BYTES = 72; // límite de BCrypt

export type PasswordRuleId =
  | 'LENGTH'
  | 'UPPERCASE'
  | 'LOWERCASE'
  | 'DIGIT'
  | 'SYMBOL'
  | 'NO_EDGE_SPACES'
  | 'NO_REPEATS'
  | 'NO_SEQUENCES'
  | 'NO_PERSONAL_DATA'
  | 'NOT_COMMON';

export const PASSWORD_RULES: { id: PasswordRuleId; label: string }[] = [
  { id: 'LENGTH', label: `Entre ${PASSWORD_MIN_LENGTH} y ${PASSWORD_MAX_LENGTH} caracteres` },
  { id: 'UPPERCASE', label: 'Una letra mayúscula' },
  { id: 'LOWERCASE', label: 'Una letra minúscula' },
  { id: 'DIGIT', label: 'Un número' },
  { id: 'SYMBOL', label: 'Un símbolo, por ejemplo ! ? # $ % & * - _' },
  { id: 'NO_EDGE_SPACES', label: 'Sin espacios al inicio ni al final' },
  { id: 'NO_REPEATS', label: 'Sin el mismo carácter 3 veces seguidas (aaa, 111)' },
  { id: 'NO_SEQUENCES', label: 'Sin secuencias de 4 caracteres (1234, abcd)' },
  { id: 'NO_PERSONAL_DATA', label: 'Sin tu nombre ni tu correo' },
  { id: 'NOT_COMMON', label: 'Sin palabras muy usadas (password, qwerty, 123456, homeia…)' },
];

const COMMON_FRAGMENTS = [
  'password', 'passw0rd', 'contrasena', 'contrasenia', 'clave', 'qwerty', 'asdfgh', 'zxcvbn',
  '123456', '654321', '111111', '000000', 'abc123', 'iloveyou', 'teamo', 'letmein', 'welcome',
  'bienvenido', 'admin', 'administrador', 'homeia', 'colombia', 'football', 'futbol', 'dragon',
  'monkey', 'master', 'secret', 'secreto', 'changeme', 'cambiame',
];

/** Minúsculas y sin tildes, igual que en el backend. */
function fold(text: string): string {
  return text.normalize('NFD').replace(/\p{M}/gu, '').toLowerCase();
}

function hasRepeats(pw: string): boolean {
  return /([\s\S])\1\1/u.test(pw);
}

function isAlnum(c: string): boolean {
  return /^[\p{L}\p{N}]$/u.test(c);
}

function hasSequence(folded: string): boolean {
  let up = 1;
  let down = 1;
  for (let i = 1; i < folded.length; i++) {
    const prev = folded.charCodeAt(i - 1);
    const cur = folded.charCodeAt(i);
    const alnum = isAlnum(folded[i - 1]) && isAlnum(folded[i]);
    up = alnum && cur === prev + 1 ? up + 1 : 1;
    down = alnum && cur === prev - 1 ? down + 1 : 1;
    if (up >= 4 || down >= 4) return true;
  }
  return false;
}

function containsPersonalData(folded: string, personalData: (string | undefined)[]): boolean {
  for (const data of personalData) {
    if (!data || !data.trim()) continue;
    let value = fold(data);
    const at = value.indexOf('@');
    if (at > 0) value = value.slice(0, at);
    for (const part of value.split(/[^a-z0-9]+/)) {
      if (part.length >= 3 && folded.includes(part)) return true;
    }
  }
  return false;
}

/** Reglas que la contraseña no cumple (vacío si es válida). */
export function passwordViolations(password: string, personalData: (string | undefined)[] = []): Set<PasswordRuleId> {
  const failed = new Set<PasswordRuleId>();
  const pw = password ?? '';
  const length = [...pw].length;
  if (length < PASSWORD_MIN_LENGTH || length > PASSWORD_MAX_LENGTH || new TextEncoder().encode(pw).length > MAX_BYTES) {
    failed.add('LENGTH');
  }
  if (!/\p{Lu}/u.test(pw)) failed.add('UPPERCASE');
  if (!/\p{Ll}/u.test(pw)) failed.add('LOWERCASE');
  if (!/\p{Nd}/u.test(pw)) failed.add('DIGIT');
  if (!/[^\p{L}\p{N}\s]/u.test(pw)) failed.add('SYMBOL');
  if (pw.length > 0 && (/^\s/u.test(pw) || /\s$/u.test(pw))) failed.add('NO_EDGE_SPACES');
  if (hasRepeats(pw)) failed.add('NO_REPEATS');
  const folded = fold(pw);
  if (hasSequence(folded)) failed.add('NO_SEQUENCES');
  if (containsPersonalData(folded, personalData)) failed.add('NO_PERSONAL_DATA');
  const lettersAndDigits = folded.replace(/[^a-z0-9]/g, '');
  if (COMMON_FRAGMENTS.some((f) => lettersAndDigits.includes(f))) failed.add('NOT_COMMON');
  return failed;
}

/** Fortaleza orientativa de 0 a 4, solo para el medidor visual. */
export function passwordStrength(password: string, failed: Set<PasswordRuleId>): 0 | 1 | 2 | 3 | 4 {
  if (!password) return 0;
  const passed = PASSWORD_RULES.length - failed.size;
  if (failed.size > 0) return passed >= 7 ? 2 : 1;
  return [...password].length >= 16 ? 4 : 3;
}
