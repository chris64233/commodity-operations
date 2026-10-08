package com.chris64233.commodityoperations;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import com.chris64233.commodityoperations.repository.DailyCapacityRepository;
import com.chris64233.commodityoperations.repository.LoadingContractRepository;
import com.chris64233.commodityoperations.repository.NominationHistoryRepository;
import com.chris64233.commodityoperations.repository.VesselGuardRepository;
import com.chris64233.commodityoperations.repository.VesselNominationRepository;

import static org.hamcrest.Matchers.hasSize;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
class NominationApiTest {

    @Autowired
    private MockMvc mockMvc;
    @Autowired
    private LoadingContractRepository contractRepository;
    @Autowired
    private DailyCapacityRepository capacityRepository;
    @Autowired
    private VesselNominationRepository nominationRepository;
    @Autowired
    private NominationHistoryRepository historyRepository;
    @Autowired
    private VesselGuardRepository vesselGuardRepository;

    @BeforeEach
    void cleanUp() {
        historyRepository.deleteAllInBatch();
        nominationRepository.deleteAllInBatch();
        vesselGuardRepository.deleteAllInBatch();
        capacityRepository.deleteAllInBatch();
        contractRepository.deleteAllInBatch();
    }

    @Test
    void fullWorkflowThroughHttp() throws Exception {
        mockMvc.perform(post("/api/contracts")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"contractCode":"C-HTTP","counterparty":"矿主","allowedQuantity":10000,
                                 "windowStart":"2026-10-01","windowEnd":"2026-10-20"}"""))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.remainingQuantity").value(10000));

        mockMvc.perform(post("/api/capacities")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"date":"2026-10-10","availableCapacity":8000}"""))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.remainingCapacity").value(8000));

        mockMvc.perform(post("/api/nominations")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"contractCode":"C-HTTP","vesselCode":"MV-H","vesselName":"HTTP轮",
                                 "estimatedArrival":"2026-10-10T08:00:00","plannedQuantity":5000,
                                 "requestId":"http-req-1"}"""))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.status").value("PENDING"))
                .andExpect(jsonPath("$.id").value(1));

        mockMvc.perform(post("/api/nominations/1/confirm"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("CONFIRMED"));

        mockMvc.perform(get("/api/capacities"))
                .andExpect(jsonPath("$[0].reservedCapacity").value(5000))
                .andExpect(jsonPath("$[0].remainingCapacity").value(3000));

        mockMvc.perform(get("/api/contracts/C-HTTP"))
                .andExpect(jsonPath("$.committedQuantity").value(5000))
                .andExpect(jsonPath("$.remainingQuantity").value(5000));

        mockMvc.perform(post("/api/capacities")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"date":"2026-10-11","availableCapacity":6000}"""))
                .andExpect(status().isCreated());

        mockMvc.perform(post("/api/nominations/1/reschedule")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"newEstimatedArrival":"2026-10-11T09:00:00"}"""))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.estimatedArrival").value("2026-10-11T09:00:00"));

        mockMvc.perform(get("/api/vessels/MV-H/plans"))
                .andExpect(jsonPath("$", hasSize(1)))
                .andExpect(jsonPath("$[0].status").value("CONFIRMED"));

        mockMvc.perform(get("/api/nominations/1/history"))
                .andExpect(jsonPath("$", hasSize(3)))
                .andExpect(jsonPath("$[0].action").value("SUBMITTED"))
                .andExpect(jsonPath("$[2].action").value("RESCHEDULED"));
    }

    @Test
    void httpReturns409OnBusinessConflicts() throws Exception {
        mockMvc.perform(post("/api/contracts")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"contractCode":"C-E","counterparty":"矿主","allowedQuantity":100,
                                 "windowStart":"2026-10-10","windowEnd":"2026-10-20"}"""))
                .andExpect(status().isCreated());
        mockMvc.perform(post("/api/capacities")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"date":"2026-10-10","availableCapacity":1000}"""))
                .andExpect(status().isCreated());

        mockMvc.perform(post("/api/nominations")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"contractCode":"C-E","vesselCode":"MV-E",
                                 "estimatedArrival":"2026-11-01T08:00:00","plannedQuantity":10}"""))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("OUTSIDE_WINDOW"));

        mockMvc.perform(post("/api/nominations")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"contractCode":"C-E","vesselCode":"MV-E",
                                 "estimatedArrival":"2026-10-10T08:00:00","plannedQuantity":500}"""))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("QUANTITY_EXCEEDED"));

        mockMvc.perform(get("/api/contracts/UNKNOWN"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("NOT_FOUND"));
    }
}
