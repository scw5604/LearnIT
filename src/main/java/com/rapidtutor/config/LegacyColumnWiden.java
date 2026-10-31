package com.rapidtutor.config;

import org.springframework.beans.factory.InitializingBean;
import org.springframework.context.annotation.DependsOn;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

/**
 * ddl-auto=update does not widen an existing VARCHAR(255).
 * Runs while the context is still starting, before Tomcat accepts a request.
 */
@Component
@DependsOn("entityManagerFactory")
public class LegacyColumnWiden implements InitializingBean {

    private final JdbcTemplate jdbcTemplate;

    public LegacyColumnWiden(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    @Override
    public void afterPropertiesSet() {
        widen("TUTORING_REQUEST", "SHORT_DESCRIPTION", 2000);
        widen("REVIEW", "FEEDBACK", 1000);
    }

    public void widen(String table, String column, int target) {
        Integer length = characterLength(table, column);
        if (length == null || length >= target) {
            return;
        }
        jdbcTemplate.execute("ALTER TABLE " + table + " ALTER COLUMN " + column + " VARCHAR(" + target + ")");
    }

    public Integer characterLength(String table, String column) {
        return jdbcTemplate.query(
                "SELECT CHARACTER_MAXIMUM_LENGTH FROM INFORMATION_SCHEMA.COLUMNS "
                        + "WHERE TABLE_SCHEMA = 'PUBLIC' AND TABLE_NAME = ? AND COLUMN_NAME = ?",
                rs -> rs.next() ? rs.getInt(1) : null,
                table, column);
    }
}
