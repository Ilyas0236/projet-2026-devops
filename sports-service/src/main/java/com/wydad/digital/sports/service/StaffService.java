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

    /** Liste complete pour le back-office ADMIN. */
    public List<StaffDto> getAllStaff() {
        return staffRepository.findAll()
                .stream().map(this::mapToDto).collect(Collectors.toList());
    }

    public List<StaffDto> getStaffByTeam(SportType sportType, Category category) {
        return staffRepository.findBySportTypeAndAssignedCategory(sportType, category)
                .stream().map(this::mapToDto).collect(Collectors.toList());
    }

    /**
     * C.21 — tout le staff d'une discipline (toutes catégories).
     * Utilisé par l'annuaire du président qui gère toute sa section.
     */
    public List<StaffDto> getStaffByDiscipline(SportType sportType) {
        return staffRepository.findBySportType(sportType)
                .stream().map(this::mapToDto).collect(Collectors.toList());
    }

    public StaffDto updateStaff(Long id, StaffDto dto) {
        Staff staff = staffRepository.findById(id)
                .orElseThrow(() -> new EntityNotFoundException("Staff non trouvé"));
        staff.setUserId(dto.getUserId());
        staff.setFullName(dto.getFullName());
        staff.setRole(dto.getRole());
        staff.setSportType(dto.getSportType());
        staff.setAssignedCategory(dto.getAssignedCategory());
        return mapToDto(staffRepository.save(staff));
    }

    public void deleteStaff(Long id) {
        if (!staffRepository.existsById(id)) {
            throw new EntityNotFoundException("Staff non trouvé");
        }
        staffRepository.deleteById(id);
    }

    public StaffDto getStaffByUserId(Long userId) {
        Staff staff = staffRepository.findByUserId(userId)
                .orElseThrow(() -> new EntityNotFoundException("Profil staff non trouvé"));
        return mapToDto(staff);
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
