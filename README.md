# Global Vision TV

Android TV 原生端项目骨架，基于现有 H5 业务功能重建，但不修改原项目。

## 当前范围
- 首页
- 搜索
- 详情
- 播放器
- TV 专用焦点与遥控器导航
- 复用现有接口协议与数据语义

## 目录约定
- `app/`: Android TV 主工程
- `app/src/main/java/com/globalvision/tv/core`: 网络、模型、播放器等基础层
- `app/src/main/java/com/globalvision/tv/feature`: 页面与业务功能
- `app/src/main/java/com/globalvision/tv/ui`: TV 视觉主题与公共组件

## 下一步
1. 补齐 Android Studio/Gradle wrapper
2. 在 `core/network` 内完善当前项目的签名请求协议
3. 逐页实现 `Home -> Detail -> Player` 的闭环
