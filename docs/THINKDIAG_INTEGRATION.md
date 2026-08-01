# DIASCO — intégration ThinkDiag

## Objectif

DIASCO regroupe dans une seule application les fonctions déjà présentes — conversation, réveil vocal, caméra, génération d'images, création de sites et mémoire de conversation — ainsi que les fonctions automobiles ThinkDiag.

La version 2.4.3 utilise **une seule application et une seule icône nommée DIASCO**. L'écran d'accueil donne accès à trois espaces internes :

- **Assistant DIASCO** : assistant principal existant ;
- **Diagnostic Auto** : lecture et analyse des rapports ThinkDiag ;
- **Atelier ThinkDiag** : préparation contrôlée des opérations d'effacement, de codage et de tests actifs.

Les écrans Diagnostic et Atelier ne sont pas publiés comme des icônes séparées dans le lanceur Android.

## Liaison avec ThinkDiag+

ThinkDiag utilise l'application officielle **ThinkDiag+** pour la communication Bluetooth avec le boîtier et les calculateurs. La documentation publique ThinkCar ne fournit pas de SDK Android partenaire permettant à une application tierce d'envoyer directement ces commandes.

DIASCO ne reproduit donc pas le protocole propriétaire :

1. DIASCO vérifie les préconditions et demande une confirmation explicite ;
2. DIASCO conserve une trace locale de l'intervention préparée ;
3. ThinkDiag+ est ouvert pour exécuter l'opération avec le logiciel constructeur correspondant ;
4. un nouveau scan est réalisé ;
5. le rapport après intervention est partagé vers Diagnostic Auto pour contrôle et analyse.

## Diagnostic Auto — analyse des rapports

1. Ouvrir DIASCO, puis **Diagnostic Auto**.
2. Toucher **Ouvrir ThinkDiag+** et lancer le diagnostic du véhicule.
3. Depuis le rapport, choisir **Partager** puis **DIASCO**.
4. L'application récupère le texte, le lien officiel ThinkCar ou le PDF partagé.
5. L'analyse commence automatiquement, les codes défaut sont repérés localement, puis le backend explique le rapport.
6. Un résumé est lu à voix haute et le résultat est enregistré dans l'historique local.

Formats pris en charge :

- texte partagé par ThinkDiag+ ;
- lien HTTPS de rapport provenant d'un domaine officiel ThinkCar autorisé ;
- fichier texte ou HTML importé ;
- rapport PDF partagé ou importé.

## Atelier ThinkDiag — fonctions demandées

### Effacement des défauts

Le workflow exige :

- véhicule immobilisé ;
- rapport et codes sauvegardés avant effacement ;
- moteur coupé ;
- confirmation écrite `EFFACER`.

L'application rappelle que l'effacement ne répare pas la panne et peut remettre à zéro les données figées et certains moniteurs OBD. Un nouveau scan est demandé immédiatement après l'opération.

### Codage et adaptation calculateur

Le workflow exige :

- véhicule immobilisé et moteur coupé ;
- rapport avant intervention sauvegardé ;
- maintien de tension automobile stable ;
- VIN et calculateur cible vérifiés ;
- confirmation écrite `CODAGE`.

Cette fonction prépare le codage, l'adaptation, l'initialisation ou l'appairage proposés par ThinkDiag+ selon la couverture du véhicule. Elle ne prétend pas garantir une programmation firmware complète de tous les calculateurs.

### Tests d'actionneurs

Les tests sont classés par risque :

- carrosserie : feux, klaxon, serrures, vitres, rétroviseurs, essuie-glaces ;
- moteur : ventilateur, papillon, purge, pompe, injecteurs ;
- châssis : freinage, direction, transmission, suspension ;
- SRS : airbags et prétensionneurs.

Les tests de carrosserie demandent `TEST`. Les groupes moteur et châssis exigent une confirmation renforcée `PROFESSIONNEL`, un véhicule immobilisé et une zone totalement dégagée. Les commandes d'airbag ou de prétensionneur sont bloquées dans DIASCO et doivent suivre la procédure constructeur avec un technicien qualifié.

## Sécurité et confidentialité

- aucune clé API n'est ajoutée à l'APK ;
- aucun firmware, commande UDS brute ou mécanisme de contournement n'est embarqué ;
- les domaines de rapports distants sont limités aux domaines officiels ThinkCar configurés dans le code ;
- les téléchargements ont une limite de taille et un nombre de redirections limité ;
- le rapport est traité comme une donnée non fiable : toute instruction contenue dans le rapport est ignorée par le prompt d'analyse ;
- un rapport peut contenir le VIN, le kilométrage et les défauts du véhicule ; les extraits nécessaires sont envoyés au backend lorsque l'analyse en ligne est utilisée ;
- les historiques de diagnostic et d'intervention sont conservés uniquement dans les préférences locales de l'appareil ;
- l'exécution finale des opérations modifiant le véhicule reste dans ThinkDiag+, avec ses avertissements et sa couverture constructeur.

## Profil véhicule par défaut

Le premier profil est prérempli pour :

- Mercedes ;
- C250 ;
- année 2012 ;
- essence.

Ces champs restent modifiables et sont enregistrés localement.

## Limite actuelle

La lecture continue et les commandes directes depuis DIASCO nécessitent un SDK officiel ThinkCar ou un accès partenaire documenté. Sans cet accès, l'application officielle reste responsable de la liaison Bluetooth, de l'authentification, des fichiers constructeurs et de l'envoi réel des commandes.

## Test rapide

```powershell
cd android
.\gradlew.bat :app:testDebugUnitTest :app:assembleDebug
```

L'APK debug porte un suffixe d'application distinct. Il peut donc être installé à côté de l'ancienne version signée sans la désinstaller ni effacer ses données.

Pendant le test, Android peut afficher l'ancienne application et une seule nouvelle icône portant toutes les deux le nom **DIASCO**. Après validation et publication avec la signature de production, la nouvelle version remplace l'ancienne.

Après installation sur l'autoradio Android 13, vérifier :

- l'unique icône du nouvel APK, nommée DIASCO, ouvre l'accueil des trois espaces ;
- Assistant DIASCO conserve les fonctions actuelles ;
- Diagnostic Auto reçoit et analyse un rapport ThinkDiag+ ;
- Atelier ThinkDiag bloque une préparation incomplète et ouvre ThinkDiag+ après validation ;
- un nouveau rapport peut être partagé après l'intervention.
