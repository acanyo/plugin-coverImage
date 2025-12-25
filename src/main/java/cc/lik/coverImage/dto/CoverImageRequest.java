package cc.lik.coverImage.dto;

import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 封面图生成请求 DTO
 */
@Data
@NoArgsConstructor
public class CoverImageRequest {
    /**
     * 封面类型
     */
    private String coverType;
    /**
     * AI生成图片内容
     */
    private String aiGenerateImgContent;
    /**
     * 封面图左侧标题
     */
    private String coverImgTitleLeft;
    /**
     * 封面图右侧标题
     */
    private String coverImgTitleRight;
    /**
     * 封面图背景颜色
     */
    private String coverBackgroundColor;
    /**
     * 内置颜色
     */
    private String builtColors;
    /**
     * 自定义颜色
     */
    private String customColors;
    /**
     * logo
     */
    private String coverImgLogo;
    /**
     * 图片内容 (base64)
     */
    private String imageContent;
    /**
     * 文章元数据名称
     */
    private String postName;
    
    /**
     * 图片风格（用于配图生成）
     * 例如：realistic（写实）、cartoon（卡通）、anime（动漫）、painting（绘画）
     */
    private String imageStyle;
    
    /**
     * 图片尺寸（用于配图生成）
     * 例如：1024x1024、1792x1024、1024x1792
     */
    private String imageSize;
} 