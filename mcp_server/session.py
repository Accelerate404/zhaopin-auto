"""会话管理：Cookie 持久化、登录状态检测、自动重登"""

import time
from pathlib import Path
from DrissionPage import ChromiumPage, ChromiumOptions

PROJECT_ROOT = Path(__file__).parent.parent
PROFILE_DIR = PROJECT_ROOT / "profile"
COOKIE_FILE = PROJECT_ROOT / "data" / "cookies.json"

LOGIN_URL = "https://passport.zhaopin.com/login"
HOME_URL = "https://sou.zhaopin.com/?"


def get_browser(headless=False) -> ChromiumPage:
    """获取浏览器实例，使用持久化 profile 保持登录状态"""
    PROFILE_DIR.mkdir(exist_ok=True)
    co = ChromiumOptions()
    co.set_argument("--no-first-run")
    co.set_argument("--disable-blink-features=AutomationControlled")
    if headless:
        co.headless()
    co.set_user_data_path(str(PROFILE_DIR))
    page = ChromiumPage(co)
    return page


def check_session(page: ChromiumPage) -> bool:
    """检查当前是否已登录"""
    url = page.url
    if "passport.zhaopin.com" in url or "login" in url:
        return False
    # 检查页面是否有用户信息
    try:
        el = page.ele("css:.zp-main__personal", timeout=3)
        if el:
            return True
    except Exception:
        pass
    # 尝试访问首页看是否跳转登录
    try:
        page.get(HOME_URL)
        time.sleep(2)
        return "passport.zhaopin.com" not in page.url
    except Exception:
        return False


def login(page: ChromiumPage, timeout=120) -> bool:
    """触发扫码登录，等待用户扫码"""
    page.get(LOGIN_URL)
    time.sleep(2)

    # 尝试点击二维码登录
    try:
        qr_btn = page.ele("css:.zppp-panel-normal-bar__img", timeout=5)
        if qr_btn:
            qr_btn.click()
    except Exception:
        pass

    print("请扫描二维码登录智联招聘...")
    print(f"等待{timeout}秒超时...")

    start = time.time()
    while time.time() - start < timeout:
        try:
            el = page.ele("css:.zp-main__personal", timeout=3)
            if el:
                print("登录成功！")
                save_cookies(page)
                return True
        except Exception:
            pass
        time.sleep(2)

    print("登录超时")
    return False


def ensure_login(page: ChromiumPage) -> bool:
    """确保已登录，未登录则触发登录"""
    if check_session(page):
        return True
    print("未登录或Cookie过期，需要重新登录")
    return login(page)


def save_cookies(page: ChromiumPage):
    """保存 cookies"""
    try:
        cookies = page.cookies()
        COOKIE_FILE.parent.mkdir(exist_ok=True)
        import json
        with open(COOKIE_FILE, "w", encoding="utf-8") as f:
            json.dump(cookies, f, ensure_ascii=False, indent=2)
        print(f"Cookies已保存")
    except Exception as e:
        print(f"保存Cookie失败: {e}")


def load_cookies(page: ChromiumPage) -> bool:
    """加载已保存的 cookies"""
    import json
    if not COOKIE_FILE.exists():
        return False
    try:
        with open(COOKIE_FILE, "r", encoding="utf-8") as f:
            cookies = json.load(f)
        for cookie in cookies:
            page.set.cookies(cookie)
        return True
    except Exception:
        return False
