package com.wydad.digital.sports.dto;

import com.wydad.digital.sports.enums.SportType;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.time.LocalDate;

@Data
public class AcademyMemberDto {
    private Long id;
    @NotNull private Long parentUserId;
    @NotBlank private String childFullName;
    @NotNull private LocalDate childBirthDate;
    @NotNull private SportType sportType;
    private String level;
    private String medicalHistory;
    private String bloodType;
    private String allergies;
    private String emergencyContactName;
    private String emergencyContactPhone;
    private Boolean active;
}
