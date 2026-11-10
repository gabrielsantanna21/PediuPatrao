package com.umc.pediupatrao.config;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.CommandLineRunner;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.domain.Sort;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.index.Index;

@Configuration
public class MongoInitConfig implements CommandLineRunner {

    @Autowired
    private MongoTemplate mongoTemplate;

    @Override
    public void run(String... args) throws Exception {
        if (!mongoTemplate.collectionExists("pedidos")) {
            mongoTemplate.createCollection("pedidos");
        }
        if (!mongoTemplate.collectionExists("clientes")) {
            mongoTemplate.createCollection("clientes");
        }
        if (!mongoTemplate.collectionExists("usuarios")) {
            mongoTemplate.createCollection("usuarios");
        }
        if (!mongoTemplate.collectionExists("produtos")) {
            mongoTemplate.createCollection("produtos");
        }
        if (!mongoTemplate.collectionExists("auditoria")) {
            mongoTemplate.createCollection("auditoria");
        }
        if (!mongoTemplate.collectionExists("horariosAtendimento")) {
            mongoTemplate.createCollection("horariosAtendimento");
        }
        // Índices usados pelas consultas do dashboard (recorte por período e por status).
        try {
            mongoTemplate.indexOps("pedidos").ensureIndex(
                    new Index().on("dataHoraEntrada", Sort.Direction.DESC).named("idx_pedidos_entrada"));
            mongoTemplate.indexOps("pedidos").ensureIndex(
                    new Index().on("status", Sort.Direction.ASC).on("dataHoraEntrada", Sort.Direction.DESC)
                            .named("idx_pedidos_status_entrada"));
        } catch (RuntimeException e) {
            // Um índice equivalente com outro nome já existe: não impede a inicialização.
            System.out.println("Aviso: índices do dashboard não criados (" + e.getMessage() + ")");
        }
    }
}
