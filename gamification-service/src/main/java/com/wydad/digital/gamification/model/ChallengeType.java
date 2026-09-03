package com.wydad.digital.gamification.model;

/**
 * B.8.c — Types de défis supportés.
 *
 * SUBMIT_PREDICTIONS : soumettre N pronostics pendant la fenêtre.
 * WIN_EXACT_SCORES   : avoir N scores exacts validés pendant la fenêtre.
 * REACH_POINTS       : atteindre un solde de N points à un moment de la fenêtre
 *                      (typiquement évalué à la fin, une seule fois par défi).
 */
public enum ChallengeType {
    SUBMIT_PREDICTIONS,
    WIN_EXACT_SCORES,
    REACH_POINTS
}
