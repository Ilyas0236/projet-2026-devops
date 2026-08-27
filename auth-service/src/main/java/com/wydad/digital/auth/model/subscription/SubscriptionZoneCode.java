package com.wydad.digital.auth.model.subscription;

/**
 * Grille officielle d'abonnement Wydad AC — saison à domicile.
 * Source : tableau B.12 "Termin du wydad catégorie".
 *
 * Pour chaque zone on publie DEUX prix : le tarif "abonné normal" et
 * le tarif "adhérent" (sous-ensemble des zones premium : VIP, VVIP,
 * VVIP+Parking). Les zones marquées SOLD_OUT ne sont plus vendables.
 */
public enum SubscriptionZoneCode {
    PELOUSE_ZONE_4 ("PEL-4",  "Pelouse Zone 4",  0,      0,      true),
    PELOUSE_ZONE_6 ("PEL-6",  "Pelouse Zone 6",  450,    450,    false),
    TRIBUNE        ("TRIB",  "Tribune Zone 2",  1500,   1500,   false),
    PREMIUM        ("PREM",  "Premium",         2500,   2500,   false),
    VIP            ("VIP",   "VIP",             5000,   5000,   false),
    VIP_ADHERENT   ("VIP-A", "VIP Adhérent",    4000,   4000,   false),
    VVIP           ("VVIP",  "VVIP",            25000,  25000,  false),
    VVIP_ADHERENT  ("VVIP-A","VVIP Adhérent",   15000,  15000,  false),
    VVIP_PARKING   ("VVIP-P","VVIP + Parking",  35000,  35000,  false),
    VVIP_PARKING_ADHERENT ("VVIP-PA", "VVIP + Parking Adhérent", 25000, 25000, false);

    private final String code;
    private final String displayName;
    /** Prix catalogue (DH) pour un supporter non-adhérent. */
    private final int priceRegular;
    /** Prix catalogue (DH) pour un adhérent. Pour les zones sans remise,
     *  ce prix est strictement inférieur au prix "abonné normal" car
     *  l'achat d'un abonnement octroie déjà l'économie vs 15 billets. */
    private final int priceAdherent;
    /** True si la zone n'est plus commercialisée. */
    private final boolean soldOut;

    SubscriptionZoneCode(String code, String displayName,
                         int priceRegular, int priceAdherent, boolean soldOut) {
        this.code = code;
        this.displayName = displayName;
        this.priceRegular = priceRegular;
        this.priceAdherent = priceAdherent;
        this.soldOut = soldOut;
    }

    public String getCode() { return code; }
    public String getDisplayName() { return displayName; }
    public int getPriceRegular() { return priceRegular; }
    public int getPriceAdherent() { return priceAdherent; }
    public boolean isSoldOut() { return soldOut; }
}
