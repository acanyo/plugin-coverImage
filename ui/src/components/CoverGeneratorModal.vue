<script lang="ts" setup>
import { ref, computed, onUnmounted, watch } from "vue";
import { VButton, VModal, VSpace, Toast, VCard } from "@halo-dev/components";
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

// Tabs
type TabType = "aiGenerated" | "randomImg" | "firstPostImg" | "customizeImg";
const currentTab = ref<TabType>("aiGenerated");
const tabs = [
  { label: "AI 生成", value: "aiGenerated" },
  { label: "随机图", value: "randomImg" },
  { label: "文章首图", value: "firstPostImg" },
  { label: "自定义", value: "customizeImg" },
] as const;

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
    message.value = "正在处理...";
    elapsedTime.value = 0;
    imageUrl.value = "";

    // 开始计时
    elapsedTimer = window.setInterval(() => {
      elapsedTime.value++;
    }, 1000);

    // 调用生成 API，带上 type 参数
    const response = await axios.post(
      `/apis/coverimage.lik.cc/v1alpha1/generate/${props.postName}?type=${currentTab.value}`
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
      
      // 如果状态是 idle 但我们期望是 generating，可能任务还没开始或已丢失，但在轮询上下文中通常意味着任务还在队列或刚开始
      // 这里主要关注 success 和 failed
      
      if (data.status === "generating") {
         message.value = data.message || "正在处理...";
      } else if (data.status === "success") {
        handleSuccess(data.imageUrl);
      } else if (data.status === "failed") {
        status.value = "failed";
        message.value = data.message;
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
  Toast.success("封面图设置成功！");
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

// 切换 Tab 时重置部分状态 (如果不在生成中)
function handleTabChange(tab: TabType) {
  if (!isGenerating.value) {
    currentTab.value = tab;
    status.value = "idle";
    message.value = "";
  }
}

// 监听 visible 变化
watch(() => props.visible, async (newVal) => {
  if (newVal && props.postName) {
    // 检查是否有正在进行的任务
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

onUnmounted(() => {
  stopTimers();
});
</script>

<template>
  <VModal
    v-model:visible="modalVisible"
    :title="'封面生成工具 - ' + postTitle"
    :width="600"
    @close="handleClose"
  >
    <div class="cover-generator-content">
      <!-- Tabs -->
      <div class="tabs-header">
        <div 
          v-for="tab in tabs" 
          :key="tab.value"
          class="tab-item"
          :class="{ active: currentTab === tab.value, disabled: isGenerating }"
          @click="handleTabChange(tab.value)"
        >
          {{ tab.label }}
        </div>
      </div>

      <div class="tab-content">
        <VSpace direction="column" spacing="md">
          
          <!-- 状态展示区域 -->
          <div v-if="status === 'generating'" class="status-generating">
             <div class="generating-animation">
               <span class="spinner"></span>
             </div>
             <p class="generating-text">{{ message }}</p>
             <p class="elapsed-time">已用时 {{ elapsedTime }} 秒</p>
          </div>

          <div v-else-if="status === 'success'" class="status-success">
            <p class="success-text">操作成功！</p>
            <div class="preview-image">
              <img :src="imageUrl" alt="生成的封面图" />
            </div>
          </div>

          <div v-else-if="status === 'failed'" class="status-failed">
            <p class="failed-text">操作失败</p>
            <p class="failed-message">{{ message }}</p>
          </div>

          <!-- 默认说明区域 (Idle 状态) -->
          <div v-else class="tab-description">
            <div v-if="currentTab === 'aiGenerated'">
              <p><strong>AI 智能生成</strong></p>
              <p class="desc">根据文章内容，自动提取关键词并生成匹配的封面图。</p>
              <p class="note">耗时较长 (30-60秒)，请耐心等待。</p>
            </div>
            <div v-else-if="currentTab === 'randomImg'">
              <p><strong>随机图片</strong></p>
              <p class="desc">从配置的图库源（如 Unsplash, Bing 等）随机获取一张高质量图片。</p>
            </div>
            <div v-else-if="currentTab === 'firstPostImg'">
              <p><strong>文章首图</strong></p>
              <p class="desc">提取文章内容中的第一张图片作为封面。</p>
              <p class="note">如果文章中没有图片，将无法设置。</p>
            </div>
            <div v-else-if="currentTab === 'customizeImg'">
              <p><strong>自定义生成</strong></p>
              <p class="desc">使用插件配置的自定义策略生成图片（如文字合成图）。</p>
            </div>
          </div>

        </VSpace>
      </div>
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
          {{ status === 'failed' ? '重试' : (status === 'success' ? '重新生成' : '开始生成') }}
        </VButton>
      </VSpace>
    </template>
  </VModal>
</template>

<style scoped>
.cover-generator-content {
  padding: 0;
}

.tabs-header {
  display: flex;
  border-bottom: 1px solid var(--border-color);
  margin-bottom: 16px;
}

.tab-item {
  padding: 10px 20px;
  cursor: pointer;
  border-bottom: 2px solid transparent;
  color: var(--text-color-secondary);
  transition: all 0.3s;
}

.tab-item:hover:not(.disabled) {
  color: var(--primary-color);
}

.tab-item.active {
  color: var(--primary-color);
  border-bottom-color: var(--primary-color);
  font-weight: 500;
}

.tab-item.disabled {
  cursor: not-allowed;
  opacity: 0.5;
}

.tab-content {
  padding: 0 16px 16px;
  min-height: 200px;
}

.tab-description {
  text-align: center;
  padding: 20px;
  background: var(--bg-color-hover);
  border-radius: 8px;
}

.tab-description p {
  margin: 8px 0;
}

.desc {
  color: var(--text-color-secondary);
}

.note {
  font-size: 12px;
  color: var(--text-color-tertiary);
}

/* 复用之前的样式 */
.status-generating, .status-failed, .status-success {
  text-align: center;
  padding: 20px 0;
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
  to { transform: rotate(360deg); }
}

.preview-image img {
  max-width: 100%;
  border-radius: 8px;
  border: 1px solid var(--border-color);
}

.failed-text { color: #ff4d4f; }
.success-text { color: #52c41a; }
</style>
