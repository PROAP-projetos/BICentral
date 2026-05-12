import { ComponentFixture, TestBed } from '@angular/core/testing';

import { IngestaoIaComponent } from './ingestao-ia';

describe('IngestaoIaComponent', () => {
  let component: IngestaoIaComponent;
  let fixture: ComponentFixture<IngestaoIaComponent>;

  beforeEach(async () => {
    await TestBed.configureTestingModule({
      imports: [IngestaoIaComponent]
    })
    .compileComponents();

    fixture = TestBed.createComponent(IngestaoIaComponent);
    component = fixture.componentInstance;
    fixture.detectChanges();
  });

  it('should create', () => {
    expect(component).toBeTruthy();
  });
});
