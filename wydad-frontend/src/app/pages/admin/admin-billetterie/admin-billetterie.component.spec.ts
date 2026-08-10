import { ComponentFixture, TestBed } from '@angular/core/testing';

import { AdminBilletterieComponent } from './admin-billetterie.component';

describe('AdminBilletterieComponent', () => {
  let component: AdminBilletterieComponent;
  let fixture: ComponentFixture<AdminBilletterieComponent>;

  beforeEach(async () => {
    await TestBed.configureTestingModule({
      imports: [AdminBilletterieComponent]
    })
    .compileComponents();

    fixture = TestBed.createComponent(AdminBilletterieComponent);
    component = fixture.componentInstance;
    fixture.detectChanges();
  });

  it('should create', () => {
    expect(component).toBeTruthy();
  });
});
