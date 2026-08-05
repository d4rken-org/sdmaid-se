'use strict'

// Runner glue for the unprivileged half of the thumbnail-images workflow.
//
// Reads the event payload straight off disk rather than through workflow
// expressions: untrusted text must never be substituted into a script body.
// Emits the rewritten Markdown gzipped and base64-encoded so the privileged job
// can pass it through `env:` without any character needing escaping.
//
// Gzip is not an optimisation. A 65536-character body of CJK text is ~196 KB as
// UTF-8 and ~262 KB once base64-encoded, past the ~128 KiB Linux allows for a
// single environment string, which would stop the privileged step from starting
// at all. Markdown compresses far below that.

const crypto = require('crypto')
const fs = require('fs')
const zlib = require('zlib')
const { rewrite } = require('./rewrite')

// Base64 characters. Node's per-environment-string ceiling is ~128 KiB; stop
// short of it so an incompressible body produces a clean no-op instead of a
// privileged step that cannot start.
const MAX_ENCODED = 100000

function sha256(value) {
  return crypto.createHash('sha256').update(String(value), 'utf8').digest('hex')
}

function bodyFromEvent(eventName, payload) {
  if (eventName === 'issue_comment') return payload.comment && payload.comment.body
  if (eventName === 'issues') return payload.issue && payload.issue.body
  return null
}

async function main() {
  const eventName = process.env.GITHUB_EVENT_NAME
  const payload = JSON.parse(fs.readFileSync(process.env.GITHUB_EVENT_PATH, 'utf8'))

  const original = bodyFromEvent(eventName, payload)
  const result = await rewrite(original)

  const lines = [`changed=${result.changed}`]
  if (result.changed) {
    const packed = zlib.gzipSync(Buffer.from(result.body, 'utf8')).toString('base64')
    if (packed.length > MAX_ENCODED) {
      console.log(`skipping: encoded body is ${packed.length} chars, over the ${MAX_ENCODED} limit`)
      fs.appendFileSync(process.env.GITHUB_OUTPUT, 'changed=false\n')
      return
    }
    lines.push(`body_gz_b64=${packed}`)
    // The body analysed here is a snapshot from the webhook. Between now and
    // the API call the author may have edited it again, and blindly PATCHing
    // would revert that edit. Hand the privileged job a fingerprint of what we
    // analysed so it can refuse to overwrite anything newer.
    lines.push(`original_sha=${sha256(original)}`)
    console.log(`encoded ${packed.length} chars`)
  }
  fs.appendFileSync(process.env.GITHUB_OUTPUT, `${lines.join('\n')}\n`)

  console.log(`event=${eventName} changed=${result.changed}`)
}

main().catch((error) => {
  console.error(error)
  process.exit(1)
})
