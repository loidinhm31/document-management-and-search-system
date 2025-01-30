package com.dms.document.search.enums;

import lombok.AllArgsConstructor;
import lombok.Getter;

@Getter
@AllArgsConstructor
public enum DocumentCategory {
    LECTURE("Lecture materials"),
    EXERCISE("Exercises and assignments"),
    EXAM("Exam materials"),
    REFERENCE("Reference materials"),
    LAB("Lab instructions"),
    PROJECT("Project examples");

    private final String name;
}