# zhaopin-auto — AI Agent Deployment Guide

> This document is written for AI Agents to automatically download, configure, and launch this project.

## Overview

Java + Selenium automated job application tool for ZhiLian (智联招聘). Core flow:

```
Search job listings → Open job detail → AI reads detail + matches resume → Apply if match, skip if not
```

Each job triggers an AI API call (5-15s delay), naturally avoiding rate limits.

## Requirements

| Dependency | Version | Purpose |
|-----------|---------|---------|
| JDK | 17 (required, 21+ incompatible) | Compile & run |
| Maven | 3.6+ | Build project |
| Google Chrome | Match chromedriver version | Automation browser |

### JDK 17

- **Windows**: https://www.azul.com/downloads/?version=java-17-lts&os=windows&architecture=x86-64-bit&package=jdk
- **macOS**: `brew install --cask zulu@17`
- **Linux**: `apt install openjdk-17-jdk`

### ChromeDriver

Must match Chrome major version. Place in `src/main/resources/chromedriver.exe` (Windows) or `src/main/resources/chromedriver` (macOS/Linux).

Check Chrome version: `chrome://version`
Download: https://googlechromelabs.github.io/chrome-for-testing/

## Setup

### 1. Clone

```bash
git clone <repo-url> zhaopin-auto
cd zhaopin-auto
```

### 2. Configure AI API

```bash
cp .env_template .env
```

Edit `.env` with your API credentials:

```env
BASE_URL=https://api.deepseek.com
API_KEY=your-api-key-here
MODEL=deepseek-chat
```

Supports any OpenAI-compatible API (Xiaomi MiMo, DeepSeek, OpenAI, etc.)

### 3. Fill Resume

Edit `resume.md` with your resume content. The AI reads this to judge job matching.

### 4. Configure Search

Edit `src/main/resources/config.yaml`:

```yaml
zhilian:
  cityCode: "广州"        # City name
  salary: "0,5000"          # Salary range (yuan), "0,5000" = 0-5000
  keywords:                 # Search keywords
    - "供应链实习"
    - "数据分析"
```

### 5. Place ChromeDriver

Put chromedriver in `src/main/resources/` matching your Chrome version.

### 6. Build

```bash
mvn clean package -DskipTests
```

Output: `target/get_jobs-v2.0.1-jar-with-dependencies.jar`

### 7. Run

```bash
# Windows
start.bat

# Or directly
java -cp target/get_jobs-v2.0.1-jar-with-dependencies.jar zhilian.ZhiLian
```

First run opens browser for QR code login. Cookie is saved for subsequent runs.

## Project Structure

```
zhaopin-auto/
├── .env_template          # API config template
├── .gitignore
├── AGENTS.md              # This document
├── pom.xml                # Maven build config
├── resume.md              # Your resume (AI reads this)
├── start.bat              # Windows one-click start
└── src/main/
    ├── java/
    │   ├── zhilian/          # Core (4 files)
    │   ├── ai/               # AI matching (3 files)
    │   └── utils/            # Utilities (7 files)
    └── resources/
        ├── config.yaml           # Search config
        └── chromedriver.exe      # Browser driver
```

## Key Files

### AiService.java

`shouldApplyZhiLian(jobName, company, salary, location, jobDetail)`:
- Reads resume from `resume.md` (cached after first load)
- Sends prompt with resume + job info to AI API
- Returns `{match: true/false, reason: "reason"}`
- Rules: must be internship, direction match, Tianhe priority, no high requirements, no low education

### ZhiLian.java

1. Start Chrome, login via QR code
2. For each keyword: collect up to 200 job URLs (Phase 1)
3. Visit each job detail, AI judge, apply if match (Phase 2)
4. Close detail tab after each job

### config.yaml

- `cityCode`: City name in Chinese
- `salary`: Range like "0,5000" or "none"
- `keywords`: Array of search terms

## Customization

### Change Target City/Area

Edit the prompt in `AiService.java` `shouldApplyZhiLian` method.

### Change Matching Rules

Edit the prompt rules section in `AiService.java`.

### Add Other Platforms

This project focuses on ZhiLian only. For other platforms, use the original get_jobs project.

## Known Issues & Limitations

1. **Anti-bot detection**: ZhiLian may show CAPTCHA verification. Using a dedicated Chrome profile helps but doesn't fully solve it. If blocked, wait a few hours and try again.
2. **ChromeDriver version**: Must exactly match Chrome version. Auto-download not supported.
3. **JDK 17 only**: JDK 21+ causes compilation errors due to API changes.
4. **Chinese encoding**: Log output may show garbled text on Windows terminals. This is a display issue only.
5. **Rate limits**: ~100 applications/day per ZhiLian account. The AI judgment delay helps but doesn't guarantee avoidance.
6. **Resume quality**: Matching accuracy depends heavily on resume.md content quality. Fill it thoroughly.
7. **No GUI config**: All configuration is file-based. No graphical interface.
8. **Windows only tested**: start.bat is Windows-specific. macOS/Linux users need to run java command directly.
9. **Single-threaded**: Processes one job at a time. Not optimized for speed.
10. **ZhiLian DOM changes**: If ZhiLian updates their page structure, selectors will break. Requires manual fix.

## Credits

Based on [get_jobs](https://github.com/loks666/get_jobs) by loks666. This fork focuses exclusively on ZhiLian with AI-powered job matching.
