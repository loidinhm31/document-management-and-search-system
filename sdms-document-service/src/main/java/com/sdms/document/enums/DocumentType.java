package com.sdms.document.enums;

public enum DocumentType {
    PDF("PDF"),
    WORD("Word Document"),
    EXCEL("Excel Document"),
    POWERPOINT("PowerPoint"),
    TEXT("Text Document"),
    IMAGE("Image"),
    OTHER("Other");

    private final String displayName;

    DocumentType(String displayName) {
        this.displayName = displayName;
    }

    public String getDisplayName() {
        return displayName;
    }
}
