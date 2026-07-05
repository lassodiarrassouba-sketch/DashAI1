# DashAI V2 - améliorations de test

Cette version conserve l'autorisation HTTP locale uniquement en build debug, et ajoute :

- nettoyage des marqueurs Markdown dans les réponses, par exemple `**texte**` devient `texte` ;
- contexte récent envoyé au backend pour mieux gérer les relances comme « oui », « depuis quand ? », « et lui ? » ;
- réponses backend demandées sans Markdown pour une meilleure lecture vocale ;
- erreurs de reconnaissance vocale moins envahissantes quand aucun son exploitable n'est détecté.

Le backend accepte maintenant le champ JSON optionnel `history` :

```json
{
  "question": "depuis quand ?",
  "locale": "fr-FR",
  "client": "dashai-android",
  "history": "Vous : qui est le président de la Côte d'Ivoire ?\nDashAI : Le président est Alassane Ouattara."
}
```

Pour tester sur un téléphone réel avec un serveur local, garder une URL du type :

```text
http://ADRESSE-IP-DU-PC:8000/api/ask
```

Pour une publication réelle, utiliser HTTPS.
