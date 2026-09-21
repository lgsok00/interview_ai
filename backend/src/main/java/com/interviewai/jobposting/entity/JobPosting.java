package com.interviewai.jobposting.entity;

import com.interviewai.company.entity.Company;
import com.interviewai.jobposting.enums.EmploymentType;
import com.interviewai.jobposting.enums.JobPostingStatus;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import lombok.Getter;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.LocalDateTime;

@Entity
@Getter
@Table(name = "job_postings")
public class JobPosting {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "company_id", nullable = false, updatable = false)
    private Company company;

    @Column(nullable = false, length = 200)
    private String title;

    @Column(name = "job_role", nullable = false, length = 100)
    private String jobRole;

    @Enumerated(EnumType.STRING)
    @JdbcTypeCode(SqlTypes.VARCHAR)
    @Column(name = "employment_type", nullable = false, length = 20)
    private EmploymentType employmentType;

    @Column(length = 200)
    private String location;

    @Column(nullable = false, columnDefinition = "MEDIUMTEXT")
    private String description;

    @Column(name = "source_url", length = 2048)
    private String sourceUrl;

    @Column(name = "opens_at")
    private LocalDateTime opensAt;

    @Column(name = "closes_at")
    private LocalDateTime closesAt;

    @Column(name = "manually_closed", nullable = false)
    private boolean manuallyClosed;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;


    protected JobPosting() {

    }


    public static JobPosting create(
            Company company,
            String title,
            String jobRole,
            EmploymentType employmentType,
            String location,
            String description,
            String sourceUrl,
            LocalDateTime opensAt,
            LocalDateTime closesAt,
            boolean manuallyClosed,
            LocalDateTime now
    ) {
        JobPosting posting = new JobPosting();
        posting.company = company;
        posting.createdAt = now;

        posting.update(
                title,
                jobRole,
                employmentType,
                location,
                description,
                sourceUrl,
                opensAt,
                closesAt,
                manuallyClosed,
                now
        );

        return posting;
    }


    public void update(
            String title,
            String jobRole,
            EmploymentType employmentType,
            String location,
            String description,
            String sourceUrl,
            LocalDateTime opensAt,
            LocalDateTime closesAt,
            boolean manuallyClosed,
            LocalDateTime now
    ) {
        this.title = title;
        this.jobRole = jobRole;
        this.employmentType = employmentType;
        this.location = location;
        this.description = description;
        this.sourceUrl = sourceUrl;
        this.opensAt = opensAt;
        this.closesAt = closesAt;
        this.manuallyClosed = manuallyClosed;
        this.updatedAt = now;
    }


    public JobPostingStatus statusAt(LocalDateTime now) {
        if (manuallyClosed || (closesAt != null && !now.isBefore(closesAt))) {
            return JobPostingStatus.CLOSED;
        }

        if (opensAt != null && now.isBefore(opensAt)) {
            return JobPostingStatus.SCHEDULED;
        }

        return JobPostingStatus.OPEN;
    }
}
