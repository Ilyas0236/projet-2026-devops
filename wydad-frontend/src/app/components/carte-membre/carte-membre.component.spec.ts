import { ComponentFixture, TestBed } from '@angular/core/testing';

import { CarteMembreComponent } from './carte-membre.component';

describe('CarteMembreComponent', () => {
  let component: CarteMembreComponent;
  let fixture: ComponentFixture<CarteMembreComponent>;

  beforeEach(async () => {
    await TestBed.configureTestingModule({
      imports: [CarteMembreComponent]
    })
    .compileComponents();

    fixture = TestBed.createComponent(CarteMembreComponent);
    component = fixture.componentInstance;
    fixture.detectChanges();
  });

  it('should create', () => {
    expect(component).toBeTruthy();
  });
});
