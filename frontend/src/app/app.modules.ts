import {BrowserModule} from '@angular/platform-browser';
import {NgModule} from '@angular/core';
import {FormsModule} from '@angular/forms';
import {HttpClient} from '@angular/common/http';

import {AppRoutingModule} from './app-routing.module';
import {AppComponent} from './app.component';
import {HomeComponent} from './home/home.component';
import {CadastroComponent} from './cadastro/cadastro.components'

@NgModule({
  declarations : [ AppComponent, HomeComponent, CadastroComponent],
  imports: [
    BrowserModule,
    AppRoutingModule.
    FormsModule,
    HttpClientModule
  ],
  providers: []
    bootstrap: [AppComponent]
})
export class AppModule{ }

