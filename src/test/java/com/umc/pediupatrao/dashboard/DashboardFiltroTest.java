package com.umc.pediupatrao.dashboard;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.LocalDate;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.Test;

class DashboardFiltroTest {

    private static final LocalDate HOJE = LocalDate.of(2026, 11, 10);

    @Test
    void semParametrosUsaUltimosTrintaDias() {
        DashboardFiltro f = DashboardFiltro.of(null, null, null, " ", "", HOJE);
        assertEquals(LocalDate.of(2026, 10, 12), f.inicio());
        assertEquals(HOJE, f.fim());
        assertEquals(30, f.dias());
        assertTrue(f.status().isEmpty());
        assertNull(f.categoria());
        assertNull(f.produto());
    }

    @Test
    void apenasFimInformadoCalculaInicio() {
        DashboardFiltro f = DashboardFiltro.of(null, "2026-10-31", null, null, null, HOJE);
        assertEquals(LocalDate.of(2026, 10, 2), f.inicio());
    }

    @Test
    void dataEmFormatoInvalidoGeraMensagemClara() {
        IllegalArgumentException e = assertThrows(IllegalArgumentException.class,
                () -> DashboardFiltro.of("10/11/2026", null, null, null, null, HOJE));
        assertTrue(e.getMessage().contains("inicio"));
        assertTrue(e.getMessage().contains("AAAA-MM-DD"));
    }

    @Test
    void fimAnteriorAoInicioEhRecusado() {
        assertThrows(IllegalArgumentException.class,
                () -> DashboardFiltro.of("2026-11-10", "2026-11-01", null, null, null, HOJE));
    }

    @Test
    void periodoMaiorQueUmAnoEhRecusado() {
        assertThrows(IllegalArgumentException.class,
                () -> DashboardFiltro.of("2025-01-01", "2026-11-10", null, null, null, HOJE));
        // 366 dias ainda é aceito
        assertEquals(366, DashboardFiltro.of("2025-11-10", "2026-11-10", null, null, null, HOJE).dias());
    }

    @Test
    void statusAceitaVariacoesEListaSeparadaPorVirgula() {
        DashboardFiltro f = DashboardFiltro.of(null, null, List.of("em_preparacao,pronto", "Cancelado"), null, null,
                HOJE);
        assertEquals(Set.of("EM PREPARAÇÃO", "PRONTO", "CANCELADO"), f.status());
    }

    @Test
    void statusDesconhecidoEhRecusado() {
        IllegalArgumentException e = assertThrows(IllegalArgumentException.class,
                () -> DashboardFiltro.of(null, null, List.of("PERDIDO"), null, null, HOJE));
        assertTrue(e.getMessage().contains("PERDIDO"));
    }

    @Test
    void periodoAnteriorTemMesmoTamanho() {
        DashboardFiltro f = DashboardFiltro.of("2026-11-01", "2026-11-10", null, null, null, HOJE).periodoAnterior();
        assertEquals(LocalDate.of(2026, 10, 22), f.inicio());
        assertEquals(LocalDate.of(2026, 10, 31), f.fim());
    }
}
