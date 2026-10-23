import { describe, expect, it, vi } from "vitest";

vi.mock("@halo-dev/api-client", () => ({ axiosInstance: { get: vi.fn() } }));

import { resolveImageSizeOptions } from "../useAiImageOptions";

describe("image size options", () => {
  it("uses declared sizes rather than generic sizes", () => {
    expect(resolveImageSizeOptions({ sizes: ["1536x1024", "1024x1024", "1536x1024"] }))
      .toEqual([
        { label: "1536×1024", value: "1536x1024" },
        { label: "1024×1024", value: "1024x1024" },
      ]);
  });

  it("supports models that expose only aspect ratios", () => {
    expect(resolveImageSizeOptions({ aspectRatios: ["16:9", "1:1"] }).map((option) => option.value))
      .toEqual(["16:9", "1:1"]);
  });

  it("keeps generic options when capabilities are unknown", () => {
    expect(resolveImageSizeOptions(null).some((option) => option.value === "2560x1440"))
      .toBe(true);
  });
});
