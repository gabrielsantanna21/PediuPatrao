package com.umc.pediupatrao.dashboard;

import com.umc.pediupatrao.dashboard.DashboardDtos.DistribuicaoHorarios;
import com.umc.pediupatrao.dashboard.DashboardDtos.OpcoesFiltro;
import com.umc.pediupatrao.dashboard.DashboardDtos.PedidoResumo;
import com.umc.pediupatrao.dashboard.DashboardDtos.ProdutoRanking;
import com.umc.pediupatrao.dashboard.DashboardDtos.Resposta;
import com.umc.pediupatrao.dashboard.DashboardDtos.Resumo;
import com.umc.pediupatrao.dashboard.DashboardDtos.SerieVendas;
import com.umc.pediupatrao.dashboard.DashboardDtos.SituacaoPedidos;
import com.umc.pediupatrao.dashboard.DashboardDtos.VendaCategoria;
import java.util.List;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * API de leitura do Dashboard Administrativo (RF06). Acesso: ADMIN e GERENTE (ver SecurityConfig).
 *
 * <p>Todos os endpoints aceitam os filtros comuns {@code inicio}, {@code fim} (AAAA-MM-DD),
 * {@code status} (repetível ou separado por vírgula), {@code categoria} e {@code produto}.</p>
 */
@RestController
@RequestMapping("/api/dashboard")
public class DashboardController {

    private final DashboardService service;

    public DashboardController(DashboardService service) {
        this.service = service;
    }

    /** RF01 – indicadores gerais com comparativo ao período anterior. */
    @GetMapping("/resumo")
    public Resposta<Resumo> resumo(FiltroParams p) {
        return service.resumo(filtro(p));
    }

    /** RF02 – evolução de vendas no tempo. */
    @GetMapping("/vendas/serie")
    public Resposta<SerieVendas> serie(FiltroParams p, @RequestParam(required = false) String agrupamento) {
        return service.serie(filtro(p), agrupamento);
    }

    /** RF02 – vendas por categoria de produto. */
    @GetMapping("/vendas/categorias")
    public Resposta<List<VendaCategoria>> categorias(FiltroParams p) {
        return service.categorias(filtro(p));
    }

    /** RF02 – distribuição por hora do dia e dia da semana (tendências/picos). */
    @GetMapping("/vendas/horarios")
    public Resposta<DistribuicaoHorarios> horarios(FiltroParams p) {
        return service.horarios(filtro(p));
    }

    /** RF03 – quantidade de pedidos por status, em andamento e atrasados. */
    @GetMapping("/pedidos/situacao")
    public Resposta<SituacaoPedidos> situacao(FiltroParams p, @RequestParam(required = false) Integer limiteAtrasoMin) {
        return service.situacao(filtro(p), limiteAtrasoMin);
    }

    /** RF03 – últimos pedidos do período. */
    @GetMapping("/pedidos/recentes")
    public Resposta<List<PedidoResumo>> recentes(FiltroParams p, @RequestParam(required = false) Integer limite,
            @RequestParam(required = false) Integer limiteAtrasoMin) {
        return service.recentes(filtro(p), limite, limiteAtrasoMin);
    }

    /** RF04 – ranking dos produtos mais vendidos. */
    @GetMapping("/produtos/ranking")
    public Resposta<List<ProdutoRanking>> ranking(FiltroParams p, @RequestParam(required = false) Integer limite,
            @RequestParam(required = false) String ordenarPor) {
        return service.ranking(filtro(p), limite, ordenarPor);
    }

    /** RF05 – valores disponíveis para montar os filtros da tela. */
    @GetMapping("/filtros")
    public OpcoesFiltro filtros() {
        return service.opcoes();
    }

    private DashboardFiltro filtro(FiltroParams p) {
        return service.filtro(p.getInicio(), p.getFim(), p.getStatus(), p.getCategoria(), p.getProduto());
    }

    /** Parâmetros de filtro comuns, vinculados automaticamente a partir da query string. */
    public static class FiltroParams {
        private String inicio;
        private String fim;
        private List<String> status;
        private String categoria;
        private String produto;

        public String getInicio() {
            return inicio;
        }

        public void setInicio(String inicio) {
            this.inicio = inicio;
        }

        public String getFim() {
            return fim;
        }

        public void setFim(String fim) {
            this.fim = fim;
        }

        public List<String> getStatus() {
            return status;
        }

        public void setStatus(List<String> status) {
            this.status = status;
        }

        public String getCategoria() {
            return categoria;
        }

        public void setCategoria(String categoria) {
            this.categoria = categoria;
        }

        public String getProduto() {
            return produto;
        }

        public void setProduto(String produto) {
            this.produto = produto;
        }
    }
}
