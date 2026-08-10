package com.wydad.digital.sports.service;

import com.wydad.digital.sports.dto.SessionDto;
import com.wydad.digital.sports.dto.StaffDto;
import com.wydad.digital.sports.enums.Category;
import com.wydad.digital.sports.enums.SportType;
import com.wydad.digital.sports.model.Session;
import com.wydad.digital.sports.model.Staff;
import com.wydad.digital.sports.repository.SessionRepository;
import com.wydad.digital.sports.repository.StaffRepository;
import jakarta.persistence.EntityNotFoundException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class StaffService {

    private final StaffRepository staffRepository;
    private final SessionRepository sessionRepository;

    public StaffDto createOrUpdateStaff(StaffDto dto) {
        Staff staff = staffRepository.findByUserId(dto.getUserId()).orElse(new Staff());
        
        staff.setUserId(dto.getUserId());
        staff.setFullName(dto.getFullName());
        staff.setRole(dto.getRole());
        staff.setSportType(dto.getSportType());
        staff.setAssignedCategory(dto.getAssignedCategory());

        return mapToDto(staffRepository.save(staff));
    }

    public List<StaffDto> getStaffByTeam(SportType sportType, Category category) {
        return staffRepository.findBySportTypeAndAssignedCategory(sportType, category)
                .stream().map(this::mapToDto).collect(Collectors.toList());
    }

    public SessionDto createSession(SessionDto dto) {
        Staff staff = staffRepository.findById(dto.getCreatedByStaffId())
                .orElseThrow(() -> new EntityNotFoundException("Staff non trouvé"));

        Session session = Session.builder()
                .title(dto.getTitle())
                .description(dto.getDescription())
                .location(dto.getLocation())
                .sessionDate(dto.getSessionDate())
                .sportType(dto.getSportType())
                .category(dto.getCategory())
                .createdByStaffId(staff.getId())
                .build();

        return mapToDto(sessionRepository.save(session));
    }

    public List<SessionDto> getTeamSessions(SportType sportType, Category category) {
        return sessionRepository.findBySportTypeAndCategoryOrderBySessionDateAsc(sportType, category)
                .stream().map(this::mapToDto).collect(Collectors.toList());
    }

    private StaffDto mapToDto(Staff s) {
        StaffDto dto = new StaffDto();
        dto.setId(s.getId());
        dto.setUserId(s.getUserId());
        dto.setFullName(s.getFullName());
        dto.setRole(s.getRole());
        dto.setSportType(s.getSportType());
        dto.setAssignedCategory(s.getAssignedCategory());
        return dto;
    }

    private SessionDto mapToDto(Session s) {
        SessionDto dto = new SessionDto();
        dto.setId(s.getId());
        dto.setTitle(s.getTitle());
        dto.setDescription(s.getDescription());
        dto.setLocation(s.getLocation());
        dto.setSessionDate(s.getSessionDate());
        dto.setSportType(s.getSportType());
        dto.setCategory(s.getCategory());
        dto.setCreatedByStaffId(s.getCreatedByStaffId());
        return dto;
    }
}
