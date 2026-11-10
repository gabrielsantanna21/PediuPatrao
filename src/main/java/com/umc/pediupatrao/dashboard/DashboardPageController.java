package com.umc.pediupatrao.dashboard;

import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;

/** Página do Dashboard Administrativo. Os dados são carregados no navegador pela API /api/dashboard. */
@Controller
public class DashboardPageController {

    @GetMapping("/dashboard")
    public String dashboard(Model model) {
        model.addAttribute("content", "dashboard :: content");
        return "layout";
    }
}
