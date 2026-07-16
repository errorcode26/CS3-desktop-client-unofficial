# CloudStream Desktop (Unofficial Client)

> "This is a development branch. Fixes are actively being made, and this branch may contain unstable duct-tape code."

Welcome to the CloudStream Desktop project. This is a native Compose for Desktop application designed to run CloudStream Android plugins natively on a desktop JVM environment.

## Recent Updates
- Experimental Sandbox: Basic bytecode sandboxing is in place to try and restrict plugins from accessing local files, though it is still a work in progress and not guaranteed to be fully secure.
- UI Adjustments: Minor UI layout fixes and loading screen adjustments.
- JVM Packaging: Setup JLink for bundling the JVM to help reduce the final build size.

## Test Coverage (Or Lack Thereof)
If you are looking for extensive unit tests or a robust CI/CD pipeline, you have come to the wrong place. We currently have exactly three test files. Why? Because this entire architecture was forged in the fires of rushed development, AI-assisted coding, and late-night vibecoding sessions. We traded test coverage for pure vibes, and frankly, it works on our machines.

## Disclaimer
This repository acts purely as a blank-slate media shell. The application does not ship with any plugins, media files, or pre-configured content sources. The developers hold no responsibility or liability for how users choose to utilize this software.

## Setup & Installation

### For End Users
Simply download the latest .exe installer from the releases page and run it. Everything is pre-bundled (including the hardware-accelerated video player). There is absolutely zero configuration required.

---

### For Developers (Building from Source)

1. Prerequisites:
- JDK 21 or higher
- Git (Required for submodule cloning)
- Inno Setup (Required if you want to generate the Windows .exe installer)

2. Cloning the Repository:
```bash
git clone --recursive https://github.com/errorcode26/cloudstream-desktop-unofficial.git
cd cloudstream-desktop-unofficial
```
> [!WARNING]  
> DO NOT DOWNLOAD THIS REPOSITORY AS A ZIP FILE. You must use git clone --recursive to pull the android-reference submodule properly.

3. The Video Engine:
Download the latest mpv-dev Windows build (from SourceForge) and place libmpv-2.dll directly inside desktop-app/appResources/windows/mpv/.

4. Building for Local Testing:
To compile and launch the application locally, run:
```bash
./gradlew desktop-app:run
```

5. Packaging the Release:
First, build the optimized AppImage release folder using Gradle:
```bash
./gradlew desktop-app:packageReleaseAppImage
```
Next, to generate the final .exe installer, open installer/setup.iss in Inno Setup and compile it. The generated installer will be output to desktop-app/build/outputs/.

## Acknowledgements
Significant acknowledgement is given to the original CloudStream developers and contributors. This project utilizes their core scraping engine and extension architecture as a foundation.
