import { test } from 'node:test'
import assert from 'node:assert/strict'
import { readFileSync, readdirSync } from 'node:fs'
import { resolve, relative, isAbsolute, dirname } from 'node:path'
import { fileURLToPath } from 'node:url'

const root = fileURLToPath(new URL('..', import.meta.url))
const read = (path) => readFileSync(resolve(root, path), 'utf8')
const filesIn = (dir) => readdirSync(dir, { withFileTypes: true }).flatMap((entry) => {
  const path = resolve(dir, entry.name)
  return entry.isDirectory() ? filesIn(path) : [path]
})

test('application imports remain inside the independent project', () => {
  const files = [...filesIn(resolve(root, 'src')), resolve(root, 'vite.config.js')]
  for (const file of files.filter((path) => /\.(vue|js|css)$/.test(path))) {
    const source = readFileSync(file, 'utf8')
    for (const match of source.matchAll(/(?:from\s*|import\s*\(?|@import\s*)['"]([^'"]+)['"]/g)) {
      const specifier = match[1]
      if (specifier.startsWith('.')) {
        const path = relative(root, resolve(dirname(file), specifier))
        assert.ok(!path.startsWith('..') && !isAbsolute(path), `${file} imports outside project: ${specifier}`)
      } else {
        assert.ok(!specifier.startsWith('/') && !/^(?:https?:|file:)/.test(specifier), `Unexpected source import: ${specifier}`)
      }
    }
    assert.doesNotMatch(source, /@\/|\.\.\/vue\//, `Old source alias in ${file}`)
  }
})

test('custom colors and typography live in the token file, without global Ant overrides', () => {
  for (const file of filesIn(resolve(root, 'src')).filter((path) => /\.(vue|css)$/.test(path))) {
    const source = readFileSync(file, 'utf8')
    assert.doesNotMatch(source, /\.ant-[\w-]+/, `Use component tokens instead of global Ant overrides: ${file}`)
    if (!file.endsWith('tokens.css')) {
      assert.doesNotMatch(source, /#[\da-f]{3,8}\b|\brgba?\(|\boklch\(/i, `Direct color in ${file}`)
      assert.doesNotMatch(source, /font-size\s*:\s*[\d.]+(?:px|rem)|border-radius\s*:\s*[\d.]+(?:px|rem)/, `Use typography/radius tokens: ${file}`)
    }
  }
})

test('reading and primary action colors meet 4.5:1 contrast', () => {
  const css = read('src/styles/tokens.css')
  const tokens = new Map([...css.matchAll(/--([\w-]+):\s*(#[\da-f]{6});/gi)].map((match) => [match[1], match[2]]))
  const luminance = (hex) => {
    assert.ok(hex, 'Missing color token')
    const rgb = [1, 3, 5].map((index) => Number.parseInt(hex.slice(index, index + 2), 16) / 255)
    const linear = rgb.map((v) => v <= 0.04045 ? v / 12.92 : ((v + 0.055) / 1.055) ** 2.4)
    return linear[0] * 0.2126 + linear[1] * 0.7152 + linear[2] * 0.0722
  }
  for (const [fg, bg] of [['text', 'surface'], ['muted', 'surface'], ['muted', 'sidebar'], ['primary-text', 'primary'], ['primary', 'accent'], ['code-string', 'code-bg'], ['primary', 'sidebar'], ['muted', 'accent'], ['primary-text', 'primary-hover'], ['admin-ink', 'surface'], ['admin-muted', 'surface'], ['admin-nav-text', 'admin-sidebar'], ['admin-group-text', 'admin-sidebar'], ['admin-muted', 'admin-canvas'], ['admin-danger', 'surface'], ['admin-positive', 'surface']]) {
    const a = luminance(tokens.get(fg))
    const b = luminance(tokens.get(bg))
    assert.ok((Math.max(a, b) + 0.05) / (Math.min(a, b) + 0.05) >= 4.5, `${fg} on ${bg}`)
  }
})
