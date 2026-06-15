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

            // Phase 2: Visit each job detail, AI judge, apply if match
            String mainWindow = CHROME_DRIVER.getWindowHandle();
            int applied = 0, skipped = 0, failed = 0;
            for (int idx = 0; idx < allJobs.size(); idx++) {
                if (isLimit) break;
                String[] info = allJobs.get(idx);
                String jName = info[0], jCompany = info[1], jSalary = info[2], jLocation = info[3], jUrl = info[4];
                try {
                    // Open job detail in new tab
                    ((JavascriptExecutor) CHROME_DRIVER).executeScript("window.open(arguments[0], '_blank');", jUrl);
                    SeleniumUtil.sleep(2);
                    // Switch to new tab
                    Set<String> handles = CHROME_DRIVER.getWindowHandles();
                    String detailTab = "";
                    for (String h : handles) {
                        if (!h.equals(mainWindow)) { detailTab = h; break; }
                    }
                    if (detailTab.isEmpty()) { log.debug("Cannot open tab for {}", jName); continue; }
                    CHROME_DRIVER.switchTo().window(detailTab);
                    SeleniumUtil.sleep(1);

                    String jobDetail = readJobDetail();
                    log.info("[{}/{}] {} | {} | {} | {}", idx + 1, allJobs.size(), jName, jCompany, jSalary, jLocation);

                    boolean shouldApply = false;
                    try {
                        shouldApply = AiService.shouldApplyZhiLian(jName, jCompany, jSalary, jLocation, jobDetail);
                    } catch (Exception e) {
                        log.warn("AI error: {}", e.getMessage());
                    }

                    if (shouldApply) {
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
                    } else {
                        skipped++;
                        log.info("  => AI skip: {}", jName);
                    }
                } catch (Exception e) {
                    failed++;
                    log.debug("Error: {} - {}", jName, e.getMessage());
                } finally {
                    // Close detail tab and switch back to main window
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
            log.info("[Result] keyword [{}]: collected={}, applied={}, skipped={}, failed={}", keyword, allJobs.size(), applied, skipped, failed);
        } catch (Exception e) {
            log.info("Submit error: {}", e.getMessage());
        }
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
}