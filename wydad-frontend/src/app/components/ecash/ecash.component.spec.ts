import { ComponentFixture, TestBed } from '@angular/core/testing';

import { EcashComponent } from './ecash.component';

describe('EcashComponent', () => {
  let component: EcashComponent;
  let fixture: ComponentFixture<EcashComponent>;

  beforeEach(async () => {
    await TestBed.configureTestingModule({
      imports: [EcashComponent]
    })
    .compileComponents();

    fixture = TestBed.createComponent(EcashComponent);
    component = fixture.componentInstance;
    fixture.detectChanges();
  });

  it('should create', () => {
    expect(component).toBeTruthy();
  });
});
