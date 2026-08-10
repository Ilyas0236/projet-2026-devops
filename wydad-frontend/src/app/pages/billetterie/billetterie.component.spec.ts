import { ComponentFixture, TestBed } from '@angular/core/testing';

import { BilletterieComponent } from './billetterie.component';

describe('BilletterieComponent', () => {
  let component: BilletterieComponent;
  let fixture: ComponentFixture<BilletterieComponent>;

  beforeEach(async () => {
    await TestBed.configureTestingModule({
      imports: [BilletterieComponent]
    })
    .compileComponents();

    fixture = TestBed.createComponent(BilletterieComponent);
    component = fixture.componentInstance;
    fixture.detectChanges();
  });

  it('should create', () => {
    expect(component).toBeTruthy();
  });
});
