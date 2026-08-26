import { Injectable } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { Observable, BehaviorSubject, tap } from 'rxjs';
import { environment } from '../../environments/environment';

@Injectable({
  providedIn: 'root'
})
export class AuthService {
  private baseUrl = `${environment.apiBaseUrl}/auth`;
  private currentUserSubject = new BehaviorSubject<string | null>(null);
  public currentUser$ = this.currentUserSubject.asObservable();

  constructor(private http: HttpClient) {
    const email = localStorage.getItem('wydad_email');
    if (email) {
      this.currentUserSubject.next(email);
    }
  }

  public get currentUserValue(): string | null {
    return this.currentUserSubject.value;
  }

  login(email: string, password: string): Observable<any> {
    return this.http.post(`${this.baseUrl}/login`, { email, password }).pipe(
      tap((res: any) => {
        localStorage.setItem('wydad_token', res.accessToken);
        localStorage.setItem('wydad_user_id', res.id);
        localStorage.setItem('wydad_email', res.email);
        localStorage.setItem('wydad_first_name', res.firstName);
        localStorage.setItem('wydad_last_name', res.lastName);
        localStorage.setItem('wydad_role', res.role);
        this.currentUserSubject.next(res.email);
      })
    );
  }

  register(userData: any): Observable<any> {
    return this.http.post(`${this.baseUrl}/register`, userData).pipe(
      tap((res: any) => {
        // Demande de statut privilégié : le backend répond 202 sans corps —
        // aucun token n'est émis tant que l'ADMIN n'a pas validé le compte.
        if (!res?.accessToken) return;
        localStorage.setItem('wydad_token', res.accessToken);
        localStorage.setItem('wydad_user_id', res.id);
        localStorage.setItem('wydad_email', res.email);
        localStorage.setItem('wydad_first_name', res.firstName);
        localStorage.setItem('wydad_last_name', res.lastName);
        localStorage.setItem('wydad_role', res.role);
        this.currentUserSubject.next(res.email);
      })
    );
  }

  logout() {
    localStorage.removeItem('wydad_token');
    localStorage.removeItem('wydad_email');
    localStorage.removeItem('wydad_first_name');
    localStorage.removeItem('wydad_last_name');
    localStorage.removeItem('wydad_role');
    localStorage.removeItem('wydad_user_id');
    this.currentUserSubject.next(null);
  }

  getCurrentUserId(): number | null {
    const id = localStorage.getItem('wydad_user_id');
    return id ? parseInt(id, 10) : null;
  }

  getRole(): string | null {
    return localStorage.getItem('wydad_role');
  }

  /**
   * Decode le payload du JWT (sans verification de signature - cote client,
   * la signature est verifiee par la gateway). Retourne null si illisible.
   */
  decodeToken(): any | null {
    const token = localStorage.getItem('wydad_token');
    if (!token) return null;
    try {
      const payload = token.split('.')[1];
      const json = atob(payload.replace(/-/g, '+').replace(/_/g, '/'));
      return JSON.parse(json);
    } catch {
      return null;
    }
  }

  /** Vrai si un token existe ET n'est pas expire. */
  isTokenValid(): boolean {
    const payload = this.decodeToken();
    if (!payload?.exp) return false;
    return payload.exp * 1000 > Date.now();
  }

  /** Role depuis le token JWT (source de verite), fallback localStorage. */
  getTokenRole(): string | null {
    return this.decodeToken()?.role ?? localStorage.getItem('wydad_role');
  }

  getProfile(): Observable<any> {
    return this.http.get(`${this.baseUrl}/me`);
  }

  updateProfile(profileData: any): Observable<any> {
    return this.http.put(`${this.baseUrl}/me`, profileData);
  }

  deleteAccount(): Observable<any> {
    return this.http.delete(`${this.baseUrl}/me`, { responseType: 'text' });
  }

  // NB : upgradeMembership vit dans ApiService (email explicite, utilisée
  // par carte-membre). Ne pas la dupliquer ici.

  getSessions(): Observable<any> {
    return this.http.get(`${this.baseUrl}/sessions`);
  }

  revokeSession(sessionId: string): Observable<any> {
    return this.http.post(`${this.baseUrl}/sessions/revoke`, { sessionId }, { responseType: 'text' });
  }

  revokeAllSessions(): Observable<any> {
    return this.http.post(`${this.baseUrl}/sessions/revoke-all`, {}, { responseType: 'text' });
  }

  uploadKyc(documentType: string, documentNumber: string, filePath: string): Observable<any> {
    const email = this.currentUserValue;
    return this.http.post(`${this.baseUrl}/kyc/upload`, { email, documentType, documentNumber, filePath });
  }

  /**
   * Phase 1 — upload RÉEL du justificatif : le fichier part en multipart
   * vers /kyc/upload-file et est stocké sur Cloudinary côté backend.
   * L'utilisateur courant ne peut déposer que pour SON compte.
   */
  uploadKycFile(file: File, documentType: string, documentNumber: string): Observable<any> {
    const form = new FormData();
    form.append('file', file);
    form.append('documentType', documentType);
    form.append('documentNumber', documentNumber);
    return this.http.post(`${this.baseUrl}/kyc/upload-file`, form);
  }

  verifyKycMock(email: string): Observable<any> {
    return this.http.post(`${this.baseUrl}/kyc/verify?email=${email}`, {});
  }

  /**
   * Dépôt du justificatif juste après l'inscription : les comptes
   * EN_ATTENTE n'ont pas de session — l'authentification passe par le
   * couple email + mot de passe fraîchement créé (pas de JWT).
   */
  uploadKycRegister(file: File, documentType: string, documentNumber: string, email: string, password: string): Observable<any> {
    const form = new FormData();
    form.append('file', file);
    form.append('documentType', documentType);
    form.append('documentNumber', documentNumber);
    form.append('email', email);
    form.append('password', password);
    return this.http.post(`${this.baseUrl}/kyc/register-upload`, form);
  }

  sendOtp(phone: string): Observable<any> {
    return this.http.post(`${this.baseUrl}/otp/send`, { email: this.currentUserValue, phone }, { responseType: 'text' });
  }

  /** Canal de démonstration : renvoie le code OTP si le backend expose app.otp.mock-delivery=true, sinon erreur 404. */
  getMockOtpCode(): Observable<string> {
    return this.http.get(`${this.baseUrl}/otp/mock-code?email=${this.currentUserValue}`, { responseType: 'text' });
  }

  verifyOtp(code: string): Observable<any> {
    return this.http.post(`${this.baseUrl}/otp/verify`, { email: this.currentUserValue, code }, { responseType: 'text' });
  }

  // ==========================================
  // ADMIN ENDPOINTS
  // ==========================================
  getAllUsers(): Observable<any[]> {
    return this.http.get<any[]>(`${this.baseUrl}/admin/users`);
  }

  toggleUserActiveStatus(userId: number, activate: boolean): Observable<any> {
    return this.http.patch(`${this.baseUrl}/admin/users/${userId}/activate?status=${activate}`, {}, { responseType: 'text' });
  }

  changeUserRole(userId: number, role: string): Observable<any> {
    return this.http.patch(`${this.baseUrl}/admin/users/${userId}/role?newRole=${role}`, {}, { responseType: 'text' });
  }
}
