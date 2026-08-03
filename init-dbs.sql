-- Script tự động chạy khi container PostgreSQL được khởi tạo lần đầu tiên
-- Tạo database riêng cho từng microservice

CREATE DATABASE careflow_identity;
CREATE DATABASE careflow_patient;
CREATE DATABASE careflow_appointment;
CREATE DATABASE careflow_queue;
CREATE DATABASE careflow_consultation;
CREATE DATABASE careflow_prescription;
CREATE DATABASE careflow_emr;
CREATE DATABASE careflow_lab;
CREATE DATABASE careflow_notification;
