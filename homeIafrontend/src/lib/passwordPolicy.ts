/**
 * Reglas de contraseña de HomeIA, iguales a las del backend
 * (src/main/java/com/homeia/service/PasswordPolicy.java). El backend es quien decide:
 * esto solo guía al usuario mientras escribe. Si cambias una regla, cámbiala en los dos lados.
 */

export const PASSWORD_MIN_LENGTH = 12;
/** Tope técnico, no una regla de negocio: BCrypt solo usa los primeros 72 bytes. */
export const PASSWORD_MAX_LENGTH = 64;
const MAX_BYTES = 72;
const SEQUENCE_LENGTH = 4;
const PERSONAL_TOKEN_LENGTH = 4;

/** Alfabeto, números y filas del teclado: no pueden aparecer 4 seguidos, en ningún sentido. */
const SEQUENCES = ['abcdefghijklmnopqrstuvwxyz', '0123456789', '1234567890', 'qwertyuiop', 'asdfghjklñ', 'zxcvbnm'];

/** Contraseñas y palabras base muy usadas, comparadas sin números, símbolos ni tildes. */
const COMMON = new Set([
  'password', 'contrasena', 'clave', 'qwerty', 'admin', 'administrador', 'homeia', 'usuario', 'user',
  'login', 'bienvenido', 'welcome', 'letmein', 'iloveyou', 'teamo', 'secreto', 'secret', 'master',
  'dragon', 'monkey', 'football', 'futbol', 'princesa', 'superman', 'batman', 'colombia', 'abc',
  'casa', 'micasa', 'hogar', 'energia', 'test', 'prueba', 'default', 'changeme', 'cambiar',
]);

/** Palabras que no pueden aparecer en ninguna parte de la contraseña. */
const FORBIDDEN_ROOTS = ['password', 'contrasena', 'qwerty', 'homeia'];

export type PasswordRuleId =
  | 'LENGTH'
  | 'LOWERCASE'
  | 'UPPERCASE'
  | 'DIGIT'
  | 'SYMBOL'
  | 'NO_SPACES'
  | 'NO_REPEATS'
  | 'NO_SEQUENCES'
  | 'NOT_COMMON'
  | 'NO_PERSONAL_DATA';

export const PASSWORD_RULES: { id: PasswordRuleId; label: string }[] = [
  { id: 'LENGTH', label: `Entre ${PASSWORD_MIN_LENGTH} y ${PASSWORD_MAX_LENGTH} caracteres` },
  { id: 'LOWERCASE', label: 'Al menos una letra minúscula' },
  { id: 'UPPERCASE', label: 'Al menos una letra mayúscula' },
  { id: 'DIGIT', label: 'Al menos un número' },
  { id: 'SYMBOL', label: 'Al menos un carácter especial, por ejemplo . , # ! - _' },
  { id: 'NO_SPACES', label: 'Sin espacios' },
  { id: 'NO_REPEATS', label: 'Sin 3 caracteres iguales seguidos (aaa, 111)' },
  { id: 'NO_SEQUENCES', label: `Sin secuencias de ${SEQUENCE_LENGTH} o más (1234, abcd, qwer)` },
  { id: 'NOT_COMMON', label: 'No puede ser una contraseña común (password, qwerty, admin…)' },
  { id: 'NO_PERSONAL_DATA', label: 'No puede contener tu nombre ni tu correo' },
];

/** Datos del usuario que la contraseña no puede contener. */
export interface PersonalData {
  email?: string;
  fullName?: string;
}

/** Minúsculas y sin tildes: "Contraseña" → "contrasena". */
function fold(value: string): string {
  return value.normalize('NFD').replace(/\p{M}/gu, '').toLowerCase();
}

function hasSequence(lower: string): boolean {
  return SEQUENCES.some((sequence) => {
    const reversed = [...sequence].reverse().join('');
    for (let i = 0; i + SEQUENCE_LENGTH <= sequence.length; i++) {
      if (lower.includes(sequence.slice(i, i + SEQUENCE_LENGTH)) || lower.includes(reversed.slice(i, i + SEQUENCE_LENGTH))) {
        return true;
      }
    }
    return false;
  });
}

function isCommon(folded: string): boolean {
  const letters = folded.replace(/[^\p{L}]/gu, '');
  return COMMON.has(letters) || FORBIDDEN_ROOTS.some((root) => letters.includes(root));
}

function containsPersonalData(folded: string, personal?: PersonalData): boolean {
  const tokens: string[] = [];
  if (personal?.email) {
    const at = personal.email.indexOf('@');
    tokens.push(...fold(at >= 0 ? personal.email.slice(0, at) : personal.email).split(/[^\p{L}\p{N}]+/u));
  }
  if (personal?.fullName) {
    tokens.push(...fold(personal.fullName).split(/[^\p{L}\p{N}]+/u));
  }
  return tokens.some((t) => t.length >= PERSONAL_TOKEN_LENGTH && folded.includes(t));
}

/** Reglas que la contraseña no cumple (vacío si es válida). */
export function passwordViolations(password: string, personal?: PersonalData): Set<PasswordRuleId> {
  const failed = new Set<PasswordRuleId>();
  const pw = password ?? '';
  const lower = pw.toLowerCase();
  const folded = fold(pw);
  const length = [...pw].length;
  if (length < PASSWORD_MIN_LENGTH || length > PASSWORD_MAX_LENGTH || new TextEncoder().encode(pw).length > MAX_BYTES) {
    failed.add('LENGTH');
  }
  if (!/\p{Ll}/u.test(pw)) failed.add('LOWERCASE');
  if (!/\p{Lu}/u.test(pw)) failed.add('UPPERCASE');
  if (!/\p{Nd}/u.test(pw)) failed.add('DIGIT');
  // Especial = cualquier carácter que no sea letra, número ni espacio: . , # ! - _ @ $ % & * ? etc.
  if (!/[^\p{L}\p{N}\s]/u.test(pw)) failed.add('SYMBOL');
  if (/\s/u.test(pw)) failed.add('NO_SPACES');
  if (/(.)\1\1/u.test(lower)) failed.add('NO_REPEATS');
  if (hasSequence(lower)) failed.add('NO_SEQUENCES');
  if (isCommon(folded)) failed.add('NOT_COMMON');
  if (containsPersonalData(folded, personal)) failed.add('NO_PERSONAL_DATA');
  return failed;
}

/** Fortaleza orientativa de 0 a 4, solo para el medidor visual. */
export function passwordStrength(password: string, failed: Set<PasswordRuleId>): 0 | 1 | 2 | 3 | 4 {
  if (!password) return 0;
  if (failed.size >= 3) return 1;
  if (failed.size > 0) return 2;
  return [...password].length >= 16 ? 4 : 3;
}
