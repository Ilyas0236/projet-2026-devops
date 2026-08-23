import { Injectable } from '@angular/core';
import { HttpClient } from '@angular/common/http';
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

  // ==========================================
  // MEDIA UPLOAD
  // ==========================================
  uploadMedia(file: File): Observable<any> {
    const formData = new FormData();
    formData.append('file', file);
    return this.http.post<any>(`${this.baseUrl}/content/media/upload`, formData);
  }

  getMediaUrl(relativeUrl: string): string {
    if (!relativeUrl) return '';
    if (relativeUrl.startsWith('http')) return relativeUrl;
    return `${environment.mediaBaseUrl}${relativeUrl}`;
  }
}