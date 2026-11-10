package com.umc.pediupatrao.dashboard;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.format.DateTimeParseException;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * Filtros de consulta do dashboard (RF05), já validados.
 *
 * @param inicio    primeiro dia do período (inclusivo), no fuso do estabelecimento
 * @param fim       último dia do período (inclusivo)
 * @param status    status canônicos aceitos; vazio = todos
 * @param categoria categoria de produto (Produto.tipo); nulo = todas
 * @param produto   nome do produto; nulo = todos
 */
public record DashboardFiltro(LocalDate inicio, LocalDate fim, Set<String> status, String categoria, String produto) {

    /** Período padrão quando o usuário não informa datas: últimos 30 dias, incluindo hoje. */
    public static final int DIAS_PADRAO = 30;
    /** Limite para evitar consultas muito pesadas (RNF de desempenho). */
    public static final int MAXIMO_DIAS = 366;

    public DashboardFiltro {
        status = status == null ? Set.of() : Set.copyOf(status);
    }

    /**
     * Constrói e valida o filtro a partir dos parâmetros crus da requisição.
     *
     * @throws IllegalArgumentException com mensagem amigável quando algum parâmetro é inválido (HTTP 400)
     */
    public static DashboardFiltro of(String inicio, String fim, List<String> status, String categoria, String produto,
            LocalDate hoje) {
        LocalDate dFim = parseData("fim", fim, hoje);
        LocalDate dInicio = parseData("inicio", inicio, dFim.minusDays(DIAS_PADRAO - 1L));
        if (dFim.isBefore(dInicio)) {
            throw new IllegalArgumentException("Período inválido: a data final (" + dFim
                    + ") é anterior à data inicial (" + dInicio + ").");
        }
        long dias = ChronoUnit.DAYS.between(dInicio, dFim) + 1;
        if (dias > MAXIMO_DIAS) {
            throw new IllegalArgumentException("Período muito longo (" + dias + " dias). O máximo permitido é "
                    + MAXIMO_DIAS + " dias.");
        }
        Set<String> sts = new LinkedHashSet<>();
        for (String v : separar(status)) {
            sts.add(StatusPedido.deFiltro(v).orElseThrow(() -> new IllegalArgumentException(
                    "Status inválido: '" + v + "'. Valores aceitos: " + String.join(", ", StatusPedido.TODOS) + ".")));
        }
        return new DashboardFiltro(dInicio, dFim, sts, vazioParaNulo(categoria), vazioParaNulo(produto));
    }

    /** Quantidade de dias do período (inclusivo). */
    public long dias() {
        return ChronoUnit.DAYS.between(inicio, fim) + 1;
    }

    /** Período imediatamente anterior, com o mesmo tamanho, usado nos comparativos. */
    public DashboardFiltro periodoAnterior() {
        long d = dias();
        return new DashboardFiltro(inicio.minusDays(d), inicio.minusDays(1), status, categoria, produto);
    }

    public Instant inicioInstant(ZoneId zona) {
        return inicio.atStartOfDay(zona).toInstant();
    }

    /** Limite superior exclusivo: meia-noite do dia seguinte ao fim. */
    public Instant fimExclusivo(ZoneId zona) {
        return fim.plusDays(1).atStartOfDay(zona).toInstant();
    }

    public boolean temFiltroDeItem() {
        return categoria != null || produto != null;
    }

    private static LocalDate parseData(String nome, String valor, LocalDate padrao) {
        if (valor == null || valor.isBlank()) {
            return padrao;
        }
        try {
            return LocalDate.parse(valor.trim());
        } catch (DateTimeParseException e) {
            throw new IllegalArgumentException("Parâmetro '" + nome + "' inválido: '" + valor
                    + "'. Use o formato AAAA-MM-DD (ex.: 2026-11-10).");
        }
    }

    /** Aceita tanto status=A&status=B quanto status=A,B. */
    private static List<String> separar(List<String> valores) {
        List<String> out = new ArrayList<>();
        if (valores == null) {
            return out;
        }
        for (String v : valores) {
            if (v == null) {
                continue;
            }
            for (String parte : v.split(",")) {
                if (!parte.isBlank()) {
                    out.add(parte.trim());
                }
            }
        }
        return out;
    }

    private static String vazioParaNulo(String s) {
        return s == null || s.isBlank() ? null : s.trim();
    }
}
