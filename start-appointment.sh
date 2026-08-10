#!/bin/bash

echo "================================================================="
echo "  CareFlow Lightweight Starter (Prescription & Queue Testing)     "
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

# Profile default là local (hoặc remote nếu truyền qua argument)
PROFILE="${1:-local}"
echo "--> Running with Spring Profile: $PROFILE"

declare -a SERVICE_PIDS

run_service() {
    local service_name=$1
    local cmd=$2
    echo "--> Launching $service_name..."
    eval "$cmd" > "$LOG_DIR/logs_${service_name}.log" 2>&1 &
    SERVICE_PIDS+=($!)
}

# 1. Eureka Server (Port 8761)
run_service "eureka-server" "mvn -pl careflow-eureka-server spring-boot:run -Dspring-boot.run.profiles=$PROFILE"
echo "Waiting 6 seconds for Eureka Server..."
sleep 6

# 2. API Gateway (Port 8080)
run_service "api-gateway" "mvn -pl careflow-api-gateway spring-boot:run -Dspring-boot.run.profiles=$PROFILE"

# 3. Identity Service (Port 8081)
run_service "identity-service" "mvn -pl careflow-identity-service spring-boot:run -Dspring-boot.run.profiles=$PROFILE"

# 4. Queue Service (Port 8084) - Cần để tạo lượt phát thuốc PHARMACY_DISPENSING & seed data
run_service "queue-service" "mvn -pl careflow-queue-service spring-boot:run -Dspring-boot.run.profiles=$PROFILE"

# 5. Prescription Service (Port 8087) - Service trọng tâm cần test
run_service "prescription-service" "mvn -pl careflow-prescription-service spring-boot:run -Dspring-boot.run.profiles=$PROFILE"

# 6. Doctor Web Frontend (Port 3000)
echo "--> Launching Doctor Web Frontend..."
if [ -d "frontend/doctor-web" ]; then
    (cd frontend/doctor-web && npm run dev > "$LOG_DIR/logs_frontend.log" 2>&1) &
    SERVICE_PIDS+=($!)
fi

cleanup() {
    echo ""
    echo "--> Stopping CareFlow services..."
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
echo " CareFlow Minimal Cluster Started Successfully!                  "
echo " - Profile:                   $PROFILE                           "
echo " - Eureka Server:             http://localhost:8761              "
echo " - API Gateway:               http://localhost:8080              "
echo " - Identity Service:          http://localhost:8081              "
echo " - Queue Service:             http://localhost:8084              "
echo " - Prescription Service:      http://localhost:8087              "
echo " - Doctor Web Frontend:       http://localhost:3000              "
echo "================================================================="
echo " Tailing logs. Press [Ctrl + C] to STOP all services.            "
echo "================================================================="

touch "$LOG_DIR/logs_eureka-server.log" "$LOG_DIR/logs_api-gateway.log" "$LOG_DIR/logs_identity-service.log" "$LOG_DIR/logs_queue-service.log" "$LOG_DIR/logs_prescription-service.log" "$LOG_DIR/logs_frontend.log"

tail -f "$LOG_DIR/logs_eureka-server.log" -f "$LOG_DIR/logs_api-gateway.log" -f "$LOG_DIR/logs_identity-service.log" -f "$LOG_DIR/logs_queue-service.log" -f "$LOG_DIR/logs_prescription-service.log" -f "$LOG_DIR/logs_frontend.log"

