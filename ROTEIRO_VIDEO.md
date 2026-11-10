# Roteiro do vídeo de demonstração (5–8 minutos)

> Faça a gravação com o MongoDB conectado e use operações reais durante a gravação. Não reutilize registros de auditoria fabricados: os eventos mostrados devem ter sido gerados pelas ações demonstradas.

1. **Preparação (30 s)** — mostre o projeto iniciado e o MongoDB com as coleções `usuarios`, `clientes`, `pedidos`, `produtos` e `auditoria`.
2. **ADMIN (1 min)** — entre com usuário de perfil ADMIN. Mostre a administração de usuários e atribuição de perfil. Consulte a lista de pedidos e abra `/auditoria`. Tente aplicar desconto e cancelar por chamada direta à API: demonstre resposta de acesso negado (403). Mostre também que o ADMIN não tem permissão nessas operações.
3. **GERENTE (1–2 min)** — entre com GERENTE. Crie/consulte um pedido de teste. Avance o fluxo com transições válidas. Aplique desconto de 10%; tente 25% para demonstrar rejeição. Cancele outro pedido antes da saída, primeiro sem justificativa (rejeitado) e depois com justificativa. Mostre os eventos gerados na auditoria.
4. **ATENDENTE (1 min)** — entre com ATENDENTE. Cadastre ou altere telefone/endereço de um cliente e registre um pedido. Tente consultar `/api/auditoria`, administrar usuários, aplicar desconto e cancelar: todas as ações restritas devem ser negadas.
5. **Fluxo de status (1 min)** — demonstre `RECEBIDO → EM PREPARAÇÃO → PRONTO → SAIU PARA ENTREGA → FINALIZADO` (ou `RETIRADO → FINALIZADO`). Tente finalizar enquanto está em preparação e mostre que o backend rejeita a transição.
6. **Auditoria e encerramento (1 min)** — volte como ADMIN ou GERENTE, atualize `/auditoria` e localize os eventos correspondentes às ações feitas: criação, status, desconto, cancelamento e alteração do cliente. Mostre usuário, perfil, data/hora, entidade, campos anterior/novo e justificativa. Confirme que não há operações de edição/exclusão disponíveis para auditoria.

## Checklist antes de gravar
- [ ] Criar contas de demonstração ADMIN, GERENTE e ATENDENTE com senhas de teste.
- [ ] Cadastrar produtos com preços no catálogo para demonstrar o cálculo do servidor.
- [ ] Anotar os IDs reais dos pedidos usados no vídeo.
- [ ] Mostrar uma tentativa direta à API por perfil, não apenas esconder botões.
- [ ] Atualizar a tela de auditoria depois de cada ação para comprovar a correspondência dos registros.
- [ ] Não exibir tokens, senhas, URI de banco nem dados pessoais desnecessários.
