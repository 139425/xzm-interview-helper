import { mount } from "@vue/test-utils";
import { afterEach, beforeEach, describe, expect, it, vi } from "vitest";

const mocks = vi.hoisted(() => {
  const disposables = {
    completion: { dispose: vi.fn() },
    change: { dispose: vi.fn() },
    cursor: { dispose: vi.fn() },
    runAction: { dispose: vi.fn() },
    submitAction: { dispose: vi.fn() },
  };
  const model = {
    getValue: vi.fn(() => "class Solution {}"),
    setValue: vi.fn(),
    updateOptions: vi.fn(),
    getWordUntilPosition: vi.fn(() => ({ startColumn: 1, endColumn: 1 })),
    dispose: vi.fn(),
  };
  const editorInstance = {
    getValue: vi.fn(() => "class Solution {}"),
    setValue: vi.fn(),
    updateOptions: vi.fn(),
    layout: vi.fn(),
    dispose: vi.fn(),
    onDidChangeModelContent: vi.fn(() => disposables.change),
    onDidChangeCursorPosition: vi.fn(() => disposables.cursor),
    addAction: vi
      .fn()
      .mockImplementationOnce(() => disposables.runAction)
      .mockImplementationOnce(() => disposables.submitAction),
  };
  const monaco = {
    editor: {
      defineTheme: vi.fn(),
      createModel: vi.fn(() => model),
      create: vi.fn(() => editorInstance),
      setModelMarkers: vi.fn(),
      setModelLanguage: vi.fn(),
      setTheme: vi.fn(),
    },
    languages: {
      CompletionItemKind: {
        Text: 0,
        Keyword: 1,
        Class: 2,
        Method: 3,
        Snippet: 4,
        Variable: 5,
      },
      CompletionItemInsertTextRule: { InsertAsSnippet: 1 },
      registerCompletionItemProvider: vi.fn(() => disposables.completion),
    },
    MarkerSeverity: { Error: 8, Warning: 4, Info: 2, Hint: 1 },
    Uri: { parse: vi.fn((value) => value) },
    KeyMod: { CtrlCmd: 1, Shift: 2 },
    KeyCode: { Enter: 3 },
  };
  return { disposables, editorInstance, model, monaco };
});

vi.mock("monaco-editor/esm/vs/editor/editor.api", () => mocks.monaco);
vi.mock("monaco-editor/esm/vs/basic-languages/java/java.contribution", () => ({}));
vi.mock("monaco-editor/esm/vs/editor/editor.worker?worker", () => ({
  default: class EditorWorker {},
}));

import MonacoCodeEditor from "@/components/algorithm/MonacoCodeEditor.vue";

describe("MonacoCodeEditor lifecycle", () => {
  let resizeDisconnect;

  beforeEach(() => {
    vi.clearAllMocks();
    mocks.editorInstance.addAction
      .mockReset()
      .mockImplementationOnce(() => mocks.disposables.runAction)
      .mockImplementationOnce(() => mocks.disposables.submitAction);
    resizeDisconnect = vi.fn();
    vi.stubGlobal(
      "ResizeObserver",
      class ResizeObserver {
        observe() {}
        disconnect() {
          resizeDisconnect();
        }
      },
    );
  });

  afterEach(() => {
    vi.unstubAllGlobals();
  });

  it("disposes the provider, editor actions, observers, markers and model", () => {
    const wrapper = mount(MonacoCodeEditor, {
      props: { modelValue: "class Solution {}", language: "java" },
    });

    expect(mocks.monaco.languages.registerCompletionItemProvider).toHaveBeenCalledTimes(1);
    expect(mocks.editorInstance.addAction).toHaveBeenCalledTimes(2);

    wrapper.unmount();

    expect(resizeDisconnect).toHaveBeenCalledTimes(1);
    expect(mocks.disposables.completion.dispose).toHaveBeenCalledTimes(1);
    expect(mocks.disposables.change.dispose).toHaveBeenCalledTimes(1);
    expect(mocks.disposables.cursor.dispose).toHaveBeenCalledTimes(1);
    expect(mocks.disposables.runAction.dispose).toHaveBeenCalledTimes(1);
    expect(mocks.disposables.submitAction.dispose).toHaveBeenCalledTimes(1);
    expect(mocks.monaco.editor.setModelMarkers).toHaveBeenLastCalledWith(
      mocks.model,
      expect.any(String),
      [],
    );
    expect(mocks.editorInstance.dispose).toHaveBeenCalledTimes(1);
    expect(mocks.model.dispose).toHaveBeenCalledTimes(1);
  });
});
