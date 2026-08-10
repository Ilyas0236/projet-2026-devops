package com.wydad.digital.sports.service;

import com.wydad.digital.sports.dto.AcademyMemberDto;
import com.wydad.digital.sports.model.AcademyMember;
import com.wydad.digital.sports.repository.AcademyMemberRepository;
import jakarta.persistence.EntityNotFoundException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class AcademyService {

    private final AcademyMemberRepository academyRepository;

    public AcademyMemberDto registerChild(AcademyMemberDto dto) {
        AcademyMember member = AcademyMember.builder()
                .parentUserId(dto.getParentUserId())
                .childFullName(dto.getChildFullName())
                .childBirthDate(dto.getChildBirthDate())
                .sportType(dto.getSportType())
                .level(dto.getLevel())
                .medicalHistory(dto.getMedicalHistory())
                .build();

        return mapToDto(academyRepository.save(member));
    }

    public List<AcademyMemberDto> getChildrenByParent(Long parentUserId) {
        return academyRepository.findByParentUserId(parentUserId)
                .stream().map(this::mapToDto).collect(Collectors.toList());
    }

    public AcademyMemberDto updateChildStatus(Long id, Boolean active) {
        AcademyMember member = academyRepository.findById(id)
                .orElseThrow(() -> new EntityNotFoundException("Enfant non trouvé"));
        member.setActive(active);
        return mapToDto(academyRepository.save(member));
    }

    private AcademyMemberDto mapToDto(AcademyMember a) {
        AcademyMemberDto dto = new AcademyMemberDto();
        dto.setId(a.getId());
        dto.setParentUserId(a.getParentUserId());
        dto.setChildFullName(a.getChildFullName());
        dto.setChildBirthDate(a.getChildBirthDate());
        dto.setSportType(a.getSportType());
        dto.setLevel(a.getLevel());
        dto.setMedicalHistory(a.getMedicalHistory());
        dto.setActive(a.getActive());
        return dto;
    }
}
