# Integración de Gemini AI en HomeIA

## Descripción General

Este proyecto utiliza **Google Gemini 2.0 Flash** para análisis inteligente de predicción de ocupancia en habitaciones basándose en datos históricos de consumo de energía.

## Arquitectura

```
Domain (RoomId, OccupancyPredictionPort)
    ↓
Application Layer
    ├── GeminiOccupancyPredictionService (lógica de negocio)
    └── OccupancyPredictionPort (interfaz)
    ↓
Infrastructure Layer
    ├── GeminiConfig (configuración del cliente Gemini)
    ├── GeminiAiAdapter (implementa el puerto)
    └── EnergyConsumptionHistoryRepository (datos históricos)
```

## Configuración

### 1. Variables de Entorno (.env)

Crear/actualizar archivo `.env` en la raíz del proyecto:

```bash
GEMINI_API_KEY="tu-api-key-aqui"
```

**Nota:** El archivo `.env` está en `.gitignore` para seguridad. **NUNCA** versionarlo con credenciales reales.

### 2. Propiedades de Aplicación

Las siguientes propiedades se cargan en `application.properties`:

```properties
gemini.api-key=${GEMINI_API_KEY}
gemini.model-id=gemini-2.0-flash
```

## Flujo de Predicción

1. **Input:** RoomId + Instant (hora actual)
2. **Recuperar datos:** Los últimos 24 horas de consumo de energía de la habitación
3. **Construir prompt:** Análisis estructurado con contexto de consumo
4. **Query a Gemini:** Envía prompt y obtiene respuesta
5. **Parsear respuesta:** Extrae probabilidad de ocupancia (0.0 - 1.0)
6. **Cachear resultado:** 5 minutos de cache para evitar queries innecesarias
7. **Output:** Probabilidad numérica (double)

## Respuesta esperada de Gemini

Gemini debe responder con JSON estructurado:

```json
{
  "probability": 0.85,
  "reasoning": "Alto consumo de energía detectado en últimas 2 horas, indicativo de ocupancia activa"
}
```

## Manejo de Errores

### Cache Degradado
Si Gemini falla pero hay predicción en cache expirada → usa el valor en cache antiguo

### Fallback
Si todo falla → retorna probabilidad por defecto: 0.5 (50%)

### Logging
Todos los pasos críticos se registran en LOG con nivel DEBUG/INFO:
- Cache hits/misses
- Queries a Gemini
- Parsing de respuestas
- Errores y recuperaciones

## Dependencias

```xml
<!-- Google Generative AI SDK -->
<dependency>
    <groupId>com.google.ai.client.generativeai</groupId>
    <artifactId>google-generativeai</artifactId>
    <version>0.7.0</version>
</dependency>

<!-- JSON parsing -->
<dependency>
    <groupId>com.google.code.gson</groupId>
    <artifactId>gson</artifactId>
    <version>2.10.1</version>
</dependency>
```

## Testing

Tests disponibles en: `src/test/java/com/home/ia/application/service/GeminiOccupancyPredictionServiceTest.java`

```bash
# Ejecutar tests
mvn test -Dtest=GeminiOccupancyPredictionServiceTest
```

## Casos de Uso

### 1. Predicción de Ocupancia
Usado por `OccupancyPolicy` para determinar si una habitación está ocupada:

```java
double probability = occupancyPredictionPort.probabilityOfReturn(roomId, now);
if (probability >= 0.7) {
    // Considerar habitación como ocupada
}
```

### 2. Automatización de Apagado
Si una habitación se vacía (probabilidad < threshold), se apagan dispositivos automáticamente.

## Optimizaciones

### Cache Thread-Safe
- **ConcurrentHashMap** para evitar race conditions
- **Expiración automática** después de 5 minutos
- **Fallback a cache expirado** si hay error

### Validación de Respuesta
- Valida rango 0.0-1.0
- Parseo robusto con fallback a texto plano
- Logging de discrepancias

## Costos API

- **Modelo:** gemini-2.0-flash (muy económico)
- **Rate Limiting:** Respeta los límites de Google Cloud
- **Caching:** Reduce llamadas innecesarias a 1 por habitación cada 5 min

## Monitoreo

Métricas disponibles en `/actuator/metrics`:
- `gemini.predictions.total`
- `gemini.predictions.duration`
- `gemini.api.errors`

## Solución de Problemas

### Error: "Invalid API Key"
- Verificar que `GEMINI_API_KEY` está en `.env`
- Confirmar que la API key es válida en Google Cloud Console
- Reiniciar la aplicación después de cambiar `.env`

### Error: "Rate limit exceeded"
- El cache de 5 minutos previene esto
- Si persiste, aumentar duración del cache en `GeminiOccupancyPredictionService`

### Predicción siempre 0.5 (default)
- Revisar logs para errores de Gemini
- Confirmar que hay datos de consumo en base de datos
- Verificar prompt en `buildAnalysisPrompt()`

## Seguridad

✅ **Recomendaciones implementadas:**
- API key en `.env` (no en código)
- Logs no revelan API key
- Validación de respuestas antes de usar
- Manejo de excepciones sin exponer detalles internos

## Próximas Mejoras

- [ ] Integración con métricas Prometheus
- [ ] Fine-tuning del prompt basado en feedback
- [ ] Predicción multi-modelo (Gemini + reglas locales)
- [ ] Análisis de patrones semanales/estacionales
