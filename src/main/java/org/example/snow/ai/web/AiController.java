package org.example.snow.ai.web;

import org.example.snow.ai.application.OllamaService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class AiController {

    private final OllamaService ollamaService;

    public AiController(OllamaService ollamaService) {
        this.ollamaService = ollamaService;
    }

    @GetMapping("/api/ai/ask")
    public String ask(@RequestParam String q) {
        return ollamaService.ask(q);
    }
}