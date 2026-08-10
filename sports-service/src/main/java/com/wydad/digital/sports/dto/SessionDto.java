package com.wydad.digital.sports.dto;

import com.wydad.digital.sports.enums.Category;
import com.wydad.digital.sports.enums.SportType;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.time.LocalDateTime;

@Data
public class SessionDto {
    private Long id;
    @NotBlank private String title;
    private String description;
    private String location;
    @NotNull private LocalDateTime sessionDate;
    @NotNull private SportType sportType;
    @NotNull private Category category;
    @NotNull private Long createdByStaffId;
}
