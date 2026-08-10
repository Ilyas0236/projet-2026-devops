import { ComponentFixture, TestBed } from '@angular/core/testing';

import { DonComponent } from './don.component';

describe('DonComponent', () => {
  let component: DonComponent;
  let fixture: ComponentFixture<DonComponent>;

  beforeEach(async () => {
    await TestBed.configureTestingModule({
      imports: [DonComponent]
    })
    .compileComponents();

    fixture = TestBed.createComponent(DonComponent);
    component = fixture.componentInstance;
    fixture.detectChanges();
  });

  it('should create', () => {
    expect(component).toBeTruthy();
  });
});
