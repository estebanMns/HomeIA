# Refresh Tokens - Guía de Implementación

## 📋 Resumen

Los Refresh Tokens permiten a los usuarios mantener sesiones seguras sin necesidad de re-autenticarse constantemente:

- **Access Token**: Corta vida (1 hora) para acceso a APIs
- **Refresh Token**: Larga vida (7 días) para obtener nuevos access tokens
- **Seguridad**: Tokens revocables, limitados por usuario, almacenados en BD

---

## 🔑 Flujo de Autenticación Completo

```
1. Usuario -> POST /api/auth/login
   ├─ Credentials: { email, password }
   └─ Response: { accessToken, refreshToken, userId, email }

2. Usuario -> GET /api/devices (header: Authorization: Bearer accessToken)
   ├─ JwtAuthenticationFilter valida
   └─ Request exitoso

3. Access token expira (después de 1 hora)
   
4. Usuario -> POST /api/auth/refresh
   ├─ Body: { refreshToken }
   └─ Response: { accessToken (nuevo) }

5. Usuario -> GET /api/devices (header: Authorization: Bearer newAccessToken)
   └─ Request exitoso

6. Usuario -> POST /api/auth/logout
   ├─ Header: Authorization: Bearer accessToken
   └─ Todos los refresh tokens del usuario son revocados
```

---

## 🗄️ Base de Datos

### Tabla refresh_tokens

```sql
CREATE TABLE refresh_tokens (
    id UUID PRIMARY KEY,
    token VARCHAR(500) UNIQUE,
    user_id VARCHAR(100),
    user_email VARCHAR(255),
    expires_at TIMESTAMP,
    revoked BOOLEAN DEFAULT FALSE,
    used BOOLEAN DEFAULT FALSE,
    created_at TIMESTAMP,
    used_at TIMESTAMP,
    revoked_at TIMESTAMP
);
```

**Índices:**
- `idx_refresh_tokens_token` - Búsqueda rápida por token
- `idx_refresh_tokens_user_id` - Listar tokens de usuario
- `idx_refresh_tokens_expires_at` - Limpieza de expirados
- `idx_refresh_tokens_revoked` - Filtrar revocados
- `idx_refresh_tokens_used` - Filtrar usados

---

## 🔧 Componentes Implementados

### 1. RefreshTokenEntity
```java
@Entity
@Table(name = "refresh_tokens")
public class RefreshTokenEntity {
    String id;
    String token;           // Token único
    String userId;
    String userEmail;
    Instant expiresAt;
    Boolean revoked;        // Revocado manualmente
    Boolean used;           // Ya fue usado (one-time use)
    Instant createdAt;
    Instant usedAt;
    Instant revokedAt;
}
```

### 2. RefreshTokenService
**Métodos principales:**

- `generateRefreshToken(userId, email)`: Crea token seguro
  - Límite: 5 tokens activos por usuario
  - Genera token Base64 de 32 bytes
  - Expira en 7 días
  
- `refreshAccessToken(refreshToken)`: Valida y genera nuevo access token
  - Marca el token como "usado" (one-time)
  - Genera nuevo access token
  - Auditoría completa
  
- `revokeToken(refreshToken, userId)`: Revoca un token
  
- `revokeAllTokens(userId)`: Revoca todos los tokens del usuario
  - Usado en logout
  
- `cleanupExpiredTokens()`: Limpia tokens expirados
  - Ejecutar periódicamente

### 3. AuthController - Endpoints Actualizados

#### **POST /api/auth/login**
```json
// Request
{
  "email": "andrea@homeia.co",
  "password": "HomeIA2025",
  "ipAddress": "192.168.1.1"
}

// Response 200
{
  "success": true,
  "message": "Login exitoso",
  "accessToken": "eyJhbGc...",      // JWT - 1 hora
  "refreshToken": "abc123xyz...",    // Base64 - 7 días
  "userId": "user-001",
  "email": "andrea@homeia.co",
  "expiresIn": 3600,
  "timestamp": "2026-10-08T..."
}
```

#### **POST /api/auth/refresh**
```json
// Request
{
  "refreshToken": "abc123xyz..."
}

// Response 200
{
  "success": true,
  "message": "Access token refrescado",
  "accessToken": "eyJhbGc...",      // Nuevo JWT
  "expiresIn": 3600,
  "timestamp": "2026-10-08T..."
}

// Response 401 (si token es inválido/expirado)
{
  "success": false,
  "message": "Refresh token inválido o expirado"
}
```

#### **POST /api/auth/logout**
```json
// Request
// Header: Authorization: Bearer eyJhbGc...

// Response 200
{
  "success": true,
  "message": "Logout exitoso",
  "userId": "user-001",
  "email": "andrea@homeia.co",
  "timestamp": "2026-10-08T..."
}
```

---

## 📊 Seguridad

### Características de Seguridad

1. **Token Storage**: Almacenados en BD (NO en JWT)
2. **One-Time Use**: Cada refresh token se marca como "used" después de usarlo
3. **Revocación**: Pueden ser revocados en cualquier momento
4. **Límites**: Máximo 5 tokens activos por usuario
5. **Expiración**: 7 días, con limpieza automática
6. **Auditoría**: Todas operaciones registradas

### Auditoría

Todas las operaciones se registran:

- `REFRESH_TOKEN_GENERATED` - Token generado
- `REFRESH_TOKEN_INVALID` - Token inválido/expirado
- `REFRESH_TOKEN_REVOKED` - Token revocado
- `ALL_REFRESH_TOKENS_REVOKED` - Logout: revocó todos
- `ACCESS_TOKEN_REFRESHED` - Nuevo access token generado
- `LOGIN_SUCCESS` / `LOGIN_FAILURE` - Intento de login
- `LOGOUT` - Cierre de sesión

---

## 📈 Métricas

Métricas de Prometheus integradas:

```
auth.login.success        - Logins exitosos
auth.login.failure        - Logins fallidos
auth.duration             - Duración del proceso auth (ms)
```

---

## 🔌 Cliente JavaScript (Ejemplo)

```javascript
// 1. Login
async function login() {
  const response = await fetch('/api/auth/login', {
    method: 'POST',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify({
      email: 'andrea@homeia.co',
      password: 'HomeIA2025',
      ipAddress: '192.168.1.1'
    })
  });
  
  const data = await response.json();
  localStorage.setItem('accessToken', data.accessToken);
  localStorage.setItem('refreshToken', data.refreshToken);
  localStorage.setItem('expiresIn', data.expiresIn);
  localStorage.setItem('loginTime', Date.now());
}

// 2. Refresh token automático
async function ensureAccessToken() {
  const loginTime = parseInt(localStorage.getItem('loginTime'));
  const expiresIn = parseInt(localStorage.getItem('expiresIn'));
  const now = Date.now();
  
  // Si token va a expirar en < 5 minutos, refrescar
  if (now - loginTime > (expiresIn - 300) * 1000) {
    const response = await fetch('/api/auth/refresh', {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify({
        refreshToken: localStorage.getItem('refreshToken')
      })
    });
    
    const data = await response.json();
    if (data.success) {
      localStorage.setItem('accessToken', data.accessToken);
      localStorage.setItem('loginTime', Date.now());
    } else {
      // Refresh token expirado, pedir login
      window.location.href = '/login';
    }
  }
}

// 3. API Request con token automático
async function apiRequest(url, options = {}) {
  await ensureAccessToken();
  
  const token = localStorage.getItem('accessToken');
  const headers = {
    ...options.headers,
    'Authorization': `Bearer ${token}`,
    'Content-Type': 'application/json'
  };
  
  return fetch(url, { ...options, headers });
}

// 4. Uso
const devices = await apiRequest('/api/devices');

// 5. Logout
async function logout() {
  const token = localStorage.getItem('accessToken');
  await fetch('/api/auth/logout', {
    method: 'POST',
    headers: {
      'Authorization': `Bearer ${token}`,
      'Content-Type': 'application/json'
    }
  });
  
  localStorage.removeItem('accessToken');
  localStorage.removeItem('refreshToken');
  localStorage.removeItem('expiresIn');
  localStorage.removeItem('loginTime');
  window.location.href = '/login';
}
```

---

## 🛡️ Mejores Prácticas

### En el Cliente (JavaScript)

1. **Nunca guardar refresh token en localStorage**
   ```javascript
   // ❌ NO hacer esto
   localStorage.setItem('refreshToken', token);
   
   // ✅ Mejor: guardar en httpOnly cookie
   // El servidor debe enviar: Set-Cookie: refreshToken=...; HttpOnly; Secure
   ```

2. **Refrescar automáticamente antes de expirar**
   ```javascript
   // ✅ Refrescar 5 minutos antes de expirar
   const minutosRestantes = (expiresIn - (Date.now() - loginTime)/1000) / 60;
   if (minutosRestantes < 5) {
     await refreshToken();
   }
   ```

3. **Manejar expiración de refresh token**
   ```javascript
   // ✅ Si refresh falla, pedir login nuevamente
   if (refreshResponse.status === 401) {
     window.location.href = '/login';
   }
   ```

### En el Servidor (Java)

1. **Limpiar tokens expirados** (Scheduled task)
   ```java
   @Scheduled(fixedRate = 3600000) // 1 hora
   public void cleanupExpiredTokens() {
     refreshTokenService.cleanupExpiredTokens();
   }
   ```

2. **One-time use**: Ya implementado
   - Cada refresh marca el token como usado
   - No puede ser usado dos veces

3. **Auditoría**: Ya implementada
   - Todas operaciones registradas en domain_events

---

## 🚀 Pasos para Producción

1. **Cambiar SECRET_KEY en .env**
   ```bash
   SECRET_KEY=<clave-muy-larga-aleatoria-de-produccion>
   ```

2. **Configurar HTTPS**
   - Todos los tokens deben transmitirse por HTTPS

3. **Usar HttpOnly Cookies**
   - Guardar refreshToken en httpOnly cookie
   - No en localStorage

4. **Implementar Rate Limiting**
   - Limitar intentos de refresh
   - Limitar intentos de login

5. **Monitoreo**
   - Alertas en `/api/metrics` si hay muchos logins fallidos
   - Alertas si hay muchas revocaciones

6. **Rotación de Keys** (Futuro)
   - Implementar key rotation
   - Soportar múltiples claves simultáneamente

---

## 📚 Archivos Modificados/Creados

| Archivo | Tipo | Descripción |
|---------|------|-------------|
| `RefreshTokenEntity.java` | Nuevo | Entidad de BD |
| `RefreshTokenJpaRepository.java` | Nuevo | Repositorio |
| `RefreshTokenService.java` | Nuevo | Lógica de negocio |
| `AuthController.java` | Modificado | Nuevos endpoints |
| `V006__create_refresh_tokens_table.sql` | Nuevo | Migración Flyway |
| `.env` | Modificado | REFRESH_TOKEN_EXPIRE_MINUTES |

---

## ✅ Checklist

- [x] Entidad RefreshTokenEntity
- [x] Repositorio con queries optimizadas
- [x] Service con lógica completa
- [x] AuthController con endpoints
- [x] Migración Flyway
- [x] Auditoría integrada
- [x] Métricas de Prometheus
- [x] Documentación y ejemplos

---

**Versión:** 1.0
**Fecha:** 2026-10-08
**Generado con:** Claude Haiku 4.5
