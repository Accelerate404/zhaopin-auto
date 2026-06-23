# zhaopin-auto

基于 AI 的智联招聘自动投递工具。

> 基于 [get_jobs](https://github.com/loks666/get_jobs) by loks666 的 fork，专注于智联招聘 + AI 岗位匹配。

## 功能

- **AI 岗位匹配**：读取简历和岗位详情，由 AI 判断是否投递
- **两阶段策略**：先快速收集最多 200 个岗位，再逐个 AI 判断
- **地址预过滤**：根据通勤范围快速排除不在目标区域的岗位，大幅减少 AI 调用
- **关键词过滤**：包含电话销售、地推等关键词的岗位直接跳过
- **灵活配置**：支持任意 OpenAI 兼容 API（DeepSeek、小米 MiMo、OpenAI 等）
- **简历驱动**：编辑 `resume.md` 填入简历，AI 据此匹配

## 快速开始

```bash
# 1. 克隆
git clone https://github.com/Accelerate404/zhaopin-auto.git
cd zhaopin-auto

# 2. 配置 API
cp .env_template .env
# 编辑 .env 填入你的 API Key

# 3. 填写简历
# 编辑 resume.md 填入你的简历内容

# 4. 配置搜索条件
# 编辑 src/main/resources/config.yaml（城市、关键词、薪资）

# 5. 自定义通勤范围（可选）
# 编辑 src/main/java/zhilian/ZhiLian.java 中的 ALLOWED_AREAS / EXCLUDED_KEYWORDS

# 6. 放置 ChromeDriver
# 下载与 Chrome 版本匹配的 chromedriver：https://googlechromelabs.github.io/chrome-for-testing/

# 7. 构建 & 运行
mvn clean package -DskipTests
start.bat
```

## 工作流程

```
搜索智联招聘 → 收集 200 个岗位链接 → 逐个处理：
  ├── 地址预过滤（不在通勤范围 → 直接跳过）
  ├── 打开岗位详情页
  ├── 关键词过滤（电话销售/地推等 → 直接跳过）
  ├── AI 对比简历判断匹配度
  ├── 匹配 → 投递
  └── 不匹配 → 跳过
```

## 配置说明

### `.env` - AI API

```env
BASE_URL=https://api.deepseek.com
API_KEY=your-api-key-here
MODEL=deepseek-chat
```

支持任何 OpenAI 兼容 API（小米 MiMo、DeepSeek、OpenAI 等）。

### `config.yaml` - 搜索条件

```yaml
zhilian:
  cityCode: "广州"
  salary: "0,5000"
  keywords:
    - "供应链实习"
    - "数据分析实习"
    - "电商运营实习"
```

### `resume.md` - 个人简历

填入你的实际简历内容，AI 读取此文件进行匹配。

### 通勤范围自定义

编辑 `src/main/java/zhilian/ZhiLian.java` 中的三个列表：

- `ALLOWED_AREAS`：全区可投递的区域（如天河、越秀）
- `ALLOWED_SUB_AREAS`：部分区域可投递的具体片区
- `EXCLUDED_KEYWORDS`：明确排除的区域

## 环境要求

- JDK 17
- Maven 3.6+
- Google Chrome + 匹配版本的 ChromeDriver

## 已知问题

- **反爬机制**：智联可能触发验证码，被封后等几小时再试
- **ChromeDriver**：需手动匹配 Chrome 版本
- **仅 JDK 17**：JDK 21+ 不兼容
- **仅 Windows**：`start.bat` 为 Windows 专用
- **无界面**：所有配置基于文件
- **DOM 依赖**：智联改版后选择器可能失效

## 致谢

- 原项目：[get_jobs](https://github.com/loks666/get_jobs) by loks666
- 本 fork：专注智联招聘 + AI 匹配

## 许可证

MIT
