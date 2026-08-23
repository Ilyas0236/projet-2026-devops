import { Component, OnInit, inject } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormBuilder, FormGroup, ReactiveFormsModule, Validators } from '@angular/forms';
import { forkJoin } from 'rxjs';
import { Router, RouterModule } from '@angular/router';
import { ApiService } from '../../../services/api.service';
import { AuthService } from '../../../services/auth.service';
import { ToastService } from '../../../services/toast.service';

@Component({
  selector: 'app-inscription-academie',
  standalone: true,
  imports: [CommonModule, ReactiveFormsModule, RouterModule],
  templateUrl: './inscription.component.html',
})
export class InscriptionAcademieComponent implements OnInit {
  currentStep = 1;
  totalSteps = 4;
  inscriptionForm!: FormGroup;
  /** Saison en cours depuis la configuration club (source de verite ADMIN). */
  saison = '';

  api = inject(ApiService);
  auth = inject(AuthService);
  router = inject(Router);
  fb = inject(FormBuilder);
  toast = inject(ToastService);

  isSubmitting = false;
  success = false;

  /** Pièces justificatives sélectionnées à l'étape Documents (0-BIS.6). */
  documents: { [key: string]: File | null } = {
    BIRTH_CERTIFICATE: null,
    MEDICAL_CERTIFICATE: null,
    PHOTO: null
  };

  /** Erreur de validation de fichier affichée sous un bloc d'upload. */
  documentError = '';

  onDocumentSelected(docType: string, event: Event): void {
    const input = event.target as HTMLInputElement;
    const file = input.files?.[0] ?? null;
    this.documentError = '';
    if (file) {
      const allowedTypes = ['image/jpeg', 'image/png', 'image/webp', 'application/pdf'];
      if (!allowedTypes.includes(file.type)) {
        this.documentError = 'Format accepté : JPEG, PNG, WebP ou PDF.';
        input.value = '';
        this.documents[docType] = null;
        return;
      }
      if (file.size > 5 * 1024 * 1024) {
        this.documentError = 'Fichier trop volumineux (5 Mo maximum).';
        input.value = '';
        this.documents[docType] = null;
        return;
      }
    }
    this.documents[docType] = file;
  }

  ngOnInit() {
    this.api.getClubSetting('club_info').subscribe({
      next: (info) => {
        this.saison = info?.saison || '';
      },
      error: () => {
        this.saison = '';
      }
    });

    this.inscriptionForm = this.fb.group({
      childInfo: this.fb.group({
        childFullName: ['', Validators.required],
        childBirthDate: ['', Validators.required],
        sportType: ['FOOTBALL', Validators.required],
        level: ['Débutant', Validators.required],
      }),
      medical: this.fb.group({
        bloodType: [''],
        allergies: [''],
        medicalHistory: [''],
        emergencyContactName: ['', Validators.required],
        emergencyContactPhone: ['', Validators.required]
      }),
      documents: this.fb.group({
        // Mocking files for now (MVP)
        birthCertificate: [''],
        medicalCertificate: [''],
        photo: ['']
      }),
      payment: this.fb.group({
        paymentMethod: ['ECASH', Validators.required]
      })
    });
  }

  nextStep() {
    if (this.currentStep < this.totalSteps) {
      // Basic validation check could be added here per step
      this.currentStep++;
    }
  }

  prevStep() {
    if (this.currentStep > 1) {
      this.currentStep--;
    }
  }

  submitInscription() {
    if (this.inscriptionForm.invalid) return;

    this.isSubmitting = true;
    const parentId = this.auth.getCurrentUserId();
    
    if (!parentId) {
      this.router.navigate(['/login']);
      return;
    }

    const formData = this.inscriptionForm.value;
    const payload = {
      parentUserId: parentId,
      childFullName: formData.childInfo.childFullName,
      childBirthDate: formData.childInfo.childBirthDate,
      sportType: formData.childInfo.sportType,
      level: formData.childInfo.level,
      bloodType: formData.medical.bloodType,
      allergies: formData.medical.allergies,
      medicalHistory: formData.medical.medicalHistory,
      emergencyContactName: formData.medical.emergencyContactName,
      emergencyContactPhone: formData.medical.emergencyContactPhone,
      active: false // En attente de validation / paiement (for this MVP, let's say true to see it active or false and admin validates)
    };

    // If ECASH, we could call debit endpoint. For MVP, we just register the child.
    this.api.registerAcademyChild(payload).subscribe({
      next: (res) => {
        // 0-BIS.6 : transmission réelle des pièces justificatives au backend
        const uploads = Object.entries(this.documents)
          .filter(([, file]) => !!file)
          .map(([docType, file]) =>
            this.api.uploadAcademyDocument(res.id, docType, file as File));
        if (uploads.length > 0) {
          forkJoin(uploads).subscribe({
            next: () => {
              this.isSubmitting = false;
              this.success = true;
              setTimeout(() => this.router.navigate(['/academie/mes-enfants']), 3000);
            },
            error: (uploadErr) => {
              this.isSubmitting = false;
              // Le dossier existe : on informe sans bloquer la navigation
              this.toast.error(uploadErr.error?.message
                || 'Dossier créé mais échec d\'envoi d\'une pièce justificative.');
              setTimeout(() => this.router.navigate(['/academie/mes-enfants']), 3000);
            }
          });
        } else {
          this.isSubmitting = false;
          this.success = true;
          setTimeout(() => {
            this.router.navigate(['/academie/mes-enfants']);
          }, 3000);
        }
      },
      error: (err) => {
        console.error(err);
        this.isSubmitting = false;
        this.toast.error(err.error?.message || 'Erreur lors de l\'inscription.');
      }
    });
  }
}
