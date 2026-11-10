/*
 * Dashboard administrativo — Pediu Patrão
 * Consome a API /api/dashboard (RF06), aplica filtros (RF05) e mantém os dados atualizados (RF07).
 */
(function () {
    'use strict';

    // ------------------------------------------------------------------ configuração
    const script = document.currentScript || document.querySelector('script[src*="dashboard.js"]');
    const BASE = script ? script.src.replace(/\/js\/dashboard\.js.*$/, '') : '';
    const API = BASE + '/api/dashboard';
    const INTERVALO_MS = 60000;
    const MAX_DIAS = 366;
    const COR = { serie: '#2a78d6', serieSuave: 'rgba(42,120,214,0.12)', grade: '#ecebe7', texto: '#52514e' };

    const moeda = new Intl.NumberFormat('pt-BR', { style: 'currency', currency: 'BRL' });
    const moedaCurta = new Intl.NumberFormat('pt-BR', { style: 'currency', currency: 'BRL', notation: 'compact', maximumFractionDigits: 1 });
    const inteiro = new Intl.NumberFormat('pt-BR');
    const decimal1 = new Intl.NumberFormat('pt-BR', { minimumFractionDigits: 1, maximumFractionDigits: 1 });

    const $ = (id) => document.getElementById(id);

    const estado = {
        filtros: { inicio: '', fim: '', status: '', categoria: '', produto: '', periodo: '30' },
        agrupamento: '',
        metrica: 'faturamento',
        ordem: 'QUANTIDADE',
        modoHorario: 'hora',
        dados: {},
        controle: null,
        timer: null,
        carregando: false,
        graficos: {}
    };

    // ------------------------------------------------------------------ utilitários
    function hojeISO() {
        const d = new Date();
        return iso(d);
    }
    function iso(d) {
        const p = (n) => String(n).padStart(2, '0');
        return d.getFullYear() + '-' + p(d.getMonth() + 1) + '-' + p(d.getDate());
    }
    function deISO(s) {
        const [a, m, d] = s.split('-').map(Number);
        return new Date(a, m - 1, d);
    }
    function diasEntre(a, b) {
        return Math.round((deISO(b) - deISO(a)) / 86400000) + 1;
    }
    function dataBR(s) {
        if (!s) return '';
        const [a, m, d] = s.split('-');
        return d + '/' + m + '/' + a;
    }
    function horaBR(instante) {
        const d = new Date(instante);
        const hoje = new Date();
        const hh = d.toLocaleTimeString('pt-BR', { hour: '2-digit', minute: '2-digit' });
        if (d.toDateString() === hoje.toDateString()) return hh;
        return d.toLocaleDateString('pt-BR', { day: '2-digit', month: '2-digit' }) + ' ' + hh;
    }
    function num(v) {
        const n = Number(v);
        return Number.isFinite(n) ? n : 0;
    }
    function esc(s) {
        return String(s == null ? '' : s).replace(/[&<>"']/g, (c) => ({ '&': '&amp;', '<': '&lt;', '>': '&gt;', '"': '&quot;', "'": '&#39;' }[c]));
    }
    function periodoPreset(preset) {
        const hoje = new Date();
        const fim = iso(hoje);
        if (preset === 'hoje') return { inicio: fim, fim };
        if (preset === 'mes') return { inicio: iso(new Date(hoje.getFullYear(), hoje.getMonth(), 1)), fim };
        if (preset === 'mes-anterior') {
            return {
                inicio: iso(new Date(hoje.getFullYear(), hoje.getMonth() - 1, 1)),
                fim: iso(new Date(hoje.getFullYear(), hoje.getMonth(), 0))
            };
        }
        const dias = parseInt(preset, 10);
        if (dias > 0) {
            const ini = new Date(hoje);
            ini.setDate(ini.getDate() - (dias - 1));
            return { inicio: iso(ini), fim };
        }
        return null;
    }

    // ------------------------------------------------------------------ API
    class ApiErro extends Error {
        constructor(status, mensagem) {
            super(mensagem);
            this.status = status;
        }
    }

    function parametrosFiltro(extra) {
        const p = new URLSearchParams();
        const f = estado.filtros;
        if (f.inicio) p.set('inicio', f.inicio);
        if (f.fim) p.set('fim', f.fim);
        if (f.status) p.set('status', f.status);
        if (f.categoria) p.set('categoria', f.categoria);
        if (f.produto) p.set('produto', f.produto);
        Object.entries(extra || {}).forEach(([k, v]) => { if (v !== '' && v != null) p.set(k, v); });
        return p;
    }

    async function api(caminho, params, signal) {
        const url = API + caminho + (params && params.toString() ? '?' + params : '');
        let resp;
        try {
            resp = await fetch(url, { headers: { Accept: 'application/json' }, credentials: 'same-origin', signal });
        } catch (e) {
            if (e.name === 'AbortError') throw e;
            throw new ApiErro(0, 'Sem conexão com o servidor.');
        }
        let corpo = null;
        try { corpo = await resp.json(); } catch (e) { /* resposta sem JSON */ }
        if (resp.redirected && /\/login/.test(resp.url)) {
            throw new ApiErro(401, 'Sua sessão expirou.');
        }
        if (!resp.ok || corpo == null) {
            const msg = (corpo && corpo.erro) || ('Falha ao carregar dados (HTTP ' + resp.status + ').');
            throw new ApiErro(resp.status, msg);
        }
        return corpo;
    }

    // ------------------------------------------------------------------ estados visuais
    function marcarCarregando(ativo) {
        document.querySelectorAll('[data-card]').forEach((el) => el.classList.toggle('carregando', ativo));
    }

    function falhaHtml(msg) {
        return '<div class="dash-falha"><i class="bi bi-exclamation-triangle"></i> ' + esc(msg) +
            '<br><button type="button" class="btn btn-sm btn-outline-secondary" data-retry>Tentar novamente</button></div>';
    }

    function mostrarFalhaGrafico(canvasId, msg) {
        const box = $(canvasId).parentElement;
        limparSobreposicao(box);
        box.insertAdjacentHTML('beforeend', falhaHtml(msg));
    }
    function mostrarVazioGrafico(canvasId, msg) {
        const box = $(canvasId).parentElement;
        limparSobreposicao(box);
        box.insertAdjacentHTML('beforeend', '<div class="dash-vazio"><i class="bi bi-inbox"></i> ' + esc(msg) + '</div>');
    }
    function limparSobreposicao(box) {
        box.querySelectorAll('.dash-vazio, .dash-falha').forEach((e) => e.remove());
    }

    function erroGlobal(msg, html) {
        const el = $('dash-erro-global');
        if (!msg) { el.hidden = true; el.innerHTML = ''; return; }
        el.hidden = false;
        el.innerHTML = '<i class="bi bi-exclamation-octagon"></i><div>' + (html ? msg : esc(msg)) + '</div>';
    }

    // ------------------------------------------------------------------ filtros (RF05)
    function lerUrl() {
        const q = new URLSearchParams(location.search);
        const f = estado.filtros;
        f.periodo = q.get('periodo') || (q.get('inicio') || q.get('fim') ? 'custom' : '30');
        const preset = f.periodo !== 'custom' ? periodoPreset(f.periodo) : null;
        f.inicio = preset ? preset.inicio : (q.get('inicio') || '');
        f.fim = preset ? preset.fim : (q.get('fim') || '');
        f.status = q.get('status') || '';
        f.categoria = q.get('categoria') || '';
        f.produto = q.get('produto') || '';
        estado.agrupamento = q.get('agrupamento') || '';
    }

    function gravarUrl() {
        const f = estado.filtros;
        const q = new URLSearchParams();
        if (f.periodo && f.periodo !== 'custom') q.set('periodo', f.periodo);
        else { if (f.inicio) q.set('inicio', f.inicio); if (f.fim) q.set('fim', f.fim); }
        ['status', 'categoria', 'produto'].forEach((k) => { if (f[k]) q.set(k, f[k]); });
        if (estado.agrupamento) q.set('agrupamento', estado.agrupamento);
        const qs = q.toString();
        history.replaceState(null, '', location.pathname + (qs ? '?' + qs : ''));
    }

    function preencherFormulario() {
        const f = estado.filtros;
        $('f-periodo').value = f.periodo;
        $('f-inicio').value = f.inicio;
        $('f-fim').value = f.fim;
        $('f-status').value = f.status;
        $('f-categoria').value = f.categoria;
        $('f-produto').value = f.produto;
        $('serie-agrupamento').value = estado.agrupamento;
    }

    function preencherSelect(id, valores, rotuloTodos, selecionado) {
        const sel = $(id);
        sel.innerHTML = '<option value="">' + esc(rotuloTodos) + '</option>' +
            valores.map((v) => '<option value="' + esc(v) + '">' + esc(v) + '</option>').join('');
        if (selecionado && !valores.some((v) => v.toLowerCase() === selecionado.toLowerCase())) {
            // Mantém um valor vindo da URL mesmo que não esteja mais no catálogo.
            sel.insertAdjacentHTML('beforeend', '<option value="' + esc(selecionado) + '">' + esc(selecionado) + '</option>');
        }
        sel.value = selecionado || '';
    }

    async function carregarOpcoes() {
        try {
            const o = await api('/filtros');
            preencherSelect('f-status', o.status || [], 'Todos', estado.filtros.status);
            preencherSelect('f-categoria', o.categorias || [], 'Todas', estado.filtros.categoria);
            preencherSelect('f-produto', o.produtos || [], 'Todos', estado.filtros.produto);
            if (o.ultimoPedido == null) {
                erroGlobal('Ainda não há pedidos registrados. Os indicadores serão exibidos assim que houver vendas.');
            }
        } catch (e) {
            // Sem opções o dashboard continua funcionando; erros de acesso são tratados no carregamento principal.
        }
    }

    function validarFormulario() {
        const ini = $('f-inicio').value;
        const fim = $('f-fim').value;
        const erro = $('f-erro');
        let msg = '';
        if (!ini || !fim) msg = 'Informe as datas inicial e final.';
        else if (fim < ini) msg = 'A data final não pode ser anterior à inicial.';
        else if (diasEntre(ini, fim) > MAX_DIAS) msg = 'Escolha um período de até ' + MAX_DIAS + ' dias.';
        erro.hidden = !msg;
        erro.textContent = msg;
        return !msg;
    }

    function aplicarFormulario() {
        if (!validarFormulario()) return;
        const f = estado.filtros;
        f.periodo = $('f-periodo').value;
        f.inicio = $('f-inicio').value;
        f.fim = $('f-fim').value;
        f.status = $('f-status').value;
        f.categoria = $('f-categoria').value;
        f.produto = $('f-produto').value;
        gravarUrl();
        carregarTudo();
    }

    // ------------------------------------------------------------------ RF01 – indicadores
    function delta(el, variacao, rotulo) {
        if (variacao == null) {
            el.innerHTML = 'sem dados no período anterior';
            return;
        }
        const sobe = variacao > 0;
        const igual = variacao === 0;
        const icone = igual ? 'bi-dash' : (sobe ? 'bi-arrow-up-right up' : 'bi-arrow-down-right down');
        const sinal = sobe ? '+' : '';
        el.innerHTML = '<i class="bi ' + icone + '" aria-hidden="true"></i>' + sinal + decimal1.format(variacao) + '% ' + esc(rotulo);
        el.title = (sobe ? 'Alta' : igual ? 'Estável' : 'Queda') + ' em relação ao período anterior';
    }

    function renderResumo(r) {
        const d = r.dados;
        const c = d.comparativo;
        $('kpi-faturamento').textContent = moeda.format(num(d.faturamento));
        $('kpi-pedidos').textContent = inteiro.format(d.totalPedidos);
        $('kpi-ticket').textContent = moeda.format(num(d.ticketMedio));
        $('kpi-itens').textContent = inteiro.format(d.itensVendidos);
        $('kpi-desconto').textContent = num(d.descontoConcedido) > 0
            ? moeda.format(num(d.descontoConcedido)) + ' em descontos' : 'sem descontos';
        $('kpi-cancelados').textContent = inteiro.format(d.pedidosCancelados);
        $('kpi-taxa').textContent = decimal1.format(d.taxaCancelamentoPercentual) + '% dos pedidos';
        $('kpi-tempo').textContent = d.tempoMedioAtendimentoMinutos == null ? '—'
            : decimal1.format(d.tempoMedioAtendimentoMinutos) + ' min';
        const vs = 'vs. anterior';
        delta($('kpi-faturamento-delta'), c.variacaoFaturamentoPercentual, vs);
        delta($('kpi-pedidos-delta'), c.variacaoPedidosPercentual, vs);
        delta($('kpi-ticket-delta'), c.variacaoTicketMedioPercentual, vs);

        const f = r.filtros;
        let rotulo = dataBR(f.inicio) + (f.inicio !== f.fim ? ' a ' + dataBR(f.fim) : '') +
            ' · ' + f.dias + (f.dias === 1 ? ' dia' : ' dias') +
            ' · comparado a ' + dataBR(c.inicioAnterior) + (c.inicioAnterior !== c.fimAnterior ? ' a ' + dataBR(c.fimAnterior) : '');
        const extras = [];
        if (f.status && f.status.length) extras.push('status: ' + f.status.join(', '));
        if (f.categoria) extras.push('categoria: ' + f.categoria);
        if (f.produto) extras.push('produto: ' + f.produto);
        if (extras.length) rotulo += ' · ' + extras.join(' · ');
        $('dash-periodo-label').textContent = rotulo;
    }

    function falhaResumo(msg) {
        ['kpi-faturamento', 'kpi-pedidos', 'kpi-ticket', 'kpi-itens', 'kpi-cancelados', 'kpi-tempo'].forEach((id) => { $(id).textContent = '—'; });
        ['kpi-faturamento-delta', 'kpi-pedidos-delta', 'kpi-ticket-delta', 'kpi-desconto', 'kpi-taxa'].forEach((id) => { $(id).textContent = ''; });
        $('kpi-faturamento-delta').textContent = msg;
    }

    // ------------------------------------------------------------------ RF02 – série temporal
    const METRICAS = {
        faturamento: { rotulo: 'Faturamento', fmt: (v) => moeda.format(v), eixo: (v) => moedaCurta.format(v) },
        pedidos: { rotulo: 'Pedidos', fmt: (v) => inteiro.format(v) + ' pedidos', eixo: (v) => inteiro.format(v) },
        ticketMedio: { rotulo: 'Ticket médio', fmt: (v) => moeda.format(v), eixo: (v) => moedaCurta.format(v) }
    };
    const NOMES_AGRUP = { DIA: 'por dia', SEMANA: 'por semana', MES: 'por mês' };

    function renderSerie() {
        const r = estado.dados.serie;
        if (!r) return;
        const pontos = r.dados.pontos;
        const m = METRICAS[estado.metrica];
        const box = $('chart-serie').parentElement;
        limparSobreposicao(box);
        $('serie-sub').textContent = m.rotulo + ' ' + NOMES_AGRUP[r.dados.agrupamento] +
            ' (pedidos cancelados não entram no faturamento)';
        const labels = pontos.map((p) => p.rotulo);
        const valores = pontos.map((p) => num(p[estado.metrica]));
        const config = {
            type: 'line',
            data: {
                labels,
                datasets: [{
                    label: m.rotulo, data: valores, borderColor: COR.serie, backgroundColor: COR.serieSuave,
                    borderWidth: 2, pointRadius: pontos.length > 45 ? 0 : 3, pointHoverRadius: 5,
                    pointBackgroundColor: COR.serie, pointBorderColor: '#fff', pointBorderWidth: 1.5,
                    lineTension: 0.25, fill: true
                }]
            },
            options: {
                maintainAspectRatio: false, animation: { duration: 300 },
                legend: { display: false },
                hover: { mode: 'index', intersect: false },
                tooltips: {
                    mode: 'index', intersect: false, displayColors: false,
                    callbacks: {
                        title: (itens) => {
                            const p = pontos[itens[0].index];
                            return p.inicio === p.fim ? dataBR(p.inicio) : dataBR(p.inicio) + ' a ' + dataBR(p.fim);
                        },
                        label: (item) => m.rotulo + ': ' + m.fmt(item.yLabel),
                        afterLabel: (item) => {
                            const p = pontos[item.index];
                            const linhas = [];
                            if (estado.metrica !== 'pedidos') linhas.push(inteiro.format(p.pedidos) + ' pedidos');
                            if (p.cancelados) linhas.push(inteiro.format(p.cancelados) + ' cancelado(s)');
                            return linhas;
                        }
                    }
                },
                scales: {
                    xAxes: [{ gridLines: { display: false }, ticks: { maxTicksLimit: 12, maxRotation: 0, fontColor: COR.texto } }],
                    yAxes: [{
                        gridLines: { color: COR.grade, drawBorder: false, zeroLineColor: COR.grade },
                        ticks: { beginAtZero: true, maxTicksLimit: 6, fontColor: COR.texto, callback: m.eixo, precision: 0 }
                    }]
                }
            }
        };
        desenhar('serie', 'chart-serie', config);
        if (pontos.every((p) => p.pedidos === 0 && p.cancelados === 0)) {
            mostrarVazioGrafico('chart-serie', 'Nenhuma venda no período selecionado.');
        }
    }

    function desenhar(chave, canvasId, config) {
        if (typeof Chart === 'undefined') {
            mostrarFalhaGrafico(canvasId, 'Biblioteca de gráficos não carregada.');
            return;
        }
        const atual = estado.graficos[chave];
        if (atual && atual.config.type === config.type) {
            atual.data.labels = config.data.labels;
            atual.data.datasets = config.data.datasets;
            atual.options = config.options;
            atual.update();
        } else {
            if (atual) atual.destroy();
            estado.graficos[chave] = new Chart($(canvasId).getContext('2d'), config);
        }
    }

    // ------------------------------------------------------------------ RF02 – categorias
    function listaBarras(el, itens, vazio) {
        if (!itens.length) {
            el.innerHTML = '<li class="dash-vazio"><i class="bi bi-inbox"></i> ' + esc(vazio) + '</li>';
            return;
        }
        const max = Math.max(...itens.map((i) => i.valor), 0);
        el.innerHTML = itens.map((i) => {
            const pct = max > 0 ? (i.valor / max) * 100 : 0;
            return '<li class="barra-linha' + (i.valor === 0 ? ' zero' : '') + '" title="' + esc(i.titulo || '') + '">' +
                '<span class="barra-nome">' + (i.icone || '') + esc(i.nome) + '</span>' +
                '<span class="barra-valor">' + esc(i.texto) + '</span>' +
                '<span class="barra-trilho" aria-hidden="true"><span class="barra-fill" style="width:' + pct.toFixed(1) + '%"></span></span>' +
                '</li>';
        }).join('');
    }

    function renderCategorias(r) {
        listaBarras($('lista-categorias'), r.dados.map((c) => ({
            nome: c.categoria,
            valor: num(c.faturamento),
            texto: moeda.format(num(c.faturamento)) + ' · ' + decimal1.format(c.percentualFaturamento) + '%',
            titulo: inteiro.format(c.quantidade) + ' itens vendidos'
        })), 'Nenhuma venda no período.');
    }

    // ------------------------------------------------------------------ RF03 – situação
    function iconeStatus(status) {
        if (status === 'CANCELADO') return '<i class="bi bi-x-circle" aria-hidden="true"></i>';
        if (status === 'FINALIZADO' || status === 'RETIRADO') return '<i class="bi bi-check-circle" aria-hidden="true"></i>';
        if (status === 'SAIU PARA ENTREGA') return '<i class="bi bi-bicycle" aria-hidden="true"></i>';
        return '<i class="bi bi-hourglass-split" aria-hidden="true"></i>';
    }
    function classeStatus(status) {
        if (status === 'CANCELADO') return 'st-cancelado';
        if (status === 'FINALIZADO' || status === 'RETIRADO') return 'st-ok';
        return 'st-andamento';
    }

    function renderSituacao(r) {
        const d = r.dados;
        $('status-sub').textContent = inteiro.format(d.total) + ' pedidos no período · ' +
            inteiro.format(d.emAndamento) + ' em andamento';
        const alertas = [];
        if (d.atrasados > 0) {
            alertas.push('<div class="dash-alerta"><i class="bi bi-exclamation-triangle-fill warning" aria-hidden="true"></i>' +
                '<span><strong>' + inteiro.format(d.atrasados) + '</strong> pedido(s) aguardando há mais de ' + d.limiteAtrasoMinutos + ' min</span></div>');
        } else if (d.emAndamento > 0) {
            alertas.push('<div class="dash-alerta"><i class="bi bi-check-circle-fill good" aria-hidden="true"></i>' +
                '<span>Nenhum pedido atrasado (limite de ' + d.limiteAtrasoMinutos + ' min)</span></div>');
        }
        $('status-alertas').innerHTML = alertas.join('');
        listaBarras($('lista-status'), d.porStatus.map((s) => ({
            nome: s.status.charAt(0) + s.status.slice(1).toLowerCase(),
            icone: '<span class="st ' + classeStatus(s.status) + ' me-1">' + iconeStatus(s.status) + '</span>',
            valor: s.quantidade,
            texto: inteiro.format(s.quantidade) + ' · ' + decimal1.format(s.percentual) + '%',
            titulo: moeda.format(num(s.valor)) + ' em pedidos'
        })), 'Nenhum pedido no período.');
    }

    function renderRecentes(r) {
        const tb = $('tabela-recentes');
        if (!r.dados.length) {
            tb.innerHTML = '<tr><td colspan="4" class="dash-vazio">Nenhum pedido no período.</td></tr>';
            return;
        }
        tb.innerHTML = r.dados.map((p) => {
            let st = '<span class="st ' + classeStatus(p.status) + '">' + iconeStatus(p.status) + esc(p.status.toLowerCase()) + '</span>';
            if (p.atrasado) {
                st += '<br><span class="st st-atraso"><i class="bi bi-exclamation-triangle-fill" aria-hidden="true"></i>há ' + p.minutosEmAberto + ' min</span>';
            } else if (p.minutosEmAberto != null) {
                st += '<br><span class="dash-sub">há ' + p.minutosEmAberto + ' min</span>';
            }
            return '<tr>' +
                '<td class="text-nowrap">' + esc(horaBR(p.dataHoraEntrada)) + '</td>' +
                '<td><div>' + esc(p.cliente) + '</div><div class="itens" title="' + esc(p.itens) + '">' + esc(p.itens || '—') + '</div></td>' +
                '<td>' + st + '</td>' +
                '<td class="num">' + moeda.format(num(p.valorTotal)) + '</td>' +
                '</tr>';
        }).join('');
    }

    // ------------------------------------------------------------------ RF04 – ranking
    function renderRanking(r) {
        const tb = $('tabela-ranking');
        if (!r.dados.length) {
            tb.innerHTML = '<tr><td colspan="6" class="dash-vazio">Nenhum produto vendido no período.</td></tr>';
            return;
        }
        const max = Math.max(...r.dados.map((p) => p.participacaoPercentual), 0);
        tb.innerHTML = r.dados.map((p) => {
            const largura = max > 0 ? (p.participacaoPercentual / max) * 100 : 0;
            return '<tr>' +
                '<td class="num">' + p.posicao + '</td>' +
                '<td>' + esc(p.produto) + '</td>' +
                '<td class="d-none d-md-table-cell text-secondary">' + esc(p.categoria) + '</td>' +
                '<td class="num">' + inteiro.format(p.quantidade) + '</td>' +
                '<td class="num">' + moeda.format(num(p.faturamento)) + '</td>' +
                '<td class="d-none d-lg-table-cell"><div class="d-flex align-items-center gap-2">' +
                '<div class="mini-trilho flex-grow-1" aria-hidden="true"><div class="mini-fill" style="width:' + largura.toFixed(1) + '%"></div></div>' +
                '<span class="text-secondary small">' + decimal1.format(p.participacaoPercentual) + '%</span></div></td>' +
                '</tr>';
        }).join('');
    }

    // ------------------------------------------------------------------ RF02 – horários
    function renderHorarios() {
        const r = estado.dados.horarios;
        if (!r) return;
        const d = r.dados;
        const box = $('chart-horarios').parentElement;
        limparSobreposicao(box);
        let faixas;
        let labels;
        if (estado.modoHorario === 'hora') {
            const ativos = d.porHora.filter((h) => h.pedidos > 0).map((h) => h.hora);
            const ini = ativos.length ? Math.max(0, Math.min(...ativos) - 1) : 10;
            const fim = ativos.length ? Math.min(23, Math.max(...ativos) + 1) : 23;
            faixas = d.porHora.slice(ini, fim + 1);
            labels = faixas.map((h) => h.hora + 'h');
        } else {
            faixas = d.porDiaSemana;
            labels = faixas.map((x) => x.nome.replace('-feira', '').replace(/^./, (c) => c.toUpperCase()));
        }
        const pico = [];
        if (d.horaPico != null) pico.push('pico às ' + d.horaPico + 'h');
        if (d.diaSemanaPico) pico.push('dia mais movimentado: ' + d.diaSemanaPico);
        $('horarios-sub').textContent = 'Pedidos não cancelados' + (pico.length ? ' · ' + pico.join(' · ') : '');
        desenhar('horarios', 'chart-horarios', {
            type: 'bar',
            data: { labels, datasets: [{ label: 'Pedidos', data: faixas.map((x) => x.pedidos), backgroundColor: COR.serie, hoverBackgroundColor: '#1c5cab', barPercentage: 0.7, categoryPercentage: 0.9 }] },
            options: {
                maintainAspectRatio: false, animation: { duration: 300 }, legend: { display: false },
                tooltips: {
                    displayColors: false,
                    callbacks: {
                        label: (item) => inteiro.format(item.yLabel) + ' pedidos',
                        afterLabel: (item) => moeda.format(num(faixas[item.index].faturamento))
                    }
                },
                scales: {
                    xAxes: [{ gridLines: { display: false }, ticks: { fontColor: COR.texto, maxRotation: 0, autoSkip: true } }],
                    yAxes: [{ gridLines: { color: COR.grade, drawBorder: false, zeroLineColor: COR.grade }, ticks: { beginAtZero: true, precision: 0, maxTicksLimit: 5, fontColor: COR.texto } }]
                }
            }
        });
        if (faixas.every((x) => x.pedidos === 0)) mostrarVazioGrafico('chart-horarios', 'Nenhum pedido no período.');
    }

    // ------------------------------------------------------------------ carregamento (RF06/RF07)
    const CARTOES = {
        resumo: { caminho: '/resumo', render: renderResumo, falha: falhaResumo },
        serie: {
            caminho: '/vendas/serie', extra: () => ({ agrupamento: estado.agrupamento }),
            render: (r) => { estado.dados.serie = r; renderSerie(); },
            falha: (m) => mostrarFalhaGrafico('chart-serie', m)
        },
        categorias: {
            caminho: '/vendas/categorias', render: renderCategorias,
            falha: (m) => { $('lista-categorias').innerHTML = '<li>' + falhaHtml(m) + '</li>'; }
        },
        situacao: {
            caminho: '/pedidos/situacao', render: renderSituacao,
            falha: (m) => { $('status-alertas').innerHTML = ''; $('lista-status').innerHTML = '<li>' + falhaHtml(m) + '</li>'; }
        },
        ranking: {
            caminho: '/produtos/ranking', extra: () => ({ limite: 10, ordenarPor: estado.ordem }), render: renderRanking,
            falha: (m) => { $('tabela-ranking').innerHTML = '<tr><td colspan="6">' + falhaHtml(m) + '</td></tr>'; }
        },
        horarios: {
            caminho: '/vendas/horarios', render: (r) => { estado.dados.horarios = r; renderHorarios(); },
            falha: (m) => mostrarFalhaGrafico('chart-horarios', m)
        },
        recentes: {
            caminho: '/pedidos/recentes', extra: () => ({ limite: 8 }), render: renderRecentes,
            falha: (m) => { $('tabela-recentes').innerHTML = '<tr><td colspan="4">' + falhaHtml(m) + '</td></tr>'; }
        }
    };

    async function carregar(chaves) {
        // Se uma carga completa estava em andamento, ela será cancelada: recarrega tudo para não deixar cartões desatualizados.
        if (estado.carregando) chaves = Object.keys(CARTOES);
        if (estado.controle) estado.controle.abort();
        const controle = new AbortController();
        estado.controle = controle;
        estado.carregando = true;
        marcarCarregando(true);
        $('dash-refresh').disabled = true;

        const resultados = await Promise.allSettled(chaves.map(async (k) => {
            const c = CARTOES[k];
            const r = await api(c.caminho, parametrosFiltro(c.extra ? c.extra() : null), controle.signal);
            if (controle.signal.aborted) return;
            c.render(r);
        }));
        if (controle.signal.aborted) return; // uma carga mais nova assumiu

        let acesso = null;
        resultados.forEach((res, i) => {
            if (res.status === 'rejected') {
                const e = res.reason;
                if (e && (e.status === 401 || e.status === 403)) acesso = e;
                CARTOES[chaves[i]].falha(e && e.message ? e.message : 'Falha ao carregar.');
            }
        });
        if (acesso && acesso.status === 401) {
            erroGlobal('Sua sessão expirou. <a href="' + esc(BASE) + '/login">Entrar novamente</a>.', true);
            pararAuto();
        } else if (acesso) {
            erroGlobal('Seu perfil não tem permissão para ver o dashboard. Ele é restrito a administradores e gerentes.');
            pararAuto();
        } else if (resultados.every((r) => r.status === 'rejected')) {
            const motivo = resultados[0].reason && resultados[0].reason.message;
            erroGlobal('Não foi possível carregar o dashboard. ' + (motivo || 'Verifique a conexão e tente novamente.'));
        } else {
            const vazio = $('dash-erro-global').textContent.indexOf('Ainda não há pedidos') >= 0;
            if (!vazio) erroGlobal(null);
        }
        $('dash-atualizado').textContent = 'Atualizado às ' + new Date().toLocaleTimeString('pt-BR');
        estado.carregando = false;
        estado.controle = null;
        marcarCarregando(false);
        $('dash-refresh').disabled = false;
    }

    function carregarTudo() {
        return carregar(Object.keys(CARTOES));
    }

    // Atualização automática: pausa quando a aba não está visível.
    function iniciarAuto() {
        pararAuto();
        estado.timer = setInterval(() => {
            if (!document.hidden && !estado.carregando) carregarTudo();
        }, INTERVALO_MS);
    }
    function pararAuto() {
        if (estado.timer) clearInterval(estado.timer);
        estado.timer = null;
    }

    // ------------------------------------------------------------------ eventos
    function alternar(grupoId, atributo, callback) {
        $(grupoId).addEventListener('click', (ev) => {
            const b = ev.target.closest('button[' + atributo + ']');
            if (!b) return;
            $(grupoId).querySelectorAll('button').forEach((x) => x.classList.toggle('active', x === b));
            callback(b.getAttribute(atributo));
        });
    }

    function ligarEventos() {
        $('dash-filtros').addEventListener('submit', (ev) => { ev.preventDefault(); aplicarFormulario(); });
        $('f-periodo').addEventListener('change', () => {
            const p = periodoPreset($('f-periodo').value);
            if (p) { $('f-inicio').value = p.inicio; $('f-fim').value = p.fim; }
            validarFormulario();
        });
        ['f-inicio', 'f-fim'].forEach((id) => $(id).addEventListener('change', () => {
            $('f-periodo').value = 'custom';
            validarFormulario();
        }));
        $('f-limpar').addEventListener('click', () => {
            const p = periodoPreset('30');
            estado.filtros = { periodo: '30', inicio: p.inicio, fim: p.fim, status: '', categoria: '', produto: '' };
            estado.agrupamento = '';
            preencherFormulario();
            $('f-erro').hidden = true;
            gravarUrl();
            carregarTudo();
        });
        $('dash-refresh').addEventListener('click', () => carregarTudo());
        $('dash-auto').addEventListener('change', (ev) => {
            try { localStorage.setItem('dashboard.auto', ev.target.checked ? '1' : '0'); } catch (e) { /* ignorado */ }
            if (ev.target.checked) iniciarAuto(); else pararAuto();
        });
        document.addEventListener('visibilitychange', () => {
            if (!document.hidden && $('dash-auto').checked && !estado.carregando) carregarTudo();
        });
        $('serie-agrupamento').addEventListener('change', (ev) => {
            estado.agrupamento = ev.target.value;
            gravarUrl();
            carregar(['serie']);
        });
        alternar('serie-metrica', 'data-metrica', (m) => { estado.metrica = m; renderSerie(); });
        alternar('ranking-ordem', 'data-ordem', (o) => { estado.ordem = o; carregar(['ranking']); });
        alternar('horarios-modo', 'data-modo', (m) => { estado.modoHorario = m; renderHorarios(); });
        $('dashboard').addEventListener('click', (ev) => {
            if (ev.target.closest('[data-retry]')) carregarTudo();
        });
    }

    async function iniciar() {
        lerUrl();
        if (!estado.filtros.inicio || !estado.filtros.fim) {
            const p = periodoPreset('30');
            estado.filtros.inicio = estado.filtros.inicio || p.inicio;
            estado.filtros.fim = estado.filtros.fim || p.fim;
        }
        preencherFormulario();
        $('f-inicio').max = hojeISO();
        $('f-fim').max = hojeISO();
        ligarEventos();
        let auto = true;
        try { auto = localStorage.getItem('dashboard.auto') !== '0'; } catch (e) { /* ignorado */ }
        $('dash-auto').checked = auto;
        await carregarOpcoes();
        await carregarTudo();
        if (auto) iniciarAuto();
    }

    if (document.readyState === 'loading') document.addEventListener('DOMContentLoaded', iniciar);
    else iniciar();
})();
