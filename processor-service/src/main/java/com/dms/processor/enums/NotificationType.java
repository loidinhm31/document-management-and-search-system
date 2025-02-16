package com.dms.processor.enums;

public enum NotificationType {
    NEW_COMMENT_FROM_NEW_USER,    // When a new user adds a comment
    NEW_FILE_VERSION,             // When document is updated with new file
    DOCUMENT_REVERTED             // When document is reverted to previous version
}
