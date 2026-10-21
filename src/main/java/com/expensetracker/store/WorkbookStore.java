package com.expensetracker.store;

/**
 * Abstraction over where the backup {@code .xlsx} is uploaded. The only
 * implementation is Google Drive; local-only runs simply have no store bean.
 */
public interface WorkbookStore {

    /** Uploads workbook bytes; returns an opaque revision token for the upload. */
    String write(byte[] bytes);
}
