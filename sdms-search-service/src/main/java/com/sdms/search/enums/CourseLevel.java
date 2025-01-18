package com.sdms.search.enums;

import lombok.AllArgsConstructor;
import lombok.Getter;

@Getter
@AllArgsConstructor
public enum CourseLevel {
    FUNDAMENTAL("FUN", "Fundamental"),
    INTERMEDIATE("INT", "Intermediate"),
    ADVANCED("ADV", "Advanced"),
    SPECIALIZED("SPE", "Specialized");

    private final String code;
    private final String name;
}