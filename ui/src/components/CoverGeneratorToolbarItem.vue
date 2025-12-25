<template>
  <div class="cover-generator-toolbar-item">
    <VDropdown
      v-model:visible="dropdownVisible"
      :disabled="disabled"
      :triggers="['click']"
      :auto-close="false"
      :close-on-content-click="false"
      @update:visible="handleOpenDropdown"
    >
      <button
        v-tooltip="'AI 封面生成'"
        class="toolbar-btn"
        :class="{ active: isActive || dropdownVisible }"
        :disabled="disabled"
        @click="toggleDropdown"
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
                <div class="flex gap-2">
                  <VButton size="sm" type="primary" @click="setCoverToPost">
                    设置为封面
                  </VButton>
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
                  <p class="text-xs text-gray-500 mt-1">根据文章内容自动提取关键词并生成匹配的封面图。</p>
                </div>
                
                  <div class="mb-4 text-left">
                    <label class="text-sm font-medium text-gray-700 block mb-2">模型选择</label>
                    <select 
                      v-model="aiModel"
                      class="halo-select w-full"
                    >
                      <option 
                        v-for="option in modelOptions" 
                        :key="option.value" 
                        :value="option.value"
                      >
                        {{ option.label }}
                      </option>
                    </select>
                    
                    <!-- 模型详细信息 -->
                    <div class="mt-3 p-3 bg-gradient-to-r from-gray-50 to-blue-50 rounded-lg border border-gray-200">
                      <div v-if="aiModel === 'doubao-seedream-4.5'" class="space-y-2">
                        <div class="font-semibold text-purple-700 flex items-center gap-2">
                          🚀 Seedream 4.5 (最新多模态)
                          <span class="text-xs bg-purple-100 text-purple-600 px-2 py-1 rounded-full">NEW</span>
                        </div>
                        <div class="text-sm text-gray-600 space-y-1">
                          <div>• 字节跳动最新图像多模态模型</div>
                          <div>• 整合文生图、图生图、组图输出能力</div>
                          <div>• 更好的编辑一致性和多图融合效果</div>
                          <div>• 小字、小人脸生成更自然</div>
                        </div>
                        <div class="text-sm font-medium text-blue-700 bg-blue-100 px-2 py-1 rounded">
                          支持分辨率：2560×1440 - 4096×4096
                        </div>
                      </div>
                      
                      <div v-else-if="aiModel === 'doubao-seedream-3.0'" class="space-y-2">
                        <div class="font-semibold text-blue-700 flex items-center gap-2">
                          ⭐ Seedream 3.0 (原生高分辨率)
                          <span class="text-xs bg-blue-100 text-blue-600 px-2 py-1 rounded-full">2K</span>
                        </div>
                        <div class="text-sm text-gray-600 space-y-1">
                          <div>• 支持原生2K分辨率输出</div>
                          <div>• 综合能力媲美GPT-4o，世界第一梯队</div>
                          <div>• 响应速度更快，小字生成更准确</div>
                          <div>• 指令遵循能力强，美感&结构提升</div>
                        </div>
                        <div class="text-sm font-medium text-blue-700 bg-blue-100 px-2 py-1 rounded">
                          支持分辨率：512×512 - 2048×2048
                        </div>
                      </div>
                      
                      <div v-else-if="aiModel === 'doubao-seedream-4.0'" class="space-y-2">
                        <div class="font-semibold text-green-700 flex items-center gap-2">
                          💰 Seedream 4.0 (性价比之选)
                          <span class="text-xs bg-green-100 text-green-600 px-2 py-1 rounded-full">推荐</span>
                        </div>
                        <div class="text-sm text-gray-600 space-y-1">
                          <div>• 价格最优惠，仅¥0.20/次</div>
                          <div>• 平衡性能与成本的理想选择</div>
                          <div>• 适合批量生成和日常使用</div>
                        </div>
                        <div class="text-sm font-medium text-green-700 bg-green-100 px-2 py-1 rounded">
                          支持分辨率：1280×720 - 2048×2048
                        </div>
                      </div>
                      
                      <div v-else-if="aiModel.startsWith('gemini')" class="space-y-2">
                        <div v-if="aiModel === 'gemini-3-pro-image-preview'" class="space-y-2">
                          <div class="font-semibold text-cyan-700 flex items-center gap-2">
                            🌟 Gemini 3 Pro Image (最先进)
                            <span class="text-xs bg-cyan-100 text-cyan-600 px-2 py-1 rounded-full">2K</span>
                          </div>
                          <div class="text-sm text-gray-600 space-y-1">
                            <div>• 谷歌最先进的图像生成和编辑模型</div>
                            <div>• 基于 Gemini 3 Pro 构建</div>
                            <div>• 多模态推理、高保真视觉合成</div>
                            <div>• 支持搜索基础整合实时信息</div>
                          </div>
                          <div class="text-sm font-medium text-cyan-700 bg-cyan-100 px-2 py-1 rounded">
                            支持比例：1:1, 16:9, 4:3, 3:2, 9:16, 21:9 | 超高清 2K
                          </div>
                        </div>
                        <div v-else class="space-y-2">
                          <div class="font-semibold text-cyan-700 flex items-center gap-2">
                            ⚡ Gemini 2.5 Flash Image (上下文理解)
                            <span class="text-xs bg-cyan-100 text-cyan-600 px-2 py-1 rounded-full">快速</span>
                          </div>
                          <div class="text-sm text-gray-600 space-y-1">
                            <div>• 最先进的图像生成模型</div>
                            <div>• 具有上下文理解功能</div>
                            <div>• 支持图像编辑和多轮对话</div>
                            <div>• 响应速度快</div>
                          </div>
                          <div class="text-sm font-medium text-cyan-700 bg-cyan-100 px-2 py-1 rounded">
                            支持比例：1:1, 16:9, 4:3, 3:2, 9:16, 21:9
                          </div>
                        </div>
                      </div>
                    </div>
                  </div>
                  
                  <div class="mb-4 text-left">
                    <label class="text-sm font-medium text-gray-700 block mb-2">
                      图片尺寸 
                      <span class="text-gray-500 font-normal text-xs">(像素越高质量越好，但生成时间更长)</span>
                    </label>
                    <select 
                      v-model="aiSize"
                      class="halo-select w-full"
                    >
                      <option 
                        v-for="option in sizeOptions" 
                        :key="option.value" 
                        :value="option.value"
                      >
                        {{ option.label }}
                      </option>
                    </select>
                  </div>

                  <div class="mb-4 text-left">
                    <label class="text-sm font-medium text-gray-700 block mb-2">图片风格</label>
                    <select 
                      v-model="aiStyle"
                      class="halo-select w-full"
                    >
                      <option 
                        v-for="option in styleOptions" 
                        :key="option.value" 
                        :value="option.value"
                      >
                        {{ option.label }}
                      </option>
                    </select>
                  </div>

                  <div v-if="!aiModel.startsWith('gemini')" class="mb-4 text-left">
                    <div class="flex items-center justify-between">
                      <div>
                        <label class="text-sm font-medium text-gray-700">水印设置</label>
                        <p class="text-xs text-gray-500 mt-1">关闭水印可获得更清洁的图片</p>
                      </div>
                      <VSwitch v-model="watermark" />
                    </div>
                  </div>
              </div>

              <!-- 随机图 -->
              <div v-else-if="currentTab === 'randomImg'" class="tab-pane">
                <div class="info-box">
                  <IconImage class="h-5 w-5 text-blue-500 mb-2" />
                  <h4 class="font-medium text-gray-900">随机图片</h4>
                  <p class="text-xs text-gray-500 mt-1">从配置的图库源（如 Unsplash, Bing 等）随机获取一张高质量图片。</p>
                </div>
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
              <div class="mt-4 flex justify-end pt-4 border-t border-gray-100">
                <VButton 
                  type="primary" 
                  size="sm"
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
  </div>
</template>

<script lang="ts" setup>
import { ref, computed, watch } from "vue";
import type { Editor } from "@tiptap/core";
import { 
  VButton, 
  VDropdown, 
  VTabbar,
  VAlert, 
  Toast,
  VLoading,
  VSpace,
  VSwitch
} from "@halo-dev/components";
import axios from "axios";

// Icons
import IconPicAi from "~icons/mingcute/pic-ai-line";
import IconSparkles from "~icons/lucide/sparkles";
import IconImage from "~icons/lucide/image";
import IconCamera from "~icons/lucide/camera";
import IconCheckCircle from "~icons/lucide/check-circle";
import IconAlertCircle from "~icons/lucide/alert-circle";
import IconWand2 from "~icons/lucide/wand-2";
import IconRefresh from "~icons/lucide/refresh-cw";

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

// Tabs
type TabType = "aiGenerated" | "randomImg" | "firstPostImg";
const currentTab = ref<TabType>("aiGenerated");
const tabItems = [
  { label: "AI 生成", id: "aiGenerated" },
  { label: "随机图", id: "randomImg" },
  { label: "文章首图", id: "firstPostImg" },
];

// 状态
const status = ref<"idle" | "generating" | "success" | "failed">("idle");
const message = ref("");
const imageUrl = ref("");
const elapsedTime = ref(0);
const postName = ref("");
const aiModel = ref("doubao-seedream-4.5");
const aiSize = ref("2560x1440");
const aiStyle = ref("默认");
const watermark = ref(false);

// 监听模型变化，自动切换默认尺寸
watch(aiModel, (newVal) => {
  if (newVal === "doubao-seedream-4.5") {
    aiSize.value = "2560x1440";
  } else if (newVal === "doubao-seedream-3.0") {
    aiSize.value = "2048x2048";
  } else if (newVal === "doubao-seedream-4.0") {
    aiSize.value = "2048x2048";
  } else if (newVal === "gemini-3-pro-image-preview") {
    aiSize.value = "16:9-2K";
  } else if (newVal.startsWith("gemini")) {
    aiSize.value = "16:9";
  } else {
    aiSize.value = "2048x2048";
  }
});

// 选项数据
const modelOptions = computed(() => [
  { 
    label: "Seedream 4.5 - ¥0.25/次 (最新多模态)", 
    value: "doubao-seedream-4.5" 
  },
  { 
    label: "Seedream 3.0 - ¥0.26/次 (原生2K高分辨率)", 
    value: "doubao-seedream-3.0" 
  },
  { 
    label: "Seedream 4.0 - ¥0.20/次 (性价比推荐)", 
    value: "doubao-seedream-4.0" 
  },
  { 
    label: "Gemini 3 Pro Image - 付费 (最先进图像生成)", 
    value: "gemini-3-pro-image-preview" 
  },
  { 
    label: "Gemini 2.5 Flash Image - 付费 (上下文理解)", 
    value: "gemini-2.5-flash-image" 
  }
]);

const sizeOptions = computed(() => {
  if (aiModel.value === "doubao-seedream-4.5") {
    return [
      { label: "2560×1440 (16:9 2K推荐)", value: "2560x1440" },
      { label: "4096×4096 (1:1 超高清)", value: "4096x4096" },
      { label: "2048×2048 (1:1 标准)", value: "2048x2048" },
      { label: "3840×2160 (4K UHD)", value: "3840x2160" }
    ];
  } else if (aiModel.value === "doubao-seedream-3.0") {
    return [
      { label: "2048×2048 (原生2K推荐)", value: "2048x2048" },
      { label: "1920×1920 (1:1 高清)", value: "1920x1920" },
      { label: "2048×1152 (16:9)", value: "2048x1152" },
      { label: "1536×1536 (1:1 中等)", value: "1536x1536" }
    ];
  } else if (aiModel.value.startsWith("gemini")) {
    // Gemini 使用比例格式
    const baseOptions = [
      { label: "16:9 (横屏推荐)", value: "16:9" },
      { label: "1:1 (正方形)", value: "1:1" },
      { label: "4:3 (传统比例)", value: "4:3" },
      { label: "3:2 (摄影比例)", value: "3:2" },
      { label: "9:16 (竖屏)", value: "9:16" },
      { label: "21:9 (超宽屏)", value: "21:9" }
    ];
    // Gemini 3 Pro 支持 2K
    if (aiModel.value === "gemini-3-pro-image-preview") {
      return [
        { label: "16:9 2K (横屏超高清)", value: "16:9-2K" },
        ...baseOptions
      ];
    }
    return baseOptions;
  } else {
    return [
      { label: "2048×2048 (推荐)", value: "2048x2048" },
      { label: "1920×1920 (1:1 标准)", value: "1920x1920" },
      { label: "2048×1152 (16:9)", value: "2048x1152" },
      { label: "1920×1080 (Full HD)", value: "1920x1080" }
    ];
  }
});

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
}

function toggleDropdown() {
  if (dropdownVisible.value) {
    dropdownVisible.value = false;
  } else {
    handleOpenDropdown(true);
  }
}

function resetStatus() {
  status.value = "idle";
  message.value = "";
  stopTimers();
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

    // 调用生成 API，带上 type 参数
    let url = `/apis/coverimage.lik.cc/v1alpha1/generate/${postName.value}?type=${currentTab.value}`;
    if (currentTab.value === 'aiGenerated') {
      url += `&model=${aiModel.value}&size=${aiSize.value}&style=${aiStyle.value}&watermark=${watermark.value}`;
    }
    
    // 直接等待响应（后端现在是同步返回结果）
    const response = await axios.post(url);

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
    .insertContent(`<img src="${imageUrl.value}" alt="封面图" />`)
    .run();
  
  Toast.success("图片已插入到文章正文中");
  dropdownVisible.value = false;
  resetStatus();
}

// 关闭并提示刷新
function closeAndRefresh() {
  dropdownVisible.value = false;
  resetStatus();
  Toast.success("封面图已设置，刷新页面后可在文章设置中查看");
}

// 设置封面图（上传到附件并设置到文章，然后刷新页面）
async function setCoverToPost() {
  if (!imageUrl.value || !postName.value) {
    Toast.warning("无法设置封面图");
    return;
  }

  try {
    // 显示上传中状态，但不改变整体状态，保留图片预览
    Toast.info("正在上传封面图...");

    // 调用上传接口
    const response = await axios.post('/apis/coverimage.lik.cc/v1alpha1/upload', {
      imageContent: imageUrl.value,
      postName: postName.value
    });

    Toast.success("封面图已设置，正在刷新页面...");
    setTimeout(() => {
      window.location.reload();
    }, 500);
  } catch (error: any) {
    console.error('设置封面图失败:', error);
    // 上传失败时不重置状态，保留图片预览，让用户可以重试
    Toast.error("设置封面图失败: " + (error.response?.data || error.message) + "，请重试");
  }
}

// 停止计时器
function stopTimers() {
  if (elapsedTimer) {
    clearInterval(elapsedTimer);
    elapsedTimer = null;
  }
}
</script>

<style scoped>
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

/* Halo Select Styles */
.halo-select {
  appearance: none;
  background-color: #fff;
  background-image: url("data:image/svg+xml,%3csvg xmlns='http://www.w3.org/2000/svg' fill='none' viewBox='0 0 20 20'%3e%3cpath stroke='%236b7280' stroke-linecap='round' stroke-linejoin='round' stroke-width='1.5' d='m6 8 4 4 4-4'/%3e%3c/svg%3e");
  background-position: right 0.5rem center;
  background-repeat: no-repeat;
  background-size: 1.5em 1.5em;
  border: 1px solid #d1d5db;
  border-radius: 0.375rem;
  color: #374151;
  font-size: 0.875rem;
  line-height: 1.5rem;
  padding: 0.5rem 2.5rem 0.5rem 0.75rem;
  transition: border-color 0.15s ease-in-out, box-shadow 0.15s ease-in-out;
}

.halo-select:focus {
  border-color: #3b82f6;
  box-shadow: 0 0 0 3px rgba(59, 130, 246, 0.1);
  outline: none;
}

.halo-select:hover {
  border-color: #9ca3af;
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
