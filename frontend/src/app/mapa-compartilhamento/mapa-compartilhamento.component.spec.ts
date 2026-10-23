import { ComponentFixture, TestBed } from '@angular/core/testing';
import { of } from 'rxjs';
import { MapaCompartilhamentoComponent } from './mapa-compartilhamento.component';
import { RankingService } from '../services/ranking.service';

describe('MapaCompartilhamentoComponent', () => {
  let fixture: ComponentFixture<MapaCompartilhamentoComponent>;
  let component: MapaCompartilhamentoComponent;

  beforeEach(async () => {
    const rankingService = {
      compartilhamento: () => of([
        { origem: 'A - AAA', destino: 'B - BBB', qtdAcoes: 4, diferencaMediaPct: 55 },
        { origem: 'A - AAA', destino: 'C - CCC', qtdAcoes: 2, diferencaMediaPct: 10 }
      ]),
      listarRanking: () => of([])
    };

    await TestBed.configureTestingModule({
      imports: [MapaCompartilhamentoComponent],
      providers: [{ provide: RankingService, useValue: rankingService }]
    }).compileComponents();

    fixture = TestBed.createComponent(MapaCompartilhamentoComponent);
    component = fixture.componentInstance;
  });

  it('monta o grafo sem animar o layout, pra os pontos não ficarem se mexendo', () => {
    fixture.detectChanges();

    const serie = component.opcoes.series[0];
    expect(serie.force.layoutAnimation).toBeFalse();
    expect(serie.data.length).toBe(3);
    expect(serie.links.length).toBe(2);
  });

  it('destaca só a ligação com diferença de 40 pontos ou mais', () => {
    fixture.detectChanges();

    const [forte, fraca] = component.opcoes.series[0].links;
    expect(forte.lineStyle.color).toBe('#e5484d');
    expect(fraca.lineStyle.color).not.toBe('#e5484d');
  });
});
