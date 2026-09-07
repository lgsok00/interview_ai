package com.interviewai.rag.document;

public enum RagSourceType {
    COMPANY(RagVisibility.AUTHENTICATED_SHARED),
    JOB_POSTING(RagVisibility.AUTHENTICATED_SHARED),
    COVER_LETTER(RagVisibility.PRIVATE),
    RESUME(RagVisibility.PRIVATE);

    private final RagVisibility visibility;


    RagSourceType(RagVisibility visibility) {
        this.visibility = visibility;
    }


    public RagVisibility visibility() {
        return visibility;
    }


    public boolean isPrivate() {
        return visibility == RagVisibility.PRIVATE;
    }


    public boolean belongsToCompany() {
        return this == COMPANY || this == JOB_POSTING;
    }
}
