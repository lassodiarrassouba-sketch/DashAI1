# Architecture DashAI reconstruite

## Problème corrigé

L'ancien APK mélangeait trois choses : réponses locales limitées, recherche Google Custom Search et une branche API générique non configurée. Cette reconstruction sépare clairement les responsabilités.

## Nouveau flux

```text
Question utilisateur
  ↓
LocalAnswerEngine
  ├─ réponse locale fiable : heure, date, batterie, calcul simple, aide
  └─ pas de réponse locale
        ↓
        Mode en ligne activé ?
          ├─ non : message clair indiquant la limite hors ligne
          └─ oui : RemoteAiClient → votre backend /api/ask → fournisseur IA
```

## Principes de sécurité

- Aucune clé fournisseur IA dans l'APK.
- L'application Android ne connaît que l'URL de votre backend.
- Le backend lit `OPENAI_API_KEY` depuis l'environnement.
- Le trafic production doit passer en HTTPS.
- Le HTTP clair est seulement autorisé pour `10.0.2.2`, `localhost` et `127.0.0.1` afin de faciliter les tests locaux.

## Format API mobile ↔ backend

Requête :

```json
{
  "question": "Explique pourquoi le ciel est bleu en deux phrases.",
  "locale": "fr-FR",
  "client": "dashai-android"
}
```

Réponse :

```json
{
  "answer": "Le ciel paraît bleu parce que l'atmosphère diffuse davantage la lumière bleue du Soleil que les autres couleurs. Cette lumière bleue arrive alors à nos yeux depuis toutes les directions."
}
```
