#!/bin/bash

echo "================================================================="
echo "     CareFlow Ecosystem Services Starter (Connected Mode)        "
echo "================================================================="

# 1. Check working directory
SCRIPT_DIR="$( cd "$( dirname "${BASH_SOURCE[0]}" )" && pwd )"
cd "$SCRIPT_DIR" || exit 1

echo "--> Infrastructure checks: assuming Postgres & RabbitMQ are running..."

# Array to keep track of background service PIDs
declare -a SERVICE_PIDS

# 2. Helper to run mvn commands in background
LOG_DIR="$SCRIPT_DIR/logs"
mkdir -p "$LOG_DIR"

run_service() {
    local service_name=$1
    local cmd=$2
    echo "--> Launching $service_name..."
    eval "$cmd" > "$LOG_DIR/logs_${service_name}.log" 2>&1 &
    SERVICE_PIDS+=($!)
}

# 3. Start Eureka Server (Port 8761)
run_service "eureka-server" "mvn -pl careflow-eureka-server spring-boot:run"
echo "Waiting 12 seconds for Eureka Server to register..."
sleep 12

# 4. Start API Gateway (Port 8080)
run_service "api-gateway" "mvn -pl careflow-api-gateway spring-boot:run"

# 5. Start Identity Service (Port 8081)
run_service "identity-service" "mvn -pl careflow-identity-service spring-boot:run -Dspring-boot.run.profiles=remote"

# 6. Start Patient Service (Port 8082)
run_service "patient-service" "mvn -pl careflow-patient-service spring-boot:run -Dspring-boot.run.profiles=remote"

# 7. Start Appointment Service (Port 8083)
run_service "appointment-service" "mvn -pl careflow-appointment-service spring-boot:run -Dspring-boot.run.profiles=remote"

# 8. Start Queue Service (Port 8084)
run_service "queue-service" "mvn -pl careflow-queue-service spring-boot:run -Dspring-boot.run.profiles=remote"

# 9. Start Notification Service (Port 8085)
run_service "notification-service" "mvn -pl careflow-notification-service spring-boot:run -Dspring-boot.run.profiles=remote"

# 10. Start Consultation Service (Port 8086)
run_service "consultation-service" "mvn -pl careflow-consultation-service spring-boot:run -Dspring-boot.run.profiles=remote"

# 11. Start Prescription Service (Port 8087)
run_service "prescription-service" "mvn -pl careflow-prescription-service spring-boot:run -Dspring-boot.run.profiles=remote"

# 12. Start EMR Service (Port 8088)
run_service "emr-service" "mvn -pl careflow-emr-service spring-boot:run -Dspring-boot.run.profiles=remote"

# 13. Start Lab Service (Port 8089)
run_service "lab-service" "mvn -pl careflow-lab-service spring-boot:run -Dspring-boot.run.profiles=remote"

# 14. Start Hospital Directory Service (Port 8090)
run_service "hospital-directory-service" "mvn -pl careflow-hospital-directory-service spring-boot:run -Dspring-boot.run.profiles=remote"

# 15. Start AI Service (Port 8091)
run_service "ai-service" "mvn -pl careflow-ai-service spring-boot:run -Dspring-boot.run.profiles=remote"

# 16. Start Doctor Web Frontend (Port 3000)
echo "--> Launching Doctor Web Frontend..."
if [ -d "frontend/doctor-web" ]; then
    (cd frontend/doctor-web && npm run dev > "$LOG_DIR/logs_frontend.log" 2>&1) &
    SERVICE_PIDS+=($!)
fi

# Cleanup function to FORCE KILL all started processes and Java/Node children on exit
cleanup() {
    echo ""
    echo "--> Stopping all CareFlow services and child processes..."
    for pid in "${SERVICE_PIDS[@]}"; do
        pkill -9 -P "$pid" 2>/dev/null || true
        kill -9 "$pid" 2>/dev/null || true
    done
    pkill -9 -f "spring-boot:run" 2>/dev/null || true
    pkill -9 -f "careflow" 2>/dev/null || true
    pkill -9 -f "next-server" 2>/dev/null || true
    echo "--> All CareFlow services stopped cleanly. Goodbye!"
    exit 0
}

# Trap Ctrl+C (SIGINT), terminate (SIGTERM), and shell exit (EXIT)
trap cleanup SIGINT SIGTERM EXIT

echo "================================================================="
echo " All services have been launched!                                "
echo " - Eureka Server:             http://localhost:8761              "
echo " - API Gateway:               http://localhost:8080              "
echo " - Identity Service:          http://localhost:8081              "
echo " - Patient Service:           http://localhost:8082              "
echo " - Appointment Service:       http://localhost:8083              "
echo " - Queue Service:             http://localhost:8084              "
echo " - Notification Service:      http://localhost:8085              "
echo " - Consultation Service:      http://localhost:8086              "
echo " - Prescription Service:      http://localhost:8087              "
echo " - EMR Service:               http://localhost:8088              "
echo " - Lab Service:               http://localhost:8089              "
echo " - Hospital Directory Service:http://localhost:8090              "
echo " - AI Service:                http://localhost:8091              "
echo " - Doctor Web Frontend:       http://localhost:3000              "
echo "================================================================="
echo " Tailing logs in real-time. Press [Ctrl + C] to STOP all services. "
echo "================================================================="

# Create empty log files if they don't exist yet to avoid tail errors
touch "$LOG_DIR/logs_eureka-server.log" "$LOG_DIR/logs_api-gateway.log" "$LOG_DIR/logs_identity-service.log" "$LOG_DIR/logs_patient-service.log" "$LOG_DIR/logs_appointment-service.log" "$LOG_DIR/logs_queue-service.log" "$LOG_DIR/logs_notification-service.log" "$LOG_DIR/logs_consultation-service.log" "$LOG_DIR/logs_prescription-service.log" "$LOG_DIR/logs_emr-service.log" "$LOG_DIR/logs_lab-service.log" "$LOG_DIR/logs_hospital-directory-service.log" "$LOG_DIR/logs_ai-service.log" "$LOG_DIR/logs_frontend.log"

# Tail all logs
tail -f "$LOG_DIR/logs_eureka-server.log" -f "$LOG_DIR/logs_api-gateway.log" -f "$LOG_DIR/logs_identity-service.log" -f "$LOG_DIR/logs_patient-service.log" -f "$LOG_DIR/logs_appointment-service.log" -f "$LOG_DIR/logs_queue-service.log" -f "$LOG_DIR/logs_notification-service.log" -f "$LOG_DIR/logs_consultation-service.log" -f "$LOG_DIR/logs_prescription-service.log" -f "$LOG_DIR/logs_emr-service.log" -f "$LOG_DIR/logs_lab-service.log" -f "$LOG_DIR/logs_hospital-directory-service.log" -f "$LOG_DIR/logs_ai-service.log" -f "$LOG_DIR/logs_frontend.log"
