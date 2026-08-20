<template>
  <div
    ref="shell"
    class="editor-shell"
    :class="{ 'theme-dark': theme === 'xzm-dark', fullscreen: isFullscreen }"
  >
    <div ref="container" class="monaco-host" aria-label="Java 代码编辑器"></div>

    <footer class="editor-statusbar" aria-label="编辑器状态栏">
      <span class="cursor-position">
        Ln {{ cursor.line }}, Col {{ cursor.column }}
      </span>
      <span class="status-spacer"></span>
      <button
        type="button"
        title="切换编辑器字号"
        :aria-label="`当前字号 ${fontSize} 像素，点击切换`"
        @click="cycleFontSize"
      >
        {{ fontSize }} px
      </button>
      <button
        type="button"
        title="切换 Tab 缩进宽度"
        :aria-label="`当前 Tab 宽度 ${tabSize}，点击切换`"
        @click="cycleTabSize"
      >
        Tab: {{ tabSize }}
      </button>
      <button
        type="button"
        :class="{ active: wordWrap === 'on' }"
        :title="wordWrap === 'on' ? '关闭自动折行' : '开启自动折行'"
        :aria-pressed="wordWrap === 'on'"
        @click="toggleWordWrap"
      >
        {{ wordWrap === "on" ? "折行" : "不折行" }}
      </button>
      <button
        type="button"
        :title="isFullscreen ? '退出全屏' : '全屏编辑'"
        :aria-label="isFullscreen ? '退出全屏编辑器' : '全屏打开编辑器'"
        @click="toggleFullscreen"
      >
        <span aria-hidden="true">{{ isFullscreen ? "↙" : "↗" }}</span>
      </button>
    </footer>
  </div>
</template>

<script>
const JAVA_RESERVED_WORDS = new Set([
  "abstract",
  "assert",
  "boolean",
  "break",
  "byte",
  "case",
  "catch",
  "char",
  "class",
  "const",
  "continue",
  "default",
  "do",
  "double",
  "else",
  "enum",
  "extends",
  "final",
  "finally",
  "float",
  "for",
  "goto",
  "if",
  "implements",
  "import",
  "instanceof",
  "int",
  "interface",
  "long",
  "native",
  "new",
  "package",
  "private",
  "protected",
  "public",
  "return",
  "short",
  "static",
  "strictfp",
  "super",
  "switch",
  "synchronized",
  "this",
  "throw",
  "throws",
  "transient",
  "try",
  "void",
  "volatile",
  "while",
  "true",
  "false",
  "null",
]);

const JAVA_COMPLETION_ENTRIES = [
  ...[
    "public",
    "private",
    "protected",
    "static",
    "final",
    "class",
    "interface",
    "extends",
    "implements",
    "void",
    "int",
    "long",
    "double",
    "boolean",
    "char",
    "new",
    "return",
    "if",
    "else",
    "switch",
    "case",
    "break",
    "continue",
    "try",
    "catch",
    "throw",
  ].map((label) => ({
    label,
    kind: "Keyword",
    detail: "Java 关键字",
    insertText: label,
    sortText: `30-${label}`,
  })),
  ...[
    "String",
    "StringBuilder",
    "Math",
    "Arrays",
    "Collections",
    "List",
    "ArrayList",
    "LinkedList",
    "Map",
    "HashMap",
    "TreeMap",
    "Set",
    "HashSet",
    "TreeSet",
    "Deque",
    "ArrayDeque",
    "Queue",
    "PriorityQueue",
  ].map((label) => ({
    label,
    kind: "Class",
    detail: "Java 常用类型",
    insertText: label,
    sortText: `20-${label}`,
  })),
  ...[
    ["size", "size()", "返回集合大小"],
    ["length", "length", "数组长度"],
    ["charAt", "charAt(${1:index})", "读取指定位置字符"],
    ["substring", "substring(${1:begin}, ${2:end})", "截取字符串"],
    ["equals", "equals(${1:other})", "判断内容相等"],
    ["add", "add(${1:value})", "添加元素"],
    ["get", "get(${1:index})", "读取元素"],
    ["set", "set(${1:index}, ${2:value})", "更新元素"],
    ["put", "put(${1:key}, ${2:value})", "写入键值"],
    [
      "getOrDefault",
      "getOrDefault(${1:key}, ${2:defaultValue})",
      "读取键或默认值",
    ],
    ["contains", "contains(${1:value})", "判断是否包含元素"],
    ["containsKey", "containsKey(${1:key})", "判断是否包含键"],
    [
      "computeIfAbsent",
      "computeIfAbsent(${1:key}, k -> ${2:new ArrayList<>()})",
      "按需创建映射值",
    ],
    ["offer", "offer(${1:value})", "队尾入队"],
    ["poll", "poll()", "队首出队"],
    ["peek", "peek()", "读取队首"],
    ["sort", "sort(${1:array})", "排序"],
    ["binarySearch", "binarySearch(${1:array}, ${2:target})", "二分查找"],
  ].map(([label, insertText, detail]) => ({
    label,
    kind: "Method",
    detail,
    insertText,
    snippet: insertText.includes("${"),
    sortText: `10-${label}`,
  })),
  {
    label: "fori",
    kind: "Snippet",
    detail: "正序索引循环",
    insertText: "for (int ${1:i} = 0; ${1:i} < ${2:n}; ${1:i}++) {\n\t${0}\n}",
    snippet: true,
    sortText: "00-fori",
  },
  {
    label: "forr",
    kind: "Snippet",
    detail: "倒序索引循环",
    insertText:
      "for (int ${1:i} = ${2:n} - 1; ${1:i} >= 0; ${1:i}--) {\n\t${0}\n}",
    snippet: true,
    sortText: "00-forr",
  },
  {
    label: "foreach",
    kind: "Snippet",
    detail: "增强 for 循环",
    insertText: "for (${1:int} ${2:value} : ${3:values}) {\n\t${0}\n}",
    snippet: true,
    sortText: "00-foreach",
  },
  {
    label: "while",
    kind: "Snippet",
    detail: "while 循环",
    insertText: "while (${1:condition}) {\n\t${0}\n}",
    snippet: true,
    sortText: "00-while",
  },
  {
    label: "if",
    kind: "Snippet",
    detail: "条件分支",
    insertText: "if (${1:condition}) {\n\t${0}\n}",
    snippet: true,
    sortText: "00-if",
  },
  {
    label: "list",
    kind: "Snippet",
    detail: "创建 ArrayList",
    insertText: "List<${1:Integer}> ${2:list} = new ArrayList<>();",
    snippet: true,
    sortText: "00-list",
  },
  {
    label: "map",
    kind: "Snippet",
    detail: "创建 HashMap",
    insertText: "Map<${1:Integer}, ${2:Integer}> ${3:map} = new HashMap<>();",
    snippet: true,
    sortText: "00-map",
  },
  {
    label: "set",
    kind: "Snippet",
    detail: "创建 HashSet",
    insertText: "Set<${1:Integer}> ${2:set} = new HashSet<>();",
    snippet: true,
    sortText: "00-set",
  },
  {
    label: "deque",
    kind: "Snippet",
    detail: "创建双端队列",
    insertText: "Deque<${1:Integer}> ${2:queue} = new ArrayDeque<>();",
    snippet: true,
    sortText: "00-deque",
  },
  {
    label: "pq",
    kind: "Snippet",
    detail: "创建优先队列",
    insertText:
      "PriorityQueue<${1:Integer}> ${2:heap} = new PriorityQueue<>();",
    snippet: true,
    sortText: "00-pq",
  },
  {
    label: "sb",
    kind: "Snippet",
    detail: "创建 StringBuilder",
    insertText: "StringBuilder ${1:builder} = new StringBuilder();",
    snippet: true,
    sortText: "00-sb",
  },
  {
    label: "binary-search",
    kind: "Snippet",
    detail: "二分查找骨架",
    insertText:
      "int ${1:left} = 0, ${2:right} = ${3:nums}.length - 1;\nwhile (${1:left} <= ${2:right}) {\n\tint ${4:mid} = ${1:left} + (${2:right} - ${1:left}) / 2;\n\tif (${3:nums}[${4:mid}] == ${5:target}) {\n\t\t${0}\n\t} else if (${3:nums}[${4:mid}] < ${5:target}) {\n\t\t${1:left} = ${4:mid} + 1;\n\t} else {\n\t\t${2:right} = ${4:mid} - 1;\n\t}\n}",
    snippet: true,
    sortText: "00-binary-search",
  },
  {
    label: "bfs",
    kind: "Snippet",
    detail: "BFS 层序遍历骨架",
    insertText:
      "Deque<${1:Integer}> ${2:queue} = new ArrayDeque<>();\n${2:queue}.offer(${3:start});\nwhile (!${2:queue}.isEmpty()) {\n\tint ${4:size} = ${2:queue}.size();\n\tfor (int ${5:i} = 0; ${5:i} < ${4:size}; ${5:i}++) {\n\t\t${1:Integer} ${6:node} = ${2:queue}.poll();\n\t\t${0}\n\t}\n}",
    snippet: true,
    sortText: "00-bfs",
  },
  {
    label: "dfs",
    kind: "Snippet",
    detail: "DFS 方法骨架",
    insertText:
      "private void dfs(${1:int node}) {\n\tif (${2:终止条件}) return;\n\t${0}\n}",
    snippet: true,
    sortText: "00-dfs",
  },
  {
    label: "ListNode",
    kind: "Snippet",
    detail: "链表节点定义",
    insertText:
      "static class ListNode {\n\tint val;\n\tListNode next;\n\tListNode(int val) { this.val = val; }\n\tListNode(int val, ListNode next) { this.val = val; this.next = next; }\n}",
    snippet: true,
    sortText: "00-ListNode",
  },
  {
    label: "TreeNode",
    kind: "Snippet",
    detail: "二叉树节点定义",
    insertText:
      "static class TreeNode {\n\tint val;\n\tTreeNode left;\n\tTreeNode right;\n\tTreeNode(int val) { this.val = val; }\n}",
    snippet: true,
    sortText: "00-TreeNode",
  },
];

/**
 * 从题目代码模板中提取可用于补全的类型、方法和参数名。
 * 返回值不依赖 Monaco，便于单元测试和未来替换编辑器实现。
 */
export function extractJavaContextIdentifiers(codeTemplate = "") {
  const source = String(codeTemplate)
    .replace(/\/\*[\s\S]*?\*\//g, " ")
    .replace(/\/\/.*$/gm, " ")
    .replace(/"(?:\\.|[^"\\])*"|'(?:\\.|[^'\\])*'/g, " ");
  const identifiers = source.match(/[A-Za-z_$][\w$]*/g) || [];

  return [
    ...new Set(
      identifiers.filter(
        (identifier) =>
          identifier.length > 1 &&
          !JAVA_RESERVED_WORDS.has(identifier) &&
          !/^(java|util|lang)$/.test(identifier),
      ),
    ),
  ].slice(0, 80);
}

/**
 * 生成与 Monaco API 解耦的 Java 补全项。
 */
export function buildJavaCompletionItems(codeTemplate = "") {
  const entries = JAVA_COMPLETION_ENTRIES.map((entry) => ({ ...entry }));
  const knownLabels = new Set(entries.map((entry) => entry.label));

  for (const identifier of extractJavaContextIdentifiers(codeTemplate)) {
    if (knownLabels.has(identifier)) continue;
    entries.push({
      label: identifier,
      kind: /^[A-Z]/.test(identifier) ? "Class" : "Variable",
      detail: "来自当前题目的代码模板",
      insertText: identifier,
      sortText: `05-${identifier}`,
    });
    knownLabels.add(identifier);
  }

  return entries;
}
</script>

<script setup>
import { onBeforeUnmount, onMounted, reactive, ref, watch } from "vue";
import * as monaco from "monaco-editor/esm/vs/editor/editor.api";
import "monaco-editor/esm/vs/editor/contrib/suggest/browser/suggestController.js";
import "monaco-editor/esm/vs/basic-languages/java/java.contribution";
import EditorWorker from "monaco-editor/esm/vs/editor/editor.worker?worker";

self.MonacoEnvironment = {
  ...(self.MonacoEnvironment || {}),
  getWorker: () => new EditorWorker(),
};

monaco.editor.defineTheme("xzm-light", {
  base: "vs",
  inherit: true,
  rules: [
    { token: "keyword", foreground: "245B8A", fontStyle: "bold" },
    { token: "type.identifier", foreground: "0D8068" },
    { token: "identifier", foreground: "263442" },
    { token: "string", foreground: "9A5D00" },
    { token: "number", foreground: "A33A58" },
    { token: "comment", foreground: "7B8794", fontStyle: "italic" },
  ],
  colors: {
    "editor.background": "#EDF3EF",
    "editor.foreground": "#263832",
    "editor.lineHighlightBackground": "#E3EBE6",
    "editorLineNumber.foreground": "#87968F",
    "editorLineNumber.activeForeground": "#0D8068",
    "editor.selectionBackground": "#C7E4DB",
    "editorCursor.foreground": "#0D8068",
    "editorSuggestWidget.background": "#F4F7F3",
    "editorSuggestWidget.border": "#CDD8D1",
    "editorSuggestWidget.selectedBackground": "#DDECE6",
  },
});

monaco.editor.defineTheme("xzm-dark", {
  base: "vs-dark",
  inherit: true,
  rules: [
    { token: "keyword", foreground: "7DB8E8", fontStyle: "bold" },
    { token: "type.identifier", foreground: "65DFBD" },
    { token: "identifier", foreground: "D8E1EB" },
    { token: "string", foreground: "E8B86D" },
    { token: "number", foreground: "E58BA5" },
    { token: "comment", foreground: "7F8C9C", fontStyle: "italic" },
  ],
  colors: {
    "editor.background": "#101821",
    "editor.foreground": "#D8E1EB",
    "editor.lineHighlightBackground": "#172431",
    "editorLineNumber.foreground": "#647282",
    "editorLineNumber.activeForeground": "#65DFBD",
    "editor.selectionBackground": "#245A50",
    "editorCursor.foreground": "#65DFBD",
    "editorSuggestWidget.background": "#14212C",
    "editorSuggestWidget.border": "#2B3B48",
    "editorSuggestWidget.selectedBackground": "#1E4A43",
  },
});

const props = defineProps({
  modelValue: { type: String, default: "" },
  language: { type: String, default: "java" },
  theme: { type: String, default: "xzm-dark" },
  readOnly: { type: Boolean, default: false },
  codeTemplate: { type: String, default: "" },
  errors: { type: [Array, String], default: () => [] },
});

const emit = defineEmits(["update:modelValue", "run", "submit"]);
const shell = ref(null);
const container = ref(null);
const cursor = reactive({ line: 1, column: 1 });
const fontSize = ref(14);
const tabSize = ref(4);
const wordWrap = ref("off");
const isFullscreen = ref(false);

let editor = null;
let model = null;
let resizeObserver = null;
let completionProvider = null;
let changeSubscription = null;
let cursorSubscription = null;
const actionSubscriptions = [];
const markerOwner = `xzm-java-editor-${Math.random().toString(36).slice(2)}`;

function toCompletionSuggestion(entry, range) {
  return {
    ...entry,
    kind:
      monaco.languages.CompletionItemKind[entry.kind] ??
      monaco.languages.CompletionItemKind.Text,
    insertTextRules: entry.snippet
      ? monaco.languages.CompletionItemInsertTextRule.InsertAsSnippet
      : undefined,
    range,
  };
}

function provideCompletionItems(activeModel, position) {
  if (activeModel !== model) return { suggestions: [] };
  const word = activeModel.getWordUntilPosition(position);
  const range = {
    startLineNumber: position.lineNumber,
    endLineNumber: position.lineNumber,
    startColumn: word.startColumn,
    endColumn: word.endColumn,
  };
  return {
    suggestions: buildJavaCompletionItems(props.codeTemplate).map((entry) =>
      toCompletionSuggestion(entry, range),
    ),
  };
}

function parseStringDiagnostics(value) {
  const source = String(value).trim();
  if (!source) return [];
  const diagnostics = source
    .split(/\r?\n/)
    .map((line) => {
      const match = line.match(
        /(?:[\w$.-]+\.java):(\d+)(?::(\d+))?\s*:?\s*(?:error:)?\s*(.*)/i,
      );
      if (!match) return null;
      return {
        line: Number(match[1]),
        column: Number(match[2] || 1),
        message: match[3] || line,
        severity: "error",
      };
    })
    .filter(Boolean);
  return diagnostics.length
    ? diagnostics
    : [{ line: 1, column: 1, message: source, severity: "error" }];
}

function updateErrorMarkers() {
  if (!model) return;
  const diagnostics = Array.isArray(props.errors)
    ? props.errors
    : parseStringDiagnostics(props.errors);
  const severityMap = {
    error: monaco.MarkerSeverity.Error,
    warning: monaco.MarkerSeverity.Warning,
    info: monaco.MarkerSeverity.Info,
    hint: monaco.MarkerSeverity.Hint,
  };

  const markers = diagnostics.filter(Boolean).map((diagnostic) => {
    if (typeof diagnostic === "string") {
      return {
        startLineNumber: 1,
        startColumn: 1,
        endLineNumber: 1,
        endColumn: 2,
        message: diagnostic,
        severity: monaco.MarkerSeverity.Error,
      };
    }
    const startLineNumber = Math.max(
      1,
      Number(
        diagnostic.startLineNumber ??
          diagnostic.lineNumber ??
          diagnostic.line ??
          1,
      ),
    );
    const startColumn = Math.max(
      1,
      Number(diagnostic.startColumn ?? diagnostic.column ?? 1),
    );
    return {
      startLineNumber,
      startColumn,
      endLineNumber: Math.max(
        startLineNumber,
        Number(diagnostic.endLineNumber ?? startLineNumber),
      ),
      endColumn: Math.max(
        startColumn + 1,
        Number(diagnostic.endColumn ?? startColumn + 1),
      ),
      message: String(diagnostic.message ?? diagnostic.error ?? "编译错误"),
      source: diagnostic.source || "Java compiler",
      severity:
        severityMap[String(diagnostic.severity || "error").toLowerCase()] ??
        monaco.MarkerSeverity.Error,
    };
  });
  monaco.editor.setModelMarkers(model, markerOwner, markers);
}

function cycleFontSize() {
  const sizes = [13, 14, 15, 16, 18];
  fontSize.value = sizes[(sizes.indexOf(fontSize.value) + 1) % sizes.length];
  editor?.updateOptions({
    fontSize: fontSize.value,
    lineHeight: Math.round(fontSize.value * 1.64),
  });
}

function cycleTabSize() {
  tabSize.value = tabSize.value === 4 ? 2 : 4;
  model?.updateOptions({ tabSize: tabSize.value, insertSpaces: true });
}

function toggleWordWrap() {
  wordWrap.value = wordWrap.value === "off" ? "on" : "off";
  editor?.updateOptions({ wordWrap: wordWrap.value });
}

async function toggleFullscreen() {
  if (!shell.value) return;
  try {
    if (document.fullscreenElement === shell.value) {
      await document.exitFullscreen?.();
    } else {
      await shell.value.requestFullscreen?.();
    }
  } catch {
    // 浏览器或系统策略可能禁用全屏；保留当前编辑状态即可。
  }
}

function handleFullscreenChange() {
  isFullscreen.value = document.fullscreenElement === shell.value;
  requestAnimationFrame(() => editor?.layout());
}

function registerEditorAction(id, label, keybindings, eventName) {
  actionSubscriptions.push(
    editor.addAction({
      id: `${id}-${markerOwner}`,
      label,
      keybindings,
      run: () => {
        if (!props.readOnly) emit(eventName, editor.getValue());
      },
    }),
  );
}

onMounted(() => {
  model = monaco.editor.createModel(
    props.modelValue,
    props.language,
    monaco.Uri.parse(`inmemory://xzm/${markerOwner}/Solution.java`),
  );
  editor = monaco.editor.create(container.value, {
    model,
    theme: props.theme,
    readOnly: props.readOnly,
    ariaLabel: "Java 代码编辑器",
    accessibilitySupport: "auto",
    automaticLayout: true,
    minimap: { enabled: false },
    fontFamily: "'JetBrains Mono', 'Cascadia Code', monospace",
    fontLigatures: true,
    fontSize: fontSize.value,
    lineHeight: 23,
    padding: { top: 14, bottom: 14 },
    scrollBeyondLastLine: false,
    smoothScrolling: true,
    cursorSmoothCaretAnimation: "on",
    cursorBlinking: "smooth",
    renderLineHighlight: "all",
    bracketPairColorization: { enabled: true },
    guides: { bracketPairs: true, indentation: true },
    stickyScroll: { enabled: true },
    folding: true,
    foldingHighlight: true,
    showFoldingControls: "mouseover",
    wordWrap: wordWrap.value,
    tabSize: tabSize.value,
    insertSpaces: true,
    detectIndentation: false,
    quickSuggestions: { other: true, comments: false, strings: false },
    suggestOnTriggerCharacters: true,
    acceptSuggestionOnEnter: "on",
    tabCompletion: "on",
    snippetSuggestions: "top",
    parameterHints: { enabled: true, cycle: true },
    suggest: {
      showKeywords: true,
      showSnippets: true,
      showMethods: true,
      showFunctions: true,
      showClasses: true,
      preview: true,
    },
  });

  changeSubscription = editor.onDidChangeModelContent(() => {
    emit("update:modelValue", editor.getValue());
  });
  cursorSubscription = editor.onDidChangeCursorPosition(({ position }) => {
    cursor.line = position.lineNumber;
    cursor.column = position.column;
  });
  completionProvider = monaco.languages.registerCompletionItemProvider("java", {
    triggerCharacters: [".", "(", ",", "@"],
    provideCompletionItems,
  });

  registerEditorAction(
    "xzm-run-code",
    "运行代码",
    [monaco.KeyMod.CtrlCmd | monaco.KeyCode.Enter],
    "run",
  );
  registerEditorAction(
    "xzm-submit-code",
    "提交代码",
    [monaco.KeyMod.CtrlCmd | monaco.KeyMod.Shift | monaco.KeyCode.Enter],
    "submit",
  );

  if (typeof ResizeObserver !== "undefined") {
    resizeObserver = new ResizeObserver(() => editor?.layout());
    resizeObserver.observe(container.value);
  }
  document.addEventListener("fullscreenchange", handleFullscreenChange);
  updateErrorMarkers();
});

watch(
  () => props.modelValue,
  (value) => {
    if (editor && editor.getValue() !== value) editor.setValue(value || "");
  },
);
watch(
  () => props.language,
  (language) => {
    if (model) monaco.editor.setModelLanguage(model, language);
  },
);
watch(
  () => props.theme,
  (theme) => monaco.editor.setTheme(theme),
);
watch(
  () => props.readOnly,
  (readOnly) => editor?.updateOptions({ readOnly }),
);
watch(() => props.errors, updateErrorMarkers, { deep: true });

onBeforeUnmount(() => {
  document.removeEventListener("fullscreenchange", handleFullscreenChange);
  if (document.fullscreenElement === shell.value) {
    document.exitFullscreen?.().catch(() => {});
  }
  resizeObserver?.disconnect();
  completionProvider?.dispose();
  changeSubscription?.dispose();
  cursorSubscription?.dispose();
  actionSubscriptions.forEach((subscription) => subscription.dispose());
  if (model) monaco.editor.setModelMarkers(model, markerOwner, []);
  editor?.dispose();
  model?.dispose();
  editor = null;
  model = null;
});
</script>

<style scoped>
.editor-shell {
  width: 100%;
  height: 100%;
  min-width: 0;
  min-height: 0;
  display: grid;
  grid-template-rows: minmax(0, 1fr) 29px;
  overflow: hidden;
  background: #edf3ef;
}

.monaco-host {
  width: 100%;
  height: 100%;
  min-width: 0;
  min-height: 0;
}

.editor-statusbar {
  z-index: 2;
  display: flex;
  align-items: center;
  gap: 2px;
  min-width: 0;
  padding: 0 6px 0 12px;
  color: #52665e;
  background: #e1e9e4;
  border-top: 1px solid #ccd8d1;
  font:
    500 11px/1 "JetBrains Mono",
    "Cascadia Code",
    monospace;
  user-select: none;
}

.cursor-position {
  min-width: 88px;
  font-variant-numeric: tabular-nums;
}

.status-spacer {
  flex: 1;
}

.editor-statusbar button {
  height: 23px;
  padding: 0 7px;
  border: 0;
  border-radius: 5px;
  color: inherit;
  background: transparent;
  font: inherit;
  white-space: nowrap;
  cursor: pointer;
  transition:
    color 140ms ease,
    background 140ms ease;
}

.editor-statusbar button:hover,
.editor-statusbar button:focus-visible,
.editor-statusbar button.active {
  color: #087b68;
  background: rgba(13, 128, 104, 0.1);
  outline: none;
}

.theme-dark {
  background: #101821;
}

.theme-dark .editor-statusbar {
  color: #a5b2be;
  background: #111d27;
  border-top-color: #273744;
}

.theme-dark .editor-statusbar button:hover,
.theme-dark .editor-statusbar button:focus-visible,
.theme-dark .editor-statusbar button.active {
  color: #70e0c1;
  background: rgba(101, 223, 189, 0.12);
}

.editor-shell:fullscreen,
.editor-shell.fullscreen {
  width: 100vw;
  height: 100vh;
}

@media (max-width: 640px) {
  .cursor-position {
    min-width: 72px;
  }

  .editor-statusbar {
    padding-left: 8px;
  }

  .editor-statusbar button {
    padding-inline: 5px;
  }
}
</style>
