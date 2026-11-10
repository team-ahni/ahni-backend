const assert = require('node:assert/strict')
const { readFileSync } = require('node:fs')
const { join } = require('node:path')
const { spawnSync } = require('node:child_process')
const { test } = require('node:test')

const workflow = readFileSync(
  join(__dirname, '../workflows/auto-pr.yml'),
  'utf8',
)
const AsyncFunction = Object.getPrototypeOf(async function () {}).constructor

function step(marker) {
  const lines = workflow.split('\n')
  const index = lines.findIndex((line) => line.trim() === marker)
  assert.notEqual(index, -1, `Missing workflow step: ${marker}`)
  let start = index
  while (start > 0 && !lines[start].startsWith('      - ')) start--
  let end = start + 1
  while (end < lines.length && !lines[end].startsWith('      - ')) end++
  return lines.slice(start, end)
}

function block(lines, field) {
  const index = lines.findIndex((line) => line.trim() === `${field}: |`)
  assert.notEqual(index, -1, `Missing ${field} block`)
  const indent = lines[index].search(/\S/) + 2
  const body = []
  for (const line of lines.slice(index + 1)) {
    if (line.trim() && line.search(/\S/) < indent) break
    body.push(line.slice(indent))
  }
  return body.join('\n')
}

async function execute(lines, github, context, outputs = {}, failures = []) {
  await new AsyncFunction('github', 'context', 'core', block(lines, 'script'))(
    github,
    context,
    {
      setOutput: (name, value) => {
        outputs[name] = value
      },
      setFailed: (message) => {
        failures.push(message)
      },
    },
  )
  return { outputs, failures }
}

async function create(login, existing = null, authError = null) {
  const writes = []
  const reads = []
  const github = {
    rest: {
      users: {
        getAuthenticated: async () => {
          if (authError) throw authError
          return { data: { login } }
        },
      },
      pulls: {
        list: async () => {
          reads.push('pulls')
          return { data: existing ? [existing] : [] }
        },
        create: async (input) => {
          writes.push(input)
          return { data: { number: 12 } }
        },
        update: async (input) => {
          writes.push(input)
          return { data: {} }
        },
      },
      repos: {
        compareCommits: async () => {
          reads.push('commits')
          return {
            data: {
              commits: [
                {
                  sha: 'abcdef1234567890',
                  commit: {
                    message: 'feat(example): add a feature\n\nDetails',
                  },
                  html_url:
                    'https://github.com/example/project/commit/abcdef1234567890',
                },
              ],
            },
          }
        },
      },
    },
  }
  const result = await execute(step('id: create-pr'), github, {
    actor: 'team-member',
    repo: { owner: 'example', repo: 'project' },
    ref: 'refs/heads/feat/example',
    payload: { head_commit: { message: 'feat(example): add a feature' } },
  })
  return { ...result, writes, reads }
}

for (const [actor, expected] of [
  ['nimuseel', 'GH_PAT_NIMUSEEL'],
  ['team-member', 'GH_PAT_TEAM_MEMBER'],
  ['Team-Member', 'GH_PAT_TEAM_MEMBER'],
]) {
  test(`selects a secret for the original push actor: ${actor}`, async () => {
    const result = await execute(
      step('id: actor-token'),
      {},
      {
        actor,
        triggering_actor: 'different-rerun-user',
      },
    )
    assert.equal(result.outputs['secret-name'], expected)
  })
}

test('missing personal token stops the workflow without using the shared token', () => {
  const result = spawnSync(
    'bash',
    ['-c', block(step('id: validate-token'), 'run')],
    {
      encoding: 'utf8',
      env: {
        PATH: process.env.PATH,
        ACTOR_PAT: '',
        SECRET_NAME: 'GH_PAT_TEAM_MEMBER',
      },
    },
  )
  assert.notEqual(result.status, 0)
  assert.match(result.stdout + result.stderr, /GH_PAT_TEAM_MEMBER/)
})

test('a present token passes the presence check without being printed', () => {
  const token = 'fixture-token-not-a-real-credential'
  const result = spawnSync(
    'bash',
    ['-c', block(step('id: validate-token'), 'run')],
    {
      encoding: 'utf8',
      env: {
        PATH: process.env.PATH,
        ACTOR_PAT: token,
        SECRET_NAME: 'GH_PAT_TEAM_MEMBER',
      },
    },
  )
  assert.equal(result.status, 0)
  assert.equal((result.stdout + result.stderr).includes(token), false)
})

test('a token owned by another user cannot create or update a PR', async () => {
  const result = await create('repository-owner')
  assert.equal(result.failures.length, 1)
  assert.deepEqual(result.reads, [])
  assert.deepEqual(result.writes, [])
  assert.equal(result.outputs['pull-number'], undefined)
})

test('the matching token creates a PR and preserves the commit section', async () => {
  const result = await create('TEAM-MEMBER')
  assert.deepEqual(result.failures, [])
  assert.equal(result.writes.length, 1)
  assert.equal(result.writes[0].head, 'feat/example')
  assert.match(result.writes[0].body, /abcdef1/)
  assert.match(result.writes[0].body, /feat\(example\): add a feature/)
  assert.equal(result.outputs['pull-number'], 12)
})

test('updates the existing PR without recreating it or removing human text', async () => {
  const result = await create('team-member', {
    number: 9,
    body: 'Human summary',
  })
  assert.equal(result.writes.length, 1)
  assert.equal(result.writes[0].pull_number, 9)
  assert.match(result.writes[0].body, /^Human summary/)
  assert.match(result.writes[0].body, /auto-generated-commits/)
  assert.equal(result.outputs['pull-number'], 9)
})

test('an invalid token fails before any PR operation', async () => {
  await assert.rejects(
    create(null, null, new Error('Unauthorized')),
    /Unauthorized/,
  )
})

test('token validation and PR creation receive the selected personal token', async () => {
  const secrets = { GH_PAT_TEAM_MEMBER: 'worker-token', GH_PAT: 'owner-token' }
  const { outputs } = await execute(
    step('id: actor-token'),
    {},
    { actor: 'team-member' },
  )
  for (const [marker, field] of [
    ['id: validate-token', 'ACTOR_PAT'],
    ['id: create-pr', 'github-token'],
  ]) {
    const line = step(marker).find((value) =>
      value.trim().startsWith(`${field}:`),
    )
    assert.ok(line)
    const expression = line.split(':').slice(1).join(':').trim()
    const reference = expression.match(
      /^\$\{\{ secrets(?:\[steps\.actor-token\.outputs\.secret-name\]|\.([A-Z_]+)) \}\}$/,
    )
    assert.ok(reference, 'Unsupported token reference')
    const selectedKey = reference[1] ?? outputs['secret-name']
    assert.equal(secrets[selectedKey], 'worker-token')
  }
})

test('does not request a Copilot review', () => {
  assert.doesNotMatch(workflow, /copilot/i)
})
