"""智联招聘自动化：搜索、查看详情、投递"""

import time
import random
from DrissionPage import ChromiumPage
from session import ensure_login, get_browser
from config import is_in_commute_area, add_to_history, load_commute

SEARCH_URL = "https://sou.zhaopin.com/?"


def _sleep(min_s=1, max_s=3):
    time.sleep(random.uniform(min_s, max_s))


def search_jobs(keywords: list, city: str = "广州", salary: str = "",
                max_results: int = 50) -> list:
    """搜索岗位，返回岗位列表"""
    page = get_browser()
    try:
        if not ensure_login(page):
            return {"error": "登录失败，请重试"}

        results = []
        for keyword in keywords:
            if len(results) >= max_results:
                break
            page_num = 1
            while len(results) < max_results:
                url = f"{SEARCH_URL}jl={city}&kw={keyword}"
                if salary:
                    url += f"&sl={salary}"
                url += f"&p={page_num}"

                page.get(url)
                _sleep(2, 4)

                # 检查是否跳转到登录页
                if "passport.zhaopin.com" in page.url:
                    if not ensure_login(page):
                        return {"error": "登录过期，重新登录失败"}
                    page.get(url)
                    _sleep(2, 4)

                cards = page.eles("css:.joblist-box__item.clearfix")
                if not cards:
                    break

                for card in cards:
                    if len(results) >= max_results:
                        break
                    try:
                        job = _parse_job_card(card)
                        if job and job["url"]:
                            # 按城市过滤（ZhiLian URL参数不可靠，后置过滤）
                            job_loc = job.get("location", "")
                            if city and job_loc and city not in job_loc:
                                continue
                            # 去重
                            if not any(j["url"] == job["url"] for j in results):
                                results.append(job)
                    except Exception:
                        continue

                page_num += 1
                _sleep(1, 2)

        return {"total": len(results), "jobs": results}
    finally:
        page.quit()


def _parse_job_card(card) -> dict:
    """解析搜索结果卡片"""
    job = {"name": "", "company": "", "salary": "", "location": "", "url": ""}

    try:
        name_el = card.ele("css:.jobinfo__name", timeout=2)
        if name_el:
            job["name"] = name_el.text.strip()
            job["url"] = name_el.attr("href") or ""
    except Exception:
        pass

    try:
        company_el = card.ele("css:.companyinfo__name", timeout=2)
        if company_el:
            job["company"] = company_el.text.strip()
    except Exception:
        pass

    try:
        tags = card.eles("css:*[class*='joblist-box__item-tag']")
        for tag in tags:
            t = tag.text.strip()
            if "元" in t or "薪" in t:
                job["salary"] = t
    except Exception:
        pass

    try:
        infos = card.eles("css:*[class*='jobinfo__other-info-item']")
        if infos:
            job["location"] = infos[0].text.strip()
    except Exception:
        pass

    return job


def get_job_detail(url: str) -> dict:
    """获取岗位详情（从搜索结果页提取，避免触发反爬验证）"""
    # 详情页有腾讯云EdgeOne反爬验证，直接访问会被拦截
    # 返回基本信息，由AI根据搜索结果判断
    detail = {"url": url, "name": "", "company": "", "salary": "",
              "location": "", "description": "详情页有安全验证，需手动访问查看完整描述",
              "note": "请让用户提供岗位详情截图或描述，或直接根据搜索结果判断"}
    return detail


def apply_job(url: str) -> dict:
    """投递岗位"""
    page = get_browser()
    try:
        if not ensure_login(page):
            return {"success": False, "error": "登录失败"}

        page.get(url)
        _sleep(2, 3)

        if "passport.zhaopin.com" in page.url:
            return {"success": False, "error": "登录过期"}

        # 查找投递按钮
        apply_btn = _find_apply_button(page)
        if not apply_btn:
            return {"success": False, "error": "未找到投递按钮"}

        btn_text = apply_btn.text.strip()
        if "已投递" in btn_text or "已申请" in btn_text:
            return {"success": False, "error": "已投递过该岗位"}

        apply_btn.click()
        _sleep(2, 3)

        # 检查是否达到上限
        try:
            limit_el = page.ele("css:.a-job-apply-workflow", timeout=3)
            if limit_el and "达到上限" in limit_el.text:
                return {"success": False, "error": "今日投递已达上限"}
        except Exception:
            pass

        # 检查弹窗
        try:
            dialog = page.ele("css:.dialog, .modal, .a-job-apply-workflow", timeout=2)
            if dialog:
                dialog_text = dialog.text
                if "上限" in dialog_text or "已达" in dialog_text:
                    return {"success": False, "error": "今日投递已达上限"}
                # 尝试关闭弹窗
                try:
                    close_btn = dialog.ele("css:button, .close", timeout=2)
                    if close_btn:
                        close_btn.click()
                except Exception:
                    pass
        except Exception:
            pass

        # 记录投递历史
        job_info = {"url": url, "applied_at": time.strftime("%Y-%m-%d %H:%M:%S")}
        try:
            title_el = page.ele("css:.summary-plane__title", timeout=2)
            if title_el:
                job_info["name"] = title_el.text.strip()
        except Exception:
            pass
        add_to_history(job_info)

        return {"success": True, "message": "投递成功"}
    finally:
        page.quit()


def _find_apply_button(page: ChromiumPage):
    """查找投递按钮"""
    selectors = [
        "css:button.apply-btn",
        "css:button[class*='apply']",
        "css:.apply-btn",
        "css:.job-detail__apply",
        "css:button.btn-apply",
        "css:.btn--primary",
    ]

    for sel in selectors:
        try:
            btns = page.eles(sel)
            for btn in btns:
                text = btn.text.strip()
                if any(kw in text for kw in ["投递", "申请", "立即", "马上"]):
                    return btn
        except Exception:
            continue

    # 备用：扫描所有 button
    try:
        all_btns = page.eles("tag:button")
        for btn in all_btns:
            text = btn.text.strip()
            if any(kw in text for kw in ["投递", "申请职位", "立即投递"]) and "已投递" not in text:
                return btn
    except Exception:
        pass

    return None
