package com.community.market;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import java.math.BigDecimal;
import java.util.HashMap;
import java.util.Map;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 现场换品审核集成测试：
 * 二手玩具摊现场临时增加自制蛋糕 →
 * 资质不足下架（照片/已售/退款、食品风险事件、信用扣分、下次报名限制）→
 * 提前复核解除；以及资质齐全允许售卖（更新品类/用电/食品安全提示）两条路径。
 *
 * 使用独立 H2 库名，避免与其他测试类共享上下文数据。
 */
@SpringBootTest(properties = "spring.datasource.url=jdbc:h2:mem:goods_change_test;MODE=MySQL;DB_CLOSE_DELAY=-1;DATABASE_TO_LOWER=TRUE")
@AutoConfigureMockMvc
class GoodsChangeIntegrationTest {

    @Autowired
    private MockMvc mockMvc;
    @Autowired
    private ObjectMapper om;

    private String login(String username, String password) throws Exception {
        Map<String, Object> body = Map.of("username", username, "password", password);
        MvcResult r = mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(om.writeValueAsString(body)))
                .andExpect(status().isOk()).andReturn();
        return om.readTree(r.getResponse().getContentAsString()).path("data").path("token").asText();
    }

    private MvcResult call(String token, String method, String url, Object body) throws Exception {
        var req = method.equals("POST") ? post(url) : method.equals("PUT") ? put(url) : get(url);
        req.contentType(MediaType.APPLICATION_JSON);
        if (token != null) {
            req.header("Authorization", "Bearer " + token);
        }
        if (body != null) {
            req.content(om.writeValueAsString(body));
        }
        return mockMvc.perform(req).andReturn();
    }

    private JsonNode data(MvcResult r) throws Exception {
        JsonNode root = om.readTree(r.getResponse().getContentAsString(java.nio.charset.StandardCharsets.UTF_8));
        org.junit.jupiter.api.Assertions.assertEquals(0, root.path("code").asInt(),
                "接口应成功：" + r.getResponse().getContentAsString(java.nio.charset.StandardCharsets.UTF_8));
        return root.path("data");
    }

    private int respCode(MvcResult r) throws Exception {
        return om.readTree(r.getResponse().getContentAsString(java.nio.charset.StandardCharsets.UTF_8)).path("code").asInt();
    }

    private long findAppId(JsonNode apps, String username) {
        for (JsonNode a : apps) {
            if (username.equals(a.path("vendor").path("username").asText())) {
                return a.path("id").asLong();
            }
        }
        throw new IllegalStateException("找不到摊主报名：" + username);
    }

    private Map<String, Object> changeBody(long appId, String qual, String license, int watt,
                                           boolean power, int cap, boolean foodZone, boolean hygiene,
                                           String photos) {
        Map<String, Object> m = new HashMap<>();
        m.put("applicationId", appId);
        m.put("newGoods", "自制奶油蛋糕、纸杯蛋糕");
        m.put("changeType", "ADD");
        m.put("newCategory", "FOOD");
        m.put("foodRelated", true);
        m.put("foodQualification", qual);
        m.put("foodLicenseNo", license);
        m.put("powerNeededWatt", watt);
        m.put("stallPowerAvailable", power);
        m.put("stallPowerCapacity", cap);
        m.put("foodZone", foodZone);
        m.put("hygieneReady", hygiene);
        m.put("stallConditionNote", "二手区摊位，无上下水");
        m.put("photoUrls", photos);
        m.put("remark", "摊主现场提出增项");
        return m;
    }

    private long findFreeStall(JsonNode stalls, String zone) {
        for (JsonNode s : stalls) {
            if ((zone == null || zone.equals(s.path("zone").asText()))
                    && "FREE".equals(s.path("status").asText())) {
                return s.path("id").asLong();
            }
        }
        return 0;
    }

    @Test
    void goodsChangeReviewFullFlow() throws Exception {
        String admin = login("admin", "admin123");
        String staff = login("staff", "staff123");
        String mayi = login("mayi", "123456");
        String shanyi = login("shanyi", "123456");

        JsonNode events = data(call(admin, "GET", "/api/events", null));
        long eventId = events.get(0).path("id").asLong();
        JsonNode apps = data(call(admin, "GET", "/api/events/" + eventId + "/applications", null));
        long mayiAppId = findAppId(apps, "mayi");
        long shanyiAppId = findAppId(apps, "shanyi");

        // 第二个活动：用于验证食品风险解除后的后续报名
        Map<String, Object> event2 = new HashMap<>();
        event2.put("name", "2027 春季社区公益集市");
        event2.put("location", "幸福社区中心广场");
        event2.put("publicGoal", "春季助学");
        event2.put("goalAmount", new BigDecimal("5000"));
        long event2Id = data(call(admin, "POST", "/api/events", event2)).path("id").asLong();

        // 审核通过：蚂蚁闲置（二手）与善意公益
        data(call(admin, "POST", "/api/applications/" + mayiAppId + "/review",
                Map.of("approved", true, "comment", "通过")));
        data(call(admin, "POST", "/api/applications/" + shanyiAppId + "/review",
                Map.of("approved", true, "comment", "通过")));

        // 智能分配
        data(call(admin, "POST", "/api/allocations/auto", Map.of("eventId", eventId)));
        JsonNode mayiApp = data(call(admin, "GET", "/api/applications/" + mayiAppId, null));
        // 二手摊不应在食品区经营（若分配结果落在食品区则手动改派到非食品区空闲摊位）
        if ("食品区".equals(mayiApp.path("stall").path("zone").asText())) {
            JsonNode stallsNow = data(call(admin, "GET", "/api/stalls?eventId=" + eventId, null));
            long nonFood = findFreeStall(stallsNow, null);
            org.junit.jupiter.api.Assertions.assertTrue(nonFood > 0);
            // findFreeStall 不排除食品区，手工挑一个非食品区的
            for (JsonNode s : stallsNow) {
                if ("FREE".equals(s.path("status").asText()) && !"食品区".equals(s.path("zone").asText())) {
                    nonFood = s.path("id").asLong();
                    break;
                }
            }
            data(call(admin, "POST", "/api/allocations/reassign",
                    Map.of("applicationId", mayiAppId, "stallId", nonFood)));
            mayiApp = data(call(admin, "GET", "/api/applications/" + mayiAppId, null));
        }
        org.junit.jupiter.api.Assertions.assertNotEquals("食品区", mayiApp.path("stall").path("zone").asText());

        // 核销入场
        data(call(staff, "POST", "/api/onsite/checkin", Map.of("code", mayiApp.path("checkinCode").asText())));

        // ---------- 路径一：资质不足 → 下架 ----------
        Map<String, Object> badChange = changeBody(mayiAppId, "INCOMPLETE", "健康证办理中", 0,
                false, 0, false, false, "");
        JsonNode created = data(call(staff, "POST", "/api/onsite/goods-changes", badChange));
        long changeId = created.path("id").asLong();
        org.junit.jupiter.api.Assertions.assertEquals("PENDING", created.path("status").asText());

        // 资质不齐不能放行
        MvcResult approveFail = call(staff, "POST", "/api/onsite/goods-changes/" + changeId + "/review",
                Map.of("decision", "APPROVED", "reviewComment", "尝试放行"));
        org.junit.jupiter.api.Assertions.assertNotEquals(0, respCode(approveFail));

        // 下架无照片被拦截
        MvcResult noPhoto = call(staff, "POST", "/api/onsite/goods-changes/" + changeId + "/review",
                Map.of("decision", "REMOVE", "soldQuantity", 3, "refundNeeded", true,
                        "refundNote", "48 小时内原路退款"));
        org.junit.jupiter.api.Assertions.assertNotEquals(0, respCode(noPhoto));

        // 审核单详情可查
        JsonNode detail = data(call(staff, "GET", "/api/onsite/goods-changes/" + changeId, null));
        org.junit.jupiter.api.Assertions.assertTrue(detail.path("createdAt").asText().length() > 0);

        // 工作人员补录照片后重新登记并下架：已售 3 份需退款
        Map<String, Object> badChange2 = changeBody(mayiAppId, "INCOMPLETE", "健康证办理中", 0,
                false, 0, false, false, "photo_001.jpg,photo_002.jpg");
        long changeId2 = data(call(staff, "POST", "/api/onsite/goods-changes", badChange2)).path("id").asLong();
        JsonNode removed = data(call(staff, "POST", "/api/onsite/goods-changes/" + changeId2 + "/review",
                Map.of("decision", "REMOVE", "reviewComment", "无健康证制售蛋糕，责令下架",
                        "soldQuantity", 3, "refundNeeded", true,
                        "refundNote", "登记 3 位居民联系方式，48 小时内原路退款")));
        org.junit.jupiter.api.Assertions.assertEquals("REMOVED", removed.path("status").asText());
        org.junit.jupiter.api.Assertions.assertEquals(3, removed.path("soldQuantity").asInt());
        org.junit.jupiter.api.Assertions.assertTrue(removed.path("refundNeeded").asBoolean());
        org.junit.jupiter.api.Assertions.assertTrue(removed.path("incidentId").asLong() > 0);

        // 异常档案出现"现场换品食品风险下架"
        JsonNode incidents = data(call(staff, "GET", "/api/onsite/events/" + eventId + "/incidents", null));
        boolean riskIncident = false;
        for (JsonNode i : incidents) {
            if ("FOOD_CHANGE_REMOVED".equals(i.path("type").asText())
                    && i.path("description").asText().contains("自制奶油蛋糕")) {
                riskIncident = true;
            }
        }
        org.junit.jupiter.api.Assertions.assertTrue(riskIncident, "应登记食品风险下架异常");

        // 信用：初始 102 + 到场 2 − 下架 15 − 已售需退款 5 = 84；风险/提前复核标记已设置
        JsonNode mayiAfter = data(call(admin, "GET", "/api/applications/" + mayiAppId, null));
        org.junit.jupiter.api.Assertions.assertEquals(84, mayiAfter.path("vendor").path("creditScore").asInt());
        org.junit.jupiter.api.Assertions.assertTrue(mayiAfter.path("vendor").path("foodRiskFlag").asBoolean());
        org.junit.jupiter.api.Assertions.assertTrue(mayiAfter.path("vendor").path("requirePreReview").asBoolean());

        // 摊主端可见下架记录与已售/退款信息
        JsonNode myChanges = data(call(mayi, "GET", "/api/my/goods-changes", null));
        org.junit.jupiter.api.Assertions.assertTrue(myChanges.size() >= 2);
        boolean seenRemoved = false;
        for (JsonNode g : myChanges) {
            if ("REMOVED".equals(g.path("status").asText())) {
                seenRemoved = true;
                org.junit.jupiter.api.Assertions.assertEquals(3, g.path("soldQuantity").asInt());
                org.junit.jupiter.api.Assertions.assertTrue(g.path("refundNeeded").asBoolean());
            }
        }
        org.junit.jupiter.api.Assertions.assertTrue(seenRemoved);

        // 食品风险影响后续食品类报名：下一活动报食品被拒
        MvcResult foodBlocked = call(mayi, "POST", "/api/my/applications", Map.of(
                "eventId", event2Id, "shopName", "蚂蚁轻食", "category", "FOOD",
                "goodsDescription", "蛋糕", "foodQualification", "COMPLETE", "foodLicenseNo", "新证 001",
                "donationRate", new BigDecimal("10"), "paymentMethod", "CASH"));
        org.junit.jupiter.api.Assertions.assertNotEquals(0, respCode(foodBlocked));

        // 管理员提前复核通过 → 解除限制，可报食品
        long vendorId = mayiAfter.path("vendorId").asLong();
        data(call(admin, "POST", "/api/admin/vendors/" + vendorId + "/clear-food-risk",
                Map.of("reason", "健康证已补办，复核通过")));
        JsonNode foodOk = data(call(mayi, "POST", "/api/my/applications", Map.of(
                "eventId", event2Id, "shopName", "蚂蚁轻食", "category", "FOOD",
                "goodsDescription", "合规包装点心", "foodQualification", "COMPLETE",
                "foodLicenseNo", "食摊字 2027-0009",
                "donationRate", new BigDecimal("10"), "paymentMethod", "CASH")));
        org.junit.jupiter.api.Assertions.assertEquals("SUBMITTED", foodOk.path("status").asText());

        // ---------- 路径二：资质齐全、条件满足 → 允许售卖 ----------
        JsonNode shanyiApp = data(call(admin, "GET", "/api/applications/" + shanyiAppId, null));
        data(call(staff, "POST", "/api/onsite/checkin",
                Map.of("code", shanyiApp.path("checkinCode").asText())));

        // 公益摊当前不在食品区：登记后放行应被"非食品区"条件拦截
        Map<String, Object> goodChangeBadZone = changeBody(shanyiAppId, "COMPLETE", "食摊字 2026-0209",
                0, false, 0, false, true, "photo_ok_1.jpg");
        long gzId = data(call(staff, "POST", "/api/onsite/goods-changes", goodChangeBadZone)).path("id").asLong();
        MvcResult zoneFail = call(staff, "POST", "/api/onsite/goods-changes/" + gzId + "/review",
                Map.of("decision", "APPROVED", "reviewComment", "尝试放行"));
        org.junit.jupiter.api.Assertions.assertNotEquals(0, respCode(zoneFail));

        // 管理员改派到空闲食品区摊位
        JsonNode stalls = data(call(admin, "GET", "/api/stalls?eventId=" + eventId, null));
        long freeFoodStall = findFreeStall(stalls, "食品区");
        org.junit.jupiter.api.Assertions.assertTrue(freeFoodStall > 0, "应有空闲食品区摊位");
        data(call(admin, "POST", "/api/allocations/reassign",
                Map.of("applicationId", shanyiAppId, "stallId", freeFoodStall)));

        // 重新登记：资质齐全、卫生合格、无用电设备；foodZone 由后端按摊位快照回填
        Map<String, Object> goodChange = changeBody(shanyiAppId, "COMPLETE", "食摊字 2026-0209",
                0, true, 1000, false, true, "photo_ok_2.jpg");
        long gcId = data(call(staff, "POST", "/api/onsite/goods-changes", goodChange)).path("id").asLong();
        JsonNode approved = data(call(staff, "POST", "/api/onsite/goods-changes/" + gcId + "/review",
                Map.of("decision", "APPROVED", "reviewComment", "资质齐全，限当日售完即止")));
        org.junit.jupiter.api.Assertions.assertEquals("APPROVED", approved.path("status").asText());
        org.junit.jupiter.api.Assertions.assertFalse(approved.path("foodSafetyNotice").asText().isBlank(),
                "放行应生成食品安全提示");

        // 报名品类、资质、食品安全提示均已更新
        JsonNode shanyiAfter = data(call(admin, "GET", "/api/applications/" + shanyiAppId, null));
        org.junit.jupiter.api.Assertions.assertEquals("FOOD", shanyiAfter.path("category").asText());
        org.junit.jupiter.api.Assertions.assertEquals("COMPLETE", shanyiAfter.path("foodQualification").asText());
        org.junit.jupiter.api.Assertions.assertFalse(shanyiAfter.path("foodSafetyNotice").asText().isBlank());
        org.junit.jupiter.api.Assertions.assertTrue(shanyiAfter.path("goodsDescription").asText().contains("现场新增"));
        // 主动报备合规 → 信用加分：115 + 到场 2 + 放行 2 = 119
        org.junit.jupiter.api.Assertions.assertEquals(119, shanyiAfter.path("vendor").path("creditScore").asInt());

        // 用电超容量拦截：2000W > 摊位 1000W
        Map<String, Object> overPower = changeBody(shanyiAppId, "COMPLETE", "食摊字 2026-0209",
                2000, true, 1000, true, true, "");
        long opId = data(call(staff, "POST", "/api/onsite/goods-changes", overPower)).path("id").asLong();
        MvcResult powerFail = call(staff, "POST", "/api/onsite/goods-changes/" + opId + "/review",
                Map.of("decision", "APPROVED", "reviewComment", "尝试放行"));
        org.junit.jupiter.api.Assertions.assertNotEquals(0, respCode(powerFail));

        // 重复审核被拦截
        MvcResult dupReview = call(staff, "POST", "/api/onsite/goods-changes/" + gcId + "/review",
                Map.of("decision", "REMOVE", "soldQuantity", 0));
        org.junit.jupiter.api.Assertions.assertNotEquals(0, respCode(dupReview));

        // ---------- 档案 / 复盘 ----------
        JsonNode archive = data(call(admin, "GET", "/api/events/" + eventId + "/archive", null));
        org.junit.jupiter.api.Assertions.assertTrue(archive.path("goodsChanges").size() >= 4);

        JsonNode review = data(call(admin, "GET", "/api/events/" + eventId + "/review", null));
        boolean hasRemoveTip = false, hasApproveTip = false;
        for (JsonNode s : review.path("suggestions")) {
            String t = s.asText();
            if (t.contains("临时换品") && t.contains("下架")) {
                hasRemoveTip = true;
            }
            if (t.contains("允许售卖")) {
                hasApproveTip = true;
            }
        }
        org.junit.jupiter.api.Assertions.assertTrue(hasRemoveTip, "复盘应提示下架风险与提前复核");
        org.junit.jupiter.api.Assertions.assertTrue(hasApproveTip, "复盘应提示放行情况与报备通道");

        // 越权：摊主不能登记换品
        mockMvc.perform(post("/api/onsite/goods-changes")
                        .header("Authorization", "Bearer " + mayi)
                        .contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(status().isBadRequest());
    }
}
