package com.umc.pediupatrao.service;

import com.umc.pediupatrao.audit.AuditoriaService;
import com.umc.pediupatrao.entity.Pedido;
import com.umc.pediupatrao.repository.PedidoRepository;
import com.umc.pediupatrao.repository.ProdutoRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import java.util.*;
import java.math.BigDecimal;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class PedidoServiceTest {
 private PedidoRepository pedidos; private ProdutoRepository produtos; private AuditoriaService auditoria; private PedidoService service;
 @BeforeEach void setup(){pedidos=mock(PedidoRepository.class);produtos=mock(ProdutoRepository.class);auditoria=mock(AuditoriaService.class);service=new PedidoService(pedidos,produtos,auditoria);}
 @AfterEach void cleanup(){SecurityContextHolder.clearContext();}
 private void gerente(){SecurityContextHolder.getContext().setAuthentication(new UsernamePasswordAuthenticationToken("gerente","x",List.of(new SimpleGrantedAuthority("ROLE_GERENTE"))));}
 @Test void naoPermiteFinalizarPedidoEmPreparacao(){Pedido p=new Pedido();p.setId("1");p.setStatus("EM PREPARAÇÃO");when(pedidos.findById("1")).thenReturn(Optional.of(p));assertThrows(IllegalArgumentException.class,()->service.atualizarStatus("1","FINALIZADO"));verify(pedidos,never()).save(any());}
 @Test void descontoAcimaDeVintePorCentoEhRecusado(){gerente();Pedido p=new Pedido();p.setId("1");p.setStatus("RECEBIDO");p.setValorSubtotal(new BigDecimal("100"));p.setValorTotal(new BigDecimal("100"));when(pedidos.findById("1")).thenReturn(Optional.of(p));assertThrows(IllegalArgumentException.class,()->service.aplicarDesconto("1",new BigDecimal("21")));}
 @Test void atendenteNaoPodeDarDesconto(){SecurityContextHolder.getContext().setAuthentication(new UsernamePasswordAuthenticationToken("atendente","x",List.of(new SimpleGrantedAuthority("ROLE_ATENDENTE"))));assertThrows(org.springframework.security.access.AccessDeniedException.class,()->service.aplicarDesconto("1",new BigDecimal("10")));}
}
