import { definePlugin } from "@halo-dev/ui-shared";
import { Extension } from "@tiptap/core";
import { markRaw } from "vue";
import CoverGeneratorToolbarItem from "./components/CoverGeneratorToolbarItem.vue";

const CoverGeneratorExtension = Extension.create({
  name: "coverGenerator",
  addOptions() {
    return {
      getToolbarItems({ editor }: { editor: any }) {
        return [
          {
            priority: 1000,
            component: markRaw(CoverGeneratorToolbarItem),
            props: {
              editor,
              isActive: false,
              disabled: false,
            },
          },
        ];
      },
    };
  },
});

export default definePlugin({
  components: {},
  routes: [],
  extensionPoints: {
    "default:editor:extension:create": () => {
      return [CoverGeneratorExtension] as any[];
    },
  },
});
