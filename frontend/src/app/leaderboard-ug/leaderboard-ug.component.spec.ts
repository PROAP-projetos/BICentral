import { ComponentFixture, TestBed } from '@angular/core/testing';
import { of, throwError } from 'rxjs';
import { LeaderboardUgComponent } from './leaderboard-ug.component';
import { RankingDepartamento, RankingService } from '../services/ranking.service';

describe('LeaderboardUgComponent', () => {
  let component: LeaderboardUgComponent;
  let fixture: ComponentFixture<LeaderboardUgComponent>;
  let listarRanking: jasmine.Spy;

  const rankingCompleto: RankingDepartamento[] = Array.from({ length: 8 }, (_, index) => ({
    departamento: `UG-${index + 1}`,
    tipoUnidade: 'UG' as const,
    mediaExecucaoPct: 80 - index,
    qtdAcoes: 10,
    posicaoAtual: index + 1,
    posicaoAnterior: index + 1
  }));

  beforeEach(async () => {
    listarRanking = jasmine.createSpy('listarRanking').and.returnValue(of(rankingCompleto));

    await TestBed.configureTestingModule({
      imports: [LeaderboardUgComponent],
      providers: [{ provide: RankingService, useValue: { listarRanking } }]
    }).compileComponents();

    fixture = TestBed.createComponent(LeaderboardUgComponent);
    component = fixture.componentInstance;
  });

  it('carrega todas as UGs retornadas pelo endpoint, sem recortar a lista', () => {
    fixture.detectChanges();

    expect(listarRanking).toHaveBeenCalledWith('UG');
    expect(component.ugs.length).toBe(8);
    expect(component.ugs.map(ug => ug.id)).toEqual(rankingCompleto.map(ug => ug.departamento));
  });

  it('informa erro quando o ranking não pode ser carregado', () => {
    listarRanking.and.returnValue(throwError(() => new Error('falha')));

    fixture.detectChanges();

    expect(component.erroRanking).toContain('Não foi possível carregar');
    expect(component.ugs).toEqual([]);
  });
});
