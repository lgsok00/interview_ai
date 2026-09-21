package com.interviewai.company.controller;

import com.interviewai.company.dto.CompanyResponse;
import com.interviewai.company.dto.CreateCompanyRequest;
import com.interviewai.company.dto.UpdateCompanyRequest;
import com.interviewai.company.service.CompanyService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.net.URI;

@RestController
@RequestMapping("/api/admin/companies")
public class AdminCompanyController {

    private final CompanyService companyService;


    public AdminCompanyController(CompanyService companyService) {
        this.companyService = companyService;
    }


    @PostMapping
    public ResponseEntity<CompanyResponse> create(
            @AuthenticationPrincipal Jwt jwt,
            @Valid @RequestBody CreateCompanyRequest request
    ) {
        CompanyResponse response = companyService.create(jwt.getSubject(), request);

        return ResponseEntity
                .created(URI.create("/api/companies/" + response.id()))
                .body(response);
    }


    @PutMapping("/{companyId}")
    public CompanyResponse update(
            @AuthenticationPrincipal Jwt jwt,
            @PathVariable Long companyId,
            @Valid @RequestBody UpdateCompanyRequest request
    ) {
        return companyService.update(jwt.getSubject(), companyId, request);
    }


    @DeleteMapping("/{companyId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(@AuthenticationPrincipal Jwt jwt, @PathVariable Long companyId) {
        companyService.delete(jwt.getSubject(), companyId);
    }
}
