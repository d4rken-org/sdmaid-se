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

const fs = require('fs')
const zlib = require('zlib')
const { rewrite } = require('./rewrite')

function bodyFromEvent(eventName, payload) {
  if (eventName === 'issue_comment') return payload.comment && payload.comment.body
  if (eventName === 'issues') return payload.issue && payload.issue.body
  return null
}

async function main() {
  const eventName = process.env.GITHUB_EVENT_NAME
  const payload = JSON.parse(fs.readFileSync(process.env.GITHUB_EVENT_PATH, 'utf8'))

  const result = await rewrite(bodyFromEvent(eventName, payload))

  const lines = [`changed=${result.changed}`]
  if (result.changed) {
    const packed = zlib.gzipSync(Buffer.from(result.body, 'utf8')).toString('base64')
    lines.push(`body_gz_b64=${packed}`)
    console.log(`encoded ${packed.length} bytes`)
  }
  fs.appendFileSync(process.env.GITHUB_OUTPUT, `${lines.join('\n')}\n`)

  console.log(`event=${eventName} changed=${result.changed}`)
}

main().catch((error) => {
  console.error(error)
  process.exit(1)
})
