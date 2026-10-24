#!/bin/bash

echo "================================================================="
echo "     CareFlow Ecosystem Services Starter (OPTIMIZED LOCAL)      "
echo "================================================================="

# 1. Check working directory
SCRIPT_DIR="$( cd "$( dirname "${BASH_SOURCE[0]}" )" && pwd )"
cd "$SCRIPT_DIR" || exit 1

# Load AI keys from careflow-ai-service/.env if present
if [ -f "$SCRIPT_DIR/careflow-ai-service/.env" ]; then
    echo "--> Loading AI API keys from careflow-ai-service/.env..."
    set -a
    source "$SCRIPT_DIR/careflow-ai-service/.env"
    set +a
elif [ -f "$SCRIPT_DIR/.env" ]; then
    echo "--> Loading environment variables from .env..."
    set -a
    source "$SCRIPT_DIR/.env"
    set +a
fi

echo "--> Infrastructure checks: assuming Local Postgres (localhost:5432) & RabbitMQ (localhost:5672) are running..."

# JVM Memory Optimization Flag (Giới hạn 256MB RAM tối đa, bật TieredStopAtLevel=1 để khởi động siêu nhanh)
JVM_OPTS="-Xms64m -Xmx256m -XX:+TieredCompilation -XX:TieredStopAtLevel=1"
MAVEN_JVM_FLAG="-Dspring-boot.run.jvmArguments=\"$JVM_OPTS\""

# Array to keep track of background service PIDs and log files
declare -a SERVICE_PIDS
declare -a ACTIVE_LOGS

LOG_DIR="$SCRIPT_DIR/logs"
mkdir -p "$LOG_DIR"

run_service() {
    local service_name=$1
    local cmd=$2
    local delay=${3:-2} # Độ trễ mặc định 2 giây giữa các service để tránh nghẽn CPU
    echo "--> Launching $service_name (Max RAM: 256MB)..."
    touch "$LOG_DIR/logs_${service_name}.log"
    ACTIVE_LOGS+=("$LOG_DIR/logs_${service_name}.log")
    eval "$cmd" > "$LOG_DIR/logs_${service_name}.log" 2>&1 &
    SERVICE_PIDS+=($!)
    sleep "$delay"
}

# 2. Chọn Mode khởi chạy (Cách 3: Chạy theo cụm phân hệ)
MODE="${1:-}"

if [ -z "$MODE" ]; then
    echo ""
    echo "Chọn cụm phân hệ muốn khởi chạy để tiết kiệm RAM & CPU:"
    echo "  1) ALL          - Toàn bộ 13 services + Frontend (Dành cho máy >= 16GB RAM rảnh)"
    echo "  2) PRESCRIPTION - Kê đơn thuốc (Gateway, Identity, Patient, Consultation, Prescription, Directory, Frontend)"
    echo "  3) APPOINTMENT  - Đặt khám & Hàng đợi (Gateway, Identity, Patient, Appointment, Queue, Directory, AI, Frontend)"
    echo "  4) EMR & LAB    - Bệnh án & Xét nghiệm (Gateway, Identity, Patient, Consultation, EMR, Lab, Directory, Frontend)"
    echo "  5) CORE ONLY    - Cốt lõi (Eureka, Gateway, Identity, Patient, Frontend)"
    echo "  6) AI ONLY      - Trợ lý AI Lâm sàng (Gateway, Identity, Patient, Consultation, Directory, AI, Frontend)"
    echo ""
    read -r -p "Nhập lựa chọn của bạn [1-6] (Mặc định: 1 sau 5s): " -t 5 USER_CHOICE || USER_CHOICE="1"
    echo ""

    case "$USER_CHOICE" in
        2) MODE="prescription" ;;
        3) MODE="appointment" ;;
        4) MODE="emr-lab" ;;
        5) MODE="core" ;;
        6) MODE="ai" ;;
        *) MODE="all" ;;
    esac
fi

echo "--> Đang khởi chạy với chế độ: [${MODE^^}]"
echo "================================================================="

# --- 1. ALWAYS START EUREKA SERVER FIRST ---
run_service "eureka-server" "mvn -pl careflow-eureka-server spring-boot:run $MAVEN_JVM_FLAG" 8

# --- 2. ALWAYS START API GATEWAY ---
run_service "api-gateway" "mvn -pl careflow-api-gateway spring-boot:run $MAVEN_JVM_FLAG" 3

# --- 3. ALWAYS START IDENTITY SERVICE ---
run_service "identity-service" "mvn -pl careflow-identity-service spring-boot:run -Dspring-boot.run.profiles=local $MAVEN_JVM_FLAG" 2

# --- 4. ALWAYS START PATIENT SERVICE ---
run_service "patient-service" "mvn -pl careflow-patient-service spring-boot:run -Dspring-boot.run.profiles=local $MAVEN_JVM_FLAG" 2

# --- 5. CONDITIONAL SERVICES BASED ON MODE ---
case "$MODE" in
    "ai")
        run_service "hospital-directory-service" "mvn -pl careflow-hospital-directory-service spring-boot:run -Dspring-boot.run.profiles=local $MAVEN_JVM_FLAG" 2
        run_service "queue-service" "mvn -pl careflow-queue-service spring-boot:run -Dspring-boot.run.profiles=local $MAVEN_JVM_FLAG" 2
        run_service "consultation-service" "mvn -pl careflow-consultation-service spring-boot:run -Dspring-boot.run.profiles=local $MAVEN_JVM_FLAG" 2
        run_service "ai-service" "mvn -pl careflow-ai-service spring-boot:run -Dspring-boot.run.profiles=local $MAVEN_JVM_FLAG" 2
        ;;
    "prescription")
        run_service "hospital-directory-service" "mvn -pl careflow-hospital-directory-service spring-boot:run -Dspring-boot.run.profiles=local $MAVEN_JVM_FLAG" 2
        run_service "consultation-service" "mvn -pl careflow-consultation-service spring-boot:run -Dspring-boot.run.profiles=local $MAVEN_JVM_FLAG" 2
        run_service "prescription-service" "mvn -pl careflow-prescription-service spring-boot:run -Dspring-boot.run.profiles=local $MAVEN_JVM_FLAG" 2
        ;;
    "appointment")
        run_service "hospital-directory-service" "mvn -pl careflow-hospital-directory-service spring-boot:run -Dspring-boot.run.profiles=local $MAVEN_JVM_FLAG" 2
        run_service "queue-service" "mvn -pl careflow-queue-service spring-boot:run -Dspring-boot.run.profiles=local $MAVEN_JVM_FLAG" 2
        run_service "appointment-service" "mvn -pl careflow-appointment-service spring-boot:run -Dspring-boot.run.profiles=local $MAVEN_JVM_FLAG" 2
        run_service "ai-service" "mvn -pl careflow-ai-service spring-boot:run -Dspring-boot.run.profiles=local $MAVEN_JVM_FLAG" 2
        ;;
    "emr-lab")
        run_service "hospital-directory-service" "mvn -pl careflow-hospital-directory-service spring-boot:run -Dspring-boot.run.profiles=local $MAVEN_JVM_FLAG" 2
        run_service "consultation-service" "mvn -pl careflow-consultation-service spring-boot:run -Dspring-boot.run.profiles=local $MAVEN_JVM_FLAG" 2
        run_service "emr-service" "mvn -pl careflow-emr-service spring-boot:run -Dspring-boot.run.profiles=local $MAVEN_JVM_FLAG" 2
        run_service "lab-service" "mvn -pl careflow-lab-service spring-boot:run -Dspring-boot.run.profiles=local $MAVEN_JVM_FLAG" 2
        ;;
    "core")
        # Only Eureka + Gateway + Identity + Patient
        ;;
    "all"|*)
        run_service "hospital-directory-service" "mvn -pl careflow-hospital-directory-service spring-boot:run -Dspring-boot.run.profiles=local $MAVEN_JVM_FLAG" 2
        run_service "queue-service" "mvn -pl careflow-queue-service spring-boot:run -Dspring-boot.run.profiles=local $MAVEN_JVM_FLAG" 2
        run_service "appointment-service" "mvn -pl careflow-appointment-service spring-boot:run -Dspring-boot.run.profiles=local $MAVEN_JVM_FLAG" 2
        run_service "consultation-service" "mvn -pl careflow-consultation-service spring-boot:run -Dspring-boot.run.profiles=local $MAVEN_JVM_FLAG" 2
        run_service "prescription-service" "mvn -pl careflow-prescription-service spring-boot:run -Dspring-boot.run.profiles=local $MAVEN_JVM_FLAG" 2
        run_service "emr-service" "mvn -pl careflow-emr-service spring-boot:run -Dspring-boot.run.profiles=local $MAVEN_JVM_FLAG" 2
        run_service "lab-service" "mvn -pl careflow-lab-service spring-boot:run -Dspring-boot.run.profiles=local $MAVEN_JVM_FLAG" 2
        run_service "notification-service" "mvn -pl careflow-notification-service spring-boot:run -Dspring-boot.run.profiles=local $MAVEN_JVM_FLAG" 2
        run_service "ai-service" "mvn -pl careflow-ai-service spring-boot:run -Dspring-boot.run.profiles=local $MAVEN_JVM_FLAG" 2
        ;;
esac

# --- 6. START DOCTOR WEB FRONTEND ---
echo "--> Launching Doctor Web Frontend..."
if [ -d "frontend/doctor-web" ]; then
    touch "$LOG_DIR/logs_frontend.log"
    ACTIVE_LOGS+=("$LOG_DIR/logs_frontend.log")
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

# --- 7. READINESS HEALTH PROBE ---
echo ""
echo "================================================================="
echo "--> Đang kiểm tra trạng thái khởi động của các dịch vụ cốt lõi..."
echo "================================================================="

wait_for_service() {
    local name=$1
    local url=$2
    local timeout=${3:-45}
    local interval=2
    local elapsed=0

    printf "  ⏳ Đang chờ %-22s " "$name..."
    while [ "$elapsed" -lt "$timeout" ]; do
        local code
        code=$(curl -s -o /dev/null -w "%{http_code}" "$url" 2>/dev/null || echo "000")
        if [ "$code" = "200" ] || [ "$code" = "401" ] || [ "$code" = "404" ] || [ "$code" = "307" ]; then
            echo "[ SẴN SÀNG ]"
            return 0
        fi
        sleep "$interval"
        elapsed=$((elapsed + interval))
        printf "."
    done
    echo "[ TIMEOUT ] (Dịch vụ vẫn đang tiếp tục khởi động ngầm)"
    return 1
}

# Wait for Eureka Server
wait_for_service "Eureka Server" "http://localhost:8761/actuator/health" 30

# Wait for API Gateway
wait_for_service "API Gateway" "http://localhost:8080/actuator/health" 35

# Wait for Identity Service
wait_for_service "Identity Service" "http://localhost:8081/actuator/health" 35

# Wait for Frontend
wait_for_service "Doctor Web Frontend" "http://localhost:3000" 30

echo ""
echo "================================================================="
echo "🎉 HỆ THỐNG ĐÃ SẴN SÀNG! [${MODE^^}]                             "
echo " - Eureka Server:             http://localhost:8761              "
echo " - API Gateway:               http://localhost:8080              "
echo " - Identity Service:          http://localhost:8081              "
echo " - Doctor Web Frontend:       http://localhost:3000              "
echo "                                                                 "
echo " 👉 Đăng nhập Bác sĩ mẫu:                                        "
echo "    Email:    doctor@careflow.com                                "
echo "    Mật khẩu: Password123@                                       "
echo "================================================================="
echo " Tailing active logs in real-time. Nhấn [Ctrl + C] để DỪNG.       "
echo "================================================================="

tail -f "${ACTIVE_LOGS[@]}"
