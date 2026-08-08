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
- [ ] Rebaser/merger le dernier code upstream NewPipe (le fork est sur 0.28.7)
- [x] Corriger le target Android de `shared` (n'a jamais compilé : import `androidContext` cassé dans `ComposeActivity.kt`)
- [ ] Implémenter un fournisseur poToken côté desktop (le kiosk `FEtrending` est bloqué par YouTube → tendances officielles indisponibles, voir issue upstream #12805)
- [ ] Revoir `settings.gradle.kts` / CI (workflows GitHub Actions à valider sur `main`)

### 1.2 Interface — grande refonte
- [x] Éclater `App.kt` (monolithe ~400 lignes) en écrans modulaires : `HomeContent` (recherche + catégories + grille), `PlayerOverlay` (lecteur + détails + liées), `DownloadOverlay` (téléchargements)
- [x] **Navigation sidebar fonctionnelle** : chaque menu affiche son contenu (Home/Trending → grille, Subscriptions/Library → états vides explicites au lieu de réutiliser l'accueil)
- [ ] Navigation par onglets / sidebar cohérente mobile + desktop
- [ ] Thème dynamique par service (YouTube rouge, SoundCloud orange, Bandcamp bleu…) + thème sombre/clair + Material 3
- [ ] Mode adaptatif : layout smartphone ↔ tablette ↔ desktop (grille responsive)
- [ ] Écran d'accueil : rangées thématiques (À la une, Musique, Gaming, Films & Séries, Podcasts…)
- [ ] Page vidéo moderne : miniatures, uploader, description, commentaires, vidéos liées
- [ ] Page chaîne (abonnement, contenu, playlists)
- [ ] Page recherche avec filtres (vidéo / chaîne / playlist / date / durée)
- [ ] Animations et transitions fluides
- [ ] États vide/chargement/erreur soignés

### 1.3 Tendances par thèmes (jeux vidéo, musique, films/séries, podcasts)
- [x] Onglets de catégories dans l'écran d'accueil (Tout, Gaming, Musique, Films & Séries, Podcasts)
- [x] Récupération robuste : kiosk officiel tenté d'abord, repli sur requêtes de recherche fiables (le kiosk `FEtrending` est bloqué par YouTube côté upstream)
- [x] Filtre « vidéos uniquement » + tri par nombre de vues + dédoublonnage → **Gaming : 4 → 20 items**, vraies vidéos populaires
- [x] Nombre de vues affiché sur les cartes (ex. « 17,1M views »)
- [x] Test d'intégration JVM vérifiant le contenu (20 items par catégorie, 3/3 tests OK)
- [ ] Support des catégories pour les autres services (SoundCloud, Bandcamp…)
- [ ] Personnalisation des catégories par l'utilisateur

### 1.4 Lecture & médias
- [ ] Lecteur : qualité auto, vitesse, file d'attente, lecture en arrière-plan (déjà dans NewPipe Android — à porter/valider côté shared)
- [ ] Sous-titres, doublage (pistes audio multiples)
- [ ] Mini-player flottant (mobile)
- [ ] Cast / Chromecast (optionnel)
- [ ] Lecture audio seule (mode « musique »)

### 1.5 Bibliothèque locale (déjà dans NewPipe, à harmoniser avec la nouvelle UI)
- [ ] Abonnements sans compte
- [ ] Historique de lecture
- [ ] Playlists locales
- [ ] Téléchargements (video/audio) — déjà en partie porté dans `shared`
- [ ] Export/import (abonnements OPML, historique…)

### 1.6 Apps PC & mobile
- [x] Installateurs : MSI/EXE Windows, DEB Linux (ciblés par Compose + workflow CI)
- [ ] DMG macOS, AppImage Linux
- [x] App iOS (`iosApp`) — utilise la nouvelle UI `shared` (MainViewController)
- [ ] Notifications de nouveaux contenus des abonnements
- [ ] Tester l'APK sur un vrai appareil Android
- [ ] **Porter la nouvelle UI `shared` sur Android** : l'app Android actuelle (`app`) est l'app NewPipe d'origine et ne dépend pas de `shared` — la refonte UI (tendances par catégories, navigation, sync serveur) n'est donc pour l'instant disponible que sur PC et iOS. Brancher `ComposeActivity` (déjà dans `shared/androidMain`) dans un launcher Android

### 1.7 Connexion au serveur (menu dans les apps)
- [x] Bouton « serveur » dans la sidebar (icône nuage) + dialogue de connexion (URL, identifiant, mot de passe)
- [x] Inscription + connexion + déconnexion, token JWT persisté localement
- [x] **Sync des positions de lecture** : reprise à la position sauvegardée à l'ouverture, push à la fermeture
- [x] Client multiplateforme (ktor-client, okhttp/darwin) + test d'intégration contre un serveur live (register → push → pull, 0 échec)
- [ ] Sync des likes, playlists, abonnements, historique (API prête à étendre)
- [ ] Indicateur visuel d'état de connexion + message d'erreur réseau dans le dialogue

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
- [ ] Enregistrements (« plus tard » / favoris)
- [ ] Playlists (création, partage, collaboration)
- [ ] Historique synchronisé (optionnel, avec respect de la vie privée)
- [ ] Abonnements synchronisés

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
- [ ] Abonnements, playlists dans la web UI
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
- [ ] Tests automatisés (unitaires UI `shared`, tests extractor)
- [ ] Signature APK et mises à jour
- [ ] Telemetry/CRASH reporting (opt-in)
- [ ] Documentation développeur + utilisateur
- [ ] Traductions (le code source est déjà très internationalisé)

---

### État actuel (août 2026)

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
