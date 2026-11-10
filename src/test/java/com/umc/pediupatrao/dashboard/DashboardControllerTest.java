package com.umc.pediupatrao.dashboard;

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.hasItem;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.umc.pediupatrao.config.SecurityConfig;
import com.umc.pediupatrao.entity.Pedido;
import com.umc.pediupatrao.entity.Produto;
import com.umc.pediupatrao.repository.ClienteRepository;
import com.umc.pediupatrao.repository.ProdutoRepository;
import com.umc.pediupatrao.service.UsuarioDetailsService;
import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.dao.DataAccessResourceFailureException;
import org.springframework.http.MediaType;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

/**
 * Testes da camada HTTP da API do dashboard: rotas, segurança por perfil, validação de parâmetros
 * e formato de erro. O MongoDB é substituído por mocks dos repositórios.
 */
@WebMvcTest(controllers = DashboardController.class)
@Import({SecurityConfig.class, DashboardService.class})
class DashboardControllerTest {

    @Autowired
    private MockMvc mvc;

    @MockitoBean
    private DashboardPedidoRepository pedidos;
    @MockitoBean
    private ProdutoRepository produtos;
    @MockitoBean
    private ClienteRepository clientes;
    @MockitoBean
    private UsuarioDetailsService usuarioDetailsService;

    @BeforeEach
    void dados() {
        Pedido p = new Pedido();
        p.setId("p1");
        p.setStatus("FINALIZADO");
        p.setDataHoraEntrada(Instant.now().minus(Duration.ofHours(1)));
        Pedido.ItemPedido i = new Pedido.ItemPedido();
        i.setNome("Pizza Calabresa");
        i.setCategoria("Pizza");
        i.setQuantidade(1);
        i.setPrecoUnitario(new BigDecimal("50.00"));
        p.setItens(new ArrayList<>(List.of(i)));
        p.setValorSubtotal(new BigDecimal("50.00"));
        p.setValorTotal(new BigDecimal("50.00"));
        when(pedidos.buscarPorEntrada(any(), any())).thenReturn(List.of(p));
        when(pedidos.extremoEntrada(true)).thenReturn(Optional.of(p.getDataHoraEntrada()));
        when(pedidos.extremoEntrada(false)).thenReturn(Optional.of(p.getDataHoraEntrada()));
        Produto prod = new Produto();
        prod.setNome("Pizza Calabresa");
        prod.setTipo("Pizza");
        prod.setPreco(50.0);
        when(produtos.findAll()).thenReturn(List.of(prod));
    }

    @Test
    void semLoginRetorna401EmJson() throws Exception {
        mvc.perform(get("/api/dashboard/resumo"))
                .andExpect(status().isUnauthorized())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.erro").exists())
                .andExpect(jsonPath("$.status").value(401));
    }

    @Test
    @WithMockUser(roles = "ATENDENTE")
    void atendenteNaoAcessaDashboard() throws Exception {
        mvc.perform(get("/api/dashboard/resumo"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.status").value(403));
    }

    @Test
    @WithMockUser(roles = "GERENTE")
    void gerenteConsultaResumo() throws Exception {
        mvc.perform(get("/api/dashboard/resumo"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.dados.totalPedidos").value(1))
                .andExpect(jsonPath("$.dados.faturamento").value(50.0))
                .andExpect(jsonPath("$.filtros.dias").value(30))
                .andExpect(jsonPath("$.filtros.fusoHorario").value("America/Sao_Paulo"))
                .andExpect(jsonPath("$.geradoEm").exists());
    }

    @Test
    @WithMockUser(roles = "ADMIN")
    void adminConsultaRankingEFiltros() throws Exception {
        mvc.perform(get("/api/dashboard/produtos/ranking").param("limite", "5").param("ordenarPor", "faturamento"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.dados[0].produto").value("Pizza Calabresa"))
                .andExpect(jsonPath("$.dados[0].posicao").value(1));
        mvc.perform(get("/api/dashboard/filtros"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status", hasItem("EM PREPARAÇÃO")))
                .andExpect(jsonPath("$.categorias[0]").value("Pizza"));
    }

    @Test
    @WithMockUser(roles = "GERENTE")
    void demaisEndpointsRespondem() throws Exception {
        mvc.perform(get("/api/dashboard/vendas/serie").param("agrupamento", "SEMANA"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.dados.agrupamento").value("SEMANA"));
        mvc.perform(get("/api/dashboard/vendas/categorias"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.dados[0].categoria").value("Pizza"));
        mvc.perform(get("/api/dashboard/vendas/horarios"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.dados.porHora.length()").value(24));
        mvc.perform(get("/api/dashboard/pedidos/situacao").param("status", "FINALIZADO,CANCELADO"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.dados.total").value(1));
        mvc.perform(get("/api/dashboard/pedidos/recentes").param("limite", "5"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.dados[0].id").value("p1"));
    }

    @Test
    @WithMockUser(roles = "GERENTE")
    void dataInvalidaRetorna400() throws Exception {
        mvc.perform(get("/api/dashboard/resumo").param("inicio", "10/11/2026"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.erro", containsString("AAAA-MM-DD")))
                .andExpect(jsonPath("$.status").value(400))
                .andExpect(jsonPath("$.caminho").value("/api/dashboard/resumo"));
    }

    @Test
    @WithMockUser(roles = "GERENTE")
    void periodoInvertidoRetorna400() throws Exception {
        mvc.perform(get("/api/dashboard/resumo").param("inicio", "2026-11-10").param("fim", "2026-11-01"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.erro", containsString("anterior")));
    }

    @Test
    @WithMockUser(roles = "GERENTE")
    void parametrosInvalidosRetornam400() throws Exception {
        mvc.perform(get("/api/dashboard/produtos/ranking").param("limite", "abc"))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.erro", containsString("limite")));
        mvc.perform(get("/api/dashboard/produtos/ranking").param("limite", "0"))
                .andExpect(status().isBadRequest());
        mvc.perform(get("/api/dashboard/produtos/ranking").param("ordenarPor", "preco"))
                .andExpect(status().isBadRequest());
        mvc.perform(get("/api/dashboard/vendas/serie").param("agrupamento", "ANO"))
                .andExpect(status().isBadRequest());
        mvc.perform(get("/api/dashboard/pedidos/situacao").param("status", "PERDIDO"))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.erro", containsString("PERDIDO")));
    }

    @Test
    @WithMockUser(roles = "GERENTE")
    void apiDoDashboardEhSomenteLeitura() throws Exception {
        mvc.perform(post("/api/dashboard/resumo").with(csrf())).andExpect(status().isForbidden());
    }

    @Test
    @WithMockUser(roles = "GERENTE")
    void bancoIndisponivelRetorna503() throws Exception {
        when(pedidos.buscarPorEntrada(any(), any())).thenThrow(new DataAccessResourceFailureException("down"));
        mvc.perform(get("/api/dashboard/resumo"))
                .andExpect(status().isServiceUnavailable())
                .andExpect(jsonPath("$.erro", containsString("banco de dados")));
    }
}
