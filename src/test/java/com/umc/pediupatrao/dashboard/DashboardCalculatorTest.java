package com.umc.pediupatrao.dashboard;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.umc.pediupatrao.dashboard.DashboardCalculator.Agrupamento;
import com.umc.pediupatrao.dashboard.DashboardCalculator.OrdenacaoRanking;
import com.umc.pediupatrao.dashboard.DashboardDtos.ContagemStatus;
import com.umc.pediupatrao.dashboard.DashboardDtos.DistribuicaoHorarios;
import com.umc.pediupatrao.dashboard.DashboardDtos.PedidoResumo;
import com.umc.pediupatrao.dashboard.DashboardDtos.PontoSerie;
import com.umc.pediupatrao.dashboard.DashboardDtos.ProdutoRanking;
import com.umc.pediupatrao.dashboard.DashboardDtos.Resumo;
import com.umc.pediupatrao.dashboard.DashboardDtos.SerieVendas;
import com.umc.pediupatrao.dashboard.DashboardDtos.SituacaoPedidos;
import com.umc.pediupatrao.dashboard.DashboardDtos.VendaCategoria;
import com.umc.pediupatrao.entity.Pedido;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/**
 * Testes das regras de cálculo do dashboard com um cenário montado à mão
 * (valores esperados conferidos manualmente — ver docs/DOCUMENTACAO.md, seção de testes).
 */
class DashboardCalculatorTest {

    private static final ZoneId SP = ZoneId.of("America/Sao_Paulo");
    /** "Agora" = 10/11/2026 19:00 em São Paulo. */
    private static final Instant AGORA = LocalDateTime.of(2026, 11, 10, 19, 0).atZone(SP).toInstant();
    private static final LocalDate HOJE = LocalDate.of(2026, 11, 10);

    private DashboardCalculator calc;
    private List<Pedido> atuais;
    private List<Pedido> anteriores;
    private DashboardFiltro filtro;

    @BeforeEach
    void cenario() {
        calc = new DashboardCalculator(SP, Map.of("pizza mussarela", "Pizza"));
        filtro = DashboardFiltro.of("2026-11-01", "2026-11-10", null, null, null, HOJE);

        Pedido p1 = pedido("p1", "FINALIZADO", at(2, 20, 0), "99.00", "110.00", "11.00",
                item("Pizza Calabresa", "Pizza", 2, "50.00"), item("Refrigerante 2L", "Bebida", 1, "10.00"));
        p1.setDataHoraSaida(at(2, 20, 40));
        Pedido p2 = pedido("p2", "FINALIZADO", at(2, 12, 30), "50.00", "50.00", "0",
                item("X-Burguer", "Lanche", 2, "25.00"));
        p2.setDataHoraSaida(at(2, 13, 0));
        Pedido p3 = pedido("p3", "CANCELADO", at(5, 21, 0), "50.00", "50.00", "0",
                item("Pizza Calabresa", "Pizza", 1, "50.00"));
        // Status legado com underscore e item sem categoria (deve ser resolvida pelo catálogo).
        Pedido p4 = pedido("p4", "EM_PREPARACAO", at(10, 18, 0), "40.00", "40.00", "0",
                item("Pizza Mussarela", null, 1, "40.00"));
        p4.setClienteId("c1");
        Pedido p5 = pedido("p5", "RECEBIDO", at(10, 18, 50), "20.00", "20.00", "0",
                item("Refrigerante 2L", "Bebida", 2, "10.00"));
        Pedido semData = pedido("p6", "FINALIZADO", null, "999.00", "999.00", "0",
                item("Pizza Calabresa", "Pizza", 1, "999.00"));
        atuais = new ArrayList<>(List.of(p1, p2, p3, p4, p5, semData));

        Pedido a1 = pedido("a1", "FINALIZADO", LocalDateTime.of(2026, 10, 31, 23, 30).atZone(SP).toInstant(),
                "100.00", "100.00", "0", item("Pizza Calabresa", "Pizza", 2, "50.00"));
        Pedido a2 = pedido("a2", "ENTREGUE", LocalDateTime.of(2026, 10, 25, 20, 0).atZone(SP).toInstant(),
                "50.00", "50.00", "0", item("X-Burguer", "Lanche", 2, "25.00"));
        anteriores = List.of(a1, a2);
    }

    @Test
    void resumoCalculaIndicadoresGerais() {
        Resumo r = calc.resumo(atuais, anteriores, filtro);
        assertEquals(5, r.totalPedidos());
        assertEquals(4, r.pedidosValidos());
        assertEquals(1, r.pedidosCancelados());
        assertEquals(new BigDecimal("209.00"), r.faturamento());
        assertEquals(new BigDecimal("52.25"), r.ticketMedio());
        assertEquals(8, r.itensVendidos());
        assertEquals(new BigDecimal("11.00"), r.descontoConcedido());
        assertEquals(20.0, r.taxaCancelamentoPercentual());
        assertEquals(2, r.pedidosEmAndamento());
        assertEquals(35.0, r.tempoMedioAtendimentoMinutos());
    }

    @Test
    void resumoComparaComPeriodoAnteriorDeMesmoTamanho() {
        Resumo r = calc.resumo(atuais, anteriores, filtro);
        assertEquals(LocalDate.of(2026, 10, 22), r.comparativo().inicioAnterior());
        assertEquals(LocalDate.of(2026, 10, 31), r.comparativo().fimAnterior());
        assertEquals(new BigDecimal("150.00"), r.comparativo().faturamentoAnterior());
        assertEquals(39.3, r.comparativo().variacaoFaturamentoPercentual());
        assertEquals(2, r.comparativo().pedidosAnterior());
        assertEquals(150.0, r.comparativo().variacaoPedidosPercentual());
        assertEquals(-30.3, r.comparativo().variacaoTicketMedioPercentual());
    }

    @Test
    void variacaoEhNulaQuandoNaoHaBaseDeComparacao() {
        Resumo r = calc.resumo(atuais, List.of(), filtro);
        assertNull(r.comparativo().variacaoFaturamentoPercentual());
        assertNull(r.comparativo().variacaoPedidosPercentual());
    }

    @Test
    void periodoSemPedidosRetornaZerosSemErro() {
        Resumo r = calc.resumo(List.of(), List.of(), filtro);
        assertEquals(0, r.totalPedidos());
        assertEquals(new BigDecimal("0.00"), r.faturamento());
        assertEquals(new BigDecimal("0.00"), r.ticketMedio());
        assertEquals(0.0, r.taxaCancelamentoPercentual());
        assertNull(r.tempoMedioAtendimentoMinutos());
    }

    @Test
    void serieDiariaPreencheDiasSemVendaComZero() {
        SerieVendas s = calc.serie(atuais, filtro, Agrupamento.DIA);
        assertEquals("DIA", s.agrupamento());
        assertEquals(10, s.pontos().size());
        PontoSerie dia2 = s.pontos().get(1);
        assertEquals("02/11", dia2.rotulo());
        assertEquals(new BigDecimal("149.00"), dia2.faturamento());
        assertEquals(2, dia2.pedidos());
        PontoSerie dia5 = s.pontos().get(4);
        assertEquals(0, dia5.pedidos());
        assertEquals(1, dia5.cancelados());
        assertEquals(new BigDecimal("0.00"), dia5.faturamento());
        assertEquals(new BigDecimal("60.00"), s.pontos().get(9).faturamento());
    }

    @Test
    void serieSemanalRecortaSemanasParciais() {
        SerieVendas s = calc.serie(atuais, filtro, Agrupamento.SEMANA);
        assertEquals(3, s.pontos().size());
        assertEquals("01/11–01/11", s.pontos().get(0).rotulo());
        assertEquals(LocalDate.of(2026, 11, 2), s.pontos().get(1).inicio());
        assertEquals(new BigDecimal("149.00"), s.pontos().get(1).faturamento());
        assertEquals(1, s.pontos().get(1).cancelados());
        assertEquals(LocalDate.of(2026, 11, 10), s.pontos().get(2).fim());
    }

    @Test
    void agrupamentoAutomaticoDependeDoTamanhoDoPeriodo() {
        assertEquals(Agrupamento.DIA, Agrupamento.resolver(null, 30));
        assertEquals(Agrupamento.SEMANA, Agrupamento.resolver("", 90));
        assertEquals(Agrupamento.MES, Agrupamento.resolver(null, 365));
        assertEquals(Agrupamento.MES, Agrupamento.resolver("mês", 10));
        assertThrows(IllegalArgumentException.class, () -> Agrupamento.resolver("ANO", 10));
    }

    @Test
    void categoriasRateiamDescontoEFechamComFaturamento() {
        List<VendaCategoria> c = calc.categorias(atuais, filtro);
        assertEquals(3, c.size());
        assertEquals("Pizza", c.get(0).categoria());
        assertEquals(new BigDecimal("130.00"), c.get(0).faturamento());
        assertEquals(3, c.get(0).quantidade());
        assertEquals(62.2, c.get(0).percentualFaturamento());
        assertEquals("Lanche", c.get(1).categoria());
        assertEquals("Bebida", c.get(2).categoria());
        assertEquals(new BigDecimal("29.00"), c.get(2).faturamento());
        BigDecimal soma = c.stream().map(VendaCategoria::faturamento).reduce(BigDecimal.ZERO, BigDecimal::add);
        assertEquals(new BigDecimal("209.00"), soma);
    }

    @Test
    void rankingPorQuantidadeIgnoraCancelados() {
        List<ProdutoRanking> r = calc.ranking(atuais, filtro, 10, OrdenacaoRanking.QUANTIDADE);
        assertEquals(4, r.size());
        assertEquals("Refrigerante 2L", r.get(0).produto());
        assertEquals(3, r.get(0).quantidade());
        assertEquals(37.5, r.get(0).participacaoPercentual());
        assertEquals("Pizza Calabresa", r.get(1).produto());
        assertEquals(2, r.get(1).quantidade()); // o pedido cancelado não conta
        assertEquals(1, r.get(1).pedidos());
        assertEquals("X-Burguer", r.get(2).produto());
        assertEquals(4, r.get(3).posicao());
    }

    @Test
    void rankingPorFaturamentoELimite() {
        List<ProdutoRanking> r = calc.ranking(atuais, filtro, 2, OrdenacaoRanking.FATURAMENTO);
        assertEquals(2, r.size());
        assertEquals("Pizza Calabresa", r.get(0).produto());
        assertEquals(new BigDecimal("90.00"), r.get(0).faturamento());
        assertEquals("X-Burguer", r.get(1).produto());
    }

    @Test
    void situacaoContaPorStatusNormalizadoEAtrasados() {
        SituacaoPedidos s = calc.situacao(atuais, filtro, AGORA, 40);
        assertEquals(5, s.total());
        assertEquals(2, s.emAndamento());
        assertEquals(1, s.atrasados());
        assertEquals(StatusPedido.TODOS.size(), s.porStatus().size());
        ContagemStatus emPrep = s.porStatus().stream().filter(c -> c.status().equals("EM PREPARAÇÃO")).findFirst()
                .orElseThrow();
        assertEquals(1, emPrep.quantidade());
        assertEquals(20.0, emPrep.percentual());
        ContagemStatus fin = s.porStatus().stream().filter(c -> c.status().equals("FINALIZADO")).findFirst()
                .orElseThrow();
        assertEquals(2, fin.quantidade());
        assertEquals(new BigDecimal("149.00"), fin.valor());
    }

    @Test
    void recentesOrdenaPorEntradaEMarcaAtraso() {
        List<PedidoResumo> r = calc.recentes(atuais, filtro, 3, AGORA, 40, Map.of("c1", "Ana Souza"));
        assertEquals(List.of("p5", "p4", "p3"), r.stream().map(PedidoResumo::id).toList());
        assertEquals(10L, r.get(0).minutosEmAberto());
        assertFalse(r.get(0).atrasado());
        assertEquals("Ana Souza", r.get(1).cliente());
        assertTrue(r.get(1).atrasado());
        assertEquals("EM PREPARAÇÃO", r.get(1).status());
        assertNull(r.get(2).minutosEmAberto());
        assertEquals("1x Pizza Calabresa", r.get(2).itens());
    }

    @Test
    void horariosIdentificamPicos() {
        DistribuicaoHorarios h = calc.horarios(atuais, filtro);
        assertEquals(24, h.porHora().size());
        assertEquals(7, h.porDiaSemana().size());
        assertEquals(18, h.horaPico());
        assertEquals(2, h.porHora().get(18).pedidos());
        assertEquals("segunda-feira", h.diaSemanaPico());
    }

    @Test
    void filtroPorStatus() {
        DashboardFiltro f = DashboardFiltro.of("2026-11-01", "2026-11-10", List.of("cancelado"), null, null, HOJE);
        Resumo r = calc.resumo(atuais, List.of(), f);
        assertEquals(1, r.totalPedidos());
        assertEquals(new BigDecimal("0.00"), r.faturamento());
        assertEquals(100.0, r.taxaCancelamentoPercentual());
    }

    @Test
    void filtroPorCategoriaRestringePedidosEItens() {
        DashboardFiltro f = DashboardFiltro.of("2026-11-01", "2026-11-10", null, "bebida", null, HOJE);
        Resumo r = calc.resumo(atuais, List.of(), f);
        assertEquals(2, r.totalPedidos());
        assertEquals(new BigDecimal("119.00"), r.faturamento());
        assertEquals(3, r.itensVendidos());
        List<ProdutoRanking> rank = calc.ranking(atuais, f, 10, OrdenacaoRanking.QUANTIDADE);
        assertEquals(1, rank.size());
        assertEquals("Refrigerante 2L", rank.get(0).produto());
    }

    @Test
    void filtroPorProdutoIgnoraMaiusculas() {
        DashboardFiltro f = DashboardFiltro.of("2026-11-01", "2026-11-10", null, null, "pizza calabresa", HOJE);
        assertEquals(2, calc.resumo(atuais, List.of(), f).totalPedidos());
    }

    @Test
    void pedidoLegadoSemValorTotalUsaItensMenosDesconto() {
        Pedido p = pedido("x", null, at(3, 19, 0), null, null, "5.00", item("Pizza Calabresa", "Pizza", 1, "50.00"));
        Resumo r = calc.resumo(List.of(p), List.of(), filtro);
        assertEquals(new BigDecimal("45.00"), r.faturamento());
        assertEquals(1, r.pedidosEmAndamento()); // status nulo é tratado como RECEBIDO
    }

    // ------------------------------------------------------------------

    private static Instant at(int dia, int hora, int minuto) {
        return LocalDateTime.of(2026, 11, dia, hora, minuto).atZone(SP).toInstant();
    }

    private static Pedido.ItemPedido item(String nome, String categoria, int qtd, String preco) {
        Pedido.ItemPedido i = new Pedido.ItemPedido();
        i.setNome(nome);
        i.setCategoria(categoria);
        i.setQuantidade(qtd);
        i.setPrecoUnitario(new BigDecimal(preco));
        return i;
    }

    private static Pedido pedido(String id, String status, Instant entrada, String total, String subtotal,
            String desconto, Pedido.ItemPedido... itens) {
        Pedido p = new Pedido();
        p.setId(id);
        p.setStatus(status);
        p.setDataHoraEntrada(entrada);
        p.setValorTotal(total == null ? null : new BigDecimal(total));
        p.setValorSubtotal(subtotal == null ? null : new BigDecimal(subtotal));
        p.setValorDesconto(new BigDecimal(desconto));
        p.setItens(new ArrayList<>(List.of(itens)));
        return p;
    }
}
