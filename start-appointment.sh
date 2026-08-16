#!/bin/bash

echo "================================================================="
echo "  CareFlow Doctor & Appointment Testing Cluster Starter           "
echo "================================================================="

SCRIPT_DIR="$( cd "$( dirname "${BASH_SOURCE[0]}" )" && pwd )"
cd "$SCRIPT_DIR" || exit 1

LOG_DIR="$SCRIPT_DIR/logs"
mkdir -p "$LOG_DIR"

if [ -f "$SCRIPT_DIR/.env" ]; then
    set -a
    source "$SCRIPT_DIR/.env"
    set +a
fi

declare -a SERVICE_PIDS

run_service() {
    local service_name=$1
    local cmd=$2
    echo "--> Launching $service_name..."
    eval "$cmd" > "$LOG_DIR/logs_${service_name}.log" 2>&1 &
    SERVICE_PIDS+=($!)
}

# 1. Eureka Server (Port 8761)
run_service "eureka-server" "mvn -pl careflow-eureka-server spring-boot:run"
echo "Waiting 8 seconds for Eureka Server..."
sleep 8

# 2. API Gateway (Port 8080)
run_service "api-gateway" "mvn -pl careflow-api-gateway spring-boot:run"

# 3. Identity Service (Port 8081)
run_service "identity-service" "mvn -pl careflow-identity-service spring-boot:run -Dspring-boot.run.profiles=remote"

# 4. Patient Service (Port 8082)
run_service "patient-service" "mvn -pl careflow-patient-service spring-boot:run -Dspring-boot.run.profiles=remote"

# 5. Appointment Service (Port 8083)
run_service "appointment-service" "mvn -pl careflow-appointment-service spring-boot:run -Dspring-boot.run.profiles=remote"

# 6. Queue Service (Port 8084)
run_service "queue-service" "mvn -pl careflow-queue-service spring-boot:run -Dspring-boot.run.profiles=remote"

# 7. Consultation Service (Port 8086)
run_service "consultation-service" "mvn -pl careflow-consultation-service spring-boot:run -Dspring-boot.run.profiles=remote"

# 8. Hospital Directory Service (Port 8090)
run_service "hospital-directory-service" "mvn -pl careflow-hospital-directory-service spring-boot:run -Dspring-boot.run.profiles=remote"

# 9. AI Service (Port 8091)
run_service "ai-service" "mvn -pl careflow-ai-service spring-boot:run -Dspring-boot.run.profiles=remote"

# 10. Doctor Web Frontend (Port 3000)
echo "--> Launching Doctor Web Frontend..."
if [ -d "frontend/doctor-web" ]; then
    (cd frontend/doctor-web && npm run dev > "$LOG_DIR/logs_frontend.log" 2>&1) &
    SERVICE_PIDS+=($!)
fi

cleanup() {
    echo ""
    echo "--> Stopping CareFlow Appointment & AI cluster..."
    for pid in "${SERVICE_PIDS[@]}"; do
        pkill -9 -P "$pid" 2>/dev/null || true
        kill -9 "$pid" 2>/dev/null || true
    done
    pkill -9 -f "spring-boot:run" 2>/dev/null || true
    pkill -9 -f "careflow" 2>/dev/null || true
    pkill -9 -f "next-server" 2>/dev/null || true
    echo "--> Stopped. Goodbye!"
    exit 0
}

trap cleanup SIGINT SIGTERM EXIT

echo "================================================================="
echo " CareFlow Cluster Started Successfully!                          "
echo " - Eureka Server:             http://localhost:8761              "
echo " - API Gateway:               http://localhost:8080              "
echo " - Identity Service:          http://localhost:8081              "
echo " - Appointment Service:       http://localhost:8083              "
echo " - AI Service (Gemini API):   http://localhost:8091              "
echo " - Doctor Web Frontend:       http://localhost:3000              "
echo "================================================================="
echo " Tailing logs. Press [Ctrl + C] to STOP all services.            "
echo "================================================================="

touch "$LOG_DIR/logs_eureka-server.log" "$LOG_DIR/logs_api-gateway.log" "$LOG_DIR/logs_identity-service.log" "$LOG_DIR/logs_appointment-service.log" "$LOG_DIR/logs_ai-service.log" "$LOG_DIR/logs_frontend.log"

tail -f "$LOG_DIR/logs_eureka-server.log" -f "$LOG_DIR/logs_api-gateway.log" -f "$LOG_DIR/logs_identity-service.log" -f "$LOG_DIR/logs_appointment-service.log" -f "$LOG_DIR/logs_ai-service.log" -f "$LOG_DIR/logs_frontend.log"
