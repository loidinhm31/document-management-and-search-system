package com.dms.document.interaction.model;

import lombok.Data;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.mapping.Document;
import org.springframework.data.mongodb.core.mapping.Field;

import java.util.Date;
import java.util.Map;
import java.util.Set;

@Data
@Document(collection = "document_preferences")
public class DocumentPreferences {
    @Id
    private String id;

    @Indexed
    @Field("user_id")
    private String userId;

    @Field("preferred_majors")
    private Set<String> preferredMajors;

    @Field("preferred_levels")
    private Set<String> preferredLevels;

    @Field("preferred_categories")
    private Set<String> preferredCategories;

    @Field("preferred_tags")
    private Set<String> preferredTags;

    @Field("language_preferences")
    private Set<String> languagePreferences;

    // Weighted scores for different content types (0-1)
    @Field("content_type_weights")
    private Map<String, Double> contentTypeWeights;

    // Interaction history aggregates
    @Field("category_interaction_counts")
    private Map<String, Integer> categoryInteractionCounts;

    @Field("tag_interaction_counts")
    private Map<String, Integer> tagInteractionCounts;

    @Field("major_interaction_counts")
    private Map<String, Integer> majorInteractionCounts;

    @Field("recent_viewed_documents")
    private Set<String> recentViewedDocuments;

    @Field("created_at")
    private Date createdAt;

    @Field("updated_at")
    private Date updatedAt;
}