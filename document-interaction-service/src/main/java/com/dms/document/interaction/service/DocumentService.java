package com.dms.document.interaction.service;

import com.dms.document.interaction.dto.DocumentUpdateRequest;
import com.dms.document.interaction.dto.ThumbnailResponse;
import com.dms.document.interaction.model.DocumentInformation;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.util.Set;

/**
 * Service interface for document management operations.
 */
public interface DocumentService {

    /**
     * Upload a new document to the system.
     *
     * @param file File to upload
     * @param summary Document summary
     * @param courseCodes Associated course codes
     * @param majors Associated majors
     * @param level Course level
     * @param categories Document categories
     * @param tags Document tags
     * @param username Username of the uploader
     * @return Created document information
     * @throws IOException If file operations fail
     */
    DocumentInformation uploadDocument(MultipartFile file,
                                       String summary,
                                       Set<String> courseCodes,
                                       Set<String> majors,
                                       String level,
                                       Set<String> categories,
                                       Set<String> tags,
                                       String username) throws IOException;

    /**
     * Retrieve document thumbnail.
     *
     * @param documentId Document identifier
     * @param username Username requesting the thumbnail
     * @return Thumbnail response with data and status
     * @throws IOException If file operations fail
     */
    ThumbnailResponse getDocumentThumbnail(String documentId, String username) throws IOException;

    /**
     * Retrieve document content.
     *
     * @param documentId Document identifier
     * @param username Username requesting the content
     * @param action Action being performed (e.g., "download")
     * @param history Whether to record history of this action
     * @return Document content as byte array
     * @throws IOException If file operations fail
     */
    byte[] getDocumentContent(String documentId, String username, String action, Boolean history) throws IOException;

    /**
     * Get document details.
     *
     * @param documentId Document identifier
     * @param username Username requesting the details
     * @param history Whether to record history of this action
     * @return Document information
     */
    DocumentInformation getDocumentDetails(String documentId, String username, Boolean history);

    /**
     * Update document metadata.
     *
     * @param documentId Document identifier
     * @param documentUpdateRequest Updated document details
     * @param username Username making the update
     * @return Updated document information
     */
    DocumentInformation updateDocument(String documentId, DocumentUpdateRequest documentUpdateRequest, String username);

    /**
     * Update document with new file and metadata.
     *
     * @param documentId Document identifier
     * @param file New file
     * @param documentUpdateRequest Updated document details
     * @param username Username making the update
     * @return Updated document information
     * @throws IOException If file operations fail
     */
    DocumentInformation updateDocumentWithFile(String documentId,
                                               MultipartFile file,
                                               DocumentUpdateRequest documentUpdateRequest,
                                               String username) throws IOException;

    /**
     * Delete a document.
     *
     * @param documentId Document identifier
     * @param username Username requesting deletion
     */
    void deleteDocument(String documentId, String username);

    /**
     * Get popular tags, optionally filtered by prefix.
     *
     * @param prefix Optional prefix to filter tags
     * @return Set of matching tags
     */
    Set<String> getPopularTags(String prefix);

    /**
     * Get content of a specific document version.
     *
     * @param documentId Document identifier
     * @param versionNumber Version number to retrieve
     * @param username Username requesting the version
     * @param action Action being performed (e.g., "download")
     * @param history Whether to record history of this action
     * @return Document version content as byte array
     * @throws IOException If file operations fail
     */
    byte[] getDocumentVersionContent(String documentId, Integer versionNumber, String username, String action, Boolean history) throws IOException;

    /**
     * Revert document to a previous version.
     *
     * @param documentId Document identifier
     * @param versionNumber Version to revert to
     * @param username Username requesting reversion
     * @return Updated document information
     */
    DocumentInformation revertToVersion(String documentId, Integer versionNumber, String username);
}