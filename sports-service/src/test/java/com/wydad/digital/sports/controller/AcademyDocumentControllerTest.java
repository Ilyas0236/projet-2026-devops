package com.wydad.digital.sports.controller;

import com.wydad.digital.sports.filter.SportsUserContext;
import com.wydad.digital.sports.model.AcademyMember;
import com.wydad.digital.sports.repository.AcademyDocumentRepository;
import com.wydad.digital.sports.repository.AcademyMemberRepository;
import com.wydad.digital.sports.service.AcademyService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Mockito;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.util.Collections;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 0-BIS.6 : le certificat médical (et les autres pièces) est réellement
 * transmis au backend, avec whitelist MIME, limite de taille et propriété
 * du dossier vérifiée côté serveur.
 */
class AcademyDocumentControllerTest {

    private MockMvc mockMvc;
    private AcademyMemberRepository memberRepo;
    private AcademyDocumentRepository docRepo;

    private final AcademyMember folder = AcademyMember.builder()
            .id(10L)
            .parentUserId(5L)
            .childFullName("Enfant Test")
            .build();

    @BeforeEach
    void setUp() {
        memberRepo = Mockito.mock(AcademyMemberRepository.class);
        docRepo = Mockito.mock(AcademyDocumentRepository.class);
        Mockito.when(memberRepo.findById(10L)).thenReturn(Optional.of(folder));
        mockMvc = MockMvcBuilders.standaloneSetup(new AcademyController(
                        Mockito.mock(AcademyService.class), memberRepo, docRepo))
                .setControllerAdvice(new GlobalExceptionHandler())
                .build();
    }

    @AfterEach
    void tearDown() {
        SportsUserContext.clear();
        SecurityContextHolder.clearContext();
    }

    private void loginAs(Long userId, String role) {
        SportsUserContext.setCurrentUserId(userId);
        SportsUserContext.setCurrentUserRole(role);
        SportsUserContext.setCurrentUserEmail("user" + userId + "@test.ma");
        UsernamePasswordAuthenticationToken auth = new UsernamePasswordAuthenticationToken(
                "user" + userId + "@test.ma", null,
                Collections.singletonList(new SimpleGrantedAuthority("ROLE_" + role)));
        SecurityContextHolder.getContext().setAuthentication(auth);
    }

    private MockMultipartFile pdf(String name) {
        return new MockMultipartFile("file", "certificat.pdf", "application/pdf",
                "%PDF-1.4 contenu de test".getBytes());
    }

    @Test
    @DisplayName("Parent propriétaire -> certificat médical accepté (201)")
    void ownerUploadsMedicalCertificate_created() throws Exception {
        loginAs(5L, "PARENT");
        mockMvc.perform(multipart("/api/sports/academy/10/documents")
                        .file(pdf("certificat.pdf"))
                        .param("docType", "MEDICAL_CERTIFICATE")
                        .with(csrf()))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.docType").value("MEDICAL_CERTIFICATE"));

        ArgumentCaptor<com.wydad.digital.sports.model.AcademyDocument> captor =
                ArgumentCaptor.forClass(com.wydad.digital.sports.model.AcademyDocument.class);
        Mockito.verify(docRepo).save(captor.capture());
        assertThat(captor.getValue().getData()).isNotEmpty();
        assertThat(captor.getValue().getOwnerUserId()).isEqualTo(5L);
    }

    @Test
    @DisplayName("Autre parent -> 403 (anti-IDOR sur le dossier)")
    void otherParent_forbidden() throws Exception {
        loginAs(9L, "PARENT");
        mockMvc.perform(multipart("/api/sports/academy/10/documents")
                        .file(pdf("certificat.pdf"))
                        .param("docType", "MEDICAL_CERTIFICATE")
                        .with(csrf()))
                .andExpect(status().isForbidden());
    }

    @Test
	@DisplayName("Fichier exécutable déguisé -> 400 (whitelist MIME)")
    void executableMimeType_rejected() throws Exception {
        loginAs(5L, "PARENT");
        MockMultipartFile exe = new MockMultipartFile("file", "malware.exe",
                "application/x-msdownload", "MZ...".getBytes());
        mockMvc.perform(multipart("/api/sports/academy/10/documents")
                        .file(exe)
                        .param("docType", "MEDICAL_CERTIFICATE")
                        .with(csrf()))
                .andExpect(status().isBadRequest());
    }
}
