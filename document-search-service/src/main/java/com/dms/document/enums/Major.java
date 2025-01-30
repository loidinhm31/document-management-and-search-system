package com.dms.document.enums;

import lombok.AllArgsConstructor;
import lombok.Getter;

@Getter
@AllArgsConstructor
public enum Major {
    SOFTWARE_ENGINEERING("SE", "Software Engineering"),
    ARTIFICIAL_INTELLIGENCE("AI", "Artificial Intelligence"),
    INFORMATION_SECURITY("IS", "Information Security"),
    IOT("IOT", "Internet Of Things");

    private final String code;
    private final String name;
}