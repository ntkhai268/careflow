package com.careflow.common.constants;

public final class AppConstants {
    private AppConstants() {}

    // RabbitMQ exchanges
    public static final String EXCHANGE_APPOINTMENT = "appointment.exchange";
    public static final String EXCHANGE_QUEUE = "queue.exchange";
    public static final String EXCHANGE_NOTIFICATION = "notification.exchange";
    public static final String EXCHANGE_PRESCRIPTION = "prescription.exchange";
    public static final String EXCHANGE_CONSULTATION = "consultation.exchange";
    public static final String EXCHANGE_LAB = "lab.exchange";

    // RabbitMQ routing keys
    public static final String RK_APPOINTMENT_CREATED = "appointment.created";
    public static final String RK_APPOINTMENT_CONFIRMED = "appointment.confirmed";
    public static final String RK_APPOINTMENT_CANCELLED = "appointment.cancelled";
    public static final String RK_QUEUE_NUMBER_ASSIGNED = "queue.number.assigned";
    public static final String RK_QUEUE_CHECKED_IN = "queue.checked-in";
    public static final String RK_QUEUE_CALLED = "queue.called";
    public static final String RK_QUEUE_MISSED = "queue.missed";
    public static final String RK_QUEUE_STARTED = "queue.started";
    public static final String RK_QUEUE_COMPLETED = "queue.completed";
    public static final String RK_QUEUE_NEAR_TURN = "queue.near-turn";
    public static final String RK_PRESCRIPTION_CREATED = "prescription.created";
    public static final String RK_CONSULTATION_COMPLETED = "consultation.completed";
    public static final String RK_PRESCRIPTION_ISSUED = "prescription.issued";
    public static final String RK_PRESCRIPTION_CANCELLED = "prescription.cancelled";
    public static final String RK_PRESCRIPTION_DISPENSED = "prescription.dispensed";
    public static final String RK_LAB_ALL_REQUIRED_RESULTS_AVAILABLE = "lab.results.all-required-available";
    public static final String RK_NOTIFICATION_SEND = "notification.send";

    // Roles
    public static final String ROLE_PATIENT = "PATIENT";
    public static final String ROLE_DOCTOR = "DOCTOR";
    public static final String ROLE_STAFF = "STAFF";
    public static final String ROLE_LAB_TECHNICIAN = "LAB_TECHNICIAN";
    public static final String ROLE_ADMIN = "ADMIN";

    public static final String HEADER_USER_ID = "X-User-Id";
    public static final String HEADER_USER_ROLE = "X-User-Role";
    public static final String HEADER_CORRELATION_ID = "X-Correlation-Id";
    public static final String HEADER_IDEMPOTENCY_KEY = "Idempotency-Key";
}
