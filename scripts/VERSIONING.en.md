# VERSIONING

## 1. Overview

This document defines the versioning scheme and build artifact naming conventions for the project. The goals are:

- Keep external versions simple and stable
- Support multiple builds per day
- Ensure full traceability of builds
- Facilitate automated release and operations

------

## 2. Version Structure

| Item           | Format                           | Example              | Description                |
| -------------- | -------------------------------- | -------------------- | -------------------------- |
| Full Version   | `major.minor.patch+yyMMdd.SSSSS` | `1.0.0+260415.42305` | Used for internal tracking |
| Public Version | `major.minor.patch`              | `1.0.0`              | Visible to users           |
| Build Number   | `yyMMdd.SSSSS`                   | `260415.42305`       | Internal use only          |

------

## 3. Public Version (SemVer)

### Format

```
major.minor.patch
```

### Rules

| Field | Meaning       | When to Increment              |
| ----- | ------------- | ------------------------------ |
| major | Major version | Breaking changes               |
| minor | Minor version | Backward-compatible features   |
| patch | Patch version | Bug fixes / small improvements |

### Examples

| Type     | Example |
| -------- | ------- |
| Initial  | `1.0.0` |
| Feature  | `1.1.0` |
| Fix      | `1.1.1` |
| Breaking | `2.0.0` |

### MSI Constraints (Windows)

| Field | Limit   |
| ----- | ------- |
| major | ≤ 255   |
| minor | ≤ 255   |
| patch | ≤ 65535 |

------

## 4. Build Number

### Format

```
yyMMdd.SSSSS
```

### Fields

| Field | Example | Description             |
| ----- | ------- | ----------------------- |
| yy    | 26      | Last two digits of year |
| MM    | 04      | Month                   |
| dd    | 15      | Day                     |
| SSSSS | 42305   | Seconds since midnight  |

------

## 5. Seconds Offset Calculation

### Formula

```
SSSSS = hour * 3600 + minute * 60 + second
```

### Examples

| Time     | Calculation         | Result |
| -------- | ------------------- | ------ |
| 00:00:00 | 0                   | 0      |
| 11:45:05 | 11×3600 + 45×60 + 5 | 42305  |
| 23:59:59 | Max value           | 86399  |

### Range

```
0 ~ 86399
```

------

## 6. Full Version

### Format

```
major.minor.patch+yyMMdd.SSSSS
```

### Examples

| Build Time          | Full Version         |
| ------------------- | -------------------- |
| 2026-04-15 00:00:00 | `1.0.0+260415.0`     |
| 2026-04-15 11:45:05 | `1.0.0+260415.42305` |
| 2026-04-15 23:59:59 | `1.0.0+260415.86399` |

------

## 7. Sorting Rules

Versions are compared in the following order:

| Priority | Field  |
| -------- | ------ |
| 1        | major  |
| 2        | minor  |
| 3        | patch  |
| 4        | yyMMdd |
| 5        | SSSSS  |

Notes:

- Compare semantic version first
- Then compare build time
- Later build → higher version

------

## 8. Display & Usage

### 8.1 Public Display

| Scenario      | Format  |
| ------------- | ------- |
| UI            | `1.0.0` |
| Release notes | `1.0.0` |

------

### 8.2 Internal Usage

| Scenario   | Format               |
| ---------- | -------------------- |
| Logs       | `1.0.0+260415.42305` |
| Debugging  | Full version         |
| About page | Full version         |

------

### 8.3 MSI Version

| Item        | Rule                          |
| ----------- | ----------------------------- |
| Format      | `major.minor.patch`           |
| Restriction | Must NOT include build number |

------

## 9. File Naming Convention

### Format

```
<ProductName>-<version>-<platform>.<ext>
```

### Example

```
NomoClaw-1.0.0-windows-x64.msi
```

------

## 10. Field Definitions

### 10.1 ProductName

| Rule      | Description                |
| --------- | -------------------------- |
| Style     | PascalCase                 |
| Forbidden | spaces / non-ASCII / emoji |

Examples:

- `NomoClaw`
- `MyApp`

------

### 10.2 Version

| Rule      | Description                           |
| --------- | ------------------------------------- |
| Use       | `major.minor.patch`                   |
| Forbidden | build number / `+` / pre-release tags |

------

### 10.3 Platform

| Platform    | Example         |
| ----------- | --------------- |
| Windows     | `windows-x64`   |
| Windows ARM | `windows-arm64` |
| macOS       | `macos-arm64`   |
| Linux       | `linux-x64`     |

------

### 10.4 Extension

| Type      | Example   |
| --------- | --------- |
| Installer | msi / exe |
| macOS     | dmg       |
| Linux     | AppImage  |

------

## 11. File Naming Constraints

### ❌ Invalid Examples

```
NomoClaw-1.0.0+260415.42305.msi
NomoClaw-1.0.0🔥.msi
NomoClaw 1.0.0.msi
```

### ✅ Valid Example

```
NomoClaw-1.0.0-windows-x64.msi
```

------

## 12. Build Number Storage

| Location   | Example                             |
| ---------- | ----------------------------------- |
| Logs       | `Build Version: 1.0.0 (g1a2b3c4)`   |
| About page | `Version: 1.0.0 (g1a2b3c4)`         |
| File       | `build/version.txt`                 |

Content:

```
1.0.0 (g1a2b3c4)
```

------

## 13. Design Principles

- Keep external versions simple and stable
- Ensure precise internal traceability
- Support multiple daily builds
- Avoid manual version increments
- Keep filenames stable and automation-friendly
- Do not expose build metadata in public artifacts

------

## 14. Bash Example

```bash
APP_VERSION="1.0.0"
GIT_SHORT_SHA="$(git rev-parse --short=7 HEAD)"

FULL_VERSION="${APP_VERSION} (g${GIT_SHORT_SHA})"

echo "APP_VERSION=${APP_VERSION}"
echo "GIT_SHORT_SHA=${GIT_SHORT_SHA}"
echo "FULL_VERSION=${FULL_VERSION}"
```

------

## 15. Summary

| Item           | Example                          |
| -------------- | -------------------------------- |
| Public Version | `1.0.0`                          |
| Build Number   | `g1a2b3c4`                       |
| Full Version   | `1.0.0 (g1a2b3c4)`               |
| Artifact       | `NomoClaw-1.0.0-windows-x64.msi` |
