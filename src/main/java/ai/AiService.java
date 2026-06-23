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
import java.util.concurrent.*;

@Slf4j
public class AiService {

    private static final Dotenv dotenv = Dotenv.load();
    private static final String BASE_URL = dotenv.get("BASE_URL") + "/v1/chat/completions";
    private static final String API_KEY = dotenv.get("API_KEY");
    private static final String MODEL = dotenv.get("MODEL");
    private static String RESUME_CACHE = null;

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
            "   b. 跨境电商/电商运营/产品管理等跨境方向\n" +
            "   c. 数据分析/数据运营/商业分析/策略分析等数据方向\n" +
            "   d. AI/人工智能/大数据/智慧物流等新技术方向\n" +
            "   e. 管理咨询/战略规划/项目管理等管理方向\n" +
            "   f. 运营/市场/产品/客服等互联网/电商岗\n" +
            "   g. 物流管理/管理科学与工程/运营管理等本硕专业相关岗\n" +
            "3. 要求不能过高：明确要求3年及以上经验则拒绝；要求硕士学历可接受（求职者研一在读）\n" +
            "4. 即使岗位名不完全匹配，但工作内容与背景有交集就应投递\n" +
            "5. 学历过滤：要求大专及以下则拒绝；要求本科及以上或硕士均可接受\n" +
            "6. 地址判断（宽松）：地址不明则投递；非广州则拒绝；广州其他区域均可投递\n\n" +
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

    public static String cleanBossDesc(String raw) {
        return raw.replaceAll("kanzhun|BOSS直聘|来自BOSS直聘", "")
                .replaceAll("[\\u200b-\\u200d\\uFEFF]", "")
                .replaceAll("<[^>]+>", "")
                .replaceAll("\\s+", " ")
                .trim();
    }
}