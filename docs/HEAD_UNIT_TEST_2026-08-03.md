# Essai autoradio — 3 août 2026

## Observations sur les photos

L’autoradio ouvre encore l’ancien écran direct de `MainActivity` (bandeau DIASCO avec les actions Image, Caméra, Code et Site). La version unifiée actuelle doit d’abord afficher `DiascoHomeActivity` avec trois espaces : Assistant DIASCO, Diagnostic Auto et Atelier ThinkDiag.

Deux erreurs sont visibles :

1. `La reconnaissance vocale Android n’est pas disponible sur cet appareil.`
2. `Impossible de joindre le backend IA : Unacceptable certificate: CN=WE1, O=Google Trust Services, C=US`.

## Diagnostic

### Reconnaissance vocale

`SpeechRecognizer.isRecognitionAvailable(context)` retourne `false`. L’autoradio ne fournit donc aucun service Android `RecognitionService` utilisable par l’application, même si le firmware affiche Android 13.

Actions immédiates sur l’autoradio :

- vérifier que le microphone fonctionne dans une autre application ;
- installer ou activer un moteur de reconnaissance vocale compatible, par exemple Speech Services by Google, si le firmware accepte Google Play Services ;
- choisir ce moteur dans Paramètres > Langue et saisie > Saisie vocale, puis redémarrer l’autoradio.

Solution logicielle durable à préparer : ajouter un moteur Vosk français hors ligne comme solution de secours lorsque le service Android est absent. Le modèle mobile recommandé est `vosk-model-small-fr-0.22` (environ 41 Mo). Ne supprimer ni le réveil vocal ni les commandes existantes pendant cette intégration.

### Certificat HTTPS WE1

Le backend HTTPS présente une chaîne Google Trust Services utilisant l’intermédiaire WE1. Le magasin de certificats de cet autoradio ne reconnaît pas correctement cette chaîne, ou sa date/heure est fausse.

Actions immédiates sur l’autoradio :

- activer la date et l’heure automatiques ;
- vérifier le fuseau horaire ;
- redémarrer l’autoradio et retester.

Correction ajoutée sur la branche `codex/diasco-thinkdiag` :

- ajout du certificat officiel `GTS Root R4` dans `android/app/src/main/res/raw/gts_root_r4.pem` ;
- conservation des autorités système ;
- ajout de GTS Root R4 dans les configurations réseau main et debug ;
- aucune désactivation de TLS, aucune acceptation générale de certificats et aucune suppression de la vérification du nom d’hôte.

## Vérification de la bonne version

Dans Paramètres Android > Applications > DIASCO > Informations sur l’application, vérifier la version. La branche Codex actuelle correspond à `2.4.3-test` et utilise l’identifiant `com.dashai.app.thinkdiagtest`.

À l’ouverture de la bonne version, l’écran initial doit proposer :

- Assistant DIASCO ;
- Diagnostic Auto ;
- Atelier ThinkDiag.

Si l’application ouvre directement la conversation comme sur les photos, c’est l’ancienne installation qui a été lancée.

## Prochaines validations Codex

1. Compiler après l’ajout de GTS Root R4 :

```bash
cd android
./gradlew :app:testDebugUnitTest :app:assembleDebug --stacktrace
```

2. Installer la nouvelle APK sur l’autoradio sans supprimer la version signée existante.
3. Vérifier l’écran d’accueil unifié.
4. Tester le backend avec la date/heure correctes.
5. Si la reconnaissance Android reste absente, intégrer Vosk hors ligne en mode de secours.
