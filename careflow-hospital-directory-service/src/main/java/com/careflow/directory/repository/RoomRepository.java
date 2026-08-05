package com.careflow.directory.repository;

import com.careflow.directory.model.Room;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface RoomRepository extends JpaRepository<Room, String> {
    List<Room> findByIsActiveTrue();
    List<Room> findByDepartmentCodeAndIsActiveTrue(String departmentCode);
    List<Room> findByRoomTypeAndIsActiveTrue(String roomType);
}
