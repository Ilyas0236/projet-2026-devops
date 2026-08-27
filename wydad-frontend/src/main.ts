import { bootstrapApplication } from '@angular/platform-browser';
import { registerLocaleData } from '@angular/common';
import localeFr from '@angular/common/locales/fr';
import { appConfig } from './app/app.config';
import { AppComponent } from './app/app.component';

// B.28 (page billetterie visiteur, 27/08) : la date est formatée avec
// la locale 'fr' (`{{ event.eventDate | date:'fullDate':'':'fr' }}`).
// Sans cet enregistrement, Angular jette MissingLocaleDataError au
// rendu du hero, ce qui effondre tout le bloc : plus de date, plus
// d'heure, plus de venue, plus de liste de sections — la page paraît
// "vide" alors qu'elle est juste plantée. L'enregistrement est global
// (donc bénéficie aussi aux autres pages qui utiliseraient 'fr' plus tard).
registerLocaleData(localeFr);

bootstrapApplication(AppComponent, appConfig)
  .catch((err) => console.error(err));
