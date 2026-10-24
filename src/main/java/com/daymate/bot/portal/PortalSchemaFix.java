package com.daymate.bot.portal;

import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

@Component
public class PortalSchemaFix implements ApplicationRunner {
    private final JdbcTemplate jdbc;

    public PortalSchemaFix(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public void run(ApplicationArguments args) {
        for (String sql : new String[] {
                "ALTER TABLE portal_users ALTER COLUMN password_hash DROP NOT NULL",
                "ALTER TABLE portal_users ALTER COLUMN password_hash SET NULL"
        }) {
            try {
                jdbc.execute(sql);
                return;
            } catch (RuntimeException ignored) {
                // The column is already nullable, or this database uses the other syntax.
            }
        }
    }
}
