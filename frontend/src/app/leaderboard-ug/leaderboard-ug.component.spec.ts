import { ComponentFixture, TestBed } from '@angular/core/testing';
import { of, throwError } from 'rxjs';
import { LeaderboardUgComponent } from './leaderboard-ug.component';
import { RankingDepartamento, RankingService } from '../services/ranking.service';

describe('LeaderboardUgComponent', () => {
  let component: LeaderboardUgComponent;
  let fixture: ComponentFixture<LeaderboardUgComponent>;
  let listarRanking: jasmine.Spy;
  let resumo: jasmine.Spy;
  let atualizacao: jasmine.Spy;

  const rankingCompleto: RankingDepartamento[] = Array.from({ length: 8 }, (_, index) => ({
    departamento: `UG-${index + 1}`,
    tipoUnidade: 'UG' as const,
    mediaExecucaoPct: 80 - index,
    qtdAcoes: 10,
    qtdAcoesConcluidas: 4,
    posicaoAtual: index + 1,
    posicaoAnterior: index + 1
  }));

  beforeEach(async () => {
    listarRanking = jasmine.createSpy('listarRanking').and.returnValue(of(rankingCompleto));
    atualizacao = jasmine.createSpy('atualizacao').and.returnValue(of({ atualizadoEm: '08/10/2026 09:15' }));
    resumo = jasmine.createSpy('resumo').and.returnValue(of({ totalAcoes: 50, acoesConcluidas: 12 }));

    await TestBed.configureTestingModule({
      imports: [LeaderboardUgComponent],
      providers: [{ provide: RankingService, useValue: { listarRanking, resumo, atualizacao } }]
    }).compileComponents();

    fixture = TestBed.createComponent(LeaderboardUgComponent);
    component = fixture.componentInstance;
  });

  it('carrega todas as UGs retornadas pelo endpoint, sem recortar a lista', () => {
    fixture.detectChanges();

    expect(listarRanking).toHaveBeenCalledWith();
    expect(component.ugs.length).toBe(8);
    expect(component.ugs.map(ug => ug.id)).toEqual(rankingCompleto.map(ug => ug.departamento));
  });

  it('usa os totais únicos do backend, sem somar a mesma ação em várias UGs', () => {
    fixture.detectChanges();

    expect(resumo).toHaveBeenCalledWith();
    expect(component.totalAcoesGlobal).toBe(50);
    expect(component.acoesConcluidasGlobal).toBe(12);
    expect(component.percentualAcoesConcluidas).toBe(24);
    expect(component.ugs[0].acoesConcluidas).toBe(4);
  });

  it('informa erro quando o ranking não pode ser carregado', () => {
    listarRanking.and.returnValue(throwError(() => new Error('falha')));

    fixture.detectChanges();

    expect(component.erroRanking).toContain('Não foi possível carregar');
    expect(component.ugs).toEqual([]);
  });

  it('filtra por tipo de unidade e recalcula a posição dentro do grupo', () => {
    const base = { tipoUnidade: 'UG' as const, qtdAcoes: 10, qtdAcoesConcluidas: 1 };
    listarRanking.and.returnValue(of([
      { ...base, departamento: 'Pró-Reitoria de Graduação - PROGRAD', mediaExecucaoPct: 90, posicaoAtual: 1, posicaoAnterior: 1 },
      { ...base, departamento: 'Campus Universitário de Palmas - CUP', mediaExecucaoPct: 80, posicaoAtual: 2, posicaoAnterior: 3 },
      { ...base, departamento: 'Gabinete do Reitor', mediaExecucaoPct: 70, posicaoAtual: 3, posicaoAnterior: 2 },
      { ...base, departamento: 'Campus Universitário de Gurupi - CAUG', mediaExecucaoPct: 60, posicaoAtual: 4, posicaoAnterior: 4 },
      { ...base, tipoUnidade: 'UA' as const, departamento: 'Coordenação do curso de Pedagogia - CUP', mediaExecucaoPct: 50, posicaoAtual: 5, posicaoAnterior: 5 }
    ]));
    fixture.detectChanges();

    expect(component.contagemFiltro('todos')).toBe(4);
    expect(component.ugsExibidas.length).toBe(4);
    expect(component.contagemFiltro('campus')).toBe(2);
    expect(component.contagemFiltro('pro-reitoria')).toBe(1);
    expect(component.contagemFiltro('outros')).toBe(1);
    expect(component.contagemFiltro('coordenacao')).toBe(1);

    component.selecionarFiltro('campus');

    expect(component.ugsExibidas.map(u => u.sigla)).toEqual(['CUP', 'CAUG']);
    expect(component.ugsExibidas.map(u => u.posicao)).toEqual([1, 2]);
    expect(component.ugsExibidas[0].variacaoPosicao).toBe(0);

    component.selecionarFiltro('coordenacao');
    expect(component.ugsExibidas.map(u => u.posicao)).toEqual([1]);

    component.selecionarFiltro('todos');
    expect(component.ugsExibidas.length).toBe(4);
    expect(component.ugsExibidas.some(u => u.nome.startsWith('Coordenação'))).toBeFalse();
  });

  it('mostra o nome completo do departamento no tooltip do gráfico de barras', () => {
    fixture.detectChanges();

    const formatter = component.echartsOptions.tooltip.formatter;
    const ultima = rankingCompleto[rankingCompleto.length - 1];
    const html = formatter({ dataIndex: 0, name: 'sigla', value: 73 });

    expect(html).toContain(ultima.departamento);
    expect(html).toContain('73% de execução');
  });

  it('aumenta a altura do gráfico de barras conforme o número de unidades', () => {
    fixture.detectChanges();
    expect(component.alturaGrafico).toBe(380);

    const muitas = Array.from({ length: 82 }, (_, i) => ({
      departamento: `Coordenação do curso ${i + 1}`,
      tipoUnidade: 'UA' as const,
      mediaExecucaoPct: 50,
      qtdAcoes: 5,
      qtdAcoesConcluidas: 0,
      posicaoAtual: i + 1,
      posicaoAnterior: i + 1
    }));
    listarRanking.and.returnValue(of(muitas));
    component.carregarRanking();
    component.selecionarFiltro('coordenacao');

    expect(component.ugsExibidas.length).toBe(82);
    expect(component.alturaGrafico).toBe(82 * 30 + 70);
  });

  it('monta as análises rápidas com as unidades reais do ranking, sem nomes fixos', () => {
    fixture.detectChanges();

    const titulos = component.insightsSugeridos.map(i => i.titulo);
    expect(titulos).toContain('Diagnóstico da UG-8');
    expect(titulos).toContain('UG-1 x UG-8');
    expect(JSON.stringify(component.insightsSugeridos)).not.toContain('DTI');
  });

  it('emite a pergunta ao clicar numa unidade, pra o agente responder no chat', () => {
    fixture.detectChanges();
    const emitidas: string[] = [];
    component.selecionarUg.subscribe((pergunta: string) => emitidas.push(pergunta));

    component.clicarUg(component.ugs[0]);
    component.clicarInsight(component.insightsSugeridos[0]);

    expect(emitidas.length).toBe(2);
    expect(emitidas[0]).toContain('UG-1');
    expect(emitidas[1]).toContain('UG-8');
  });

  it('mostra a data da última atualização e marca unidade sem posição anterior', () => {
    listarRanking.and.returnValue(of([
      { departamento: 'Nova - NOV', tipoUnidade: 'UG' as const, mediaExecucaoPct: 10, qtdAcoes: 2, qtdAcoesConcluidas: 0, posicaoAtual: 1, posicaoAnterior: null }
    ]));
    fixture.detectChanges();

    expect(component.atualizadoEm).toBe('08/10/2026 09:15');
    expect(component.ugs[0].semComparacao).toBeTrue();
    expect(fixture.nativeElement.querySelector('.subtitle').textContent).toContain('08/10/2026 09:15');
  });
});
