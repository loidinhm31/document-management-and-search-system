package com.dms.processor.mapper;

import com.dms.processor.elasticsearch.DocumentIndex;
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
                .major(doc.getMajor())
                .courseCode(doc.getCourseCode())
                .courseLevel(doc.getCourseLevel())
                .category(doc.getCategory())
                .tags(doc.getTags())
                .fileSize(doc.getFileSize())
                .sharingType(doc.getSharingType())
                .sharedWith(doc.getSharedWith())
                .deleted(doc.isDeleted())
                .status(doc.getStatus())
                .createdAt(doc.getCreatedAt())
                .build();
    }
}