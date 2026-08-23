package com.wydad.digital.content.config;

import com.wydad.digital.content.model.ClubSetting;
import com.wydad.digital.content.repository.ClubSettingRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * Seed des parametres club (paliers d'adhesion, coordonnees, competitions)
 * au premier demarrage uniquement : jamais de surcharge si l'ADMIN les a
 * modifies. Les prix sont alignes sur l'enum MembershipLevel du auth-service.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class ClubSettingsInitializer implements ApplicationRunner {

    private final ClubSettingRepository clubSettingRepository;

    @Override
    @Transactional
    public void run(ApplicationArguments args) {
        seedIfAbsent("membership_tiers", """
                [
                  {"level":"JUNIOR","name":"Junior","subtitle":"Moins de 16 ans","price":200,
                   "features":["Accès aux actualités","Carte membre digitale Junior","Activités académie WAC"]},
                  {"level":"ROUGE","name":"Rouge","price":500,"popular":true,
                   "features":["Tout du niveau Junior","Activation E-cash WydadPay","5% de réduction boutique","Priorité billetterie"]},
                  {"level":"OR","name":"Or","price":1200,
                   "features":["Tout du niveau Rouge","10% de réduction boutique","Priorité billetterie renforcée","Accès Tribune VIP (2 matchs/an)"]},
                  {"level":"DIAMANT","name":"Diamant","price":3000,
                   "features":["Tout du niveau Or","15% de réduction boutique","Priorité absolue billetterie","Droit de vote à l'AG","Rencontres exclusives joueurs"]},
                  {"level":"LEGENDE","name":"Légende","subtitle":"Sur invitation","price":null,
                   "features":["Tous les avantages Diamant","Abonnement Annuel VIP inclus","Maillot officiel dédicacé offert","Statut honorifique à vie"]}
                ]
                """);

        seedIfAbsent("competitions", """
                [
                  {"name":"Botola Pro","sport":"FOOTBALL"},
                  {"name":"CAF Champions League","sport":"FOOTBALL"},
                  {"name":"Coupe du Trone","sport":"FOOTBALL"},
                  {"name":"D1 Basketball","sport":"BASKETBALL"},
                  {"name":"Division Excellence","sport":"HANDBALL"}
                ]
                """);

        seedIfAbsent("club_info", """
                {
                  "email":"contact@wac.ma",
                  "telephone":"+212 5 22 00 00 00",
                  "adresse":"Complexe Mohamed Benjelloun, Casablanca",
                  "slogan":"Dima Wydad",
                  "saison":"2026/2027",
                  "description":"Le club de la nation. Fierté, passion et gloire depuis 1937."
                }
                """);
    }

    private void seedIfAbsent(String key, String json) {
        if (clubSettingRepository.findBySettingKey(key).isEmpty()) {
            clubSettingRepository.save(ClubSetting.builder()
                    .settingKey(key)
                    .settingValue(json)
                    .build());
            log.info("Parametre club '{}' initialise avec les valeurs par defaut", key);
        }
    }
}
