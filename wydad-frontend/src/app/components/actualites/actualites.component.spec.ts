import { ComponentFixture, TestBed } from '@angular/core/testing';

import { ActualitesComponent } from './actualites.component';

describe('ActualitesComponent', () => {
  let component: ActualitesComponent;
  let fixture: ComponentFixture<ActualitesComponent>;

  beforeEach(async () => {
    await TestBed.configureTestingModule({
      imports: [ActualitesComponent]
    })
    .compileComponents();

    fixture = TestBed.createComponent(ActualitesComponent);
    component = fixture.componentInstance;
    fixture.detectChanges();
  });

  it('should create', () => {
    expect(component).toBeTruthy();
  });
});
