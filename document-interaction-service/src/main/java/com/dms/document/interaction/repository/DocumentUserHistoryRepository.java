package com.dms.document.interaction.repository;

import com.dms.document.interaction.model.ActionCountResult;
import com.dms.document.interaction.model.DocumentUserHistory;
import org.springframework.data.mongodb.repository.Aggregation;
import org.springframework.data.mongodb.repository.MongoRepository;

import java.util.List;

public interface DocumentUserHistoryRepository extends MongoRepository<DocumentUserHistory, String> {
    @Aggregation(pipeline = {
            "{ $match: { 'document_id': ?0 } }",
            """
            { $group: {
                _id: '$action_type',
                count: { $sum: 1 }
              }
            }
            """
    })
    List<ActionCountResult> getActionCountsForDocument(String documentId);
}