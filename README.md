# Mon Petit Journal 💌

Application Android native en Kotlin + Jetpack Compose. Le journal fonctionne hors ligne et conserve les entrées sur l'appareil.

## Fonctions de cette première version
- Journal intime avec ajout, modification et suppression d'entrées.
- Petit compagnon personnalisable : prénom, ton, âge/catégorie et thème.
- Rappels quotidiens configurables via notifications locales.
- Analyse locale et heuristique du texte : repère certains mots associés au stress, à la tristesse ou à la surcharge et propose doucement une conversation.
- Thèmes : Nuit, Océan, Rose, Lavande, Forêt, Soleil.
- Profils d'ambiance : doux, drôle, calme, motivant.
- Fonctionnement hors ligne : aucun texte de journal envoyé à un serveur.

## Important à savoir
Le « compagnon IA » est volontairement une première version embarquée, fondée sur des règles et des indices linguistiques locaux, pas un grand modèle de langage. Il peut mal interpréter un texte. Il ne pose pas de diagnostic et ne remplace pas une personne de confiance ou un professionnel. Si un texte évoque un danger immédiat, l'application encourage à contacter une personne de confiance ou les services d'urgence locaux. Les notifications et l'analyse émotionnelle sont désactivables.

## Compiler avec GitHub Actions
1. Crée un dépôt GitHub et envoie-y tout le contenu de ce projet.
2. Ouvre l'onglet **Actions** et autorise les workflows si GitHub le demande.
3. Le workflow `Android APK` compile automatiquement à chaque push sur `main`/`master`, et peut aussi être lancé manuellement.
4. Télécharge `MonPetitJournal-debug-apk` dans les artefacts du workflow terminé.

Aucune clé API n'est nécessaire. Le projet utilise JDK 17 et Android SDK 35.

## Compilation locale
Ouvre le projet dans Android Studio récent, puis lance la configuration `app`. Pour une compilation en ligne de commande : `./gradlew assembleDebug` (le wrapper Gradle n'est pas inclus dans cette archive ; Android Studio peut le générer, ou ajoute les fichiers Gradle Wrapper habituels au dépôt).

## Vie privée
Les entrées sont stockées dans les préférences privées de l'application sur l'appareil. Cette version n'inclut pas de verrouillage biométrique ni de chiffrement applicatif dédié : évite d'y stocker des secrets très sensibles et protège ton téléphone par code.
