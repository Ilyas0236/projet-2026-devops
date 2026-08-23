package com.wydad.digital.content.service;

import com.wydad.digital.content.dto.*;
import com.wydad.digital.content.model.*;
import com.wydad.digital.content.repository.*;
import jakarta.persistence.EntityNotFoundException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class ContentService {

    private final ArticleRepository articleRepository;
    private final MatchRepository matchRepository;
    private final ClassementRepository classementRepository;
    private final JoueurRepository joueurRepository;
    private final com.wydad.digital.content.client.GamificationClient gamificationClient;

    // ==================== ARTICLES ====================
    public ArticleResponse createArticle(ArticleRequest request) {
        Article article = Article.builder()
                .titre(request.titre())
                .contenu(request.contenu())
                .imageUrl(request.imageUrl())
                .sport(request.sport())
                .auteur(request.auteur())
                .published(true)
                .build();
        Article saved = articleRepository.save(article);
        return mapToArticleResponse(saved);
    }

    public List<ArticleResponse> getAllArticles() {
        return articleRepository.findByPublishedTrue()
                .stream().map(this::mapToArticleResponse).collect(Collectors.toList());
    }

    public List<ArticleResponse> getArticlesBySport(SportSection sport) {
        return articleRepository.findBySportAndPublishedTrue(sport)
                .stream().map(this::mapToArticleResponse).collect(Collectors.toList());
    }

    public ArticleResponse getArticleById(Long id) {
        Article article = articleRepository.findById(id)
                .orElseThrow(() -> new EntityNotFoundException("Article non trouvé"));
        // Un brouillon non publie n'est visible que d'un ADMIN (jamais en acces direct).
        if (!article.isPublished() && !isAdmin()) {
            throw new EntityNotFoundException("Article non trouvé");
        }
        return mapToArticleResponse(article);
    }

    /** Rôle dérivé du JWT par la gateway et propagé via X-User-Role. */
    private boolean isAdmin() {
        return com.wydad.digital.content.filter.UserContextFilter
                .currentAuthentication()
                .map(auth -> auth.getAuthorities().stream().anyMatch(a ->
                        "ROLE_ADMIN".equals(a.getAuthority())))
                .orElse(false);
    }

    public ArticleResponse updateArticle(Long id, ArticleRequest request) {
        Article article = articleRepository.findById(id)
                .orElseThrow(() -> new EntityNotFoundException("Article non trouvé"));
        article.setTitre(request.titre());
        article.setContenu(request.contenu());
        article.setImageUrl(request.imageUrl());
        article.setSport(request.sport());
        article.setAuteur(request.auteur());
        Article saved = articleRepository.save(article);
        return mapToArticleResponse(saved);
    }

    public void deleteArticle(Long id) {
        articleRepository.deleteById(id);
    }

    private ArticleResponse mapToArticleResponse(Article article) {
        return new ArticleResponse(
                article.getId(),
                article.getTitre(),
                article.getContenu(),
                article.getImageUrl(),
                article.getSport(),
                article.getAuteur(),
                article.isPublished(),
                article.getCreatedAt()
        );
    }

    // ==================== MATCHS ====================
    public MatchResponse createMatch(MatchRequest request) {
        Match match = Match.builder()
                .date(request.date())
                .heure(request.heure())
                .adversaire(request.adversaire())
                .competition(request.competition())
                .lieu(request.lieu())
                .scoreWydad(request.scoreWydad())
                .scoreAdversaire(request.scoreAdversaire())
                .statut(request.statut())
                .sport(request.sport())
                .build();
        Match saved = matchRepository.save(match);
        return mapToMatchResponse(saved);
    }

    public List<MatchResponse> getAllMatches() {
        return matchRepository.findAll()
                .stream().map(this::mapToMatchResponse).collect(Collectors.toList());
    }

    public List<MatchResponse> getMatchesByStatut(MatchStatut statut) {
        return matchRepository.findByStatut(statut)
                .stream().map(this::mapToMatchResponse).collect(Collectors.toList());
    }

    public java.util.Optional<MatchResponse> getMatchById(Long id) {
        return matchRepository.findById(id).map(this::mapToMatchResponse);
    }

    public MatchResponse updateMatchResult(Long id, Integer scoreWydad, Integer scoreAdversaire) {
        Match match = matchRepository.findById(id)
                .orElseThrow(() -> new EntityNotFoundException("Match non trouvé"));
        match.setScoreWydad(scoreWydad);
        match.setScoreAdversaire(scoreAdversaire);
        match.setStatut(MatchStatut.TERMINE);
        Match saved = matchRepository.save(match);

        // Best-effort : déclenche la résolution des pronostics côté gamification
        // (points attribués aux gagnants). Une panne ne doit pas invalider le résultat.
        gamificationClient.notifyMatchResult(id, scoreWydad, scoreAdversaire);

        return mapToMatchResponse(saved);
    }

    public MatchResponse updateMatch(Long id, MatchRequest request) {
        Match match = matchRepository.findById(id)
                .orElseThrow(() -> new EntityNotFoundException("Match non trouvé"));
        match.setDate(request.date());
        match.setHeure(request.heure());
        match.setAdversaire(request.adversaire());
        match.setCompetition(request.competition());
        match.setLieu(request.lieu());
        match.setScoreWydad(request.scoreWydad());
        match.setScoreAdversaire(request.scoreAdversaire());
        match.setStatut(request.statut());
        match.setSport(request.sport());
        Match saved = matchRepository.save(match);
        return mapToMatchResponse(saved);
    }

    public void deleteMatch(Long id) {
        matchRepository.deleteById(id);
    }

    private MatchResponse mapToMatchResponse(Match match) {
        return new MatchResponse(
                match.getId(),
                match.getDate(),
                match.getHeure(),
                match.getAdversaire(),
                match.getCompetition(),
                match.getLieu(),
                match.getScoreWydad(),
                match.getScoreAdversaire(),
                match.getStatut(),
                match.getSport()
        );
    }

    // ==================== CLASSEMENTS ====================
    public ClassementResponse createClassement(ClassementRequest request) {
        Classement c = Classement.builder()
                .position(request.position())
                .equipe(request.equipe())
                .joues(request.joues())
                .gagnes(request.gagnes())
                .nuls(request.nuls())
                .perdus(request.perdus())
                .bp(request.bp())
                .bc(request.bc())
                .points(request.points())
                .competition(request.competition())
                .sport(request.sport())
                .build();
        Classement saved = classementRepository.save(c);
        return mapToClassementResponse(saved);
    }

    /** Liste complete (back-office ADMIN) : toutes competitions confondues. */
    public List<ClassementResponse> getAllClassements() {
        return classementRepository.findAll()
                .stream().map(this::mapToClassementResponse).collect(Collectors.toList());
    }

    public List<ClassementResponse> getClassementsByCompetition(String competition) {
        return classementRepository.findByCompetition(competition)
                .stream().map(this::mapToClassementResponse).collect(Collectors.toList());
    }

    public ClassementResponse updateClassement(Long id, ClassementRequest request) {
        Classement c = classementRepository.findById(id)
                .orElseThrow(() -> new EntityNotFoundException("Classement non trouvé"));
        c.setPosition(request.position());
        c.setEquipe(request.equipe());
        c.setJoues(request.joues());
        c.setGagnes(request.gagnes());
        c.setNuls(request.nuls());
        c.setPerdus(request.perdus());
        c.setBp(request.bp());
        c.setBc(request.bc());
        c.setPoints(request.points());
        c.setCompetition(request.competition());
        c.setSport(request.sport());
        Classement saved = classementRepository.save(c);
        return mapToClassementResponse(saved);
    }

    public void deleteClassement(Long id) {
        classementRepository.deleteById(id);
    }

    private ClassementResponse mapToClassementResponse(Classement c) {
        return new ClassementResponse(
                c.getId(), c.getPosition(), c.getEquipe(), c.getJoues(),
                c.getGagnes(), c.getNuls(), c.getPerdus(), c.getBp(),
                c.getBc(), c.getPoints(), c.getCompetition(), c.getSport()
        );
    }

    // ==================== JOUEURS ====================
    public JoueurResponse createJoueur(JoueurRequest request) {
        Joueur j = Joueur.builder()
                .nom(request.nom())
                .photoUrl(request.photoUrl())
                .poste(request.poste())
                .age(request.age())
                .numero(request.numero())
                .sport(request.sport())
                .matchsJoues(request.matchsJoues())
                .buts(request.buts())
                .passes(request.passes())
                .build();
        Joueur saved = joueurRepository.save(j);
        return mapToJoueurResponse(saved);
    }

    public List<JoueurResponse> getJoueursBySport(SportSection sport) {
        return joueurRepository.findBySport(sport)
                .stream().map(this::mapToJoueurResponse).collect(Collectors.toList());
    }

    public JoueurResponse updateJoueur(Long id, JoueurRequest request) {
        Joueur j = joueurRepository.findById(id)
                .orElseThrow(() -> new EntityNotFoundException("Joueur non trouvé"));
        j.setNom(request.nom());
        j.setPhotoUrl(request.photoUrl());
        j.setPoste(request.poste());
        j.setAge(request.age());
        j.setNumero(request.numero());
        j.setSport(request.sport());
        j.setMatchsJoues(request.matchsJoues());
        j.setButs(request.buts());
        j.setPasses(request.passes());
        Joueur saved = joueurRepository.save(j);
        return mapToJoueurResponse(saved);
    }

    public void deleteJoueur(Long id) {
        joueurRepository.deleteById(id);
    }

    private JoueurResponse mapToJoueurResponse(Joueur j) {
        return new JoueurResponse(
                j.getId(), j.getNom(), j.getPhotoUrl(), j.getPoste(),
                j.getAge(), j.getNumero(), j.getSport(),
                j.getMatchsJoues(), j.getButs(), j.getPasses()
        );
    }
}