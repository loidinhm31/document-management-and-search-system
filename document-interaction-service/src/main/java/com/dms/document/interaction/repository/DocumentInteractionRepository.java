package com.dms.document.interaction.repository;

import com.dms.document.interaction.model.DocumentInteraction;
import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.stereotype.Repository;

import java.util.Date;
import java.util.List;

@Repository
public interface DocumentInteractionRepository extends MongoRepository<DocumentInteraction, String> {
    List<DocumentInteraction> findByUserIdAndCreatedAtAfter(String userId, Date date);
    List<DocumentInteraction> findByUserIdAndDocumentId(String userId, String documentId);
}