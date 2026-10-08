package com.chris64233.commodityoperations.laytime.web;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import static org.hamcrest.Matchers.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
class LaytimeControllerWebTest {

    @Autowired
    private MockMvc mockMvc;

    @Test
    void endToEndHttpFlow() throws Exception {
        String voyage = """
                {"voyageCode":"V-HTTP-1","vesselName":"MV HTTP",
                 "allowedLaytimeSeconds":86400,"demurrageRatePerDay":24000,
                 "currency":"USD","countingBasis":"ON_BERTH"}
                """;
        mockMvc.perform(post("/api/laytime/voyages")
                        .contentType(MediaType.APPLICATION_JSON).content(voyage))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.voyageCode").value("V-HTTP-1"));

        String base = "/api/laytime/voyages/V-HTTP-1/events";
        mockMvc.perform(post(base).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"externalEventNo\":\"e1\",\"type\":\"BERTH\","
                                + "\"occurredAt\":\"2026-10-01T00:00:00Z\"}"))
                .andExpect(status().isAccepted())
                .andExpect(jsonPath("$.replayed").value(false));

        // 重放同号同内容 → 幂等
        mockMvc.perform(post(base).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"externalEventNo\":\"e1\",\"type\":\"BERTH\","
                                + "\"occurredAt\":\"2026-10-01T00:00:00Z\"}"))
                .andExpect(status().isAccepted())
                .andExpect(jsonPath("$.replayed").value(true));

        // 同号内容变化 → 409
        mockMvc.perform(post(base).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"externalEventNo\":\"e1\",\"type\":\"START\","
                                + "\"occurredAt\":\"2026-10-01T00:00:00Z\"}"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.message", containsString("已存在但内容不同")));

        mockMvc.perform(post(base).contentType(MediaType.APPLICATION_JSON)
                .content("{\"externalEventNo\":\"e2\",\"type\":\"START\","
                        + "\"occurredAt\":\"2026-10-01T02:00:00Z\"}"))
                .andExpect(status().isAccepted());
        mockMvc.perform(post(base).contentType(MediaType.APPLICATION_JSON)
                .content("{\"externalEventNo\":\"e3\",\"type\":\"COMPLETE\","
                        + "\"occurredAt\":\"2026-10-02T06:00:00Z\"}"))
                .andExpect(status().isAccepted());

        // 30h 已用 - 24h 允许 = 6h 滞期；6/24*24000 = 6000
        mockMvc.perform(post("/api/laytime/voyages/V-HTTP-1/settlements/generate"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.versionNo").value(1))
                .andExpect(jsonPath("$.demurrageAmount").value(6000.00))
                .andExpect(jsonPath("$.usedLaytimeSeconds").value(108000));

        mockMvc.perform(post("/api/laytime/voyages/V-HTTP-1/settlements/confirm-current"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("CONFIRMED"));

        mockMvc.perform(get("/api/laytime/voyages/V-HTTP-1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.rawEvents.length()").value(3))
                .andExpect(jsonPath("$.timeline.events.length()").value(3))
                .andExpect(jsonPath("$.timeline.suspensions.length()").value(0))
                .andExpect(jsonPath("$.settlements.length()").value(1))
                .andExpect(jsonPath("$.currentSettlement.status").value("CONFIRMED"));
    }

    @Test
    void invalidTimelineReturns422WithProblems() throws Exception {
        String voyage = """
                {"voyageCode":"V-HTTP-2","allowedLaytimeSeconds":3600,
                 "demurrageRatePerDay":1000,"currency":"USD","countingBasis":"ON_WORK"}
                """;
        mockMvc.perform(post("/api/laytime/voyages")
                        .contentType(MediaType.APPLICATION_JSON).content(voyage))
                .andExpect(status().isCreated());

        mockMvc.perform(post("/api/laytime/voyages/V-HTTP-2/settlements/generate"))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.problems", hasSize(greaterThanOrEqualTo(3))));
    }

    @Test
    void invalidCurrencyReturns400() throws Exception {
        String voyage = """
                {"voyageCode":"V-HTTP-3","allowedLaytimeSeconds":3600,
                 "demurrageRatePerDay":1000,"currency":"US","countingBasis":"ON_WORK"}
                """;
        mockMvc.perform(post("/api/laytime/voyages")
                        .contentType(MediaType.APPLICATION_JSON).content(voyage))
                .andExpect(status().isBadRequest());
    }
}
