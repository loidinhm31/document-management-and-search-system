package com.dms.document.interaction.repository;

import com.dms.document.interaction.model.UserDocumentHistory;
import org.springframework.data.mongodb.repository.Aggregation;
import org.springframework.data.mongodb.repository.MongoRepository;

public interface UserDocumentHistoryRepository extends MongoRepository<UserDocumentHistory, String> {
    @Aggregation(pipeline = {
            "{ $match: { 'document_id': ?0, 'action_type': 'DOWNLOAD_FILE' } }",
            "{ $group: { _id: null, downloadCount: { $sum: 1 } } }"
    })
    Integer getDownloadCountForDocument(String documentId);
}
