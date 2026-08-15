const TEXT_BOUNDARY = /[\s，。！？；：、,.!?;:）】》」』]/u
const CODE_BOUNDARY = /[\n\r\s,;{}()[\]]/u

function splitAtCodePoint(text, count) {
  const characters = Array.from(text)
  return {
    head: characters.slice(0, count).join(''),
    tail: characters.slice(count).join(''),
  }
}

function boundaryIndex(characters, from, to, boundaryPattern) {
  const upperBound = Math.min(to, characters.length)
  for (let index = upperBound - 1; index >= from - 1; index -= 1) {
    if (boundaryPattern.test(characters[index])) return index + 1
  }
  return -1
}

/**
 * Pull one presentation-sized phrase from an incoming stream buffer.
 *
 * The model stream can arrive one character at a time. Rendering every frame
 * directly recreates a typewriter effect, so the view groups a few code points
 * and reveals them together. This function never rewrites content: concatenating
 * every returned `phrase` with the final `rest` always reproduces the input.
 */
export function takeRevealPhrase(buffer, options = {}) {
  const text = String(buffer || '')
  if (!text) return null

  const mode = options.mode === 'code' ? 'code' : 'text'
  const force = Boolean(options.force)
  const characters = Array.from(text)

  if (force) return { phrase: text, rest: '' }

  const minimum = mode === 'code' ? 6 : 4
  const target = mode === 'code' ? 16 : 8
  const maximum = mode === 'code' ? 28 : 14
  const boundaryPattern = mode === 'code' ? CODE_BOUNDARY : TEXT_BOUNDARY

  // A completed sentence or source line is already a natural visual unit, even
  // when it is shorter than the normal target size.
  const lastCharacter = characters.at(-1)
  if (
    characters.length >= 2 &&
    characters.length <= maximum &&
    boundaryPattern.test(lastCharacter)
  ) {
    return { phrase: text, rest: '' }
  }

  if (characters.length < target) return null

  const naturalSplit = boundaryIndex(characters, minimum, maximum, boundaryPattern)
  const splitAt = naturalSplit >= minimum ? naturalSplit : target
  const { head, tail } = splitAtCodePoint(text, splitAt)
  return { phrase: head, rest: tail }
}
