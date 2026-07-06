package com.example.validationdemo;

import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class TestController {

    @PostMapping("/test2")
    public Integer test(@Validated @RequestBody TestRequest req) {
        return req.getId();
    }
}
