package com.careflow.ai.repository.projection;

import java.util.UUID;

public interface ChunkSearchResultProjection {
    UUID getId();
    UUID getDocumentId();
    String getDocumentCode();
    String getDocumentTitle();
    String getSpecialty();
    String getSectionTitle();
    String getContent();
    String getMetadata();
    Double getSimilarityScore();
}
