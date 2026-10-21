CREATE TABLE lab_service_catalog (
 service_code varchar(50) PRIMARY KEY,
 service_name varchar(255) NOT NULL,
 service_point_id varchar(100) NOT NULL,
 preparation_instruction varchar(500),
 is_active boolean NOT NULL DEFAULT true
);
