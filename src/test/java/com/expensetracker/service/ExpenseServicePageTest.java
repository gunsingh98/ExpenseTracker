package com.expensetracker.service;

import com.expensetracker.audit.AuditService;
import com.expensetracker.data.ExpenseDao;
import com.expensetracker.model.CursorPage;
import com.expensetracker.model.ExpenseInput;
import com.expensetracker.model.ExpenseRow;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.jdbc.core.JdbcTemplate;
import org.sqlite.SQLiteDataSource;

import java.nio.file.Path;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;

/** Cursor handling in {@link ExpenseService#page} against a real SQLite file. */
class ExpenseServicePageTest {

    private ExpenseDao dao;
    private ExpenseService service;

    @BeforeEach
    void setUp(@TempDir Path tempDir) {
        SQLiteDataSource ds = new SQLiteDataSource();
        ds.setUrl("jdbc:sqlite:" + tempDir.resolve("tracker.db"));
        dao = new ExpenseDao(new JdbcTemplate(ds));
        dao.initSchema();
        service = new ExpenseService(dao, mock(AuditService.class));
    }

    @Test
    void followingNextCursorVisitsEveryRowAndEndsWithNull() {
        for (int i = 0; i < 7; i++) {
            dao.insert(input(LocalDate.of(2026, 5, 1 + i % 2)));
        }
        List<Integer> ids = new ArrayList<>();
        String cursor = null;
        int pages = 0;
        do {
            CursorPage<ExpenseRow> page = service.page(cursor, 3, null, " ");
            page.items().forEach(r -> ids.add(r.id()));
            cursor = page.nextCursor();
            pages++;
        } while (cursor != null);

        assertThat(pages).isEqualTo(3);
        assertThat(ids).containsExactlyElementsOf(dao.findAll().stream().map(ExpenseRow::id).toList());
    }

    @Test
    void exactlyFullLastPageHasNoNextCursor() {
        dao.insert(input(LocalDate.of(2026, 5, 1)));
        dao.insert(input(LocalDate.of(2026, 5, 2)));
        assertThat(service.page(null, 2, null, null).nextCursor()).isNull();
    }

    @Test
    void malformedCursorIsRejected() {
        assertThatThrownBy(() -> service.page("garbage", 10, null, null))
                .isInstanceOf(IllegalArgumentException.class);
    }

    private static ExpenseInput input(LocalDate date) {
        return new ExpenseInput(date, "Me", "Groceries", "x", 100, "UPI", false, "", null);
    }
}
