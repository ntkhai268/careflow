package com.careflow.identity.security;

import com.careflow.common.exception.BusinessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import java.sql.Timestamp;
import java.time.Instant;

@Service
public class DpopReplayStore {
    private final JdbcTemplate jdbc;
    public DpopReplayStore(JdbcTemplate jdbc) { this.jdbc = jdbc; }
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void consume(String hash) {
        if (jdbc.update("INSERT INTO identity.dpop_replays(proof_hash,expires_at) VALUES (?,?) ON CONFLICT DO NOTHING", hash,
                Timestamp.from(Instant.now().plusSeconds(120))) != 1) throw new BusinessException(401, "DPoP proof đã được sử dụng");
    }
}
