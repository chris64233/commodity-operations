package com.chris64233.commodityoperations;

import com.chris64233.commodityoperations.repo.ContractRepository;
import com.chris64233.commodityoperations.repo.MarketPriceRepository;
import com.chris64233.commodityoperations.repo.PricingOrderRepository;
import com.chris64233.commodityoperations.repo.SettlementVersionRepository;
import com.jayway.jsonpath.JsonPath;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import java.time.Instant;
import java.time.temporal.ChronoUnit;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
class ContractApiWebTest {

    @Autowired
    private MockMvc mockMvc;
    @Autowired
    private SettlementVersionRepository settlementVersionRepository;
    @Autowired
    private PricingOrderRepository pricingOrderRepository;
    @Autowired
    private ContractRepository contractRepository;
    @Autowired
    private MarketPriceRepository marketPriceRepository;

    @BeforeEach
    void cleanDatabase() {
        settlementVersionRepository.deleteAllInBatch();
        pricingOrderRepository.deleteAllInBatch();
        contractRepository.deleteAllInBatch();
        marketPriceRepository.deleteAllInBatch();
    }

    private String iso(Instant t) {
        return t.toString();
    }

    @Test
    void registerPricingAndFinalSettleOverHttp() throws Exception {
        Instant now = Instant.now();
        String priceJson = """
                {
                  "source": "LME", "priceVersion": "W1", "commodity": "COPPER",
                  "currency": "USD", "price": "10100.00",
                  "validFrom": "%s", "validTo": "%s"
                }
                """.formatted(iso(now.minus(1, ChronoUnit.DAYS)), iso(now.plus(1, ChronoUnit.DAYS)));
        mockMvc.perform(post("/api/market-prices")
                        .contentType(MediaType.APPLICATION_JSON).content(priceJson))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.price").value(10100));

        String contractJson = """
                {
                  "contractNo": "WEB-01", "commodity": "COPPER", "direction": "BUY",
                  "quantity": "100", "currency": "USD", "provisionalPrice": "10000.00",
                  "pricingWindowStart": "%s", "pricingWindowEnd": "%s",
                  "allowedSources": ["LME"]
                }
                """.formatted(iso(now.minus(1, ChronoUnit.DAYS)), iso(now.plus(1, ChronoUnit.DAYS)));
        mockMvc.perform(post("/api/contracts")
                        .contentType(MediaType.APPLICATION_JSON).content(contractJson))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.unpricedQty").value(100))
                .andExpect(jsonPath("$.provisionalAmount").value(1000000))
                .andExpect(jsonPath("$.settlementVersions[0].type").value("PROVISIONAL"));

        String contractId = JsonPath.read(mockMvc.perform(get("/api/contracts/WEB-01"))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString(), "$.id").toString();

        mockMvc.perform(get("/api/contracts/WEB-01"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").exists());

        String pricingJson = """
                {
                  "externalPricingNo": "WEB-P1", "quantity": "100",
                  "priceSource": "LME", "priceVersion": "W1",
                  "premiumDiscount": "0", "pricedAt": "%s"
                }
                """.formatted(iso(now));
        mockMvc.perform(post("/api/contracts/" + contractId + "/pricings")
                        .contentType(MediaType.APPLICATION_JSON).content(pricingJson))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("FULLY_PRICED"))
                .andExpect(jsonPath("$.pricings[0].adjustmentAmount")
                        .value(10000));

        mockMvc.perform(post("/api/contracts/" + contractId + "/final-settlement"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("FINALLY_SETTLED"))
                .andExpect(jsonPath("$.finalAmount")
                        .value(1010000));

        // 迟到点价：422，且最终结算版本仍然只有一份。
        mockMvc.perform(post("/api/contracts/" + contractId + "/pricings")
                        .contentType(MediaType.APPLICATION_JSON).content(
                                pricingJson.replace("WEB-P1", "WEB-LATE")))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.message").exists());
    }

    @Test
    void invalidRequestReturns400AndUnknownContractReturns404() throws Exception {
        String invalid = """
                {
                  "contractNo": "", "commodity": "COPPER", "direction": "BUY",
                  "quantity": "0", "currency": "USD", "provisionalPrice": "10000",
                  "pricingWindowStart": "2026-01-01T00:00:00Z",
                  "pricingWindowEnd": "2026-02-01T00:00:00Z",
                  "allowedSources": ["LME"]
                }
                """;
        mockMvc.perform(post("/api/contracts")
                        .contentType(MediaType.APPLICATION_JSON).content(invalid))
                .andExpect(status().isBadRequest());

        mockMvc.perform(get("/api/contracts/NO-SUCH-CONTRACT"))
                .andExpect(status().isNotFound());
    }
}
