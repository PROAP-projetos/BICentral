import { TestBed } from '@angular/core/testing';
import { provideHttpClient } from '@angular/common/http';
import { provideHttpClientTesting } from '@angular/common/http/testing';

import { IngestaoIaService } from './ingestao-ia';

describe('IngestaoIaService', () => {
  let service: IngestaoIaService;

  beforeEach(() => {
    TestBed.configureTestingModule({
      providers: [provideHttpClient(), provideHttpClientTesting()]
    });
    service = TestBed.inject(IngestaoIaService);
  });

  it('should be created', () => {
    expect(service).toBeTruthy();
  });
});
