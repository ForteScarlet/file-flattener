# FileFlattener

<img src="icon.svg" alt="FileFlattener Icon" height="128">

[中文](README_CN.md) | **English**

A desktop application that flattens files from multiple directories into a single target directory using hard links.

## Features

- **Hard Link Flattening**: Flatten files from source directories to a target directory using hard links, **no disk
  space duplication**.
- **Extension Filtering**: Select specific file types to include.
- **Multi-Source Support**: Add multiple source directories in one operation.
- **Cross-Platform**: Supports Windows, macOS, and Linux.

## Use Cases

When you have files or media scattered across deeply nested directory structures, and your browsing application cannot
recursively display them all at once, you may want to create a completely flattened view without disrupting the original
directory structure, without consuming extra disk space, and allowing for seamless marathon-style
browsing ---- **FileFlattener** is the perfect tool for this!

## Installation

### Download

Visit the [Download Page](https://fortescarlet.github.io/file-flattener/download) to get the latest version.

## Examples

### Before Flattening

```
Source Directories:
├── Music/
│   ├── Album1/
│   │   ├── track1.mp3
│   │   └── track2.mp3
│   └── Album2/
│       └── track3.mp3
└── Downloads/
    └── Songs/
        └── track4.mp3
```

### After Flattening

```
Target Directory:
└── FlattenedMusic/
    ├── track1.mp3  (hard link)
    ├── track2.mp3  (hard link)
    ├── track3.mp3  (hard link)
    └── track4.mp3  (hard link)
```

All files are accessible in a single directory without duplicating disk space.

## Usage

1. Launch the application
2. Go to **Workspace**
3. Add source directories and select file extensions to include
4. Choose a target directory
5. Click **Execute** to flatten files

## Requirements

- Java 21+ (bundled in installers)
- File system must support hard links

## Building from Source

```bash
# Clone the repository
git clone https://github.com/ForteScarlet/file-flattener.git
cd file-flattener

# Build and run
./gradlew :composeApp:run

# Create distribution packages
./gradlew :composeApp:packageDistributionForCurrentOS
```

## License

Copyright © 2024-2025 Forte Scarlet. All rights reserved.

## Links

- [GitHub Repository](https://github.com/ForteScarlet/file-flattener)
- [Download Page](https://fortescarlet.github.io/file-flattener/download)
