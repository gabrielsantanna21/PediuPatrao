# PediuPatrão — controle de acesso, pedidos e auditoria

Projeto Spring Boot 3.4 / Java 21, Thymeleaf e MongoDB. Esta versão amplia a aplicação com perfis, regras de pedidos e trilha de auditoria.

## Dashboard administrativo (v1.1.0)
Painel em `/dashboard` (perfis ADMIN e GERENTE) com indicadores de vendas, gráficos, situação dos pedidos, produtos mais vendidos e filtros, alimentado pela API `GET /api/dashboard/**`.

- **Vídeo de demonstração:** [`docs/PediuPatrao_video_dash.mp4`](docs/PediuPatrao_video_dash.mp4)
- **Documentação completa** (requisitos, decisões de projeto, endpoints, evidências de testes): [`docs/DOCUMENTACAO.md`](docs/DOCUMENTACAO.md)
- **Teste da API contra a aplicação rodando:** `./docs/testes/testar-api.sh`
- **Dados de demonstração:** inicie com `PEDIUPATRAO_DEMO_DATA=true` (só popula se não houver pedidos; cria `gerente`/`atendente` com senha `demo123`).

| Endpoint | Finalidade |
|---|---|
| `GET /api/dashboard/resumo` | Indicadores gerais e comparativo com o período anterior |
| `GET /api/dashboard/vendas/serie` | Evolução por dia/semana/mês |
| `GET /api/dashboard/vendas/categorias` | Vendas por categoria |
| `GET /api/dashboard/vendas/horarios` | Movimento por hora e dia da semana |
| `GET /api/dashboard/pedidos/situacao` | Pedidos por status, em andamento e atrasados |
| `GET /api/dashboard/pedidos/recentes` | Últimos pedidos |
| `GET /api/dashboard/produtos/ranking` | Produtos mais vendidos |
| `GET /api/dashboard/filtros` | Opções para os filtros |

Filtros comuns: `inicio`, `fim` (AAAA-MM-DD), `status`, `categoria`, `produto`.

## Requisitos
- Java 21
- MongoDB local (ou uma instância MongoDB acessível)
- Maven 3.9+ (ou wrapper incluído, quando a distribuição puder ser baixada)

## Configuração e execução
1. Configure o MongoDB. Por padrão, a aplicação usa `mongodb://localhost:27017/pizzaria`.
2. Para usar outro servidor, defina `MONGODB_URI` com a URI completa antes de iniciar.
3. Defina `PEDIUPATRAO_ADMIN_PASSWORD` para escolher a senha inicial do administrador. Se não definida, o primeiro usuário admin é criado com `trocar123`; altere essa configuração em qualquer ambiente real.
4. Execute `./mvnw spring-boot:run` (Linux/macOS) ou `mvnw.cmd spring-boot:run` (Windows). Também é possível abrir como projeto Maven na IDE e executar `PizzariaPedidosApplication`.
5. Acesse `http://localhost:8080/login`.

Não coloque credenciais de banco ou senhas reais no repositório. Se uma credencial antiga foi compartilhada anteriormente, rotacione-a.

## Perfis e permissões
- **ADMIN**: gerencia usuários e atribui perfis, consulta pedidos e auditoria. Não pode aplicar descontos nem cancelar pedidos.
- **GERENTE**: consulta e gerencia pedidos, aplica desconto de até 20%, cancela com justificativa e consulta auditoria.
- **ATENDENTE**: cadastra/atualiza clientes, registra pedidos e acompanha o fluxo de status.

As regras são aplicadas pelo Spring Security às rotas web e às APIs. O cadastro público de usuários foi removido. Alteração de perfil fica restrita à administração; endpoints de usuário não retornam senha. O CSRF permanece habilitado para sessões/formulários. Para operações mutáveis via API autenticada por sessão, enviar o token CSRF obtido da sessão/formulário.

## APIs principais
- `GET /api/pedidos` — consulta pedidos para os três perfis.
- `POST /api/pedidos` — cria pedido (GERENTE/ATENDENTE). O servidor define status inicial, responsável e horário e recalcula o total.
- `PUT /api/pedidos/{id}/status?status=EM%20PREPARAÇÃO` — avança o fluxo conforme as transições permitidas.
- `POST /api/pedidos/{id}/desconto` — exclusivo GERENTE; JSON `{ "percentual": 10 }`.
- `POST /api/pedidos/{id}/cancelar` — exclusivo GERENTE; JSON `{ "justificativa": "Motivo do cancelamento" }`.
- `GET /api/clientes` — consulta clientes conforme perfil; `POST` e `PUT` cadastram/atualizam e geram auditoria.
- `GET /api/usuarios` e demais rotas `/api/usuarios/**` — somente ADMIN; as respostas não incluem senha.
- `GET /api/auditoria` — somente ADMIN/GERENTE; a tela de consulta está em `/auditoria`.
- `GET /api/dashboard/**` — somente ADMIN/GERENTE; detalhes em `docs/DOCUMENTACAO.md`.

Para compor o valor do pedido, envie `itens` com `nome` de produto cadastrado em Produtos e `quantidade`. O preço unitário enviado pelo cliente é ignorado: o servidor consulta o catálogo e calcula subtotal, desconto e total. Pedidos legados sem itens permanecem compatíveis, mas não recebem preço automaticamente.

## Fluxo de status
`RECEBIDO → EM PREPARAÇÃO → PRONTO → (SAIU PARA ENTREGA ou RETIRADO) → FINALIZADO`.
Pedidos cancelados/finalizados ficam bloqueados. Cancelamento e desconto só ocorrem antes da saída; cancelamento exige justificativa. O sistema registra responsáveis e timestamps de entrada/saída.

## Auditoria
A coleção `auditoria` guarda usuário, perfil, instante, operação, entidade/ID, campos alterados e valores anterior/novo, além da justificativa quando aplicável. São auditadas criação/alteração de pedidos, status, desconto, cancelamento e alterações relevantes de cliente. Rotas HTTP de criação, alteração e exclusão de auditoria são bloqueadas. Nunca registrar senhas/tokens nos eventos.

## Testes
- `./mvnw test` ou `mvnw.cmd test` executa os testes unitários incluídos para transições inválidas, limite de desconto e autorização do gerente.
- Testes de integração exigem MongoDB acessível e devem ser executados antes de uma entrega de produção.

## Principais arquivos alterados
`SecurityConfig`, `UsuarioDetailsService`, `DataInitializer`, `Pedido`, `PedidoService`, `ClienteService`, `PedidoController`, `UsuarioController`, configuração Mongo, além do novo pacote `audit` (entidade, repositório, serviço e controllers).
