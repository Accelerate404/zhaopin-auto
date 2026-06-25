# get_jobs — 智联招聘 AI Agent

> 本文档是给 AI Agent 读的。当你看到这个文件时，说明用户希望你帮他在智联招聘上找工作。

## 你能做什么

你有以下 MCP 工具可以调用：

### 搜索与投递
- `zhilian_search(keywords, city, salary, max_results)` — 搜索岗位
- `zhilian_get_detail(url)` — 获取岗位详情
- `zhilian_apply(url)` — 投递岗位

### 会话管理
- `zhilian_check_session()` — 检查登录状态
- `zhilian_login()` — 触发扫码登录（会打开浏览器）

### 配置管理
- `get_config()` / `update_config(...)` — 读写搜索配置
- `get_preferences()` / `save_preferences_file(...)` — 读写偏好画像
- `get_resume()` / `save_resume_file(...)` — 读写简历
- `get_commute()` / `save_commute_file(...)` — 读写通勤范围
- `get_history()` — 查看投递历史

## 工作流程

### 第一步：了解用户需求

用对话方式收集以下信息（如果用户没说清楚）：

1. **城市**：在哪里找工作？
2. **方向**：想找什么方向的实习/工作？（AI、数据、供应链、产品等）
3. **薪资**：期望薪资范围？
4. **通勤**：哪些区域可以接受？哪些太远不想去？

### 第二步：配置

根据用户回答，调用工具生成配置：

1. 调用 `update_config()` 设置关键词、城市、薪资
2. 调用 `save_commute_file()` 设置通勤范围
3. 如果用户愿意，帮生成 `save_preferences_file()` 和 `save_resume_file()`

### 第三步：登录

1. 调用 `zhilian_check_session()` 检查登录状态
2. 如果未登录，调用 `zhilian_login()` 触发扫码
3. 提示用户扫码

### 第四步：搜索与投递

1. 调用 `zhilian_search()` 获取岗位列表
2. **你来判断**哪些岗位匹配用户需求（这是你的能力，不需要工具）
3. 对匹配的岗位调用 `zhilian_get_detail()` 获取详情
4. 确认后调用 `zhilian_apply()` 投递

### 第五步：反馈

- 汇报投递结果
- 如果有被拒的岗位，分析原因并调整搜索方向
- 询问用户是否继续搜索更多岗位

## 判断标准

投递前你需要综合判断：

1. **岗位方向**：是否属于用户目标方向
2. **技术匹配**：岗位要求的技术栈用户是否具备
3. **公司质量**：是否为正规公司（警惕销售型小公司、皮包公司）
4. **通勤距离**：是否在用户可接受范围内
5. **岗位含金量**：工作内容是否有实际价值

## 注意事项

- 每次搜索会打开浏览器，完成后自动关闭
- 扫码登录需要用户手动操作，你无法代替
- 智联有每日投递上限（约100个），注意节奏
- 遇到反爬验证（验证码），提示用户手动处理
- 所有投递记录保存在 `data/history.json`，可以查看历史

## 配置文件说明

| 文件 | 用途 | 格式 |
|------|------|------|
| `config.yaml` | 搜索关键词、城市、薪资 | YAML |
| `preferences.md` | 求职偏好画像 | Markdown |
| `resume.md` | 求职者简历 | Markdown |
| `commute.json` | 通勤范围（结构化） | JSON |
| `data/history.json` | 投递历史 | JSON |
| `profile/` | 浏览器数据（保持登录） | 目录 |

## 通勤范围配置格式 (commute.json)

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
