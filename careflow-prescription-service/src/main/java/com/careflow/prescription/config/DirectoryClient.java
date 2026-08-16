package com.careflow.prescription.config;

import com.careflow.common.dto.ApiResponse;
import com.careflow.common.exception.BusinessException;
import lombok.Data;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.web.client.RestTemplateBuilder;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.HttpMethod;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestTemplate;

import java.time.Duration;
import java.util.List;

@Component
@Slf4j
public class DirectoryClient {

    private final RestTemplate restTemplate;

    @Value("${app.directory-service.url:http://careflow-hospital-directory-service:8090}")
    private String directoryServiceUrl;

    public DirectoryClient(RestTemplateBuilder builder) {
        this.restTemplate = builder
                .setConnectTimeout(Duration.ofSeconds(2))
                .setReadTimeout(Duration.ofSeconds(3))
                .build();
    }

    public String getActivePharmacyServicePointId() {
        String url = directoryServiceUrl + "/api/directory/rooms?roomType=PHARMACY";
        try {
            ResponseEntity<ApiResponse<List<RoomResponse>>> response = restTemplate.exchange(
                    url,
                    HttpMethod.GET,
                    null,
                    new ParameterizedTypeReference<>() { });
            List<RoomResponse> rooms = response.getBody() == null ? null : response.getBody().getData();
            if (rooms != null) {
                return rooms.stream()
                        .filter(room -> Boolean.TRUE.equals(room.getIsActive()))
                        .map(RoomResponse::getId)
                        .filter(id -> id != null && !id.isBlank())
                        .findFirst()
                        .orElseThrow(() -> new BusinessException(503,
                                "Hospital Directory không có quầy phát thuốc đang hoạt động"));
            }
        } catch (BusinessException exception) {
            throw exception;
        } catch (Exception exception) {
            log.error("Cannot resolve active pharmacy service point from Hospital Directory", exception);
            throw new BusinessException(503, "Không thể lấy quầy phát thuốc từ Hospital Directory");
        }
        throw new BusinessException(503, "Hospital Directory trả về danh sách quầy thuốc rỗng");
    }

    @Data
    public static class RoomResponse {
        private String id;
        private String departmentCode;
        private String displayName;
        private String roomType;
        private Boolean isActive;
    }
}
