'use strict'

// Rewrites oversized GitHub-hosted screenshots in issue/comment Markdown into
// clickable thumbnails.
//
// This module runs in the unprivileged half of the thumbnail-images workflow:
// it touches attacker-controlled text and fetches attacker-supplied URLs, but
// the job it runs in holds no write scopes. It performs no GitHub API calls and
// returns plain data.

const probe = require('probe-image-size')

// Skip a body entirely when it carries this marker.
const OPT_OUT = '<!-- no-thumbnail -->'

// GitHub caps issue/comment bodies at 65536 characters.
const MAX_BODY = 65536
// Bounds the probe fan-out so one comment cannot stall the job or turn the
// workflow into a request amplifier.
const MAX_IMAGES = 20
const PROBE_TIMEOUT_MS = 5000

// Only portrait images taller than this are touched. Landscape shots are
// already constrained by the width of the comment column.
const MIN_HEIGHT = 640
// Rendered height we aim for; the emitted width is derived from it.
const TARGET_HEIGHT = 400
const MIN_WIDTH = 80

// Exact host allowlist. Every entry is GitHub-controlled, so the redirect chain
// a probe follows is GitHub-controlled too. Repo content (raw.githubusercontent)
// is deliberately absent: those are not user uploads.
const ALLOWED_HOSTS = [
  { host: 'github.com', prefix: '/user-attachments/assets/' },
  { host: 'user-images.githubusercontent.com', prefix: '/' },
  { host: 'private-user-images.githubusercontent.com', prefix: '/' },
]

// Deliberately strict: no parentheses, quotes or whitespace in the URL, so a
// Markdown link containing parens cannot be mis-sliced. No nesting.
// Both runs are explicitly bounded: with an open-ended `[^\]]*` a body of
// "![![![..." makes every "!" rescan to end-of-string, which is quadratic.
const MAX_ALT = 300
const MAX_URL = 2048
const IMAGE_RE = new RegExp(
  `!\\[([^\\]]{0,${MAX_ALT}})\\]\\((https://[^\\s()<>"'\`]{1,${MAX_URL}})\\)`,
  'g',
)

/**
 * Validates a URL against the host allowlist.
 * @returns {string|null} the normalised URL, or null if it is not eligible.
 */
function allowedUrl(raw) {
  let url
  try {
    url = new URL(raw)
  } catch {
    return null
  }
  if (url.protocol !== 'https:') return null
  // Credentials in the URL would make the runner send basic auth upstream.
  if (url.username || url.password) return null
  // hostname excludes the port, so this has to be checked separately.
  if (url.port !== '') return null

  const rule = ALLOWED_HOSTS.find((entry) => entry.host === url.hostname)
  if (!rule) return null
  // URL parsing already resolved any "..", so this cannot be walked out of.
  if (!url.pathname.startsWith(rule.prefix)) return null

  return url.toString()
}

function escapeAttr(value) {
  return String(value)
    .replace(/&/g, '&amp;')
    .replace(/</g, '&lt;')
    .replace(/>/g, '&gt;')
    .replace(/"/g, '&quot;')
}

/**
 * Indices of lines that sit inside fenced or indented code blocks.
 */
function codeLineIndices(lines) {
  const inCode = new Set()
  let fence = null
  let indented = false
  let prevBlank = true

  for (let i = 0; i < lines.length; i++) {
    // Strip container prefixes so a fence is still recognised inside a
    // blockquote ("> ```") or a list item ("- ```"). Missing the opener is the
    // damaging direction: the fenced sample then gets rewritten as if it were
    // prose, and its closing fence reads as a stray opener that hides real
    // images further down.
    const line = lines[i]
      .replace(/^(?:\s*>)+ ?/, '')
      .replace(/^ {0,3}(?:[-*+]|\d{1,9}[.)]) +/, '')
    const fenceMatch = /^ {0,3}(`{3,}|~{3,})(.*)$/.exec(line)

    if (fence) {
      inCode.add(i)
      // GFM only closes a fence when nothing but whitespace follows the run;
      // treating "```foo" as a close would end the block early and expose the
      // rest of it to rewriting.
      const closes =
        fenceMatch &&
        fenceMatch[1][0] === fence[0] &&
        fenceMatch[1].length >= fence.length &&
        fenceMatch[2].trim() === ''
      if (closes) fence = null
      continue
    }
    if (fenceMatch) {
      fence = fenceMatch[1]
      inCode.add(i)
      prevBlank = false
      indented = false
      continue
    }
    if (line.trim() === '') {
      // A blank line does not close an indented block.
      prevBlank = true
      continue
    }
    if (/^(?: {4}|\t)/.test(line) && (prevBlank || indented)) {
      inCode.add(i)
      indented = true
      prevBlank = false
      continue
    }
    indented = false
    prevBlank = false
  }
  return inCode
}

/**
 * Character ranges covered by inline code spans on a single line.
 *
 * Deliberately a hand-rolled scan rather than /(`+)(.*?)\1/g: on a line shaped
 * like 30k backticks followed by 35k other characters, that pattern backtracks
 * for around two minutes. This is a single linear pass. It approximates
 * CommonMark rather than implementing it, which is enough to answer the only
 * question asked of it: is this image inside inline code?
 */
function inlineCodeRanges(line) {
  const ranges = []
  const openers = new Map() // backtick run length -> index of the pending opener

  let i = 0
  while (i < line.length) {
    if (line[i] !== '`') {
      i++
      continue
    }
    let end = i
    while (end < line.length && line[end] === '`') end++
    const length = end - i

    if (openers.has(length)) {
      ranges.push([openers.get(length), end])
      openers.delete(length)
    } else {
      openers.set(length, i)
    }
    i = end
  }
  return ranges
}

async function probeSize(url) {
  let timer
  const timeout = new Promise((resolve) => {
    timer = setTimeout(() => resolve(null), PROBE_TIMEOUT_MS)
    if (typeof timer.unref === 'function') timer.unref()
  })
  try {
    const result = await Promise.race([
      probe(url, {
        open_timeout: PROBE_TIMEOUT_MS,
        response_timeout: PROBE_TIMEOUT_MS,
        read_timeout: PROBE_TIMEOUT_MS,
        follow_max: 3,
      }),
      timeout,
    ])
    if (!result) return null
    // Trust boundary with the image parser. The bytes behind an attachment URL
    // are fully attacker-controlled, so only two numbers are allowed out of
    // here; everything else the parser reports is discarded. Math.round() would
    // coerce a rogue string to NaN rather than let markup through, but NaN
    // passes every comparison below, so reject it explicitly.
    if (!Number.isFinite(result.width) || !Number.isFinite(result.height)) return null
    if (result.width <= 0 || result.height <= 0) return null
    return { width: result.width, height: result.height }
  } catch {
    // Broken, private, redirected or unsupported images are left untouched.
    return null
  } finally {
    clearTimeout(timer)
  }
}

/**
 * @returns {number|null} the width to render at, or null to leave the image alone.
 */
function thumbnailWidth(size) {
  if (!Number.isFinite(size.width) || !Number.isFinite(size.height)) return null
  if (size.width <= 0 || size.height <= 0) return null
  if (size.height <= size.width) return null
  if (size.height < MIN_HEIGHT) return null

  const width = Math.round(TARGET_HEIGHT * (size.width / size.height))
  // NaN would satisfy neither comparison, so assert the result directly.
  if (!Number.isFinite(width)) return null
  if (width < MIN_WIDTH || width >= size.width) return null
  return width
}

// Only "width" is emitted. Setting "height" as well fights GitHub's
// max-width:100% and stretches the image on narrow displays.
function thumbnailHtml(url, alt, width) {
  const safeUrl = escapeAttr(url)
  return `<a href="${safeUrl}"><img src="${safeUrl}" alt="${escapeAttr(alt)}" width="${width}"></a>`
}

/**
 * @param {string} body Markdown body of an issue or comment.
 * @param {{probe?: (url: string) => Promise<{width: number, height: number}|null>}} [options]
 *   Seam for tests, so the replacement path can be covered without network access.
 * @returns {Promise<{changed: boolean, body: string}>}
 */
async function rewrite(body, { probe: probeFn = probeSize } = {}) {
  if (typeof body !== 'string' || body.length === 0) return { changed: false, body: '' }
  if (body.length > MAX_BODY) return { changed: false, body }
  if (body.includes(OPT_OUT)) return { changed: false, body }

  const lines = body.split('\n')
  const skipLines = codeLineIndices(lines)

  const hits = []
  for (let i = 0; i < lines.length; i++) {
    if (skipLines.has(i)) continue
    const codeRanges = inlineCodeRanges(lines[i])

    IMAGE_RE.lastIndex = 0
    let match
    while ((match = IMAGE_RE.exec(lines[i])) !== null) {
      const start = match.index
      if (codeRanges.some(([from, to]) => start >= from && start < to)) continue

      const prev = start > 0 ? lines[i][start - 1] : ''
      // "\![alt](url)" is an escaped literal, not an image.
      if (prev === '\\') continue
      // "[![alt](url)](target)" is a linked image. Rewriting the inner image
      // would strip the author's link target, and it is already clickable,
      // which is the whole point of the rewrite.
      if (prev === '[') continue

      const url = allowedUrl(match[2])
      if (!url) continue

      hits.push({ line: i, start, length: match[0].length, alt: match[1], url })
    }
  }
  if (hits.length === 0) return { changed: false, body }

  // The cap exists to bound outbound requests, so it counts distinct URLs
  // rather than occurrences. Counting occurrences would let twenty repeats of
  // one thumbnail-sized image crowd out a genuine screenshot further down.
  const probeTargets = [...new Set(hits.map((hit) => hit.url))].slice(0, MAX_IMAGES)
  const sizes = new Map()
  for (const url of probeTargets) {
    sizes.set(url, await probeFn(url))
  }

  // Apply right-to-left so earlier offsets stay valid.
  let changed = false
  for (const hit of hits.slice().reverse()) {
    const size = sizes.get(hit.url)
    if (!size) continue
    const width = thumbnailWidth(size)
    if (!width) continue

    const line = lines[hit.line]
    lines[hit.line] =
      line.slice(0, hit.start) +
      thumbnailHtml(hit.url, hit.alt, width) +
      line.slice(hit.start + hit.length)
    changed = true
  }
  if (!changed) return { changed: false, body }

  const updated = lines.join('\n')
  // Refuse to produce a body GitHub would reject.
  if (updated.length > MAX_BODY) return { changed: false, body }

  return { changed: true, body: updated }
}

module.exports = { rewrite, allowedUrl, thumbnailWidth, codeLineIndices, escapeAttr }
