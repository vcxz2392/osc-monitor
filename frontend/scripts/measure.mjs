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
const ROW_HEIGHT = 36

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

/** 상호작용 한 번을 클릭 직전부터 "그려진 다음 페인트" 까지 잰다. */
async function interaction(page, act, settled) {
  await page.evaluate(() => performance.clearMarks())
  await page.evaluate(() => performance.mark('act:start'))
  await act()
  await settled()
  return page.evaluate(
    () =>
      new Promise((resolve) =>
        requestAnimationFrame(() =>
          setTimeout(() => {
            performance.mark('act:end')
            resolve(performance.measure('act', 'act:start', 'act:end').duration)
          }, 0),
        ),
      ),
  )
}

const rowCount = (page) => page.evaluate(() => document.querySelectorAll('.row').length)
const totalRows = (page) =>
  page.evaluate(() => Number(document.querySelector('.app-count').textContent.replace(/[^0-9]/g, '')))

const clickCollapsedInView = (page) =>
  page.evaluate(() => {
    // 아직 안 펼친 화살표만 고른다. 표시 문자를 비교하면 문자를 바꿀 때 자동화가 조용히 깨진다.
    const arrows = [...document.querySelectorAll('.row-arrow[data-collapsed]')].reverse()
    for (const arrow of arrows) arrow.click()
    return arrows.length
  })

/**
 * 10만 행을 실제로 만든다.
 *
 * 가상 스크롤이라 DOM 에는 30여 행뿐이므로 화면을 훑으며 펼쳐야 한다.
 * <b>아래에서 위로</b> 가는 것이 요령이다 — 펼치면 새 행이 항상 현재 위치 아래에 생기므로
 * 위로 이동하면 아직 안 펼친 것들의 자리가 흔들리지 않는다.
 */
async function expandAll(page) {
  const height = await page.evaluate(() => document.querySelector('.tree').clientHeight)
  for (let level = 0; level < 3; level++) {
    let top = Math.max(0, (await totalRows(page)) * ROW_HEIGHT - height)
    while (top >= 0) {
      await page.evaluate((y) => { document.querySelector('.tree').scrollTop = y }, top)
      await page.waitForTimeout(30)
      if ((await clickCollapsedInView(page)) > 0) await page.waitForTimeout(60)
      if (top === 0) break
      top = Math.max(0, top - height * 0.85)
    }
    for (let i = 0; i < 3; i++) {
      await page.waitForTimeout(200)
      if ((await clickCollapsedInView(page)) === 0) break
    }
  }
  return totalRows(page)
}

/** 앱이 심어 둔 User Timing 측정값을 읽는다. 디바운스처럼 재면 안 되는 구간을 앱이 알고 있다. */
const userTiming = (page, name) =>
  page.evaluate((n) => performance.getEntriesByName(n).at(-1).duration, name)

/** ③ 검색어 입력 → 결과 표시 (디바운스 제외, 300ms) */
async function search(browser) {
  return repeat(() =>
    withPage(browser, async (page) => {
      await page.goto(URL, { waitUntil: 'commit' })
      await page.waitForSelector('body[data-first-interactive]')
      await page.fill('.search-input', 'api')
      await page.waitForSelector('.row-result')
      await page.waitForFunction(() => performance.getEntriesByName('search').length > 0)
      return userTiming(page, 'search')
    }),
  )
}

/** ④ 필터 적용 → 화면 반영 (200ms) */
async function filter(browser) {
  return repeat(() =>
    withPage(browser, async (page) => {
      await page.goto(URL, { waitUntil: 'commit' })
      await page.waitForSelector('body[data-first-interactive]')
      // 파드까지 펼쳐 둔 상태에서 필터를 건다. 루트만 있는 화면은 갈아끼울 것이 20행뿐이다.
      for (let level = 0; level < 3; level++) {
        await page.locator('.row-arrow[data-collapsed]').first().click()
        await page.waitForTimeout(150)
      }
      await page.evaluate(() => performance.clearMeasures('filter'))
      await page.locator('.chip-error').click()
      await page.waitForFunction(() => performance.getEntriesByName('filter').length > 0)
      return userTiming(page, 'filter')
    }),
  )
}

/** ⑥ 데이터 갱신 → 화면 반영 (200ms, 깜빡임 없음) */
async function delta(browser) {
  return withPage(browser, async (page) => {
    await page.goto(URL, { waitUntil: 'commit' })
    await page.waitForSelector('body[data-first-interactive]')
    for (let level = 0; level < 3; level++) {
      await page.locator('.row-arrow[data-collapsed]').first().click()
      await page.waitForTimeout(200)
    }
    // 변경이 실제로 있었던 폴링만 앱이 잰다. 그런 폴링이 RUNS 번 쌓일 때까지 기다린다.
    await page.waitForFunction((n) => performance.getEntriesByName('delta').length >= n, RUNS, { timeout: 60_000 })
    const durations = await page.evaluate(() => performance.getEntriesByName('delta').map((entry) => entry.duration))
    const hud = await page.textContent('.hud')
    durations.sort((a, b) => a - b)
    return { median: durations[Math.floor(durations.length / 2)], runs: durations, hud: hud.trim() }
  })
}

/**
 * ⑤ 10만 건 규모 리스트 스크롤 (55fps)
 *
 * fps 절대값이 아니라 <b>프레임 간격이 18.2ms 를 넘긴 횟수</b>로 판정한다.
 * 디스플레이 주사율에 따라 fps 상한이 60/120/144 로 달라지기 때문이다.
 */
async function scrollFrames(browser) {
  return withPage(browser, async (page) => {
    await page.goto(URL, { waitUntil: 'commit' })
    await page.waitForSelector('body[data-first-interactive]')

    const rows = await expandAll(page)
    const domRows = await rowCount(page)

    const passes = []
    for (let i = 0; i < RUNS; i++) {
      await page.evaluate(() => { document.querySelector('.tree').scrollTop = 0 })
      await page.waitForTimeout(300)
      passes.push(
        await page.evaluate(
          () =>
            new Promise((resolve) => {
              const tree = document.querySelector('.tree')
              const gaps = []
              let last = performance.now()
              let ticks = 0
              const step = () => {
                const now = performance.now()
                gaps.push(now - last)
                last = now
                tree.scrollTop += 240 // 실제 휠 입력에 가까운 이동량을 매 프레임
                if (++ticks < 240) requestAnimationFrame(step)
                else {
                  gaps.shift()
                  const sorted = [...gaps].sort((a, b) => a - b)
                  resolve({
                    frames: gaps.length,
                    medianGap: sorted[Math.floor(sorted.length / 2)],
                    worstGap: sorted[sorted.length - 1],
                    over: gaps.filter((gap) => gap > 18.2).length,
                  })
                }
              }
              requestAnimationFrame(step)
            }),
        ),
      )
    }
    const median = (key) => [...passes].map((p) => p[key]).sort((a, b) => a - b)[Math.floor(RUNS / 2)]
    return {
      rows,
      domRows,
      medianFps: 1000 / median('medianGap'),
      over: median('over'),
      frames: passes[0].frames,
      perPass: passes.map((p) => p.over),
    }
  })
}

/** ② 하위 계층 펼치기 (100ms) */
async function expand(browser) {
  return repeat(() =>
    withPage(browser, async (page) => {
      await page.goto(URL, { waitUntil: 'commit' })
      await page.waitForSelector('body[data-first-interactive]')
      const before = await rowCount(page)
      return interaction(
        page,
        () => page.locator('.row-arrow[data-collapsed]').first().click(),
        () => page.waitForFunction((n) => document.querySelectorAll('.row').length > n, before),
      )
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
  record('② 하위 계층 펼치기', 100, 'ms', await expand(browser))
  record('③ 검색 → 결과 표시 (디바운스 제외)', 300, 'ms', await search(browser))
  record('④ 필터 적용 → 화면 반영', 200, 'ms', await filter(browser))
  const deltaResult = await delta(browser)
  record('⑥ 데이터 갱신 → 화면 반영', 200, 'ms', deltaResult, deltaResult.hud)
  console.log(`   ${deltaResult.hud}`)

  const scroll = await scrollFrames(browser)
  results.push({ item: '⑤ 10만 건 스크롤', target: 55, unit: 'fps', median: scroll.medianFps, ...scroll, passed: scroll.medianFps >= 55 && scroll.over === 0 })
  console.log(
    `${scroll.medianFps >= 55 && scroll.over === 0 ? '✅' : '❌'} ⑤ 10만 건 스크롤${' '.repeat(19)}목표   55fps  중위 ${scroll.medianFps.toFixed(1)}fps · 18.2ms 초과 ${scroll.over}/${scroll.frames} · ${scroll.rows.toLocaleString()}행 중 DOM ${scroll.domRows}행`,
  )

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
