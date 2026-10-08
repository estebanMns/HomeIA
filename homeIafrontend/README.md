# HomeIA - Frontend

Frontend para HomeIA - Sistema Inteligente de Control del Hogar

Desarrollado con:
- **Next.js 15** - Framework React
- **TypeScript** - Tipado estático
- **Tailwind CSS** - Estilos
- **REST API** - Comunicación con backend

## 🚀 Inicio Rápido

### Requisitos
- Node.js 18+
- npm o yarn
- Backend HomeIA ejecutándose en `http://localhost:8080`

### Instalación

```bash
# Instalar dependencias
npm install

# Configurar variables de entorno
# (crear .env.local con valores de API_URL)

# Ejecutar en desarrollo
npm run dev
```

Abre [http://localhost:3000](http://localhost:3000) en tu navegador.

## 📁 Estructura

```
src/
├── app/              # Páginas (Next.js App Router)
├── components/       # Componentes reutilizables
├── contexts/         # Contextos (Auth)
├── services/         # Servicios (API)
├── types/            # Tipos TypeScript
└── utils/            # Utilidades
```

## 🔐 Autenticación

**Credenciales Demo:**
- Email: `andrea@homeia.co`
- Contraseña: `HomeIA2025`

## 📡 API Integration

Backend URL: `http://localhost:8080/api`

### Endpoints Principales
- `POST /auth/login` - Login
- `POST /auth/refresh` - Refrescar token
- `GET /devices` - Dispositivos
- `POST /devices/{id}/toggle` - Control de dispositivos
- `GET /alerts` - Alertas
- `GET /metrics/summary` - Métricas

## 🛠️ Scripts

```bash
npm run dev      # Desarrollo
npm run build    # Build
npm start        # Producción
npm run lint     # Linting
```

## 📝 Variables de Entorno

```env
NEXT_PUBLIC_API_URL=http://localhost:8080/api
NEXT_PUBLIC_WS_URL=ws://localhost:8080/ws
```

## 🤖 Generado con

Claude Haiku 4.5 - 2026-10-08
