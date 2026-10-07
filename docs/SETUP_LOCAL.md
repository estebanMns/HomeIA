# Configuración Local de HomeIA

## Opción 1: Con Docker (Recomendado)

### Requisitos
- Docker Desktop instalado y ejecutándose

### Pasos
```bash
cd /Users/estebanmeneses/Desktop/HomeIA

# Habilitar Docker Compose en application.properties
# Cambiar spring.docker.compose.enabled=false a spring.docker.compose.enabled=true

# Iniciar la aplicación
mvn spring-boot:run
```

Docker Compose iniciará automáticamente PostgreSQL en el puerto 5432.

---

## Opción 2: Sin Docker (Desarrollo Local)

### Para PostgreSQL Local

**Requisitos:**
- PostgreSQL instalado localmente
- Base de datos creada

**Configuración:**
```sql
-- En PostgreSQL
CREATE DATABASE homeIA;
CREATE USER homeIA WITH PASSWORD 'homeIA';
GRANT ALL PRIVILEGES ON DATABASE homeIA TO homeIA;
```

**Archivo: `src/main/resources/application-local.properties`**
```properties
# Database local
spring.datasource.url=jdbc:postgresql://localhost:5432/homeIA
spring.datasource.username=homeIA
spring.datasource.password=homeIA

# Docker Compose deshabilitado
spring.docker.compose.enabled=false
```

**Ejecutar:**
```bash
mvn spring-boot:run -Dspring-boot.run.arguments="--spring.profiles.active=local"
```

---

### Opción 3: Con H2 (Embebida - Rápida para Desarrollo)

**Archivo: `src/main/resources/application-h2.properties`**
```properties
# H2 Embebida (en memoria)
spring.datasource.url=jdbc:h2:mem:homeIA
spring.datasource.driver-class-name=org.h2.Driver
spring.jpa.database-platform=org.hibernate.dialect.H2Dialect
spring.h2.console.enabled=true

# Docker Compose deshabilitado
spring.docker.compose.enabled=false
```

**Ejecutar:**
```bash
mvn spring-boot:run -Dspring-boot.run.arguments="--spring.profiles.active=h2"
```

**Acceder a H2 Console:**
- URL: http://localhost:8080/h2-console
- JDBC URL: `jdbc:h2:mem:homeIA`
- Username: `sa`
- Password: (dejar vacío)

---

## Variables de Entorno Necesarias

**Crear archivo `.env` en la raíz del proyecto:**
```bash
GEMINI_API_KEY="tu-api-key-de-gemini-aqui"
```

---

## Solución de Problemas

### Error: "Docker is not installed"
**Solución:** Desabilitar Docker Compose en `application.properties`:
```properties
spring.docker.compose.enabled=false
```

### Error: "Connection refused - PostgreSQL"
**Soluciones:**
1. Asegurate de que PostgreSQL está corriendo: `brew services start postgresql@15`
2. O usa la opción H2 embebida (Opción 3 arriba)

### Error: "Cannot find symbol" - Gemini API
**Solución:** Asegurate de tener la variable de entorno `GEMINI_API_KEY` configurada en `.env`

---

## Recomendación para Desarrollo

**Para desarrollo rápido sin instalaciones extra, usa la Opción 3 (H2):**
```bash
mvn spring-boot:run -Dspring-boot.run.arguments="--spring.profiles.active=h2"
```

Esto:
- ✅ No requiere Docker
- ✅ No requiere PostgreSQL
- ✅ Base de datos en memoria (rápida)
- ✅ Perfecta para testing local
- ⚠️ Datos se pierden al reiniciar

---

## Para Producción

Usa Docker Compose con PostgreSQL para una configuración robusta y persistente.
