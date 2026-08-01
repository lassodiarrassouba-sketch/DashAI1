# DIASCO Auto — intégration ThinkDiag

## Objectif

Cette extension ajoute l'analyse des diagnostics automobiles à DIASCO sans modifier les fonctions déjà présentes dans l'application principale : conversation, réveil vocal, caméra, génération d'images, création de sites et mémoire de conversation.

L'écran automobile est une activité Android séparée nommée **DIASCO Auto**. Dans la version finale signée, il apparaît comme une deuxième icône de lancement, tout en restant dans la même application et le même APK.

## Architecture retenue

ThinkDiag utilise son application officielle **ThinkDiag+** pour la communication Bluetooth avec le boîtier et les calculateurs du véhicule. DIASCO ne tente pas de reproduire, contourner ou modifier ce protocole.

Le fonctionnement est le suivant :

1. Ouvrir DIASCO Auto puis toucher **Ouvrir ThinkDiag+**.
2. Dans ThinkDiag+, lancer le diagnostic du véhicule.
3. Depuis le rapport, choisir **Partager** puis **DIASCO Auto**.
4. DIASCO Auto récupère le texte, le lien officiel ThinkCar ou le PDF partagé.
5. L'analyse commence automatiquement, les codes défaut sont repérés localement, puis le backend DIASCO explique le rapport.
6. Un résumé est lu à voix haute et le résultat est enregistré dans l'historique local.

Cette architecture est volontairement en lecture seule. Elle évite qu'une erreur logicielle puisse effacer des codes, programmer un calculateur, lancer un test actif ou commander un organe du véhicule.

## Formats pris en charge

- texte partagé par ThinkDiag+ ;
- lien HTTPS de rapport provenant d'un domaine officiel ThinkCar autorisé ;
- fichier texte ou HTML importé ;
- rapport PDF partagé ou importé.

Pour un PDF, les premières pages sont rendues localement par Android puis lues par le moteur visuel déjà présent dans DIASCO. Le texte obtenu est ensuite envoyé au moteur d'analyse automobile.

## Sécurité et confidentialité

- aucune clé API n'est ajoutée à l'APK ;
- les domaines de rapports distants sont limités aux domaines officiels ThinkCar configurés dans le code ;
- les téléchargements ont une limite de taille et un nombre de redirections limité ;
- le rapport est traité comme une donnée non fiable : toute instruction contenue dans le rapport est ignorée par le prompt d'analyse ;
- un rapport peut contenir le VIN, le kilométrage et les défauts du véhicule ; les extraits nécessaires sont envoyés au backend DIASCO lorsque l'analyse en ligne est utilisée ;
- l'historique automobile est conservé uniquement dans les préférences locales de l'appareil ;
- aucune fonction d'effacement de DTC, de codage ECU ou de commande d'actionneur n'est implémentée.

## Profil véhicule par défaut

Le premier profil est prérempli pour :

- Mercedes ;
- C250 ;
- année 2012 ;
- essence.

Ces champs restent modifiables et sont enregistrés localement.

## Limite actuelle

L'analyse automatique débute dès que le rapport est partagé vers DIASCO Auto. En revanche, la lecture continue des valeurs en direct depuis le boîtier ThinkDiag ne peut être ajoutée proprement que si ThinkCar fournit un SDK Android ou un accès partenaire documenté. Jusqu'à cette étape, l'application officielle reste responsable de la liaison Bluetooth et du scan.

## Test rapide

```powershell
cd android
.\gradlew.bat :app:testDebugUnitTest :app:assembleDebug
```

L'APK debug porte un suffixe d'application distinct. Il peut donc être installé à côté de la version DIASCO déjà signée, sans désinstaller l'application actuelle ni effacer ses données.

Après installation sur l'autoradio Android 13, deux entrées de test apparaissent dans ce nouvel APK :

- DIASCO Test ;
- DIASCO Auto.

Vérifier que les fonctions existantes de DIASCO sont inchangées, puis partager un rapport ThinkDiag+ vers DIASCO Auto.
