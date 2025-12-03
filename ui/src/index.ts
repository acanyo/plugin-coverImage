import { definePlugin } from "@halo-dev/console-shared";
import { markRaw, type Ref } from "vue";
import type { ListedPost } from "@halo-dev/api-client";
import { VDropdownItem, Toast } from "@halo-dev/components";
import AICoverGeneratorModal from "./components/AICoverGeneratorModal.vue";
import { h, render } from "vue";

// 创建弹窗挂载点
function createModalContainer() {
  let container = document.getElementById("ai-cover-modal-container");
  if (!container) {
    container = document.createElement("div");
    container.id = "ai-cover-modal-container";
    document.body.appendChild(container);
  }
  return container;
}

// 打开弹窗
function openGeneratorModal(postName: string, postTitle: string) {
  const container = createModalContainer();

  const vnode = h(AICoverGeneratorModal, {
    visible: true,
    postName: postName,
    postTitle: postTitle,
    "onUpdate:visible": (value: boolean) => {
      if (!value) {
        render(null, container);
      }
    },
    onSuccess: (imageUrl: string) => {
      console.log("封面图生成成功:", imageUrl);
      Toast.success("封面图已更新，请刷新页面查看");
    },
  });

  render(vnode, container);
}

export default definePlugin({
  components: {},
  routes: [],
  extensionPoints: {
    "post:list-item:operation:create": (
      post: Ref<ListedPost>
    ) => {
      return [
        {
          priority: 21,
          component: markRaw(VDropdownItem),
          label: "AI 生成封面",
          visible: true,
          permissions: [],
          action: (item?: ListedPost) => {
            if (!item) return;
            const postName = item.post.metadata.name;
            const postTitle = item.post.spec.title;
            openGeneratorModal(postName, postTitle);
          },
        },
      ];
    },
  },
});
