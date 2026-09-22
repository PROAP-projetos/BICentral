import { TestBed } from '@angular/core/testing';
import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { RankingDepartamento, RankingService } from './ranking.service';

describe('RankingService', () => {
  let service: RankingService;
  let httpTesting: HttpTestingController;

  beforeEach(() => {
    TestBed.configureTestingModule({
      providers: [provideHttpClient(), provideHttpClientTesting()]
    });
    service = TestBed.inject(RankingService);
    httpTesting = TestBed.inject(HttpTestingController);
  });

  afterEach(() => httpTesting.verify());

  it('busca todas as UGs sem enviar limite ao endpoint', () => {
    const resposta: RankingDepartamento[] = [{
      departamento: 'PROAP - PROAP',
      tipoUnidade: 'UG',
      mediaExecucaoPct: 72.5,
      qtdAcoes: 8,
      posicaoAtual: 1,
      posicaoAnterior: null
    }];

    service.listarRanking('UG').subscribe(resultado => expect(resultado).toEqual(resposta));

    const req = httpTesting.expectOne('/api/ranking?tipoUnidade=UG');
    expect(req.request.method).toBe('GET');
    req.flush(resposta);
  });
});
