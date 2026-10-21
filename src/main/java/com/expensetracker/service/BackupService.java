package com.expensetracker.service;

import com.expensetracker.config.ConditionalOnNonBlankProperty;
import com.expensetracker.store.WorkbookStore;
import com.expensetracker.workbook.WorkbookExportService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

/**
 * Pushes a periodic Excel export of the SQLite data to Google Drive as an
 * offsite backup. Only active when {@code app.drive.file-id} is set (so
 * local-only runs skip backup); the upload is best-effort — a failure is logged
 * and never propagated.
 */
@Service
@ConditionalOnNonBlankProperty("app.drive.file-id")
public class BackupService {

    private static final Logger log = LoggerFactory.getLogger(BackupService.class);

    private final WorkbookExportService exporter;
    private final WorkbookStore store;

    public BackupService(WorkbookExportService exporter, WorkbookStore store) {
        this.exporter = exporter;
        this.store = store;
    }

    @Scheduled(cron = "${app.backup.cron:0 0 3 * * *}")
    public void backup() {
        try {
            String revision = store.write(exporter.export());
            log.info("Pushed Drive backup (revision {})", revision);
        } catch (RuntimeException e) {
            log.warn("Drive backup failed; will retry on next schedule", e);
        }
    }
}
