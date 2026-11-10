package com.umc.pediupatrao.dashboard;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class StatusPedidoTest {

    @Test
    void normalizaVariacoesLegadas() {
        assertEquals("RECEBIDO", StatusPedido.normalizar(null));
        assertEquals("RECEBIDO", StatusPedido.normalizar("  "));
        assertEquals("EM PREPARAÇÃO", StatusPedido.normalizar("EM_PREPARACAO"));
        assertEquals("EM PREPARAÇÃO", StatusPedido.normalizar("Em Preparação"));
        assertEquals("EM PREPARAÇÃO", StatusPedido.normalizar("EM_PREPARO"));
        assertEquals("SAIU PARA ENTREGA", StatusPedido.normalizar("saiu_para_entrega"));
        assertEquals("FINALIZADO", StatusPedido.normalizar("ENTREGUE"));
        assertEquals("OUTROS", StatusPedido.normalizar("xpto"));
    }

    @Test
    void filtroNaoAceitaStatusDesconhecido() {
        assertTrue(StatusPedido.deFiltro("xpto").isEmpty());
        assertEquals("PRONTO", StatusPedido.deFiltro("pronto").orElseThrow());
    }
}
