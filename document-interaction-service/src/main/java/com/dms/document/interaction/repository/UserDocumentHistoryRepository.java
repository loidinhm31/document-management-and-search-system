package com.dms.document.interaction.repository;

import com.dms.document.interaction.model.UserDocumentHistory;
import org.springframework.data.mongodb.repository.MongoRepository;

public interface UserDocumentHistoryRepository extends MongoRepository<UserDocumentHistory, String> {
}
