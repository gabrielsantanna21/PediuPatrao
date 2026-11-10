package com.umc.pediupatrao.dashboard;

import com.umc.pediupatrao.entity.Pedido;
import java.time.Instant;
import java.util.Date;
import java.util.List;
import java.util.Optional;
import org.springframework.data.domain.Sort;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.stereotype.Repository;

/**
 * Consultas de leitura usadas pelo dashboard.
 *
 * <p>O recorte por período é feito no MongoDB (campo {@code dataHoraEntrada}, indexado em
 * {@link com.umc.pediupatrao.config.MongoInitConfig}), de forma que apenas os pedidos do intervalo
 * trafegam para a aplicação. As agregações são feitas em {@link DashboardCalculator}.</p>
 */
@Repository
public class DashboardPedidoRepository {

    static final String CAMPO_ENTRADA = "dataHoraEntrada";

    private final MongoTemplate mongo;

    public DashboardPedidoRepository(MongoTemplate mongo) {
        this.mongo = mongo;
    }

    /** Pedidos com entrada em [de, ate). */
    public List<Pedido> buscarPorEntrada(Instant de, Instant ate) {
        Query q = Query.query(Criteria.where(CAMPO_ENTRADA).gte(Date.from(de)).lt(Date.from(ate)));
        return mongo.find(q, Pedido.class);
    }

    /** Data/hora do primeiro (ou último) pedido registrado, para orientar o filtro de período na tela. */
    public Optional<Instant> extremoEntrada(boolean primeiro) {
        Query q = Query.query(Criteria.where(CAMPO_ENTRADA).ne(null))
                .with(Sort.by(primeiro ? Sort.Direction.ASC : Sort.Direction.DESC, CAMPO_ENTRADA))
                .limit(1);
        q.fields().include(CAMPO_ENTRADA);
        return Optional.ofNullable(mongo.findOne(q, Pedido.class)).map(Pedido::getDataHoraEntrada);
    }
}
