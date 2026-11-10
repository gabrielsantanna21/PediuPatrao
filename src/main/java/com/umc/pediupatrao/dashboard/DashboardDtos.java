package com.umc.pediupatrao.dashboard;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Set;

/** Objetos de resposta da API do dashboard. Todos são imutáveis e serializados como JSON. */
public final class DashboardDtos {

    private DashboardDtos() {
    }

    /** Envelope comum: filtros efetivamente aplicados + instante de geração + dados. */
    public record Resposta<T>(FiltrosAplicados filtros, Instant geradoEm, T dados) {
    }

    public record FiltrosAplicados(LocalDate inicio, LocalDate fim, long dias, Set<String> status, String categoria,
            String produto, String fusoHorario) {

        static FiltrosAplicados de(DashboardFiltro f, String zona) {
            return new FiltrosAplicados(f.inicio(), f.fim(), f.dias(), f.status(), f.categoria(), f.produto(), zona);
        }
    }

    // ---------- RF01: indicadores gerais ----------
    public record Resumo(
            BigDecimal faturamento,
            long totalPedidos,
            long pedidosValidos,
            long pedidosCancelados,
            BigDecimal ticketMedio,
            long itensVendidos,
            BigDecimal descontoConcedido,
            double taxaCancelamentoPercentual,
            long pedidosEmAndamento,
            Double tempoMedioAtendimentoMinutos,
            Comparativo comparativo) {
    }

    /** Variações em relação ao período anterior de mesmo tamanho. Variação nula = sem base de comparação. */
    public record Comparativo(LocalDate inicioAnterior, LocalDate fimAnterior, BigDecimal faturamentoAnterior,
            Double variacaoFaturamentoPercentual, long pedidosAnterior, Double variacaoPedidosPercentual,
            BigDecimal ticketMedioAnterior, Double variacaoTicketMedioPercentual) {
    }

    // ---------- RF02: análise de vendas ----------
    public record SerieVendas(String agrupamento, List<PontoSerie> pontos) {
    }

    public record PontoSerie(String rotulo, LocalDate inicio, LocalDate fim, BigDecimal faturamento, long pedidos,
            long cancelados, BigDecimal ticketMedio) {
    }

    public record VendaCategoria(String categoria, long quantidade, BigDecimal faturamento,
            double percentualFaturamento) {
    }

    public record DistribuicaoHorarios(List<FaixaHorario> porHora, List<FaixaDiaSemana> porDiaSemana,
            Integer horaPico, String diaSemanaPico) {
    }

    public record FaixaHorario(int hora, long pedidos, BigDecimal faturamento) {
    }

    public record FaixaDiaSemana(int dia, String nome, long pedidos, BigDecimal faturamento) {
    }

    // ---------- RF03: situação dos pedidos ----------
    public record SituacaoPedidos(List<ContagemStatus> porStatus, long total, long emAndamento, long atrasados,
            int limiteAtrasoMinutos, Double tempoMedioAtendimentoMinutos) {
    }

    public record ContagemStatus(String status, long quantidade, double percentual, BigDecimal valor) {
    }

    public record PedidoResumo(String id, String cliente, String status, BigDecimal valorTotal, String itens,
            int quantidadeItens, Instant dataHoraEntrada, Instant dataHoraSaida, Long minutosEmAberto,
            boolean atrasado, String responsavel) {
    }

    // ---------- RF04: produtos mais vendidos ----------
    public record ProdutoRanking(int posicao, String produto, String categoria, long quantidade, BigDecimal faturamento,
            long pedidos, double participacaoPercentual) {
    }

    // ---------- RF05: opções de filtro ----------
    public record OpcoesFiltro(List<String> status, List<String> categorias, List<String> produtos,
            List<String> agrupamentos, List<String> ordenacoesRanking, LocalDate inicioPadrao, LocalDate fimPadrao,
            Instant primeiroPedido, Instant ultimoPedido, int maximoDias, String fusoHorario) {
    }
}
