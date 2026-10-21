package com.careflow.directory.service;

import com.careflow.common.exception.BusinessException;
import com.careflow.directory.dto.ServicePriceResponse;
import com.careflow.directory.dto.request.ServicePriceRequest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

@Service
public class ServiceCatalogService {
    private final JdbcTemplate jdbc;
    private static final RowMapper<ServicePriceResponse> ROW = (r, n) -> new ServicePriceResponse(
            r.getString("code"),r.getString("name"),r.getString("description"),r.getLong("amount"),
            r.getString("currency"),r.getLong("version"),r.getTimestamp("effective_at").toInstant(),
            r.getBoolean("active"),r.getInt("estimated_minutes"));
    public ServiceCatalogService(JdbcTemplate jdbc) { this.jdbc=jdbc; }

    @Transactional(readOnly=true)
    public List<ServicePriceResponse> list(boolean administration) {
        String where=administration?"":" WHERE effective_at<=CURRENT_TIMESTAMP";
        var rows=jdbc.query("SELECT DISTINCT ON (code) * FROM medical_service_prices"+where+" ORDER BY code,version DESC",ROW);
        return administration?rows:rows.stream().filter(ServicePriceResponse::active).toList();
    }

    @Transactional(readOnly=true)
    public ServicePriceResponse current(String code) {
        var rows=jdbc.query("SELECT * FROM medical_service_prices WHERE code=? AND effective_at<=CURRENT_TIMESTAMP ORDER BY version DESC LIMIT 1",ROW,code);
        if(rows.isEmpty()) throw new BusinessException(404,"Không tìm thấy dịch vụ có bảng giá hiệu lực.");
        var price=rows.getFirst();
        if(!price.active()) throw new BusinessException(409,"Dịch vụ đã ngừng nhận lịch.");
        return price;
    }

    @Transactional
    public ServicePriceResponse publish(ServicePriceRequest request,UUID actor) {
        // Serialize a service's append-only revisions, including its first revision.
        jdbc.queryForList("SELECT pg_advisory_xact_lock(hashtextextended(?,0))",request.code());
        Long previous=jdbc.queryForObject("SELECT COALESCE(MAX(version),0) FROM medical_service_prices WHERE code=?",Long.class,request.code());
        if(previous==null || previous!=request.expectedVersion()) throw new BusinessException(409,"Bảng giá đã thay đổi. Vui lòng tải lại trước khi lưu.");
        Instant effective=request.effectiveAt()==null?Instant.now():request.effectiveAt();
        if(request.effectiveAt()!=null && effective.isBefore(Instant.now().minusSeconds(5)))
            throw new BusinessException(400,"Không được công bố bảng giá lùi ngày.");
        jdbc.update("INSERT INTO medical_service_prices(id,code,name,description,amount,currency,version,effective_at,active,estimated_minutes,created_by) VALUES (?,?,?,?,?,'VND',?,?,?,?,?)",
                UUID.randomUUID(),request.code(),request.name().strip(),request.description(),request.amount(),previous+1,
                Timestamp.from(effective),request.active(),request.estimatedMinutes(),actor);
        return new ServicePriceResponse(request.code(),request.name().strip(),request.description(),request.amount(),"VND",previous+1,effective,request.active(),request.estimatedMinutes());
    }

    @Transactional
    public void seedDemo() {
        jdbc.update("INSERT INTO medical_service_prices(id,code,name,description,amount,currency,version,effective_at,active,estimated_minutes) VALUES (?,'GENERAL_CONSULTATION','Khám thường','Khám ban đầu với bác sĩ chuyên khoa',150000,'VND',1,'2026-01-01T00:00:00Z',true,15) ON CONFLICT (code,version) DO NOTHING",UUID.fromString("de100001-0000-0000-0000-000000000001"));
    }
}
