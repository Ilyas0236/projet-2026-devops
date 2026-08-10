package com.wydad.digital.sports.dto;

import com.wydad.digital.sports.enums.Category;
import com.wydad.digital.sports.enums.SportType;
import com.wydad.digital.sports.enums.StaffRole;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

@Data
public class StaffDto {
    private Long id;
    @NotNull private Long userId;
    @NotBlank private String fullName;
    @NotNull private StaffRole role;
    @NotNull private SportType sportType;
    @NotNull private Category assignedCategory;
}
