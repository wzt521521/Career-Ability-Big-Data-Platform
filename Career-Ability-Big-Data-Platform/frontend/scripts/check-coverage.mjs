import { readFileSync } from 'node:fs'

const summary = JSON.parse(readFileSync('coverage/coverage-summary.json', 'utf8'))
const targets = [
  { path: 'src/views/recommend/RecommendView.vue', lines: 75, branches: 70 },
  { path: 'src/views/report/ReportCenterView.vue', lines: 70, branches: 50 },
]

const failures = []

for (const target of targets) {
  const entry = Object.entries(summary).find(([file]) =>
    file.replaceAll('\\', '/').endsWith(target.path),
  )?.[1]

  if (!entry) {
    failures.push(`${target.path}: coverage entry was not generated`)
    continue
  }

  for (const metric of ['lines', 'branches']) {
    if (entry[metric].pct < target[metric]) {
      failures.push(
        `${target.path}: ${metric} ${entry[metric].pct}% is below ${target[metric]}%`,
      )
    }
  }
}

if (failures.length > 0) {
  console.error('Critical UI coverage gate failed:\n' + failures.join('\n'))
  process.exit(1)
}

console.log('Critical UI coverage gate passed.')
