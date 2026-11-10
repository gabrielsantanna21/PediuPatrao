package com.umc.pediupatrao.dashboard;

import com.umc.pediupatrao.dashboard.DashboardDtos.Comparativo;
import com.umc.pediupatrao.dashboard.DashboardDtos.ContagemStatus;
import com.umc.pediupatrao.dashboard.DashboardDtos.DistribuicaoHorarios;
import com.umc.pediupatrao.dashboard.DashboardDtos.FaixaDiaSemana;
import com.umc.pediupatrao.dashboard.DashboardDtos.FaixaHorario;
import com.umc.pediupatrao.dashboard.DashboardDtos.PedidoResumo;
import com.umc.pediupatrao.dashboard.DashboardDtos.PontoSerie;
import com.umc.pediupatrao.dashboard.DashboardDtos.ProdutoRanking;
import com.umc.pediupatrao.dashboard.DashboardDtos.Resumo;
import com.umc.pediupatrao.dashboard.DashboardDtos.SerieVendas;
import com.umc.pediupatrao.dashboard.DashboardDtos.SituacaoPedidos;
import com.umc.pediupatrao.dashboard.DashboardDtos.VendaCategoria;
import com.umc.pediupatrao.entity.Pedido;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.DayOfWeek;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.time.format.TextStyle;
import java.time.temporal.TemporalAdjusters;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.TreeMap;

/**
 * Regras de negócio dos indicadores do dashboard.
 *
 * <p>Classe sem dependência de banco ou Spring: recebe os pedidos já recortados pelo período
 * (consulta feita no MongoDB) e devolve os agregados. Isso permite testar todas as regras
 * com testes unitários puros.</p>
 *
 * <p>Regras principais:</p>
 * <ul>
 *   <li>Faturamento = soma de {@code valorTotal} (já com desconto) dos pedidos <b>não cancelados</b>.</li>
 *   <li>Ticket médio = faturamento / pedidos não cancelados.</li>
 *   <li>Taxa de cancelamento = cancelados / total de pedidos do período.</li>
 *   <li>Valor de cada item = preço × quantidade × (valorTotal / valorSubtotal), ou seja, o desconto do
 *       pedido é rateado proporcionalmente entre os itens. Assim a soma por categoria fecha com o faturamento.</li>
 * </ul>
 */
public class DashboardCalculator {

    public static final String SEM_CATEGORIA = "Sem categoria";
    private static final Locale PT_BR = Locale.forLanguageTag("pt-BR");
    private static final BigDecimal CEM = new BigDecimal("100");

    public enum Agrupamento {
        DIA, SEMANA, MES;

        /** Resolve o parâmetro; se ausente, escolhe automaticamente pelo tamanho do período. */
        public static Agrupamento resolver(String valor, long dias) {
            if (valor == null || valor.isBlank()) {
                return dias <= 31 ? DIA : dias <= 180 ? SEMANA : MES;
            }
            try {
                return valueOf(StatusPedido.semAcento(valor).trim().toUpperCase(Locale.ROOT));
            } catch (IllegalArgumentException e) {
                throw new IllegalArgumentException("Agrupamento inválido: '" + valor + "'. Use DIA, SEMANA ou MES.");
            }
        }
    }

    public enum OrdenacaoRanking {
        QUANTIDADE, FATURAMENTO;

        public static OrdenacaoRanking resolver(String valor) {
            if (valor == null || valor.isBlank()) {
                return QUANTIDADE;
            }
            try {
                return valueOf(valor.trim().toUpperCase(Locale.ROOT));
            } catch (IllegalArgumentException e) {
                throw new IllegalArgumentException("Ordenação inválida: '" + valor + "'. Use QUANTIDADE ou FATURAMENTO.");
            }
        }
    }

    private final ZoneId zona;
    private final Map<String, String> categoriaPorProduto;

    /**
     * @param zona                fuso do estabelecimento (define o que é "dia" e "hora")
     * @param categoriaPorProduto mapa nome do produto (minúsculo) → categoria, usado para itens antigos sem categoria
     */
    public DashboardCalculator(ZoneId zona, Map<String, String> categoriaPorProduto) {
        this.zona = Objects.requireNonNull(zona);
        this.categoriaPorProduto = categoriaPorProduto == null ? Map.of() : categoriaPorProduto;
    }

    // =====================================================================
    // Normalização (RF07 – tratamento de dados)
    // =====================================================================

    record Item(String nome, String categoria, long quantidade, BigDecimal valor) {
    }

    record PedidoNorm(Pedido origem, String status, BigDecimal valor, BigDecimal desconto, Instant entrada,
            Instant saida, List<Item> itens) {

        boolean cancelado() {
            return StatusPedido.isCancelado(status);
        }
    }

    /** Normaliza e aplica os filtros de status/categoria/produto. Pedidos sem data de entrada são descartados. */
    List<PedidoNorm> preparar(List<Pedido> pedidos, DashboardFiltro filtro) {
        List<PedidoNorm> out = new ArrayList<>();
        if (pedidos == null) {
            return out;
        }
        Instant de = filtro.inicioInstant(zona);
        Instant ate = filtro.fimExclusivo(zona);
        for (Pedido p : pedidos) {
            if (p == null || p.getDataHoraEntrada() == null) {
                continue;
            }
            Instant e = p.getDataHoraEntrada();
            if (e.isBefore(de) || !e.isBefore(ate)) {
                continue;
            }
            PedidoNorm n = normalizar(p);
            if (!filtro.status().isEmpty() && !filtro.status().contains(n.status())) {
                continue;
            }
            if (filtro.temFiltroDeItem() && n.itens().stream().noneMatch(i -> itemAtende(i, filtro))) {
                continue;
            }
            out.add(n);
        }
        return out;
    }

    PedidoNorm normalizar(Pedido p) {
        String status = StatusPedido.normalizar(p.getStatus());
        List<Item> brutos = new ArrayList<>();
        BigDecimal subtotalItens = BigDecimal.ZERO;
        if (p.getItens() != null && !p.getItens().isEmpty()) {
            for (Pedido.ItemPedido i : p.getItens()) {
                if (i == null || i.getQuantidade() <= 0) {
                    continue;
                }
                String nome = i.getNome() == null || i.getNome().isBlank() ? "Item sem nome" : i.getNome().trim();
                BigDecimal preco = nz(i.getPrecoUnitario());
                BigDecimal bruto = preco.multiply(BigDecimal.valueOf(i.getQuantidade()));
                subtotalItens = subtotalItens.add(bruto);
                brutos.add(new Item(nome, categoriaDe(nome, i.getCategoria()), i.getQuantidade(), bruto));
            }
        } else if (p.getQuantidade() > 0 && (p.getTipoPizza() != null || p.getSabor() != null)) {
            // Pedido legado (antes do catálogo): mantém o volume, sem valor.
            String nome = (p.getTipoPizza() == null ? "Pedido" : p.getTipoPizza())
                    + (p.getSabor() == null ? "" : " - " + p.getSabor());
            brutos.add(new Item(nome, categoriaDe(nome, null), p.getQuantidade(), BigDecimal.ZERO));
        }
        BigDecimal desconto = nz(p.getValorDesconto());
        BigDecimal valor = p.getValorTotal() != null ? p.getValorTotal()
                : subtotalItens.subtract(desconto).max(BigDecimal.ZERO);
        valor = valor.max(BigDecimal.ZERO);
        BigDecimal subtotal = p.getValorSubtotal() != null && p.getValorSubtotal().signum() > 0
                ? p.getValorSubtotal() : subtotalItens;
        List<Item> itens = new ArrayList<>(brutos.size());
        for (Item i : brutos) {
            BigDecimal liquido = subtotal.signum() > 0
                    ? i.valor().multiply(valor).divide(subtotal, 2, RoundingMode.HALF_UP)
                    : i.valor().setScale(2, RoundingMode.HALF_UP);
            itens.add(new Item(i.nome(), i.categoria(), i.quantidade(), liquido));
        }
        return new PedidoNorm(p, status, money(valor), money(desconto), p.getDataHoraEntrada(), p.getDataHoraSaida(),
                itens);
    }

    private String categoriaDe(String nome, String categoriaItem) {
        if (categoriaItem != null && !categoriaItem.isBlank()) {
            return categoriaItem.trim();
        }
        String c = categoriaPorProduto.get(nome.toLowerCase(Locale.ROOT));
        return c == null || c.isBlank() ? SEM_CATEGORIA : c;
    }

    private static boolean itemAtende(Item i, DashboardFiltro f) {
        return (f.categoria() == null || f.categoria().equalsIgnoreCase(i.categoria()))
                && (f.produto() == null || f.produto().equalsIgnoreCase(i.nome()));
    }

    // =====================================================================
    // RF01 – Indicadores gerais
    // =====================================================================

    public Resumo resumo(List<Pedido> atuais, List<Pedido> anteriores, DashboardFiltro filtro) {
        List<PedidoNorm> lista = preparar(atuais, filtro);
        DashboardFiltro fAnt = filtro.periodoAnterior();
        List<PedidoNorm> listaAnt = preparar(anteriores, fAnt);

        Totais t = totais(lista, filtro);
        Totais ta = totais(listaAnt, fAnt);

        long emAndamento = lista.stream().filter(p -> StatusPedido.EM_ANDAMENTO.contains(p.status())).count();
        Comparativo comp = new Comparativo(fAnt.inicio(), fAnt.fim(), ta.faturamento,
                variacao(t.faturamento, ta.faturamento), ta.total,
                variacao(BigDecimal.valueOf(t.total), BigDecimal.valueOf(ta.total)), ta.ticket,
                variacao(t.ticket, ta.ticket));
        return new Resumo(t.faturamento, t.total, t.validos, t.cancelados, t.ticket, t.itens, t.desconto,
                percentual(t.cancelados, t.total), emAndamento, tempoMedioAtendimento(lista), comp);
    }

    private record Totais(BigDecimal faturamento, long total, long validos, long cancelados, BigDecimal ticket,
            long itens, BigDecimal desconto) {
    }

    private Totais totais(List<PedidoNorm> lista, DashboardFiltro filtro) {
        BigDecimal fat = BigDecimal.ZERO;
        BigDecimal desc = BigDecimal.ZERO;
        long validos = 0;
        long cancelados = 0;
        long itens = 0;
        for (PedidoNorm p : lista) {
            if (p.cancelado()) {
                cancelados++;
                continue;
            }
            validos++;
            fat = fat.add(p.valor());
            desc = desc.add(p.desconto());
            for (Item i : p.itens()) {
                if (!filtro.temFiltroDeItem() || itemAtende(i, filtro)) {
                    itens += i.quantidade();
                }
            }
        }
        BigDecimal ticket = validos == 0 ? money(BigDecimal.ZERO)
                : fat.divide(BigDecimal.valueOf(validos), 2, RoundingMode.HALF_UP);
        return new Totais(money(fat), lista.size(), validos, cancelados, ticket, itens, money(desc));
    }

    // =====================================================================
    // RF02 – Análise de vendas
    // =====================================================================

    public SerieVendas serie(List<Pedido> pedidos, DashboardFiltro filtro, Agrupamento agrupamento) {
        List<PedidoNorm> lista = preparar(pedidos, filtro);
        // Cria todos os intervalos do período (inclusive os sem venda, com zero) para o gráfico não "pular" datas.
        TreeMap<LocalDate, long[]> contagens = new TreeMap<>();
        TreeMap<LocalDate, BigDecimal> valores = new TreeMap<>();
        for (LocalDate d = inicioDoGrupo(filtro.inicio(), agrupamento); !d.isAfter(filtro.fim());
                d = proximoGrupo(d, agrupamento)) {
            contagens.put(d, new long[2]);
            valores.put(d, BigDecimal.ZERO);
        }
        for (PedidoNorm p : lista) {
            LocalDate chave = inicioDoGrupo(p.entrada().atZone(zona).toLocalDate(), agrupamento);
            long[] c = contagens.get(chave);
            if (c == null) {
                continue;
            }
            if (p.cancelado()) {
                c[1]++;
            } else {
                c[0]++;
                valores.merge(chave, p.valor(), BigDecimal::add);
            }
        }
        List<PontoSerie> pontos = new ArrayList<>();
        DateTimeFormatter diaFmt = DateTimeFormatter.ofPattern("dd/MM");
        DateTimeFormatter mesFmt = DateTimeFormatter.ofPattern("MM/yyyy");
        for (Map.Entry<LocalDate, long[]> e : contagens.entrySet()) {
            LocalDate ini = e.getKey();
            LocalDate fimGrupo = proximoGrupo(ini, agrupamento).minusDays(1);
            // O primeiro/último grupo podem ser parciais: recorta pelo período filtrado.
            LocalDate iniReal = ini.isBefore(filtro.inicio()) ? filtro.inicio() : ini;
            LocalDate fimReal = fimGrupo.isAfter(filtro.fim()) ? filtro.fim() : fimGrupo;
            String rotulo = switch (agrupamento) {
                case DIA -> ini.format(diaFmt);
                case SEMANA -> iniReal.format(diaFmt) + "–" + fimReal.format(diaFmt);
                case MES -> ini.format(mesFmt);
            };
            long ped = e.getValue()[0];
            BigDecimal fat = money(valores.get(ini));
            BigDecimal ticket = ped == 0 ? money(BigDecimal.ZERO)
                    : fat.divide(BigDecimal.valueOf(ped), 2, RoundingMode.HALF_UP);
            pontos.add(new PontoSerie(rotulo, iniReal, fimReal, fat, ped, e.getValue()[1], ticket));
        }
        return new SerieVendas(agrupamento.name(), pontos);
    }

    private static LocalDate inicioDoGrupo(LocalDate d, Agrupamento a) {
        return switch (a) {
            case DIA -> d;
            case SEMANA -> d.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY));
            case MES -> d.withDayOfMonth(1);
        };
    }

    private static LocalDate proximoGrupo(LocalDate d, Agrupamento a) {
        return switch (a) {
            case DIA -> d.plusDays(1);
            case SEMANA -> d.plusWeeks(1);
            case MES -> d.plusMonths(1);
        };
    }

    public List<VendaCategoria> categorias(List<Pedido> pedidos, DashboardFiltro filtro) {
        Map<String, long[]> qtd = new LinkedHashMap<>();
        Map<String, BigDecimal> val = new HashMap<>();
        BigDecimal total = BigDecimal.ZERO;
        for (PedidoNorm p : preparar(pedidos, filtro)) {
            if (p.cancelado()) {
                continue;
            }
            for (Item i : p.itens()) {
                if (filtro.temFiltroDeItem() && !itemAtende(i, filtro)) {
                    continue;
                }
                qtd.computeIfAbsent(i.categoria(), k -> new long[1])[0] += i.quantidade();
                val.merge(i.categoria(), i.valor(), BigDecimal::add);
                total = total.add(i.valor());
            }
        }
        List<VendaCategoria> out = new ArrayList<>();
        for (Map.Entry<String, long[]> e : qtd.entrySet()) {
            BigDecimal v = money(val.get(e.getKey()));
            out.add(new VendaCategoria(e.getKey(), e.getValue()[0], v, percentual(v, total)));
        }
        out.sort(Comparator.comparing(VendaCategoria::faturamento).reversed()
                .thenComparing(VendaCategoria::quantidade, Comparator.reverseOrder())
                .thenComparing(VendaCategoria::categoria));
        return out;
    }

    public DistribuicaoHorarios horarios(List<Pedido> pedidos, DashboardFiltro filtro) {
        long[] pedHora = new long[24];
        BigDecimal[] fatHora = new BigDecimal[24];
        long[] pedDia = new long[7];
        BigDecimal[] fatDia = new BigDecimal[7];
        java.util.Arrays.fill(fatHora, BigDecimal.ZERO);
        java.util.Arrays.fill(fatDia, BigDecimal.ZERO);
        for (PedidoNorm p : preparar(pedidos, filtro)) {
            if (p.cancelado()) {
                continue;
            }
            ZonedDateTime z = p.entrada().atZone(zona);
            int h = z.getHour();
            int d = z.getDayOfWeek().getValue() - 1; // 0 = segunda
            pedHora[h]++;
            fatHora[h] = fatHora[h].add(p.valor());
            pedDia[d]++;
            fatDia[d] = fatDia[d].add(p.valor());
        }
        List<FaixaHorario> porHora = new ArrayList<>();
        Integer horaPico = null;
        for (int h = 0; h < 24; h++) {
            porHora.add(new FaixaHorario(h, pedHora[h], money(fatHora[h])));
            if (pedHora[h] > 0 && (horaPico == null || pedHora[h] > pedHora[horaPico])) {
                horaPico = h;
            }
        }
        List<FaixaDiaSemana> porDia = new ArrayList<>();
        Integer diaPico = null;
        for (int d = 0; d < 7; d++) {
            DayOfWeek dow = DayOfWeek.of(d + 1);
            porDia.add(new FaixaDiaSemana(d + 1, dow.getDisplayName(TextStyle.FULL, PT_BR), pedDia[d],
                    money(fatDia[d])));
            if (pedDia[d] > 0 && (diaPico == null || pedDia[d] > pedDia[diaPico])) {
                diaPico = d;
            }
        }
        return new DistribuicaoHorarios(porHora, porDia, horaPico,
                diaPico == null ? null : DayOfWeek.of(diaPico + 1).getDisplayName(TextStyle.FULL, PT_BR));
    }

    // =====================================================================
    // RF03 – Situação dos pedidos
    // =====================================================================

    public SituacaoPedidos situacao(List<Pedido> pedidos, DashboardFiltro filtro, Instant agora, int limiteAtrasoMin) {
        List<PedidoNorm> lista = preparar(pedidos, filtro);
        Map<String, long[]> qtd = new LinkedHashMap<>();
        Map<String, BigDecimal> val = new HashMap<>();
        for (String s : StatusPedido.TODOS) {
            qtd.put(s, new long[1]);
            val.put(s, BigDecimal.ZERO);
        }
        long emAndamento = 0;
        long atrasados = 0;
        for (PedidoNorm p : lista) {
            qtd.computeIfAbsent(p.status(), k -> new long[1])[0]++;
            val.merge(p.status(), p.valor(), BigDecimal::add);
            if (StatusPedido.EM_ANDAMENTO.contains(p.status())) {
                emAndamento++;
            }
            if (atrasado(p, agora, limiteAtrasoMin)) {
                atrasados++;
            }
        }
        List<ContagemStatus> porStatus = new ArrayList<>();
        for (Map.Entry<String, long[]> e : qtd.entrySet()) {
            porStatus.add(new ContagemStatus(e.getKey(), e.getValue()[0], percentual(e.getValue()[0], lista.size()),
                    money(val.get(e.getKey()))));
        }
        return new SituacaoPedidos(porStatus, lista.size(), emAndamento, atrasados, limiteAtrasoMin,
                tempoMedioAtendimento(lista));
    }

    public List<PedidoResumo> recentes(List<Pedido> pedidos, DashboardFiltro filtro, int limite, Instant agora,
            int limiteAtrasoMin, Map<String, String> nomesClientes) {
        Map<String, String> nomes = nomesClientes == null ? Map.of() : nomesClientes;
        return preparar(pedidos, filtro).stream()
                .sorted(Comparator.comparing(PedidoNorm::entrada).reversed())
                .limit(limite)
                .map(p -> {
                    Long minutos = StatusPedido.EM_ANDAMENTO.contains(p.status())
                            ? Math.max(0, Duration.between(p.entrada(), agora).toMinutes()) : null;
                    StringBuilder itens = new StringBuilder();
                    int qtdItens = 0;
                    for (Item i : p.itens()) {
                        if (itens.length() > 0) {
                            itens.append(", ");
                        }
                        itens.append(i.quantidade()).append("x ").append(i.nome());
                        qtdItens += (int) i.quantidade();
                    }
                    String clienteId = p.origem().getClienteId();
                    String cliente = clienteId == null ? "Não informado" : nomes.getOrDefault(clienteId, "Cliente " + clienteId);
                    return new PedidoResumo(p.origem().getId(), cliente, p.status(), p.valor(), itens.toString(),
                            qtdItens, p.entrada(), p.saida(), minutos, atrasado(p, agora, limiteAtrasoMin),
                            p.origem().getResponsavelRegistro());
                })
                .toList();
    }

    private static boolean atrasado(PedidoNorm p, Instant agora, int limiteMin) {
        return StatusPedido.AGUARDANDO_SAIDA.contains(p.status())
                && Duration.between(p.entrada(), agora).toMinutes() > limiteMin;
    }

    /** Média, em minutos, entre a entrada e a saída (entrega/retirada) dos pedidos não cancelados. */
    private static Double tempoMedioAtendimento(List<PedidoNorm> lista) {
        long soma = 0;
        long n = 0;
        for (PedidoNorm p : lista) {
            if (!p.cancelado() && p.saida() != null && !p.saida().isBefore(p.entrada())) {
                soma += Duration.between(p.entrada(), p.saida()).getSeconds();
                n++;
            }
        }
        return n == 0 ? null : Math.round(soma / 60.0 / n * 10) / 10.0;
    }

    // =====================================================================
    // RF04 – Produtos mais vendidos
    // =====================================================================

    public List<ProdutoRanking> ranking(List<Pedido> pedidos, DashboardFiltro filtro, int limite,
            OrdenacaoRanking ordem) {
        record Acc(String nome, String categoria, long[] qtd, BigDecimal[] val, Set<String> pedidos) {
        }
        Map<String, Acc> mapa = new LinkedHashMap<>();
        long totalQtd = 0;
        BigDecimal totalVal = BigDecimal.ZERO;
        int seq = 0;
        for (PedidoNorm p : preparar(pedidos, filtro)) {
            if (p.cancelado()) {
                continue;
            }
            String idPedido = p.origem().getId() != null ? p.origem().getId() : "sem-id-" + (seq++);
            for (Item i : p.itens()) {
                if (filtro.temFiltroDeItem() && !itemAtende(i, filtro)) {
                    continue;
                }
                Acc a = mapa.computeIfAbsent(i.nome().toLowerCase(Locale.ROOT),
                        k -> new Acc(i.nome(), i.categoria(), new long[1], new BigDecimal[] {BigDecimal.ZERO},
                                new HashSet<>()));
                a.qtd()[0] += i.quantidade();
                a.val()[0] = a.val()[0].add(i.valor());
                a.pedidos().add(idPedido);
                totalQtd += i.quantidade();
                totalVal = totalVal.add(i.valor());
            }
        }
        Comparator<Acc> cmp = ordem == OrdenacaoRanking.FATURAMENTO
                ? Comparator.comparing((Acc a) -> a.val()[0]).reversed().thenComparing(a -> -a.qtd()[0])
                : Comparator.comparingLong((Acc a) -> -a.qtd()[0]).thenComparing((Acc a) -> a.val()[0],
                        Comparator.reverseOrder());
        cmp = cmp.thenComparing(Acc::nome);
        List<Acc> ordenados = mapa.values().stream().sorted(cmp).limit(limite).toList();
        List<ProdutoRanking> out = new ArrayList<>();
        int pos = 1;
        for (Acc a : ordenados) {
            double part = ordem == OrdenacaoRanking.FATURAMENTO ? percentual(a.val()[0], totalVal)
                    : percentual(a.qtd()[0], totalQtd);
            out.add(new ProdutoRanking(pos++, a.nome(), a.categoria(), a.qtd()[0], money(a.val()[0]),
                    a.pedidos().size(), part));
        }
        return out;
    }

    // =====================================================================
    // Utilitários
    // =====================================================================

    private static BigDecimal nz(BigDecimal v) {
        return v == null ? BigDecimal.ZERO : v;
    }

    private static BigDecimal money(BigDecimal v) {
        return nz(v).setScale(2, RoundingMode.HALF_UP);
    }

    static double percentual(long parte, long total) {
        return total == 0 ? 0.0 : Math.round(parte * 1000.0 / total) / 10.0;
    }

    static double percentual(BigDecimal parte, BigDecimal total) {
        if (total == null || total.signum() == 0) {
            return 0.0;
        }
        return parte.multiply(CEM).divide(total, 1, RoundingMode.HALF_UP).doubleValue();
    }

    /** Variação percentual de {@code atual} sobre {@code anterior}; nula quando não há base (anterior = 0). */
    static Double variacao(BigDecimal atual, BigDecimal anterior) {
        if (anterior == null || anterior.signum() == 0) {
            return null;
        }
        return atual.subtract(anterior).multiply(CEM).divide(anterior, 1, RoundingMode.HALF_UP).doubleValue();
    }
}
