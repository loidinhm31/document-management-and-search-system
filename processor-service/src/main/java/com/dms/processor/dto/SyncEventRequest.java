package com.dms.processor.dto;

import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.experimental.SuperBuilder;


@SuperBuilder
@NoArgsConstructor
@Getter
@Setter
public class SyncEventRequest extends EventRequest {
    private String documentId;
}
