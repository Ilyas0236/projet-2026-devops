import { Injectable } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { Observable, BehaviorSubject, tap } from 'rxjs';

@Injectable({
  providedIn: 'root'
})
export class AuthService {
  private baseUrl = 'http://localhost:8080/api/auth';
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
        localStorage.setItem('wydad_token', res.accessToken);
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
    this.currentUserSubject.next(null);
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

  upgradeMembership(newLevel: string): Observable<any> {
    const email = this.currentUserValue;
    return this.http.post(`${this.baseUrl}/upgrade`, { email, newLevel });
  }

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

  verifyKycMock(email: string): Observable<any> {
    return this.http.post(`${this.baseUrl}/kyc/verify?email=${email}`, {});
  }

  sendOtp(phone: string): Observable<any> {
    return this.http.post(`${this.baseUrl}/otp/send`, { email: this.currentUserValue, phone }, { responseType: 'text' });
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
