package com.wydad.digital.sports.dto;

import com.wydad.digital.sports.enums.Category;
import com.wydad.digital.sports.enums.SportType;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.time.LocalDate;

@Data
public class PlayerDto {
    private Long id;
    @NotNull private Long userId;
    @NotBlank private String fullName;
    @NotNull private SportType sportType;
    @NotNull private Category category;
    private String position;
    private Integer jerseyNumber;
    private Double height;
    private Double weight;
    private Double bmi;
    private LocalDate birthDate;
    private String nationality;
    private String photoUrl;
    private Integer matchesPlayed;
    private Integer goals;
    private Integer assists;
}
