package zhilian;

import org.openqa.selenium.*;
import org.openqa.selenium.JavascriptExecutor;
import org.openqa.selenium.support.ui.ExpectedConditions;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import ai.AiService;
import utils.Job;
import utils.JobUtils;
import utils.SeleniumUtil;

import java.util.ArrayList;
import java.util.Set;
import java.util.Date;
import java.util.List;
import java.util.stream.Collectors;
import java.util.Arrays;

import static utils.Bot.sendMessageByTime;
import static utils.Constant.*;
import static utils.JobUtils.formatDuration;

/**
 * @author loks666
 */
public class ZhiLian {
    static {
        System.setProperty("log.name", "zhilian");
    }
    
    private static final Logger log = LoggerFactory.getLogger(ZhiLian.class);
    static String loginUrl = "https://passport.zhaopin.com/login";
    static String homeUrl = "https://sou.zhaopin.com/?";
    static boolean isLimit = false;
    static int maxPage = 500;
    static ZhilianConfig config = ZhilianConfig.init();
    static List<Job> resultList = new ArrayList<>();
    static Date startDate;

    public static void main(String[] args) {
        SeleniumUtil.initDriver();
        startDate = new Date();
        login();
        config.getKeywords().forEach(keyword -> {
            if (isLimit) {
                return;
            }
            CHROME_DRIVER.get(getSearchUrl(keyword, 1));
            // Cookie过期检测：如果被重定向到登录页，自动重新登录
            if (isSessionExpired()) {
                log.info("Cookie已过期，重新登录...");
                reLogin();
                CHROME_DRIVER.get(getSearchUrl(keyword, 1));
            }
            submitJobs(keyword);

        });
        log.info(resultList.isEmpty() ? "未投递新的岗位.." : "新投递公司如下:\n{}", resultList.stream().map(Object::toString).collect(Collectors.joining("\n")));
        printResult();
    }

    private static void printResult() {
        String message = String.format("\n智联招聘投递完成，共投递%d个岗位，用时%s", resultList.size(), formatDuration(startDate, new Date()));
        log.info(message);
        sendMessageByTime(message);
        resultList.clear();
        CHROME_DRIVER.close();
        CHROME_DRIVER.quit();
        
        try {
            Thread.sleep(1000);
            ch.qos.logback.classic.LoggerContext loggerContext = (ch.qos.logback.classic.LoggerContext) org.slf4j.LoggerFactory.getILoggerFactory();
            loggerContext.stop();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    private static String getSearchUrl(String keyword, int page) {
        return homeUrl +
                JobUtils.appendParam("jl", config.getCityCode()) +
                JobUtils.appendParam("kw", keyword) +
                JobUtils.appendParam("sl", config.getSalary()) +
                "&p=" + page;
    }

    private static final int MAX_COLLECT = 200;
    private static final int BATCH_SIZE = 5;

    private static void submitJobs(String keyword) {
        if (isLimit) return;
        try {
            setMaxPages();

            // Phase 1: Fast collect up to MAX_COLLECT job URLs from list pages
            List<String[]> allJobs = new ArrayList<>();
            int collected = 0;
            for (int page = 1; page <= maxPage && collected < MAX_COLLECT; page++) {
                CHROME_DRIVER.get(getSearchUrl(keyword, page));
                SeleniumUtil.sleep(3);
                log.info("[Collect] keyword [{}] page {} (collected so far: {})", keyword, page, collected);
                // Debug: log first card info
                if (page == 1) {
                    try {
                        var firstCards = CHROME_DRIVER.findElements(By.xpath("//div[contains(@class, 'joblist-box__item')]"));
                        log.info("[Debug] joblist-box__item count: {}", firstCards.size());
                        if (!firstCards.isEmpty()) {
                            String fcText = firstCards.get(0).getText().substring(0, Math.min(100, firstCards.get(0).getText().length()));
                            log.info("[Debug] first card text: {}", fcText);
                            var nameLink = firstCards.get(0).findElements(By.xpath(".//a[contains(@class, 'jobinfo__name')]"));
                            log.info("[Debug] jobinfo__name links: {}", nameLink.size());
                        }
                    } catch (Exception e) { log.info("[Debug] error: {}", e.getMessage()); }
                }

                List<WebElement> jobCards;
                try {
                    WAIT.until(ExpectedConditions.presenceOfElementLocated(By.xpath("//div[contains(@class, 'joblist-box__item') and contains(@class, 'clearfix')]")));
                    jobCards = CHROME_DRIVER.findElements(By.xpath("//div[contains(@class, 'joblist-box__item') and contains(@class, 'clearfix')]"));
                } catch (Exception e) {
                    log.info("[Collect] No cards on page {}, stop", page);
                    break;
                }
                if (jobCards.isEmpty()) break;

                for (WebElement card : jobCards) {
                    if (collected >= MAX_COLLECT) break;
                    try {
                        String jName = safeGetText(card, ".//a[contains(@class, 'jobinfo__name')]");
                        if (jName.isEmpty()) jName = safeGetText(card, ".//*[contains(@class,'jobname')]");
                        String jCompany = safeGetText(card, ".//a[contains(@class,'companyinfo__name')]");
                        if (jCompany.isEmpty()) jCompany = safeGetText(card, ".//*[contains(@class,'companyinfo')]");
                        // Read all tag spans for salary info
                        String jSalary = "";
                        String jLocation = "";
                        try {
                            var tags = card.findElements(By.xpath(".//*[contains(@class,'joblist-box__item-tag')]"));
                            for (var tag : tags) {
                                String t = tag.getText().trim();
                                if (t.contains("元") || t.contains("薪")) jSalary = t;
                            }
                        } catch (Exception ignore) {}
                        try {
                            var infos = card.findElements(By.xpath(".//*[contains(@class,'jobinfo__other-info-item')]"));
                            if (infos.size() > 0) jLocation = infos.get(0).getText().trim();
                        } catch (Exception ignore) {}

                        String jUrl = "";
                        try {
                            WebElement link = card.findElement(By.xpath(".//a[contains(@class, 'jobinfo__name')]"));
                            jUrl = link.getAttribute("href");
                        } catch (Exception ignore) {}

                        // Quick filter: skip already applied
                        String cardText = card.getText();
                        if (cardText.contains("\u5df2\u6295\u9012") || cardText.contains("\u5df2\u7533\u8bf7")) {
                            continue;
                        }

                        if (jName.isEmpty()) jName = "unknown";
                        if (jUrl != null && !jUrl.isEmpty()) {
                            allJobs.add(new String[]{jName, jCompany, jSalary, jLocation, jUrl});
                            collected++;
                        }
                    } catch (Exception ignore) {}
                }
            }
            log.info("[Collect] Done. Collected {} jobs for keyword [{}]", allJobs.size(), keyword);

            // Phase 2A: Fast filter + collect details (no AI call)
            String mainWindow = CHROME_DRIVER.getWindowHandle();
            int skipped = 0;
            List<String[]> pendingJobs = new ArrayList<>();
            List<String[]> pendingDetails = new ArrayList<>();

            for (int idx = 0; idx < allJobs.size(); idx++) {
                if (isLimit) break;
                String[] info = allJobs.get(idx);
                String jName = info[0], jCompany = info[1], jSalary = info[2], jLocation = info[3], jUrl = info[4];

                if (!isInCommuteArea(jLocation)) {
                    skipped++;
                    log.info("[{}/{}] SKIP(area): {} | {} | {}", idx + 1, allJobs.size(), jName, jCompany, jLocation);
                    continue;
                }

                try {
                    ((JavascriptExecutor) CHROME_DRIVER).executeScript("window.open(arguments[0], '_blank');", jUrl);
                    SeleniumUtil.sleep(2);
                    Set<String> handles = CHROME_DRIVER.getWindowHandles();
                    String detailTab = "";
                    for (String h : handles) {
                        if (!h.equals(mainWindow)) { detailTab = h; break; }
                    }
                    if (detailTab.isEmpty()) { log.debug("Cannot open tab for {}", jName); continue; }
                    CHROME_DRIVER.switchTo().window(detailTab);
                    SeleniumUtil.sleep(1);

                    String jobDetail = readJobDetail();

                    if (hasBlacklistedContent(jName, jobDetail)) {
                        skipped++;
                        log.info("[{}/{}] SKIP(keywords): {} | {}", idx + 1, allJobs.size(), jName, jCompany);
                    } else {
                        pendingJobs.add(info);
                        pendingDetails.add(new String[]{jName, jCompany, jSalary, jLocation, jobDetail});
                        log.info("[{}/{}] READY: {} | {} | {} | {}", idx + 1, allJobs.size(), jName, jCompany, jSalary, jLocation);
                    }
                } catch (Exception e) {
                    log.debug("Read error: {} - {}", jName, e.getMessage());
                } finally {
                    try {
                        Set<String> allHandles = CHROME_DRIVER.getWindowHandles();
                        for (String h : allHandles) {
                            if (!h.equals(mainWindow)) {
                                CHROME_DRIVER.switchTo().window(h);
                                CHROME_DRIVER.close();
                            }
                        }
                        CHROME_DRIVER.switchTo().window(mainWindow);
                    } catch (Exception ignore) {}
                }
            }
            log.info("[Phase A] keyword [{}]: collected={}, filtered={}, pending AI={}", keyword, allJobs.size(), skipped, pendingJobs.size());

            // Phase 2B: Batch AI evaluate + apply
            int applied = 0, aiSkipped = 0, failed = 0;
            for (int batchStart = 0; batchStart < pendingJobs.size(); batchStart += BATCH_SIZE) {
                if (isLimit) break;
                int batchEnd = Math.min(batchStart + BATCH_SIZE, pendingJobs.size());
                List<String[]> batchDetails = pendingDetails.subList(batchStart, batchEnd);
                List<String[]> batchJobs = pendingJobs.subList(batchStart, batchEnd);

                log.info("[Phase B] 批量评估第{}-{}个岗位（共{}个）", batchStart + 1, batchEnd, pendingJobs.size());

                List<Boolean> results;
                try {
                    results = AiService.shouldApplyBatch(batchDetails);
                } catch (Exception e) {
                    log.warn("批量AI判断异常: {}", e.getMessage());
                    results = new ArrayList<>();
                    for (int i = 0; i < batchDetails.size(); i++) results.add(false);
                }

                for (int i = 0; i < batchJobs.size(); i++) {
                    if (isLimit) break;
                    String[] info = batchJobs.get(i);
                    String jName = info[0], jCompany = info[1], jSalary = info[2], jLocation = info[3], jUrl = info[4];
                    boolean shouldApply = i < results.size() && results.get(i);

                    if (shouldApply) {
                        try {
                            ((JavascriptExecutor) CHROME_DRIVER).executeScript("window.open(arguments[0], '_blank');", jUrl);
                            SeleniumUtil.sleep(2);
                            Set<String> handles = CHROME_DRIVER.getWindowHandles();
                            String detailTab = "";
                            for (String h : handles) {
                                if (!h.equals(mainWindow)) { detailTab = h; break; }
                            }
                            if (detailTab.isEmpty()) { failed++; continue; }
                            CHROME_DRIVER.switchTo().window(detailTab);
                            SeleniumUtil.sleep(1);

                            boolean ok = false;
                            try { ok = applyOnDetailPage(); } catch (Exception e) { log.debug("Apply err: {}", e.getMessage()); }
                            if (ok) {
                                Job job = new Job();
                                job.setJobName(jName);
                                job.setSalary(jSalary);
                                job.setCompanyName(jCompany);
                                resultList.add(job);
                                applied++;
                                log.info("  => APPLIED: {} - {}", jName, jCompany);
                            } else {
                                failed++;
                                log.info("  => AI match but apply failed: {}", jName);
                            }
                        } catch (Exception e) {
                            failed++;
                            log.debug("Apply error: {} - {}", jName, e.getMessage());
                        } finally {
                            try {
                                Set<String> allHandles = CHROME_DRIVER.getWindowHandles();
                                for (String h : allHandles) {
                                    if (!h.equals(mainWindow)) {
                                        CHROME_DRIVER.switchTo().window(h);
                                        CHROME_DRIVER.close();
                                    }
                                }
                                CHROME_DRIVER.switchTo().window(mainWindow);
                            } catch (Exception ignore) {}
                        }
                    } else {
                        aiSkipped++;
                        log.info("  => AI skip: {}", jName);
                    }
                }
            }
            log.info("[Result] keyword [{}]: collected={}, applied={}, skipped={}, aiSkipped={}, failed={}", keyword, allJobs.size(), applied, skipped, aiSkipped, failed);
        } catch (Exception e) {
            log.info("Submit error: {}", e.getMessage());
        }
    }

    // === 地址预过滤：根据求职者的通勤范围自定义 ===
    // 使用方式：根据你的位置修改以下列表
    // ALLOWED_AREAS: 全区可投递的区域（如天河、越秀）
    // ALLOWED_SUB_AREAS: 部分区域可投递的具体片区
    // EXCLUDED_KEYWORDS: 明确排除的区域
    private static final List<String> ALLOWED_AREAS = Arrays.asList("天河", "越秀");
    private static final List<String> ALLOWED_SUB_AREAS = Arrays.asList(
            "黄花岗", "区庄", "淘金", "东山口",  // 越秀区东段
            "同和", "京溪",                      // 白云区
            "黄陂", "金峰"                       // 黄埔6号线沿线
    );
    private static final List<String> EXCLUDED_KEYWORDS = Arrays.asList(
            "花都", "从化", "番禺", "南沙", "佛山",
            "海珠", "荔湾",
            "猎德", "珠江新城",
            "北京路",
            "芳村", "滘口", "花地湾",
            "嘉禾望岗", "永泰", "白云新城", "龙归", "钟落潭",
            "琶洲", "客村", "赤岗", "江南西", "沥滘", "中大",
            "香雪", "云埔", "萝岗", "黄埔东", "苏元"
    );

    private static boolean isInCommuteArea(String location) {
        if (location == null || location.trim().isEmpty()) return true; // 地址不明则投递
        for (String ex : EXCLUDED_KEYWORDS) {
            if (location.contains(ex)) return false;
        }
        for (String area : ALLOWED_AREAS) {
            if (location.contains(area)) return true;
        }
        for (String sub : ALLOWED_SUB_AREAS) {
            if (location.contains(sub)) return true;
        }
        return false;
    }

    // === 内容关键词过滤：命中则跳过，不调用AI ===
    private static final List<String> CONTENT_BLACKLIST = Arrays.asList(
            "电话销售", "电话客服", "电话邀约", "电话营销",
            "陌拜", "地推", "扫楼", "摆摊",
            "催收", "贷款销售", "pos机",
            "主播", "直播带货", "娱乐直播"
    );

    private static boolean hasBlacklistedContent(String jobName, String jobDetail) {
        String combined = (jobName == null ? "" : jobName) + (jobDetail == null ? "" : jobDetail);
        for (String kw : CONTENT_BLACKLIST) {
            if (combined.contains(kw)) return true;
        }
        return false;
    }

    private static String safeGetText(WebElement parent, String xpath) {
        try {
            return parent.findElement(By.xpath(xpath)).getText().trim();
        } catch (Exception e) {
            return "";
        }
    }

    private static String readJobDetail() {
        StringBuilder sb = new StringBuilder();
        try {
            // 获取整个详情页的文本内容
            String pageText = (String) ((JavascriptExecutor) CHROME_DRIVER).executeScript(
                "return document.body ? document.body.innerText : '';");
            if (pageText != null && !pageText.isEmpty()) {
                // 截取合理的长度，避免太长浪费token
                if (pageText.length() > 3000) {
                    pageText = pageText.substring(0, 3000);
                }
                sb.append(pageText);
            }
        } catch (Exception e) {
            // 尝试备用方案
            try {
                // 尝试常见详情页选择器
                String[] selectors = {
                    ".describtion__detail-content",
                    ".job-detail__content",
                    ".job-summary",
                    ".job-description",
                    ".pos-ul",
                    ".job_require",
                    ".job-detail",
                    ".describtion"
                };
                for (String sel : selectors) {
                    try {
                        WebElement el = CHROME_DRIVER.findElement(By.cssSelector(sel));
                        String text = el.getText().trim();
                        if (!text.isEmpty()) {
                            sb.append(text);
                            break;
                        }
                    } catch (Exception ignore) {}
                }
            } catch (Exception ex) {
                log.warn("读取岗位详情失败: {}", ex.getMessage());
            }
        }
        return sb.toString().trim();
    }

    /**
     * 在详情页尝试投递
     * @return 是否投递成功
     */
    private static boolean applyOnDetailPage() {
        try {
            // 查找详情页的投递按钮
            String[] applySelectors = {
                "button.apply-btn",
                "button[class*='apply']",
                "button[class*='Apply']",
                ".apply-btn",
                ".job-detail__apply",
                "button.btn-apply",
                ".btn--primary",
                "button[data-zp-btn='Apply']"
            };
            
            WebElement applyBtn = null;
            for (String sel : applySelectors) {
                try {
                    List<WebElement> btns = CHROME_DRIVER.findElements(By.cssSelector(sel));
                    for (WebElement btn : btns) {
                        String text = btn.getText().trim();
                        if (text.contains("投递") || text.contains("申请") || text.contains("立即") || text.contains("马上")) {
                            applyBtn = btn;
                            break;
                        }
                    }
                    if (applyBtn != null) break;
                } catch (Exception ignore) {}
            }
            
            if (applyBtn == null) {
                // 备用方案：查找所有按钮
                try {
                    List<WebElement> allBtns = CHROME_DRIVER.findElements(By.tagName("button"));
                    for (WebElement btn : allBtns) {
                        String text = btn.getText().trim();
                        if ((text.contains("投递") || text.contains("申请职位") || text.contains("立即投递")) 
                            && !text.contains("已投递")) {
                            applyBtn = btn;
                            break;
                        }
                    }
                } catch (Exception ignore) {}
            }

            if (applyBtn != null) {
                // 检查是否已投递
                String btnText = applyBtn.getText().trim();
                if (btnText.contains("已投递") || btnText.contains("已申请")) {
                    log.info("该岗位已在详情页标记为已投递");
                    return false;
                }
                
                ((JavascriptExecutor) CHROME_DRIVER).executeScript("arguments[0].click();", applyBtn);
                SeleniumUtil.sleep(2);
                
                // 检查投递结果
                if (checkIsLimit()) {
                    return false;
                }
                
                // 检查弹窗
                try {
                    WebElement dialog = CHROME_DRIVER.findElement(By.cssSelector(".a-job-apply-workflow, .dialog, .modal"));
                    String dialogText = dialog.getText();
                    if (dialogText.contains("上限") || dialogText.contains("已达")) {
                        isLimit = true;
                        return false;
                    }
                    // 尝试关闭弹窗
                    try {
                        WebElement closeBtn = dialog.findElement(By.cssSelector("button, .close, .btn-close"));
                        closeBtn.click();
                        SeleniumUtil.sleep(1);
                    } catch (Exception ignore) {}
                } catch (Exception noDialog) {
                    // 没有弹窗，正常
                }
                
                return true;
            } else {
                log.info("详情页未找到投递按钮");
                return false;
            }
        } catch (Exception e) {
            log.info("详情页投递异常: {}", e.getMessage());
            return false;
        }
    }

    /**
     * 关闭除主窗口外的所有标签页
     */
    private static void closeExtraTabs(String mainWindow) {
        try {
            Set<String> handles = CHROME_DRIVER.getWindowHandles();
            for (String handle : handles) {
                if (!handle.equals(mainWindow)) {
                    try {
                        CHROME_DRIVER.switchTo().window(handle);
                        CHROME_DRIVER.close();
                    } catch (Exception ignore) {}
                }
            }
            CHROME_DRIVER.switchTo().window(mainWindow);
        } catch (Exception e) {
            try {
                CHROME_DRIVER.switchTo().window(mainWindow);
            } catch (Exception ignore) {}
        }
    }

    private static void ensureMainWindow(String mainWindow) {
        try {
            // 先关闭多余的标签页
            Set<String> handles = CHROME_DRIVER.getWindowHandles();
            for (String handle : handles) {
                if (!handle.equals(mainWindow)) {
                    try {
                        CHROME_DRIVER.switchTo().window(handle);
                        CHROME_DRIVER.close();
                    } catch (Exception ignore) {}
                }
            }
            CHROME_DRIVER.switchTo().window(mainWindow);
        } catch (Exception e) {
            // 如果主窗口也失效了，尝试恢复
            try {
                Set<String> handles = CHROME_DRIVER.getWindowHandles();
                if (!handles.isEmpty()) {
                    CHROME_DRIVER.switchTo().window(handles.iterator().next());
                }
            } catch (Exception ignore) {}
        }
    }

    private static boolean checkIsLimit() {
        try {
            SeleniumUtil.sleepByMilliSeconds(500);
            WebElement result = CHROME_DRIVER.findElement(By.xpath("//div[@class='a-job-apply-workflow']"));
            if (result.getText().contains("达到上限")) {
                log.info("今日投递已达上限！");
                isLimit = true;
                return true;
            }
            return false;
        } catch (Exception e) {
            return false;
        }
    }

    private static void setMaxPages() {
        try {
            ACTIONS.keyDown(Keys.CONTROL).sendKeys(Keys.END).keyUp(Keys.CONTROL).perform();
            WebElement inputElement = CHROME_DRIVER.findElement(By.className("soupager__pagebox__goinp"));
            inputElement.clear();
            inputElement.sendKeys("99999");
            JavascriptExecutor js = CHROME_DRIVER;
            String modifiedValue = (String) js.executeScript("return arguments[0].value;", inputElement);
            maxPage = Integer.parseInt(modifiedValue);
            log.info("设置最大页数：{}", maxPage);
            WebElement home = CHROME_DRIVER.findElement(By.xpath("//li[@class='listsort__item']"));
            ACTIONS.moveToElement(home).perform();
        } catch (Exception ignore) {
            log.info("设置最大页数异常，设置默认最大页数50...");
            maxPage = 50;
        }
    }

    private static void printRecommendJobs(List<WebElement> jobs) {
        jobs.forEach(j -> {
            String jn = j.findElement(By.xpath(".//*[contains(@class, 'recommend-job__position')]")).getText();
            String s = j.findElement(By.xpath(".//span[@class='recommend-job__demand__salary']")).getText();
            String y = j.findElement(By.xpath(".//span[@class='recommend-job__demand__experience']")).getText().replaceAll("\n", " ");
            String ed = j.findElement(By.xpath(".//span[@class='recommend-job__demand__educational']")).getText().replaceAll("\n", " ");
            String cn = j.findElement(By.xpath(".//*[contains(@class, 'recommend-job__cname')]")).getText();
            String ct = j.findElement(By.xpath(".//*[contains(@class, 'recommend-job__demand__cinfo')]")).getText().replaceAll("\n", " ");
            Job job = new Job();
            job.setJobName(jn);
            job.setSalary(s);
            job.setCompanyTag(ct);
            job.setCompanyName(cn);
            job.setJobInfo(y + "·" + ed);
            log.info("投递【{}】公司【{}】岗位，薪资【{}】，要求【{}·{}】，规模【{}】", cn, jn, s, y, ed, ct);
            resultList.add(job);
        });
    }

    private static void login() {
        CHROME_DRIVER.get(loginUrl);
        if (SeleniumUtil.isCookieValid("./src/main/java/zhilian/cookie.json")) {
            SeleniumUtil.loadCookie("./src/main/java/zhilian/cookie.json");
            CHROME_DRIVER.navigate().refresh();
            SeleniumUtil.sleep(1);
        }
        if (isLoginRequired()) {
            scanLogin();
        }
    }

    private static void scanLogin() {
        try {
            WebElement button = CHROME_DRIVER.findElement(By.xpath("//div[@class='zppp-panel-normal-bar__img']"));
            button.click();
            log.info("等待扫码登录中...");
            WAIT.until(ExpectedConditions.presenceOfElementLocated(By.xpath("//div[@class='zp-main__personal']")));
            log.info("扫码登录成功！");
            SeleniumUtil.saveCookie("./src/main/java/zhilian/cookie.json");
        } catch (Exception e) {
            log.error("扫码登录异常！");
            System.exit(-1);
        }
    }

    private static boolean isLoginRequired() {
        return !CHROME_DRIVER.getCurrentUrl().contains("i.zhaopin.com");
    }

    private static boolean isSessionExpired() {
        String url = CHROME_DRIVER.getCurrentUrl();
        return url.contains("passport.zhaopin.com") || url.contains("login") || isLoginRequired();
    }

    private static void reLogin() {
        try {
            // 清除旧cookie
            CHROME_DRIVER.manage().deleteAllCookies();
            login();
            log.info("重新登录成功");
        } catch (Exception e) {
            log.error("重新登录失败: {}", e.getMessage());
        }
    }
}