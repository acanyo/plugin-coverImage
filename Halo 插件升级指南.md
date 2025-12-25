# Halo 插件升级指南
插件API变更请参考 [Halo 官方文档](https://docs.halo.run/next/developer-guide/plugin/api-changelog)
## 升级到 Halo 2.22

### 1. 后端依赖更新

#### build.gradle
```groovy
// 更新平台版本
implementation platform('run.halo.tools.platform:plugin:2.22.0')

// 更新 devtools 版本
id "run.halo.plugin.devtools" version "0.6.2"

// 更新 halo 配置块中的版本
halo {
    version = '2.22.0'
}
```

#### plugin.yaml
```yaml
spec:
  requires: ">=2.22.0"
```

---

### 2. 后端 API 变更

#### 2.1 IndexSpecs 注册方式变更

**旧写法（已弃用）：**
```java
import static run.halo.app.extension.index.IndexAttributeFactory.simpleAttribute;
import run.halo.app.extension.index.IndexSpec;

schemeManager.register(Summary.class, indexSpecs -> {
    indexSpecs.add(new IndexSpec()
        .setName("spec.fieldName")
        .setIndexFunc(simpleAttribute(Summary.class,
            item -> item.getSpec().getFieldName())
        ));
});
```

**新写法：**
```java
import run.halo.app.extension.index.IndexSpecs;
import java.util.Optional;

schemeManager.register(Summary.class, indexSpecs -> {
    indexSpecs.add(IndexSpecs.<Summary, String>single("spec.fieldName", String.class)
        .indexFunc(item -> Optional.ofNullable(item.getSpec())
            .map(Summary.Spec::getFieldName)
            .orElse(null)));
});
```

#### 2.2 QueryFactory → Queries

**旧写法（已弃用）：**
```java
import static run.halo.app.extension.index.query.QueryFactory.and;
import static run.halo.app.extension.index.query.QueryFactory.equal;
import static run.halo.app.extension.index.query.QueryFactory.isNotNull;
import static run.halo.app.extension.index.query.QueryFactory.in;

// 使用
and(equal("field", value), isNotNull("field"))
```

**新写法：**
```java
import static run.halo.app.extension.index.query.Queries.and;
import static run.halo.app.extension.index.query.Queries.equal;
import static run.halo.app.extension.index.query.Queries.isNull;
import static run.halo.app.extension.index.query.Queries.in;

// 使用 - 注意：isNotNull 改为 isNull().not()
and(equal("field", value), isNull("field").not())
```

**注意：** `Queries` 中没有 `isNotNull` 方法，需要使用 `isNull("field").not()` 替代。

---

### 3. 前端依赖更新

#### ui/package.json

##### 3.1 @halo-dev/console-shared 已弃用
移除 `@halo-dev/console-shared`，改用 `@halo-dev/ui-shared`。

##### 3.2 使用版本
```json
{
  "dependencies": {
    "@halo-dev/api-client": "^2.22.0",
    "@halo-dev/components": "^2.22.0",
    "@halo-dev/ui-shared": "^2.22.0",
    "@halo-dev/richtext-editor": "^2.22.0",
    "pinia": "^3.0.4",
    "vue-router": "^4.6.4",
    "axios": "^1.13.2",
    "vue": "^3.5.24"
  },
  "devDependencies": {
    "@halo-dev/ui-plugin-bundler-kit": "^2.22.0",
    "@rsbuild/core": "^1.4.3"
  }
}
```

**注意：** 
- `@halo-dev/ui-shared` 需要 `pinia` 和 `vue-router` 作为 peer dependencies
- 版本号需要从 Halo 官方获取最新的版本

##### 3.3 完整依赖示例（2.22.0）

```json
{
  "dependencies": {
    "@halo-dev/api-client": "^2.22.0",
    "@halo-dev/components": "^2.22.0",
    "@halo-dev/ui-shared": "^2.22.0",
    "@halo-dev/richtext-editor": "^2.22.0",
    "pinia": "^3.0.4",
    "vue-router": "^4.6.4"
  },
  "devDependencies": {
    "@halo-dev/ui-plugin-bundler-kit": "^2.22.0"
  }
}
```

---

### 4. 前端代码变更

#### ui/src/index.ts

**旧写法：**
```typescript
import { definePlugin } from "@halo-dev/console-shared";
```

**新写法：**
```typescript
import { definePlugin } from "@halo-dev/ui-shared";
```

#### ui/rsbuild.config.ts

添加类型注解，解决 TS2742 错误（The inferred type of 'default' cannot be named without a reference to '@rsbuild/core'）：

**旧写法：**
```typescript
import { rsbuildConfig } from '@halo-dev/ui-plugin-bundler-kit';

export default rsbuildConfig({
  // ...配置
})
```

**新写法：**
```typescript
import type { RsbuildConfig } from "@rsbuild/core";
import { rsbuildConfig } from '@halo-dev/ui-plugin-bundler-kit';

export default rsbuildConfig({
  // ...配置
}) as RsbuildConfig
```

**变更要点：**
- 添加 `import type { RsbuildConfig } from "@rsbuild/core"` 类型导入
- 在 `rsbuildConfig({...})` 后添加 `as RsbuildConfig` 类型断言

#### 4.1 编辑器 BubbleMenu 扩展点变更

**旧写法（2.21）：**
```typescript
getBubbleMenu({ editor }: { editor: Editor }) {
  return {
    pluginKey: 'my-bubble-menu',
    shouldShow: ({ state }) => isActive(state, 'myNode'),
    items: [
      {
        priority: 50,
        props: {
          icon: markRaw(MyIcon),
          title: '操作',
          action: ({ editor }: { editor: Editor }) => {
            // action 接收 editor 参数
            doSomething(editor)
          },
        },
      },
    ],
  }
}
```

**新写法（2.22）：**
```typescript
getBubbleMenu({ editor }: { editor: Editor }) {
  return {
    pluginKey: 'my-bubble-menu',
    shouldShow: ({ state }) => isActive(state, 'myNode'),
    items: [
      {
        priority: 50,
        icon: markRaw(MyIcon),  // 直接放在 item 上，不再包裹在 props 中
        title: '操作',
        action: () => {         // action 不再接收参数，使用闭包中的 editor
          doSomething(editor)
        },
      },
    ],
  }
}
```

**变更要点：**
- `icon`、`title`、`action` 直接放在 item 对象上，移除 `props` 包装层
- `action` 函数不再接收 `{ editor }` 参数，需通过闭包访问 `editor`

#### 4.2 编辑器 getDraggable 扩展点移除

在 Halo 2.22.0 中，旧的 `getDraggable` 扩展点被移除，取而代之的是 `getDraggableMenuItems` 扩展点。

**处理方式：**
- 如果项目中使用了 `getDraggable`，可直接移除，不再使用
- 无需考虑兼容性问题
- 如需拖拽菜单功能，使用新的 `getDraggableMenuItems` 扩展点

**新扩展点示例：**
```typescript
getDraggableMenuItems({ editor }: { editor: Editor }) {
  return [
    {
      priority: 100,
      icon: markRaw(MyIcon),
      title: '我的操作',
      action: () => {
        // 执行操作
      },
    },
  ]
}
```

详细文档：[拖拽菜单扩展](https://docs.halo.run/developer-guide/plugin/extension-points/ui/default-editor-extension-create#5-%E6%8B%96%E6%8B%BD%E8%8F%9C%E5%8D%95%E6%89%A9%E5%B1%95)

---

### 5. 升级步骤清单

#### 后端

1. [ ] 更新 `build.gradle` 平台版本 `plugin:2.22.0`
2. [ ] 更新 `build.gradle` devtools 版本 `0.6.2`
3. [ ] 更新 `build.gradle` halo 配置块 `version = '2.22'`
4. [ ] 更新 `plugin.yaml` requires 版本 `>=2.22.0`
5. [ ] 替换 `QueryFactory` → `Queries`
6. [ ] 替换 `isNotNull()` → `isNull().not()`
7. [ ] 替换 `IndexSpec` + `IndexAttributeFactory` → `IndexSpecs.single()` (如有使用)

#### 前端

1. [ ] 更新 `ui/package.json` 所有 `@halo-dev/*` 依赖版本到 `2.22.0`
2. [ ] 替换 `@halo-dev/console-shared` → `@halo-dev/ui-shared`
3. [ ] 添加 `pinia` 和 `vue-router` 依赖
4. [ ] 更新 `ui/src/index.ts` 中的 import
5. [ ] 更新 `rsbuild.config.ts` 添加类型注解 (如需要)

#### 验证

1. [ ] 刷新 Gradle 依赖：`./gradlew --refresh-dependencies`
2. [ ] 重新安装前端依赖：`cd ui && rm -rf node_modules pnpm-lock.yaml && pnpm install`
3. [ ] 验证构建：`./gradlew build`

---

### 6. 参考资源

- [plugin-equipment 升级示例](https://github.com/chengzhongxue/plugin-equipment/commit/c9ccdfe1a777c8928289fcbcc297165d89c5455e)
- [Halo 官方文档](https://docs.halo.run/)
- [pkg.pr.new 预发布包](https://pkg.pr.new/)

---

### 7. 常见问题

#### Q: IDE 报错找不到新 API？
A: 刷新 Gradle 依赖：`./gradlew --refresh-dependencies` 或 IDE 中 Reload Gradle Project。

#### Q: 前端 404 错误？
A: 检查 pkg.pr.new 版本号是否正确，可以在浏览器访问 URL 验证。

#### Q: TypeScript 类型冲突？
A: 确保所有 `@halo-dev/*` 包使用相同的 pkg.pr.new 版本系列。

---
