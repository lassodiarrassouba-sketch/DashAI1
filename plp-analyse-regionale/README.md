# PLP Analyse Régionale — environnement Codex

Ce dossier est la passation technique du progiciel Excel VBA **PLP Analyse Régionale**, marque **Prince Lass Progiciel**.

## Démarrage dans Codex

1. Ouvrir le dépôt `lassodiarrassouba-sketch/DashAI1`.
2. Choisir la branche `codex/plp-analyse-regionale`.
3. Ouvrir le dossier `plp-analyse-regionale/`.
4. Ajouter dans l’espace de travail Codex le paquet local `PLP_Analyse_Regionale_Codex_Sanitise.zip` fourni dans la conversation.
5. Lire `AGENTS.md`, puis ce fichier.

Le dépôt est public : les classeurs binaires `.xlsm` ne sont pas publiés directement dans la branche. Le paquet local contient les versions v2.5 et v2.6, le VBA extrait, les captures d’erreur et le jeu CSV anonymisé, sans la liste réelle de 2 552 personnes.

## Tâche prioritaire

Corriger définitivement les macros **ExporterPDF** et **ImprimerStatistiques**. Les versions v2.5 et v2.6 échouent toujours dans Excel Windows sur le PC utilisateur. Les autres fonctions principales fonctionnent suffisamment pour traiter 2 552 personnes.

## Fichiers de départ du paquet

- `workbooks/reference/PLP_Analyse_Regionale_v2_5_Statistiques_Stables.xlsm` : base de référence stable pour import/statistiques ;
- `workbooks/current/PLP_Analyse_Regionale_v2_6_PDF_Impression_Stabilises.xlsm` : dernière tentative PDF/impression, toujours en échec ;
- `src/vba/v2_5/` et `src/vba/v2_6/` : modules VBA déjà extraits ;
- `screenshots/08_erreur_export_pdf.jpeg` et `screenshots/09_erreur_impression.jpeg` : erreurs observées ;
- `data/sanitized/111_anonymise.csv` : jeu structuré anonymisé.

## Objectif fonctionnel

Le progiciel doit :

- importer une liste Excel ou CSV jusqu’à **3 000 personnes** ;
- accepter : N° d’ordre, Nom et prénom, Grade, Matricule, Unité, Moyenne, Examen, Région ;
- reconnaître les CSV séparés par `;`, tabulation ou virgule ;
- accepter le collage direct d’une plage copiée depuis Excel ;
- classer et colorier en Nord, Sud, Centre, Est ou Ouest ;
- rechercher par nom, matricule, grade, unité ou examen ;
- calculer le statut uniquement à partir de la moyenne : **Admis si moyenne >= 12**, Échec si moyenne < 12 ;
- afficher effectif, admis, échecs, % admis, % échecs et % du total par région ;
- afficher les cinq régions dans un camembert unique avec le pourcentage sur chaque part ;
- exporter la liste coloriée vers Excel ;
- exporter le rapport statistique en PDF ;
- imprimer le rapport statistique.

## Architecture connue

Feuille principale : `Application`

- données : `A20:I3019` ;
- capacité : 3 000 lignes ;
- tableau statistique intégré : `K20:Q33` ;
- commandes statistiques : `K46:W48` ;
- données auxiliaires : `Z5:AF10` ;
- graphique : `PLP_Graphique_Regions`.

Modules VBA :

- `ThisWorkbook.cls` : événements d’ouverture, activation, sauvegarde et clics sur zones-cellules ;
- `Sheet1.cls` : module de feuille minimal ;
- `Module1.bas` : logique métier, import, collage, couleurs, statistiques, PDF, impression et export Excel.

## Bug actuel

Sur le PC Windows de l’utilisateur :

- `ExporterPDF` affiche : **« L’export PDF a échoué : »** sans description utile ;
- `ImprimerStatistiques` affiche : **« L’aperçu avant impression n’a pas pu être affiché : »** ;
- une tentative ultérieure utilisant `Range.ExportAsFixedFormat` et `Range.PrintOut` échoue encore ;
- une ancienne capture a aussi montré une licence Office expirée lors d’une opération graphique. Il faut distinguer erreur VBA, absence d’imprimante et Office non activé.

## Correction recommandée

Ne pas exporter/imprimer une plage de la feuille principale contenant toute l’interface.

Créer une feuille dédiée, par exemple `Rapport_Impression`, puis :

1. recalculer les statistiques ;
2. copier le tableau en valeurs/formats sur cette feuille ;
3. reproduire ou copier le graphique sur la feuille rapport ;
4. définir une zone d’impression bornée ;
5. exporter **la feuille** avec `Worksheet.ExportAsFixedFormat` ;
6. imprimer **la feuille** avec `Worksheet.PrintOut` ;
7. restaurer dans un bloc de sortie unique `ScreenUpdating`, `EnableEvents`, `DisplayAlerts`, `Calculation` et la feuille active ;
8. capturer avant toute autre instruction `Err.Number`, `Err.Source`, `Err.Description` ;
9. diagnostiquer `Application.ActivePrinter` et signaler clairement l’absence d’imprimante ;
10. ne jamais masquer l’erreur par un gestionnaire vide.

Ajouter de préférence deux macros testables sans boîte de dialogue :

```vb
Public Function ExporterRapportPDFVers(ByVal chemin As String) As Boolean
Public Function ImprimerRapportSurImprimanteParDefaut() As Boolean
```

Les boutons peuvent ensuite appeler ces fonctions. Cela permet un test COM automatisé dans Excel Windows.

## Critères d’acceptation

- Le CSV anonymisé est réparti en huit colonnes.
- `12,00` donne **Admis** ; `11,90` donne **Échec**.
- Les cinq régions et leurs pourcentages apparaissent sur le graphique.
- PDF : le fichier est réellement créé, lisible, avec titre, tableau et graphique.
- Impression : envoi vers l’imprimante par défaut, ou message précis si aucune imprimante n’est disponible.
- Annulation ou erreur : Excel reste utilisable et tous les états applicatifs sont restaurés.
- Aucun message d’erreur vide.
- Produire une nouvelle version, par exemple `PLP_Analyse_Regionale_v2_7_PDF_Impression_OK.xlsm`.

## Test Windows indispensable

Tester dans **Microsoft Excel de bureau sous Windows**, avec Office activé et :

- une imprimante physique, ou
- `Microsoft Print to PDF` définie comme imprimante par défaut.

LibreOffice ne doit pas servir à sauvegarder le `.xlsm`, car il peut altérer le VBA, le ruban et les objets Excel.

## Confidentialité

Le dépôt est public. La liste réelle de 2 552 personnes, leurs noms, matricules et unités ne doivent jamais y être ajoutés. Le jeu fourni est anonymisé.
