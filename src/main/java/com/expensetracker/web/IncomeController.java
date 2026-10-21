package com.expensetracker.web;

import com.expensetracker.model.IncomeInput;
import com.expensetracker.model.IncomeRow;
import com.expensetracker.service.IncomeService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/income")
public class IncomeController {

    private final IncomeService income;

    public IncomeController(IncomeService income) {
        this.income = income;
    }

    @GetMapping
    public List<IncomeRow> list() {
        return income.list();
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public void create(@Valid @RequestBody IncomeInput input) {
        income.add(input);
    }

    @PutMapping("/{id}")
    public void update(@PathVariable int id, @Valid @RequestBody IncomeInput input) {
        income.update(id, input);
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(@PathVariable int id) {
        income.delete(id);
    }
}
