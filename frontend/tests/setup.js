// vitest 测试环境初始化
// 模拟 requestAnimationFrame，使 useStreamingMarkdown 在 jsdom 下可用
if (typeof globalThis.requestAnimationFrame !== 'function') {
  globalThis.requestAnimationFrame = (cb) => setTimeout(() => cb(performance.now()), 0)
  globalThis.cancelAnimationFrame = (id) => clearTimeout(id)
}
