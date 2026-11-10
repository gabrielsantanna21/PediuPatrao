package com.umc.pediupatrao.dashboard;

import com.umc.pediupatrao.entity.Cliente;
import com.umc.pediupatrao.entity.Pedido;
import com.umc.pediupatrao.entity.Produto;
import com.umc.pediupatrao.entity.Usuario;
import com.umc.pediupatrao.repository.ClienteRepository;
import com.umc.pediupatrao.repository.PedidoRepository;
import com.umc.pediupatrao.repository.ProdutoRepository;
import com.umc.pediupatrao.repository.UsuarioRepository;
import java.time.Instant;
import java.time.ZoneId;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.CommandLineRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;

/**
 * Carga de dados de demonstração. Só roda com {@code PEDIUPATRAO_DEMO_DATA=true} e somente se a coleção
 * de pedidos estiver vazia, para nunca misturar dados fictícios com dados reais.
 */
@Component
@ConditionalOnProperty(name = "pediupatrao.demo-data", havingValue = "true")
public class DemoDataSeeder implements CommandLineRunner {

    private static final Logger log = LoggerFactory.getLogger(DemoDataSeeder.class);

    private final PedidoRepository pedidos;
    private final ProdutoRepository produtos;
    private final ClienteRepository clientes;
    private final UsuarioRepository usuarios;
    private final PasswordEncoder encoder;
    private final ZoneId zona;

    public DemoDataSeeder(PedidoRepository pedidos, ProdutoRepository produtos, ClienteRepository clientes,
            UsuarioRepository usuarios, PasswordEncoder encoder,
            @Value("${pediupatrao.fuso-horario:America/Sao_Paulo}") String fuso) {
        this.pedidos = pedidos;
        this.produtos = produtos;
        this.clientes = clientes;
        this.usuarios = usuarios;
        this.encoder = encoder;
        this.zona = ZoneId.of(fuso);
    }

    @Override
    public void run(String... args) {
        criarUsuarioDemo("gerente", "GERENTE");
        criarUsuarioDemo("atendente", "ATENDENTE");
        if (pedidos.count() > 0) {
            log.info("Dados de demonstração ignorados: a coleção 'pedidos' já possui registros.");
            return;
        }
        List<Produto> catalogo = produtos.count() == 0 ? produtos.saveAll(DemoDataGenerator.catalogo())
                : produtos.findAll().stream().filter(p -> p.getPreco() != null && p.getNome() != null).toList();
        if (catalogo.isEmpty()) {
            log.warn("Dados de demonstração ignorados: nenhum produto com preço cadastrado.");
            return;
        }
        List<Cliente> cli = clientes.count() == 0 ? clientes.saveAll(DemoDataGenerator.clientes()) : clientes.findAll();
        List<String> ids = cli.stream().map(Cliente::getId).toList();
        List<Pedido> gerados = new DemoDataGenerator(42L, zona).gerar(catalogo, ids, Instant.now(), 120);
        pedidos.saveAll(gerados);
        log.info("Dados de demonstração criados: {} pedidos, {} produtos, {} clientes.", gerados.size(),
                catalogo.size(), cli.size());
    }

    private void criarUsuarioDemo(String username, String role) {
        Usuario u = usuarios.findByUsername(username).orElseGet(Usuario::new);
        if (u.getUsername() != null && role.equals(u.getRole())
                && encoder.matches(System.getenv().getOrDefault("PEDIUPATRAO_DEMO_PASSWORD", "demo123"),
                        u.getPassword())) {
            return;
        }
        u.setUsername(username);
        u.setRole(role);
        u.setPassword(encoder.encode(System.getenv().getOrDefault("PEDIUPATRAO_DEMO_PASSWORD", "demo123")));
        usuarios.save(u);
        log.info("Usuário de demonstração '{}' ({}) criado.", username, role);
    }
}
