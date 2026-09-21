package com.interviewai.rag.controller;

import com.interviewai.rag.search.RagSearchResult;
import com.interviewai.rag.service.RagSearchService;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/rag")
@ConditionalOnProperty(
        prefix = "rag.search",
        name = "enabled",
        havingValue = "true"
)
public class RagSearchController {

    private final RagSearchService searchService;


    public RagSearchController(RagSearchService searchService) {
        this.searchService = searchService;
    }


    @GetMapping("/search")
    public List<RagSearchResult> search(@AuthenticationPrincipal Jwt jwt, @RequestParam String query) {
        return searchService.search(jwt.getSubject(), query);
    }
}
