package com.expensetracker.web;

import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.RequestMapping;

/**
 * Serves the React SPA's {@code index.html} for client-side routes so that
 * deep-linking / refreshing on e.g. {@code /expenses} works. API ({@code /api/**})
 * and static asset requests are handled before this.
 */
@Controller
public class SpaForwardingController {

    @RequestMapping({"/dashboard", "/expenses", "/investments", "/lists", "/login"})
    public String forward() {
        return "forward:/index.html";
    }
}
