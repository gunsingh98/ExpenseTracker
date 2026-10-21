package com.expensetracker.web;

import com.expensetracker.model.BalanceInput;
import com.expensetracker.model.BalanceRow;
import com.expensetracker.service.BalanceService;
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
@RequestMapping("/api/balances")
public class BalanceController {

    private final BalanceService balances;

    public BalanceController(BalanceService balances) {
        this.balances = balances;
    }

    @GetMapping
    public List<BalanceRow> list() {
        return balances.list();
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public void create(@Valid @RequestBody BalanceInput input) {
        balances.add(input);
    }

    @PutMapping("/{id}")
    public void update(@PathVariable int id, @Valid @RequestBody BalanceInput input) {
        balances.update(id, input);
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(@PathVariable int id) {
        balances.delete(id);
    }
}
