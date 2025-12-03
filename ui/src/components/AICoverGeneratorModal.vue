<script lang="ts" setup>
import { ref, computed, onUnmounted, watch } from "vue";
import { VButton, VModal, VSpace, Toast } from "@halo-dev/components";
import axios from "axios";

const props = defineProps<{
  visible: boolean;
  postName: string;
  postTitle: string;
}>();

const emit = defineEmits<{
  (e: "update:visible", value: boolean): void;
  (e: "success", imageUrl: string): void;
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

const modalVisible = computed({
  get: () => props.visible,
  set: (value: boolean) => emit("update:visible", value),
});

// 触发生成
async function handleGenerate() {
  if (isGenerating.value) return;

  try {
    status.value = "generating";
    message.value = "正在生成封面图...";
    elapsedTime.value = 0;
    imageUrl.value = "";

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
    } else if (response.data.status === "success") {
      handleSuccess(response.data.imageUrl);
    }
  } catch (error: any) {
    status.value = "failed";
    message.value = error.response?.data?.message || error.message || "请求失败";
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
      message.value = data.message;

      if (data.status === "success") {
        handleSuccess(data.imageUrl);
      } else if (data.status === "failed") {
        status.value = "failed";
        stopTimers();
        Toast.error("生成失败: " + data.message);
      }
    } catch (error) {
      console.error("轮询状态失败:", error);
    }
  }, 2000);
}

function handleSuccess(url: string) {
  status.value = "success";
  imageUrl.value = url;
  stopTimers();
  Toast.success("封面图生成成功！");
  emit("success", url);
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

// 关闭弹窗时重置状态
function handleClose() {
  stopTimers();
  status.value = "idle";
  message.value = "";
  imageUrl.value = "";
  elapsedTime.value = 0;
  modalVisible.value = false;
}

// 监听 visible 变化，当打开时检查是否有进行中的任务
watch(() => props.visible, async (newVal) => {
  if (newVal && props.postName) {
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
  }
});

// 组件卸载时清理
onUnmounted(() => {
  stopTimers();
});
</script>

<template>
  <VModal
    v-model:visible="modalVisible"
    :title="'AI 生成封面 - ' + postTitle"
    :width="500"
    @close="handleClose"
  >
    <div class="ai-cover-modal-content">
      <VSpace direction="column" spacing="md">
        <!-- 状态提示 -->
        <div v-if="status === 'idle'" class="status-hint">
          <p>点击下方按钮，AI 将根据文章内容自动生成封面图。</p>
          <p class="hint-note">生成过程可能需要 30-60 秒，请耐心等待。</p>
        </div>

        <!-- 生成中状态 -->
        <div v-else-if="status === 'generating'" class="status-generating">
          <div class="generating-animation">
            <span class="spinner"></span>
          </div>
          <p class="generating-text">{{ message }}</p>
          <p class="elapsed-time">已用时 {{ elapsedTime }} 秒</p>
        </div>

        <!-- 成功状态 -->
        <div v-else-if="status === 'success'" class="status-success">
          <p class="success-text">封面图已生成并设置成功！</p>
          <div class="preview-image">
            <img :src="imageUrl" alt="生成的封面图" />
          </div>
        </div>

        <!-- 失败状态 -->
        <div v-else-if="status === 'failed'" class="status-failed">
          <p class="failed-text">生成失败</p>
          <p class="failed-message">{{ message }}</p>
        </div>
      </VSpace>
    </div>

    <template #footer>
      <VSpace>
        <VButton @click="handleClose">
          {{ status === 'success' ? '完成' : '关闭' }}
        </VButton>
        <VButton
          v-if="status !== 'generating'"
          type="primary"
          :loading="isGenerating"
          @click="handleGenerate"
        >
          {{ status === 'failed' ? '重新生成' : (status === 'success' ? '重新生成' : '开始生成') }}
        </VButton>
      </VSpace>
    </template>
  </VModal>
</template>

<style scoped>
.ai-cover-modal-content {
  padding: 16px 0;
}

.status-hint {
  text-align: center;
  color: var(--text-color-secondary);
}

.status-hint p {
  margin: 8px 0;
}

.hint-note {
  font-size: 12px;
  color: var(--text-color-tertiary);
}

.status-generating {
  text-align: center;
  padding: 24px 0;
}

.generating-animation {
  margin-bottom: 16px;
}

.spinner {
  display: inline-block;
  width: 40px;
  height: 40px;
  border: 3px solid var(--border-color);
  border-top-color: var(--primary-color);
  border-radius: 50%;
  animation: spin 1s linear infinite;
}

@keyframes spin {
  to {
    transform: rotate(360deg);
  }
}

.generating-text {
  font-size: 14px;
  color: var(--text-color-primary);
  margin: 8px 0;
}

.elapsed-time {
  font-size: 12px;
  color: var(--text-color-tertiary);
}

.status-success {
  text-align: center;
}

.success-text {
  color: #52c41a;
  font-size: 14px;
  font-weight: 500;
  margin-bottom: 16px;
}

.preview-image {
  border-radius: 8px;
  overflow: hidden;
  border: 1px solid var(--border-color);
}

.preview-image img {
  width: 100%;
  height: auto;
  display: block;
}

.status-failed {
  text-align: center;
  padding: 24px 0;
}

.failed-text {
  color: #ff4d4f;
  font-size: 14px;
  font-weight: 500;
  margin-bottom: 8px;
}

.failed-message {
  color: var(--text-color-secondary);
  font-size: 12px;
}
</style>
