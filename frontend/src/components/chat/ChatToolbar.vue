<template>
  <el-popover
    v-model:visible="visible"
    placement="bottom-end"
    :width="288"
    trigger="click"
    popper-class="xzm-tools-popover"
  >
    <template #reference>
      <button
        type="button"
        class="tools-trigger"
        :aria-expanded="visible"
        aria-label="工具箱"
        aria-haspopup="dialog"
      >
        <svg
          viewBox="0 0 24 24"
          width="17"
          height="17"
          fill="none"
          stroke="currentColor"
          stroke-width="1.7"
          aria-hidden="true"
        >
          <rect x="4" y="4" width="6" height="6" rx="1.5" />
          <rect x="14" y="4" width="6" height="6" rx="1.5" />
          <rect x="4" y="14" width="6" height="6" rx="1.5" />
          <rect x="14" y="14" width="6" height="6" rx="1.5" />
        </svg>
        <span v-if="!isMobile">工具</span>
      </button>
    </template>
    <div class="tools-menu" @keydown.esc="visible = false">
      <div class="tools-heading">工具箱 <span>随时继续当前对话</span></div>
      <section
        v-for="group in groups"
        :key="group.label"
        :aria-label="group.label"
      >
        <p>{{ group.label }}</p>
        <button
          v-for="item in group.items"
          :key="item.id"
          type="button"
          @click="handleAction(item.id)"
        >
          <span class="tools-symbol" aria-hidden="true">{{ item.symbol }}</span>
          <span class="tools-copy"
            ><strong>{{ item.label }}</strong
            ><small>{{ item.desc }}</small></span
          >
          <span class="tools-arrow" aria-hidden="true">↗</span>
        </button>
      </section>
    </div>
  </el-popover>
</template>

<script setup>
import { ref } from "vue";
import { ElPopover } from "element-plus";
import { useRouter } from "vue-router";
defineProps({ isMobile: { type: Boolean, default: false } });
const emit = defineEmits([
  "open-md-editor",
  "open-code-editor",
  "open-resume-editor",
  "open-wechat-modal",
  "scroll-to-bottom",
  "toggle-question-nav",
  "open-network-review",
  "open-html-showcase",
]);
const visible = ref(false);
const router = useRouter();
const groups = [
  {
    label: "编辑与创作",
    items: [
      {
        id: "code",
        symbol: "</>",
        label: "代码编辑器",
        desc: "编写、运行与调试",
      },
      {
        id: "md",
        symbol: "M",
        label: "文档编辑器",
        desc: "整理回答与学习笔记",
      },
      {
        id: "resume",
        symbol: "▤",
        label: "简历编辑器",
        desc: "排版、预览与导出",
      },
      {
        id: "html",
        symbol: "◇",
        label: "HTML 在线展示",
        desc: "预览网页代码效果",
      },
    ],
  },
  {
    label: "求职与帮助",
    items: [
      {
        id: "recruitment",
        symbol: "↗",
        label: "求职信息",
        desc: "查看招聘岗位与投递入口",
      },
      { id: "wechat", symbol: "?", label: "微信联系", desc: "获取帮助与反馈" },
    ],
  },
];
function handleAction(id) {
  visible.value = false;
  if (id === "recruitment") return router.push("/recruitment");
  const events = {
    code: "open-code-editor",
    md: "open-md-editor",
    resume: "open-resume-editor",
    html: "open-html-showcase",
    wechat: "open-wechat-modal",
  };
  if (events[id]) emit(events[id]);
}
</script>

<style scoped>
.tools-trigger {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  gap: 7px;
  height: 36px;
  padding: 0 10px;
  border: 1px solid transparent;
  border-radius: 9px;
  background: transparent;
  color: var(--xzm-text-secondary);
  cursor: pointer;
  font-size: 13px;
  transition:
    background 150ms,
    color 150ms,
    transform 120ms;
}
.tools-trigger:hover,
.tools-trigger[aria-expanded="true"] {
  background: var(--xzm-surface-2);
  color: var(--xzm-brand);
}
.tools-trigger:active {
  transform: scale(0.96);
}
.tools-heading {
  padding: 5px 6px 12px;
  color: var(--xzm-text-primary);
  font-weight: 600;
  font-size: 13px;
}
.tools-heading span {
  display: block;
  margin-top: 3px;
  color: var(--xzm-text-tertiary);
  font-size: 11px;
  font-weight: 400;
}
.tools-menu section + section {
  margin-top: 8px;
  padding-top: 8px;
  border-top: 1px solid var(--xzm-border-color);
}
.tools-menu p {
  margin: 0 6px 4px;
  color: var(--xzm-text-tertiary);
  font-size: 11px;
}
.tools-menu section button {
  display: flex;
  width: 100%;
  align-items: center;
  gap: 11px;
  padding: 9px 7px;
  border: 0;
  border-radius: 8px;
  background: transparent;
  text-align: left;
  color: var(--xzm-text-primary);
  cursor: pointer;
  transition: background 150ms;
}
.tools-menu section button:hover {
  background: var(--xzm-hover-bg);
}
.tools-symbol {
  display: grid;
  place-items: center;
  flex: 0 0 30px;
  height: 30px;
  border: 1px solid var(--xzm-border-color);
  border-radius: 8px;
  color: var(--xzm-brand);
  background: var(--xzm-surface-0);
  font: 600 12px var(--xzm-font-mono);
}
.tools-copy {
  display: grid;
  gap: 2px;
  flex: 1;
}
.tools-copy strong {
  font-size: 13px;
  font-weight: 500;
}
.tools-copy small {
  font-size: 11px;
  color: var(--xzm-text-tertiary);
}
.tools-arrow {
  color: var(--xzm-text-muted);
}
</style>
