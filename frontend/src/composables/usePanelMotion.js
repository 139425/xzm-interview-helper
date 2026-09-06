import { nextTick, onBeforeUnmount, watch } from 'vue'

// Reflow text once at its final width, then animate only the panel position.
export function usePanelMotion(elementRef, source) {
  let animation
  let generation = 0
  const stop = watch(source, async () => {
    const element = elementRef.value
    const run = ++generation
    if (!element) return
    const before = element.getBoundingClientRect()
    animation?.cancel()
    await nextTick()
    if (run !== generation || !element.isConnected || !element.animate || window.matchMedia('(prefers-reduced-motion: reduce)').matches) return
    const delta = before.left - element.getBoundingClientRect().left
    if (Math.abs(delta) < 1) return
    animation = element.animate([{ transform: `translateX(${delta}px)` }, { transform: 'translateX(0)' }], {
      duration: 180, easing: 'cubic-bezier(0.2, 0, 0, 1)',
    })
  })
  onBeforeUnmount(() => { generation++; stop(); animation?.cancel() })
}
