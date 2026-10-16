package com.interviewai.ai.usage;

import org.springframework.http.CacheControl;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/ai")
public class AiUsageController {

    private final AiUsageQueryService service;


    public AiUsageController(AiUsageQueryService service) {
        this.service = service;
    }

    @GetMapping("/usage")
    public ResponseEntity<AiUsageResponse> getUsage(@AuthenticationPrincipal Jwt jwt) {
        return ResponseEntity.ok()
                .cacheControl(CacheControl.noStore())
                .body(service.getUsage(jwt.getSubject()));
    }
}
