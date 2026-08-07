#!/bin/bash

echo "================================================================="
echo "     CareFlow Ecosystem Services Starter (LOCAL Profile Mode)    "
echo "================================================================="

# Export local DB env vars
export DB_HOST=localhost
export DB_PORT=5432
export DB_USERNAME=careflow
export DB_PASSWORD=careflow

echo "--> Connecting to Local PostgreSQL at ${DB_HOST}:${DB_PORT} (User: ${DB_USERNAME})..."

# Array to keep track of background service PIDs
declare -a SERVICE_PIDS

# Helper to run mvn commands in background
run_service() {
    local service_name=$1
    local cmd=$2
    echo "--> Launching $service_name (Profile: local)..."
    eval "$cmd" > "logs_${service_name}.log" 2>&1 &
    SERVICE_PIDS+=($!)
}

# 1. Start Eureka Server (Port 8761)
run_service "eureka-server" "mvn -pl careflow-eureka-server spring-boot:run"
echo "Waiting 12 seconds for Eureka Server to initialize..."
sleep 12

# 2. Start API Gateway (Port 8080)
run_service "api-gateway" "mvn -pl careflow-api-gateway spring-boot:run -Dspring-boot.run.profiles=local"

# 3. Start Identity Service (Port 8081)
run_service "identity-service" "mvn -pl careflow-identity-service spring-boot:run -Dspring-boot.run.profiles=local"

# 4. Start Patient Service (Port 8082)
run_service "patient-service" "mvn -pl careflow-patient-service spring-boot:run -Dspring-boot.run.profiles=local"

# 5. Start Appointment Service (Port 8083)
run_service "appointment-service" "mvn -pl careflow-appointment-service spring-boot:run -Dspring-boot.run.profiles=local"

# 6. Start Consultation Service (Port 8086)
run_service "consultation-service" "mvn -pl careflow-consultation-service spring-boot:run -Dspring-boot.run.profiles=local"

# 7. Start Prescription Service (Port 8087)
run_service "prescription-service" "mvn -pl careflow-prescription-service spring-boot:run -Dspring-boot.run.profiles=local"

# 8. Start Queue Service (Port 8084)
run_service "queue-service" "mvn -pl careflow-queue-service spring-boot:run -Dspring-boot.run.profiles=local"

# 9. Start Notification Service (Port 8085)
run_service "notification-service" "mvn -pl careflow-notification-service spring-boot:run -Dspring-boot.run.profiles=local"

# 10. Start Consultation Service (Port 8086)
run_service "consultation-service" "mvn -pl careflow-consultation-service spring-boot:run -Dspring-boot.run.profiles=local"

# 11. Start Prescription Service (Port 8087)
run_service "prescription-service" "mvn -pl careflow-prescription-service spring-boot:run -Dspring-boot.run.profiles=local"

# 12. Start EMR Service (Port 8088)
run_service "emr-service" "mvn -pl careflow-emr-service spring-boot:run -Dspring-boot.run.profiles=local"

# 13. Start Lab Service (Port 8089)
run_service "lab-service" "mvn -pl careflow-lab-service spring-boot:run -Dspring-boot.run.profiles=local"

# 14. Start Hospital Directory Service (Port 8090)
run_service "hospital-directory-service" "mvn -pl careflow-hospital-directory-service spring-boot:run -Dspring-boot.run.profiles=local"

# 15. Start AI Service (Port 8091)
run_service "ai-service" "mvn -pl careflow-ai-service spring-boot:run -Dspring-boot.run.profiles=local"

# 16. Start Frontend Web (Port 3000)
echo "--> Launching Doctor Web Frontend..."
cd frontend/doctor-web
npm run dev > "../../logs_frontend.log" 2>&1 &
SERVICE_PIDS+=($!)
cd ../..

# Cleanup function to FORCE KILL all started processes and Java/Node children on exit
cleanup() {
    echo ""
    echo "--> Stopping all CareFlow Local services..."
    for pid in "${SERVICE_PIDS[@]}"; do
        pkill -9 -P "$pid" 2>/dev/null || true
        kill -9 "$pid" 2>/dev/null || true
    done
    pkill -9 -f "spring-boot:run" 2>/dev/null || true
    pkill -9 -f "careflow" 2>/dev/null || true
    pkill -9 -f "next-server" 2>/dev/null || true
    killall -9 java node 2>/dev/null || true
    echo "--> All CareFlow Local services stopped cleanly. Goodbye!"
    exit 0
}

# Trap Ctrl+C (SIGINT), terminate (SIGTERM), and shell exit (EXIT)
trap cleanup SIGINT SIGTERM EXIT

echo "================================================================="
echo " All Local services have been launched!                          "
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
touch logs_eureka-server.log logs_api-gateway.log logs_identity-service.log logs_patient-service.log logs_appointment-service.log logs_queue-service.log logs_notification-service.log logs_consultation-service.log logs_prescription-service.log logs_emr-service.log logs_lab-service.log logs_hospital-directory-service.log logs_ai-service.log logs_frontend.log

# Tail all logs
tail -f logs_eureka-server.log -f logs_api-gateway.log -f logs_identity-service.log -f logs_patient-service.log -f logs_appointment-service.log -f logs_queue-service.log -f logs_notification-service.log -f logs_consultation-service.log -f logs_prescription-service.log -f logs_emr-service.log -f logs_lab-service.log -f logs_hospital-directory-service.log -f logs_ai-service.log -f logs_frontend.log
