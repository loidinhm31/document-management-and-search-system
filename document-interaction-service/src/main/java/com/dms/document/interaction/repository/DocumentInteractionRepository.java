package com.dms.document.interaction.repository;

import com.dms.document.interaction.dto.AggregatedInteractionStats;
import com.dms.document.interaction.model.DocumentInteraction;
import org.springframework.data.mongodb.repository.Aggregation;
import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.data.mongodb.repository.Query;
import org.springframework.stereotype.Repository;

import java.util.Date;
import java.util.List;
import java.util.Optional;

@Repository
public interface DocumentInteractionRepository extends MongoRepository<DocumentInteraction, String> {

    Optional<DocumentInteraction> findByUserIdAndDocumentId(String userId, String documentId);

    @Query(value = "{ 'user_id': ?0, 'last_interaction_date': { $gte: ?1 } }")
    List<DocumentInteraction> findRecentInteractions(String userId, Date since);

    @Aggregation(pipeline = {
            "{ $match: { 'user_id': ?0, 'last_interaction_date': { $gte: ?1 } } }",
            """
                 {
                     $group: {
                         _id: null,
                         totalViews: { $sum: "$interactions.VIEW.count" },
                         totalDownloads: { $sum: "$interactions.DOWNLOAD.count" },
                         totalComments: { $sum: "$interactions.COMMENT.count" },
                         totalShares: { $sum: "$interactions.SHARE.count" },
                         uniqueDocuments: { $sum: 1 }
                     }
                 }
            """
    })
    AggregatedInteractionStats getAggregatedStats(String userId, Date since);
}