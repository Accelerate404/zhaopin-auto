# zhaopin-auto

基于 MCP 的智联招聘 AI 自动投递 Agent。

> 基于 [get_jobs](https://github.com/loks666/get_jobs) by loks666 的 fork，重构为 MCP Server 架构，支持通过 AI 对话完成岗位搜索、评估和投递。

## 功能

- **MCP Agent 架构**：通过 MCP 协议暴露工具，任何支持 MCP 的 AI（Claude Desktop、Cursor 等）均可调用
- **AI 岗位匹配**：读取简历和偏好画像，由 AI 判断是否投递
- **两阶段策略**：先快速收集岗位，再批量 AI 评估（5个一批，减少 API 调用）
- **地址预过滤**：根据通勤范围快速排除不在目标区域的岗位
- **关键词过滤**：包含电话销售、地推等关键词的岗位直接跳过
- **灵活配置**：支持任意 OpenAI 兼容 API（DeepSeek、小米 MiMo、OpenAI 等）

## 快速开始

```bash
# 1. 克隆
git clone https://github.com/YOUR_USERNAME/zhaopin-auto.git
cd zhaopin-auto

# 2. 安装依赖
pip install -r requirements.txt

# 3. 配置 API
cp .env_template .env
# 编辑 .env 填入你的 API Key

# 4. 填写简历
# 编辑 resume.md 填入你的简历内容

# 5. 配置搜索条件
# 编辑 config.yaml（城市、关键词、薪资）

# 6. 配置 AI 客户端
# 将以下配置添加到你的 AI 客户端配置文件中
```

### MCP 客户端配置

**Claude Desktop** (`%APPDATA%\Claude\claude_desktop_config.json`)：

```json
{
  "mcpServers": {
    "get-jobs": {
      "type": "stdio",
      "command": "python",
      "args": ["path/to/mcp_server/server.py"]
    }
  }
}
```

**Cursor** (`.cursor/mcp.json`)：

```json
{
  "mcpServers": {
    "get-jobs": {
      "type": "stdio",
      "command": "python",
      "args": ["path/to/mcp_server/server.py"]
    }
  }
}
```

## MCP 工具列表

| 工具 | 说明 |
|------|------|
| `zhilian_search(keywords, city, salary, max_results)` | 搜索岗位 |
| `zhilian_get_detail(url)` | 获取岗位详情 |
| `zhilian_apply(url)` | 投递岗位 |
| `zhilian_check_session()` | 检查登录状态 |
| `zhilian_login()` | 触发扫码登录 |
| `get_config()` / `update_config(...)` | 读写搜索配置 |
| `get_preferences()` / `save_preferences_file(...)` | 读写偏好画像 |
| `get_resume()` / `save_resume_file(...)` | 读写简历 |
| `get_commute()` / `save_commute_file(...)` | 读写通勤范围 |
| `get_history()` | 查看投递历史 |

## 工作流程

```
用户对 AI 说："帮我投递广州的 AI 实习岗位"
  │
  ├── AI 调用 zhilian_search() 搜索岗位
  ├── AI 根据简历和偏好画像判断匹配度
  ├── AI 调用 zhilian_apply() 投递匹配岗位
  └── AI 汇报投递结果
```

## 配置文件说明

| 文件 | 用途 | 格式 |
|------|------|------|
| `.env` | AI API 配置（BASE_URL、API_KEY、MODEL） | dotenv |
| `config.yaml` | 搜索关键词、城市、薪资 | YAML |
| `resume.md` | 求职者简历 | Markdown |
| `preferences.md` | 求职者偏好画像 | Markdown |
| `commute.json` | 通勤范围（结构化） | JSON |
| `data/history.json` | 投递历史 | JSON |

### 通勤范围配置格式 (commute.json)

```json
{
  "city": "广州",
  "allowed": [
    {"area": "天河区", "type": "full"},
    {"area": "越秀区", "type": "partial", "subareas": ["黄花岗","区庄","淘金","东山口"]},
    {"area": "黄埔区", "type": "line", "line": "6号线", "from": "黄陂", "to": "金峰"}
  ],
  "excluded": ["海珠","荔湾","番禺","花都","从化","佛山"],
  "unknown_pass": true
}
```

- `type: "full"` — 该区域全域可投递
- `type: "partial"` — 只有指定子区域可投递
- `type: "line"` — 地铁沿线，from/to 之间的站点可投递
- `unknown_pass` — 地址为空或不明时是否通过

## 环境要求

- Python 3.10+
- DrissionPage 4.0+
- MCP SDK 1.0+

## 已知问题

- **反爬机制**：智联详情页有腾讯云 EdgeOne 验证，自动访问会被拦截，需手动处理
- **仅智联招聘**：目前仅支持智联招聘平台
- **仅 Windows**：浏览器自动化部分依赖 Windows 环境

## 致谢

- 原项目：[get_jobs](https://github.com/loks666/get_jobs) by loks666
- 本 fork：重构为 MCP Server 架构 + AI Agent 工作流

## 许可证

MIT
