package com.wydad.digital.auth.service;

import com.wydad.digital.auth.model.Role;
import com.wydad.digital.auth.model.StatutCompte;
import com.wydad.digital.auth.model.User;
import com.wydad.digital.auth.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Optional;
import java.util.UUID;

/**
 * Gestion des « User shadow » représentant les enfants d'un parent au sens
 * de l'académie Wydad (cf. sports-service {@code AcademyMember}).
 *
 * <p>Pourquoi : {@code AcademyMember} n'a pas de compte User (juste un
 * nom + un parentUserId). Or le flow supporter (billet, abonnement) est
 * bâti autour de l'entité User (un abonnement par user par saison).
 * On crée donc, à la première interaction « parent achète pour son fils »,
 * un User « shadow » qui :
 * <ul>
 *   <li>porte l'identité du fils (firstName / lastName issus de
 *       {@code childFullName}),</li>
 *   <li>a un email déterministe et inutilisable en login
 *       ({@code enfant-{academyMemberId}@wac.parent}),</li>
 *   <li>a un password bcrypt(randomUUID) — jamais connu, jamais utilisable,</li>
 *   <li>a le rôle {@link Role#ADHERENT} pour profiter de l'infrastructure
 *       « un abonnement par saison par user » (B.12),</li>
 *   <li>a un statutCompte VALIDE (pas de circuit d'inscription).<br>
 *       On NE respecte pas la règle d'unicité phone en se basant sur
 *       academyMemberId, ce qui permet l'insertion sans collision même
 *       après un grand nombre d'enfants. Le phone est également marqué
 *       UNIQUE côté table : on concatène donc l'id de l'enfant.</li>
 * </ul>
 *
 * <p>Idempotent : si l'User shadow existe déjà, on le renvoie tel quel.
 * Pas de mise à jour du firstName / lastName (l'identité du fils peut
 * évoluer côté AcademyMember — on fige au premier achat pour ne pas
 * casser la traçabilité des billets).</p>
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class ChildUserService {

    /** Préfixe d'email des comptes shadow enfants. */
    public static final String CHILD_EMAIL_DOMAIN = "@wac.parent";
    private static final String CHILD_EMAIL_PREFIX = "enfant-";

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;

    /**
     * Crée ou récupère l'User shadow d'un enfant académie.
     *
     * @param parentUserId   id du parent (User authentifié)
     * @param childFullName  nom complet de l'enfant (pour firstName/lastName)
     * @param academyMemberId id de l'AcademyMember (utilisé pour dériver l'email)
     * @return l'User shadow (jamais null)
     */
    @Transactional
    public User ensureChildUser(Long parentUserId, String childFullName, Long academyMemberId) {
        String email = emailFor(academyMemberId);
        Optional<User> existing = userRepository.findByEmailIgnoreCase(email);
        if (existing.isPresent()) {
            log.debug("User shadow enfant déjà existant pour academyMemberId={} : userId={}",
                    academyMemberId, existing.get().getId());
            return existing.get();
        }

        String[] nameParts = splitName(childFullName);
        // Phone dérivé : 000 + id académie sur 10 chiffres (la colonne est UNIQUE).
        // Si l'académie dépasse 7 chiffres (impossible avant longtemps), on hash
        // — pas un sujet opérationnel.
        String phone = String.format("000%07d", academyMemberId);

        User child = User.builder()
                .email(email)
                .phone(phone)
                .password(passwordEncoder.encode(UUID.randomUUID().toString()))
                .firstName(nameParts[0])
                .lastName(nameParts[1])
                .role(Role.ADHERENT)
                .statutCompte(StatutCompte.VALIDE)
                .active(true)
                .kycVerified(false)
                .build();

        User saved = userRepository.save(child);
        log.info("User shadow enfant créé : academyMemberId={}, parentUserId={}, childUserId={}, email={}",
                academyMemberId, parentUserId, saved.getId(), email);
        return saved;
    }

    /** Email shadow déterministe d'un enfant. */
    public static String emailFor(Long academyMemberId) {
        return CHILD_EMAIL_PREFIX + academyMemberId + CHILD_EMAIL_DOMAIN;
    }

    /**
     * Décompose un nom complet « Prénom NOM » en (firstName, lastName).
     * Tolérant : si une seule partie, lastName est vide. Si plus de deux
     * parties, le lastName agrège le reste (gestion des noms composés).
     */
    private static String[] splitName(String fullName) {
        if (fullName == null || fullName.isBlank()) {
            return new String[]{"Enfant", "WAC"};
        }
        String trimmed = fullName.trim().replaceAll("\\s+", " ");
        int firstSpace = trimmed.indexOf(' ');
        if (firstSpace < 0) {
            return new String[]{trimmed, ""};
        }
        String first = trimmed.substring(0, firstSpace);
        String last = trimmed.substring(firstSpace + 1);
        return new String[]{first, last};
    }
}
