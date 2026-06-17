import { ComponentFixture, TestBed } from '@angular/core/testing';

import { GraficoIa } from './grafico-ia';

describe('GraficoIa', () => {
  let component: GraficoIa;
  let fixture: ComponentFixture<GraficoIa>;

  beforeEach(async () => {
    await TestBed.configureTestingModule({
      imports: [GraficoIa]
    })
    .compileComponents();

    fixture = TestBed.createComponent(GraficoIa);
    component = fixture.componentInstance;
    fixture.detectChanges();
  });

  it('should create', () => {
    expect(component).toBeTruthy();
  });
});
