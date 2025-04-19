package com.dms.document.interaction.repository;

import com.dms.document.interaction.model.DocumentRecommendation;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.UUID;

@Repository
public interface DocumentRecommendationRepository extends JpaRepository<DocumentRecommendation, Long> {

    boolean existsByDocumentIdAndMentorId(String documentId, UUID mentorId);

    Optional<DocumentRecommendation> findByDocumentIdAndMentorId(String documentId, UUID mentorId);

}