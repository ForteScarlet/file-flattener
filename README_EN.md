# File Flattener

<img src="icon.svg" alt="File Flattener Icon" height="128">

**English** | [中文](README.md)

A desktop application that recursively flattens files from multiple directories into a single target directory using
file links, without consuming extra disk space.

## Features

- **Directory Recursive Flattening**: Flatten files from source directories to a target directory using file links, **no
  extra disk space consumption**.
- **Extension Filtering**: Selectively transfer files by type.
- **Multi-Source Support**: Add multiple source directories simultaneously.
- **Cross-Platform**: Supports Windows, macOS, and Linux.

## Use Cases

When you have files or media scattered across deeply nested directory structures, and your browsing application cannot
recursively display them all at once, you may want to create a completely flattened view without disrupting the original
directory structure, without consuming extra disk space, and allowing for seamless marathon-style browsing ---- **File
Flattener** is the perfect tool for this!

## Installation

### Download

Visit the [Download Page](https://fortescarlet.github.io/file-flattener/download) to get the latest version.

## Examples

### Before Flattening

```
Source Directories:
├── Music/
│   ├── Album1/
│   │   ├── Track1.mp3
│   │   └── Track2.mp3
│   └── Album2/
│       └── Track3.mp3
└── Downloads/
    └── Songs/
        └── Track4.mp3
```

### After Flattening

```
Target Directory:
└── Flattened Music/
    ├── Music-Album1-Track1.mp3
    ├── Music-Album1-Track2.mp3
    ├── Music-Album2-Track3.mp3
    └── Downloads-Songs-Track4.mp3
```

All files are centralized in a single directory without consuming extra disk space.

## Application Screenshots

<picture>
  <source media="(prefers-color-scheme: dark)" srcset=".github/images/Home_dark.png">
  <source media="(prefers-color-scheme: light)" srcset=".github/images/Home.png">
  <img alt="Home" src=".github/images/Home.png">
</picture>

<picture>
  <source media="(prefers-color-scheme: dark)" srcset=".github/images/Workspace_dark.png">
  <source media="(prefers-color-scheme: light)" srcset=".github/images/Workspace.png">
  <img alt="Workspace" src=".github/images/Workspace.png">
</picture>

<picture>
  <source media="(prefers-color-scheme: dark)" srcset=".github/images/Config_dark.png">
  <source media="(prefers-color-scheme: light)" srcset=".github/images/Config.png">
  <img alt="Config" src=".github/images/Config.png">
</picture>

<picture>
  <source media="(prefers-color-scheme: dark)" srcset=".github/images/Workspace-Add1_dark.png">
  <source media="(prefers-color-scheme: light)" srcset=".github/images/Workspace-Add1.png">
  <img alt="Workspace-Add1" src=".github/images/Workspace-Add1.png">
</picture>

<picture>
  <source media="(prefers-color-scheme: dark)" srcset=".github/images/Workspace-Add2_dark.png">
  <source media="(prefers-color-scheme: light)" srcset=".github/images/Workspace-Add2.png">
  <img alt="Workspace-Add2" src=".github/images/Workspace-Add2.png">
</picture>

<picture>
  <source media="(prefers-color-scheme: dark)" srcset=".github/images/Workspace-Work_dark.png">
  <source media="(prefers-color-scheme: light)" srcset=".github/images/Workspace-Work.png">
  <img alt="Workspace-Work" src=".github/images/Workspace-Work.png">
</picture>

<picture>
  <source media="(prefers-color-scheme: dark)" srcset=".github/images/Workspace-Detail_dark.png">
  <source media="(prefers-color-scheme: light)" srcset=".github/images/Workspace-Detail.png">
  <img alt="Workspace-Detail" src=".github/images/Workspace-Detail.png">
</picture>