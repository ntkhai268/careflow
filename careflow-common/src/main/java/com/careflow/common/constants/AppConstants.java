package com.careflow.common.constants;

public final class AppConstants {
    private AppConstants() {}

    // RabbitMQ exchanges
    public static final String EXCHANGE_APPOINTMENT = "appointment.exchange";
    public static final String EXCHANGE_QUEUE = "queue.exchange";
    public static final String EXCHANGE_NOTIFICATION = "notification.exchange";
    public static final String EXCHANGE_PRESCRIPTION = "prescription.exchange";

    // RabbitMQ routing keys
    public static final String RK_APPOINTMENT_CREATED = "appointment.created";
    public static final String RK_QUEUE_NUMBER_ASSIGNED = "queue.number.assigned";
    public static final String RK_QUEUE_CALLED = "queue.called";
    public static final String RK_QUEUE_MISSED = "queue.missed";
    public static final String RK_PRESCRIPTION_CREATED = "prescription.created";
    public static final String RK_NOTIFICATION_SEND = "notification.send";

    // Roles
    public static final String ROLE_PATIENT = "PATIENT";
    public static final String ROLE_DOCTOR = "DOCTOR";
    public static final String ROLE_ADMIN = "ADMIN";
}
