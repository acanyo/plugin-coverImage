import { beforeEach, describe, expect, it, vi } from "vitest";
import { effectScope, ref } from "vue";
import { AxiosError } from "axios";

const { post } = vi.hoisted(() => ({ post: vi.fn() }));
vi.mock("@halo-dev/api-client", () => ({ axiosInstance: { post } }));
import { useCoverPromptOptimization } from "../useCoverPromptOptimization";

function setup() {
  const prompt = ref("原提示词");
  const scope = effectScope();
  const optimizer = scope.run(() => useCoverPromptOptimization(prompt, () => ({
    postName: "article", model: "text-model", style: "水墨画", size: "16:9",
  })))!;
  return { prompt, scope, ...optimizer };
}

beforeEach(() => { post.mockReset(); });

describe("cover prompt optimization", () => {
  it("fills only the prompt and supports undo without generating images", async () => {
    post.mockResolvedValue({ data: { prompt: " 优化后的绘图提示词 " } });
    const state = setup();
    expect(await state.optimize()).toBe(true);
    expect(state.prompt.value).toBe("优化后的绘图提示词");
    expect(post).toHaveBeenCalledOnce();
    expect(post.mock.calls[0][0]).toContain("/optimize-prompt/article");
    expect(post.mock.calls[0][1]).toEqual({
      prompt: "原提示词", model: "text-model", style: "水墨画", size: "16:9",
    });
    expect(state.canUndo.value).toBe(true);
    state.undo();
    expect(state.prompt.value).toBe("原提示词");
    state.scope.stop();
  });

  it("preserves the current prompt and displays the server error", async () => {
    const failure = new AxiosError("service unavailable");
    failure.response = { data: { message: "请配置默认文本模型" } } as never;
    post.mockRejectedValue(failure);
    const state = setup();
    expect(await state.optimize()).toBe(false);
    expect(state.prompt.value).toBe("原提示词");
    expect(state.error.value).toBe("请配置默认文本模型");
    expect(state.loading.value).toBe(false);
    state.scope.stop();
  });

  it("ignores responses after leaving the form and prevents duplicate requests", async () => {
    let finish!: (result: unknown) => void;
    post.mockImplementation(() => new Promise((resolve) => { finish = resolve; }));
    const state = setup();
    const pending = state.optimize();
    expect(await state.optimize()).toBe(false);
    state.scope.stop();
    finish({ data: { prompt: "应丢弃的结果" } });
    expect(await pending).toBe(false);
    expect(state.prompt.value).toBe("原提示词");
    expect(post).toHaveBeenCalledOnce();
  });

  it("does not overwrite edits made while waiting", async () => {
    let finish!: (result: unknown) => void;
    post.mockImplementation(() => new Promise((resolve) => { finish = resolve; }));
    const state = setup();
    const pending = state.optimize();
    state.prompt.value = "手动修改";
    finish({ data: { prompt: "优化结果" } });
    expect(await pending).toBe(false);
    expect(state.prompt.value).toBe("手动修改");
    state.scope.stop();
  });
});
