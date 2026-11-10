package com.umc.pediupatrao.dashboard;

import com.umc.pediupatrao.entity.Cliente;
import com.umc.pediupatrao.entity.Pedido;
import com.umc.pediupatrao.entity.Produto;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.DayOfWeek;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;

/**
 * Gera dados FICTÍCIOS para demonstrar o dashboard em ambiente local (pediupatrao.demo-data=true).
 *
 * <p>Os pedidos seguem as mesmas regras do {@code PedidoService}: preço vindo do catálogo, subtotal,
 * desconto de no máximo 20%, horários de entrada/saída coerentes com o status. Usa semente fixa para
 * que a demonstração seja reproduzível. Todos os registros ficam marcados com
 * {@code responsavelRegistro = "demo-seed"}.</p>
 */
public class DemoDataGenerator {

    public static final String RESPONSAVEL = "demo-seed";

    private final Random rnd;
    private final ZoneId zona;

    public DemoDataGenerator(long semente, ZoneId zona) {
        this.rnd = new Random(semente);
        this.zona = zona;
    }

    /** Catálogo de exemplo de uma pizzaria/lanchonete. */
    public static List<Produto> catalogo() {
        Object[][] dados = {
            {"Pizza Calabresa", "Pizza", 49.90}, {"Pizza Mussarela", "Pizza", 45.90},
            {"Pizza Portuguesa", "Pizza", 54.90}, {"Pizza Frango com Catupiry", "Pizza", 56.90},
            {"Pizza Margherita", "Pizza", 52.90}, {"Pizza Quatro Queijos", "Pizza", 58.90},
            {"Esfiha de Carne", "Esfiha", 6.50}, {"Esfiha de Queijo", "Esfiha", 6.50},
            {"Esfiha de Calabresa", "Esfiha", 7.00}, {"X-Burguer", "Lanche", 24.90},
            {"X-Salada", "Lanche", 26.90}, {"X-Bacon", "Lanche", 29.90},
            {"Refrigerante 2L", "Bebida", 14.00}, {"Guaraná 2L", "Bebida", 12.00},
            {"Suco Natural 500ml", "Bebida", 9.50}, {"Água 500ml", "Bebida", 4.00}};
        List<Produto> out = new ArrayList<>();
        for (Object[] d : dados) {
            Produto p = new Produto();
            p.setNome((String) d[0]);
            p.setTipo((String) d[1]);
            p.setPreco((Double) d[2]);
            p.setAtivo(true);
            out.add(p);
        }
        return out;
    }

    /** Clientes fictícios. */
    public static List<Cliente> clientes() {
        String[] nomes = {"Ana Souza", "Bruno Lima", "Carla Mendes", "Diego Alves", "Elisa Rocha", "Felipe Costa",
            "Gabriela Nunes", "Henrique Dias", "Isabela Martins", "João Pereira", "Karina Lopes", "Lucas Ribeiro",
            "Mariana Teixeira", "Nicolas Barros", "Olívia Castro", "Paulo Moreira", "Renata Freitas", "Samuel Gomes",
            "Tatiane Araújo", "Vinícius Ramos"};
        List<Cliente> out = new ArrayList<>();
        for (int i = 0; i < nomes.length; i++) {
            Cliente c = new Cliente();
            c.setNome(nomes[i]);
            c.setTelefone(String.format("(11) 9%04d-%04d", 1000 + i * 37, 2000 + i * 53));
            c.setCidade("Mogi das Cruzes");
            c.setEstado("SP");
            out.add(c);
        }
        return out;
    }

    /**
     * Gera pedidos para os últimos {@code dias} dias até {@code agora}.
     *
     * @param catalogo   produtos com nome, tipo e preço
     * @param clienteIds ids de clientes existentes (pode ser vazio)
     */
    public List<Pedido> gerar(List<Produto> catalogo, List<String> clienteIds, Instant agora, int dias) {
        LocalDate hoje = agora.atZone(zona).toLocalDate();
        List<Pedido> out = new ArrayList<>();
        // Pesos de popularidade: pizzas e refrigerante vendem mais.
        int[] pesos = new int[catalogo.size()];
        for (int i = 0; i < catalogo.size(); i++) {
            String tipo = catalogo.get(i).getTipo() == null ? "" : catalogo.get(i).getTipo();
            pesos[i] = switch (tipo) {
                case "Pizza" -> 10 - Math.min(i, 5);
                case "Bebida" -> 7 - Math.min(i % 4, 3) * 2;
                case "Esfiha" -> 4;
                default -> 3;
            };
        }
        for (int d = dias - 1; d >= 0; d--) {
            LocalDate dia = hoje.minusDays(d);
            DayOfWeek dow = dia.getDayOfWeek();
            boolean fimDeSemana = dow == DayOfWeek.FRIDAY || dow == DayOfWeek.SATURDAY || dow == DayOfWeek.SUNDAY;
            double tendencia = 1.0 + (dias - d) / (double) dias * 0.25; // leve crescimento no período
            int base = (int) Math.round((fimDeSemana ? 22 : 11) * tendencia);
            int quantidade = Math.max(3, base + rnd.nextInt(7) - 3);
            for (int k = 0; k < quantidade; k++) {
                Instant entrada = dia.atTime(horario(), rnd.nextInt(60), rnd.nextInt(60)).atZone(zona).toInstant();
                if (entrada.isAfter(agora)) {
                    continue;
                }
                out.add(pedido(catalogo, pesos, clienteIds, entrada, agora));
            }
        }
        // Alguns pedidos em andamento "agora", para a demonstração da situação dos pedidos em qualquer horário.
        String[] fluxo = {StatusPedido.RECEBIDO, StatusPedido.EM_PREPARACAO, StatusPedido.PRONTO, StatusPedido.EM_PREPARACAO};
        long[] minutosAtras = {3, 14, 26, 55};
        for (int k = 0; k < fluxo.length; k++) {
            Pedido p = pedido(catalogo, pesos, clienteIds, agora.minusSeconds(minutosAtras[k] * 60), agora);
            p.setStatus(fluxo[k]); // o último fica parado em preparação e aparece como atrasado
            p.setDataHoraSaida(null);
            p.setResponsavelSaida(null);
            p.setJustificativaCancelamento(null);
            out.add(p);
        }
        return out;
    }

    private int horario() {
        // 25% almoço (11h–14h), 75% jantar com pico às 20h.
        if (rnd.nextDouble() < 0.25) {
            return 11 + rnd.nextInt(4);
        }
        int[] noite = {18, 19, 19, 20, 20, 20, 21, 21, 22, 23};
        return noite[rnd.nextInt(noite.length)];
    }

    private Pedido pedido(List<Produto> catalogo, int[] pesos, List<String> clienteIds, Instant entrada, Instant agora) {
        Pedido p = new Pedido();
        if (!clienteIds.isEmpty()) {
            p.setClienteId(clienteIds.get(rnd.nextInt(clienteIds.size())));
        }
        List<Pedido.ItemPedido> itens = new ArrayList<>();
        int linhas = 1 + rnd.nextInt(3);
        BigDecimal subtotal = BigDecimal.ZERO;
        List<String> usados = new ArrayList<>();
        for (int l = 0; l < linhas; l++) {
            Produto prod = sortear(catalogo, pesos);
            if (usados.contains(prod.getNome())) {
                continue;
            }
            usados.add(prod.getNome());
            int qtd = "Esfiha".equals(prod.getTipo()) ? 4 + rnd.nextInt(9) : 1 + (rnd.nextDouble() < 0.25 ? 1 : 0);
            Pedido.ItemPedido i = new Pedido.ItemPedido();
            i.setNome(prod.getNome());
            i.setCategoria(prod.getTipo());
            i.setQuantidade(qtd);
            BigDecimal preco = BigDecimal.valueOf(prod.getPreco()).setScale(2, RoundingMode.HALF_UP);
            i.setPrecoUnitario(preco);
            subtotal = subtotal.add(preco.multiply(BigDecimal.valueOf(qtd)));
            itens.add(i);
        }
        p.setItens(itens);
        p.setQuantidade(itens.stream().mapToInt(Pedido.ItemPedido::getQuantidade).sum());
        p.setTipoPizza(itens.get(0).getNome());
        p.setValorSubtotal(subtotal);
        BigDecimal pct = rnd.nextDouble() < 0.08 ? BigDecimal.valueOf(5L * (1 + rnd.nextInt(3))) : BigDecimal.ZERO;
        BigDecimal desconto = subtotal.multiply(pct).divide(new BigDecimal("100"), 2, RoundingMode.HALF_UP);
        p.setDescontoPercentual(pct);
        p.setValorDesconto(desconto);
        p.setValorTotal(subtotal.subtract(desconto));
        p.setResponsavelRegistro(RESPONSAVEL);
        p.setDataHoraEntrada(entrada);

        long idadeMin = java.time.Duration.between(entrada, agora).toMinutes();
        boolean entrega = rnd.nextDouble() < 0.6;
        long preparo = 20 + rnd.nextInt(25);
        if (rnd.nextDouble() < 0.06) {
            p.setStatus(StatusPedido.CANCELADO);
            p.setJustificativaCancelamento("Cliente desistiu do pedido (dado de demonstração)");
        } else if (idadeMin > preparo + 25) {
            p.setStatus(StatusPedido.FINALIZADO);
            p.setDataHoraSaida(entrada.plusSeconds(preparo * 60));
            p.setResponsavelSaida(RESPONSAVEL);
        } else if (idadeMin >= preparo) {
            p.setStatus(entrega ? StatusPedido.SAIU_PARA_ENTREGA : StatusPedido.RETIRADO);
            p.setDataHoraSaida(entrada.plusSeconds(preparo * 60));
            p.setResponsavelSaida(RESPONSAVEL);
        } else if (idadeMin >= preparo - 8) {
            p.setStatus(StatusPedido.PRONTO);
        } else if (idadeMin >= 6) {
            p.setStatus(StatusPedido.EM_PREPARACAO);
        } else {
            p.setStatus(StatusPedido.RECEBIDO);
        }
        return p;
    }

    private Produto sortear(List<Produto> catalogo, int[] pesos) {
        int total = 0;
        for (int w : pesos) {
            total += w;
        }
        int r = rnd.nextInt(total);
        for (int i = 0; i < pesos.length; i++) {
            r -= pesos[i];
            if (r < 0) {
                return catalogo.get(i);
            }
        }
        return catalogo.get(catalogo.size() - 1);
    }
}
