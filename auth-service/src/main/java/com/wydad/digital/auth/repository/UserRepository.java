package com.wydad.digital.auth.repository;

import com.wydad.digital.auth.model.StatutCompte;
import com.wydad.digital.auth.model.User;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface UserRepository extends JpaRepository<User, Long> {
    Optional<User> findByEmail(String email);
    Optional<User> findByEmailIgnoreCase(String email);
    Optional<User> findByPhone(String phone);
    boolean existsByEmail(String email);
    boolean existsByPhone(String phone);
    Optional<User> findByReferralCode(String referralCode);
    List<User> findByActiveTrue();

    /** Phase 0 : demandes de comptes à valider (écran admin). */
    List<User> findByStatutCompte(StatutCompte statutCompte);

    /** Phase 0 : comptes VALIDE dont le rôle exige une validation. */
    @org.springframework.data.jpa.repository.Query(
            "select u from User u where u.statutCompte = com.wydad.digital.auth.model.StatutCompte.VALIDE " +
            "and u.role in (com.wydad.digital.auth.model.Role.ENTRAINEUR, com.wydad.digital.auth.model.Role.JOURNALISTE, com.wydad.digital.auth.model.Role.PRESIDENT)")
    List<User> findValidatedPrivilegedRoles();
}