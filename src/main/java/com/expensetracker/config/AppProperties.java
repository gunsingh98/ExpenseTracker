package com.expensetracker.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.util.List;

/**
 * Application configuration, bound from {@code app.*} properties / env vars.
 */
@ConfigurationProperties(prefix = "app")
public class AppProperties {

    /** Google Drive settings for the periodic backup upload. */
    private Drive drive = new Drive();

    /** The single SQLite data store (financial data + audit log). */
    private Data data = new Data();

    /** Periodic Drive-backup schedule. */
    private Backup backup = new Backup();

    /** Login accounts (user + spouse). */
    private List<Account> users = List.of();

    public Drive getDrive() {
        return drive;
    }

    public void setDrive(Drive drive) {
        this.drive = drive;
    }

    public Data getData() {
        return data;
    }

    public void setData(Data data) {
        this.data = data;
    }

    public Backup getBackup() {
        return backup;
    }

    public void setBackup(Backup backup) {
        this.backup = backup;
    }

    public List<Account> getUsers() {
        return users;
    }

    public void setUsers(List<Account> users) {
        this.users = users;
    }

    public static class Drive {
        /** The Drive file id of the .xlsx workbook used as the backup target. */
        private String fileId;
        /**
         * Service-account JSON key. Either an inline JSON string
         * ({@code app.drive.credentials-json}) or a file path
         * ({@code app.drive.credentials-path}).
         */
        private String credentialsJson;
        private String credentialsPath;

        public String getFileId() {
            return fileId;
        }

        public void setFileId(String fileId) {
            this.fileId = fileId;
        }

        public String getCredentialsJson() {
            return credentialsJson;
        }

        public void setCredentialsJson(String credentialsJson) {
            this.credentialsJson = credentialsJson;
        }

        public String getCredentialsPath() {
            return credentialsPath;
        }

        public void setCredentialsPath(String credentialsPath) {
            this.credentialsPath = credentialsPath;
        }
    }

    public static class Data {
        /**
         * Filesystem path to the single SQLite database holding both the financial
         * tables and the audit log. The parent directory is created on startup if
         * missing. WAL mode produces {@code -wal}/{@code -shm} sidecar files
         * alongside it, so the whole directory must be writable (and, in a
         * container, on a persistent volume).
         */
        private String dbPath = "data/tracker.db";

        public String getDbPath() {
            return dbPath;
        }

        public void setDbPath(String dbPath) {
            this.dbPath = dbPath;
        }
    }

    public static class Backup {
        /**
         * Cron expression for the periodic Excel export pushed to Google Drive.
         * Only effective when {@code app.drive.file-id} is set. Defaults to 03:00
         * daily.
         */
        private String cron = "0 0 3 * * *";

        public String getCron() {
            return cron;
        }

        public void setCron(String cron) {
            this.cron = cron;
        }
    }

    public static class Account {
        private String username;
        /** BCrypt hash of the password. */
        private String passwordHash;

        public String getUsername() {
            return username;
        }

        public void setUsername(String username) {
            this.username = username;
        }

        public String getPasswordHash() {
            return passwordHash;
        }

        public void setPasswordHash(String passwordHash) {
            this.passwordHash = passwordHash;
        }
    }
}
