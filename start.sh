#!/bin/bash

echo "================================================================="
echo "     CareFlow Development Services Starter (Prescription Feat)   "
echo "================================================================="

# 1. Start Infrastructure (Assumes Postgres and RabbitMQ are already running locally)
echo "--> Infrastructure checks: assuming local Postgres & RabbitMQ are running..."

# Array to keep track of background service PIDs
declare -a SERVICE_PIDS

# 2. Helper to run mvn commands in background
run_service() {
    local service_name=$1
    local cmd=$2
    echo "--> Launching $service_name..."
    eval "$cmd" > "logs_${service_name}.log" 2>&1 &
    SERVICE_PIDS+=($!)
}

# 3. Start Eureka Server (Port 8761)
run_service "eureka-server" "mvn -pl careflow-eureka-server spring-boot:run"
echo "Waiting 12 seconds for Eureka Server to register..."
sleep 12

# 4. Start API Gateway (Port 8080)
run_service "api-gateway" "mvn -pl careflow-api-gateway spring-boot:run"

# 5. Start Consultation Service (Port 8086)
run_service "consultation-service" "mvn -pl careflow-consultation-service spring-boot:run"

# 6. Start Prescription Service (Port 8087)
run_service "prescription-service" "mvn -pl careflow-prescription-service spring-boot:run"

# 7. Start Frontend Web (Port 3000)
echo "--> Launching Doctor Web Frontend..."
cd frontend/doctor-web
npm run dev > ../../logs_frontend.log 2>&1 &
SERVICE_PIDS+=($!)
cd ../..

# Cleanup function to kill all started processes on exit
cleanup() {
    echo ""
    echo "--> Stopping all CareFlow services..."
    for pid in "${SERVICE_PIDS[@]}"; do
        if kill -0 "$pid" 2>/dev/null; then
            # Kill process group to make sure child processes (like node under npm) are stopped
            kill -TERM -"$pid" 2>/dev/null || kill "$pid" 2>/dev/null
        fi
    done
    # Dọn dẹp triệt để Java và Node
    killall java node 2>/dev/null
    echo "--> Services stopped. Goodbye!"
    exit 0
}

# Trap Ctrl+C (SIGINT) and terminate (SIGTERM)
trap cleanup SIGINT SIGTERM

echo "================================================================="
echo " All services have been launched!                                "
echo " - Eureka Server:         http://localhost:8761                  "
echo " - API Gateway:           http://localhost:8080                  "
echo " - Doctor Web Frontend:   http://localhost:3000                  "
echo "================================================================="
echo " Tailing logs in real-time. Press [Ctrl + C] to STOP all services. "
echo "================================================================="

# Create empty log files if they don't exist yet to avoid tail errors
touch logs_eureka-server.log logs_api-gateway.log logs_consultation-service.log logs_prescription-service.log logs_frontend.log

# Tail all logs
tail -f logs_eureka-server.log -f logs_api-gateway.log -f logs_consultation-service.log -f logs_prescription-service.log -f logs_frontend.log
