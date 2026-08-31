package com.wydad.digital.sports.dto;

/**
 * B.18 — Vue minimale d'un {@code AcademyMember} destinée aux appels
 * service-à-service. Contient strictement ce qu'il faut pour valider
 * une opération « le parent X achète pour l'enfant Y » côté auth-service
 * et ticket-service :
 * <ul>
 *   <li>{@code id} — id AcademyMember (clé d'unicité de l'email shadow
 *       côté auth-service),</li>
 *   <li>{@code parentUserId} — pour le contrôle IDOR (« cet enfant
 *       est-il bien le vôtre ? »),</li>
 *   <li>{@code childFullName} — pour générer le prénom/nom de l'User
 *       shadow côté auth-service.</li>
 * </ul>
 *
 * <p>Volontairement allégé (pas de birthDate, sportType, etc.) : ces
 * champs sont sans intérêt pour le flow « acheter au nom de » et
 * alourdiraient le payload JSON.</p>
 */
public record AcademyMemberLookup(
        Long id,
        Long parentUserId,
        String childFullName
) {}
