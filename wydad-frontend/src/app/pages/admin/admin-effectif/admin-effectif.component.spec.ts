import { ComponentFixture, TestBed } from '@angular/core/testing';

import { AdminEffectifComponent } from './admin-effectif.component';

describe('AdminEffectifComponent', () => {
  let component: AdminEffectifComponent;
  let fixture: ComponentFixture<AdminEffectifComponent>;

  beforeEach(async () => {
    await TestBed.configureTestingModule({
      imports: [AdminEffectifComponent]
    })
    .compileComponents();

    fixture = TestBed.createComponent(AdminEffectifComponent);
    component = fixture.componentInstance;
    fixture.detectChanges();
  });

  it('should create', () => {
    expect(component).toBeTruthy();
  });
});
