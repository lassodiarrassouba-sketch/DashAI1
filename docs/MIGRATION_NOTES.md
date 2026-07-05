# Notes de migration depuis l'APK fourni

## Changements faits

- Remplacement du moteur Google Custom Search par un client backend IA configurable.
- Suppression du principe de clé API embarquée dans l'application.
- Suppression de la dépendance obligatoire à des assets IA incomplets ou non vérifiables.
- Ajout d'une interface de test du backend.
- Ajout d'un mode hors ligne explicite avec réponses locales fiables.
- Ajout d'une reconnaissance vocale via le service Android disponible sur l'appareil.
- Ajout d'une synthèse vocale Android.

## Points volontairement non repris tels quels

- Les fichiers TensorFlow Lite placeholders de l'ancien APK ne sont pas repris.
- Les clés Google visibles dans le binaire ne sont pas reprises.
- Le moteur Google Custom Search n'est pas repris comme backend de conversation.
- Les grosses dépendances natives Vosk/OpenCV/TFLite ne sont pas incluses dans cette base propre. Elles peuvent être réintégrées ensuite, mais séparément et avec de vrais modèles.

## À faire avant publication Play Store

- Héberger le backend derrière HTTPS.
- Ajouter authentification/quotas côté backend si l'application devient publique.
- Mettre à jour la politique de confidentialité pour indiquer clairement quand une question est envoyée au backend IA.
- Générer une clé de signature release et publier un AAB signé.
