import { ComponentFixture, TestBed } from '@angular/core/testing';

import { IngestaoIa } from './ingestao-ia';

describe('IngestaoIa', () => {
  let component: IngestaoIa;
  let fixture: ComponentFixture<IngestaoIa>;

  beforeEach(async () => {
    await TestBed.configureTestingModule({
      imports: [IngestaoIa]
    })
    .compileComponents();

    fixture = TestBed.createComponent(IngestaoIa);
    component = fixture.componentInstance;
    fixture.detectChanges();
  });

  it('should create', () => {
    expect(component).toBeTruthy();
  });
});
