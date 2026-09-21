package com.interviewai.rag.index;

public enum RagIndexStatus {
    PENDING,
    INDEXING,
    INDEXED,
    DELETING,
    DELETED,
    FAILED,
    CANCELLED
}
