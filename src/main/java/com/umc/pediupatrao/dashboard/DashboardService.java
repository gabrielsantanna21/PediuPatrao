package com.umc.pediupatrao.dashboard;

import com.umc.pediupatrao.dashboard.DashboardCalculator.Agrupamento;
import com.umc.pediupatrao.dashboard.DashboardCalculator.OrdenacaoRanking;
import com.umc.pediupatrao.dashboard.DashboardDtos.DistribuicaoHorarios;
import com.umc.pediupatrao.dashboard.DashboardDtos.FiltrosAplicados;
import com.umc.pediupatrao.dashboard.DashboardDtos.OpcoesFiltro;
import com.umc.pediupatrao.dashboard.DashboardDtos.PedidoResumo;
import com.umc.pediupatrao.dashboard.DashboardDtos.ProdutoRanking;
import com.umc.pediupatrao.dashboard.DashboardDtos.Resposta;
import com.umc.pediupatrao.dashboard.DashboardDtos.Resumo;
import com.umc.pediupatrao.dashboard.DashboardDtos.SerieVendas;
import com.umc.pediupatrao.dashboard.DashboardDtos.SituacaoPedidos;
import com.umc.pediupatrao.dashboard.DashboardDtos.VendaCategoria;
import com.umc.pediupatrao.entity.Cliente;
import com.umc.pediupatrao.entity.Pedido;
import com.umc.pediupatrao.entity.Produto;
import com.umc.pediupatrao.repository.ClienteRepository;
import com.umc.pediupatrao.repository.ProdutoRepository;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.TreeSet;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

/** Orquestra filtros, consultas ao MongoDB e cálculos do dashboard. */
@Service
public class DashboardService {

    public static final int LIMITE_MAXIMO = 50;

    private final DashboardPedidoRepository pedidos;
    private final ProdutoRepository produtos;
    private final ClienteRepository clientes;
    private final ZoneId zona;
    private final int limiteAtrasoPadrao;
    private Clock clock = Clock.systemUTC();

    public DashboardService(DashboardPedidoRepository pedidos, ProdutoRepository produtos, ClienteRepository clientes,
            @Value("${pediupatrao.fuso-horario:America/Sao_Paulo}") String fuso,
            @Value("${pediupatrao.dashboard.limite-atraso-minutos:40}") int limiteAtrasoPadrao) {
        this.pedidos = pedidos;
        this.produtos = produtos;
        this.clientes = clientes;
        this.zona = ZoneId.of(fuso);
        this.limiteAtrasoPadrao = limiteAtrasoPadrao;
    }

    /** Permite fixar o relógio em testes. */
    void setClock(Clock clock) {
        this.clock = Objects.requireNonNull(clock);
    }

    public DashboardFiltro filtro(String inicio, String fim, List<String> status, String categoria, String produto) {
        return DashboardFiltro.of(inicio, fim, status, categoria, produto, hoje());
    }

    public Resposta<Resumo> resumo(DashboardFiltro f) {
        DashboardFiltro ant = f.periodoAnterior();
        List<Pedido> atuais = carregar(f);
        List<Pedido> anteriores = carregar(ant);
        return resposta(f, calculadora().resumo(atuais, anteriores, f));
    }

    public Resposta<SerieVendas> serie(DashboardFiltro f, String agrupamento) {
        Agrupamento a = Agrupamento.resolver(agrupamento, f.dias());
        return resposta(f, calculadora().serie(carregar(f), f, a));
    }

    public Resposta<List<VendaCategoria>> categorias(DashboardFiltro f) {
        return resposta(f, calculadora().categorias(carregar(f), f));
    }

    public Resposta<DistribuicaoHorarios> horarios(DashboardFiltro f) {
        return resposta(f, calculadora().horarios(carregar(f), f));
    }

    public Resposta<SituacaoPedidos> situacao(DashboardFiltro f, Integer limiteAtrasoMin) {
        int limite = limiteAtraso(limiteAtrasoMin);
        return resposta(f, calculadora().situacao(carregar(f), f, clock.instant(), limite));
    }

    public Resposta<List<PedidoResumo>> recentes(DashboardFiltro f, Integer limite, Integer limiteAtrasoMin) {
        int n = limite(limite, 10);
        List<Pedido> lista = carregar(f);
        List<String> ids = lista.stream().map(Pedido::getClienteId).filter(Objects::nonNull).distinct().toList();
        Map<String, String> nomes = new HashMap<>();
        if (!ids.isEmpty()) {
            for (Cliente c : clientes.findAllById(ids)) {
                if (c.getId() != null && c.getNome() != null) {
                    nomes.put(c.getId(), c.getNome());
                }
            }
        }
        return resposta(f, calculadora().recentes(lista, f, n, clock.instant(), limiteAtraso(limiteAtrasoMin), nomes));
    }

    public Resposta<List<ProdutoRanking>> ranking(DashboardFiltro f, Integer limite, String ordenarPor) {
        OrdenacaoRanking ordem = OrdenacaoRanking.resolver(ordenarPor);
        return resposta(f, calculadora().ranking(carregar(f), f, limite(limite, 10), ordem));
    }

    public OpcoesFiltro opcoes() {
        TreeSet<String> categorias = new TreeSet<>(String.CASE_INSENSITIVE_ORDER);
        TreeSet<String> nomes = new TreeSet<>(String.CASE_INSENSITIVE_ORDER);
        for (Produto p : produtos.findAll()) {
            if (p.getTipo() != null && !p.getTipo().isBlank()) {
                categorias.add(p.getTipo().trim());
            }
            if (p.getNome() != null && !p.getNome().isBlank()) {
                nomes.add(p.getNome().trim());
            }
        }
        LocalDate hoje = hoje();
        return new OpcoesFiltro(StatusPedido.TODOS, new ArrayList<>(categorias), new ArrayList<>(nomes),
                List.of("DIA", "SEMANA", "MES"), List.of("QUANTIDADE", "FATURAMENTO"),
                hoje.minusDays(DashboardFiltro.DIAS_PADRAO - 1L), hoje,
                pedidos.extremoEntrada(true).orElse(null), pedidos.extremoEntrada(false).orElse(null),
                DashboardFiltro.MAXIMO_DIAS, zona.getId());
    }

    // ------------------------------------------------------------------

    private List<Pedido> carregar(DashboardFiltro f) {
        return pedidos.buscarPorEntrada(f.inicioInstant(zona), f.fimExclusivo(zona));
    }

    private DashboardCalculator calculadora() {
        Map<String, String> categorias = new HashMap<>();
        for (Produto p : produtos.findAll()) {
            if (p.getNome() != null && p.getTipo() != null) {
                categorias.put(p.getNome().trim().toLowerCase(Locale.ROOT), p.getTipo().trim());
            }
        }
        return new DashboardCalculator(zona, categorias);
    }

    private <T> Resposta<T> resposta(DashboardFiltro f, T dados) {
        return new Resposta<>(FiltrosAplicados.de(f, zona.getId()), clock.instant(), dados);
    }

    private LocalDate hoje() {
        return LocalDate.now(clock.withZone(zona));
    }

    private static int limite(Integer valor, int padrao) {
        if (valor == null) {
            return padrao;
        }
        if (valor < 1 || valor > LIMITE_MAXIMO) {
            throw new IllegalArgumentException("Parâmetro 'limite' deve estar entre 1 e " + LIMITE_MAXIMO + ".");
        }
        return valor;
    }

    private int limiteAtraso(Integer valor) {
        if (valor == null) {
            return limiteAtrasoPadrao;
        }
        if (valor < 5 || valor > 240) {
            throw new IllegalArgumentException("Parâmetro 'limiteAtrasoMin' deve estar entre 5 e 240 minutos.");
        }
        return valor;
    }

    ZoneId zona() {
        return zona;
    }

    Instant agora() {
        return clock.instant();
    }
}
