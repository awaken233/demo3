package com.example.validationdemo;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
class TestControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Test
    void shouldReturnIdWhenRequestBodyIsValid() throws Exception {
        mockMvc.perform(post("/test2")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"id\":1,\"name\":\"demo\"}"))
                .andExpect(status().isOk())
                .andExpect(content().string("1"));
    }

    @Test
    void shouldReturnBadRequestWhenRequestBodyFieldIsInvalid() throws Exception {
        mockMvc.perform(post("/test2")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"id\":null,\"name\":\"\"}"))
                .andExpect(status().isBadRequest());
    }
}
