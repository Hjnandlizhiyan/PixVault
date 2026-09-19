# 模拟器内容区域黑屏排查

## 2026-09-19 验证记录

环境：Android Emulator 37.1.11，Medium_Phone，Android 17 / API 37.1，16 KB x86_64 系统镜像。

故障时 PixVault 的 Compose 控件层级完整、Activity 处于 resumed 状态，但 WindowManager 中应用 Surface 为 `shown=false`、`HAS_DRAWN`。对照启动系统设置，同样出现隐藏 Surface。因此不能仅凭该现象认定为 PixVault 启动动画或布局错误。

重启模拟器系统后，恢复直接启动 MainActivity 的版本连续通过 6 次启动测试，并人工检查截图，能看到标题、导航、角色卡片与照片。已撤除先前未能解决问题的 BootstrapActivity 和禁用窗口动画设置。

原 AVD 分配 2048 MB 内存并启用快照启动。已将本机 Medium_Phone 改为 4096 MB，并启用冷启动；未清除图库或应用数据。该配置位于本机 AVD，不随 APK 分发。

官方说明 Android 17 手机虚拟设备至少需要 4 GB 内存：[Emulator release notes](https://developer.android.com/studio/releases/emulator)。内存不足是否是此次窗口异常的唯一诱因，尚未单独证明。

## 若再次出现

1. 检查系统设置等其他应用是否也黑屏，并读取 WindowManager 状态和崩溃日志。
2. 在 Android Studio Device Manager 对目标设备执行 Cold Boot，避免恢复故障快照；不要直接 Wipe Data。
3. 确认 Android 17 AVD 内存至少为 4096 MB。
4. 同时验证冷启动、返回退出后重开和回桌面后重开，检查实际 UI 截图。仅检查非黑像素不能证明页面正常，单色启动背景也可能通过这种检查。
