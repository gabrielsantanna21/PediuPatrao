package com.umc.pediupatrao.dashboard;

import java.text.Normalizer;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.Set;

/**
 * Catálogo dos status de pedido usados pelo dashboard e regras de normalização.
 *
 * <p>A base possui registros antigos gravados com variações ("EM_PREPARACAO", "EM PREPARO",
 * "ENTREGUE", nulo). Para que indicadores e filtros não se fragmentem, todo status é
 * convertido para um dos valores canônicos abaixo antes de qualquer cálculo (RF07).</p>
 */
public final class StatusPedido {

    public static final String RECEBIDO = "RECEBIDO";
    public static final String EM_PREPARACAO = "EM PREPARAÇÃO";
    public static final String PRONTO = "PRONTO";
    public static final String SAIU_PARA_ENTREGA = "SAIU PARA ENTREGA";
    public static final String RETIRADO = "RETIRADO";
    public static final String FINALIZADO = "FINALIZADO";
    public static final String CANCELADO = "CANCELADO";
    public static final String OUTROS = "OUTROS";

    /** Ordem do fluxo, usada para apresentar os status sempre na mesma sequência. */
    public static final List<String> TODOS = List.of(
            RECEBIDO, EM_PREPARACAO, PRONTO, SAIU_PARA_ENTREGA, RETIRADO, FINALIZADO, CANCELADO);

    /** Pedidos que ainda estão sendo atendidos (não encerrados). */
    public static final Set<String> EM_ANDAMENTO = Set.of(RECEBIDO, EM_PREPARACAO, PRONTO, SAIU_PARA_ENTREGA);

    /** Pedidos ainda dentro da cozinha/balcão: entram na contagem de atrasados. */
    public static final Set<String> AGUARDANDO_SAIDA = Set.of(RECEBIDO, EM_PREPARACAO, PRONTO);

    private StatusPedido() {
    }

    /** Converte qualquer variação conhecida para o status canônico. Nulo/vazio vira RECEBIDO (padrão da entidade). */
    public static String normalizar(String bruto) {
        if (bruto == null || bruto.isBlank()) {
            return RECEBIDO;
        }
        String chave = semAcento(bruto).trim().toUpperCase(Locale.ROOT).replace('_', ' ').replaceAll("\\s+", " ");
        return switch (chave) {
            case "RECEBIDO", "NOVO", "ABERTO" -> RECEBIDO;
            case "EM PREPARACAO", "EM PREPARO", "PREPARANDO" -> EM_PREPARACAO;
            case "PRONTO" -> PRONTO;
            case "SAIU PARA ENTREGA", "EM ENTREGA", "SAIU" -> SAIU_PARA_ENTREGA;
            case "RETIRADO" -> RETIRADO;
            case "FINALIZADO", "ENTREGUE", "CONCLUIDO" -> FINALIZADO;
            case "CANCELADO" -> CANCELADO;
            default -> OUTROS;
        };
    }

    /** Valida um status informado pelo usuário em filtros. Retorna vazio se não for reconhecido. */
    public static Optional<String> deFiltro(String valor) {
        if (valor == null || valor.isBlank()) {
            return Optional.empty();
        }
        String s = normalizar(valor);
        return OUTROS.equals(s) ? Optional.empty() : Optional.of(s);
    }

    public static boolean isCancelado(String statusCanonico) {
        return CANCELADO.equals(statusCanonico);
    }

    static String semAcento(String s) {
        return Normalizer.normalize(s, Normalizer.Form.NFD).replaceAll("\\p{M}", "");
    }
}
