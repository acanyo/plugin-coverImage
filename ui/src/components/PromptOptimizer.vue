<script lang="ts" setup>
import { onBeforeUnmount, ref, watch } from "vue";
import { Toast, VAlert, VButton } from "@halo-dev/components";
import { useCoverPromptOptimization } from "../composables/useCoverPromptOptimization";

const prompt = defineModel<string>({ required: true });
const props = defineProps<{
  postName: string;
  imageStyle: string;
  imageSize: string;
}>();
const emit = defineEmits<{ "update:loading": [value: boolean] }>();
const textModel = ref<string>();
const { loading, error, canUndo, optimize, undo } = useCoverPromptOptimization(prompt, () => ({
  postName: props.postName,
  model: textModel.value,
  style: props.imageStyle,
  size: props.imageSize,
}));

watch(loading, (value) => emit("update:loading", value));
onBeforeUnmount(() => emit("update:loading", false));

async function handleOptimize() {
  if (await optimize()) Toast.success("提示词已优化，可继续修改后生成封面");
}
</script>

<template>
  <div>
    <FormKit
      v-model="textModel"
      type="aiModelSelector"
      name="cover-prompt-model"
      label="提示词优化模型"
      model-type="language"
      :available="true"
      :clearable="true"
      :disabled="loading"
      placeholder="留空使用 AI 基座默认文本模型"
      help="结合已保存的文章、当前提示词、尺寸和风格生成绘图提示词。"
    />
    <div class="flex gap-2">
      <VButton size="sm" type="secondary" :loading="loading" :disabled="loading" @click="handleOptimize">
        {{ loading ? "正在优化提示词…" : "AI 优化提示词" }}
      </VButton>
      <VButton v-if="canUndo" size="sm" :disabled="loading" @click="undo">撤销优化</VButton>
    </div>
    <VAlert v-if="error" class="mt-3" type="error" :title="error" :closable="false" />
  </div>
</template>
