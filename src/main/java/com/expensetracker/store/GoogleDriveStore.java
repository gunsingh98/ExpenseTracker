package com.expensetracker.store;

import com.expensetracker.config.AppProperties;
import com.expensetracker.config.ConditionalOnNonBlankProperty;
import com.google.api.client.http.ByteArrayContent;
import com.google.api.services.drive.Drive;
import com.google.api.services.drive.model.File;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.io.UncheckedIOException;

/**
 * Uploads the backup workbook to a Google Drive file via the Drive v3 API using
 * a service account. Active when {@code app.drive.file-id} is set.
 */
@Component
@ConditionalOnNonBlankProperty("app.drive.file-id")
public class GoogleDriveStore implements WorkbookStore {

    private static final String XLSX_MIME =
            "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet";

    private final Drive drive;
    private final String fileId;

    public GoogleDriveStore(Drive drive, AppProperties props) {
        this.drive = drive;
        this.fileId = props.getDrive().getFileId();
    }

    @Override
    public String write(byte[] bytes) {
        try {
            File meta = drive.files()
                    .update(fileId, null, new ByteArrayContent(XLSX_MIME, bytes))
                    .setFields("headRevisionId")
                    .execute();
            return meta.getHeadRevisionId();
        } catch (IOException e) {
            throw new UncheckedIOException("Failed to upload Drive file " + fileId, e);
        }
    }
}
