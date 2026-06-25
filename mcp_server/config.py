"""配置管理：读写 config.yaml / preferences.md / resume.md / commute.json"""

import os
import json
import yaml
from pathlib import Path

PROJECT_ROOT = Path(__file__).parent.parent
CONFIG_FILE = PROJECT_ROOT / "config.yaml"
PREFERENCES_FILE = PROJECT_ROOT / "preferences.md"
RESUME_FILE = PROJECT_ROOT / "resume.md"
COMMUTE_FILE = PROJECT_ROOT / "commute.json"
HISTORY_FILE = PROJECT_ROOT / "data" / "history.json"


def ensure_dirs():
    (PROJECT_ROOT / "data").mkdir(exist_ok=True)
    (PROJECT_ROOT / "profile").mkdir(exist_ok=True)


def load_config() -> dict:
    if CONFIG_FILE.exists():
        with open(CONFIG_FILE, "r", encoding="utf-8") as f:
            return yaml.safe_load(f) or {}
    return {}


def save_config(config: dict):
    with open(CONFIG_FILE, "w", encoding="utf-8") as f:
        yaml.dump(config, f, allow_unicode=True, default_flow_style=False)


def load_preferences() -> str:
    if PREFERENCES_FILE.exists():
        return PREFERENCES_FILE.read_text(encoding="utf-8")
    return ""


def save_preferences(content: str):
    PREFERENCES_FILE.write_text(content, encoding="utf-8")


def load_resume() -> str:
    if RESUME_FILE.exists():
        return RESUME_FILE.read_text(encoding="utf-8")
    return ""


def save_resume(content: str):
    RESUME_FILE.write_text(content, encoding="utf-8")


def load_commute() -> dict:
    if COMMUTE_FILE.exists():
        with open(COMMUTE_FILE, "r", encoding="utf-8") as f:
            return json.load(f)
    return {}


def save_commute(data: dict):
    ensure_dirs()
    with open(COMMUTE_FILE, "w", encoding="utf-8") as f:
        json.dump(data, f, ensure_ascii=False, indent=2)


def load_history() -> list:
    if HISTORY_FILE.exists():
        with open(HISTORY_FILE, "r", encoding="utf-8") as f:
            return json.load(f)
    return []


def save_history(history: list):
    ensure_dirs()
    with open(HISTORY_FILE, "w", encoding="utf-8") as f:
        json.dump(history, f, ensure_ascii=False, indent=2)


def add_to_history(job: dict):
    history = load_history()
    history.append(job)
    save_history(history)


def is_in_commute_area(location: str) -> bool:
    """检查地址是否在通勤范围内"""
    commute = load_commute()
    if not commute:
        return True  # 未配置通勤范围时默认通过

    if not location or not location.strip():
        return commute.get("unknown_pass", True)

    excluded = commute.get("excluded", [])
    for ex in excluded:
        if ex in location:
            return False

    allowed = commute.get("allowed", [])
    for area_rule in allowed:
        area = area_rule.get("area", "")
        if area not in location:
            continue
        area_type = area_rule.get("type", "full")
        if area_type == "full":
            return True
        subareas = area_rule.get("subareas", [])
        for sub in subareas:
            if sub in location:
                return True
        # line type: check if station names match
        if area_type == "line":
            line_from = area_rule.get("from", "")
            line_to = area_rule.get("to", "")
            if line_from in location or line_to in location:
                return True

    return False
