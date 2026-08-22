package com.wydad.digital.sports.service;

import com.wydad.digital.sports.dto.PlayerDto;
import com.wydad.digital.sports.enums.Category;
import com.wydad.digital.sports.enums.SportType;
import com.wydad.digital.sports.model.Player;
import com.wydad.digital.sports.repository.PlayerRepository;
import jakarta.persistence.EntityNotFoundException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class PlayerService {

    private final PlayerRepository playerRepository;

    public PlayerDto createOrUpdatePlayer(PlayerDto dto) {
        Player player = playerRepository.findByUserId(dto.getUserId()).orElse(new Player());
        
        player.setUserId(dto.getUserId());
        player.setFullName(dto.getFullName());
        player.setSportType(dto.getSportType());
        player.setCategory(dto.getCategory());
        player.setPosition(dto.getPosition());
        player.setJerseyNumber(dto.getJerseyNumber());
        player.setHeight(dto.getHeight());
        player.setWeight(dto.getWeight());
        player.setBirthDate(dto.getBirthDate());
        player.setNationality(dto.getNationality());
        player.setPhotoUrl(dto.getPhotoUrl());
        
        if (dto.getMatchesPlayed() != null) player.setMatchesPlayed(dto.getMatchesPlayed());
        if (dto.getGoals() != null) player.setGoals(dto.getGoals());
        if (dto.getAssists() != null) player.setAssists(dto.getAssists());

        // Calcul de l'IMC (Poids / Taille(m)^2)
        if (dto.getWeight() != null && dto.getHeight() != null && dto.getHeight() > 0) {
            double heightInMeters = dto.getHeight() / 100.0;
            double bmi = dto.getWeight() / (heightInMeters * heightInMeters);
            player.setBmi(Math.round(bmi * 100.0) / 100.0);
        }

        return mapToDto(playerRepository.save(player));
    }

    public PlayerDto getPlayerByUserId(Long userId) {
        Player player = playerRepository.findByUserId(userId)
                .orElseThrow(() -> new EntityNotFoundException("Joueur non trouvé"));
        return mapToDto(player);
    }

    public List<PlayerDto> getPlayersByCategory(SportType sportType, Category category) {
        return playerRepository.findBySportTypeAndCategory(sportType, category)
                .stream().map(this::mapToDto).collect(Collectors.toList());
    }

    public List<PlayerDto> getAllPlayers() {
        return playerRepository.findAll().stream().map(this::mapToDto).collect(Collectors.toList());
    }

    @Transactional
    public PlayerDto updatePlayer(Long id, PlayerDto dto) {
        Player player = playerRepository.findById(id)
                .orElseThrow(() -> new EntityNotFoundException("Joueur non trouvé: " + id));

        player.setFullName(dto.getFullName());
        player.setSportType(dto.getSportType());
        player.setCategory(dto.getCategory());
        player.setPosition(dto.getPosition());
        player.setJerseyNumber(dto.getJerseyNumber());
        player.setHeight(dto.getHeight());
        player.setWeight(dto.getWeight());
        player.setBirthDate(dto.getBirthDate());
        player.setNationality(dto.getNationality());
        player.setPhotoUrl(dto.getPhotoUrl());

        if (dto.getMatchesPlayed() != null) player.setMatchesPlayed(dto.getMatchesPlayed());
        if (dto.getGoals() != null) player.setGoals(dto.getGoals());
        if (dto.getAssists() != null) player.setAssists(dto.getAssists());

        // Calcul de l'IMC (Poids / Taille(m)^2)
        if (dto.getWeight() != null && dto.getHeight() != null && dto.getHeight() > 0) {
            double heightInMeters = dto.getHeight() / 100.0;
            double bmi = dto.getWeight() / (heightInMeters * heightInMeters);
            player.setBmi(Math.round(bmi * 100.0) / 100.0);
        }

        return mapToDto(playerRepository.save(player));
    }

    @Transactional
    public void deletePlayer(Long id) {
        if (!playerRepository.existsById(id)) {
            throw new EntityNotFoundException("Joueur non trouvé: " + id);
        }
        playerRepository.deleteById(id);
    }

    private PlayerDto mapToDto(Player p) {
        PlayerDto dto = new PlayerDto();
        dto.setId(p.getId());
        dto.setUserId(p.getUserId());
        dto.setFullName(p.getFullName());
        dto.setSportType(p.getSportType());
        dto.setCategory(p.getCategory());
        dto.setPosition(p.getPosition());
        dto.setJerseyNumber(p.getJerseyNumber());
        dto.setHeight(p.getHeight());
        dto.setWeight(p.getWeight());
        dto.setBmi(p.getBmi());
        dto.setBirthDate(p.getBirthDate());
        dto.setNationality(p.getNationality());
        dto.setPhotoUrl(p.getPhotoUrl());
        dto.setMatchesPlayed(p.getMatchesPlayed());
        dto.setGoals(p.getGoals());
        dto.setAssists(p.getAssists());
        return dto;
    }
}
