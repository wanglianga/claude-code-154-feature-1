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
import java.util.List;
import java.util.Map;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * 现场换品审核集成测试：
 * 摊主报名二手物品、现场临时加售自制蛋糕 → 工作人员登记换品内容/食品资质/摊位条件 →
 * 资质不足下架（照片留档、已售数量、退款）→ 信用扣分 + 下次报名需提前复核 + 食品风险记录 →
 * 食品安全风险事件限制后续食品类报名；另验证资质齐全时允许售卖并更新品类/用电/食品安全提示。
 */
@SpringBootTest
@AutoConfigureMockMvc
class GoodsChangeFlowTest {

    @Autowired
    private MockMvc mockMvc;
    @Autowired
    private ObjectMapper om;

    private String login(String username, String password) throws Exception {
        MvcResult r = mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(om.writeValueAsString(Map.of("username", username, "password", password))))
                .andExpect(status().isOk())
                .andReturn();
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

    /** 在种子活动中找到指定摊主的报名；若仍待审核则由管理员通过 */
    private long ensureApproved(String admin, long eventId, String vendorUsername) throws Exception {
        JsonNode apps = data(call(admin, "GET", "/api/events/" + eventId + "/applications", null));
        for (JsonNode a : apps) {
            if (vendorUsername.equals(a.path("vendor").path("username").asText())) {
                long id = a.path("id").asLong();
                if ("SUBMITTED".equals(a.path("status").asText())) {
                    data(call(admin, "POST", "/api/applications/" + id + "/review",
                            Map.of("approved", true, "comment", "符合活动规则")));
                }
                return id;
            }
        }
        throw new IllegalStateException("找不到摊主报名：" + vendorUsername);
    }

    private JsonNode vendorUser(String admin, String username) throws Exception {
        JsonNode users = data(call(admin, "GET", "/api/admin/users?role=VENDOR", null));
        for (JsonNode u : users) {
            if (username.equals(u.path("username").asText())) {
                return u;
            }
        }
        throw new IllegalStateException("找不到摊主账号：" + username);
    }

    @Test
    void goodsChangeReviewFlow() throws Exception {
        String admin = login("admin", "admin123");
        String staff = login("staff", "staff123");
        String mayi = login("mayi", "123456"); // 蚂蚁闲置铺：报名二手物品

        JsonNode events = data(call(admin, "GET", "/api/events", null));
        long eventId = events.get(0).path("id").asLong(); // 种子活动（日期最新，排在第一）
        long mayiAppId = ensureApproved(admin, eventId, "mayi");
        long mayiUserId = vendorUser(admin, "mayi").path("id").asLong();
        int creditBefore = vendorUser(admin, "mayi").path("creditScore").asInt();

        // ---------- 1. 工作人员登记现场换品：二手玩具摊临时加售自制蛋糕 ----------
        Map<String, Object> submit = new HashMap<>();
        submit.put("applicationId", mayiAppId);
        submit.put("newCategory", "FOOD");
        submit.put("newGoodsDescription", "新增自制蛋糕、曲奇售卖");
        submit.put("involvesFood", true);
        submit.put("foodQualification", "INCOMPLETE");
        submit.put("foodLicenseNo", "健康证办理中，无食品经营许可证");
        submit.put("stallConditionOk", false);
        submit.put("stallConditionNote", "无冷藏设备、无防蝇防尘设施");
        submit.put("powerNeeded", true);
        submit.put("powerWanted", 800);
        JsonNode change = data(call(staff, "POST", "/api/onsite/goods-changes", submit));
        long changeId = change.path("id").asLong();
        org.junit.jupiter.api.Assertions.assertEquals("PENDING", change.path("status").asText());

        // 同一摊位不可重复登记待审核换品
        org.junit.jupiter.api.Assertions.assertNotEquals(0,
                respCode(call(staff, "POST", "/api/onsite/goods-changes", submit)));

        // ---------- 2. 下架处理：必须保留现场照片、登记已售数量与退款 ----------
        Map<String, Object> noPhoto = new HashMap<>();
        noPhoto.put("decision", "REMOVE");
        noPhoto.put("soldQuantity", 15);
        noPhoto.put("refundNeeded", true);
        org.junit.jupiter.api.Assertions.assertNotEquals(0,
                respCode(call(staff, "POST", "/api/onsite/goods-changes/" + changeId + "/review", noPhoto)),
                "下架必须留现场照片");

        Map<String, Object> remove = new HashMap<>();
        remove.put("decision", "REMOVE");
        remove.put("reviewComment", "食品资质不足且摊位条件不满足，要求立即下架");
        remove.put("photoUrls", List.of("onsite-cake-1.jpg", "onsite-cake-2.jpg"));
        remove.put("soldQuantity", 15);
        remove.put("refundNeeded", true);
        remove.put("refundNote", "已售 15 件按收款流水原路退回");
        JsonNode removed = data(call(staff, "POST", "/api/onsite/goods-changes/" + changeId + "/review", remove));
        org.junit.jupiter.api.Assertions.assertEquals("REMOVED", removed.path("status").asText());
        org.junit.jupiter.api.Assertions.assertEquals(15, removed.path("soldQuantity").asInt());
        org.junit.jupiter.api.Assertions.assertTrue(removed.path("refundNeeded").asBoolean());
        org.junit.jupiter.api.Assertions.assertTrue(removed.path("photoUrls").asText().contains("onsite-cake-1.jpg"));

        // 异常档案登记「换品资质不足被下架」
        JsonNode incidents = data(call(staff, "GET", "/api/onsite/events/" + eventId + "/incidents", null));
        boolean removedIncident = false;
        for (JsonNode i : incidents) {
            if ("GOODS_CHANGE_REMOVED".equals(i.path("type").asText())) {
                removedIncident = true;
            }
        }
        org.junit.jupiter.api.Assertions.assertTrue(removedIncident, "应登记换品下架异常");

        // 信用扣分 + 下次报名需提前复核 + 食品风险记录
        JsonNode mayiAfterRemove = vendorUser(admin, "mayi");
        org.junit.jupiter.api.Assertions.assertEquals(creditBefore - 10, mayiAfterRemove.path("creditScore").asInt());
        org.junit.jupiter.api.Assertions.assertTrue(mayiAfterRemove.path("nextApplyReviewRequired").asBoolean());
        org.junit.jupiter.api.Assertions.assertEquals(1, mayiAfterRemove.path("foodRiskLevel").asInt());

        // ---------- 3. 下次报名需提前复核：直接报名被拦截，管理员复核后放行 ----------
        Map<String, Object> event2 = new HashMap<>();
        event2.put("name", "换品复核测试活动B");
        event2.put("eventDate", "2026-09-15"); // 日期早于种子活动，避免影响其他测试取 events.get(0)
        event2.put("location", "社区活动室");
        event2.put("publicGoal", "测试");
        event2.put("goalAmount", new BigDecimal("100"));
        long event2Id = data(call(admin, "POST", "/api/events", event2)).path("id").asLong();

        Map<String, Object> applyFood = new HashMap<>();
        applyFood.put("eventId", event2Id);
        applyFood.put("shopName", "蚂蚁烘焙角");
        applyFood.put("category", "FOOD");
        applyFood.put("goodsDescription", "自制蛋糕");
        applyFood.put("foodQualification", "COMPLETE");
        applyFood.put("foodLicenseNo", "食摊字 2026-1001");
        applyFood.put("donationRate", new BigDecimal("10"));
        applyFood.put("paymentMethod", "CASH");
        org.junit.jupiter.api.Assertions.assertNotEquals(0,
                respCode(call(mayi, "POST", "/api/my/applications", applyFood)),
                "有提前复核标记的摊主不能直接报名");

        data(call(admin, "POST", "/api/admin/users/" + mayiUserId + "/clear-review-flag", null));
        org.junit.jupiter.api.Assertions.assertFalse(vendorUser(admin, "mayi").path("nextApplyReviewRequired").asBoolean());
        // 食品风险等级 1（曾被下架）不禁止食品报名，仅提示重点核验
        JsonNode applied = data(call(mayi, "POST", "/api/my/applications", applyFood));
        org.junit.jupiter.api.Assertions.assertEquals("SUBMITTED", applied.path("status").asText());

        // ---------- 4. 食品安全风险事件：影响后续食品类报名 ----------
        data(call(staff, "POST", "/api/onsite/incidents", Map.of(
                "eventId", eventId, "applicationId", mayiAppId,
                "type", "FOOD_RISK", "description", "居民食用蛋糕后肠胃不适，送医观察", "severity", 3)));
        JsonNode mayiRisk = vendorUser(admin, "mayi");
        org.junit.jupiter.api.Assertions.assertEquals(2, mayiRisk.path("foodRiskLevel").asInt());
        org.junit.jupiter.api.Assertions.assertTrue(mayiRisk.path("nextApplyReviewRequired").asBoolean());
        org.junit.jupiter.api.Assertions.assertEquals(creditBefore - 10 - 15, mayiRisk.path("creditScore").asInt());

        Map<String, Object> event3 = new HashMap<>(event2);
        event3.put("name", "换品复核测试活动C");
        event3.put("eventDate", "2026-09-16");
        long event3Id = data(call(admin, "POST", "/api/events", event3)).path("id").asLong();

        // 复核标记再次拦截；管理员复核清除后，食品类仍因风险事件被拦截，其他品类可报名
        applyFood.put("eventId", event3Id);
        org.junit.jupiter.api.Assertions.assertNotEquals(0, respCode(call(mayi, "POST", "/api/my/applications", applyFood)));
        data(call(admin, "POST", "/api/admin/users/" + mayiUserId + "/clear-review-flag", null));
        org.junit.jupiter.api.Assertions.assertNotEquals(0, respCode(call(mayi, "POST", "/api/my/applications", applyFood)),
                "食品安全风险事件后应限制食品类报名");

        Map<String, Object> applyHandmade = new HashMap<>(applyFood);
        applyHandmade.put("category", "HANDMADE");
        applyHandmade.put("shopName", "蚂蚁手作铺");
        applyHandmade.put("goodsDescription", "手工布艺");
        applyHandmade.put("foodQualification", "NONE");
        JsonNode handmadeApp = data(call(mayi, "POST", "/api/my/applications", applyHandmade));
        org.junit.jupiter.api.Assertions.assertEquals("SUBMITTED", handmadeApp.path("status").asText());

        // ---------- 5. 资质齐全且条件满足：允许售卖，更新品类/用电/食品安全提示 ----------
        Map<String, Object> submit2 = new HashMap<>();
        submit2.put("applicationId", mayiAppId);
        submit2.put("newCategory", "FOOD");
        submit2.put("newGoodsDescription", "新增瓶装自制酸梅汤（预包装）");
        submit2.put("involvesFood", true);
        submit2.put("foodQualification", "COMPLETE");
        submit2.put("foodLicenseNo", "食摊字 2026-1001");
        submit2.put("stallConditionOk", true);
        submit2.put("stallConditionNote", "有冷藏箱与防蝇罩，条件达标");
        submit2.put("powerNeeded", true);
        submit2.put("powerWanted", 600);
        long change2Id = data(call(staff, "POST", "/api/onsite/goods-changes", submit2)).path("id").asLong();

        Map<String, Object> approve = new HashMap<>();
        approve.put("decision", "APPROVE");
        approve.put("reviewComment", "资质齐全、条件达标，允许售卖");
        JsonNode approved = data(call(staff, "POST", "/api/onsite/goods-changes/" + change2Id + "/review", approve));
        org.junit.jupiter.api.Assertions.assertEquals("APPROVED", approved.path("status").asText());
        org.junit.jupiter.api.Assertions.assertFalse(approved.path("foodSafetyNotice").asText().isBlank(),
                "涉及食品应生成食品安全提示");
        org.junit.jupiter.api.Assertions.assertFalse(approved.path("powerUpdateNote").asText().isBlank(),
                "功率变化应生成用电变更提示");

        // 报名档案已更新：品类、资质、用电、提示
        JsonNode appAfter = data(call(admin, "GET", "/api/applications/" + mayiAppId, null));
        org.junit.jupiter.api.Assertions.assertEquals("FOOD", appAfter.path("category").asText());
        org.junit.jupiter.api.Assertions.assertEquals("COMPLETE", appAfter.path("foodQualification").asText());
        org.junit.jupiter.api.Assertions.assertEquals("食摊字 2026-1001", appAfter.path("foodLicenseNo").asText());
        org.junit.jupiter.api.Assertions.assertTrue(appAfter.path("powerNeeded").asBoolean());
        org.junit.jupiter.api.Assertions.assertEquals(600, appAfter.path("powerWanted").asInt());
        org.junit.jupiter.api.Assertions.assertFalse(appAfter.path("foodSafetyNotice").asText().isBlank());
        org.junit.jupiter.api.Assertions.assertTrue(appAfter.path("goodsDescription").asText().contains("现场换品新增"));
        org.junit.jupiter.api.Assertions.assertTrue(appAfter.path("goodsChanges").size() >= 2,
                "报名详情应串联换品审核记录");
        // 允许售卖也扣少量信用（未提前申报）
        org.junit.jupiter.api.Assertions.assertEquals(creditBefore - 10 - 15 - 2,
                vendorUser(admin, "mayi").path("creditScore").asInt());

        // ---------- 6. 资质不足时不能允许售卖 ----------
        Map<String, Object> submit3 = new HashMap<>(submit2);
        submit3.put("newGoodsDescription", "再次临时加售自制酸奶");
        submit3.put("foodQualification", "INCOMPLETE");
        long change3Id = data(call(staff, "POST", "/api/onsite/goods-changes", submit3)).path("id").asLong();
        org.junit.jupiter.api.Assertions.assertNotEquals(0,
                respCode(call(staff, "POST", "/api/onsite/goods-changes/" + change3Id + "/review",
                        Map.of("decision", "APPROVE"))),
                "资质不足不允许售卖");
        // 改做下架（无需退款）
        Map<String, Object> remove3 = new HashMap<>();
        remove3.put("decision", "REMOVE");
        remove3.put("photoUrls", List.of("yogurt-1.jpg"));
        remove3.put("soldQuantity", 0);
        remove3.put("refundNeeded", false);
        data(call(staff, "POST", "/api/onsite/goods-changes/" + change3Id + "/review", remove3));

        // ---------- 7. 活动档案归集换品记录，复盘建议覆盖换品与食品风险 ----------
        JsonNode archive = data(call(admin, "GET", "/api/events/" + eventId + "/archive", null));
        org.junit.jupiter.api.Assertions.assertTrue(archive.path("goodsChanges").size() >= 3,
                "活动档案应归集换品审核记录");

        JsonNode review = data(call(admin, "GET", "/api/events/" + eventId + "/review", null));
        boolean hasGoodsChangeTip = false, hasFoodRiskTip = false;
        for (JsonNode tip : review.path("suggestions")) {
            String t = tip.asText();
            if (t.contains("换品")) {
                hasGoodsChangeTip = true;
            }
            if (t.contains("食品安全风险")) {
                hasFoodRiskTip = true;
            }
        }
        org.junit.jupiter.api.Assertions.assertTrue(hasGoodsChangeTip, "复盘建议应覆盖现场换品问题");
        org.junit.jupiter.api.Assertions.assertTrue(hasFoodRiskTip, "复盘建议应覆盖食品安全风险事件");
    }
}
