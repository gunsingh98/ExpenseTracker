package com.expensetracker.web;

import com.expensetracker.workbook.WorkbookExportService;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;

/**
 * Serves the current data as a downloadable {@code .xlsx} export. Authenticated
 * like the rest of {@code /api/**}.
 */
@RestController
@RequestMapping("/api/export")
public class ExportController {

    private static final MediaType XLSX = MediaType.parseMediaType(
            "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet");

    private final WorkbookExportService exporter;

    public ExportController(WorkbookExportService exporter) {
        this.exporter = exporter;
    }

    @GetMapping
    public ResponseEntity<byte[]> export() {
        byte[] bytes = exporter.export();
        String filename = "expense-tracker-" + LocalDate.now() + ".xlsx";
        return ResponseEntity.ok()
                .contentType(XLSX)
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"" + filename + "\"")
                .body(bytes);
    }
}
