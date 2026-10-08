#!/bin/bash

# Script para iniciar HomeIA (Backend + Frontend) en modo desarrollo

echo "🚀 Iniciando HomeIA (Backend + Frontend)..."
echo ""

# Colores
GREEN='\033[0;32m'
BLUE='\033[0;34m'
NC='\033[0m' # No Color

# Iniciar Backend (Spring Boot)
echo -e "${BLUE}📦 Iniciando Backend en puerto 8080...${NC}"
mvn spring-boot:run > /tmp/homeIA-backend.log 2>&1 &
BACKEND_PID=$!
echo -e "${GREEN}✅ Backend iniciado (PID: $BACKEND_PID)${NC}"

# Esperar a que el backend esté listo
sleep 10

# Iniciar Frontend (Next.js)
echo -e "${BLUE}🌐 Iniciando Frontend en puerto 3000...${NC}"
cd homeIafrontend
npm run dev > /tmp/homeIA-frontend.log 2>&1 &
FRONTEND_PID=$!
echo -e "${GREEN}✅ Frontend iniciado (PID: $FRONTEND_PID)${NC}"

echo ""
echo -e "${GREEN}════════════════════════════════════════${NC}"
echo -e "${GREEN}✅ HomeIA está ejecutándose!${NC}"
echo -e "${GREEN}════════════════════════════════════════${NC}"
echo ""
echo "📱 Frontend:  http://localhost:3000"
echo "🔧 Backend:   http://localhost:8080"
echo "📊 API Docs:  http://localhost:8080/swagger-ui.html"
echo "📈 Metrics:   http://localhost:8080/actuator/prometheus"
echo ""
echo "🔐 Credenciales:"
echo "   Email:    andrea@homeia.co"
echo "   Password: HomeIA2025"
echo ""
echo "Logs:"
echo "   Backend:  tail -f /tmp/homeIA-backend.log"
echo "   Frontend: tail -f /tmp/homeIA-frontend.log"
echo ""
echo "Para parar: kill $BACKEND_PID $FRONTEND_PID"
echo ""

# Mantener los procesos vivos
wait
