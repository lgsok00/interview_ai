package com.interviewai.ai.usage;

public enum AiUsageFeature {
    INITIAL_QUESTIONS,
    FOLLOW_UP,
    ANSWER_EVALUATION,
    COVER_LETTER_DRAFT,
    PERSONAL_EMBEDDING;


    public boolean isChat() {
        return this != PERSONAL_EMBEDDING;
    }
}
