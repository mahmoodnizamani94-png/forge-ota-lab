# Forge OTA Lab — Play Store Listing

## App Identity

| Field | Value |
|-------|-------|
| **App Title** (30 chars max) | Forge OTA Lab |
| **Developer Name** | Forge OTA Lab |
| **Category** | Tools |
| **Content Rating** | PEGI 3 / Everyone |
| **Contact Email** | support@forgeotalab.dev |
| **Privacy Policy URL** | https://forgeotalab.dev/privacy |

## Short Description (80 chars max)

> Extract, verify, and inspect Android OTA packages — local-first, on-device.

## Full Description (4000 chars max)

Forge OTA Lab is a mobile extraction workbench for Android OTA update packages. Import an OTA ZIP or payload.bin file, and Forge will classify it, tell you exactly what's extractable, extract verified partition images, and export them — all locally on your phone or tablet.

**What it does:**

🔍 FORMAT DETECTION
Drop in any file. Forge reads magic bytes to identify the format — ZIP-wrapped AOSP OTA, standalone payload.bin, or legacy image archive. No filename gymnastics. No guessing.

📋 HONEST SUPPORT TIERS
Every package gets a truthful classification:
• Supported — full extraction with SHA-256 verification
• Experimental — extraction available with weaker guarantees
• Forensic — metadata and inspection only, no extraction promised

⚙️ VERIFIED EXTRACTION
Extract boot.img, init_boot.img, vendor_boot.img, vbmeta.img, dtbo.img, super.img, and other partitions directly on your device. Every extracted image includes SHA-256 verification against the manifest hash. No green checkmark until verification passes.

🔄 INCREMENTAL OTA SUPPORT (Experimental)
Forge detects incremental OTAs and shows you exactly what base images are needed. Field-level mismatch explanations — never "wrong base image."

📁 FILESYSTEM BROWSER
Browse extracted ext4 partition images read-only. View directory trees, file metadata, and export individual files.

💪 BUILT FOR RELIABILITY
• Resumable extraction — survives process death and device reboots
• Partial success — one failed partition doesn't kill the whole job
• Streaming extraction — 256 KB chunks, never loads a full partition into memory
• Progress notification when backgrounded

🔒 PRIVACY-FIRST
• All processing happens locally on your device
• No package contents, filenames, or paths are uploaded
• Crash reporting is opt-in only — disabled by default
• Works fully offline after installation

**Supported Formats:**
• Google / Pixel full payload-based OTAs (Supported)
• Standard payload.bin families (Supported)
• Standalone boot-critical images (Supported)
• Incremental payload-based OTAs with validated bases (Experimental)
• Unknown payload-based packages (Forensic — inspection only)

**Built With:**
• Rust extraction core for memory-safe, streaming binary parsing
• Jetpack Compose with Material 3 for a modern, accessible UI
• Dark mode default with full light mode support

**Who It's For:**
• Magisk users who need boot.img monthly
• ROM maintainers who need repeatable, verified extraction
• Security researchers who need partition inspection
• Repair technicians who need portable firmware tools
• Anyone tired of chaining desktop scripts to get a boot image

Forge OTA Lab does NOT flash firmware, unlock bootloaders, patch boot images, or download OTA files. It extracts and verifies — nothing more, nothing less.

## Feature Graphic

| Dimension | Value |
|-----------|-------|
| Width | 1024 px |
| Height | 500 px |
| Format | PNG (24-bit, no alpha) |
| Design | Dark surface with Forge copper brand, app name, tagline |

## Screenshots Needed

| # | Screen | Content |
|---|--------|---------|
| 1 | Home (empty) | First-launch branded illustration + Import CTA |
| 2 | Analysis | Supported package with partition list |
| 3 | Extraction | Progress with phase indicators |
| 4 | Results | Verified partitions with trust badges |
| 5 | Forensic | Unknown package in Forensic mode |
| 6 | Settings | Theme toggle, crash reporting consent |

## Content Rating Notes

- App does not contain violence, sexual content, gambling, or controlled substances
- App does not enable users to communicate with each other
- App does not collect personal information
- App processes firmware files — no user-generated content
- Target audience: 13+ (technical sophistication required)
