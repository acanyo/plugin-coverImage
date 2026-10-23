# plugin-coverImage

Halo 文章封面生成插件，支持 AI 图像生成与提示词优化、可视化封面设计、随机图库和文章首图。

当前版本 **1.2.0**，需要 **Halo 2.27.0+**，并安装启用 **Halo AI Foundation 1.x**。
从 1.1.x 升级时，请将原来直接配置的模型供应商、API 地址和密钥迁移到 AI Foundation；旧的 CodeSphere / 火山方舟配置不再读取。

## 功能特性

支持以下封面图来源：

- 文章首图
- 随机图（支持栗次元随机图、风景图）
- 自定义封面图（可视化编辑标题、字体、图标、渐变背景和版式）
- AI生成封面图（使用 Halo AI Foundation 中配置的图像模型）

AI 生成需要先安装并启用 [Halo AI Foundation](https://github.com/halo-dev/plugin-ai-foundation)，
然后在文章编辑器的封面生成页面选择图像生成模型并调整本次提示词。模型供应商、API Key 和模型启用状态由 AI Foundation 统一管理。

生成时保持当前页面等待并预览结果，成功后自动设置封面，无需重复上传。尺寸或比例会按照所选模型（包括默认模型）声明的能力展示；未声明时提供通用选项。文章内容会先清理 HTML 和 Markdown 格式，再截取摘要作为生成素材。

插件设置保留附件存储组、存储策略和默认提示词；生成页提示词为空时使用设置中的默认提示词。生成接口通过 JSON 请求体接收参数，并对无效参数、文章不存在和上游错误返回相应的 HTTP 错误状态。

生成页支持“AI 优化提示词”：选择 AI 基座中的文本模型（留空使用默认文本模型），结合已保存文章、当前提示词、尺寸和风格生成绘图提示词。结果填回输入框供编辑，可撤销本次优化；点击“开始生成”后才会调用图像模型生成封面。

## 演示图
![演示图](https://www.lik.cc/upload/image-zuys.png)

## 文档
详细的图文使用文档：[封面图插件](https://docs.lik.cc/plugins/cover-image/)。

## 交流群
![QQ群](https://www.lik.cc/upload/QQ%E7%BE%A4.png)

## 开发环境

插件开发的详细文档请查阅：<https://docs.halo.run/developer-guide/plugin/introduction>

所需环境：

1. Java 21
2. Node 22.12+
3. pnpm 9
4. Docker (可选)

克隆项目：

```bash
git clone git@github.com:acanyo/plugin-coverImage.git

# 或者当你 fork 之后

git clone git@github.com:{your_github_id}/plugin-coverImage.git
```

```bash
cd path/to/plugin-coverImage
```

### 运行方式 1（推荐）

> 此方式需要本地安装 Docker

```bash
# macOS / Linux
./gradlew pnpmInstall

# Windows
./gradlew.bat pnpmInstall
```

```bash
# macOS / Linux
./gradlew haloServer

# Windows
./gradlew.bat haloServer
```

执行此命令后，会自动创建一个 Halo 的 Docker 容器并加载当前的插件，更多文档可查阅：<https://docs.halo.run/developer-guide/plugin/basics/devtools>

### 运行方式 2

> 此方式需要使用源码运行 Halo

编译插件：

```bash
# macOS / Linux
./gradlew build

# Windows
./gradlew.bat build
```

修改 Halo 配置文件：

```yaml
halo:
  plugin:
    runtime-mode: development
    fixedPluginPath:
      - "/path/to/plugin-coverImage"
```

最后重启 Halo 项目即可。

## 致谢

感谢以下开发者的贡献和支持：

- [困困鱼](https://wwww.kunkunyu.com) - 提供了宝贵的建议
- [柳意梧情](https://blog.muyin.site) - 提供了技术支持

## 许可证

本项目采用 GPL-3.0 许可证 - 查看 [LICENSE](LICENSE) 文件了解详情
