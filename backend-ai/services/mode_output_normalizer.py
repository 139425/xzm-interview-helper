from __future__ import annotations

from collections.abc import Mapping


class StreamingTextReplacer:
    """Replace fixed strings without exposing matches split across chunks."""

    def __init__(self, replacements: Mapping[str, str]):
        self._replacements = dict(replacements)
        self._pending = ""

    def feed(self, value: str) -> str:
        if not value:
            return ""
        if not self._replacements:
            return value

        self._pending += value
        emitted: list[str] = []
        while self._pending:
            matched = next(
                (old for old in self._replacements if self._pending.startswith(old)),
                None,
            )
            if matched is not None:
                emitted.append(self._replacements[matched])
                self._pending = self._pending[len(matched):]
                continue

            # A heading may be split at any SSE chunk boundary. Keep only a
            # prefix that could still become one of the configured matches.
            if any(old.startswith(self._pending) for old in self._replacements):
                break

            emitted.append(self._pending[0])
            self._pending = self._pending[1:]

        return "".join(emitted)

    def flush(self) -> str:
        value = self._replace(self._pending)
        self._pending = ""
        return value

    def _replace(self, value: str) -> str:
        for old, new in self._replacements.items():
            value = value.replace(old, new)
        return value
