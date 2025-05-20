# plugin-coverImage

Halo 2.0 文章封面图片生成插件，支持自动生成文章封面图片。

## 功能特性

三种封面图
- 文章首图
- 随机图（支持全场景4K图、风景图、二次元）
- 自定义封面图(根据你输入文字 ICON 生成一个封面图)

## 演示图
![演示图](https://www.lik.cc/upload/image-zuys.png)

## 文档
详细的使用文档：https://docs.lik.cc/

## 交流群
![QQ群](https://www.lik.cc/upload/QQ%E7%BE%A4.png)

## 开发环境

插件开发的详细文档请查阅：<https://docs.halo.run/developer-guide/plugin/introduction>

所需环境：

1. Java 17
2. Node 20
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

本项目采用 MIT 许可证 - 查看 [LICENSE](LICENSE) 文件了解详情
