<template>
  <div class="cover-generator-toolbar-item">
    <VDropdown
      v-model:shown="dropdownVisible"
      :disabled="disabled"
      :triggers="['click']"
      :auto-hide="false"
      popper-class="cover-generator-popper"
      @update:shown="handleOpenDropdown"
    >
      <button
        v-tooltip="'AI 封面生成'"
        aria-label="封面生成工具"
        class="toolbar-btn"
        :class="{ active: isActive || dropdownVisible }"
        :disabled="disabled"
      >
        <IconPicAi class="h-4 w-4" />
      </button>

      <template #popper>
        <div class="cover-generator-dropdown" @click.stop>
          <!-- 使用说明 -->
          <div class="p-3">
            <VAlert
              type="info"
              title="封面生成工具"
              description="选择一种方式为当前文章生成封面图，生成成功后将自动设置为文章封面。"
              :closable="false"
              class="text-xs"
            />
          </div>

          <!-- 标签页导航 -->
          <div class="p-3">
            <VTabbar
              v-model:activeId="currentTab"
              :items="tabItems"
              type="default"
            />
          </div>

          <!-- 内容区域 -->
          <div class="p-3 content-area">
            
            <!-- 状态展示 (生成中/成功/失败) -->
            <div v-if="status !== 'idle'" class="status-container">
              <div v-if="status === 'generating'" class="flex flex-col items-center justify-center py-8">
                <VLoading />
                <p class="mt-4 text-sm text-gray-500">{{ message }}</p>
                <p class="text-xs text-gray-400 mt-1">已用时 {{ elapsedTime }} 秒</p>
              </div>

              <div v-else-if="status === 'success'" class="flex flex-col items-center justify-center py-4">
                <div class="text-green-500 mb-2 flex items-center gap-2">
                  <IconCheckCircle class="h-5 w-5" />
                  <span class="font-medium">生成成功</span>
                </div>
                <div class="preview-image mb-4">
                  <img :src="imageUrl" alt="生成的封面图" />
                </div>
                <p class="text-xs text-green-600 mb-3">封面图已自动设置到当前文章</p>
                <div class="flex gap-2">
                  <VButton size="sm" @click="insertToEditor">
                    <template #icon>
                      <IconImage class="h-4 w-4" />
                    </template>
                    插入正文
                  </VButton>
                  <VButton size="sm" type="secondary" @click="resetStatus">返回</VButton>
                </div>
              </div>

              <div v-else-if="status === 'failed'" class="flex flex-col items-center justify-center py-8">
                <div class="text-red-500 mb-2 flex items-center gap-2">
                  <IconAlertCircle class="h-5 w-5" />
                  <span class="font-medium">生成失败</span>
                </div>
                <p class="text-sm text-gray-500 mb-4">{{ message }}</p>
                <VButton size="sm" type="primary" @click="handleGenerate">重试</VButton>
                <VButton size="sm" type="secondary" class="mt-2" @click="resetStatus">返回</VButton>
              </div>
            </div>

            <!-- 配置表单 (Idle 状态) -->
            <div v-else class="config-container">
              
              <!-- AI 生成 -->
              <div v-if="currentTab === 'aiGenerated'" class="tab-pane">
                <div class="info-box">
                  <IconSparkles class="h-5 w-5 text-purple-500 mb-2" />
                  <h4 class="font-medium text-gray-900">AI 智能生成</h4>
                  <p class="text-xs text-gray-500 mt-1">选择 AI 基座中的图像模型，根据文章内容生成封面图。</p>
                </div>

                <FormKit
                  v-model="aiModelName"
                  type="aiModelSelector"
                  name="cover-ai-model"
                  label="图片生成模型"
                  model-type="image-generation"
                  :available="true"
                  :clearable="true"
                  :required-capabilities="requiredImageCapabilities"
                  placeholder="请选择图像生成模型（留空使用默认模型）"
                  help="模型由 Halo AI 基座提供，选择结果只对本次生成生效。"
                />
                
                <FormKit
                  v-model="aiSize"
                  type="select"
                  name="cover-ai-size"
                  label="图片尺寸 / 比例"
                  :options="effectiveSizeOptions"
                  :disabled="modelLoading"
                  :help="modelLoading ? '正在读取模型支持的尺寸…' : hasDeclaredSizes
                    ? '已按当前模型能力筛选选项。' : '模型未声明尺寸，使用通用选项。'"
                />

                <FormKit
                  v-model="aiStyle"
                  type="select"
                  name="cover-ai-style"
                  label="图片风格"
                  :options="styleOptions"
                />

                <FormKit
                  v-model="aiPrompt"
                  type="textarea"
                  name="cover-ai-prompt"
                  label="提示词"
                  rows="7"
                  maxlength="8000"
                  :disabled="promptOptimizing"
                  help="支持 {title} 和 {content} 占位符；只对本次生成生效。"
                />
                <PromptOptimizer
                  v-model="aiPrompt"
                  :post-name="postName"
                  :image-style="aiStyle"
                  :image-size="aiSize"
                  @update:loading="promptOptimizing = $event"
                />
              </div>

              <!-- 自定义封面 -->
              <div v-else-if="currentTab === 'customCover'" class="tab-pane">
                <div class="info-box">
                  <IconPaintbrush class="h-5 w-5 text-pink-500 mb-2" />
                  <h4 class="font-medium text-gray-900">自定义封面</h4>
                  <p class="text-xs text-gray-500 mt-1">使用可视化编辑器自定义标题、图标、渐变背景和版式。</p>
                </div>
                <div class="mt-4 flex justify-center">
                  <VButton type="primary" @click="openCustomCoverModal">
                    <template #icon>
                      <IconPaintbrush />
                    </template>
                    打开封面编辑器
                  </VButton>
                </div>
              </div>

              <!-- 随机图 -->
              <div v-else-if="currentTab === 'randomImg'" class="tab-pane">
                <div class="info-box">
                  <IconImage class="h-5 w-5 text-blue-500 mb-2" />
                  <h4 class="font-medium text-gray-900">随机图片</h4>
                  <p class="text-xs text-gray-500 mt-1">选择图库源后获取一张高质量图片。</p>
                </div>
                <div class="mb-4 text-left">
                  <FormKit
                    v-model="randomType"
                    type="select"
                    name="random-type"
                    label="随机图类型"
                    :options="randomTypeOptions"
                    help="选择本次生成使用的随机图片来源。"
                  />
                </div>
                <p v-if="randomType === 'custom'" class="text-xs text-gray-500">
                  使用插件「随机图设置」中保存的自定义接口；支持直接图片、图片地址和 JSON。
                </p>
              </div>

              <!-- 文章首图 -->
              <div v-else-if="currentTab === 'firstPostImg'" class="tab-pane">
                <div class="info-box">
                  <IconCamera class="h-5 w-5 text-green-500 mb-2" />
                  <h4 class="font-medium text-gray-900">文章首图</h4>
                  <p class="text-xs text-gray-500 mt-1">提取文章内容中的第一张图片作为封面。</p>
                  <p class="text-xs text-orange-400 mt-2">注意：如果文章中没有图片，将无法生成。</p>
                </div>
              </div>
              
              <!-- 底部操作栏 -->
              <div v-if="currentTab !== 'customCover'" class="mt-4 flex justify-end pt-4 border-t border-gray-100">
                <VButton 
                  type="primary" 
                  size="sm"
                  :disabled="currentTab === 'aiGenerated' && (modelLoading || promptOptimizing)"
                  @click="handleGenerate"
                >
                  <template #icon>
                    <IconWand2 v-if="currentTab === 'aiGenerated'" />
                    <IconRefresh v-else />
                  </template>
                  {{ generateButtonText }}
                </VButton>
              </div>

            </div>
          </div>
        </div>
      </template>
    </VDropdown>

    <VModal
      v-model:visible="customCoverModalVisible"
      title="自定义封面编辑器"
      :width="960"
      :layer-closable="false"
    >
      <CustomCoverGenerator @confirm="handleCustomCoverConfirm" />
    </VModal>
  </div>
</template>

<script lang="ts" setup>
import { ref, computed, onBeforeUnmount } from "vue";
import type { Editor } from "@tiptap/core";
import { 
  VButton, 
  VDropdown, 
  VTabbar,
  VAlert, 
  Toast,
  VLoading,
  VModal
} from "@halo-dev/components";
import axios from "axios";
import { consoleApiClient } from "@halo-dev/api-client";
import CustomCoverGenerator from "./CustomCoverGenerator.vue";
import PromptOptimizer from "./PromptOptimizer.vue";
import { useAiImageOptions } from "../composables/useAiImageOptions";

// Icons
import IconPicAi from "~icons/mingcute/pic-ai-line";
import IconSparkles from "~icons/lucide/sparkles";
import IconImage from "~icons/lucide/image";
import IconCamera from "~icons/lucide/camera";
import IconCheckCircle from "~icons/lucide/check-circle";
import IconAlertCircle from "~icons/lucide/alert-circle";
import IconWand2 from "~icons/lucide/wand-2";
import IconRefresh from "~icons/lucide/refresh-cw";
import IconPaintbrush from "~icons/lucide/paintbrush";

interface Props {
  editor: Editor;
  isActive?: boolean;
  disabled?: boolean;
}

const props = withDefaults(defineProps<Props>(), {
  isActive: false,
  disabled: false
});

const dropdownVisible = ref(false);
const customCoverModalVisible = ref(false);

// Tabs
type TabType = "aiGenerated" | "customCover" | "randomImg" | "firstPostImg";
const currentTab = ref<TabType>("aiGenerated");
const tabItems = [
  { label: "AI 生成", id: "aiGenerated" },
  { label: "自定义封面", id: "customCover" },
  { label: "随机图", id: "randomImg" },
  { label: "文章首图", id: "firstPostImg" },
];

// 状态
const status = ref<"idle" | "generating" | "success" | "failed">("idle");
const message = ref("");
const imageUrl = ref("");
const elapsedTime = ref(0);
const postName = ref("");
const aiModelName = ref<string | undefined>("");
const requiredImageCapabilities = { imageGeneration: { textToImage: true } };
const aiSize = ref("2560x1440");
const aiStyle = ref("默认");
const defaultPrompt = `请为这篇博客文章创作一张适合作为文章封面的横版图片。
创作要求：准确提炼文章主题，突出核心概念；构图简洁、主体明确，适合 16:9 横版展示；
使用高级、统一的视觉风格，保证主体清晰、层次分明；不要生成任何文字、字母、Logo、水印或边框；
避免杂乱背景、低清晰度、畸形物体和重复元素。
文章标题：{title}
文章内容摘要：{content}`;
const aiPrompt = ref(defaultPrompt);
const promptOptimizing = ref(false);
let promptDefaultsLoaded = false;
const randomType = ref("alcy");
const randomTypeOptions = [
  { label: "栗次元随机图", value: "alcy" },
  { label: "风景", value: "views" },
  { label: "自定义接口", value: "custom" },
];
const {
  options: effectiveSizeOptions,
  loading: modelLoading,
  hasDeclaredSizes,
} = useAiImageOptions(aiModelName, aiSize);

const styleOptions = computed(() => [
  { label: "默认 (智能匹配)", value: "默认" },
  { label: "写实摄影", value: "写实摄影" },
  { label: "二次元/动漫", value: "二次元/动漫" },
  { label: "3D 渲染 (C4D风格)", value: "3D渲染" },
  { label: "赛博朋克", value: "赛博朋克" },
  { label: "中国风/水墨", value: "水墨画" },
  { label: "油画/艺术", value: "油画" },
  { label: "扁平插画", value: "扁平插画" },
  { label: "极简主义", value: "极简主义" }
]);

let elapsedTimer: number | null = null;

// 计算属性
const generateButtonText = computed(() => {
  switch (currentTab.value) {
    case "aiGenerated": return "开始生成";
    case "randomImg": return "获取随机图";
    case "firstPostImg": return "提取首图";
    default: return "生成";
  }
});

function handleOpenDropdown(visible: boolean) {
  if (!visible) {
    dropdownVisible.value = false;
    return;
  }
  
  // 尝试从 URL 获取 postName
  // 优先尝试从查询参数获取
  const urlParams = new URLSearchParams(window.location.search);
  let name = urlParams.get('name');

  // 如果查询参数没有，尝试从路径获取
  if (!name) {
    const match = window.location.pathname.match(/\/posts\/([^/]+)\/edit/);
    name = match ? match[1] : null;
  }
  
  if (!name) {
    Toast.warning("无法获取文章名称，请先保存文章或确保在文章编辑页面使用");
    dropdownVisible.value = false;
    return;
  }
  
  postName.value = name;
  dropdownVisible.value = true;
  void loadDefaultPrompt();
}

async function loadDefaultPrompt() {
  if (promptDefaultsLoaded) return;
  promptDefaultsLoaded = true;
  try {
    const { data } = await consoleApiClient.plugin.plugin.fetchPluginJsonConfig({ name: "coverImage" });
    const config = data as { ai?: { promptTemplate?: string }; random?: { defaultSource?: string } };
    const template = config.ai?.promptTemplate;
    const source = config.random?.defaultSource;
    if (source && randomType.value === "alcy" && randomTypeOptions.some(option => option.value === source)) {
      randomType.value = source;
    }
    if (template?.trim() && aiPrompt.value === defaultPrompt) {
      aiPrompt.value = template;
    }
  } catch {
    // 无设置读取权限时使用内置提示词，不阻断生成。
  }
}

function resetStatus() {
  status.value = "idle";
  message.value = "";
  stopTimers();
}

function openCustomCoverModal() {
  dropdownVisible.value = false;
  customCoverModalVisible.value = true;
}

// 触发生成
async function handleGenerate() {
  if (status.value === 'generating') return;

  try {
    status.value = "generating";
    message.value = "正在处理...";
    elapsedTime.value = 0;
    imageUrl.value = "";

    // 开始计时
    elapsedTimer = window.setInterval(() => {
      elapsedTime.value++;
    }, 1000);

    const response = await axios.post(
      `/apis/coverimage.lik.cc/v1alpha1/generate/${encodeURIComponent(postName.value)}`,
      {
        type: currentTab.value,
        ...(currentTab.value === "randomImg" ? { randomType: randomType.value } : {}),
        ...(currentTab.value === "aiGenerated" ? {
          model: aiModelName.value,
          size: aiSize.value,
          style: aiStyle.value,
          prompt: aiPrompt.value,
        } : {}),
      },
    );

    if (response.data.status === "success") {
      handleSuccess(response.data.imageUrl);
    } else {
      throw new Error(response.data.message || "生成失败");
    }

  } catch (error: any) {
    status.value = "failed";
    message.value = error.response?.data?.message || error.message || "请求失败";
    stopTimers();
    Toast.error("生成失败: " + message.value);
  }
}

function handleSuccess(url: string) {
  status.value = "success";
  imageUrl.value = url;
  stopTimers();
  Toast.success("封面图设置成功！");
}

// 插入图片到编辑器
function insertToEditor() {
  if (!imageUrl.value || !props.editor) {
    Toast.warning("无法插入图片");
    return;
  }
  
  props.editor
    .chain()
    .focus()
    .insertContent({ type: "image", attrs: { src: imageUrl.value, alt: "封面图" } })
    .run();
  
  Toast.success("图片已插入到文章正文中");
  dropdownVisible.value = false;
  resetStatus();
}

async function handleCustomCoverConfirm(dataUrl: string) {
  if (!dataUrl || !postName.value) {
    Toast.warning("无法生成封面图");
    return;
  }

  customCoverModalVisible.value = false;
  status.value = "generating";
  message.value = "正在上传封面图...";
  imageUrl.value = "";

  try {
    const response = await axios.post("/apis/coverimage.lik.cc/v1alpha1/upload", {
      imageContent: dataUrl,
      postName: postName.value,
    });
    const coverUrl = typeof response.data === "string"
      ? response.data
      : response.data?.imageUrl;
    if (!coverUrl) {
      throw new Error("上传接口未返回图片地址");
    }
    dropdownVisible.value = true;
    handleSuccess(coverUrl);
  } catch (error: any) {
    dropdownVisible.value = true;
    status.value = "failed";
    message.value = error.response?.data || error.message || "上传失败";
    stopTimers();
    Toast.error("自定义封面上传失败: " + message.value);
  }
}

// 停止计时器
function stopTimers() {
  if (elapsedTimer) {
    clearInterval(elapsedTimer);
    elapsedTimer = null;
  }
}

onBeforeUnmount(stopTimers);
</script>

<style scoped>
/* AI 基座将模型选项挂载到 body，外层弹层需低于其选项层级。 */
:global(.v-popper__popper.cover-generator-popper) {
  z-index: 1000;
}

.cover-generator-toolbar-item {
  display: inline-block;
}

.toolbar-btn {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  border: none;
  background: transparent;
  cursor: pointer;
  width: 32px;
  height: 32px;
  padding: 6px;
  border-radius: 4px;
  color: #6b7280;
  transition: all 0.2s ease;
}

.toolbar-btn:hover:not(:disabled) {
  color: #374151;
  background: rgba(0, 0, 0, 0.05);
}

.toolbar-btn.active {
  color: var(--primary-color);
  background: rgba(var(--primary-color-rgb), 0.1);
}

.toolbar-btn:disabled {
  color: #9ca3af;
  cursor: not-allowed;
}

/* Dropdown Styles */
.cover-generator-dropdown {
  width: 580px;
  background: #fff;
  border-radius: 8px;
  box-shadow: 0 10px 25px -5px rgba(0, 0, 0, 0.1), 0 10px 10px -5px rgba(0, 0, 0, 0.04);
  overflow: hidden;
}

.content-area {
  min-height: 300px;
  max-height: 65vh;
  overflow-y: auto;
  padding-right: 4px;
}

/* 美化内部滚动条 */
.content-area::-webkit-scrollbar {
  width: 6px;
}

.content-area::-webkit-scrollbar-track {
  background: #f1f1f1;
  border-radius: 3px;
}

.content-area::-webkit-scrollbar-thumb {
  background: #c1c1c1;
  border-radius: 3px;
}

.content-area::-webkit-scrollbar-thumb:hover {
  background: #a1a1a1;
}

.info-box {
  background-color: #f9fafb;
  border: 1px solid #e5e7eb;
  border-radius: 8px;
  padding: 16px;
  text-align: center;
  display: flex;
  flex-direction: column;
  align-items: center;
  margin-bottom: 12px;
}

.preview-image img {
  max-width: 100%;
  max-height: 200px;
  border-radius: 4px;
  border: 1px solid #e5e7eb;
}

/* Tailwind-like utilities if not available globally */
.p-3 { padding: 0.75rem; }
.pt-4 { padding-top: 1rem; }
.mt-4 { margin-top: 1rem; }
.mt-3 { margin-top: 0.75rem; }
.mt-2 { margin-top: 0.5rem; }
.mt-1 { margin-top: 0.25rem; }
.mb-2 { margin-bottom: 0.5rem; }
.mb-3 { margin-bottom: 0.75rem; }
.mb-4 { margin-bottom: 1rem; }
.text-xs { font-size: 0.75rem; line-height: 1rem; }
.text-sm { font-size: 0.875rem; line-height: 1.25rem; }
.font-medium { font-weight: 500; }
.font-semibold { font-weight: 600; }
.flex { display: flex; }
.flex-col { flex-direction: column; }
.items-center { align-items: center; }
.justify-center { justify-content: center; }
.justify-end { justify-content: flex-end; }
.gap-2 { gap: 0.5rem; }
.space-x-6 > * + * { margin-left: 1.5rem; }
.space-y-1 > * + * { margin-top: 0.25rem; }
.space-y-2 > * + * { margin-top: 0.5rem; }
.border-t { border-top-width: 1px; }
.border { border-width: 1px; }
.border-gray-100 { border-color: #f3f4f6; }
.border-gray-200 { border-color: #e5e7eb; }
.bg-gradient-to-r { background-image: linear-gradient(to right, var(--tw-gradient-stops)); }
.from-gray-50 { --tw-gradient-from: #f9fafb; --tw-gradient-stops: var(--tw-gradient-from), var(--tw-gradient-to, rgba(249, 250, 251, 0)); }
.to-blue-50 { --tw-gradient-to: #eff6ff; }
.bg-purple-100 { background-color: #e9d5ff; }
.bg-blue-100 { background-color: #dbeafe; }
.bg-green-100 { background-color: #dcfce7; }
.rounded-lg { border-radius: 0.5rem; }
.rounded { border-radius: 0.25rem; }
.rounded-full { border-radius: 9999px; }
.px-2 { padding-left: 0.5rem; padding-right: 0.5rem; }
.py-1 { padding-top: 0.25rem; padding-bottom: 0.25rem; }
.text-gray-900 { color: #111827; }
.text-gray-700 { color: #374151; }
.text-gray-600 { color: #4b5563; }
.text-gray-500 { color: #6b7280; }
.text-gray-400 { color: #9ca3af; }
.text-green-500 { color: #22c55e; }
.text-green-600 { color: #16a34a; }
.text-green-700 { color: #15803d; }
.text-red-500 { color: #ef4444; }
.text-purple-500 { color: #a855f7; }
.text-purple-600 { color: #9333ea; }
.text-purple-700 { color: #7c3aed; }
.text-blue-500 { color: #3b82f6; }
.text-blue-600 { color: #2563eb; }
.text-blue-700 { color: #1d4ed8; }
.text-orange-400 { color: #fb923c; }
.text-orange-500 { color: #f97316; }
.text-cyan-600 { color: #0891b2; }
.text-cyan-700 { color: #0e7490; }
.bg-cyan-100 { background-color: #cffafe; }
.h-4 { height: 1rem; }
.w-4 { width: 1rem; }
.h-5 { height: 1.25rem; }
.w-5 { width: 1.25rem; }
.w-full { width: 100%; }
.cursor-pointer { cursor: pointer; }
.group:hover .group-hover\:text-gray-900 { color: #111827; }
.focus\:ring-blue-500:focus { --tw-ring-color: #3b82f6; }
.mr-3 { margin-right: 0.75rem; }
.ml-2 { margin-left: 0.5rem; }
</style>
