package com.umc.pediupatrao.audit;
import org.springframework.stereotype.Controller;import org.springframework.ui.Model;import org.springframework.web.bind.annotation.GetMapping;import java.util.List;
@Controller public class AuditoriaPageController {private final AuditoriaService service;public AuditoriaPageController(AuditoriaService s){service=s;}@GetMapping("/auditoria") public String pagina(Model model){List<Auditoria> eventos=service.listar();model.addAttribute("eventos",eventos);model.addAttribute("content","auditoria :: content");return "layout";}}
