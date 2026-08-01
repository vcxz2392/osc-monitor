/**
 * 화면 동작 회귀 확인.
 *
 * 성능 스크립트는 재기만 하고 값이 나빠져도 알려주지 않는다.
 * 여기서는 <b>깨지면 실패로 끝나야 하는 규칙</b>만 골라 실제 브라우저에서 단언한다.
 * 고른 기준은 하나다 — <b>눈으로 봐서는 회귀를 알 수 없는 것.</b>
 *
 *   npm run build && npm run preview   # 다른 터미널
 *   npm run check
 */
import { chromium } from 'playwright'

const URL = process.env.MEASURE_URL ?? 'http://localhost:4173/'
const VIEWPORT = { width: 1440, height: 900 }
const failures = []

function check(name, passed, detail) {
  console.log(`${passed ? '✅' : '❌'} ${name}${detail ? `  — ${detail}` : ''}`)
  if (!passed) failures.push(name)
}

const totalRows = (page) => page.evaluate(() => Number(document.querySelector('.app-count').textContent.replace(/[^0-9]/g, '')))
const domRows = (page) => page.evaluate(() => document.querySelectorAll('.row').length)
const hud = (page) => page.evaluate(() => {
  const text = document.querySelector('.hud')?.textContent ?? ''
  const [, changed, merged, rerendered] = text.match(/변경 (\d+)건 · 반영 (\d+)건 → 리렌더 (\d+)행/) ?? []
  return { changed: +changed, merged: +merged, rerendered: +rerendered, raw: text }
})

const expandDeep = async (page, depth = 3) => {
  for (let i = 0; i < depth; i++) {
    await page.locator('.row-arrow[data-collapsed]').first().click()
    await page.waitForTimeout(250)
  }
}

const browser = await chromium.launch({ channel: 'chrome' })
const context = await browser.newContext({ viewport: VIEWPORT })
const page = await context.newPage()

const requests = []
page.on('request', (request) => {
  // data:·blob: 같은 요청은 URL 로 파싱되지 않는다. 우리가 볼 것은 /api 뿐이다.
  try {
    requests.push(new URL(request.url()).pathname)
  } catch {
    /* 무시 */
  }
})

await page.goto(URL)
await page.waitForSelector('body[data-first-interactive]')

// ── 1. 많은 행을 DOM 에 그리지 않는다 (느려질 뿐 눈에는 안 보인다)
await expandDeep(page, 3)
{
  const total = await totalRows(page)
  const dom = await domRows(page)
  check('많은 행을 DOM 에 그리지 않는다', dom < 100, `${total.toLocaleString()}행 중 DOM ${dom}행`)
}

// ── 2. 화면 밖 변경은 다시 그리지 않는다 (전부 다시 그려도 똑같아 보인다)
{
  await page.waitForTimeout(4500)
  const stats = await hud(page)
  check(
    '화면 밖 변경은 다시 그리지 않는다',
    stats.merged > 0 && stats.rerendered < stats.merged,
    stats.raw.replace('rev', 'rev ').trim(),
  )
}

// ── 3. 갱신 주기가 조작 중에도 유지된다 (조작을 멈추면 다시 돌아 눈치채기 어렵다)
{
  const before = await page.evaluate(() => performance.getEntriesByName('delta').length)
  const until = Date.now() + 11_000
  while (Date.now() < until) {
    // 갱신 주기(2초)보다 짧은 간격으로 계속 건드린다. 주기와 같은 간격이면 타이머가 아슬아슬 먼저 발화해 통과해 버린다.
    await page.locator('.row').first().click()
    await page.waitForTimeout(700)
  }
  const after = await page.evaluate(() => performance.getEntriesByName('delta').length)
  check('갱신 주기가 조작 중에도 유지된다', after > before, `11초 동안 델타 ${after - before}회`)
}

// ── 4. 경로는 선택이 바뀔 때만 다시 부른다 (요청만 늘고 화면은 똑같다)
{
  await page.locator('.row .row-name').nth(2).click()
  await page.waitForSelector('.detail')
  const base = requests.filter((path) => path.endsWith('/ancestors')).length
  await page.waitForTimeout(6000) // 델타가 두세 번 도는 동안
  const after = requests.filter((path) => path.endsWith('/ancestors')).length
  check('경로는 선택이 바뀔 때만 다시 부른다', after === base, `6초 동안 추가 요청 ${after - base}건`)
}

// ── 5. 검색 결과를 고르면 그 위치까지 펼쳐진다
{
  await page.fill('.search-input', 'ns-auth-07')
  await page.waitForSelector('.row-result')
  const target = await page.locator('.row-result').first().innerText()
  await page.locator('.row-result').first().click()
  // 앞 단계의 선택이 남아 있으므로 "선택된 행이 있다" 로는 부족하다. 그 행이 대상인지까지 본다.
  await page
    .waitForFunction(() => document.querySelector('.row-selected')?.innerText.includes('ns-auth-07'), null, {
      timeout: 8000,
    })
    .catch(() => {})
  const selected = await page.$eval('.row-selected', (el) => el.innerText).catch(() => '')
  const path = await page.$eval('.detail-path', (el) => el.innerText).catch(() => '')
  check('검색 결과를 고르면 그 위치까지 펼쳐진다', selected.includes('ns-auth-07'), path || target.split('\n')[1])
}

// ── 6. 필터를 바꿔도 목록이 비었다 다시 차지 않는다
{
  const counts = []
  const timer = setInterval(async () => counts.push(await totalRows(page).catch(() => -1)), 40)
  await page.locator('.chip-error').click()
  await page.waitForTimeout(1200)
  clearInterval(timer)
  const dipped = counts.some((count) => count === 0)
  check('필터를 바꿔도 목록이 비지 않는다', !dipped, `관찰 ${counts.length}회, 최소 ${Math.min(...counts.filter((c) => c >= 0))}행`)
}

// ── 7. 펼칠 때 목록이 두 번 밀리지 않는다 (한 프레임 차이라 거의 안 보인다)
{
  await page.locator('.chip-error').click() // 필터 해제
  await page.waitForTimeout(600)
  const before = await totalRows(page)
  const lengths = []
  const timer = setInterval(async () => lengths.push(await totalRows(page).catch(() => -1)), 25)
  await page.locator('.row-arrow[data-collapsed]').first().click()
  await page.waitForTimeout(900)
  clearInterval(timer)
  const distinct = [...new Set(lengths.filter((n) => n > 0))]
  check(
    '펼칠 때 목록이 두 번 밀리지 않는다',
    distinct.length <= 2,
    `길이 변화 ${before} → ${distinct.filter((n) => n !== before).join(' → ') || '없음'}`,
  )
}

await browser.close()
console.log(failures.length === 0 ? '\n전부 통과' : `\n실패 ${failures.length}건: ${failures.join(', ')}`)
if (failures.length > 0) process.exit(1)
