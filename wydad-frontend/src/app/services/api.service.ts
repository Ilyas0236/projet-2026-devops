import { Injectable } from '@angular/core';
import { HttpClient, HttpParams } from '@angular/common/http';
import { Observable } from 'rxjs';
import { environment } from '../../environments/environment';

@Injectable({
  providedIn: 'root'
})
export class ApiService {
  private baseUrl = environment.apiBaseUrl;

  constructor(private http: HttpClient) {}

  login(email: string, password: string): Observable<any> {
    return this.http.post(`${this.baseUrl}/auth/login`, { email, password });
  }

  getMemberCard(email: string): Observable<any> {
    return this.http.get(`${this.baseUrl}/auth/member-card?email=${email}`);
  }

  getAttestation(email: string): Observable<Blob> {
    return this.http.get(`${this.baseUrl}/auth/attestation?email=${email}`, { responseType: 'blob' });
  }

  upgradeMembership(email: string, newLevel: string): Observable<any> {
    return this.http.post(`${this.baseUrl}/auth/upgrade`, { email, newLevel });
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
  getAllJoueurs(): Observable<any[]> {
    return this.http.get<any[]>(`${this.baseUrl}/sports/players`);
  }

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
  // SONDAGES (B.2)
  // ==========================================
  getActivePolls(): Observable<any[]> {
    return this.http.get<any[]>(`${this.baseUrl}/sports/polls/active`);
  }

  votePoll(pollId: number, optionIndex: number): Observable<any> {
    return this.http.post<any>(`${this.baseUrl}/sports/polls/${pollId}/vote?optionIndex=${optionIndex}`, {});
  }

  createPoll(question: string, options: string[]): Observable<any> {
    return this.http.post<any>(`${this.baseUrl}/sports/polls`, { question, options });
  }

  closePoll(pollId: number): Observable<any> {
    return this.http.post<any>(`${this.baseUrl}/sports/polls/${pollId}/close`, {});
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

  /** Convocation d'un joueur par le staff (B.3.a) — réservée au staff de la catégorie. */
  createConvocation(joueurUserId: number, sessionId: number): Observable<any> {
    return this.http.post<any>(
      `${this.baseUrl}/sports/my-space/staff/convocations?joueurUserId=${joueurUserId}&sessionId=${sessionId}`, {});
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
  sendMessage(toUserId: number, content: string): Observable<any> {
    const params = new HttpParams().set('toUserId', String(toUserId));
    return this.http.post<any>(`${this.baseUrl}/sports/messaging/send`, { content }, { params });
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
  // STATUT MEDICAL (B.6)
  // ==========================================
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

  changeUserRole(userId: number, newRole: string): Observable<any> {
    return this.http.patch(`${this.baseUrl}/auth/admin/users/${userId}/role?newRole=${newRole}`, {}, { responseType: 'text' as 'json' });
  }

  toggleUserActive(userId: number, status: boolean): Observable<any> {
    return this.http.patch(`${this.baseUrl}/auth/admin/users/${userId}/activate?status=${status}`, {}, { responseType: 'text' as 'json' });
  }

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
}