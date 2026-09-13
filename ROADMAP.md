# 🗺️ ONewPipe — Feuille de route (Roadmap)

> Vision : **un seul endroit** pour regarder ce qu'on veut — vidéos YouTube,
> films, séries, podcasts — sans switcher entre 10 plateformes.
> ONewPipe = fork NewPipe + apps mobiles/PC + système de compte + serveur local + intégrations.

**Ordre des phases (validé avec l'utilisateur) :**
1. 🟢 **Phase 1 — Apps mobile (Android) + PC** *(en cours)*
2. 🔵 **Phase 2 — Serveur de compte** (recommandations, likes, enregistrements, playlists, sync multi-appareils) *(bases posées : compte + JWT + sync positions de lecture)*
3. 🟣 **Phase 3 — Serveur YouTube local** (un vrai site YouTube accessible via navigateur) *(base posée : web UI servie par le serveur)*
4. ⚫ **Phase 4 — MCP + plugins** (connexion MCP pour modèles d'IA, plugins Home Assistant, Jellyfin, etc.)

---

## 🟢 PHASE 1 — Applications mobile + PC

### 1.1 Socle technique (à faire en premier — tout repose dessus)
- [x] Clone + build Android APK (`app/build/outputs/apk/debug/app-debug.apk`)
- [x] Clone + build app PC (MSI : `desktopApp/build/compose/binaries/main/msi/`)
- [x] Corriger le build Android cassé (package namespace, tri `libs.versions.toml`, bugs compilateur Kotlin 2.3 sur `const val`)
- [x] **Mettre à jour NewPipeExtractor** vers la dernière version upstream (`4de221b`, poToken + correctifs YouTube)
- [x] **Merger le dernier code upstream NewPipe** (base actuelle : NewPipe 0.29.1 / branche `dev`, extractor `13a655f`)
- [x] Corriger le target Android de `shared` (n'a jamais compilé : import `androidContext` cassé dans `ComposeActivity.kt`)
- [ ] Implémenter un fournisseur poToken côté desktop (le kiosk `FEtrending` est bloqué par YouTube → tendances officielles indisponibles, voir issue upstream #12805)
- [ ] Revoir `settings.gradle.kts` / CI (workflows GitHub Actions à valider sur `main`)

### 1.2 Interface — grande refonte
- [x] Éclater `App.kt` (monolithe ~400 lignes) en écrans modulaires : `HomeContent` (recherche + catégories + grille), `PlayerOverlay` (lecteur + détails + liées), `DownloadOverlay` (téléchargements)
- [x] **Navigation sidebar fonctionnelle** : chaque menu affiche son contenu (Home/Trending → grille, Subscriptions/Library → états vides explicites au lieu de réutiliser l'accueil)
- [x] Navigation par onglets / sidebar cohérente mobile + desktop (Home = fil par défaut, Trending = catégories, Subscriptions, Library)
- [ ] Thème dynamique par service (YouTube rouge, SoundCloud orange, Bandcamp bleu…) + thème sombre/clair + Material 3
- [ ] Mode adaptatif : layout smartphone ↔ tablette ↔ desktop (grille responsive)
- [x] Écran d'accueil : rangées thématiques (Gaming, Musique, Films & Séries, Podcasts) chargées une par une + « See all » qui ouvre la grille de la catégorie
- [x] Page vidéo moderne : miniatures, uploader, description dépliable, commentaires, vidéos liées
- [x] Page chaîne : avatar, nombre d'abonnés, bouton s'abonner/se désabonner, vidéos paginées (playlists de la chaîne à venir)
- [x] Page recherche avec filtres (tout / vidéos / chaînes) + historique de recherche
- [ ] Animations et transitions fluides
- [x] États vide/chargement/erreur soignés (bouton « Try again » qui relance exactement le contenu affiché)

### 1.3 Tendances par thèmes (jeux vidéo, musique, films/séries, podcasts)
- [x] Onglets de catégories dans l'écran d'accueil (Tout, Gaming, Musique, Films & Séries, Podcasts)
- [x] Récupération robuste : kiosk officiel tenté d'abord, repli sur requêtes de recherche fiables (le kiosk `FEtrending` est bloqué par YouTube côté upstream)
- [x] Filtre « vidéos uniquement » + tri par nombre de vues + dédoublonnage → **Gaming : 4 → 20 items**, vraies vidéos populaires
- [x] Nombre de vues affiché sur les cartes (ex. « 17,1M views »)
- [x] Test d'intégration JVM vérifiant le contenu (20 items par catégorie, 3/3 tests OK)
- [ ] Support des catégories pour les autres services (SoundCloud, Bandcamp…)
- [ ] Personnalisation des catégories par l'utilisateur

### 1.4 Lecture & médias
- [x] Lecteur : sélection de qualité (+ qualité préférée dans les réglages), **vitesse de lecture**, **file d'attente**, **répétition (off / file / vidéo)**, **mode audio seul**, précédent/suivant, muet, plein écran, cinéma, PiP
- [x] Sous-titres (Android : sélection de piste + affichage des sous-titres ; desktop : menu masqué tant que VLC n'est pas branché)
- [x] Doublage : menu « Audio track » quand la vidéo propose plusieurs pistes (bascule automatiquement sur un flux vidéo seul pour éviter deux pistes superposées)
- [ ] Mini-player flottant (mobile)
- [ ] Cast / Chromecast (optionnel)
- [x] Lecture audio seule (mode « musique »)

### 1.5 Bibliothèque locale (déjà dans NewPipe, à harmoniser avec la nouvelle UI)
- [x] Abonnements sans compte (+ désabonnement et fil « nouvelles vidéos » des chaînes suivies)
- [x] Historique de lecture (reprise à la position, suppression d'une entrée, effacement complet, activation/désactivation)
- [x] Playlists locales (création, renommage, suppression, ajout/retrait de vidéos) + liste « à regarder plus tard »
- [x] Téléchargements (video/audio) avec liste des fichiers et progression (desktop)
- [x] Export/import : sauvegarde JSON complète (historique, playlists, à regarder plus tard, abonnements) depuis les réglages

### 1.6 Apps PC & mobile
- [x] Installateurs : MSI/EXE Windows, DEB Linux (ciblés par Compose + workflow CI)
- [ ] DMG macOS, AppImage Linux
- [x] App iOS (`iosApp`) — utilise la nouvelle UI `shared` (MainViewController)
- [ ] Notifications de nouveaux contenus des abonnements
- [ ] Tester l'APK sur un vrai appareil Android
- [x] Accès à l'interface NewPipe classique depuis les réglages Android (toutes les fonctions d'origine restent atteignables)
- [ ] **Porter la nouvelle UI `shared` sur Android** : l'app Android actuelle (`app`) est l'app NewPipe d'origine et ne dépend pas de `shared` — la refonte UI (tendances par catégories, navigation, sync serveur) n'est donc pour l'instant disponible que sur PC et iOS. Brancher `ComposeActivity` (déjà dans `shared/androidMain`) dans un launcher Android

### 1.7 Connexion au serveur (menu dans les apps)
- [x] Bouton « serveur » dans la sidebar (icône nuage) + dialogue de connexion (URL, identifiant, mot de passe)
- [x] Inscription + connexion + déconnexion, token JWT persisté localement
- [x] **Sync des positions de lecture** : reprise à la position sauvegardée à l'ouverture, push à la fermeture
- [x] Client multiplateforme (ktor-client, okhttp/darwin) + test d'intégration contre un serveur live (register → push → pull, 0 échec)
- [x] **Sync des playlists, abonnements et « à regarder plus tard »** (`/api/library`, fusion par URL, la copie la plus récente gagne)
- [x] Sync de l'historique de lecture (300 entrées max, fusion par URL sur la date de visionnage)
- [ ] Sync des likes
- [x] Indicateur visuel d'état de connexion (« Contacting the server… ») + message d'erreur dans le dialogue, mot de passe masqué, bouton « Close » toujours disponible

---

## 🔵 PHASE 2 — Système de compte (serveur)

### 2.1 Serveur d'authentification & compte
- [x] Stack : **Ktor 3.5** (même langage que l'app) + Netty, module Gradle `server`
- [x] Inscription / connexion (username + mot de passe salé), **JWT HMAC-SHA256** sans dépendance externe
- [x] Stockage fichiers JSON (`DATA_DIR`, atomique), env `PORT`/`HOST`/`JWT_SECRET`/`DATA_DIR`
- [x] **Synchronisation multi-appareils** des positions de lecture (`/api/watchstate` GET/POST)
- [ ] Gestion de profil (avatar, nom, préférences)
- [ ] Email ou anonyme + code

### 2.2 Données synchronisées
- [x] **Positions de lecture** (reprise d'une lecture sur un autre appareil — mobile ↔ PC ↔ web)
- [ ] Likes / « J'aime »
- [x] Enregistrements (« plus tard » / favoris) — synchronisés
- [x] Playlists (création + synchronisation multi-appareils ; partage/collaboration à venir)
- [x] Historique synchronisé (optionnel : uniquement sur demande depuis les réglages, vers votre propre serveur)
- [x] Abonnements synchronisés

### 2.3 Recommandations
- [ ] Collecte des signaux (vues, likes, recherches…) — opt-in
- [ ] Moteur de recommandations (filtrage collaboratif simple au départ)
- [ ] Fil « Pour toi » personnalisé
- [ ] Privacy-first : données chiffrées, export, suppression complète

---

## 🟣 PHASE 3 — Serveur YouTube local (web)

- [x] Le même serveur que la Phase 2 sert aussi une **interface web** (vanilla JS, servie sur `/`)
- [x] « Son propre YouTube » : recherche, tendances par catégories, lecture, comptes, reprise des lectures dans le navigateur
- [x] API REST du serveur consommée par le web (`/api/trending`, `/api/search`, `/api/video`, `/api/register`, `/api/login`, `/api/watchstate`)
- [x] Hébergement : **Docker** (`server/Dockerfile` + `docker-compose.yml`, volume persistant) + jar autonome
- [ ] Raccourci navigateur → un clic et on est sur son YouTube
- [x] Abonnements, playlists et « à regarder plus tard » dans la web UI (via `/api/library`, partagés avec les apps)
- [ ] Mode « serveur web » installable (barre d'outils / exe serveur pour Windows)

---

## ⚫ PHASE 4 — MCP + Plugins & intégrations

### 4.1 Intégration Jellyfin (le gros morceau de la vision)
- [ ] Connecteur Jellyfin : films, séries, bibliothèques
- [ ] **Recherche unifiée** : un seul champ cherche dans YouTube ET Jellyfin
- [ ] Agrégation des résultats (vidéos YouTube + films/séries) dans une même grille
- [ ] Lecteur unifié (lancer un film Jellyfin comme une vidéo YouTube)
- [ ] « Regarder plus tard » unifié entre les deux sources

### 4.2 Connexion MCP (Modèle Context Protocol) pour IA
- [ ] Serveur MCP exposant le contenu de l'utilisateur (abonnements, playlists, historique)
- [ ] Actions MCP : rechercher, lire, ajouter à une playlist, résumer une chaîne…
- [ ] Intégration avec assistants IA (Claude, etc.) pour piloter l'app à la voix/au texte

### 4.3 Plugins
- [ ] Plugin Home Assistant (contrôler la lecture, notifications)
- [ ] Plugins pour d'autres plateformes de streaming (Plex, Emby…)
- [ ] Architecture de plugins extensible (le serveur comme hub)

---

## 🔧 Suivi technique (dette / infrastructure)

- [x] **Workflow CI** `.github/workflows/build.yml` : APK Android + MSI Windows + exe portable + .deb Linux + jar serveur + image Docker (poussée sur GHCR sur `main`)
- [x] Poussé sur GitHub (`main`)
- [x] Tests automatisés (bibliothèque locale, file d'attente du lecteur, UI `shared`, extractor)
- [ ] Signature APK et mises à jour
- [ ] Telemetry/CRASH reporting (opt-in)
- [ ] Documentation développeur + utilisateur
- [ ] Traductions (le code source est déjà très internationalisé)

---

### État actuel (septembre 2026)

**Mise au point 1.3.0 — synchronisation upstream + menus complets**

- ✅ **Base upstream à jour** : 126 commits de NewPipe `dev` (0.29.1) mergés, NewPipeExtractor `13a655f`, API supprimée `setFetchIosClient` retirée du code du fork
- ✅ **Bibliothèque** (nouvel onglet) : Historique / Playlists / À regarder plus tard / Téléchargements, chaque ligne jouable, chaque liste effaçable
- ✅ **Lecteur complet** : file d'attente, répétition, vitesse, audio seul, ouvrir dans le navigateur, ajouter à une playlist, description, commentaires
- ✅ **Abonnements** : désabonnement, ouverture de chaîne, fil « nouvelles vidéos »
- ✅ **Réglages** : lecture (autoplay, reprise, qualité préférée), historique (activation + effacement), sauvegarde/restauration, interface classique (Android), à propos
- ✅ **Boutons corrigés** : bouton lecture du survol des cartes (ne faisait rien), icône de téléchargement (« + »), icône plein écran figée, boutons précédent/muet/vitesse absents sur desktop
- ✅ **Liens « À propos » réparés** : le rebranding avait cassé les URL (`TeamONewPipe/ONewPipe`, `https://ONewPipe.net/…`) → dépôt réel + `PRIVACY.md`
- ✅ **Bug de reprise de lecture** : la durée était stockée en secondes et comparée à des millisecondes, la reprise ne se déclenchait jamais

### État précédent (août 2026)

- ✅ Poussé sur GitHub (`main`) — tout le travail consolidé et commité
- ✅ **Serveur ONewPipe** : module `server` (Ktor) — comptes (register/login JWT), sync des positions de lecture, **web UI complète** (recherche, tendances par catégories, lecteur, reprise des lectures), **Docker** (Dockerfile + compose), jar autonome. Testé de bout en bout (register → push → pull watchstate, trending/search/video OK)
- ✅ **Sync dans les apps** : menu « connecter au serveur » dans la sidebar (mobile + PC), client ktor multiplateforme, reprise à la position sauvegardée + push à la fermeture
- ✅ **Workflow CI** : APK Android, MSI Windows + exe portable, .deb Linux, jar serveur + image Docker (GHCR sur `main`)
- ✅ L'app Android **compile** et produit un APK debug — 5 bugs de build corrigés (namespace R/BuildConfig, tri toml, bugs compilateur Kotlin 2.3, build `shared` Android)
- ✅ L'app PC **compile** et se lance (MSI Windows + distribution décompactée)
- ✅ NewPipeExtractor mis à jour vers la dernière version upstream
- ✅ **Tendances par catégories** fonctionnelles (Tout/Gaming/Musique/Films & Séries/Podcasts) : triées par popularité, vues affichées sur les cartes
- ✅ **Crash du lecteur corrigé** (vlcj « Invalid memory access » au stop — course entre threads)
- ✅ **Lecteur réparé dans les builds empaquetés** : le runtime jpackage n'incluait pas `jdk.unsupported` → `sun.misc.Unsafe` introuvable → surface vidéo noire. Fix : `modules("jdk.unsupported")` dans `nativeDistributions`. **Vérifié en conditions réelles** : clic sur une vidéo → lecture complète (« The Weeknd - Popular », contrôles, progression 0:00→3:50), fermeture sans crash, 0 erreur vlcj
- ✅ **Navigation vérifiée en conditions réelles** : Home → grille, Subscriptions → état vide, retour → grille
- ✅ UI modulaire : `App.kt` éclaté en `HomeContent` / `PlayerOverlay` / `DownloadOverlay`
- ⚠️ Warnings vlcj « stale plugins cache » au démarrage (inoffensifs, liés à l'installation VLC locale)
- ⚠️ **Architecture Android** : l'app mobile (`app`, NewPipe d'origine) ne consomme pas encore la nouvelle UI `shared` (tendances, sync serveur, navigation) — à brancher (voir 1.6)
- ⚠️ Le kiosk tendances officiel de YouTube est bloqué côté upstream (issue #12805) → poToken à implémenter pour le retrouver
