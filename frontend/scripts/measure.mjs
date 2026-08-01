/**
 * 성능 목표 측정.
 *
 * 원문이 지정한 조건을 그대로 고정한다 — production build(vite preview) · CPU 4x throttling ·
 * 캐시 비활성화 · warm-up 한 번을 버리고 3회 중위값.
 *
 *   npm run build && npm run preview   # 다른 터미널
 *   npm run measure
 *
 * 목표를 넘기면 종료 코드 1. 참고용 항목(메모리)은 판정에서 제외한다.
 */
import { writeFileSync } from 'node:fs'
import { chromium } from 'playwright'

const URL = process.env.MEASURE_URL ?? 'http://localhost:4173/'
const RUNS = 3
const CPU_THROTTLE = 4
const VIEWPORT = { width: 1440, height: 900 }

const results = []

/** 첫 회는 버리고 중위값을 쓴다. JIT·커넥션 초기화를 측정에서 뺀다. */
async function repeat(runner) {
  await runner()
  const taken = []
  for (let i = 0; i < RUNS; i++) taken.push(await runner())
  taken.sort((a, b) => a - b)
  return { median: taken[Math.floor(RUNS / 2)], runs: taken }
}

async function withPage(browser, work) {
  const context = await browser.newContext({ viewport: VIEWPORT })
  const page = await context.newPage()
  const cdp = await context.newCDPSession(page)
  await cdp.send('Emulation.setCPUThrottlingRate', { rate: CPU_THROTTLE })
  await cdp.send('Network.setCacheDisabled', { cacheDisabled: true })
  try {
    return await work(page, cdp)
  } finally {
    await context.close()
  }
}

/** 스로틀링이 실제로 걸렸는지 먼저 확인한다. 안 걸린 채 잰 수치는 의미가 없다. */
async function verifyThrottling(browser) {
  const busy = () => {
    const started = performance.now()
    let sum = 0
    for (let i = 0; i < 8_000_000; i++) sum += i % 7
    return { took: performance.now() - started, sum }
  }
  const context = await browser.newContext({ viewport: VIEWPORT })
  const page = await context.newPage()
  const cdp = await context.newCDPSession(page)
  await page.goto(URL, { waitUntil: 'domcontentloaded' })

  await page.evaluate(busy) // JIT 워밍업. 이걸 안 버리면 1x 기준이 부풀어 배율이 낮게 나온다
  const plain = (await page.evaluate(busy)).took
  await cdp.send('Emulation.setCPUThrottlingRate', { rate: CPU_THROTTLE })
  await page.evaluate(busy)
  const throttled = (await page.evaluate(busy)).took
  await context.close()

  return { plain: +plain.toFixed(1), throttled: +throttled.toFixed(1), ratio: +(throttled / plain).toFixed(2) }
}

/** ① 최초 진입 → 클러스터 목록이 보이고 상호작용 가능 (500ms) */
async function firstInteractive(browser) {
  return repeat(() =>
    withPage(browser, async (page) => {
      await page.goto(URL, { waitUntil: 'commit' })
      await page.waitForSelector('body[data-first-interactive]', { timeout: 30_000 })
      return page.evaluate(() => performance.getEntriesByName('firstInteractive')[0].startTime)
    }),
  )
}

function record(item, target, unit, measured, note = '') {
  const passed = measured.median <= target
  results.push({ item, target, unit, ...measured, passed, note })
  const mark = passed ? '✅' : '❌'
  console.log(`${mark} ${item.padEnd(34)} 목표 ${String(target).padStart(4)}${unit}  중위 ${measured.median.toFixed(1)}${unit}`)
}

const browser = await chromium.launch({ channel: 'chrome' })
try {
  const throttling = await verifyThrottling(browser)
  console.log(`CPU 스로틀링 교정: 1x ${throttling.plain}ms → ${CPU_THROTTLE}x ${throttling.throttled}ms (실측 배율 ${throttling.ratio})\n`)

  record('① 최초 진입 → 상호작용 가능', 500, 'ms', await firstInteractive(browser))

  const output = {
    조건: {
      URL,
      'CPU throttling': `${CPU_THROTTLE}x (실측 ${throttling.ratio})`,
      캐시: '비활성화',
      뷰포트: `${VIEWPORT.width}x${VIEWPORT.height}`,
      반복: `warm-up 1회 버리고 ${RUNS}회 중위값`,
    },
    결과: results,
  }
  writeFileSync('measure-result.json', JSON.stringify(output, null, 2) + '\n')
  console.log('\n→ measure-result.json')

  if (results.some((r) => !r.passed && !r.note.includes('참고용'))) process.exit(1)
} finally {
  await browser.close()
}
