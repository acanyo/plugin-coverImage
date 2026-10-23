import { axiosInstance } from "@halo-dev/api-client";
import { isAxiosError } from "axios";
import { computed, onScopeDispose, ref, type Ref } from "vue";

interface OptimizationContext {
  postName: string;
  model?: string;
  style: string;
  size: string;
}

export function useCoverPromptOptimization(
  prompt: Ref<string>,
  context: () => OptimizationContext,
) {
  const loading = ref(false);
  const error = ref("");
  const previousPrompt = ref<string>();
  const optimizedPrompt = ref<string>();
  const canUndo = computed(() => previousPrompt.value !== undefined
    && prompt.value === optimizedPrompt.value);
  let controller: AbortController | undefined;

  async function optimize() {
    if (loading.value) return false;
    const { postName, ...preferences } = context();
    if (!postName) {
      error.value = "请先保存文章，再优化提示词";
      return false;
    }
    const original = prompt.value;
    const requestController = new AbortController();
    controller = requestController;
    loading.value = true;
    error.value = "";
    try {
      const { data } = await axiosInstance.post<{ prompt: string }>(
        `/apis/coverimage.lik.cc/v1alpha1/optimize-prompt/${encodeURIComponent(postName)}`,
        { ...preferences, prompt: original },
        { signal: requestController.signal },
      );
      if (requestController.signal.aborted) return false;
      const result = data.prompt?.trim();
      if (!result || result.length > 8000) {
        throw new Error("文本模型未返回有效提示词，请重试");
      }
      if (prompt.value !== original) {
        throw new Error("提示词已被修改，优化结果未覆盖当前内容，请重试");
      }
      previousPrompt.value = original;
      optimizedPrompt.value = result;
      prompt.value = result;
      return true;
    } catch (failure) {
      if (!requestController.signal.aborted) {
        error.value = isAxiosError(failure)
          ? failure.response?.data?.message || failure.message || "优化提示词失败，请重试"
          : failure instanceof Error ? failure.message : "优化提示词失败，请重试";
      }
      return false;
    } finally {
      if (controller === requestController) {
        loading.value = false;
        controller = undefined;
      }
    }
  }

  function undo() {
    if (canUndo.value) {
      prompt.value = previousPrompt.value!;
      previousPrompt.value = undefined;
      optimizedPrompt.value = undefined;
      error.value = "";
    }
  }

  onScopeDispose(() => controller?.abort());
  return { loading, error, canUndo, optimize, undo };
}
