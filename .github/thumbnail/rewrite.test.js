'use strict'

// Run with: node --test .github/thumbnail/
// Covers the parsing and validation logic. The network probe is not exercised.

const test = require('node:test')
const assert = require('node:assert')

const { rewrite, allowedUrl, thumbnailWidth, codeLineIndices, escapeAttr } = require('./rewrite')

const ATTACHMENT = 'https://github.com/user-attachments/assets/0e3b1f2a-1111-2222-3333-444455556666'
const ATTACHMENT2 = 'https://github.com/user-attachments/assets/9a9a9a9a-5555-6666-7777-888899990000'

// Stands in for the network probe. 1080x2400 is a typical phone screenshot and
// yields width=180.
const tallProbe = { probe: async () => ({ width: 1080, height: 2400 }) }

test('rewrite replaces an eligible image with a clickable thumbnail', async () => {
  const result = await rewrite(`before\n\n![my "shot" & co](${ATTACHMENT})\n\nafter`, tallProbe)
  assert.strictEqual(result.changed, true)
  assert.strictEqual(
    result.body,
    'before\n\n' +
      `<a href="${ATTACHMENT}"><img src="${ATTACHMENT}" alt="my &quot;shot&quot; &amp; co" width="180"></a>` +
      '\n\nafter',
  )
  // Quotes in the alt text must not break out of the attribute.
  assert.ok(!result.body.includes('alt="my "shot"'))
  // Height is never emitted: with width it fights GitHub's max-width:100%.
  assert.ok(!result.body.includes('height='))
})

test('rewrite handles several images on one line without corrupting offsets', async () => {
  const result = await rewrite(`![a](${ATTACHMENT}) middle ![b](${ATTACHMENT2})`, tallProbe)
  assert.strictEqual(result.changed, true)
  assert.strictEqual(
    result.body,
    `<a href="${ATTACHMENT}"><img src="${ATTACHMENT}" alt="a" width="180"></a>` +
      ' middle ' +
      `<a href="${ATTACHMENT2}"><img src="${ATTACHMENT2}" alt="b" width="180"></a>`,
  )
})

test('rewrite is idempotent: its own output does not rematch', async () => {
  const once = await rewrite(`![a](${ATTACHMENT})`, tallProbe)
  assert.strictEqual(once.changed, true)
  const twice = await rewrite(once.body, tallProbe)
  assert.strictEqual(twice.changed, false)
})

test('rewrite leaves escaped and already-linked images alone', async () => {
  // Escaped: not an image at all.
  const escaped = `\\![shot](${ATTACHMENT})`
  assert.deepStrictEqual(await rewrite(escaped, tallProbe), { changed: false, body: escaped })

  // Already a linked image: rewriting would discard the author's link target.
  const linked = `[![shot](${ATTACHMENT})](https://example.com/details)`
  assert.deepStrictEqual(await rewrite(linked, tallProbe), { changed: false, body: linked })
})

test('rewrite ignores images in a fence opened inside a list item', async () => {
  const body = ['- example:', '  ```md', `  ![shot](${ATTACHMENT})`, '  ```', '', 'prose'].join('\n')
  assert.deepStrictEqual(await rewrite(body, tallProbe), { changed: false, body })
})

test('a stray fence in a list does not hide later real images', async () => {
  const body = ['- ```md', '  sample', '  ```', '', `![real](${ATTACHMENT})`].join('\n')
  const result = await rewrite(body, tallProbe)
  assert.strictEqual(result.changed, true)
  assert.ok(result.body.includes('<img src='), 'image after the list fence should still be rewritten')
  assert.ok(result.body.includes('  sample'), 'the fenced sample must be untouched')
})

test('rewrite skips images whose probe fails', async () => {
  const body = `![shot](${ATTACHMENT})`
  const failing = { probe: async () => null }
  assert.deepStrictEqual(await rewrite(body, failing), { changed: false, body })
})

test('allowedUrl accepts GitHub attachment hosts', () => {
  assert.strictEqual(allowedUrl(ATTACHMENT), ATTACHMENT)
  assert.strictEqual(
    allowedUrl('https://user-images.githubusercontent.com/1/2.png'),
    'https://user-images.githubusercontent.com/1/2.png',
  )
})

test('allowedUrl rejects lookalike and non-GitHub hosts', () => {
  // Suffix confusion: passes a naive includes('github.com') check.
  assert.strictEqual(allowedUrl('https://github.com.evil.tld/user-attachments/assets/x'), null)
  // Fragment confusion: also passes a naive includes() check.
  assert.strictEqual(allowedUrl('https://evil.tld/x.png#github.com'), null)
  // Subdomain that is not on the list.
  assert.strictEqual(allowedUrl('https://raw.githubusercontent.com/o/r/main/x.png'), null)
  // Wrong path on an allowed host.
  assert.strictEqual(allowedUrl('https://github.com/owner/repo/raw/main/x.png'), null)
  // Path traversal is normalised by URL parsing before the prefix check.
  assert.strictEqual(allowedUrl('https://github.com/user-attachments/assets/../../x.png'), null)
  // Non-https schemes.
  assert.strictEqual(allowedUrl('http://github.com/user-attachments/assets/x'), null)
  assert.strictEqual(allowedUrl('file:///etc/passwd'), null)
  // Embedded credentials.
  assert.strictEqual(allowedUrl('https://u:p@github.com/user-attachments/assets/x'), null)
  // Cloud metadata endpoint.
  assert.strictEqual(allowedUrl('http://169.254.169.254/metadata/instance'), null)
  assert.strictEqual(allowedUrl('not a url'), null)
})

test('escapeAttr neutralises attribute breakout', () => {
  assert.strictEqual(
    escapeAttr('" onerror="alert(1)'),
    '&quot; onerror=&quot;alert(1)',
  )
  assert.strictEqual(escapeAttr('a & b <c>'), 'a &amp; b &lt;c&gt;')
})

test('thumbnailWidth only shrinks tall portrait images', () => {
  assert.strictEqual(thumbnailWidth({ width: 1080, height: 2400 }), 180)
  // Landscape is already constrained by the comment column.
  assert.strictEqual(thumbnailWidth({ width: 1920, height: 1080 }), null)
  // Portrait but small enough already.
  assert.strictEqual(thumbnailWidth({ width: 200, height: 400 }), null)
  // Would scale up rather than down.
  assert.strictEqual(thumbnailWidth({ width: 100, height: 700 }), null)
})

test('thumbnailWidth rejects non-numeric dimensions from the parser', () => {
  // The image bytes are attacker-controlled, so nothing the parser reports is
  // assumed to be a number. NaN previously slipped through: every comparison
  // against it is false, so it satisfied both bounds checks.
  for (const bad of ['1 onerror=alert(1)', {}, null, undefined, NaN, Infinity, -1, 0]) {
    assert.strictEqual(thumbnailWidth({ width: bad, height: 5000 }), null, `width=${String(bad)}`)
    assert.strictEqual(thumbnailWidth({ width: 1080, height: bad }), null, `height=${String(bad)}`)
  }
})

test('codeLineIndices marks fenced and indented blocks', () => {
  const lines = [
    'before',
    '```yaml',
    'inside fence',
    '```',
    'after',
    '',
    '    indented code',
    '    still code',
    '',
    'prose again',
  ]
  const marked = codeLineIndices(lines)
  assert.deepStrictEqual([...marked].sort((a, b) => a - b), [1, 2, 3, 6, 7])
})

test('rewrite honours the opt-out marker', async () => {
  const body = `<!-- no-thumbnail -->\n![shot](${ATTACHMENT})`
  assert.deepStrictEqual(await rewrite(body), { changed: false, body })
})

test('rewrite ignores images inside fenced code blocks', async () => {
  const body = ['```md', `![shot](${ATTACHMENT})`, '```'].join('\n')
  assert.deepStrictEqual(await rewrite(body), { changed: false, body })
})

test('rewrite ignores images inside inline code spans', async () => {
  const body = `use \`![shot](${ATTACHMENT})\` to embed`
  assert.deepStrictEqual(await rewrite(body), { changed: false, body })
})

test('rewrite ignores non-allowlisted hosts', async () => {
  const body = '![shot](https://evil.tld/huge.png)'
  assert.deepStrictEqual(await rewrite(body), { changed: false, body })
})

test('rewrite ignores reference-style images and existing HTML', async () => {
  const refStyle = '![shot][ref]\n\n[ref]: ' + ATTACHMENT
  assert.deepStrictEqual(await rewrite(refStyle), { changed: false, body: refStyle })

  const html = `<a href="${ATTACHMENT}"><img src="${ATTACHMENT}" width="180"></a>`
  assert.deepStrictEqual(await rewrite(html), { changed: false, body: html })
})

test('rewrite stays fast on adversarial bodies', async () => {
  const cases = {
    // Unbounded alt/URL quantifiers made every "!" rescan to end-of-string.
    'unclosed image opens': '!['.repeat(32000),
    'nested brackets': '![' + '['.repeat(60000) + ']( https://x',
    'all backticks': '`'.repeat(65000),
    // The killer for /(`+)(.*?)\1/g: one long run, then a long non-backtick
    // tail. Took ~118 seconds before inlineCodeRanges became a linear scan.
    'backtick run then tail': '`'.repeat(30000) + 'x'.repeat(35000),
    'alternating backticks': '`x'.repeat(32000),
    'many fences': '```\n'.repeat(16000),
    'deep indent': '    x\n'.repeat(10000),
  }
  for (const [name, body] of Object.entries(cases)) {
    const started = process.hrtime.bigint()
    await rewrite(body.slice(0, 65536))
    const elapsedMs = Number(process.hrtime.bigint() - started) / 1e6
    assert.ok(elapsedMs < 1000, `${name} took ${elapsedMs.toFixed(0)}ms`)
  }
})

test('allowedUrl rejects non-default ports', () => {
  // hostname excludes the port, so this needs its own check.
  assert.strictEqual(allowedUrl('https://github.com:8443/user-attachments/assets/x'), null)
  assert.strictEqual(allowedUrl('https://user-images.githubusercontent.com:1337/a.png'), null)
})

test('codeLineIndices handles blockquoted and mid-content fences', () => {
  const lines = [
    '> ```kotlin',
    '> val x = 1',
    '> ```',
    'prose',
    '```',
    'still code, because "```foo" does not close a fence',
    '```foo',
    'also still code',
    '```',
    'free again',
  ]
  const marked = codeLineIndices(lines)
  assert.deepStrictEqual([...marked].sort((a, b) => a - b), [0, 1, 2, 4, 5, 6, 7, 8])
})

test('rewrite ignores images in blockquoted code fences', async () => {
  const body = ['> ```md', `> ![shot](${ATTACHMENT})`, '> ```'].join('\n')
  assert.deepStrictEqual(await rewrite(body), { changed: false, body })
})

test('rewrite leaves empty and oversized bodies alone', async () => {
  assert.deepStrictEqual(await rewrite(''), { changed: false, body: '' })
  assert.deepStrictEqual(await rewrite(undefined), { changed: false, body: '' })

  const huge = 'x'.repeat(65537)
  assert.deepStrictEqual(await rewrite(huge), { changed: false, body: huge })
})
