#!/bin/zsh
set -e
cd "${0:A:h}/market-atlas"
ATLAS_NODE_BIN="/Users/Admin/.cache/codex-runtimes/codex-primary-runtime/dependencies/node/bin/node"
if [[ ! -x "$ATLAS_NODE_BIN" ]]; then
  ATLAS_NODE_BIN="$(command -v node || true)"
fi
if [[ -z "$ATLAS_NODE_BIN" ]]; then
  print "需要 Node.js 22.13+，请参照 market-atlas/README.md。"
  read "?按回车关闭。"
  exit 1
fi
export PATH="${ATLAS_NODE_BIN:h}:$PATH"
print "观市地址：http://127.0.0.1:5173/"
print "保留这个窗口即可运行，按 Control+C 停止。"
exec "$ATLAS_NODE_BIN" scripts/run-framework.mjs dev
