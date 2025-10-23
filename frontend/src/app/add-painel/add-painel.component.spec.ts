import { ComponentFixture, TestBed } from '@angular/core/testing';

import { AddPainelComponent } from './add-painel.component';

describe('AddPainelComponent', () => {
  let component: AddPainelComponent;
  let fixture: ComponentFixture<AddPainelComponent>;

  beforeEach(async () => {
    await TestBed.configureTestingModule({
      imports: [AddPainelComponent]
    })
    .compileComponents();

    fixture = TestBed.createComponent(AddPainelComponent);
    component = fixture.componentInstance;
    fixture.detectChanges();
  });

  it('should create', () => {
    expect(component).toBeTruthy();
  });
});
