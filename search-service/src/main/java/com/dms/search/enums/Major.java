package com.dms.search.enums;

import lombok.AllArgsConstructor;
import lombok.Getter;

@Getter
@AllArgsConstructor
public enum Major {
    SOFTWARE_ENGINEERING("SE", "Software Engineering"),
    ARTIFICIAL_INTELLIGENCE("AI", "Artificial Intelligence"),
    INFORMATION_SECURITY("IS", "Information Security"),
    DIGITAL_MARKETING("DM", "Digital Marketing");

    private final String code;
    private final String name;
}