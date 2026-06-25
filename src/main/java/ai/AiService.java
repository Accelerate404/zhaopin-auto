package ai;

import io.github.cdimascio.dotenv.Dotenv;
import lombok.extern.slf4j.Slf4j;
import org.json.JSONArray;
import org.json.JSONObject;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.*;

@Slf4j
public class AiService {

    private static final Dotenv dotenv = Dotenv.load();
    private static final String BASE_URL = dotenv.get("BASE_URL") + "/v1/chat/completions";
    private static final String API_KEY = dotenv.get("API_KEY");
    private static final String MODEL = dotenv.get("MODEL");
    private static String RESUME_CACHE = null;
    private static String PREFERENCES_CACHE = null;

    private static String loadResume() {
        if (RESUME_CACHE != null) return RESUME_CACHE;
        String[] paths = {"resume.md", "resume.txt", "../resume.md", "src/main/resources/resume.md"};
        for (String p : paths) {
            try {
                java.io.File f = new java.io.File(p);
                if (f.exists()) {
                    RESUME_CACHE = new String(Files.readAllBytes(Paths.get(f.getAbsolutePath())), "UTF-8");
                    log.info("Resume loaded from: {} ({} chars)", f.getAbsolutePath(), RESUME_CACHE.length());
                    return RESUME_CACHE;
                }
            } catch (Exception ignore) {}
        }
        log.warn("No resume file found! AI matching will be less accurate.");
        RESUME_CACHE = "";
        return RESUME_CACHE;
    }

    private static String loadPreferences() {
        if (PREFERENCES_CACHE != null) return PREFERENCES_CACHE;
        String[] paths = {"preferences.md", "../preferences.md", "src/main/resources/preferences.md"};
        for (String p : paths) {
            try {
                java.io.File f = new java.io.File(p);
                if (f.exists()) {
                    PREFERENCES_CACHE = new String(Files.readAllBytes(Paths.get(f.getAbsolutePath())), "UTF-8");
                    log.info("Preferences loaded from: {} ({} chars)", f.getAbsolutePath(), PREFERENCES_CACHE.length());
                    return PREFERENCES_CACHE;
                }
            } catch (Exception ignore) {}
        }
        log.warn("No preferences file found.");
        PREFERENCES_CACHE = "";
        return PREFERENCES_CACHE;
    }

    public static String sendRequest(String content) {
        int timeoutInSeconds = 60;
        HttpClient client = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(timeoutInSeconds))
                .build();
        JSONObject requestData = new JSONObject();
        requestData.put("model", MODEL);
        requestData.put("temperature", 0.5);
        JSONArray messages = new JSONArray();
        JSONObject message = new JSONObject();
        message.put("role", "user");
        message.put("content", content);
        messages.put(message);
        requestData.put("messages", messages);
        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(BASE_URL))
                .header("Content-Type", "application/json")
                .header("Authorization", "Bearer " + API_KEY)
                .POST(HttpRequest.BodyPublishers.ofString(requestData.toString()))
                .build();
        ExecutorService executor = Executors.newSingleThreadExecutor();
        Callable<HttpResponse<String>> task = () -> client.send(request, HttpResponse.BodyHandlers.ofString());
        Future<HttpResponse<String>> future = executor.submit(task);
        try {
            HttpResponse<String> response = future.get(timeoutInSeconds, TimeUnit.SECONDS);
            if (response.statusCode() == 200) {
                log.info(response.body());
                JSONObject responseObject = new JSONObject(response.body());
                String responseContent = responseObject.getJSONArray("choices")
                        .getJSONObject(0)
                        .getJSONObject("message")
                        .getString("content");
                return responseContent;
            } else {
                log.error("AI request failed: {}", response.statusCode());
            }
        } catch (TimeoutException e) {
            log.error("AI request timeout: {} sec", timeoutInSeconds);
        } catch (Exception e) {
            log.error("AI request error!", e);
        } finally {
            executor.shutdownNow();
        }
        return "";
    }

    public static boolean shouldApplyZhiLian(String jobName, String company, String salary, String location, String jobDetail) {
        String detail = jobDetail.length() > 2000 ? jobDetail.substring(0, 2000) : jobDetail;
        String resumeContent = loadResume();
        String prompt = "你是求职顾问。请根据求职者简历和岗位信息，判断是否应该投递。\n\n" +
            "【求职者简历】\n" + resumeContent + "\n\n" +
            "【岗位】\n" +
            "- 名称：" + jobName + "\n" +
            "- 公司：" + company + "\n" +
            "- 薪资：" + salary + "\n" +
            "- 地址：" + location + "\n" +
            "- 详情：\n" + detail + "\n\n" +
            "【匹配规则（宽松匹配，多方向投递）】\n" +
            "1. 必须是实习岗（包含实习/intern等），否则拒绝\n" +
            "2. 以下方向任意匹配一个即可投递：\n" +
            "   a. 供应链/物流/冷链/仓储/采购等物流方向\n" +
            "   b. 产品管理/产品运营等产品方向\n" +
            "   c. 数据分析/数据运营/商业分析/策略分析等数据方向\n" +
            "   d. AI/人工智能/大数据/智慧物流等新技术方向\n" +
            "   e. 战略规划/项目管理等管理方向\n" +
            "   f. 运营/市场/产品/客服等互联网/电商岗\n" +
            "   g. 物流管理/管理科学与工程/运营管理等本硕专业相关岗\n" +
            "3. 要求不能过高：明确要求3年及以上经验则拒绝；要求硕士学历可接受（求职者研一在读）\n" +
            "4. 即使岗位名不完全匹配，但工作内容与背景有交集就应投递\n" +
            "5. 学历过滤：要求大专及以下则拒绝；要求本科及以上或硕士均可接受\n" +
            "6. 地址判断：以下区域可投递，其他区域拒绝：\n" +
            "   可投递：天河区全域、越秀区东段（黄花岗/区庄/淘金/东山口）、黄埔6号线黄陂-金峰沿线产业园、白云同和/京溪片区\n" +
            "   拒绝：珠江新城猎德以南、越秀北京路以西、海珠、荔湾、番禺、花都、从化、佛山、黄埔苏元及以东\n" +
            "   地址不明则投递\n\n" +
            "只返回JSON：" + "{\"match\":true,\"reason\":\"简短原因\"}" + " 或 " + "{\"match\":false,\"reason\":\"简短原因\"}";
        try {
            String response = sendRequest(prompt);
            if (response != null && !response.isEmpty()) {
                response = response.trim();
                if (response.contains("{")) {
                    int s = response.indexOf("{");
                    int e = response.lastIndexOf("}");
                    if (s >= 0 && e > s) response = response.substring(s, e + 1);
                }
                JSONObject json = new JSONObject(response);
                boolean match = json.optBoolean("match", false);
                String reason = json.optString("reason", "");
                log.info("AI判断: {} | 原因: {}", match ? "匹配" : "不匹配", reason);
                return match;
            }
        } catch (Exception e) {
            log.warn("AI判断异常: {}", e.getMessage());
        }
        return false;
    }

    private static final int MAX_RETRIES = 2;

    /**
     * Send request with retry logic.
     */
    private static String sendRequestWithRetry(String content) {
        for (int attempt = 0; attempt <= MAX_RETRIES; attempt++) {
            try {
                String response = sendRequest(content);
                if (response != null && !response.isEmpty()) {
                    return response;
                }
            } catch (Exception e) {
                log.warn("API调用失败 (attempt {}/{}): {}", attempt + 1, MAX_RETRIES + 1, e.getMessage());
            }
            if (attempt < MAX_RETRIES) {
                try { Thread.sleep(2000 * (attempt + 1)); } catch (InterruptedException ie) { Thread.currentThread().interrupt(); break; }
            }
        }
        return "";
    }

    /**
     * Batch evaluate multiple jobs in a single AI call with retry.
     * @param jobs List of String[]{name, company, salary, location, detail}
     * @return List of Boolean, same order as input. false for any failed evaluation.
     */
    public static List<Boolean> shouldApplyBatch(List<String[]> jobs) {
        List<Boolean> results = new ArrayList<>();
        if (jobs == null || jobs.isEmpty()) return results;

        String resumeContent = loadResume();
        String preferencesContent = loadPreferences();

        StringBuilder prompt = new StringBuilder();
        prompt.append("你是求职顾问。请根据求职者简历、偏好画像和岗位信息，批量判断每个岗位是否应该投递。\n\n");
        prompt.append("【求职者简历】\n").append(resumeContent).append("\n\n");
        if (!preferencesContent.isEmpty()) {
            prompt.append("【求职者偏好画像】\n").append(preferencesContent).append("\n\n");
        }
        prompt.append("【岗位列表】\n");
        for (int i = 0; i < jobs.size(); i++) {
            String[] job = jobs.get(i);
            String detail = job[4].length() > 1500 ? job[4].substring(0, 1500) : job[4];
            prompt.append("--- 岗位").append(i + 1).append(" ---\n");
            prompt.append("名称：").append(job[0]).append("\n");
            prompt.append("公司：").append(job[1]).append("\n");
            prompt.append("薪资：").append(job[2]).append("\n");
            prompt.append("地址：").append(job[3]).append("\n");
            prompt.append("详情：").append(detail).append("\n\n");
        }
        prompt.append("【判断要求】\n");
        prompt.append("根据简历和偏好画像，综合判断每个岗位是否值得投递。请从以下维度评估：\n");
        prompt.append("1. 岗位方向匹配度：是否属于AI/数据/供应链等目标方向\n");
        prompt.append("2. 技术栈契合度：岗位是否需要Python/SQL/AI等求职者掌握的技能\n");
        prompt.append("3. 公司质量：从岗位描述中判断公司规模、行业地位、是否为正规企业（警惕纯销售型小公司、皮包公司、培训贷公司）\n");
        prompt.append("4. 岗位含金量：工作内容是否有实际技术含量，是否能积累有价值的经验\n");
        prompt.append("5. 发展空间：是否有导师带教、转正机会、系统培训\n");
        prompt.append("对每个岗位返回判断结果。\n\n");
        prompt.append("只返回JSON数组，每个元素包含match和reason字段，顺序与岗位列表一致。\n");
        prompt.append("格式：[{\"match\":true,\"reason\":\"原因\"},{\"match\":false,\"reason\":\"原因\"},...]");

        String response = sendRequestWithRetry(prompt.toString());
        if (response != null && !response.isEmpty()) {
            try {
                response = response.trim();
                int s = response.indexOf("[");
                int e = response.lastIndexOf("]");
                if (s >= 0 && e > s) {
                    response = response.substring(s, e + 1);
                }
                JSONArray arr = new JSONArray(response);
                for (int i = 0; i < jobs.size(); i++) {
                    if (i < arr.length()) {
                        JSONObject obj = arr.getJSONObject(i);
                        boolean match = obj.optBoolean("match", false);
                        String reason = obj.optString("reason", "");
                        log.info("批量判断 岗位{}: {} | 原因: {}", i + 1, match ? "匹配" : "不匹配", reason);
                        results.add(match);
                    } else {
                        results.add(false);
                    }
                }
                return results;
            } catch (Exception e) {
                log.warn("批量AI结果解析异常: {}", e.getMessage());
            }
        }
        // On failure, return all false
        for (int i = 0; i < jobs.size(); i++) results.add(false);
        return results;
    }

    public static String cleanBossDesc(String raw) {
        return raw.replaceAll("kanzhun|BOSS直聘|来自BOSS直聘", "")
                .replaceAll("[\\u200b-\\u200d\\uFEFF]", "")
                .replaceAll("<[^>]+>", "")
                .replaceAll("\\s+", " ")
                .trim();
    }
}