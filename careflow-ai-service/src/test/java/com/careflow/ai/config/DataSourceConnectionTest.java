package com.careflow.ai.config;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.Statement;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest
class DataSourceConnectionTest {

    @Autowired(required = false)
    private DataSource dataSource;

    @Test
    @DisplayName("DataSource phải được cấu hình và kết nối thành công tới CSDL careflow_ai")
    void testDataSourceConnection() throws Exception {
        assertNotNull(dataSource, "DataSource bean không được null (cần cấu hình DataSource trong Spring context)");

        try (Connection connection = dataSource.getConnection()) {
            assertNotNull(connection, "Kết nối CSDL không được null");
            assertFalse(connection.isClosed(), "Kết nối CSDL phải đang mở");

            try (Statement statement = connection.createStatement();
                 ResultSet rs = statement.executeQuery("SELECT count(*) FROM clinical_guideline_chunks")) {
                assertTrue(rs.next(), "Query count phải trả về kết quả");
                long count = rs.getLong(1);
                assertTrue(count > 0, "Bảng clinical_guideline_chunks phải có dữ liệu (kỳ vọng 1095 chunks)");
                assertEquals(1095, count, "Bảng clinical_guideline_chunks phải có đúng 1095 chunks");
            }
        }
    }
}
