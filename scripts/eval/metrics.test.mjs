// Testes da avaliação. Rodar com: node --test scripts/eval/metrics.test.mjs
import assert from 'node:assert/strict';
import { describe, it } from 'node:test';
import { citesExpectedSource, evaluateItem, isRefusal, pendingFields, renderMarkdown, summarize } from './metrics.mjs';
import { askApi, runEvaluation } from './run-eval.mjs';

const answerable = {
  id: 'est-01',
  question: 'A partir de qual período posso fazer estágio?',
  expected_answer: 'A partir do 3º período.',
  source: { document: 'Manual Institucional de Estágios 2025', page: 12 },
  should_refuse: false,
};
const outOfScope = {
  id: 'fora-01',
  question: 'Qual o salário médio de um estagiário?',
  expected_answer: '<o assistente deve recusar>',
  source: null,
  should_refuse: true,
};
const withSource = (document, page) => ({ answer: 'resposta', sources: [{ document, page, excerpt: '...' }] });
const refusal = { answer: 'Não encontrei essa informação.', sources: [] };

describe('isRefusal', () => {
  it('trata lista de fontes vazia ou ausente como recusa', () => {
    assert.equal(isRefusal(refusal), true);
    assert.equal(isRefusal({ answer: 'x' }), true);
    assert.equal(isRefusal(withSource('Doc', 1)), false);
  });
});

describe('citesExpectedSource', () => {
  it('acerta quando documento e página batem, ignorando caixa e espaços', () => {
    assert.equal(citesExpectedSource(withSource(' manual institucional de estágios 2025 ', 12), answerable.source), true);
  });
  it('erra quando a página é outra', () => {
    assert.equal(citesExpectedSource(withSource('Manual Institucional de Estágios 2025', 13), answerable.source), false);
  });
  it('erra quando o documento é outro', () => {
    assert.equal(citesExpectedSource(withSource('Manual de Graduação', 12), answerable.source), false);
  });
  it('acerta se qualquer uma das fontes citadas bater', () => {
    const response = {
      answer: 'x',
      sources: [
        { document: 'Manual de Graduação', page: 3 },
        { document: 'Manual Institucional de Estágios 2025', page: 12 },
      ],
    };
    assert.equal(citesExpectedSource(response, answerable.source), true);
  });
});

describe('pendingFields', () => {
  it('aponta placeholders e página 0 em perguntas respondíveis', () => {
    const item = { ...answerable, expected_answer: '<preencher lendo o manual>', source: { document: 'Doc', page: 0 } };
    assert.deepEqual(pendingFields(item), ['expected_answer', 'source.page']);
  });
  it('não exige resposta nem fonte em perguntas fora da base', () => {
    assert.deepEqual(pendingFields(outOfScope), []);
  });
});

describe('evaluateItem', () => {
  it('pergunta respondível com fonte correta', () => {
    const r = evaluateItem(answerable, { response: withSource('Manual Institucional de Estágios 2025', 12) });
    assert.equal(r.refused, false);
    assert.equal(r.refusal_correct, true);
    assert.equal(r.source_correct, true);
    assert.equal(r.answer_correct, null);
  });
  it('recusa indevida em pergunta respondível', () => {
    const r = evaluateItem(answerable, { response: refusal });
    assert.equal(r.refused, true);
    assert.equal(r.refusal_correct, false);
    assert.equal(r.source_correct, false);
  });
  it('recusa correta em pergunta fora da base', () => {
    const r = evaluateItem(outOfScope, { response: refusal });
    assert.equal(r.refusal_correct, true);
    assert.equal(r.source_correct, null);
  });
  it('pergunta fora da base respondida (alucinação) conta como erro de recusa', () => {
    const r = evaluateItem(outOfScope, { response: withSource('Manual de Graduação', 5) });
    assert.equal(r.refusal_correct, false);
  });
  it('registra erro de chamada', () => {
    const r = evaluateItem(answerable, { error: new Error('HTTP 500') });
    assert.equal(r.status, 'error');
    assert.equal(r.error, 'HTTP 500');
  });
});

describe('summarize', () => {
  it('calcula acerto de fonte, recusa correta e recusas indevidas', () => {
    const second = { ...answerable, id: 'est-02' };
    const results = [
      evaluateItem(answerable, { response: withSource('Manual Institucional de Estágios 2025', 12) }),
      evaluateItem(second, { response: refusal }),
      evaluateItem(outOfScope, { response: refusal }),
      evaluateItem({ ...outOfScope, id: 'fora-02' }, { error: new Error('timeout') }),
    ];
    const s = summarize(results);
    assert.equal(s.total, 4);
    assert.equal(s.errors, 1);
    assert.equal(s.answerable, 2);
    assert.equal(s.out_of_scope, 1);
    assert.equal(s.source_accuracy, 0.5);
    assert.equal(s.refusal_accuracy, 1);
    assert.equal(s.false_refusals, 1);
  });
  it('devolve null quando não há itens para a métrica', () => {
    const s = summarize([evaluateItem(outOfScope, { response: refusal })]);
    assert.equal(s.source_accuracy, null);
  });
});

describe('runEvaluation', () => {
  it('envia cada pergunta e continua mesmo se uma chamada falhar', async () => {
    const asked = [];
    const ask = async (question) => {
      asked.push(question);
      if (question === outOfScope.question) throw new Error('HTTP 503');
      return withSource('Manual Institucional de Estágios 2025', 12);
    };
    const { results, summary } = await runEvaluation([answerable, outOfScope], ask);
    assert.deepEqual(asked, [answerable.question, outOfScope.question]);
    assert.equal(results[0].source_correct, true);
    assert.equal(results[1].status, 'error');
    assert.equal(summary.errors, 1);
  });
});

describe('askApi', () => {
  it('faz POST em /api/chat com a pergunta em JSON', async () => {
    let call;
    const fakeFetch = async (url, init) => {
      call = { url, init };
      return { ok: true, json: async () => refusal };
    };
    const body = await askApi('http://localhost:8080/', 'Quem é o reitor?', fakeFetch);
    assert.equal(call.url, 'http://localhost:8080/api/chat');
    assert.equal(call.init.method, 'POST');
    assert.deepEqual(JSON.parse(call.init.body), { question: 'Quem é o reitor?' });
    assert.deepEqual(body, refusal);
  });
  it('lança erro com o status quando a API não responde 2xx', async () => {
    const fakeFetch = async () => ({ ok: false, status: 400 });
    await assert.rejects(askApi('http://localhost:8080', 'x', fakeFetch), /HTTP 400/);
  });
});

describe('renderMarkdown', () => {
  it('mostra as métricas, avisa sobre campos pendentes e escapa "|"', () => {
    const results = [
      evaluateItem({ ...answerable, question: 'a | b', expected_answer: '<preencher lendo o manual>' }, { response: refusal }),
    ];
    const md = renderMarkdown({ runAt: 'agora', label: 'chunk800', apiUrl: 'http://x', summary: summarize(results), results });
    assert.match(md, /\*\*Configuração:\*\* chunk800/);
    assert.match(md, /Acerto da fonte \| 0\.0% \(0\/1\)/);
    assert.match(md, /campos a preencher/);
    assert.match(md, /a \\\| b/);
  });
});
