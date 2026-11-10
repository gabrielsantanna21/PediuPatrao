# Roteiro do vídeo — Dashboard Administrativo (até 3 minutos)

**Preparação (antes de gravar)**
- MongoDB rodando e aplicação iniciada com `PEDIUPATRAO_DEMO_DATA=true` (ou com seus pedidos reais).
- Abas abertas: (1) dashboard logado como **gerente**; (2) terminal na pasta do projeto; (3) `docs/DOCUMENTACAO.md` no GitHub.
- Zoom do navegador em 90% para caber a tela inteira. Não mostrar senhas nem a URI do banco.

| Tempo | Tela | O que mostrar | Fala sugerida |
|---|---|---|---|
| 0:00–0:20 | GitHub (README/branches) | Branches `main`, `develop`, `feature/dashboard-api`, `feature/dashboard-ui`, `feature/dashboard-docs`, tag `v1.1.0`. | "O dashboard foi desenvolvido em Gitflow: a API numa feature branch, a tela em outra, integradas na develop e liberadas na main com a tag v1.1.0." |
| 0:20–0:45 | Documentação, seção 5 | Diagrama da arquitetura e a tabela de endpoints. | "Separei a API em oito endpoints GET, um por bloco da tela. O MongoDB recorta o período usando um índice em dataHoraEntrada, e as regras de cálculo ficam numa classe sem dependência de framework, o que permite testar tudo com testes unitários." |
| 0:45–1:25 | Dashboard (gerente) | Indicadores com a variação, gráfico de vendas (passar o mouse; alternar Pedidos/Ticket), categorias, situação com o alerta de atrasado, ranking (trocar para Faturamento), horários (pico). | "Aqui estão o faturamento, o ticket médio e os cancelamentos, comparados ao período anterior. O gráfico mostra a evolução por dia; as categorias mostram que pizza responde pela maior parte do faturamento. A situação dos pedidos destaca um pedido parado há mais de 40 minutos, e o movimento por horário mostra o pico às 20h, o que ajuda a planejar a equipe." |
| 1:25–1:50 | Dashboard — filtros | "Últimos 90 dias" + categoria Bebida → Aplicar. Mostrar que a URL mudou e o gráfico passou para semanal. Depois, datas invertidas para mostrar a validação. Clicar em Limpar. | "Os filtros de período, status, categoria e produto valem para todos os blocos e ficam na URL. Em 90 dias o agrupamento passa a ser semanal automaticamente. A tela valida o período antes de chamar a API." |
| 1:50–2:30 | Terminal | `./docs/testes/testar-api.sh` (ou 3 `curl`: resumo com gerente → 200; com atendente → 403; data inválida → 400). Mostrar o resumo "21 OK". | "A API foi testada contra o banco real: 200 para gerente, 403 para atendente, 401 sem login e 400 com mensagens claras para parâmetros inválidos. A API é somente leitura: um POST é negado." |
| 2:30–2:50 | Terminal | `./mvnw test` → `Tests run: …, Failures: 0`. | "Os testes unitários conferem os cálculos com valores calculados à mão, e os testes MockMvc cobrem segurança e erros." |
| 2:50–3:00 | Dashboard | Botão Atualizar / chave de atualização automática. | "Os dados são atualizados a cada 60 segundos, com tratamento de erro por bloco. Obrigado!" |

**Dica:** grave o terminal (testes) antes e corte no editor, para não esperar o Maven durante o vídeo.
