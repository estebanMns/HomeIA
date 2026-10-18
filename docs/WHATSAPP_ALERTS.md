# Alertas por WhatsApp

## Por qué se necesita una plantilla

La WhatsApp Cloud API de Meta solo entrega **texto libre** dentro de las 24 horas siguientes a que el
destinatario le escribe al número de la empresa. Las alertas de HomeIA las inicia el sistema (una fuga
puede detectarse a las 3 a. m.), así que deben enviarse como **plantilla aprobada**. Sin plantilla, Meta
rechaza el mensaje y queda `FAILED` tras los reintentos.

## Crear la plantilla en Meta

1. En **WhatsApp Manager → Plantillas de mensajes**, crea una plantilla de categoría **Utilidad**.
2. Nombre sugerido: `homeia_alerta`. Idioma: Español (`es`) o Español (Colombia) (`es_CO`).
3. Cuerpo con exactamente tres variables, en este orden:

   ```
   HomeIA · Alerta {{1}}: {{2}}. {{3}}
   ```

   - `{{1}}` severidad (`CRÍTICA`, `ADVERTENCIA`, `INFO` o `PRUEBA`)
   - `{{2}}` título de la alerta (p. ej. `Posible fuga de energía`)
   - `{{3}}` detalle (p. ej. `Calentador baño consume 1500 W con la habitación vacía hace 47 min...`)

4. Espera la aprobación de Meta.

## Configurar HomeIA

En `.env`:

```properties
WHATSAPP_ACCESS_TOKEN=...
WHATSAPP_PHONE_NUMBER_ID=...
WHATSAPP_ALERT_TEMPLATE=homeia_alerta
WHATSAPP_TEMPLATE_LANGUAGE=es
```

Sin `WHATSAPP_ALERT_TEMPLATE` se envía texto libre, útil solo para pruebas con un número que te haya
escrito en las últimas 24 horas.

## Cómo se envían

- Al crearse una alerta de **advertencia** o **crítica**, se guarda un mensaje `PENDING` por destinatario
  activo. No se envía dentro del ciclo de automatización, para que una respuesta lenta de Meta no lo bloquee.
- Cada 15 s, `WhatsAppDispatchScheduler` envía los pendientes, cada uno en su propia transacción.
- Si falla, se reintenta a los **1 min** y a los **5 min**. Al tercer fallo queda `FAILED` y solo se
  reenvía manualmente desde el Centro de alertas.
- Las llamadas a Meta tienen tiempo límite (5 s para conectar, 10 s para responder).
- Una misma alerta del mismo dispositivo no se repite durante el **enfriamiento** configurado en
  Automatización (30 min por defecto), aunque ya se haya marcado como revisada.
