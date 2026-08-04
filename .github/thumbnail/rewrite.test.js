'use strict'

// Run with: node --test .github/thumbnail/
// Covers the parsing and validation logic. The network probe is not exercised.

const test = require('node:test')
const assert = require('node:assert')

const { rewrite, allowedUrl, thumbnailWidth, codeLineIndices, escapeAttr } = require('./rewrite')

const ATTACHMENT = 'https://github.com/user-attachments/assets/0e3b1f2a-1111-2222-3333-444455556666'

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
