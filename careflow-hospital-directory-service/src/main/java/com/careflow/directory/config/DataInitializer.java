package com.careflow.directory.config;

import com.careflow.directory.model.Department;
import com.careflow.directory.model.DoctorProfile;
import com.careflow.directory.model.Room;
import com.careflow.directory.repository.DepartmentRepository;
import com.careflow.directory.repository.DoctorProfileRepository;
import com.careflow.directory.repository.RoomRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.CommandLineRunner;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.UUID;

@Component
@RequiredArgsConstructor
@Slf4j
public class DataInitializer implements CommandLineRunner {

    private final DepartmentRepository departmentRepository;
    private final RoomRepository roomRepository;
    private final DoctorProfileRepository doctorProfileRepository;

    @Override
    public void run(String... args) {
        seedDepartments();
        seedRooms();
        seedDoctorProfiles();
    }

    private void seedDepartments() {
        if (departmentRepository.count() == 0) {
            log.info("Seeding 11 standard hospital departments...");
            List<Department> departments = List.of(
                    Department.builder().code("NOI_TONG_QUAT").id(UUID.fromString("de000001-0000-0000-0000-000000000001")).name("Nội tổng quát").description("Khám lâm sàng nội khoa chung").isActive(true).build(),
                    Department.builder().code("NHI").id(UUID.fromString("de000001-0000-0000-0000-000000000002")).name("Nhi khoa").description("Khám và điều trị cho trẻ em").isActive(true).build(),
                    Department.builder().code("NGOAI").id(UUID.fromString("de000001-0000-0000-0000-000000000003")).name("Ngoại khoa").description("Khám ngoại khoa chung").isActive(true).build(),
                    Department.builder().code("SAN").id(UUID.fromString("de000001-0000-0000-0000-000000000004")).name("Sản phụ khoa").description("Khám thai và sản phụ khoa").isActive(true).build(),
                    Department.builder().code("MAT").id(UUID.fromString("de000001-0000-0000-0000-000000000005")).name("Mắt").description("Khám và chữa bệnh về mắt").isActive(true).build(),
                    Department.builder().code("TAI_MUI_HONG").id(UUID.fromString("de000001-0000-0000-0000-000000000006")).name("Tai mũi họng").description("Chuyên khoa tai mũi họng").isActive(true).build(),
                    Department.builder().code("RANG_HAM_MAT").id(UUID.fromString("de000001-0000-0000-0000-000000000007")).name("Răng hàm mặt").description("Nha khoa và nha chu").isActive(true).build(),
                    Department.builder().code("DA_LIEU").id(UUID.fromString("de000001-0000-0000-0000-000000000008")).name("Da liễu").description("Khám và điều trị bệnh ngoài da").isActive(true).build(),
                    Department.builder().code("THAN_KINH").id(UUID.fromString("de000001-0000-0000-0000-000000000009")).name("Thần kinh").description("Chuyên khoa thần kinh").isActive(true).build(),
                    Department.builder().code("TIM_MACH").id(UUID.fromString("de000001-0000-0000-0000-000000000010")).name("Tim mạch").description("Chuyên khoa tim mạch và mạch máu").isActive(true).build(),
                    Department.builder().code("CO_XUONG_KHOP").id(UUID.fromString("de000001-0000-0000-0000-000000000011")).name("Cơ xương khớp").description("Khám các bệnh lý khớp và cơ").isActive(true).build()
            );
            departmentRepository.saveAll(departments);
            log.info("Successfully seeded 11 departments.");
        }
    }

    private void seedRooms() {
        if (roomRepository.count() == 0) {
            log.info("Seeding standard hospital clinic rooms...");
            List<Room> rooms = List.of(
                    Room.builder().id("ROOM-01").departmentCode("NOI_TONG_QUAT").displayName("Phòng 01 - Nội tổng quát").roomType("CONSULTATION").isActive(true).build(),
                    Room.builder().id("ROOM-02").departmentCode("NHI").displayName("Phòng 02 - Nhi khoa").roomType("CONSULTATION").isActive(true).build(),
                    Room.builder().id("ROOM-03").departmentCode("NGOAI").displayName("Phòng 03 - Ngoại khoa").roomType("CONSULTATION").isActive(true).build(),
                    Room.builder().id("ROOM-04").departmentCode("SAN").displayName("Phòng 04 - Sản phụ khoa").roomType("CONSULTATION").isActive(true).build(),
                    Room.builder().id("ROOM-05").departmentCode("MAT").displayName("Phòng 05 - Mắt").roomType("CONSULTATION").isActive(true).build(),
                    Room.builder().id("ROOM-06").departmentCode("TAI_MUI_HONG").displayName("Phòng 06 - Tai mũi họng").roomType("CONSULTATION").isActive(true).build(),
                    Room.builder().id("ROOM-07").departmentCode("RANG_HAM_MAT").displayName("Phòng 07 - Răng hàm mặt").roomType("CONSULTATION").isActive(true).build(),
                    Room.builder().id("ROOM-08").departmentCode("DA_LIEU").displayName("Phòng 08 - Da liễu").roomType("CONSULTATION").isActive(true).build(),
                    Room.builder().id("ROOM-10").departmentCode("TIM_MACH").displayName("Phòng 10 - Tim mạch").roomType("CONSULTATION").isActive(true).build(),
                    Room.builder().id("ROOM-11").departmentCode("CO_XUONG_KHOP").displayName("Phòng 11 - Cơ xương khớp").roomType("CONSULTATION").isActive(true).build(),
                    Room.builder().id("LAB-01").departmentCode("NOI_TONG_QUAT").displayName("Khu lấy mẫu Xét nghiệm 01").roomType("LAB").isActive(true).build(),
                    Room.builder().id("PHARMACY-01").departmentCode("NOI_TONG_QUAT").displayName("Quầy phát thuốc N-01").roomType("PHARMACY").isActive(true).build()
            );
            roomRepository.saveAll(rooms);
            log.info("Successfully seeded 12 rooms.");
        }
    }

    private void seedDoctorProfiles() {
        if (doctorProfileRepository.count() == 0) {
            log.info("Seeding default doctor profiles...");
            List<DoctorProfile> doctors = List.of(
                    DoctorProfile.builder()
                            .id(UUID.fromString("d0000001-0000-0000-0000-000000000001"))
                            .userId(UUID.fromString("d0000001-0000-0000-0000-000000000001"))
                            .fullName("BS. CKI Nguyễn Văn An")
                            .title("BS. CKI")
                            .departmentCode("NOI_TONG_QUAT")
                            .assignedRoomId("ROOM-01")
                            .specialization("Nội tổng quát & Tim mạch")
                            .licenseNumber("CCHN-001234/HCM")
                            .isActive(true)
                            .build(),
                    DoctorProfile.builder()
                            .id(UUID.fromString("d0000002-0000-0000-0000-000000000002"))
                            .userId(UUID.fromString("11111111-1111-1111-1111-111111111111"))
                            .fullName("BS. Phạm Hoàng Nam")
                            .title("BS. CKI")
                            .departmentCode("NOI_TONG_QUAT")
                            .assignedRoomId("ROOM-01")
                            .specialization("Nội tiêu hóa")
                            .licenseNumber("CCHN-005678/HCM")
                            .isActive(true)
                            .build()
            );
            doctorProfileRepository.saveAll(doctors);
            log.info("Successfully seeded doctor profiles.");
        }
    }
}
