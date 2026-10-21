package com.expensetracker.web;

import com.expensetracker.model.Lists;
import com.expensetracker.service.ListsService;
import com.expensetracker.data.ListColumn;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.http.HttpStatus;

import java.util.List;
import java.util.Locale;

@RestController
@RequestMapping("/api/lists")
public class ListsController {

    private final ListsService lists;

    public ListsController(ListsService lists) {
        this.lists = lists;
    }

    @GetMapping
    public Lists get() {
        return lists.get();
    }

    /** column is one of: categories, payment-methods, people, investment-types, accounts. */
    @PutMapping("/{column}")
    public void replace(@PathVariable String column, @Valid @RequestBody ListUpdateRequest request) {
        lists.replace(parse(column), request.values());
    }

    private ListColumn parse(String column) {
        String normalized = column.toUpperCase(Locale.ROOT).replace('-', '_');
        try {
            return ListColumn.valueOf(normalized);
        } catch (IllegalArgumentException e) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Unknown list: " + column);
        }
    }

    public record ListUpdateRequest(
            @NotNull @Size(max = 500) List<@Size(max = 200) String> values) {
    }
}
