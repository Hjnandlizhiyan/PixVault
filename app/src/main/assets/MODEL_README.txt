模型放置说明（请在真机运行前阅读）
========================================

本应用使用 CLIP ViT-B/32 做图片特征提取与文本语义搜图，需要两个 ONNX 模型，
均放在本目录（app/src/main/assets/）：

1) 图像编码器
--------------------------------------------------
文件名：

    image-encoder.onnx

模型要求：
- 输入：float32，NCHW 布局，shape 为 [1, 3, H, W]（通常 H=W=224）
- 输出：float32，shape 为 [1, D]（ViT-B/32 为 512 维，输出名 image_embeds）

2) 文本编码器（语义搜图用）
--------------------------------------------------
文件名：

    text-encoder.onnx        （CLIP 文本塔，本项目使用量化版，约 64.5 MB）
    vocab.json               （BPE 词表，约 862 KB）
    merges.txt               （BPE 合并规则，约 525 KB）

模型要求：
- 输入：int64，shape 为 [batch, 77]（input_ids，CLIP 上下文长度固定 77）
- 输出：float32，shape 为 [batch, 512]（text_embeds）

推荐来源：
1. HuggingFace 搜索 "clip-vit-base-patch32" 的 ONNX 版本
   （例如 Xenova/clip-vit-base-patch32）
2. 使用 open_clip 下载 ViT-B/32 权重，用 Python 分别导出图像/文本编码器为 ONNX

本项目资源对应（Xenova/clip-vit-base-patch32）：
- onnx/model.onnx（图像塔）→ 重命名为 image-encoder.onnx
- onnx/text_model_quantized.onnx  → 重命名为 text-encoder.onnx
- vocab.json / merges.txt 直接使用

说明：
- 支持 FP32 / FP16 / INT8 量化模型，代码会自动读取输入宽高
- 图像塔与文本塔必须来自同一 CLIP 模型，输出需处于同一投影空间（均为 512 维）
- 首次运行时，模型会被拷贝到应用私有目录缓存（filesDir），之后直接复用
- 文本塔为按需懒加载：只有首次使用「语义搜图」时才会加载
