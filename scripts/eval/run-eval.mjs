// Envia cada pergunta do golden set para POST /api/chat e grava
// report.json e report.md em <results-dir>/<data-hora>/.
// Uso direto: node scripts/eval/run-eval.mjs --api-url ... --golden-set ... --results-dir ...
// (normalmente chamado por scripts/run-eval.sh)

import { mkdir, readFile, writeFile } from 'node:fs/promises';
import { join } from 'node:path';
import { parseArgs } from 'node:util';
import { pathToFileURL } from 'node:url';
import { evaluateItem, renderMarkdown, summarize } from './metrics.mjs';

const REQUEST_TIMEOUT_MS = 60_000;

export async function askApi(apiUrl, question, fetchFn = fetch) {
  const res = await fetchFn(`${apiUrl.replace(/\/$/, '')}/api/chat`, {
    method: 'POST',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify({ question }),
    signal: AbortSignal.timeout(REQUEST_TIMEOUT_MS),
  });
  if (!res.ok) {
    throw new Error(`HTTP ${res.status}`);
  }
  return res.json();
}

export async function runEvaluation(goldenSet, ask, { delayMs = 0, log = () => {} } = {}) {
  const results = [];
  for (const [index, item] of goldenSet.entries()) {
    if (index > 0 && delayMs > 0) await new Promise((r) => setTimeout(r, delayMs));
    log(`[${index + 1}/${goldenSet.length}] ${item.id}`);
    try {
      results.push(evaluateItem(item, { response: await ask(item.question) }));
    } catch (error) {
      results.push(evaluateItem(item, { error }));
    }
  }
  return { results, summary: summarize(results) };
}

function validateGoldenSet(data) {
  if (!Array.isArray(data) || data.length === 0) throw new Error('O golden set deve ser uma lista não vazia.');
  const ids = new Set();
  for (const item of data) {
    if (!item.id || !item.question) throw new Error(`Item sem id ou question: ${JSON.stringify(item)}`);
    if (ids.has(item.id)) throw new Error(`id duplicado no golden set: ${item.id}`);
    ids.add(item.id);
  }
}

async function main() {
  const { values } = parseArgs({
    options: {
      'api-url': { type: 'string', default: 'http://localhost:8080' },
      'golden-set': { type: 'string', default: 'eval/golden-set.json' },
      'results-dir': { type: 'string', default: 'eval/results' },
      'delay-ms': { type: 'string', default: '0' },
      label: { type: 'string', default: '' },
    },
  });

  const goldenSet = JSON.parse(await readFile(values['golden-set'], 'utf8'));
  validateGoldenSet(goldenSet);

  const apiUrl = values['api-url'];
  const { results, summary } = await runEvaluation(goldenSet, (q) => askApi(apiUrl, q), {
    delayMs: Number(values['delay-ms']) || 0,
    log: (msg) => console.log(msg),
  });

  const runAt = new Date().toISOString();
  const outDir = join(values['results-dir'], runAt.replace(/[:.]/g, '-'));
  await mkdir(outDir, { recursive: true });
  const report = { runAt, label: values.label, apiUrl, summary, results };
  await writeFile(join(outDir, 'report.json'), `${JSON.stringify(report, null, 2)}\n`);
  await writeFile(join(outDir, 'report.md'), renderMarkdown(report));

  console.log(`\nRelatório salvo em ${outDir}`);
  console.log(JSON.stringify(summary, null, 2));
  if (summary.errors === summary.total) {
    console.error('\nTodas as chamadas falharam. A API está rodando em', apiUrl, '?');
    process.exitCode = 1;
  }
}

if (import.meta.url === pathToFileURL(process.argv[1]).href) {
  main().catch((err) => {
    console.error(`Erro: ${err.message}`);
    process.exit(1);
  });
}
