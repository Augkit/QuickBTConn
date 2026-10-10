# QuickBTConn

![Platform](https://img.shields.io/badge/platform-Android-3DDC84)
![Min SDK](https://img.shields.io/badge/min%20SDK-26%20(Android%208.0)-blue)
![License](https://img.shields.io/badge/license-MIT-green)

一个原生 Android 桌面小组件：为每个小组件选择一台已配对蓝牙设备，点击图标后尝试连接；已连接时再次点击断开。连接过程中图标呼吸渐变，成功为白色，失败或被动断开后为灰色。

> **AI 生成说明**：本项目的代码与文档由 AI 编程助手（ZCode，基于智谱 GLM 模型）生成，由人工提出需求、测试与验收。代码按"原样"提供，使用前请自行验证。

## 功能

- 每个桌面小组件独立选择设备
- 深色、蓝色背景形状与系统应用图标（自适应蒙版）一致，另有透明样式
- 内置耳机（AirPods 式）、头戴耳机、手表、音箱、键盘、鼠标、车载等图标，按设备名自动识别，也可手动指定
- 点击连接；已连接时再次点击断开
- 连接过程中图标呼吸渐亮渐暗（ViewFlipper 在启动器端播放，动画平滑），最长 8 秒
- 应用界面为 Material 3 风格，Android 12+ 支持动态取色（Material You）与深色模式
- 应用图标为自适应图标，支持系统主题色单色图标
- 监听 ACL、A2DP、Headset 和蓝牙适配器状态广播
- Android 12+ 请求“附近设备”权限
- Android 17（API 37）使用公开的 `BluetoothDevice.connect()`
- 首次保存经典蓝牙设备时通过系统“配套设备”弹窗取得主动连接授权
- Android 16 及更早版本尽力调用厂商能力，并为 BLE 设备回退到 GATT

## 构建

使用 Android Studio 打开项目，选择 JDK 17，然后构建或运行。项目使用 compileSdk/targetSdk 37、minSdk 26。

```powershell
.\gradlew.bat assembleDebug
```

安装后，长按桌面空白处，进入“小组件”，添加“蓝牙快捷连接”。首次添加会打开设备与样式设置页。

## 平台限制

Android 16（API 36）及更早版本没有向普通应用开放“连接任意已配对经典蓝牙设备”的统一接口。因此老系统上的经典耳机、音箱能否由本应用主动重连，取决于系统和厂商是否允许兼容调用；BLE GATT 设备可走公开接口。Android 17 起统一连接能力为公开 API。

## 许可证

[MIT](LICENSE)，版权所有 © 2026 Augkit。
