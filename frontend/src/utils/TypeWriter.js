/**
 * 打字机效果类
 * 参考 docs/index.html 实现
 */
export class TypeWriter {
  constructor(options = {}) {
    this.queue = []              // 待显示的字符队列
    this.displayedText = ''      // 已显示的文本
    this.fullText = ''           // 完整文本（用于保存）
    this.isTyping = false        // 是否正在打字
    this.isDone = false          // 流是否结束
    this.renderCallback = options.onRender || (() => {})
    this.completeCallback = options.onComplete || (() => {})

    // 速度配置 (毫秒/字符)
    this.baseSpeed = options.baseSpeed || 7     // 基础速度
    this.minSpeed = options.minSpeed || 1       // 最快速度（队列积压时）
    this.maxSpeed = options.maxSpeed || 14      // 最慢速度（队列快空时）
    this.chunkSize = options.chunkSize || 1     // 每次显示的字符数

    this.timer = null
    this.maxRateBudget = 0
  }

  // 添加文本到队列
  add(text) {
    if (!text) return
    this.fullText += text
    // 将文本拆分为字符加入队列
    for (const char of text) {
      this.queue.push(char)
    }
    if (!this.isTyping) {
      this.startTyping()
    }
  }

  // 开始打字
  startTyping() {
    if (this.isTyping) return
    this.isTyping = true
    this.tick()
  }

  // 打字循环
  tick() {
    if (this.queue.length === 0) {
      if (this.isDone) {
        this.isTyping = false
        this.renderCallback(this.displayedText, false)
        this.completeCallback(this.fullText)
        return
      }
      // 队列空但流未结束，等待新数据
      this.timer = setTimeout(() => this.tick(), 17)
      return
    }

    // 根据队列长度动态调整速度
    let speed = this.baseSpeed
    let chunkSize = this.chunkSize
    const maxRateSlowdownFactor = 3

    if (this.queue.length > 100) {
      speed = this.minSpeed
      chunkSize = this.chunkSize * 5
    } else if (this.queue.length > 50) {
      speed = this.minSpeed
      chunkSize = this.chunkSize * 5
    } else if (this.queue.length > 20) {
      speed = 1
      chunkSize = this.chunkSize * 5
    } else if (this.queue.length < 5 && !this.isDone) {
      speed = this.maxSpeed
    }

    if (chunkSize > this.chunkSize) {
      this.maxRateBudget += chunkSize / maxRateSlowdownFactor
      chunkSize = Math.floor(this.maxRateBudget)
      this.maxRateBudget -= chunkSize
    }

    // 取出字符
    for (let i = 0; i < chunkSize && this.queue.length > 0; i++) {
      this.displayedText += this.queue.shift()
    }

    // 渲染（带光标）
    this.renderCallback(this.displayedText, true)

    this.timer = setTimeout(() => this.tick(), speed)
  }

  // 标记流结束
  finish(options = {}) {
    this.isDone = true
    if (options && options.accelerate) {
      const speed = typeof options.speed === 'number' ? options.speed : 1
      this.baseSpeed = speed
      this.minSpeed = speed
      this.maxSpeed = speed
    }
    if (!this.isTyping) {
      this.startTyping()
    }
  }

  // 立即完成显示
  flush() {
    if (this.timer) {
      clearTimeout(this.timer)
    }
    this.displayedText = this.fullText
    this.queue = []
    this.isTyping = false
    this.isDone = true
    this.renderCallback(this.displayedText, false)
    this.completeCallback(this.fullText)
  }

  // 获取完整文本
  getFullText() {
    return this.fullText
  }

  // 获取已显示文本
  getDisplayedText() {
    return this.displayedText
  }

  // 重置
  reset() {
    if (this.timer) {
      clearTimeout(this.timer)
    }
    this.queue = []
    this.displayedText = ''
    this.fullText = ''
    this.isTyping = false
    this.isDone = false
    this.maxRateBudget = 0
  }

  // 销毁
  destroy() {
    if (this.timer) {
      clearTimeout(this.timer)
    }
    this.queue = []
    this.renderCallback = () => {}
    this.completeCallback = () => {}
    this.maxRateBudget = 0
  }
}

export default TypeWriter
