package com.lilsawe.orderdemo.api;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** HTTP 层契约测试：状态码与响应体语义。 */
@SpringBootTest
@AutoConfigureMockMvc
class OrderApiTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    private String createOrder(String key, long amountCent) throws Exception {
        MvcResult result = mockMvc.perform(post("/api/orders")
                        .header("Idempotency-Key", key)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"amountCent\":" + amountCent + "}"))
                .andExpect(status().isCreated())
                .andReturn();
        return objectMapper.readTree(result.getResponse().getContentAsString()).get("orderNo").asText();
    }

    @Test
    @DisplayName("创建订单返回 201，重复 key 返回同一笔订单")
    void createIsIdempotentOverHttp() throws Exception {
        String first = createOrder("API-KEY-1", 9900L);
        String replay = createOrder("API-KEY-1", 9900L);
        assertEquals(first, replay);
    }

    @Test
    @DisplayName("缺少 Idempotency-Key 返回 400")
    void missingKeyReturns400() throws Exception {
        mockMvc.perform(post("/api/orders")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"amountCent\":100}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").exists());
    }

    @Test
    @DisplayName("金额非正数返回 400（Bean Validation 生效）")
    void nonPositiveAmountReturns400() throws Exception {
        mockMvc.perform(post("/api/orders")
                        .header("Idempotency-Key", "API-KEY-2")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"amountCent\":0}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("查询订单返回 200，未知单号返回 400")
    void getOrder() throws Exception {
        String orderNo = createOrder("API-KEY-3", 500L);
        mockMvc.perform(get("/api/orders/" + orderNo))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.orderNo").value(orderNo))
                .andExpect(jsonPath("$.status").value("CREATED"));

        mockMvc.perform(get("/api/orders/NOT-EXIST"))
                .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("非法状态流转返回 409（CREATED 不能直接发货）")
    void illegalTransitionReturns409() throws Exception {
        String orderNo = createOrder("API-KEY-4", 700L);
        mockMvc.perform(post("/api/orders/" + orderNo + "/status")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"status\":\"SHIPPED\"}"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.error").exists());
    }

    @Test
    @DisplayName("合法状态流转成功：CREATED -> PAID")
    void legalTransition() throws Exception {
        String orderNo = createOrder("API-KEY-5", 800L);
        mockMvc.perform(post("/api/orders/" + orderNo + "/status")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"status\":\"paid\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("PAID"));
    }

    @Test
    @DisplayName("对账接口返回报告结构")
    void reconcileEndpoint() throws Exception {
        createOrder("API-KEY-6", 1234L);
        mockMvc.perform(get("/api/reconcile"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.localCount").exists())
                .andExpect(jsonPath("$.matched").exists())
                .andExpect(jsonPath("$.diffs").isArray());
    }
}
