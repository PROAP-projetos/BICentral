import { ComponentFixture, TestBed } from '@angular/core/testing';

import { GraficoIaComponent } from './grafico-ia';

describe('GraficoIaComponent', () => {
  let component: GraficoIaComponent;
  let fixture: ComponentFixture<GraficoIaComponent>;

  beforeEach(async () => {
    await TestBed.configureTestingModule({
      imports: [GraficoIaComponent]
    })
    .compileComponents();

    fixture = TestBed.createComponent(GraficoIaComponent);
    component = fixture.componentInstance;
    fixture.detectChanges();
  });

  it('should create', () => {
    expect(component).toBeTruthy();
  });
});
