# DashAI V3 — mémoire des relances

Cette version corrige les relances courtes comme :

- « oui »
- « oui dis-le-moi »
- « depuis quand ? »
- « et lui ? »
- « son parcours »

## Changement Android

L'application garde l'historique récent en mémoire pendant la session et envoie un contexte explicite au backend quand la phrase actuelle dépend de la réponse précédente.

Le sous-titre de l'application affiche maintenant :

```text
Assistant vocal + backend IA sécurisé — V3 contexte
```

Si ce texte n'apparaît pas sur le téléphone, l'ancienne application est encore installée.

## Changement backend

Le prompt serveur demande à l'IA d'utiliser le contexte récent au lieu de répondre qu'elle ne connaît pas le sujet.

## Test conseillé

1. Qui est le président de la Côte d’Ivoire ?
2. Oui dis-le-moi
3. Depuis quand ?

La deuxième et la troisième question doivent être comprises avec le contexte de la première réponse.
