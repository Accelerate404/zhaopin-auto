"""get_jobs MCP Server — 智联招聘 AI Agent"""

import sys
import io
from pathlib import Path

# Windows UTF-8 编码修复
if sys.platform == "win32":
    sys.stdout = io.TextIOWrapper(sys.stdout.buffer, encoding="utf-8")
    sys.stderr = io.TextIOWrapper(sys.stderr.buffer, encoding="utf-8")

from mcp.server.fastmcp import FastMCP

# 确保项目根目录在 path 中
PROJECT_ROOT = Path(__file__).parent.parent
sys.path.insert(0, str(Path(__file__).parent))

from zhilian import search_jobs, get_job_detail, apply_job
from session import get_browser, ensure_login, login
from config import (
    load_config, save_config,
    load_preferences, save_preferences,
    load_resume, save_resume,
    load_commute, save_commute,
    load_history, is_in_commute_area,
)

mcp = FastMCP("get-jobs", instructions="""智联招聘 AI Agent。帮用户搜索、评估和投递智联招聘岗位。
用户对话时，先了解需求（城市、方向、薪资），再调用工具执行。
所有工具均为同步调用，会打开浏览器操作，完成后自动关闭。""")


# ═══════════════════════════════════════
# 搜索与投递工具
# ═══════════════════════════════════════

@mcp.tool()
def zhilian_search(keywords: list, city: str = "广州", salary: str = "",
                   max_results: int = 30) -> dict:
    """搜索智联招聘岗位。
    Args:
        keywords: 搜索关键词列表，如 ["AI实习", "数据分析实习"]
        city: 城市名称，默认广州
        salary: 薪资范围，如 "0,5000"，空字符串不限
        max_results: 最大返回数量，默认30
    Returns:
        包含岗位列表的字典，每个岗位有 name/company/salary/location/url
    """
    return search_jobs(keywords=keywords, city=city, salary=salary,
                       max_results=max_results)


@mcp.tool()
def zhilian_get_detail(url: str) -> dict:
    """获取智联招聘岗位详情。
    Args:
        url: 岗位详情页URL
    Returns:
        岗位详细信息，包括名称、公司、薪资、描述等
    """
    return get_job_detail(url)


@mcp.tool()
def zhilian_apply(url: str) -> dict:
    """投递智联招聘岗位。
    Args:
        url: 岗位详情页URL
    Returns:
        投递结果，success=True/False 及原因
    """
    return apply_job(url)


# ═══════════════════════════════════════
# 会话管理工具
# ═══════════════════════════════════════

@mcp.tool()
def zhilian_check_session() -> dict:
    """检查智联招聘登录状态。Returns: 登录状态信息"""
    page = get_browser()
    try:
        logged_in = ensure_login(page)
        return {"logged_in": logged_in, "message": "已登录" if logged_in else "未登录"}
    finally:
        page.quit()


@mcp.tool()
def zhilian_login() -> dict:
    """触发智联招聘扫码登录。打开浏览器显示二维码，等待用户扫码。Returns: 登录结果"""
    page = get_browser()
    try:
        success = login(page)
        return {"success": success, "message": "登录成功" if success else "登录失败或超时"}
    finally:
        page.quit()


# ═══════════════════════════════════════
# 配置管理工具
# ═══════════════════════════════════════

@mcp.tool()
def get_config() -> dict:
    """读取当前搜索配置。Returns: config.yaml 内容"""
    return load_config() or {"message": "未配置"}


@mcp.tool()
def update_config(keywords: list = None, city: str = None,
                  salary: str = None) -> dict:
    """更新搜索配置。
    Args:
        keywords: 搜索关键词列表
        city: 城市名称
        salary: 薪资范围
    Returns: 更新后的配置
    """
    config = load_config()
    zhilian = config.get("zhilian", {})
    if keywords is not None:
        zhilian["keywords"] = keywords
    if city is not None:
        zhilian["cityCode"] = city
    if salary is not None:
        zhilian["salary"] = salary
    config["zhilian"] = zhilian
    save_config(config)
    return config


@mcp.tool()
def get_preferences() -> str:
    """读取求职者偏好画像。Returns: preferences.md 内容"""
    content = load_preferences()
    return content if content else "未配置偏好画像"


@mcp.tool()
def save_preferences_file(content: str) -> dict:
    """保存求职者偏好画像。
    Args:
        content: Markdown 格式的偏好画像内容
    """
    save_preferences(content)
    return {"success": True, "message": "偏好画像已保存"}


@mcp.tool()
def get_resume() -> str:
    """读取求职者简历。Returns: resume.md 内容"""
    content = load_resume()
    return content if content else "未配置简历"


@mcp.tool()
def save_resume_file(content: str) -> dict:
    """保存求职者简历。
    Args:
        content: Markdown 格式的简历内容
    """
    save_resume(content)
    return {"success": True, "message": "简历已保存"}


@mcp.tool()
def get_commute() -> dict:
    """读取通勤范围配置。Returns: commute.json 内容"""
    return load_commute() or {"message": "未配置通勤范围"}


@mcp.tool()
def save_commute_file(city: str, allowed: list, excluded: list,
                      unknown_pass: bool = True) -> dict:
    """保存通勤范围配置。
    Args:
        city: 城市名称
        allowed: 可投递区域列表，每个元素是 dict，如 {"area":"天河区","type":"full"}
        excluded: 排除区域列表，如 ["海珠","荔湾","番禺"]
        unknown_pass: 地址不明是否通过，默认True
    """
    data = {"city": city, "allowed": allowed, "excluded": excluded,
            "unknown_pass": unknown_pass}
    save_commute(data)
    return {"success": True, "message": "通勤范围已保存"}


@mcp.tool()
def get_history() -> dict:
    """查看投递历史。Returns: 投递记录列表"""
    history = load_history()
    return {"total": len(history), "records": history}


if __name__ == "__main__":
    mcp.run()
