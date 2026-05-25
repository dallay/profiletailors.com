#!/bin/bash

# Script de utilidad para gestionar el monitoreo de SMP
# Uso: ./monitoring.sh [comando]

set -e

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
COMPOSE_FILE="$SCRIPT_DIR/../../infra/apps/smp/compose.yaml"

function show_help() {
    cat << EOF
Gestión de Monitoreo SMP - Prometheus & Grafana

Uso: ./monitoring.sh [comando]

Comandos:
    start       Inicia Prometheus y Grafana
    stop        Detiene Prometheus y Grafana
    restart     Reinicia los servicios
    logs        Muestra logs de los servicios
    status      Muestra el estado de los servicios
    clean       Detiene y elimina volúmenes (¡cuidado!)
    health      Verifica la salud del servidor SMP
    metrics     Muestra métricas del servidor SMP
    help        Muestra esta ayuda

Ejemplos:
    ./monitoring.sh start
    ./monitoring.sh logs prometheus
    ./monitoring.sh health

URLs:
    Prometheus: http://localhost:9090
    Grafana:    http://localhost:3000 (admin/admin)
    SMP Health: http://localhost:8080/actuator/health
    SMP Metrics: http://localhost:9091/actuator/prometheus (puerto interno)
EOF
}

function start_monitoring() {
    echo "🚀 Iniciando Prometheus y Grafana..."
    docker-compose -f "$COMPOSE_FILE" up -d
    echo ""
    echo "✅ Servicios iniciados:"
    echo "   Prometheus: http://localhost:9090"
    echo "   Grafana:    http://localhost:3000 (admin/admin)"
    echo ""
    echo "💡 Verifica que el servidor SMP esté corriendo:"
    echo "   API:     http://localhost:8080"
    echo "   Metrics: http://localhost:9091 (puerto interno)"
}

function stop_monitoring() {
    echo "🛑 Deteniendo Prometheus y Grafana..."
    docker-compose -f "$COMPOSE_FILE" down
    echo "✅ Servicios detenidos"
}

function restart_monitoring() {
    echo "🔄 Reiniciando servicios..."
    docker-compose -f "$COMPOSE_FILE" restart
    echo "✅ Servicios reiniciados"
}

function show_logs() {
    local service="${1:-}"
    if [ -z "$service" ]; then
        docker-compose -f "$COMPOSE_FILE" logs -f
    else
        docker-compose -f "$COMPOSE_FILE" logs -f "$service"
    fi
}

function show_status() {
    echo "📊 Estado de los servicios:"
    docker-compose -f "$COMPOSE_FILE" ps
    echo ""
    echo "🔍 Verificando conectividad..."
    
    # Check Prometheus
    if curl -s http://localhost:9090/-/healthy > /dev/null 2>&1; then
        echo "✅ Prometheus: UP"
    else
        echo "❌ Prometheus: DOWN"
    fi
    
    # Check Grafana
    if curl -s http://localhost:3000/api/health > /dev/null 2>&1; then
        echo "✅ Grafana: UP"
    else
        echo "❌ Grafana: DOWN"
    fi
    
    # Check SMP
    if curl -s http://localhost:8080/actuator/health > /dev/null 2>&1; then
        echo "✅ SMP Server: UP"
    else
        echo "❌ SMP Server: DOWN (¿está corriendo?)"
    fi
}

function clean_monitoring() {
    echo "⚠️  ADVERTENCIA: Esto eliminará todos los datos de Prometheus y Grafana"
    read -p "¿Estás seguro? (y/N): " -n 1 -r
    echo
    if [[ $REPLY =~ ^[Yy]$ ]]; then
        echo "🧹 Limpiando..."
        docker-compose -f "$COMPOSE_FILE" down -v
        echo "✅ Volúmenes eliminados"
    else
        echo "❌ Operación cancelada"
    fi
}

function check_health() {
    echo "🏥 Verificando salud del servidor SMP..."
    echo ""
    
    if ! curl -s http://localhost:8080/actuator/health > /dev/null 2>&1; then
        echo "❌ El servidor SMP no está disponible en http://localhost:8080"
        echo "   Inicia el servidor con: ./gradlew bootRun"
        exit 1
    fi
    
    echo "📋 Health Check:"
    curl -s http://localhost:8080/actuator/health | jq '.' || curl -s http://localhost:8080/actuator/health
    echo ""
    
    echo "📋 Readiness Probe:"
    curl -s http://localhost:8080/actuator/health/readiness | jq '.' || curl -s http://localhost:8080/actuator/health/readiness
    echo ""
    
    echo "📋 Liveness Probe:"
    curl -s http://localhost:8080/actuator/health/liveness | jq '.' || curl -s http://localhost:8080/actuator/health/liveness
}

function show_metrics() {
    echo "📊 Métricas del servidor SMP..."
    echo ""
    
    if ! curl -s http://localhost:8080/actuator/prometheus > /dev/null 2>&1; then
        echo "❌ No se pueden obtener métricas. ¿Está el servidor corriendo?"
        exit 1
    fi
    
    echo "🔢 Métricas disponibles (primeras 20 líneas):"
    curl -s http://localhost:8080/actuator/prometheus | grep -v "^#" | head -20
    echo ""
    echo "💡 Para ver todas las métricas: curl http://localhost:8080/actuator/prometheus"
}

# Main
case "${1:-help}" in
    start)
        start_monitoring
        ;;
    stop)
        stop_monitoring
        ;;
    restart)
        restart_monitoring
        ;;
    logs)
        show_logs "${2:-}"
        ;;
    status)
        show_status
        ;;
    clean)
        clean_monitoring
        ;;
    health)
        check_health
        ;;
    metrics)
        show_metrics
        ;;
    help|--help|-h)
        show_help
        ;;
    *)
        echo "❌ Comando desconocido: $1"
        echo ""
        show_help
        exit 1
        ;;
esac
