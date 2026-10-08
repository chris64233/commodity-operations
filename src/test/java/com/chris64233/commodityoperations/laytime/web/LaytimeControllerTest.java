package com.chris64233.commodityoperations.laytime.web;

import com.chris64233.commodityoperations.laytime.repository.OperationEventRepository;
import com.chris64233.commodityoperations.laytime.repository.SettlementRepository;
import com.chris64233.commodityoperations.laytime.repository.VoyageRepository;
import tools.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
class LaytimeControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private VoyageRepository voyageRepository;
    @Autowired
    private OperationEventRepository eventRepository;
    @Autowired
    private SettlementRepository settlementRepository;

    @Test
    void 全链路http接口_重放与冲突状态码正确() throws Exception {
        postJson("/api/laytime/voyages", """
                {"voyageNo":"V-HTTP","allowedLaytime":"PT6H",
                 "demurrageRatePerHour":100.00,"currency":"USD",
                 "stopRule":"DEDUCT_PAUSE_INTERVALS"}""")
                .andExpect(status().isCreated());

        event("e1", "BERTH", "2026-10-03T08:00:00Z");
        event("e2", "START", "2026-10-03T10:00:00Z");
        event("e3", "PAUSE", "2026-10-03T12:00:00Z");
        event("e4", "RESUME", "2026-10-03T13:00:00Z");
        event("e5", "COMPLETE", "2026-10-03T18:00:00Z");

        // 同号同内容重放
        event("e3", "PAUSE", "2026-10-03T12:00:00Z");

        mockMvc.perform(post("/api/laytime/voyages/V-HTTP/settlements")
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.versionNo").value(1))
                .andExpect(jsonPath("$.usedSeconds").value(7 * 3600))
                .andExpect(jsonPath("$.demurrageAmount").value(100.00));

        mockMvc.perform(post("/api/laytime/voyages/V-HTTP/settlements/confirm"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("CONFIRMED"));

        // 同号内容变化 -> 409
        event("V-HTTP", "e3", "PAUSE", "2026-10-03T12:30:00Z", 409);

        mockMvc.perform(get("/api/laytime/voyages/V-HTTP"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.rawEvents.length()").value(5))
                .andExpect(jsonPath("$.rawEvents[0].receivedAt").exists())
                .andExpect(jsonPath("$.settlements[0].suspensions.length()").value(1))
                .andExpect(jsonPath("$.settlements[0].timeline.length()").value(5))
                .andExpect(jsonPath("$.settlements[0].differences.length()").value(0));
    }

    @Test
    void 参数非法返回400_未知航次返回404() throws Exception {
        mockMvc.perform(post("/api/laytime/voyages")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"voyageNo":"","allowedLaytime":"BAD",
                                 "demurrageRatePerHour":-1,"currency":"USD"}"""))
                .andExpect(status().isBadRequest());

        mockMvc.perform(get("/api/laytime/voyages/NO-SUCH"))
                .andExpect(status().isNotFound());
    }

    @Test
    void 时间线无法结算返回422() throws Exception {
        postJson("/api/laytime/voyages", """
                {"voyageNo":"V-BAD","allowedLaytime":"PT6H",
                 "demurrageRatePerHour":100.00,"currency":"USD"}""")
                .andExpect(status().isCreated());
        event("V-BAD", "e1", "BERTH", "2026-10-03T08:00:00Z", 200);
        event("V-BAD", "e2", "START", "2026-10-03T10:00:00Z", 200);

        mockMvc.perform(post("/api/laytime/voyages/V-BAD/settlements")
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.message").value(org.hamcrest.Matchers.containsString("完工")));

        Long voyageId = voyageRepository.findByVoyageNo("V-BAD").orElseThrow().getId();
        assertThat(settlementRepository.findByVoyage_IdOrderByVersionNoAsc(voyageId)).isEmpty();
        assertThat(eventRepository.findByVoyage_IdOrderByOccurredAtAscIdAsc(voyageId)).hasSize(2);
    }

    private ResultActions postJson(String url, String json) throws Exception {
        return mockMvc.perform(post(url).contentType(MediaType.APPLICATION_JSON).content(json));
    }

    private void event(String no, String type, String occurredAt) throws Exception {
        event("V-HTTP", no, type, occurredAt, 200);
    }

    private void event(String voyageNo, String no, String type, String occurredAt, int status)
            throws Exception {
        String body = objectMapper.writeValueAsString(Map.of(
                "externalEventNo", no, "eventType", type, "occurredAt", occurredAt));
        mockMvc.perform(post("/api/laytime/voyages/" + voyageNo + "/events")
                        .contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().is(status));
    }
}
