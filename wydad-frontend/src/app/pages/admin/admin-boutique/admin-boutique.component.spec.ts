import { ComponentFixture, TestBed } from '@angular/core/testing';

import { AdminBoutiqueComponent } from './admin-boutique.component';

describe('AdminBoutiqueComponent', () => {
  let component: AdminBoutiqueComponent;
  let fixture: ComponentFixture<AdminBoutiqueComponent>;

  beforeEach(async () => {
    await TestBed.configureTestingModule({
      imports: [AdminBoutiqueComponent]
    })
    .compileComponents();

    fixture = TestBed.createComponent(AdminBoutiqueComponent);
    component = fixture.componentInstance;
    fixture.detectChanges();
  });

  it('should create', () => {
    expect(component).toBeTruthy();
  });
});
