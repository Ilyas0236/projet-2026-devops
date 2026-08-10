package com.wydad.digital.sports.service;

import com.wydad.digital.sports.dto.SessionDto;
import com.wydad.digital.sports.enums.Category;
import com.wydad.digital.sports.enums.SportType;
import com.wydad.digital.sports.model.Session;
import com.wydad.digital.sports.repository.SessionRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class SessionService {

    private final SessionRepository sessionRepository;

    public SessionDto createSession(SessionDto dto) {
        Session session = Session.builder()
                .title(dto.getTitle())
                .description(dto.getDescription())
                .location(dto.getLocation())
                .sessionDate(dto.getSessionDate())
                .sportType(dto.getSportType())
                .category(dto.getCategory())
                .createdByStaffId(dto.getCreatedByStaffId())
                .build();
        Session saved = sessionRepository.save(session);
        return mapToDto(saved);
    }

    public List<SessionDto> getSessionsByCategory(SportType sportType, Category category) {
        return sessionRepository.findBySportTypeAndCategoryOrderBySessionDateAsc(sportType, category)
                .stream().map(this::mapToDto).collect(Collectors.toList());
    }

    public List<SessionDto> getSessionsByStaff(Long staffId) {
        return sessionRepository.findByCreatedByStaffIdOrderBySessionDateDesc(staffId)
                .stream().map(this::mapToDto).collect(Collectors.toList());
    }

    private SessionDto mapToDto(Session session) {
        SessionDto dto = new SessionDto();
        dto.setId(session.getId());
        dto.setTitle(session.getTitle());
        dto.setDescription(session.getDescription());
        dto.setLocation(session.getLocation());
        dto.setSessionDate(session.getSessionDate());
        dto.setSportType(session.getSportType());
        dto.setCategory(session.getCategory());
        dto.setCreatedByStaffId(session.getCreatedByStaffId());
        return dto;
    }
}
