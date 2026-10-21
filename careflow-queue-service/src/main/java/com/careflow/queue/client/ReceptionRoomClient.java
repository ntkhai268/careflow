package com.careflow.queue.client;

import com.careflow.common.dto.ApiResponse;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import java.util.List;

@FeignClient(name = "hospital-directory-service", contextId = "queueReceptionRooms", path = "/api/directory")
public interface ReceptionRoomClient {
    @GetMapping("/rooms")
    ApiResponse<List<ReceptionRoom>> rooms();

    @GetMapping("/departments/{code}")
    ApiResponse<Department> department(@org.springframework.web.bind.annotation.PathVariable("code") String code);
    @GetMapping("/services/{code}")
    ApiResponse<Service> service(@org.springframework.web.bind.annotation.PathVariable("code") String code);
    record Service(String code,boolean active){}
    record Department(java.util.UUID id,String code,String name,Boolean isActive){}
    record ReceptionRoom(String id,String roomType,Boolean isActive,String departmentCode){
        public ReceptionRoom(String id,String roomType,Boolean isActive){this(id,roomType,isActive,null);}
    }
}
