# zhaopin-auto

AI-powered automated job application tool for ZhiLian (智联招聘).

> Based on [get_jobs](https://github.com/loks666/get_jobs) by loks666. This fork focuses exclusively on ZhiLian with AI-powered job matching.

## Features

- **AI Job Matching**: Reads your resume and job details, uses AI to decide whether to apply
- **Two-Phase Strategy**: Fast collect up to 200 jobs, then AI-judge each one
- **Smart Filtering**: Internship-only, education filter, district priority
- **Configurable**: Any OpenAI-compatible API (DeepSeek, Xiaomi MiMo, OpenAI, etc.)
- **Resume-Driven**: Edit `resume.md` with your resume, AI reads it for matching

## Quick Start

```bash
# 1. Clone
git clone <this-repo> zhaopin-auto
cd zhaopin-auto

# 2. Configure API
cp .env_template .env
# Edit .env with your API key

# 3. Fill resume
# Edit resume.md with your resume content

# 4. Configure search
# Edit src/main/resources/config.yaml (city, keywords, salary)

# 5. Place ChromeDriver matching your Chrome version
# Download: https://googlechromelabs.github.io/chrome-for-testing/

# 6. Build & Run
mvn clean package -DskipTests
start.bat
```

## How It Works

```
Search ZhiLian → Collect 200 job URLs → For each job:
  ├── Open detail page
  ├── Read job description
  ├── AI compares with your resume
  ├── Match? → Apply
  └── No match? → Skip
```

## Known Issues

- **Anti-bot**: ZhiLian may trigger CAPTCHA. Wait a few hours if blocked.
- **ChromeDriver**: Must manually match Chrome version.
- **JDK 17 required**: JDK 21+ incompatible.
- **Windows only**: `start.bat` is Windows-specific.
- **No GUI**: All config is file-based.
- **DOM fragility**: If ZhiLian changes their HTML, selectors break.

## Configuration

### `.env` - AI API

```env
BASE_URL=https://api.deepseek.com
API_KEY=your-key
MODEL=deepseek-chat
```

### `config.yaml` - Search

```yaml
zhilian:
  cityCode: "广州"
  salary: "0,5000"
  keywords:
    - "供应链实习"
    - "数据分析"
```

### `resume.md` - Your Resume

Fill with your actual resume content. AI reads this for matching.

## Requirements

- JDK 17
- Maven 3.6+
- Google Chrome + matching ChromeDriver

## Credits

- Original project: [get_jobs](https://github.com/loks666/get_jobs) by loks666
- This fork: Focused on ZhiLian + AI matching

## License

MIT
