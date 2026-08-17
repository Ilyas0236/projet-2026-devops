package com.wydad.digital.auth.config;

import com.wydad.digital.auth.model.MembershipLevel;
import com.wydad.digital.auth.model.Role;
import com.wydad.digital.auth.model.User;
import com.wydad.digital.auth.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.CommandLineRunner;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;

@Component
@RequiredArgsConstructor
@Slf4j
public class DataInitializer implements CommandLineRunner {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;

    @Override
    public void run(String... args) {
        if (userRepository.count() == 0) {
            log.info("Aucun utilisateur trouvé. Création de l'administrateur par défaut...");

            User admin = User.builder()
                    .email("admin@wac.ma")
                    .phone("+212600000000")
                    .password(passwordEncoder.encode("DimaWydad2026"))
                    .firstName("Super")
                    .lastName("Admin")
                    .membershipLevel(MembershipLevel.ROUGE)
                    .role(Role.ADMIN)
                    .membershipExpiresAt(LocalDateTime.now().plusYears(100))
                    .referralCode("ADMINWAC")
                    .active(true)
                    .kycVerified(true)
                    .build();

            userRepository.save(admin);
            log.info("Administrateur par défaut créé avec succès : admin@wac.ma / DimaWydad2026");
        } else {
            log.info("La base de données contient déjà des utilisateurs. Initialisation ignorée.");
        }
    }
}
