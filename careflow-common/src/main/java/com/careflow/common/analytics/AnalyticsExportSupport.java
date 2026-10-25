package com.careflow.common.analytics;
import com.careflow.common.exception.BusinessException;
import com.careflow.common.security.ServiceTransportIdentity;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.jdbc.core.JdbcTemplate;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.sql.*;
import java.time.*;
import java.util.*;
/** Source-owned read API; SQL fragments are constants supplied by the owning service. */
public final class AnalyticsExportSupport {
    private final JdbcTemplate jdbc;
    private final String key;
    private final boolean allowLoopback;
    public AnalyticsExportSupport(JdbcTemplate jdbc, String key, boolean allowLoopback) {
        this.jdbc=jdbc; this.key=key; this.allowLoopback=allowLoopback;
    }
    public AnalyticsExportPage read(HttpServletRequest request, UUID id, UUID after, int limit,
            AnalyticsSnapshot.Source source, String select) {
        ServiceTransportIdentity.require(request,"analytics-service",allowLoopback);
        String presented=request.getHeader("X-Careflow-Caller-Key");
        if (!"analytics-service".equals(request.getHeader("X-Careflow-Caller")) || key.length()<32 ||
            presented==null || !MessageDigest.isEqual(key.getBytes(StandardCharsets.UTF_8),presented.getBytes(StandardCharsets.UTF_8)))
            throw new BusinessException(403,"Analytics export chưa được cấp quyền.");
        if (limit<1 || limit>500 || (id!=null && after!=null)) throw new BusinessException(400,"Cursor hoặc giới hạn không hợp lệ.");
        List<Object> values=new ArrayList<>();String sql=select;
        if(id!=null){sql+=" AND t.id=?";values.add(id);}
        else if(after!=null){sql+=" AND t.id>?";values.add(after);}
        sql+=" ORDER BY t.id LIMIT ?";values.add(limit+1);
        var items=jdbc.query(sql,(rs,row)->new AnalyticsSnapshot(source,rs.getObject("id",UUID.class),rs.getLong("revision"),instant(rs,"updated_at"),rs.getObject("business_date",LocalDate.class),rs.getObject("department_id",UUID.class),rs.getString("department_code"),rs.getString("room_id"),rs.getString("status"),rs.getObject("appointment_id",UUID.class),rs.getObject("initial_entry_id",UUID.class),rs.getObject("consultation_id",UUID.class),rs.getString("phase"),instant(rs,"checked_in_at"),instant(rs,"eligible_since_at"),instant(rs,"started_at"),instant(rs,"completed_at"),rs.getInt("missed_count")),values.toArray());
        if(id!=null && items.isEmpty())throw new BusinessException(404,"Bản ghi nguồn không còn tồn tại.");
        UUID next=items.size()>limit?items.get(limit-1).id():null;
        return new AnalyticsExportPage(List.copyOf(items.subList(0,Math.min(limit,items.size()))),next,Instant.now());
    }
    private static Instant instant(ResultSet rs,String field)throws SQLException {
        var value=rs.getTimestamp(field);return value==null?null:value.toInstant();
    }
}
