import { Injectable } from '@angular/core';
import { HttpClient, HttpParams } from '@angular/common/http';
import { Observable } from 'rxjs';
import { environment } from '../../environments/environment';

/**
 * Forme de la réponse du GET /api/auth/member-card (cf. refonte B.12).
 * La carte est 100% dérivée de l'abonnement saisonnier ACTIF. Si pas
 * d'abonnement actif, le backend renvoie 404 et le front affiche un
 * CTA d'achat.
 */
export interface MemberCardResponse {
  email: string;
  firstName: string;
  lastName: string;
  planCode: string;
  planName: string;
  season: string;
  validFrom: string;
  validTo: string;
  referralCode: string;
  qrCodeBase64: string;
}

@Injectable({
  providedIn: 'root'
})
export class ApiService {
  private baseUrl = environment.apiBaseUrl;

  constructor(private http: HttpClient) {}

  login(email: string, password: string): Observable<any> {
    return this.http.post(`${this.baseUrl}/auth/login`, { email, password });
  }

  getMemberCard(email: string): Observable<MemberCardResponse> {
    return this.http.get<MemberCardResponse>(`${this.baseUrl}/auth/member-card?email=${email}`);
  }

  getAttestation(email: string): Observable<Blob> {
    return this.http.get(`${this.baseUrl}/auth/attestation?email=${email}`, { responseType: 'blob' });
  }

  /** Badge presse PDF+QR (§17) — compte JOURNALISTE validé requis. */
  getBadgePresse(email: string): Observable<Blob> {
    return this.http.get(`${this.baseUrl}/auth/presse/badge?email=${encodeURIComponent(email)}`, { responseType: 'blob' });
  }

  // -------- B.12 — Abonnements saisonniers (carte adhérent) --------

  /**
   * Catalogue public des PLANS d'abonnement (gérés par l'admin).
   * Remplace {@link listSubscriptionZones} — la grille n'est plus hardcodée.
   * L'admin peut la modifier via /admin/abonnements/plans.
   */
  listSubscriptionPlans(): Observable<any[]> {
    return this.http.get<any[]>(`${this.baseUrl}/auth/subscriptions/plans`);
  }

  /**
   * @deprecated Conservé pour rétro-compat le temps de la bascule UI.
   * Le contrôleur renvoie désormais aussi les plans via /zones, mais
   * le format de réponse (SubscriptionZoneResponse enum) est distinct
   * de /plans. Préférer {@link listSubscriptionPlans}.
   */
  listSubscriptionZones(includeSoldOut = false): Observable<any[]> {
    const params = includeSoldOut ? `?includeSoldOut=true` : '';
    return this.http.get<any[]>(`${this.baseUrl}/auth/subscriptions/zones${params}`);
  }

  /** Abonnement actif courant (null si aucun). */
  getMyActiveSubscription(): Observable<any> {
    return this.http.get(`${this.baseUrl}/auth/subscriptions/me/active`);
  }

  /** Historique complet. */
  getMySubscriptionHistory(): Observable<any[]> {
    return this.http.get<any[]>(`${this.baseUrl}/auth/subscriptions/me/history`);
  }

  /**
   * V2.1 — Carte d'abonnement PDF (paysage 85x54mm) avec QR code d'accès
   * au stade. Régénéré à la volée côté back, le front n'a qu'à le télécharger.
   */
  getSubscriptionPdf(subscriptionId: number): Observable<Blob> {
    return this.http.get(
      `${this.baseUrl}/auth/subscriptions/${subscriptionId}/pdf`,
      { responseType: 'blob' }
    );
  }

  /**
   * V2.2 — Facture PDF d'un abonnement saisonnier (vue "comptable"
   * A4 distincte de la carte d'accès). Régénérée à la volée.
   */
  getSubscriptionInvoice(subscriptionId: number): Observable<Blob> {
    return this.http.get(
      `${this.baseUrl}/auth/subscriptions/${subscriptionId}/invoice`,
      { responseType: 'blob' }
    );
  }

  /**
   * Achat via paiement carte SIMULÉ. Le champ d'identification est
   * désormais {@code planCode} (code du plan, ex. "VIP") et non plus
   * {@code zoneCode} (valeur d'enum).
   */
  purchaseSubscription(req: {
    planCode: string;
    cardNumber: string;
    expiryDate: string;
    cvv: string;
    otp: string;
  }): Observable<any> {
    return this.http.post(`${this.baseUrl}/auth/subscriptions/purchase`, req);
  }

  // -------- Admin CRUD des plans d'abonnement --------

  /** Liste paginée (admin) — filtre optionnel par isActive. */
  adminListSubscriptionPlans(page = 0, size = 20, active?: boolean): Observable<any> {
    let url = `${this.baseUrl}/admin/subscription-plans?page=${page}&size=${size}`;
    if (active !== undefined) url += `&active=${active}`;
    return this.http.get<any>(url);
  }

  /** Détail d'un plan (admin). */
  adminGetSubscriptionPlan(id: number): Observable<any> {
    return this.http.get<any>(`${this.baseUrl}/admin/subscription-plans/${id}`);
  }

  /** Création (admin). Retourne 201 + plan créé. */
  adminCreateSubscriptionPlan(plan: any): Observable<any> {
    return this.http.post<any>(`${this.baseUrl}/admin/subscription-plans`, plan);
  }

  /** Mise à jour complète (admin). */
  adminUpdateSubscriptionPlan(id: number, plan: any): Observable<any> {
    return this.http.put<any>(`${this.baseUrl}/admin/subscription-plans/${id}`, plan);
  }

  /** Suppression (admin). 409 si le plan est référencé par un abonnement. */
  adminDeleteSubscriptionPlan(id: number): Observable<any> {
    return this.http.delete<any>(`${this.baseUrl}/admin/subscription-plans/${id}`);
  }

  /**
   * Upload de la photo de carte d'un plan (multipart, max 5 Mo, PNG/JPG).
   * Renvoie le plan complet avec le nouveau {@code cardImageUrl}.
   */
  adminUploadPlanCardImage(planId: number, file: File): Observable<any> {
    const form = new FormData();
    form.append('file', file);
    return this.http.post<any>(`${this.baseUrl}/admin/subscription-plans/${planId}/card-image`, form);
  }

  /**
   * Retire la photo de carte d'un plan (met {@code cardImageUrl = null}).
   * Renvoie le plan complet mis à jour.
   */
  adminDeletePlanCardImage(planId: number): Observable<any> {
    return this.http.delete<any>(`${this.baseUrl}/admin/subscription-plans/${planId}/card-image`);
  }

  getArticles(): Observable<any[]> {
    return this.http.get<any[]>(`${this.baseUrl}/content/articles`);
  }

  getArticleById(id: number): Observable<any> {
    return this.http.get<any>(`${this.baseUrl}/content/articles/${id}`);
  }

  createArticle(article: any): Observable<any> {
    return this.http.post<any>(`${this.baseUrl}/content/articles`, article);
  }

  updateArticle(id: number, article: any): Observable<any> {
    return this.http.put<any>(`${this.baseUrl}/content/articles/${id}`, article);
  }

  deleteArticle(id: number): Observable<any> {
    return this.http.delete<any>(`${this.baseUrl}/content/articles/${id}`);
  }

  getMatches(): Observable<any[]> {
    return this.http.get<any[]>(`${this.baseUrl}/content/matches`);
  }

  getMatchesByStatut(statut: string): Observable<any[]> {
    return this.http.get<any[]>(`${this.baseUrl}/content/matches/statut/${statut}`);
  }

  createMatch(match: any): Observable<any> {
    return this.http.post<any>(`${this.baseUrl}/content/matches`, match);
  }

  updateMatch(id: number, match: any): Observable<any> {
    return this.http.put<any>(`${this.baseUrl}/content/matches/${id}`, match);
  }

  deleteMatch(id: number): Observable<any> {
    return this.http.delete<any>(`${this.baseUrl}/content/matches/${id}`);
  }

  updateMatchResult(id: number, scoreWydad: number, scoreAdversaire: number): Observable<any> {
    const params = `scoreWydad=${scoreWydad}&scoreAdversaire=${scoreAdversaire}`;
    return this.http.post<any>(`${this.baseUrl}/content/matches/${id}/result?${params}`, {});
  }

  getClassements(competition: string): Observable<any[]> {
    return this.http.get<any[]>(`${this.baseUrl}/content/classements/${competition}`);
  }

  getAllClassements(): Observable<any[]> {
    return this.http.get<any[]>(`${this.baseUrl}/content/classements`);
  }

  /** Parametre club 'competitions' — source de verite ADMIN. */
  getCompetitions(): Observable<any> {
    return this.getClubSetting('competitions');
  }

  createClassement(classement: any): Observable<any> {
    return this.http.post<any>(`${this.baseUrl}/content/classements`, classement);
  }

  updateClassement(id: number, classement: any): Observable<any> {
    return this.http.put<any>(`${this.baseUrl}/content/classements/${id}`, classement);
  }

  deleteClassement(id: number): Observable<any> {
    return this.http.delete<any>(`${this.baseUrl}/content/classements/${id}`);
  }

  getJoueursBySport(sport: string): Observable<any[]> {
    return this.http.get<any[]>(`${this.baseUrl}/content/joueurs/sport/${sport}`);
  }

  // --- Fonctionnalité 6/6 : gestion ADMIN des fiches publiques joueurs (stats) ---
  createJoueur(joueur: any): Observable<any> {
    return this.http.post<any>(`${this.baseUrl}/content/joueurs`, joueur);
  }

  updateJoueur(id: number, joueur: any): Observable<any> {
    return this.http.put<any>(`${this.baseUrl}/content/joueurs/${id}`, joueur);
  }

  deleteJoueur(id: number): Observable<any> {
    return this.http.delete<any>(`${this.baseUrl}/content/joueurs/${id}`);
  }

  // --- Palmarès du club : lecture publique, écriture ADMIN (TrophySecurityTest) ---
  getPublicTrophies(): Observable<any[]> {
    return this.http.get<any[]>(`${this.baseUrl}/content/trophies/public`);
  }

  getAllTrophies(): Observable<any[]> {
    return this.http.get<any[]>(`${this.baseUrl}/content/trophies`);
  }

  createTrophy(trophy: any): Observable<any> {
    return this.http.post<any>(`${this.baseUrl}/content/trophies`, trophy);
  }

  updateTrophy(id: number, trophy: any): Observable<any> {
    return this.http.put<any>(`${this.baseUrl}/content/trophies/${id}`, trophy);
  }

  deleteTrophy(id: number): Observable<any> {
    return this.http.delete<any>(`${this.baseUrl}/content/trophies/${id}`);
  }

  // --- Légendes (Hall of Fame) : lecture publique, écriture ADMIN (ClubLegendSecurityTest) ---
  getPublicLegends(): Observable<any[]> {
    return this.http.get<any[]>(`${this.baseUrl}/content/legends/public`);
  }

  getAllLegends(): Observable<any[]> {
    return this.http.get<any[]>(`${this.baseUrl}/content/legends`);
  }

  createLegend(legend: any): Observable<any> {
    return this.http.post<any>(`${this.baseUrl}/content/legends`, legend);
  }

  updateLegend(id: number, legend: any): Observable<any> {
    return this.http.put<any>(`${this.baseUrl}/content/legends/${id}`, legend);
  }

  deleteLegend(id: number): Observable<any> {
    return this.http.delete<any>(`${this.baseUrl}/content/legends/${id}`);
  }

  // Parametres club (paliers adhesion, coordonnees) — source de verite ADMIN
  getClubSettings(): Observable<any[]> {
    return this.http.get<any[]>(`${this.baseUrl}/content/settings`);
  }

  getClubSetting(key: string): Observable<any> {
    return this.http.get(`${this.baseUrl}/content/settings/${key}`);
  }

  updateClubSetting(key: string, value: any): Observable<any> {
    return this.http.put(`${this.baseUrl}/content/settings/${key}`, value);
  }

  // NEWSLETTER publique — inscription anonyme depuis le footer (notification-service)
  subscribeNewsletter(email: string): Observable<any> {
    return this.http.post(`${this.baseUrl}/notification/newsletter/subscribe`, { email });
  }

  // SPONSORS (B.7) — lecture publique, ecriture ADMIN
  getSponsorsPublic(): Observable<any[]> {
    return this.http.get<any[]>(`${this.baseUrl}/content/sponsors/public`);
  }

  getAllSponsors(): Observable<any[]> {
    return this.http.get<any[]>(`${this.baseUrl}/content/sponsors`);
  }

  createSponsor(sponsor: any): Observable<any> {
    return this.http.post<any>(`${this.baseUrl}/content/sponsors`, sponsor);
  }

  updateSponsor(id: number, sponsor: any): Observable<any> {
    return this.http.put<any>(`${this.baseUrl}/content/sponsors/${id}`, sponsor);
  }

  deleteSponsor(id: number): Observable<any> {
    return this.http.delete<any>(`${this.baseUrl}/content/sponsors/${id}`);
  }

  // RECLAMATIONS & SUPPORT (B.10) — identite imposee par la gateway
  createReclamation(data: { subject: string; title: string; description: string }): Observable<any> {
    return this.http.post<any>(`${this.baseUrl}/content/reclamations`, data);
  }

  getMyReclamations(): Observable<any[]> {
    return this.http.get<any[]>(`${this.baseUrl}/content/reclamations/mine`);
  }

  getAllReclamations(): Observable<any[]> {
    return this.http.get<any[]>(`${this.baseUrl}/content/reclamations`);
  }

  respondReclamation(id: number, response: string, status: string): Observable<any> {
    return this.http.put<any>(`${this.baseUrl}/content/reclamations/${id}/response`, { response, status });
  }

  getBalance(email: string): Observable<any> {
    return this.http.get(`${this.baseUrl}/payment/balance?email=${email}`);
  }

  getTransactions(email: string): Observable<any[]> {
    return this.http.get<any[]>(`${this.baseUrl}/payment/transactions?email=${email}`);
  }

  makeDon(email: string, amount: number, type: string, recuFiscal: boolean): Observable<Blob> {
    return this.http.post(`${this.baseUrl}/payment/don`, {
      email, amount, type, recuFiscal
    }, { responseType: 'blob' });
  }

  creditWallet(email: string, amount: number, description: string): Observable<any> {
    return this.http.post(`${this.baseUrl}/payment/credit`, { email, amount, description });
  }

  payByCard(email: string, cardInfo: any): Observable<any> {
    return this.http.post(`${this.baseUrl}/payment/card?email=${email}`, cardInfo);
  }

  // ==========================================
  // SHOP SERVICE (Boutique)
  // ==========================================
  getProducts(): Observable<any[]> {
    return this.http.get<any[]>(`${this.baseUrl}/shop/products`);
  }

  getProductById(id: number): Observable<any> {
    return this.http.get<any>(`${this.baseUrl}/shop/products/${id}`);
  }

  createProduct(product: any): Observable<any> {
    return this.http.post<any>(`${this.baseUrl}/shop/products`, product);
  }

  updateProduct(id: number, product: any): Observable<any> {
    return this.http.put<any>(`${this.baseUrl}/shop/products/${id}`, product);
  }

  deleteProduct(id: number): Observable<any> {
    return this.http.delete<any>(`${this.baseUrl}/shop/products/${id}`);
  }

  // ========== PANIER (CART) ==========
  getCart(): Observable<any[]> {
    return this.http.get<any[]>(`${this.baseUrl}/shop/cart`);
  }

  addToCart(item: any): Observable<any> {
    return this.http.post<any>(`${this.baseUrl}/shop/cart`, item);
  }

  updateCartQuantity(cartItemId: number, quantity: number): Observable<any> {
    return this.http.put<any>(`${this.baseUrl}/shop/cart/${cartItemId}?quantity=${quantity}`, {});
  }

  removeFromCart(cartItemId: number): Observable<any> {
    return this.http.delete<any>(`${this.baseUrl}/shop/cart/${cartItemId}`);
  }

  // ========== COMMANDES (ORDERS) ==========
  createOrder(order: any): Observable<any> {
    return this.http.post<any>(`${this.baseUrl}/shop/orders`, order);
  }

  getMyOrders(): Observable<any> {
    return this.http.get<any>(`${this.baseUrl}/shop/orders`);
  }

  getOrderByNumber(orderNumber: string): Observable<any> {
    return this.http.get<any>(`${this.baseUrl}/shop/orders/${orderNumber}`);
  }

  /**
   * V2.2 — Facture PDF d'une commande boutique (vue "comptable" A4,
   * distincte du bordereau de livraison). Régénérée à la volée.
   * La garde d'ownership est faite côté back (X-User-Email).
   */
  getOrderInvoice(orderNumber: string): Observable<Blob> {
    return this.http.get(
      `${this.baseUrl}/shop/orders/${orderNumber}/invoice`,
      { responseType: 'blob' }
    );
  }

  /** Toutes les commandes (réservé ADMIN) */
  getAllOrders(): Observable<any> {
    return this.http.get<any>(`${this.baseUrl}/shop/orders/all`);
  }

  /** Changement de statut d'une commande (réservé ADMIN, transitions validées serveur) */
  updateOrderStatus(orderNumber: string, status: string, comment?: string): Observable<any> {
    return this.http.patch<any>(`${this.baseUrl}/shop/orders/${orderNumber}/status`, {
      status,
      comment: comment || null
    });
  }

  // ========== CODES PROMO (réservé ADMIN) ==========
  getPromoCodes(): Observable<any[]> {
    return this.http.get<any[]>(`${this.baseUrl}/shop/promo-codes`);
  }

  createPromoCode(promo: any): Observable<any> {
    return this.http.post<any>(`${this.baseUrl}/shop/promo-codes`, promo);
  }

  setPromoCodeActive(promoId: number, active: boolean): Observable<any> {
    return this.http.patch<any>(`${this.baseUrl}/shop/promo-codes/${promoId}/active`, { active });
  }
  // TICKET SERVICE (Billetterie)
  // ==========================================
  getEvents(): Observable<any[]> {
    return this.http.get<any[]>(`${this.baseUrl}/ticket/events`);
  }

  /**
   * Grille tarifaire des catégories de billets (TRIBUNE_OFFICIELLE, VIP, etc.)
   * pilotée par le back — alimentée par l'enum enrichi {@code TicketCategory}.
   * Alimente les <select> du formulaire admin billetterie : l'admin n'a plus
   * à saisir la valeur enum à la main, et le prix par défaut est pré-rempli.
   */
  getTicketCategories(): Observable<{ code: string; label: string; defaultPrice: number }[]> {
    return this.http.get<{ code: string; label: string; defaultPrice: number }[]>(
      `${this.baseUrl}/ticket/categories`);
  }

  getEventById(id: number): Observable<any> {
    return this.http.get<any>(`${this.baseUrl}/ticket/events/${id}`);
  }

  createEvent(event: any): Observable<any> {
    return this.http.post<any>(`${this.baseUrl}/ticket/events`, event);
  }

  updateEvent(id: number, event: any): Observable<any> {
    return this.http.put<any>(`${this.baseUrl}/ticket/events/${id}`, event);
  }

  deleteEvent(id: number): Observable<any> {
    return this.http.delete<any>(`${this.baseUrl}/ticket/events/${id}`);
  }

  /**
   * PATCH partiel d'une section (ADMIN).
   * Permet de corriger le prix d'une section existante SANS casser
   * l'historique des billets vendus (PUT sur l'event supprimerait+recréerait
   * les sections, ce qui viole la FK tickets.section_id).
   */
  patchSection(sectionId: number, patch: { name?: string; price?: number; capacity?: number; category?: string; seatType?: string }): Observable<any> {
    return this.http.patch<any>(`${this.baseUrl}/ticket/sections/${sectionId}`, patch);
  }

  /**
   * V3.1 — création d'une section sur un événement existant (ADMIN).
   * Refus si la catégorie est déjà présente (409 côté back).
   */
  createSection(eventId: number, body: { name: string; category: string; capacity: number; price: number; seatType?: string }): Observable<any> {
    return this.http.post<any>(`${this.baseUrl}/ticket/sections`, body, {
      params: new HttpParams().set('eventId', String(eventId))
    });
  }

  /**
   * V3.1 — suppression d'une section (ADMIN). Refus si la section a des
   * billets vendus (409 côté back).
   */
  deleteSection(sectionId: number): Observable<void> {
    return this.http.delete<void>(`${this.baseUrl}/ticket/sections/${sectionId}`);
  }

  purchaseTickets(purchaseRequest: any): Observable<any[]> {
    return this.http.post<any[]>(`${this.baseUrl}/ticket/tickets/purchase`, purchaseRequest);
  }

  getTicketsByUser(userId: number): Observable<any[]> {
    return this.http.get<any[]>(`${this.baseUrl}/ticket/tickets/user/${userId}`);
  }

  getTicketByNumber(ticketNumber: string): Observable<any> {
    return this.http.get<any>(`${this.baseUrl}/ticket/tickets/number/${ticketNumber}`);
  }

  getTicketPdf(ticketId: number): Observable<Blob> {
    return this.http.get(`${this.baseUrl}/ticket/tickets/${ticketId}/pdf`, { responseType: 'blob' });
  }

  /**
   * V2.2 — Facture PDF d'un billet (vue "comptable" A4, distincte
   * du billet d'entrée avec QR). Régénérée à la volée.
   */
  getTicketInvoice(ticketId: number): Observable<Blob> {
    return this.http.get(`${this.baseUrl}/ticket/tickets/${ticketId}/invoice`, { responseType: 'blob' });
  }

  /** QR code genere cote backend (zxing) — aucun service externe. */
  getTicketQr(ticketId: number): Observable<Blob> {
    return this.http.get(`${this.baseUrl}/ticket/tickets/${ticketId}/qr`, { responseType: 'blob' });
  }

  // ==========================================
  // SPORTS SERVICE (Effectif)
  // ==========================================
  createPlayer(player: any): Observable<any> {
    return this.http.post<any>(`${this.baseUrl}/sports/players`, player);
  }

  updatePlayer(id: number, player: any): Observable<any> {
    return this.http.put<any>(`${this.baseUrl}/sports/players/${id}`, player);
  }

  deletePlayer(id: number): Observable<any> {
    return this.http.delete<any>(`${this.baseUrl}/sports/players/${id}`);
  }

  // ==========================================
  // NOTIFICATION SERVICE
  // ==========================================
  getAllNotifications(): Observable<any[]> {
    return this.http.get<any[]>(`${this.baseUrl}/notification/all`);
  }

  sendNotification(notification: any): Observable<any> {
    return this.http.post<any>(`${this.baseUrl}/notification/send`, notification);
  }

  broadcastNotification(notification: any): Observable<any> {
    return this.http.post<any>(`${this.baseUrl}/notification/broadcast`, notification, { responseType: 'text' as 'json' });
  }

  // --- B.11 : espace de notifications personnel (ownership serveur prouvé) ---
  getMyNotifications(userId: number): Observable<any[]> {
    return this.http.get<any[]>(`${this.baseUrl}/notification/user/${userId}`);
  }

  getMyUnreadCount(userId: number): Observable<number> {
    return this.http.get<number>(`${this.baseUrl}/notification/user/${userId}/unread/count`);
  }

  markNotificationRead(notificationId: number): Observable<any> {
    return this.http.patch<any>(`${this.baseUrl}/notification/${notificationId}/read`, {});
  }

  // --- Fonctionnalité 4/6 : préférences de notification (appliquées à l'envoi côté serveur) ---
  getMyPreferences(): Observable<any> {
    return this.http.get<any>(`${this.baseUrl}/notification/preferences`);
  }

  updateMyPreferences(prefs: { emailEnabled: boolean; pushEnabled: boolean; inAppEnabled: boolean }): Observable<any> {
    return this.http.put<any>(`${this.baseUrl}/notification/preferences`, prefs);
  }

  // ==========================================
  // ACADEMY SERVICE
  // ==========================================
  registerAcademyChild(data: any): Observable<any> {
    return this.http.post<any>(`${this.baseUrl}/sports/academy/register`, data);
  }

  /** Upload d'une pièce justificative (certificat médical, etc.) sur un dossier. */
  uploadAcademyDocument(academyMemberId: number, docType: string, file: File): Observable<any> {
    const form = new FormData();
    form.append('docType', docType);
    form.append('file', file, file.name);
    return this.http.post<any>(
      `${this.baseUrl}/sports/academy/${academyMemberId}/documents`, form);
  }

  getAcademyChildrenByParent(parentId: number): Observable<any[]> {
    return this.http.get<any[]>(`${this.baseUrl}/sports/academy/parent/${parentId}`);
  }

  /** Liste globale des dossiers d'inscription (STAFF/ADMIN). */
  getAllAcademyFolders(): Observable<any[]> {
    return this.http.get<any[]>(`${this.baseUrl}/sports/academy/all`);
  }

  /** Métadonnées des pièces justificatives d'un dossier (sans les blobs). */
  getAcademyDocuments(academyMemberId: number): Observable<any[]> {
    return this.http.get<any[]>(`${this.baseUrl}/sports/academy/${academyMemberId}/documents`);
  }

  /** Téléchargement (blob authentifié) d'une pièce par type. */
  getAcademyDocumentBlob(academyMemberId: number, docType: string): Observable<Blob> {
    return this.http.get(
      `${this.baseUrl}/sports/academy/${academyMemberId}/documents/${docType}`,
      { responseType: 'blob' });
  }

  /** Validation / rejet d'un dossier par le staff ou l'admin. */
  updateAcademyStatus(id: number, active: boolean): Observable<any> {
    return this.http.patch<any>(`${this.baseUrl}/sports/academy/${id}/status?active=${active}`, {});
  }

  // ==========================================
  // SONDAGES (B.2) — servis par election-service (migration audit thématique)
  // ==========================================
  getActivePolls(): Observable<any[]> {
    return this.http.get<any[]>(`${this.baseUrl}/polls/active`);
  }

  votePoll(pollId: number, optionIndex: number): Observable<any> {
    return this.http.post<any>(`${this.baseUrl}/polls/${pollId}/vote?optionIndex=${optionIndex}`, {});
  }

  createPoll(question: string, options: string[]): Observable<any> {
    return this.http.post<any>(`${this.baseUrl}/polls`, { question, options });
  }

  closePoll(pollId: number): Observable<any> {
    return this.http.post<any>(`${this.baseUrl}/polls/${pollId}/close`, {});
  }

  // ==========================================
  // ÉLECTIONS DU PRÉSIDENT (B.8) — servis par election-service
  // ==========================================
  /** Site public : résultats publiés, visibles sans connexion. */
  getPublishedElections(): Observable<any[]> {
    return this.http.get<any[]>(`${this.baseUrl}/elections/published`);
  }

  getLatestPublishedElection(): Observable<any> {
    return this.http.get<any>(`${this.baseUrl}/elections/published/latest`);
  }

  /** Espace adhérent : élections ouvertes + état de vote de l'utilisateur. */
  getOpenElections(): Observable<any[]> {
    return this.http.get<any[]>(`${this.baseUrl}/elections/open`);
  }

  voteElection(electionId: number, candidateId: number): Observable<any> {
    return this.http.post<any>(`${this.baseUrl}/elections/${electionId}/vote`, { candidateId });
  }

  createElection(title: string, startsAt: string, endsAt: string): Observable<any> {
    return this.http.post<any>(`${this.baseUrl}/elections`, { title, startsAt, endsAt });
  }

  addElectionCandidate(electionId: number, fullName: string, presentation?: string,
                       photoUrl?: string): Observable<any> {
    const body: any = { fullName };
    if (presentation) { body.presentation = presentation; }
    if (photoUrl) { body.photoUrl = photoUrl; }
    return this.http.post<any>(`${this.baseUrl}/elections/${electionId}/candidates`, body);
  }

  removeElectionCandidate(electionId: number, candidateId: number): Observable<any> {
    return this.http.delete<any>(`${this.baseUrl}/elections/${electionId}/candidates/${candidateId}`);
  }

  closeElection(electionId: number): Observable<any> {
    return this.http.post<any>(`${this.baseUrl}/elections/${electionId}/close`, {});
  }

  // ==========================================
  // ESPACE JOUEUR (B.3 / B.3.a)
  // ==========================================
  getMyConvocations(): Observable<any[]> {
    return this.http.get<any[]>(`${this.baseUrl}/sports/my-space/convocations`);
  }

  respondToConvocation(convocationId: number, status: string, justification?: string): Observable<any> {
    const body: any = { status };
    if (justification) { body.justification = justification; }
    return this.http.post<any>(
      `${this.baseUrl}/sports/my-space/convocations/${convocationId}/respond`, body);
  }

  /** Historique de présence du joueur connecté (réponses déjà données). */
  getMyPresence(): Observable<any[]> {
    return this.http.get<any[]>(`${this.baseUrl}/sports/my-space/presence`);
  }

  getMyDocuments(): Observable<any[]> {
    return this.http.get<any[]>(`${this.baseUrl}/sports/my-space/documents`);
  }

  updateMyProfile(body: any): Observable<any> {
    return this.http.put<any>(`${this.baseUrl}/sports/my-space/profile`, body);
  }

  /** Photo de profil du joueur connecté (multipart, max 5 Mo). */
  uploadMyPhoto(file: File): Observable<any> {
    const form = new FormData();
    form.append('file', file);
    return this.http.post<any>(`${this.baseUrl}/sports/my-space/profile/photo`, form);
  }

  /** Convocation d'un joueur par le staff (B.3.a) — réservée au staff de la catégorie. */
  createConvocation(joueurUserId: number, sessionId: number): Observable<any> {
    return this.http.post<any>(
      `${this.baseUrl}/sports/my-space/staff/convocations?joueurUserId=${joueurUserId}&sessionId=${sessionId}`, {});
  }

  // ==========================================
  // PHASE 3 — CONVOCATIONS GROUPEES & SUIVI
  // ==========================================
  /** Convocation groupée (« liste cochable ») : N joueurs, une séance. */
  createBatchConvocation(sessionId: number, joueurUserIds: number[]): Observable<any> {
    return this.http.post<any>(`${this.baseUrl}/sports/my-space/staff/convocations/batch`,
      { sessionId, joueurUserIds });
  }

  /** Réponses (présence + lecture) des joueurs pour une séance. */
  getSessionResponses(sessionId: number): Observable<any[]> {
    return this.http.get<any[]>(`${this.baseUrl}/sports/my-space/staff/sessions/${sessionId}/responses`);
  }

  /** Compteurs de suivi d'une séance (lu/non lu, confirmés/absents/retards). */
  getSessionSummary(sessionId: number): Observable<any> {
    return this.http.get<any>(`${this.baseUrl}/sports/my-space/staff/sessions/${sessionId}/responses/summary`);
  }

  /** Marquer SA convocation comme lue (accusé de lecture). */
  markConvocationRead(convocationId: number): Observable<any> {
    return this.http.post<any>(`${this.baseUrl}/sports/my-space/convocations/${convocationId}/read`, {});
  }

  /** Envoi d'un média tactique (multipart) à un joueur ou toute l'équipe. */
  shareMedia(file: File, title: string, message: string | null,
             opts: { joueurUserId?: number; wholeTeam?: boolean } = {}): Observable<any> {
    const form = new FormData();
    form.append('file', file);
    form.append('title', title);
    if (message && message.trim()) { form.append('message', message.trim()); }
    if (opts.joueurUserId != null) { form.append('joueurUserId', String(opts.joueurUserId)); }
    if (opts.wholeTeam) { form.append('wholeTeam', 'true'); }
    return this.http.post<any>(`${this.baseUrl}/sports/my-space/staff/media`, form);
  }

  /** Médias émis par le staff connecté (historique). */
  getSentMedia(): Observable<any[]> {
    return this.http.get<any[]>(`${this.baseUrl}/sports/my-space/staff/media/sent`);
  }


  /** Partage d'un document avec un joueur (staff/admin). */
  shareDocument(joueurUserId: number, title: string, url: string): Observable<any> {
    return this.http.post<any>(
      `${this.baseUrl}/sports/my-space/staff/documents?joueurUserId=${joueurUserId}`,
      { title, url });
  }

  // ==========================================
  // STATISTIQUES JOUEUR (B.4)
  // ==========================================
  getMyStats(): Observable<any[]> {
    return this.http.get<any[]>(`${this.baseUrl}/sports/my-space/stats`);
  }

  /** Saisie d'une stat de match par le staff de la catégorie (ou l'admin). */
  addPlayerStat(joueurUserId: number, body: any): Observable<any> {
    return this.http.post<any>(
      `${this.baseUrl}/sports/my-space/staff/stats?joueurUserId=${joueurUserId}`, body);
  }

  /** Consultation des stats détaillées d'un joueur (staff/admin). */
  getPlayerStats(joueurUserId: number): Observable<any[]> {
    return this.http.get<any[]>(`${this.baseUrl}/sports/my-space/staff/stats?joueurUserId=${joueurUserId}`);
  }

  // ==========================================
  // MESSAGERIE ET ANNONCES (B.5)
  // ==========================================
  sendMessage(
    toUserId: number,
    content: string,
    attachment?: {
      publicId: string;
      secureUrl: string;
      resourceType: string;
      fileName: string;
      sizeBytes: number;
    }
  ): Observable<any> {
    const params = new HttpParams().set('toUserId', String(toUserId));
    const body = attachment
      ? {
          content,
          attachmentPublicId: attachment.publicId,
          attachmentSecureUrl: attachment.secureUrl,
          attachmentResourceType: attachment.resourceType,
          attachmentFileName: attachment.fileName,
          attachmentSizeBytes: attachment.sizeBytes
        }
      : { content };
    return this.http.post<any>(`${this.baseUrl}/sports/messaging/send`, body, { params });
  }

  /**
   * V2.3 — upload d'une pièce jointe de message (image / PDF / doc,
   * max 10 Mo). Renvoie les métadonnées à passer ensuite à
   * {@link sendMessage}.
   */
  uploadMessageAttachment(file: File): Observable<{
    publicId: string;
    secureUrl: string;
    resourceType: string;
    fileName: string;
    sizeBytes: number;
    cloud: boolean;
  }> {
    const fd = new FormData();
    fd.append('file', file);
    return this.http.post<any>(`${this.baseUrl}/sports/messaging/upload`, fd);
  }

  /**
   * V2.3 — récupère une URL signée fraîche (1 h) pour afficher /
   * télécharger une pièce jointe d'un message dont on est participant.
   */
  getMessageAttachmentUrl(messageId: number): Observable<{ url: string; resourceType: string }> {
    return this.http.get<{ url: string; resourceType: string }>(
      `${this.baseUrl}/sports/messaging/attachment/${messageId}`
    );
  }

  getConversation(otherUserId: number): Observable<any[]> {
    return this.http.get<any[]>(`${this.baseUrl}/sports/messaging/conversation/${otherUserId}`);
  }

  getInbox(): Observable<any[]> {
    return this.http.get<any[]>(`${this.baseUrl}/sports/messaging/inbox`);
  }

  /** Publication d'une annonce (staff/admin) ; ciblage optionnel sport/catégorie. */
  publishAnnouncement(body: { title: string; body: string }, sportType?: string, category?: string): Observable<any> {
    let params = new HttpParams();
    if (sportType) params = params.set('sportType', sportType);
    if (category) params = params.set('category', category);
    return this.http.post<any>(`${this.baseUrl}/sports/messaging/announcements`, body, { params });
  }

  /** Annonces visibles par le connecté (filtrage serveur : club + sa catégorie). */
  getVisibleAnnouncements(): Observable<any[]> {
    return this.http.get<any[]>(`${this.baseUrl}/sports/messaging/announcements`);
  }

  // ==========================================
  // CONVOCATIONS DE MATCH (§8/§9) — feuille entraîneur → Admin → public
  // ==========================================
  /** Joueurs sélectionnables pour un match (groupe du match uniquement). */
  getSelectablePlayers(matchId: number): Observable<any[]> {
    return this.http.get<any[]>(`${this.baseUrl}/sports/match-convocations/match/${matchId}/selectable`);
  }

  /** Convocation groupée d'un match : liste {joueurUserId, playerRole}. */
  convocateBatchForMatch(matchId: number, players: { joueurUserId: number; playerRole: string }[]): Observable<any> {
    return this.http.post<any>(`${this.baseUrl}/sports/match-convocations/match/${matchId}`, { players });
  }

  /** Feuille de match (vue encadrement). */
  getMatchSheet(matchId: number): Observable<any[]> {
    return this.http.get<any[]>(`${this.baseUrl}/sports/match-convocations/match/${matchId}`);
  }

  /** Soumission de la feuille à l'ADMIN. */
  submitMatchSheet(matchId: number): Observable<any> {
    return this.http.post<any>(`${this.baseUrl}/sports/match-convocations/match/${matchId}/submit`, {});
  }

  /** Convocations de match du joueur connecté. */
  getMyMatchConvocations(): Observable<any[]> {
    return this.http.get<any[]>(`${this.baseUrl}/sports/match-convocations/my`);
  }

  /** §9 — vue publique anonyme des convocations PUBLIÉES d'un match. */
  getPublicConvocations(matchId: number): Observable<any> {
    return this.http.get(`${this.baseUrl}/sports/match-convocations/public/match/${matchId}`);
  }

  /** Feuilles soumises en attente de décision ADMIN. */
  getSubmittedSheets(): Observable<any[]> {
    return this.http.get<any[]>(`${this.baseUrl}/sports/match-convocations/admin/submitted`);
  }

  publishMatchSheet(matchId: number): Observable<any> {
    return this.http.post<any>(`${this.baseUrl}/sports/match-convocations/admin/match/${matchId}/publish`, {});
  }

  rejectMatchSheet(matchId: number, reason: string): Observable<any> {
    return this.http.post<any>(
      `${this.baseUrl}/sports/match-convocations/admin/match/${matchId}/reject`, { reason });
  }

  // ==========================================
  // PHASE 4 — CHAT DE GROUPE (WebSocket + repli REST)
  // ==========================================
  /** Historique persisté des 100 derniers messages du groupe. */
  getTeamChatHistory(sportType: string, category: string): Observable<any[]> {
    return this.http.get<any[]>(`${this.baseUrl}/sports/team-chat/${sportType}/${category}/messages`);
  }

  /** Envoi via le repli REST (si le socket est momentanément coupé). */
  sendTeamChatMessage(sportType: string, category: string, content: string): Observable<any> {
    return this.http.post<any>(
      `${this.baseUrl}/sports/team-chat/${sportType}/${category}/messages`, { content });
  }

  /** En-tête du groupe : joueurs + staff de la catégorie (contrôle serveur). */
  getTeamChatMembers(sportType: string, category: string): Observable<any[]> {
    return this.http.get<any[]>(`${this.baseUrl}/sports/team-chat/${sportType}/${category}/members`);
  }


  /** Pose APT/INAPTE — réservé au staff médical de la catégorie (contrôle serveur). */
  setMedicalStatus(joueurUserId: number, status: 'APT' | 'INAPTE', note?: string): Observable<any> {
    const params = new HttpParams().set('joueurUserId', String(joueurUserId));
    return this.http.put<any>(
      `${this.baseUrl}/sports/my-space/staff/medical-status`, { status, note }, { params });
  }

  // ==========================================
  // ESPACES METIERS (Joueur & Staff)
  // ==========================================
  getPlayerByUserId(userId: number): Observable<any> {
    return this.http.get<any>(`${this.baseUrl}/sports/players/user/${userId}`);
  }

  getPlayersByCategory(sportType: string, category: string): Observable<any[]> {
    return this.http.get<any[]>(`${this.baseUrl}/sports/players/filter?sportType=${sportType}&category=${category}`);
  }

  createSession(session: any): Observable<any> {
    return this.http.post<any>(`${this.baseUrl}/sports/sessions`, session);
  }

  getSessionsByCategory(sportType: string, category: string): Observable<any[]> {
    return this.http.get<any[]>(`${this.baseUrl}/sports/sessions/filter?sportType=${sportType}&category=${category}`);
  }

  getStaffByUserId(userId: number): Observable<any> {
    return this.http.get<any>(`${this.baseUrl}/sports/staff/user/${userId}`);
  }

  getAllStaff(): Observable<any[]> {
    return this.http.get<any[]>(`${this.baseUrl}/sports/staff`);
  }

  createStaff(staff: any): Observable<any> {
    return this.http.post<any>(`${this.baseUrl}/sports/staff`, staff);
  }

  updateStaff(id: number, staff: any): Observable<any> {
    return this.http.put<any>(`${this.baseUrl}/sports/staff/${id}`, staff);
  }

  deleteStaff(id: number): Observable<any> {
    return this.http.delete(`${this.baseUrl}/sports/staff/${id}`);
  }

  // ==========================================
  // CONTENT SERVICE : Mediatheque (ADMIN)
  // ==========================================
  getMediaLibrary(): Observable<any[]> {
    return this.http.get<any[]>(`${this.baseUrl}/content/media`);
  }

  deleteMedia(id: number): Observable<any> {
    return this.http.delete(`${this.baseUrl}/content/media/${id}`);
  }

  // ==========================================
  // ADMIN : Gestion des Comptes
  // ==========================================
  adminCreateUser(data: any): Observable<any> {
    return this.http.post<any>(`${this.baseUrl}/auth/admin/users/create`, data);
  }

  getAllUsers(): Observable<any[]> {
    return this.http.get<any[]>(`${this.baseUrl}/auth/admin/users`);
  }

  /** File de validation : comptes EN_ATTENTE (écran admin « demandes »). */
  getPendingDemandes(): Observable<any[]> {
    return this.http.get<any[]>(`${this.baseUrl}/auth/admin/accounts/pending`);
  }

  // NB : changeUserRole/toggleUserActive vivent dans AuthService (utilisées
  // par admin-users). Ne pas les dupliquer ici.

  // ==========================================
  // GAMIFICATION & ENGAGEMENT
  // ==========================================
  getUserPoints(userId: number): Observable<any> {
    return this.http.get<any>(`${this.baseUrl}/gamification/points/${userId}`);
  }

  getLeaderboard(): Observable<any[]> {
    return this.http.get<any[]>(`${this.baseUrl}/gamification/leaderboard`);
  }

  submitPrediction(prediction: any): Observable<any> {
    return this.http.post<any>(`${this.baseUrl}/gamification/predictions`, prediction);
  }

  getUserPredictions(userId: number): Observable<any[]> {
    return this.http.get<any[]>(`${this.baseUrl}/gamification/predictions/user/${userId}`);
  }

  // BADGES & FIDELITE (B.8)
  getBadgesCatalog(): Observable<any[]> {
    return this.http.get<any[]>(`${this.baseUrl}/gamification/badges`);
  }

  getUserBadges(userId: number): Observable<any[]> {
    return this.http.get<any[]>(`${this.baseUrl}/gamification/badges/user/${userId}`);
  }

  /** ADMIN — gestion complète du catalogue de badges (B.8) */
  getAllBadges(): Observable<any[]> {
    return this.http.get<any[]>(`${this.baseUrl}/gamification/badges/all`);
  }

  createBadge(badge: any): Observable<any> {
    return this.http.post<any>(`${this.baseUrl}/gamification/badges`, badge);
  }

  updateBadge(badgeId: number, badge: any): Observable<any> {
    return this.http.put<any>(`${this.baseUrl}/gamification/badges/${badgeId}`, badge);
  }

  deleteBadge(badgeId: number): Observable<void> {
    return this.http.delete<void>(`${this.baseUrl}/gamification/badges/${badgeId}`);
  }

  // ==========================================
  // MEDIA UPLOAD
  // ==========================================
  uploadMedia(file: File): Observable<any> {
    const formData = new FormData();
    formData.append('file', file);
    return this.http.post<any>(`${this.baseUrl}/content/media/upload`, formData);
  }

  // ==========================================
  // RAPPORTS FINANCIERS (transparence)
  // ==========================================
  getRapportsFinanciers(): Observable<any[]> {
    return this.http.get<any[]>(`${this.baseUrl}/content/rapports-financiers`);
  }

  publierRapportFinancier(rapport: { titre: string; annee: number; description?: string; fileUrl: string; originalName?: string }): Observable<any> {
    return this.http.post<any>(`${this.baseUrl}/content/rapports-financiers`, rapport);
  }

  supprimerRapportFinancier(id: number): Observable<void> {
    return this.http.delete<void>(`${this.baseUrl}/content/rapports-financiers/${id}`);
  }

  getMediaUrl(relativeUrl: string): string {
    if (!relativeUrl) return '';
    if (relativeUrl.startsWith('http')) return relativeUrl;
    return `${environment.mediaBaseUrl}${relativeUrl}`;
  }

  // ==========================================
  // PHASE 5 — APPELS VIDÉO/VOCaux PROGRAMMÉS
  // ==========================================
  /** Appels où je suis organisateur ou participant (mon agenda). */
  getMyCalls(): Observable<any[]> {
    return this.http.get<any[]>(`${this.baseUrl}/sports/calls/mine`);
  }

  /** Programme un appel (ENTRAINEUR / PRESIDENT / ADMIN / JOUEUR — contrôlé serveur). */
  createCall(body: {
    title: string;
    sportType?: string;
    category?: string;
    scheduledAt?: string | null;
    durationMinutes?: number;
    target: 'CATEGORIE_JOUEURS' | 'EQUIPE_JOUEURS' | 'CATEGORIE_EQUIPE' | 'PREMIUM' | 'UTILISATEURS';
    targetUserIds?: number[];
  }): Observable<any> {
    return this.http.post<any>(`${this.baseUrl}/sports/calls`, body);
  }

  /** Jeton média LiveKit (organisateur ou participant uniquement). */
  getCallToken(callId: number): Observable<{ callId: number; roomName: string; token: string; url: string; organizer: boolean }> {
    return this.http.post<any>(`${this.baseUrl}/sports/calls/${callId}/token`, {});
  }

  /** Annule un appel (organisateur seul — contrôlé serveur). */
  cancelCall(callId: number): Observable<any> {
    return this.http.post<any>(`${this.baseUrl}/sports/calls/${callId}/cancel`, {});
  }

  /** Le média LiveKit est-il configuré côté serveur ? */
  getMediaStatus(): Observable<{ configured: boolean }> {
    return this.http.get<{ configured: boolean }>(`${this.baseUrl}/sports/calls/media-status`);
  }

  // ==========================================
  // ESPACE PRÉSIDENT — REÇUS SALAIRES/PRIMES
  // ==========================================
  /** Tous les reçus (vue présidence) ou les miens selon l'endpoint. */
  getSalaryReceipts(): Observable<any[]> {
    return this.http.get<any[]>(`${this.baseUrl}/auth/salary-receipts`);
  }

  getMySalaryReceipts(): Observable<any[]> {
    return this.http.get<any[]>(`${this.baseUrl}/auth/salary-receipts/mine`);
  }

  /** Émission d'un reçu — contrôlée serveur : PRÉSIDENT/ADMIN uniquement. */
  emettreRecu(body: {
    userId: number;
    receiptType: 'SALAIRE' | 'PRIME';
    amount: number;
    periode?: string;
    motif?: string;
  }): Observable<any> {
    return this.http.post<any>(`${this.baseUrl}/auth/salary-receipts`, body);
  }

  /** PDF du reçu généré à la volée (OpenPDF) côté auth-service. */
  getRecuPdf(receiptId: number): Observable<Blob> {
    return this.http.get(`${this.baseUrl}/auth/salary-receipts/${receiptId}/pdf`,
      { responseType: 'blob' });
  }

  // ==========================================
  // B.17 — ACCRÉDITATIONS PRESSE (multi-matchs)
  // ==========================================
  /**
   * Inscription journaliste en multipart (photo + champs texte).
   * Le backend force demandeRole=JOURNALISTE et crée le compte
   * EN_ATTENTE dans la file admin. Aucun token n'est émis.
   */
  registerPress(form: FormData): Observable<any> {
    return this.http.post<any>(`${this.baseUrl}/auth/register-press`, form);
  }

  /** Crée une demande d'accréditation pour un match (journaliste). */
  createPressAccreditation(matchId: number): Observable<any> {
    return this.http.post<any>(`${this.baseUrl}/auth/presse/accreditations`, { matchId });
  }

  /** Liste des demandes du journaliste connecté (toutes, tri desc). */
  getMyPressAccreditations(): Observable<any[]> {
    return this.http.get<any[]>(`${this.baseUrl}/auth/presse/accreditations/me`);
  }

  /** Téléchargement du badge PDF pour une demande VALIDÉE. */
  getPressAccreditationBadge(accreditationId: number): Observable<Blob> {
    return this.http.get(
      `${this.baseUrl}/auth/presse/accreditations/${accreditationId}/badge`,
      { responseType: 'blob' }
    );
  }

  /** Upload / remplacement de la photo de profil (multipart). */
  uploadMyPhoto(file: File): Observable<{ photoUrl: string; mode: 'cloudinary' | 'degraded' }> {
    const form = new FormData();
    form.append('photo', file);
    return this.http.post<{ photoUrl: string; mode: 'cloudinary' | 'degraded' }>(
      `${this.baseUrl}/auth/me/photo`, form);
  }

  // -------- Admin — file des demandes d'accréditation --------

  /** Liste des demandes EN_ATTENTE (vue admin). */
  adminGetPendingPressAccreditations(): Observable<any[]> {
    return this.http.get<any[]>(`${this.baseUrl}/auth/admin/press/accreditations/pending`);
  }

  /** Valide une demande d'accréditation → badge généré. */
  adminValidatePressAccreditation(id: number): Observable<any> {
    return this.http.patch<any>(
      `${this.baseUrl}/auth/admin/press/accreditations/${id}/validate`, {});
  }

  /** Refuse une demande avec motif écrit (obligatoire). */
  adminRefusePressAccreditation(id: number, motif: string): Observable<any> {
    return this.http.patch<any>(
      `${this.baseUrl}/auth/admin/press/accreditations/${id}/refuse`, { motif });
  }
}