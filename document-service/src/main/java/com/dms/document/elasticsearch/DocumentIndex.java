package com.dms.document.elasticsearch;

import com.dms.document.enums.DocumentStatus;
import com.dms.document.enums.DocumentType;
import com.dms.document.enums.SharingType;
import lombok.Builder;
import lombok.Data;
import org.springframework.data.annotation.Id;
import org.springframework.data.elasticsearch.annotations.*;

import java.util.Date;
import java.util.Map;
import java.util.Set;

@Builder
@Data
@Document(indexName = "documents")
@Setting(settingPath = "/elasticsearch/settings.json")
@Mapping(mappingPath = "/elasticsearch/mappings.json")
public class DocumentIndex {
    @Id
    private String id;

    @MultiField(
            mainField = @Field(
                    type = FieldType.Text,
                    analyzer = "vietnamese_analyzer",
                    searchAnalyzer = "vietnamese_analyzer"
            ),
            otherFields = {
                    @InnerField(suffix = "raw", type = FieldType.Keyword, ignoreAbove = 256),
                    @InnerField(
                            suffix = "analyzed",
                            type = FieldType.Text,
                            analyzer = "vietnamese_analyzer",
                            searchAnalyzer = "vietnamese_analyzer",
                            termVector = TermVector.with_positions_offsets
                    ),
                    @InnerField(
                            suffix = "search",
                            type = FieldType.Text,
                            analyzer = "filename_analyzer",
                            searchAnalyzer = "filename_analyzer"
                    )
            }
    )
    private String filename;

    @MultiField(
            mainField = @Field(
                    type = FieldType.Text,
                    analyzer = "vietnamese_analyzer",
                    searchAnalyzer = "vietnamese_analyzer",
                    termVector = TermVector.with_positions_offsets
            ),
            otherFields = {
                    @InnerField(
                            suffix = "keyword",
                            type = FieldType.Keyword,
                            ignoreAbove = 256
                    )
            }
    )
    private String content;

    @Field(type = FieldType.Long, name = "file_size")
    private Long fileSize;

    @Field(type = FieldType.Keyword, name = "mime_type")
    private String mimeType;

    @Field(type = FieldType.Keyword, name = "document_type")
    private DocumentType documentType;

    @Field(type = FieldType.Keyword)
    private String major;

    @Field(type = FieldType.Keyword, name = "course_code")
    private String courseCode;

    @Field(type = FieldType.Keyword, name = "course_level")
    private String courseLevel;

    @Field(type = FieldType.Keyword)
    private String category;

    @Field(type = FieldType.Keyword)
    private Set<String> tags;

    @Field(type = FieldType.Object, name = "extracted_metadata")
    private Map<String, String> extractedMetadata;

    @Field(type = FieldType.Keyword, name = "user_id")
    private String userId;

    @Field(type = FieldType.Keyword, name = "sharing_type")
    private SharingType sharingType;

    @Field(type = FieldType.Keyword, name = "shared_with")
    private Set<String> sharedWith;

    @Field(type = FieldType.Boolean)
    private boolean deleted;

    @Field(type = FieldType.Keyword)
    private DocumentStatus status;

    @Field(type = FieldType.Keyword)
    private String language;

    @Field(type = FieldType.Date, name = "created_at")
    private Date createdAt;
}