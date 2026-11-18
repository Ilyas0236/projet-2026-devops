# Documentation

Technical documentation for the Wydad Digital platform.

## Contents

| Folder | Description |
|--------|-------------|
| [`architecture/`](architecture/) | System architecture diagrams (microservices, Docker, CI/CD, Azure) |
| [`uml/`](uml/) | UML diagrams: use case, class, sequence, activity, state, collaboration |
| [`screenshots/`](screenshots/) | UI wireframes and screen mockups for all major views |
| [`ARCHITECTURE.md`](ARCHITECTURE.md) | Architecture overview and design decisions |

## UML Diagrams Index

### Use Case Diagrams
| Diagram | Actors |
|---------|--------|
| [Global Supporter View](uml/uc-01a-vue-globale-supporter.png) | Supporter, Public, Parent |
| [Global Club View](uml/uc-01b-vue-globale-club.png) | Player, Coach, Admin, President |
| [Public Access](uml/uc-02-supporter-public.png) | Public visitor |
| [Purchases](uml/uc-03-supporter-achat.png) | Supporter (tickets, shop) |
| [Academy](uml/uc-04-supporter-academie.png) | Parent, Player |
| [Gamification](uml/uc-05-supporter-gamification.png) | Supporter |
| [Voting](uml/uc-06-supporter-votes.png) | Supporter, Club member |
| [Player](uml/uc-07-club-joueur.png) | Player |
| [Staff & Coach](uml/uc-08-club-staff-entraineur.png) | Coach, Staff |
| [President](uml/uc-09-club-president.png) | President |
| [Messaging](uml/uc-10-club-messagerie.png) | All authenticated users |
| [Admin](uml/uc-11-club-admin.png) | Administrator |
| [Journalist](uml/uc-12-club-journaliste.png) | Journalist |

### Class Diagrams
| Diagram | Service |
|---------|---------|
| [Global Overview](uml/classes-macro.png) | All services (macro view) |
| [Auth](uml/classes-auth.png) | auth-service |
| [Ticketing](uml/classes-billetterie.png) | ticket-service |
| [Shop](uml/classes-shop.png) | shop-service |
| [Sports](uml/classes-sports.png) | sports-service |
| [Gamification](uml/classes-gamification.png) | gamification-service |
| [Elections](uml/classes-election.png) | election-service |
| [Messaging](uml/classes-messagerie.png) | communication-service |
| [Academy](uml/classes-academie.png) | content-service (academy) |

### Sequence Diagrams
| Diagram | Workflow |
|---------|----------|
| [Ticket Purchase (Payment)](uml/sequence-achat-billet-cas1-paiement.png) | Full payment flow |
| [Ticket Purchase (Visitor)](uml/sequence-achat-billet-cas1-visiteur.png) | Guest checkout |
| [Parent Ticket Purchase](uml/sequence-achat-billet-cas2-parent.png) | Parent buying for child |
| [Subscription](uml/sequence-abonnement.png) | Season subscription |
| [Vote](uml/sequence-vote-election.png) | Election voting flow |
| [Vote Error Handling](uml/sequence-vote-election-failures.png) | Failure scenarios |
| [Press Accreditation](uml/sequence-accreditation-presse.png) | Journalist workflow |
| [Match Convocations](uml/sequence-convocations.png) | Player convocation |
| [Match Sheet](uml/sequence-feuille-match.png) | Coach match sheet |
| [Team Chat](uml/sequence-team-chat.png) | Real-time messaging |
| [Admin Validation](uml/sequence-admin-validation-comptes.png) | Account validation |
| [Parent Academy](uml/sequence-parent-academie.png) | Academy enrollment |
| [President Receipts](uml/sequence-president-receipts.png) | Financial overview |
