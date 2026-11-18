package com.expensetracker.data;

import com.expensetracker.model.Lists;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.jdbc.core.JdbcTemplate;
import org.sqlite.SQLiteDataSource;

import java.nio.file.Path;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/** Exercises {@link ListsDao} replace/read semantics against a real SQLite file. */
class ListsDaoTest {

    private ListsDao dao;

    @BeforeEach
    void setUp(@TempDir Path tempDir) {
        SQLiteDataSource ds = new SQLiteDataSource();
        ds.setUrl("jdbc:sqlite:" + tempDir.resolve("tracker.db"));
        dao = new ListsDao(new JdbcTemplate(ds));
        dao.initSchema();
    }

    @Test
    void emptyByDefault() {
        Lists lists = dao.readAll();
        assertThat(lists.categories()).isEmpty();
        assertThat(lists.people()).isEmpty();
    }

    @Test
    void replacePreservesOrderAndIsReadBack() {
        dao.replace(ListColumn.PEOPLE, List.of("Me", "Husband", "Joint"));
        assertThat(dao.values(ListColumn.PEOPLE)).containsExactly("Me", "Husband", "Joint");
        assertThat(dao.readAll().people()).containsExactly("Me", "Husband", "Joint");
    }

    @Test
    void replaceReplacesPreviousValuesWholesale() {
        dao.replace(ListColumn.CATEGORIES, List.of("Rent", "Groceries", "Healthcare"));
        dao.replace(ListColumn.CATEGORIES, List.of("Rent", "Other"));
        assertThat(dao.values(ListColumn.CATEGORIES)).containsExactly("Rent", "Other");
    }

    @Test
    void columnsAreIndependent() {
        dao.replace(ListColumn.CATEGORIES, List.of("Rent"));
        dao.replace(ListColumn.PAYMENT_METHODS, List.of("UPI", "Cash"));

        Lists lists = dao.readAll();
        assertThat(lists.categories()).containsExactly("Rent");
        assertThat(lists.paymentMethods()).containsExactly("UPI", "Cash");
    }
}
