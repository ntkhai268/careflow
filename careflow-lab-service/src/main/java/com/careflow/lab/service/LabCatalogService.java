package com.careflow.lab.service;

import com.careflow.common.exception.BusinessException;
import com.careflow.lab.dto.request.LabOrderItemRequest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import java.util.List;

@Service
public class LabCatalogService {
    private final JdbcTemplate jdbc;
    public LabCatalogService(JdbcTemplate jdbc) { this.jdbc=jdbc; }
    public record Definition(String serviceCode,String serviceName,String servicePointId,String preparationInstruction) {}
    public List<Definition> list() {
        return jdbc.query("SELECT service_code,service_name,service_point_id,preparation_instruction FROM lab_service_catalog WHERE is_active=true ORDER BY service_code",
                (rs,n)->new Definition(rs.getString(1),rs.getString(2),rs.getString(3),rs.getString(4)));
    }
    public Definition requireItem(LabOrderItemRequest item) {
        return list().stream().filter(d->d.serviceCode().equals(item.serviceCode()) && d.servicePointId().equals(item.servicePointId()))
                .findFirst().orElseThrow(()->new BusinessException(422,"Dịch vụ CLS không có trong danh mục đang hoạt động hoặc sai điểm thực hiện"));
    }
}
