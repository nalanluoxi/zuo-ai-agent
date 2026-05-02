package com.example.zuoaiagent.controller;


import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/health")
public class TestController {

    @GetMapping
    public String healthCheck() {
        return "ok";
    }
}

