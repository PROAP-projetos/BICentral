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
    expect(component.alturaGrafico).toBe(82 * 40 + 70);
  });

  it('filtra as coordenações pelo campus que vem depois do último hífen', () => {
    const base = { tipoUnidade: 'UA' as const, mediaExecucaoPct: 50, qtdAcoes: 5, qtdAcoesConcluidas: 0, posicaoAnterior: 1 };
    listarRanking.and.returnValue(of([
      { ...base, departamento: 'Coordenação do Curso de Direito - Palmas', posicaoAtual: 1 },
      { ...base, departamento: 'Coordenação do Curso de Direito - Arraias', posicaoAtual: 2 },
      { ...base, departamento: 'Coordenação do Curso de Pedagogia - Palmas', posicaoAtual: 3 }
    ]));
    fixture.detectChanges();
    component.selecionarFiltro('coordenacao');
    expect(component.campi.map(c => c.valor)).toEqual(['Palmas', 'Arraias']);

    component.selecionarCampus('Palmas');
    expect(component.ugsExibidas.length).toBe(2);

    component.selecionarFiltro('campus');
    expect(component.campusAtivo).toBe('todos');
  });

  it('avisa no badge do diagnóstico quando a menor execução está empatada', () => {
    const base = { tipoUnidade: 'UG' as const, qtdAcoes: 5, qtdAcoesConcluidas: 0, posicaoAnterior: 1 };
    listarRanking.and.returnValue(of([
      { ...base, departamento: 'Unidade A - AAA', mediaExecucaoPct: 40, posicaoAtual: 1 },
      { ...base, departamento: 'Unidade B - BBB', mediaExecucaoPct: 0, posicaoAtual: 2 },
      { ...base, departamento: 'Unidade C - CCC', mediaExecucaoPct: 0, posicaoAtual: 3 },
      { ...base, departamento: 'Unidade D - DDD', mediaExecucaoPct: 0, posicaoAtual: 4 }
    ]));
    fixture.detectChanges();
    const diagnostico = component.insightsSugeridos.find(i => i.titulo.startsWith('Diagnóstico'))!;
    expect(diagnostico.badge).toBe('Menor execução (empatada com 2)');
  });

  it('usa o roteiro do resumo executivo no card de resumo', () => {
    fixture.detectChanges();
    const resumo = component.insightsSugeridos.find(i => i.titulo === 'Resumo executivo')!;
    expect(resumo.prompt).toContain('resumo executivo do PAT');
    expect(resumo.prompt).toContain('recomendação');
  });

  it('usa o roteiro de comparação no card de maior x menor', () => {
    fixture.detectChanges();
    const comparativo = component.insightsSugeridos.find(i => i.badge === 'Comparativo')!;
    expect(comparativo.prompt).toContain('tabela lado a lado');
    expect(comparativo.prompt).toContain('recomendação');
  });

  it('escolhe, entre as empatadas na menor execução, a unidade com mais ações', () => {
    const base = { tipoUnidade: 'UG' as const, qtdConcluidas: 0, qtdAcoesConcluidas: 0, posicaoAnterior: 1 };
    listarRanking.and.returnValue(of([
      { ...base, departamento: 'Alta - AAA', mediaExecucaoPct: 90, qtdAcoes: 10, posicaoAtual: 1 },
      { ...base, departamento: 'Pequena - PPP', mediaExecucaoPct: 0, qtdAcoes: 2, posicaoAtual: 2 },
      { ...base, departamento: 'Grande - GGG', mediaExecucaoPct: 0, qtdAcoes: 40, posicaoAtual: 3 },
      { ...base, departamento: 'Zeta - ZZZ', mediaExecucaoPct: 0, qtdAcoes: 5, posicaoAtual: 4 }
    ]));
    fixture.detectChanges();

    const titulos = component.insightsSugeridos.map(i => i.titulo);
    expect(titulos).toContain('Diagnóstico da GGG');
    expect(titulos).toContain('AAA x GGG');
  });

  it('usa o roteiro de ações sem execução no card de risco', () => {
    fixture.detectChanges();
    const risco = component.insightsSugeridos.find(i => i.badge === 'Risco')!;
    expect(risco.prompt).toContain('percentual de zeradas');
    expect(risco.prompt).toContain('recomendação');
  });

  it('monta as análises rápidas com as unidades reais do ranking, sem nomes fixos', () => {
    fixture.detectChanges();

    const titulos = component.insightsSugeridos.map(i => i.titulo);
    expect(titulos).toContain('Diagnóstico da UG-8');
    const diagnostico = component.insightsSugeridos.find(i => i.titulo === 'Diagnóstico da UG-8')!;
    expect(diagnostico.prompt).toContain('diagnóstico da execução do PAT');
    expect(diagnostico.prompt).toContain('tabela');
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

  it('numera a posição dentro da lista exibida, sem contar as coordenações que estão fora dela', () => {
    const base = { qtdAcoes: 10, qtdAcoesConcluidas: 1, posicaoAnterior: null };
    listarRanking.and.returnValue(of([
      { ...base, tipoUnidade: 'UA' as const, departamento: 'Coordenação do curso A - CUP', mediaExecucaoPct: 100, posicaoAtual: 1 },
      { ...base, tipoUnidade: 'UA' as const, departamento: 'Coordenação do curso B - CUP', mediaExecucaoPct: 99, posicaoAtual: 2 },
      { ...base, tipoUnidade: 'UG' as const, departamento: 'Campus Universitário de Arraias - CUAR', mediaExecucaoPct: 96, posicaoAtual: 3 },
      { ...base, tipoUnidade: 'UG' as const, departamento: 'Gabinete do Reitor', mediaExecucaoPct: 80, posicaoAtual: 4 }
    ]));
    fixture.detectChanges();

    expect(component.ugsExibidas.map(u => u.sigla)).toEqual(['CUAR', 'Gabinete do Reitor']);
    expect(component.ugsExibidas.map(u => u.posicao)).toEqual([1, 2]);
  });

  it('pinta os textos do gráfico de barras com as cores do tema, legíveis no claro e no escuro', () => {
    fixture.nativeElement.style.setProperty('--text-primary', '#111111');
    fixture.nativeElement.style.setProperty('--text-secondary', '#555555');
    fixture.detectChanges();

    const opcoes = component.echartsOptions;
    expect(opcoes.yAxis.axisLabel.color).toBe('#111111');
    expect(opcoes.xAxis.axisLabel.color).toBe('#555555');
    expect(opcoes.series[0].label.color).toBe('#111111');

    fixture.nativeElement.style.setProperty('--text-primary', '#f0f0f0');
    component.selecionarFiltro('todos');
    expect(component.echartsOptions.yAxis.axisLabel.color).toBe('#f0f0f0');
  });
});
