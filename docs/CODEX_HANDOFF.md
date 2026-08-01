# Passage de relais vers Codex — DIASCO + ThinkDiag

## Emplacement du travail

- Dépôt : `lassodiarrassouba-sketch/DashAI1`
- Branche à ouvrir dans Codex : `codex/diasco-thinkdiag`
- Branche d’origine du développement : `feature/thinkdiag-obd`
- Demande de fusion historique : PR `#2`
- Dernier état Android repris : version `2.4.3-test`

La branche Codex contient l’ensemble des sources de l’application Android, du backend FastAPI, de la PWA, de la documentation et des modules ThinkDiag.

## Résultat fonctionnel actuel

### Une seule application

Android affiche une seule icône de test. L’accueil `DiascoHomeActivity` ouvre trois espaces internes :

- **Assistant DIASCO** ;
- **Diagnostic Auto** ;
- **Atelier ThinkDiag**.

Les fonctions historiques de DIASCO n’ont pas été supprimées : conversation, réveil vocal « Dis Diasco », empreinte vocale, caméra, analyse d’image, génération d’images, création de sites et mémoire de conversation.

### Diagnostic Auto

Le module `ThinkDiagActivity` :

- ouvre l’application officielle ThinkDiag+ ;
- reçoit un rapport partagé en texte ou PDF ;
- accepte un lien HTTPS provenant d’un domaine ThinkCar autorisé ;
- extrait localement les codes DTC `P`, `C`, `B` et `U` ;
- classe le niveau en information, avertissement ou critique ;
- envoie un extrait sécurisé au backend DIASCO pour explication ;
- lit un résumé à voix haute ;
- conserve l’historique local des diagnostics ;
- utilise par défaut le profil Mercedes C250 2012 essence, modifiable localement.

### Atelier ThinkDiag

Le module `ThinkDiagWorkshopActivity` prépare et journalise :

- l’effacement des défauts ;
- le codage et les adaptations ;
- les tests actifs de carrosserie, moteur et châssis.

Il contient des contrôles de sécurité et des confirmations écrites. Les commandes SRS dangereuses restent bloquées.

L’exécution réelle reste dans ThinkDiag+ : DIASCO ouvre l’application officielle après validation, car aucun SDK public ThinkCar permettant une commande directe depuis une application tierce n’a été intégré.

## Architecture importante

- Android natif Java dans `android/`.
- Backend FastAPI dans `server/`.
- Client distant dans `android/app/src/main/java/com/dashai/app/ai/RemoteAiClient.java`.
- Backend HTTPS de test injecté par GitHub Actions : `https://dashai-backend-dabr.onrender.com/api/ask`.
- Le build debug utilise un `applicationIdSuffix` pour pouvoir être installé à côté de la version signée existante.
- Aucune clé API ne doit être placée dans le code Android.

## Fichiers créés ou modifiés pour ThinkDiag

- `.github/workflows/thinkdiag-android-build.yml`
- `android/app/build.gradle.kts`
- `android/app/src/main/AndroidManifest.xml`
- `android/app/src/main/java/com/dashai/app/DiascoHomeActivity.java`
- `android/app/src/main/java/com/dashai/app/obd/ThinkDiagActivity.java`
- `android/app/src/main/java/com/dashai/app/obd/ThinkDiagWorkshopActivity.java`
- `android/app/src/main/java/com/dashai/app/obd/ObdReportTools.java`
- `android/app/src/main/java/com/dashai/app/obd/ObdHistoryStore.java`
- `android/app/src/main/java/com/dashai/app/obd/ThinkDiagReportLoader.java`
- `android/app/src/main/java/com/dashai/app/obd/WorkshopHistoryStore.java`
- `android/app/src/test/java/com/dashai/app/obd/ObdReportToolsTest.java`
- `android/app/src/test/java/com/dashai/app/obd/WorkshopSafetyPolicyTest.java`
- `docs/THINKDIAG_INTEGRATION.md`
- `AGENTS.md`

## Dernière validation connue

Le workflow GitHub Actions a réussi sur l’état repris avant la création de la branche Codex :

- `:app:testDebugUnitTest` : succès ;
- `:app:assembleDebug` : succès ;
- artefact généré : `diasco-2.4.3-debug-apk`.

Le prochain changement poussé sur la branche Codex doit relancer la même validation.

## Travail à poursuivre dans Codex

### Étape 1 — Essai sur autoradio

- Installer l’APK debug sur l’autoradio Android 13.
- Vérifier qu’une seule nouvelle icône apparaît.
- Tester l’ouverture des trois espaces.
- Vérifier les permissions microphone, notifications et fichiers.
- Vérifier que la caméra et le réveil vocal existants fonctionnent toujours.

### Étape 2 — Essai ThinkDiag réel

- Installer ThinkDiag+ sur l’autoradio.
- Brancher le boîtier ThinkDiag au véhicule.
- Effectuer un scan complet dans ThinkDiag+.
- Partager le rapport vers DIASCO.
- Tester successivement un partage texte, un lien officiel et un PDF si disponibles.
- Comparer les DTC affichés par DIASCO aux DTC visibles dans ThinkDiag+.

### Étape 3 — Atelier

- Vérifier que les cases et confirmations empêchent toute ouverture prématurée de ThinkDiag+.
- Tester le parcours d’effacement sans exécuter l’effacement tant que le rapport avant intervention n’est pas sauvegardé.
- Tester le parcours de codage avec une alimentation stabilisée uniquement.
- Garder les tests moteur/châssis réservés à un environnement dégagé et contrôlé.

### Étape 4 — Production

Après validation réelle :

- corriger les anomalies observées ;
- décider du nom public final avec l’utilisateur avant la release ;
- fusionner le travail vers `main` ;
- construire une version release avec l’URL HTTPS et la clé de signature existantes ;
- remplacer l’ancienne application plutôt que conserver deux installations.

## Points à ne pas présenter comme déjà réalisés

- connexion Bluetooth directe entre DIASCO et le boîtier ThinkDiag ;
- surveillance continue de toutes les valeurs live depuis DIASCO ;
- effacement direct des DTC par DIASCO ;
- flash ou reprogrammation directe d’un calculateur ;
- commande directe d’actionneurs depuis DIASCO.

Ces fonctions nécessitent un SDK officiel ou un accès partenaire ThinkCar. Dans l’état actuel, DIASCO encadre le processus et ThinkDiag+ exécute la commande.