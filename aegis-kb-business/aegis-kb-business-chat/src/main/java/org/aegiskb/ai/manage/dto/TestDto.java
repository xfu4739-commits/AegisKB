package org.aegiskb.ai.manage.dto;

import jakarta.validation.constraints.NotNull;
import lombok.Data;

@Data
public class TestDto {

    @NotNull
    private Long id;
}
