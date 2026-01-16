# 目录平铺助手

![](icon.svg)

**中文** | [English](README.md)

一款桌面应用程序，通过硬链接将多个目录中的文件平铺到单一目标目录。

## 功能特性

- **硬链接平铺**：使用硬链接将源目录文件平铺到目标目录，**不占用额外磁盘空间**。
- **扩展名筛选**：按文件类型选择性转移。
- **多源目录**：支持同时添加多个源目录。
- **跨平台**：支持 Windows、macOS 和 Linux

## 使用场景

当你存在一些目录层级过多的文件、媒体等，而相对应的浏览应用无法直接递归地依次展现它们，
这时候你想要在不破坏原本资源的目录结构的前提下额外准备一个完全平铺、不占用额外磁盘空间、还方便马拉松式一马平川地阅览它们时，
**目录平铺助手** 就可以派上用场了！

## 安装

### 下载

访问 [下载页面](https://fortescarlet.github.io/file-flattener/download) 获取最新版本。

## 示例

### 平铺前

```
源目录：
├── 音乐/
│   ├── 专辑1/
│   │   ├── 曲目1.mp3
│   │   └── 曲目2.mp3
│   └── 专辑2/
│       └── 曲目3.mp3
└── 下载/
    └── 歌曲/
        └── 曲目4.mp3
```

### 平铺后

```
目标目录：
└── 平铺音乐/
    ├── 曲目1.mp3  (硬链接)
    ├── 曲目2.mp3  (硬链接)
    ├── 曲目3.mp3  (硬链接)
    └── 曲目4.mp3  (硬链接)
```

所有文件集中在单一目录中，无需占用额外磁盘空间。

## 使用方法

1. 启动应用程序
2. 进入 **工作区**
3. 添加源目录并选择要包含的文件扩展名
4. 选择目标目录
5. 点击 **执行** 开始平铺文件

## 系统要求

- Java 21+（安装包已内置）
- 文件系统需支持硬链接

## 从源码构建

```bash
# 克隆仓库
git clone https://github.com/ForteScarlet/file-flattener.git
cd file-flattener

# 构建并运行
./gradlew :composeApp:run

# 创建分发包
./gradlew :composeApp:packageDistributionForCurrentOS
```

## 许可证

Copyright © 2024-2025 Forte Scarlet. All rights reserved.

## 链接

- [GitHub 仓库](https://github.com/ForteScarlet/file-flattener)
- [下载页面](https://fortescarlet.github.io/file-flattener/download)
