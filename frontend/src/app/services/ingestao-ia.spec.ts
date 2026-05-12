import { TestBed } from '@angular/core/testing';

import { IngestaoIaService } from './ingestao-ia';

describe('IngestaoIaService', () => {
  let service: IngestaoIaService;

  beforeEach(() => {
    TestBed.configureTestingModule({});
    service = TestBed.inject(IngestaoIaService);
  });

  it('should be created', () => {
    expect(service).toBeTruthy();
  });
});
