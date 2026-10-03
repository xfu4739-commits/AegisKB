package org.aegiskb.ai.controller;

import org.aegiskb.ai.service.SpringAiAlibabaAgentService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/agent")
public class SimpleAgentController {

    private final SpringAiAlibabaAgentService agentService;

    public SimpleAgentController(SpringAiAlibabaAgentService agentService) {
        this.agentService = agentService;
    }

    @GetMapping("/hello")
    public String hello(
        @RequestParam(value = "question", defaultValue = "你好，请用三点介绍一下 Spring AI Alibaba ReactAgent 的作用")
        String question) {
        return this.agentService.simpleReply(question);
    }

}
