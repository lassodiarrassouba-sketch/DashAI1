# Instructions Codex — DIASCO

## Branche de travail

Travaille sur la branche `codex/diasco-thinkdiag` du dépôt `lassodiarrassouba-sketch/DashAI1`.

Cette branche reprend l’intégralité du travail automobile réalisé sur `feature/thinkdiag-obd` et ajoute les présentes consignes de continuité.

## État du produit

Le nom public actuellement enregistré dans le code est **DIASCO**. La version Android de test est `2.4.3-test` avec l’identifiant séparé `com.dashai.app.thinkdiagtest` afin de ne pas écraser l’installation signée existante pendant les essais.

Une seule icône Android ouvre `DiascoHomeActivity`, qui donne accès à trois espaces internes :

1. **Assistant DIASCO** — conversation, dictée manuelle, caméra, analyse d’image, génération d’images, code, formules et création de sites.
2. **Diagnostic Auto** — import ou partage des rapports ThinkDiag, extraction des DTC, analyse IA, lecture vocale et historique local.
3. **Atelier ThinkDiag** — préparation contrôlée de l’effacement des défauts, du codage/adaptation et des tests d’actionneurs.

## Contraintes impératives

- Ne supprime pas et ne dégrade pas les fonctions existantes de DIASCO : caméra, dictée manuelle, synthèse vocale, mémoire de conversation, génération d’images, création de sites et backend.
- Ne transforme pas les trois espaces internes en trois applications ou trois icônes séparées.
- Ne renomme pas l’application sans une demande explicite et récente de l’utilisateur.
- Conserve les clés API exclusivement côté serveur. Aucune clé fournisseur ne doit être intégrée à l’APK.
- ThinkDiag+ reste responsable de la liaison Bluetooth et de l’envoi réel des commandes au véhicule tant qu’aucun SDK partenaire officiel ThinkCar n’est fourni.
- N’ajoute pas de contournement du protocole ThinkDiag, de firmware, de commandes UDS brutes, de déclenchement d’airbag/prétensionneur ou de programmation ECU non documentée.
- Toute fonction modifiant le véhicule doit conserver des confirmations explicites, une vérification des préconditions et un historique local.
- L’effacement des DTC doit toujours rappeler qu’il ne répare pas la panne et exiger un nouveau scan après intervention.
- Le codage doit exiger moteur coupé, tension stabilisée, VIN/calculateur vérifiés et confirmation explicite.
- Les tests actifs moteur/châssis doivent rester protégés par une confirmation renforcée et une zone de sécurité dégagée.

## Fichiers principaux

- `android/app/src/main/java/com/dashai/app/DiascoHomeActivity.java`
- `android/app/src/main/java/com/dashai/app/MainActivity.java`
- `android/app/src/main/java/com/dashai/app/obd/ThinkDiagActivity.java`
- `android/app/src/main/java/com/dashai/app/obd/ThinkDiagWorkshopActivity.java`
- `android/app/src/main/java/com/dashai/app/obd/ObdReportTools.java`
- `android/app/src/main/java/com/dashai/app/obd/ObdHistoryStore.java`
- `android/app/src/main/java/com/dashai/app/obd/ThinkDiagReportLoader.java`
- `android/app/src/main/java/com/dashai/app/obd/WorkshopHistoryStore.java`
- `android/app/src/main/AndroidManifest.xml`
- `android/app/build.gradle.kts`
- `server/main.py`
- `docs/THINKDIAG_INTEGRATION.md`
- `docs/CODEX_HANDOFF.md`

## Validation obligatoire après chaque modification Android

Depuis la racine du dépôt :

```bash
cd android
./gradlew :app:testDebugUnitTest :app:assembleDebug --stacktrace
```

Sous Windows :

```powershell
cd android
.\gradlew.bat :app:testDebugUnitTest :app:assembleDebug --stacktrace
```

Ne considère pas une tâche Android comme terminée sans succès des tests unitaires et de `assembleDebug`.

## Priorités de continuation

1. Tester l’APK sur l’autoradio Android 13 sans désinstaller l’application signée existante.
2. Vérifier l’ouverture des trois espaces depuis l’unique accueil.
3. Installer/ouvrir ThinkDiag+, lancer un scan réel et partager un rapport texte, lien officiel ou PDF vers DIASCO.
4. Vérifier l’analyse DTC, la synthèse vocale et l’historique.
5. Vérifier que l’Atelier bloque les préparations incomplètes et ouvre ThinkDiag+ uniquement après confirmation.
6. Corriger les problèmes observés sur l’autoradio sans toucher aux fonctions actuelles.
7. Après validation réelle seulement, préparer la fusion et la version de production signée.

## Limite fonctionnelle actuelle

DIASCO peut encadrer, expliquer et journaliser les opérations, mais il ne peut pas envoyer directement les commandes propriétaires au boîtier ThinkDiag sans SDK officiel ThinkCar. Ne présente jamais cette limite comme une connexion directe déjà opérationnelle.
