package com.interviewai.company.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Getter;

import java.time.LocalDateTime;

@Entity
@Getter
@Table(name = "companies")
public class Company {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 100)
    private String name;

    @Column(length = 100)
    private String industry;

    @Column(nullable = false, columnDefinition = "MEDIUMTEXT")
    private String description;

    @Column(name = "website_url", length = 2048)
    private String websiteUrl;

    @Column(length = 200)
    private String location;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;


    protected Company() {

    }


    public static Company create(
            String name,
            String industry,
            String description,
            String websiteUrl,
            String location,
            LocalDateTime now
    ) {
        Company company = new Company();
        company.createdAt = now;
        company.update(name, industry, description, websiteUrl, location, now);

        return company;
    }


    public void update(
            String name,
            String industry,
            String description,
            String websiteUrl,
            String location,
            LocalDateTime now
    ) {
        this.name = name;
        this.industry = industry;
        this.description = description;
        this.websiteUrl = websiteUrl;
        this.location = location;
        this.updatedAt = now;
    }
}
