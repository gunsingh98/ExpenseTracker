package com.expensetracker.web;

import com.expensetracker.model.FundValueInput;
import com.expensetracker.model.InvestmentInput;
import com.expensetracker.model.InvestmentRow;
import com.expensetracker.service.InvestmentService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
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

import java.util.List;

@RestController
@RequestMapping("/api/investments")
public class InvestmentController {

    private final InvestmentService investments;

    public InvestmentController(InvestmentService investments) {
        this.investments = investments;
    }

    @GetMapping
    public List<InvestmentRow> list() {
        return investments.list();
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public void create(@Valid @RequestBody InvestmentInput input) {
        investments.add(input);
    }

    @PutMapping("/{id}")
    public void update(@PathVariable int id, @Valid @RequestBody InvestmentInput input) {
        investments.update(id, input);
    }

    /**
     * Sets the market value of a whole fund (all rows sharing the given name) in one
     * call, keeping the value on a single row. Used by the fund-grouped view so a
     * recurring investment is valued once rather than per monthly contribution.
     */
    @PutMapping("/funds/current-value")
    public void setFundCurrentValue(@Valid @RequestBody FundValueInput input) {
        investments.setFundCurrentValue(input.name(), input.currentValue());
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(@PathVariable int id) {
        investments.delete(id);
    }

    /**
     * Deletes a whole fund — every contribution sharing the given name — in one
     * call. The name is passed as a query parameter so fund names with spaces or
     * other characters survive URL encoding.
     */
    @DeleteMapping("/funds")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void deleteFund(@RequestParam String name) {
        investments.deleteFund(name);
    }
}
