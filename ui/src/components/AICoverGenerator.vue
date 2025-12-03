<script lang="ts" setup>
import { ref, onMounted, onUnmounted, computed } from "vue";
import { VButton, VLoading, VSpace, Toast } from "@halo-dev/components";
import axios from "axios";

const props = defineProps<{
  postName: string;
}>();

const emit = defineEmits<{
  (e: "update:cover", url: string): void;
}>();

// 状态
const status = ref<"idle" | "generating" | "success" | "failed">("idle");
const message = ref("");
const imageUrl = ref("");
const elapsedTime = ref(0);

let pollingTimer: number | null = null;
let elapsedTimer: number | null = null;

// 计算属性
const isGenerating = computed(() => status.value === "generating");
const buttonText = computed(() => {
  switch (status.value) {
    case "generating":
      return `生成中... ${elapsedTime.value}s`;
    case "success":
      return "生成成功";
    case "failed":
      return "重新生成";
    default:
      return "AI 生成封面";
  }
});

const buttonType = computed(() => {
  switch (status.value) {
    case "success":
      return "primary";
    case "failed":
      return "danger";
    default:
      return "secondary";
  }
});

// 触发生成
async function handleGenerate() {
  if (isGenerating.value) return;

  try {
    status.value = "generating";
    message.value = "正在生成封面图...";
    elapsedTime.value = 0;

    // 开始计时
    elapsedTimer = window.setInterval(() => {
      elapsedTime.value++;
    }, 1000);

    // 调用生成 API
    const response = await axios.post(
      `/apis/coverimage.lik.cc/v1alpha1/generate/${props.postName}`
    );

    if (response.data.status === "generating") {
      // 开始轮询状态
      startPolling();
    }
  } catch (error: any) {
    status.value = "failed";
    message.value = error.message || "请求失败";
    stopTimers();
    Toast.error("生成失败: " + message.value);
  }
}

// 轮询状态
function startPolling() {
  pollingTimer = window.setInterval(async () => {
    try {
      const response = await axios.get(
        `/apis/coverimage.lik.cc/v1alpha1/status/${props.postName}`
      );

      const data = response.data;
      status.value = data.status;
      message.value = data.message;

      if (data.status === "success") {
        imageUrl.value = data.imageUrl;
        emit("update:cover", data.imageUrl);
        stopTimers();
        Toast.success("封面图生成成功！");
      } else if (data.status === "failed") {
        stopTimers();
        Toast.error("生成失败: " + data.message);
      }
    } catch (error) {
      console.error("轮询状态失败:", error);
    }
  }, 2000);
}

// 停止计时器
function stopTimers() {
  if (pollingTimer) {
    clearInterval(pollingTimer);
    pollingTimer = null;
  }
  if (elapsedTimer) {
    clearInterval(elapsedTimer);
    elapsedTimer = null;
  }
}

// 组件卸载时清理
onUnmounted(() => {
  stopTimers();
});

// 组件挂载时检查是否有进行中的任务
onMounted(async () => {
  try {
    const response = await axios.get(
      `/apis/coverimage.lik.cc/v1alpha1/status/${props.postName}`
    );
    if (response.data.status === "generating") {
      status.value = "generating";
      message.value = response.data.message;
      startPolling();
      elapsedTimer = window.setInterval(() => {
        elapsedTime.value++;
      }, 1000);
    }
  } catch (error) {
    // 忽略错误
  }
});
</script>

<template>
  <div class="ai-cover-generator">
    <VSpace direction="column" spacing="sm">
      <div class="generator-header">
        <span class="label">AI 生成封面</span>
      </div>

      <VButton
        :type="buttonType"
        :loading="isGenerating"
        :disabled="isGenerating"
        size="sm"
        block
        @click="handleGenerate"
      >
        <template #icon>
          <span v-if="!isGenerating" class="icon">✨</span>
        </template>
        {{ buttonText }}
      </VButton>

      <div v-if="message && status !== 'idle'" class="status-message" :class="status">
        {{ message }}
      </div>

      <div v-if="imageUrl && status === 'success'" class="preview">
        <img :src="imageUrl" alt="生成的封面图" />
      </div>
    </VSpace>
  </div>
</template>

<style scoped>
.ai-cover-generator {
  padding: 12px;
  border: 1px solid var(--border-color);
  border-radius: 8px;
  background: var(--surface-color);
}

.generator-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
}

.generator-header .label {
  font-size: 14px;
  font-weight: 500;
  color: var(--text-color-primary);
}

.icon {
  margin-right: 4px;
}

.status-message {
  font-size: 12px;
  padding: 8px;
  border-radius: 4px;
}

.status-message.generating {
  background: #e6f7ff;
  color: #1890ff;
}

.status-message.success {
  background: #f6ffed;
  color: #52c41a;
}

.status-message.failed {
  background: #fff2f0;
  color: #ff4d4f;
}

.preview {
  margin-top: 8px;
  border-radius: 4px;
  overflow: hidden;
}

.preview img {
  width: 100%;
  height: auto;
  display: block;
}
</style>
