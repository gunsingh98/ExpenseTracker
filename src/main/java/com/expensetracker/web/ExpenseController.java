package com.expensetracker.web;

import com.expensetracker.model.CursorPage;
import com.expensetracker.model.ExpenseInput;
import com.expensetracker.model.ExpenseRow;
import com.expensetracker.model.ExpenseTotals;
import com.expensetracker.service.ExpenseService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import org.springframework.http.HttpStatus;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

import java.time.YearMonth;
import java.util.List;

@RestController
@RequestMapping("/api/expenses")
@Validated
public class ExpenseController {

    private final ExpenseService expenses;

    public ExpenseController(ExpenseService expenses) {
        this.expenses = expenses;
    }

    @GetMapping
    public List<ExpenseRow> list() {
        return expenses.list();
    }

    /**
     * Keyset-paginated list for the Expenses page. {@code month} is {@code yyyy-MM};
     * {@code cursor} is the {@code nextCursor} from the previous page.
     */
    @GetMapping("/page")
    public CursorPage<ExpenseRow> page(
            @RequestParam(required = false) String cursor,
            @RequestParam(defaultValue = "50") @Min(1) @Max(200) int limit,
            @RequestParam(required = false) YearMonth month,
            @RequestParam(required = false) String category) {
        try {
            return expenses.page(cursor, limit, month, category);
        } catch (IllegalArgumentException e) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Invalid cursor");
        }
    }

    /** Count and sum for the same filters as {@link #page}, across all pages. */
    @GetMapping("/totals")
    public ExpenseTotals totals(
            @RequestParam(required = false) YearMonth month,
            @RequestParam(required = false) String category) {
        return expenses.totals(month, category);
    }

    /** Months ({@code yyyy-MM}) that have expenses, newest first. */
    @GetMapping("/months")
    public List<String> months() {
        return expenses.months().stream().map(YearMonth::toString).toList();
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public void create(@Valid @RequestBody ExpenseInput input) {
        expenses.add(input);
    }

    @PutMapping("/{id}")
    public void update(@PathVariable int id, @Valid @RequestBody ExpenseInput input) {
        expenses.update(id, input);
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(@PathVariable int id) {
        expenses.delete(id);
    }
}
