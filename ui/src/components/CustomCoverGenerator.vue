<template>
  <div class="cover-generator">
    <!-- 预览 -->
    <div class="preview-section">
      <canvas ref="canvasRef" width="1200" height="630" />
    </div>

    <!-- 配置区域 - 两列：左边文字+图标，右边背景 -->
    <div class="config-section">
      <!-- 左列：文字 + 图标 -->
      <div class="config-left">
        <!-- 文字设置 -->
        <div class="config-panel">
          <div class="panel-title"><IconType class="title-icon" />文字设置</div>
          <div class="form-row">
            <div class="form-item flex-1">
              <label class="form-label">左标题</label>
              <input type="text" v-model="config.titleLeft" placeholder="文本" maxlength="4" class="form-input" />
            </div>
            <div class="form-item flex-1">
              <label class="form-label">右标题</label>
              <input type="text" v-model="config.titleRight" placeholder="绘图" maxlength="4" class="form-input" />
            </div>
          </div>
          <div class="form-row">
            <div class="form-item flex-1">
              <label class="form-label">字号 {{ config.titleFontSize }}px</label>
              <input type="range" v-model.number="config.titleFontSize" min="100" max="240" step="10" class="range-input" />
            </div>
            <div class="form-item">
              <label class="form-label">粗细</label>
              <select v-model="config.titleFontWeight" class="form-select">
                <option value="400">常规</option>
                <option value="500">中等</option>
                <option value="600">半粗</option>
                <option value="700">粗体</option>
                <option value="800">特粗</option>
                <option value="900">黑体</option>
              </select>
            </div>
          </div>
          <div class="form-item">
            <label class="form-label">描述</label>
            <textarea v-model="config.description" placeholder="它是一个基于 JavaScript 的图表绘制工具" rows="2" class="form-input form-textarea"></textarea>
          </div>
        </div>

        <!-- 图标设置 -->
        <div class="config-panel">
          <div class="panel-title"><IconImage class="title-icon" />图标设置</div>
          <div class="form-row">
            <div class="form-item flex-1" :class="{ disabled: !!config.customIconUrl }" @click.stop @mousedown.stop @pointerdown.stop>
              <!-- @vue-ignore -->
              <FormKit type="iconify" v-model="config.iconSvg" label="图标" format="svg" :value-only="true" :disabled="!!config.customIconUrl" />
            </div>
            <div class="form-item flex-1" :class="{ disabled: !!config.iconSvg }" @click.stop @mousedown.stop @pointerdown.stop>
              <!-- @vue-ignore -->
              <FormKit type="attachment" v-model="config.customIconUrl" label="或上传图片" :accepts="['image/*']" :disabled="!!config.iconSvg" />
            </div>
          </div>
          <div class="form-row align-end">
            <div class="form-item" @click.stop @mousedown.stop @pointerdown.stop>
              <FormKit type="color" v-model="config.iconBgColor" label="背景色" />
            </div>
            <div class="form-item flex-1">
              <label class="form-label">圆角 {{ config.iconRadius }}px</label>
              <input type="range" v-model.number="config.iconRadius" min="0" max="100" class="range-input" />
            </div>
            <button type="button" class="clear-btn" @click="clearIcon">清除</button>
          </div>
        </div>
      </div>

      <!-- 右列：背景设置 -->
      <div class="config-panel config-right">
        <div class="panel-title"><IconPalette class="title-icon" />背景设置</div>
        <div class="bg-custom">
          <div class="form-row align-end">
            <div class="form-item" @click.stop @mousedown.stop @pointerdown.stop>
              <FormKit type="color" v-model="config.customColor1" label="颜色1" />
            </div>
            <div class="form-item" @click.stop @mousedown.stop @pointerdown.stop>
              <FormKit type="color" v-model="config.customColor2" label="颜色2" />
            </div>
            <div class="form-item">
              <label class="form-label">类型</label>
              <select v-model="config.gradientType" class="form-select">
                <option value="linear">线性</option>
                <option value="radial">径向</option>
              </select>
            </div>
            <button type="button" class="apply-btn" :class="{ active: config.bgPresetId === -1 }" @click="config.bgPresetId = -1">应用自定义</button>
          </div>
        </div>
        <div class="form-item">
          <label class="form-label">预设背景</label>
          <div class="preset-grid">
            <button
              v-for="bg in bgPresets"
              :key="bg.id"
              type="button"
              class="preset-item"
              :class="{ active: config.bgPresetId === bg.id }"
              :style="{ backgroundImage: bg.css }"
              :title="bg.name"
              @click="config.bgPresetId = bg.id"
            />
          </div>
        </div>
      </div>
    </div>

    <!-- 操作按钮 -->
    <div class="action-bar">
      <VButton @click="downloadImage"><template #icon><IconDownload /></template>下载图片</VButton>
      <VButton type="primary" @click="handleConfirm"><template #icon><IconCheck /></template>确认使用</VButton>
    </div>
  </div>
</template>

<script lang="ts" setup>
import { ref, reactive, watch, onMounted, nextTick, computed } from 'vue'
import { VButton, Toast } from '@halo-dev/components'
import { FormKit } from '@formkit/vue'
import IconType from '~icons/lucide/type'
import IconImage from '~icons/lucide/image'
import IconPalette from '~icons/lucide/palette'
import IconDownload from '~icons/lucide/download'
import IconCheck from '~icons/lucide/check'
import { bgPresets as bgData, type BgPreset } from '@/data/backgrounds'

const emit = defineEmits<{ (e: 'confirm', dataUrl: string): void }>()

const canvasRef = ref<HTMLCanvasElement | null>(null)
const loadedIcon = ref<HTMLImageElement | null>(null)
const bgPresets = ref<BgPreset[]>(bgData)

const config = reactive({
  titleLeft: '文本',
  titleRight: '绘图',
  titleFontSize: 180,
  titleFontWeight: '900',
  description: '它是一个基于 JAVASCRIPT 的图表绘制工具',
  iconSvg: '' as string,
  customIconUrl: '',
  iconBgColor: '#FF1493',
  iconRadius: 40,
  bgPresetId: 28, // 默认紫色的盘子，-1 表示自定义
  customColor1: '#667eea',
  customColor2: '#764ba2',
  gradientType: 'linear' as 'linear' | 'radial',
})

// 当前选中的背景预设
const currentBgPreset = computed(() => bgPresets.value.find(p => p.id === config.bgPresetId))

// 加载字体
const fontLoaded = ref(false)
async function loadFont() {
  if (fontLoaded.value) return
  // 检查是否已经加载过
  const existingLink = document.querySelector('link[href*="tengxiang.css"]')
  if (!existingLink) {
    const link = document.createElement('link')
    link.rel = 'stylesheet'
    link.href = '/plugins/coverImage/assets/static/fonts/tengxiang/tengxiang.css'
    document.head.appendChild(link)
  }
  // 等待字体加载
  try {
    await document.fonts.ready
    await new Promise(resolve => setTimeout(resolve, 100))
  } catch (e) {
    console.warn('Font loading warning:', e)
  }
  fontLoaded.value = true
  renderCanvas()
}

// 清除图标
function clearIcon() {
  config.iconSvg = ''
  config.customIconUrl = ''
  loadedIcon.value = null
  renderCanvas()
}

function getFontFamily(): string {
  return '"default_font_family", "PingFang SC", "Microsoft YaHei", sans-serif'
}

function roundRect(ctx: CanvasRenderingContext2D, x: number, y: number, w: number, h: number, r: number) {
  ctx.beginPath()
  ctx.moveTo(x + r, y); ctx.lineTo(x + w - r, y); ctx.quadraticCurveTo(x + w, y, x + w, y + r)
  ctx.lineTo(x + w, y + h - r); ctx.quadraticCurveTo(x + w, y + h, x + w - r, y + h)
  ctx.lineTo(x + r, y + h); ctx.quadraticCurveTo(x, y + h, x, y + h - r)
  ctx.lineTo(x, y + r); ctx.quadraticCurveTo(x, y, x + r, y)
  ctx.closePath()
}

function draw3DText(ctx: CanvasRenderingContext2D, text: string, x: number, y: number, fontSize: number, fontWeight: string, align: CanvasTextAlign) {
  ctx.font = `${fontWeight} ${fontSize}px ${getFontFamily()}`
  ctx.textAlign = align
  ctx.textBaseline = 'middle'
  ;[{ o: 8, c: 'rgba(0,0,0,0.1)' }, { o: 4, c: 'rgba(0,0,0,0.15)' }, { o: 2, c: 'rgba(0,0,0,0.2)' }].forEach(l => {
    ctx.fillStyle = l.c; ctx.fillText(text, x + l.o, y + l.o)
  })
  ctx.fillStyle = '#fff'; ctx.shadowColor = 'rgba(0,0,0,0.3)'; ctx.shadowBlur = 10; ctx.shadowOffsetX = 4; ctx.shadowOffsetY = 4
  ctx.fillText(text, x, y)
  ctx.shadowColor = 'transparent'; ctx.shadowBlur = 0; ctx.shadowOffsetX = 0; ctx.shadowOffsetY = 0
}

// 解析 CSS 渐变
function parseGradient(css: string, W: number, H: number): { type: 'linear' | 'radial', colors: { offset: number, color: string }[], angle: number } {
  const colors: { offset: number, color: string }[] = []
  let type: 'linear' | 'radial' = 'linear'
  let angle = 135

  const linearMatch = css.match(/linear-gradient\s*\(([^)]+)\)/i)
  const radialMatch = css.match(/radial-gradient\s*\(([^)]+)\)/i)

  let content = ''
  if (linearMatch) {
    type = 'linear'
    content = linearMatch[1]
  } else if (radialMatch) {
    type = 'radial'
    content = radialMatch[1]
  } else {
    const bgColorMatch = css.match(/#[a-fA-F0-9]{3,8}/i)
    const c = bgColorMatch ? bgColorMatch[0] : '#667eea'
    return { type: 'linear', colors: [{ offset: 0, color: c }, { offset: 1, color: c }], angle: 0 }
  }

  // 解析角度
  const angleMatch = content.match(/^(-?\d+)deg/)
  const toMatch = content.match(/^to\s+(top|bottom|left|right|top\s+left|top\s+right|bottom\s+left|bottom\s+right)/i)
  
  if (angleMatch) {
    angle = parseInt(angleMatch[1])
  } else if (toMatch) {
    const dir = toMatch[1].toLowerCase().replace(/\s+/g, ' ')
    const dirMap: Record<string, number> = {
      'top': 0, 'right': 90, 'bottom': 180, 'left': 270,
      'top right': 45, 'bottom right': 135, 'bottom left': 225, 'top left': 315
    }
    angle = dirMap[dir] ?? 135
  }

  // 提取颜色
  const colorRegex = /(#[a-fA-F0-9]{3,8}|rgba?\([^)]+\))\s*(\d+%)?/gi
  let match
  const stops: { color: string, offset: number | null }[] = []
  
  while ((match = colorRegex.exec(content)) !== null) {
    const color = match[1]
    const offset = match[2] ? parseInt(match[2]) / 100 : null
    stops.push({ color, offset })
  }

  if (stops.length > 0) {
    stops.forEach((stop, i) => {
      const off = stop.offset ?? i / Math.max(stops.length - 1, 1)
      colors.push({ offset: off, color: stop.color })
    })
  }

  if (colors.length === 0) {
    colors.push({ offset: 0, color: '#667eea' }, { offset: 1, color: '#764ba2' })
  }

  return { type, colors, angle }
}

function renderCanvas() {
  const canvas = canvasRef.value; if (!canvas) return
  const ctx = canvas.getContext('2d'); if (!ctx) return
  const W = 1200, H = 630
  ctx.clearRect(0, 0, W, H)

  // 绘制背景
  let gradient: CanvasGradient
  
  if (config.bgPresetId === -1) {
    // 自定义渐变
    if (config.gradientType === 'linear') {
      const a = (135 * Math.PI) / 180
      gradient = ctx.createLinearGradient(
        W/2 - Math.cos(a) * W, H/2 - Math.sin(a) * H,
        W/2 + Math.cos(a) * W, H/2 + Math.sin(a) * H
      )
    } else {
      gradient = ctx.createRadialGradient(W/2, H/2, 0, W/2, H/2, Math.max(W, H) / 2)
    }
    gradient.addColorStop(0, config.customColor1)
    gradient.addColorStop(1, config.customColor2)
    ctx.fillStyle = gradient
    ctx.fillRect(0, 0, W, H)
  } else {
    const preset = currentBgPreset.value
    if (preset) {
      const parsed = parseGradient(preset.css, W, H)
      
      if (parsed.type === 'linear') {
        const a = (parsed.angle * Math.PI) / 180
        gradient = ctx.createLinearGradient(
          W/2 - Math.cos(a) * W, H/2 - Math.sin(a) * H,
          W/2 + Math.cos(a) * W, H/2 + Math.sin(a) * H
        )
      } else {
        gradient = ctx.createRadialGradient(W/2, H/2, 0, W/2, H/2, Math.max(W, H) / 2)
      }
      
      parsed.colors.forEach(c => gradient.addColorStop(c.offset, c.color))
      ctx.fillStyle = gradient
      ctx.fillRect(0, 0, W, H)
    }
  }

  if (config.titleLeft) draw3DText(ctx, config.titleLeft, 470, 280, config.titleFontSize, config.titleFontWeight, 'right')
  if (config.titleRight) draw3DText(ctx, config.titleRight, 730, 280, config.titleFontSize, config.titleFontWeight, 'left')

  const iconSize = 200, iconX = (W - iconSize) / 2, iconY = 175
  
  if (loadedIcon.value) {
    // 有图片时：背景色填充整个区域，图片铺满（无边距）
    ctx.save()
    ctx.shadowColor = 'rgba(0,0,0,0.4)'; ctx.shadowBlur = 30; ctx.shadowOffsetY = 10
    ctx.fillStyle = config.iconBgColor
    roundRect(ctx, iconX, iconY, iconSize, iconSize, config.iconRadius)
    ctx.fill()
    ctx.restore()
    
    // 图片铺满整个图标区域
    ctx.save()
    roundRect(ctx, iconX, iconY, iconSize, iconSize, config.iconRadius)
    ctx.clip()
    const img = loadedIcon.value
    const sc = Math.max(iconSize / img.width, iconSize / img.height)
    const sw = img.width * sc, sh = img.height * sc
    ctx.drawImage(img, 0, 0, img.width, img.height, iconX + (iconSize - sw) / 2, iconY + (iconSize - sh) / 2, sw, sh)
    ctx.restore()
  } else {
    // 无图片时：只显示背景色
    ctx.save()
    ctx.shadowColor = 'rgba(0,0,0,0.4)'; ctx.shadowBlur = 30; ctx.shadowOffsetY = 10
    ctx.fillStyle = config.iconBgColor
    roundRect(ctx, iconX, iconY, iconSize, iconSize, config.iconRadius)
    ctx.fill()
    ctx.restore()
  }

  if (config.description) {
    ctx.font = `500 26px ${getFontFamily()}`; ctx.textAlign = 'center'; ctx.textBaseline = 'middle'
    ctx.fillStyle = 'rgba(255,255,255,0.85)'; ctx.fillText(config.description, W / 2, 480)
  }
}

function downloadImage() {
  const canvas = canvasRef.value; if (!canvas) return
  const link = document.createElement('a'); link.download = `cover-${Date.now()}.png`; link.href = canvas.toDataURL('image/png'); link.click()
  Toast.success('图片已下载')
}

function handleConfirm() {
  const canvas = canvasRef.value; if (!canvas) return
  emit('confirm', canvas.toDataURL('image/png'))
}

watch(config, () => renderCanvas(), { deep: true })
watch(() => config.iconSvg, svg => {
  if (svg) {
    // 选择图标时清除附件
    config.customIconUrl = ''
    let s = svg.replace(/currentColor/gi, '#fff')
    if (!s.includes('width=')) s = s.replace('<svg', '<svg width="200" height="200"')
    const blob = new Blob([s], { type: 'image/svg+xml' }), url = URL.createObjectURL(blob)
    const img = new Image()
    img.onload = () => { loadedIcon.value = img; URL.revokeObjectURL(url); renderCanvas() }
    img.onerror = () => URL.revokeObjectURL(url)
    img.src = url
  } else if (!config.customIconUrl) {
    loadedIcon.value = null
    renderCanvas()
  }
})
watch(() => config.customIconUrl, url => {
  if (url) {
    // 选择附件时清除图标
    config.iconSvg = ''
    const img = new Image()
    img.crossOrigin = 'anonymous'
    img.onload = () => { loadedIcon.value = img; renderCanvas() }
    img.onerror = () => { loadedIcon.value = null; renderCanvas() }
    img.src = url
  } else if (!config.iconSvg) {
    loadedIcon.value = null
    renderCanvas()
  }
})

onMounted(async () => {
  await nextTick()
  loadFont()
  renderCanvas()
})
</script>

<style scoped>
.cover-generator {
  display: flex;
  flex-direction: column;
  gap: 16px;
}

/* 预览区 */
.preview-section {
  background: #f3f4f6;
  border-radius: 8px;
  padding: 16px;
  display: flex;
  justify-content: center;
}
.preview-section canvas {
  max-width: 100%;
  width: 500px;
  height: auto;
  border-radius: 6px;
  box-shadow: 0 4px 12px rgba(0,0,0,0.1);
}

/* 配置区 - 两列布局 4:6 */
.config-section {
  display: grid;
  grid-template-columns: 4fr 6fr;
  gap: 12px;
  align-items: start;
}

.config-left {
  display: flex;
  flex-direction: column;
  gap: 12px;
}

.config-right {
  display: flex;
  flex-direction: column;
}

.config-panel {
  background: #fff;
  border: 1px solid #e5e7eb;
  border-radius: 8px;
  padding: 12px;
  display: flex;
  flex-direction: column;
  gap: 12px;
}

.panel-title {
  display: flex;
  align-items: center;
  gap: 6px;
  font-size: 13px;
  font-weight: 600;
  color: #374151;
  padding-bottom: 8px;
  border-bottom: 1px solid #f3f4f6;
}
.title-icon {
  width: 16px;
  height: 16px;
  color: #6b7280;
}

/* 表单元素 */
.form-row {
  display: flex;
  gap: 10px;
}
.form-row.align-end {
  align-items: flex-end;
}
.form-row :deep(.formkit-outer) {
  margin: 0 !important;
}
.form-item {
  display: flex;
  flex-direction: column;
  gap: 4px;
}
.form-item.flex-1 {
  flex: 1;
}
.form-label {
  font-size: 12px;
  color: #6b7280;
}
.form-input {
  padding: 6px 10px;
  border: 1px solid #d1d5db;
  border-radius: 4px;
  font-size: 13px;
  color: #374151;
  background: #fff;
}
.form-input:focus {
  outline: none;
  border-color: #3b82f6;
  box-shadow: 0 0 0 2px rgba(59,130,246,0.1);
}
.form-textarea {
  resize: none;
  font-family: inherit;
}

/* 滑块 */
.range-input {
  width: 100%;
  height: 4px;
  appearance: none;
  background: #e5e7eb;
  border-radius: 2px;
  margin-top: 8px;
}
.range-input::-webkit-slider-thumb {
  appearance: none;
  width: 14px;
  height: 14px;
  background: #3b82f6;
  border-radius: 50%;
  cursor: pointer;
}

/* 按钮组 */
.btn-group {
  display: flex;
  gap: 4px;
}
.small-btn {
  padding: 6px 10px;
  font-size: 12px;
  background: #fff;
  border: 1px solid #d1d5db;
  border-radius: 4px;
  cursor: pointer;
  color: #374151;
  white-space: nowrap;
}
.small-btn:hover {
  background: #f9fafb;
}
.small-btn.danger {
  color: #dc2626;
  border-color: #fca5a5;
}

/* 应用按钮 */
.apply-btn {
  padding: 0 14px;
  font-size: 12px;
  background: #f3f4f6;
  border: 1px solid #d1d5db;
  border-radius: 4px;
  cursor: pointer;
  color: #374151;
  white-space: nowrap;
  height: 36px;
  flex-shrink: 0;
}
.apply-btn:hover {
  background: #e5e7eb;
}
.apply-btn.active {
  background: #3b82f6;
  border-color: #3b82f6;
  color: #fff;
}

/* 清除按钮 */
.clear-btn {
  padding: 0 10px;
  font-size: 12px;
  background: #fff;
  border: 1px solid #fca5a5;
  border-radius: 4px;
  cursor: pointer;
  color: #dc2626;
  white-space: nowrap;
  height: 36px;
  flex-shrink: 0;
}
.clear-btn:hover {
  background: #fef2f2;
}

/* 禁用状态 */
.form-item.disabled {
  opacity: 0.5;
  pointer-events: none;
}

/* 下拉选择框 */
.form-select {
  padding: 0 28px 0 10px;
  border: 1px solid #d1d5db;
  border-radius: 4px;
  font-size: 13px;
  color: #374151;
  background: #fff url("data:image/svg+xml,%3csvg xmlns='http://www.w3.org/2000/svg' fill='none' viewBox='0 0 20 20'%3e%3cpath stroke='%236b7280' stroke-linecap='round' stroke-linejoin='round' stroke-width='1.5' d='m6 8 4 4 4-4'/%3e%3c/svg%3e") right 6px center no-repeat;
  background-size: 16px;
  cursor: pointer;
  height: 36px;
  appearance: none;
  min-width: 80px;
}
.form-select:focus {
  outline: none;
  border-color: #3b82f6;
}

/* FormKit 样式覆盖 */
.config-panel :deep(.formkit-outer) {
  margin: 0 !important;
}
.config-panel :deep(.formkit-label) {
  font-size: 12px;
  color: #6b7280;
  margin-bottom: 4px;
}
/* 统一 FormKit 输入框高度 */
.config-panel :deep(.formkit-input) {
  height: 36px !important;
}
.bg-custom :deep(.formkit-outer),
.form-row.align-end :deep(.formkit-outer) {
  margin: 0 !important;
}

/* 背景自定义区 */
.bg-custom {
  padding-bottom: 12px;
  border-bottom: 1px solid #f3f4f6;
  margin-bottom: 12px;
}
.bg-custom .form-row {
  align-items: flex-end;
}

/* 预设网格 */
.preset-grid {
  display: grid;
  grid-template-columns: repeat(6, 1fr);
  gap: 8px;
  max-height: 180px;
  overflow-y: auto;
}
.preset-grid::-webkit-scrollbar {
  width: 4px;
}
.preset-grid::-webkit-scrollbar-thumb {
  background: #d1d5db;
  border-radius: 2px;
}
.preset-item {
  aspect-ratio: 16/9;
  border-radius: 4px;
  border: 2px solid transparent;
  cursor: pointer;
  transition: all 0.15s;
}
.preset-item:hover {
  transform: scale(1.05);
}
.preset-item.active {
  border-color: #3b82f6;
  box-shadow: 0 0 0 2px rgba(59,130,246,0.2);
}

/* 操作栏 */
.action-bar {
  display: flex;
  justify-content: flex-end;
  gap: 12px;
  padding-top: 12px;
  border-top: 1px solid #e5e7eb;
}
</style>
