// Point d'entrée des tests unitaires Angular (Karma + Jasmine).
// Chargé avant les specs : initialise l'environnement de test.
import 'zone.js/testing';
import { getTestBed } from '@angular/core/testing';
import {
  BrowserDynamicTestingModule,
  platformBrowserDynamicTesting,
} from '@angular/platform-browser-dynamic/testing';

// Bootstrap de l'environnement de test une seule fois.
getTestBed().initTestEnvironment(
  BrowserDynamicTestingModule,
  platformBrowserDynamicTesting(),
);
