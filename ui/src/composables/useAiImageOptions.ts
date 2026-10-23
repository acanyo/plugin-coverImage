import { axiosInstance } from "@halo-dev/api-client";
import { computed, ref, watch, type Ref } from "vue";

interface ImageGenerationCapabilities {
  sizes?: string[];
  aspectRatios?: string[];
}

interface AiModelOption {
  name: string;
  capabilities?: { imageGeneration?: ImageGenerationCapabilities };
}

const fallbackOptions = [
  { label: "2560×1440（封面推荐）", value: "2560x1440" },
  { label: "2048×2048（正方形）", value: "2048x2048" },
  { label: "1920×1080（Full HD）", value: "1920x1080" },
  { label: "1024×1024（兼容性最好）", value: "1024x1024" },
];

export function resolveImageSizeOptions(capabilities?: ImageGenerationCapabilities | null) {
  const sizes = capabilities?.sizes?.filter(Boolean);
  if (sizes?.length) {
    return [...new Set(sizes)].map((value) => ({ label: value.replace("x", "×"), value }));
  }
  const ratios = capabilities?.aspectRatios?.filter(Boolean);
  if (ratios?.length) {
    return [...new Set(ratios)].map((value) => ({ label: `${value}（宽高比）`, value }));
  }
  return fallbackOptions;
}

export function useAiImageOptions(modelName: Ref<string | undefined>, size: Ref<string>) {
  const capabilities = ref<ImageGenerationCapabilities | null>(null);
  const loading = ref(false);
  const options = computed(() => resolveImageSizeOptions(capabilities.value));
  const hasDeclaredSizes = computed(() => Boolean(
    capabilities.value?.sizes?.length || capabilities.value?.aspectRatios?.length,
  ));

  watch(modelName, async (name, _previous, onCleanup) => {
    const controller = new AbortController();
    onCleanup(() => controller.abort());
    capabilities.value = null;
    loading.value = true;
    try {
      let effectiveName = name;
      if (!effectiveName) {
        const { data } = await axiosInstance.get<{ imageGenerationModelName?: string }>(
          "/apis/console.api.aifoundation.halo.run/v1alpha1/default-model-slots",
          { signal: controller.signal },
        );
        effectiveName = data.imageGenerationModelName;
      }
      if (!effectiveName) return;
      const { data } = await axiosInstance.get<AiModelOption[]>(
        "/apis/console.api.aifoundation.halo.run/v1alpha1/model-options",
        { params: { modelType: "image-generation", available: true }, signal: controller.signal },
      );
      if (!controller.signal.aborted) {
        capabilities.value = data.find((option) => option.name === effectiveName)
          ?.capabilities?.imageGeneration ?? null;
      }
    } catch {
      // 未声明能力或读取失败时使用通用选项，最终兼容处理交给 AI 基座。
    } finally {
      if (!controller.signal.aborted) loading.value = false;
    }
  }, { immediate: true });

  watch(options, (availableOptions) => {
    if (!availableOptions.some((option) => option.value === size.value)) {
      size.value = availableOptions[0].value;
    }
  });

  return { options, loading, hasDeclaredSizes };
}
