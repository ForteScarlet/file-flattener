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

![Home.png](.github/images/Home.png)
![Workspace.png](.github/images/Workspace.png)
![Config.png](.github/images/Config.png)
![Workspace-Add1.png](.github/images/Workspace-Add1.png)
![Workspace-Add2.png](.github/images/Workspace-Add2.png)
![Workspace-Work.png](.github/images/Workspace-Work.png)
![Workspace-Detail.png](.github/images/Workspace-Detail.png)