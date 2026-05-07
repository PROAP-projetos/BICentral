import { TestBed } from '@angular/core/testing';

import { IngestaoIa } from './ingestao-ia';

describe('IngestaoIa', () => {
  let service: IngestaoIa;

  beforeEach(() => {
    TestBed.configureTestingModule({});
    service = TestBed.inject(IngestaoIa);
  });

  it('should be created', () => {
    expect(service).toBeTruthy();
  });
});
