import { bootstrapApplication } from '@angular/platform-browser';
import { appConfig } from './app/app.config';
import { AppComponent } from './app/app.component';

//main do frontend papai



bootstrapApplication(AppComponent, appConfig)
  .catch((err) => console.error(err));
