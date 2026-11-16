// Funções puras da avaliação: comparam a resposta da API com o golden set
// e calculam as métricas. Sem I/O, para poderem ser testadas isoladamente.

const PLACEHOLDER = /^<.*>$/;

const normalize = (text) => String(text ?? '').trim().toLowerCase();

/** Recusa = a API devolveu a lista de fontes vazia (contrato do POST /api/chat). */
export function isRefusal(response) {
  return !Array.isArray(response?.sources) || response.sources.length === 0;
}

/** Fonte correta = alguma fonte citada tem o documento e a página do gabarito. */
export function citesExpectedSource(response, expected) {
  if (!expected || !Array.isArray(response?.sources)) return false;
  return response.sources.some(
    (s) => normalize(s.document) === normalize(expected.document) && Number(s.page) === Number(expected.page),
  );
}

/** Lista o que ainda falta preencher num item do golden set. */
export function pendingFields(item) {
  const pending = [];
  if (!item.should_refuse) {
    if (!item.expected_answer || PLACEHOLDER.test(item.expected_answer.trim())) pending.push('expected_answer');
    if (!item.source?.document) pending.push('source.document');
    if (!(Number(item.source?.page) > 0)) pending.push('source.page');
  }
  return pending;
}

/** Avalia um item a partir da resposta da API (ou do erro da chamada). */
export function evaluateItem(item, { response, error }) {
  const base = {
    id: item.id,
    question: item.question,
    should_refuse: Boolean(item.should_refuse),
    expected_answer: item.expected_answer,
    expected_source: item.source ?? null,
    pending_fields: pendingFields(item),
  };
  if (error) {
    return { ...base, status: 'error', error: String(error.message ?? error) };
  }
  const refused = isRefusal(response);
  return {
    ...base,
    status: 'ok',
    answer: response.answer,
    sources: response.sources ?? [],
    refused,
    refusal_correct: refused === Boolean(item.should_refuse),
    source_correct: item.should_refuse ? null : citesExpectedSource(response, item.source),
    // Acerto da resposta: conferido manualmente na primeira rodada (plano do projeto).
    answer_correct: null,
  };
}

const ratio = (hits, total) => (total === 0 ? null : hits / total);

/** Agrega os resultados nas métricas do relatório. */
export function summarize(results) {
  const ok = results.filter((r) => r.status === 'ok');
  const answerable = ok.filter((r) => !r.should_refuse);
  const outOfScope = ok.filter((r) => r.should_refuse);
  const sourceHits = answerable.filter((r) => r.source_correct).length;
  const refusalHits = outOfScope.filter((r) => r.refused).length;
  const falseRefusals = answerable.filter((r) => r.refused).length;

  return {
    total: results.length,
    errors: results.length - ok.length,
    answerable: answerable.length,
    out_of_scope: outOfScope.length,
    source_accuracy: ratio(sourceHits, answerable.length),
    source_hits: sourceHits,
    refusal_accuracy: ratio(refusalHits, outOfScope.length),
    refusal_hits: refusalHits,
    false_refusals: falseRefusals,
    answer_accuracy: null,
    items_with_pending_fields: results.filter((r) => r.pending_fields.length > 0).length,
  };
}

const pct = (value) => (value === null ? 'n/a' : `${(value * 100).toFixed(1)}%`);
const cell = (text, max = 120) => {
  const clean = String(text ?? '').replace(/\s+/g, ' ').replace(/\|/g, '\\|').trim();
  return clean.length > max ? `${clean.slice(0, max - 1)}…` : clean;
};
const mark = (value) => (value === null || value === undefined ? '–' : value ? '✅' : '❌');

/** Gera o relatório em Markdown. */
export function renderMarkdown({ runAt, label, apiUrl, summary, results }) {
  const lines = [
    `# Avaliação — ${runAt}`,
    '',
    `- **Configuração:** ${label || '(sem rótulo)'}`,
    `- **API:** ${apiUrl}`,
    `- **Perguntas:** ${summary.total} (${summary.answerable} respondíveis, ${summary.out_of_scope} fora da base, ${summary.errors} com erro)`,
    '',
    '## Métricas',
    '',
    '| Métrica | Valor |',
    '| --- | --- |',
    `| Acerto da fonte | ${pct(summary.source_accuracy)} (${summary.source_hits}/${summary.answerable}) |`,
    `| Recusa correta | ${pct(summary.refusal_accuracy)} (${summary.refusal_hits}/${summary.out_of_scope}) |`,
    `| Recusas indevidas | ${summary.false_refusals} |`,
    '| Acerto da resposta | revisão manual (preencha a coluna abaixo) |',
    '',
  ];
  if (summary.items_with_pending_fields > 0) {
    lines.push(
      `> ⚠️ ${summary.items_with_pending_fields} item(ns) do golden set ainda têm campos a preencher; as métricas desses itens não são confiáveis.`,
      '',
    );
  }
  lines.push(
    '## Detalhe por pergunta',
    '',
    '| id | Pergunta | Recusou? | Recusa ok | Fonte ok | Resposta | Esperada | Resposta ok (manual) |',
    '| --- | --- | --- | --- | --- | --- | --- | --- |',
  );
  for (const r of results) {
    if (r.status === 'error') {
      lines.push(`| ${r.id} | ${cell(r.question)} | – | – | – | **erro:** ${cell(r.error)} | ${cell(r.expected_answer)} | |`);
      continue;
    }
    lines.push(
      `| ${r.id} | ${cell(r.question)} | ${r.refused ? 'sim' : 'não'} | ${mark(r.refusal_correct)} | ${mark(r.source_correct)} | ${cell(r.answer)} | ${cell(r.expected_answer)} | |`,
    );
  }
  return `${lines.join('\n')}\n`;
}
