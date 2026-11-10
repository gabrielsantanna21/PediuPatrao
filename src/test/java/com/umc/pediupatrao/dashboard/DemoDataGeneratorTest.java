package com.umc.pediupatrao.dashboard;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.umc.pediupatrao.entity.Pedido;
import com.umc.pediupatrao.entity.Produto;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.ZoneId;
import java.util.List;
import org.junit.jupiter.api.Test;

/** Garante que os dados de demonstração respeitam as mesmas regras do sistema real. */
class DemoDataGeneratorTest {

    @Test
    void pedidosGeradosSaoConsistentes() {
        Instant agora = Instant.parse("2026-11-10T22:00:00Z");
        List<Produto> catalogo = DemoDataGenerator.catalogo();
        for (int i = 0; i < catalogo.size(); i++) {
            catalogo.get(i).setId("prod" + i);
        }
        List<Pedido> pedidos = new DemoDataGenerator(42L, ZoneId.of("America/Sao_Paulo"))
                .gerar(catalogo, List.of("c1", "c2"), agora, 60);
        assertTrue(pedidos.size() > 300);
        for (Pedido p : pedidos) {
            assertFalse(p.getDataHoraEntrada().isAfter(agora));
            assertFalse(p.getItens().isEmpty());
            assertEquals(0, p.getValorSubtotal().subtract(p.getValorDesconto()).compareTo(p.getValorTotal()));
            assertTrue(p.getDescontoPercentual().compareTo(new BigDecimal("20")) <= 0);
            if (p.getDataHoraSaida() != null) {
                assertTrue(p.getDataHoraSaida().isAfter(p.getDataHoraEntrada()));
            }
            assertEquals(StatusPedido.normalizar(p.getStatus()), p.getStatus());
        }
    }
}
