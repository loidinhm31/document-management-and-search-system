package com.dms.processor.mapper;

import com.dms.processor.opensearch.DocumentIndex;
import com.dms.processor.model.DocumentInformation;
import org.springframework.stereotype.Component;

@Component
public class DocumentIndexMapper {
    public DocumentIndex toDocumentIndex(DocumentInformation doc) {
        return DocumentIndex.builder()
                .id(doc.getId())
                .filename(doc.getFilename())
                .content(doc.getContent())
                .userId(doc.getUserId())
                .mimeType(doc.getMimeType())
                .documentType(doc.getDocumentType())
                .summary(doc.getSummary())
                .majors(doc.getMajors())
                .courseCodes(doc.getCourseCodes())
                .courseLevel(doc.getCourseLevel())
                .categories(doc.getCategories())
                .tags(doc.getTags())
                .fileSize(doc.getFileSize())
                .sharingType(doc.getSharingType())
                .sharedWith(doc.getSharedWith())
                .deleted(doc.isDeleted())
                .status(doc.getStatus())
                .language(doc.getLanguage())
                .createdAt(doc.getCreatedAt())
                .reportStatus(doc.getReportStatus())
                .recommendationCount(doc.getRecommendationCount() != null ? doc.getRecommendationCount() : 0)
                .build();
    }
}