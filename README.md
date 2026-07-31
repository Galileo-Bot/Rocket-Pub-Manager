# Rocket Pub Manager

Rocket Pub Manager est l'outil de prédilection de la modération sur le serveur Discord Rocket Pub.
Le bot est privé et il n'est pas possible de l'inviter sur un autre serveur.

Bot développé par [Ayfri](https://github.com/Ayfri).
Serveur géré par Ayfri et Antow.

## Sommaire

- [Fonctionnalités](#fonctionnalités)
- [Stack technique](#stack-technique)
- [Structure du projet](#structure-du-projet)
- [Base de données](#base-de-données)
- [Configuration](#configuration)
- [Lancer le bot](#lancer-le-bot)
- [Contribuer](#contribuer)

## Fonctionnalités

Le bot couvre l'ensemble de la modération du serveur :

- **Sanctions** : commandes slash pour avertir, mute, ban ou unban un membre, avec gestion des durées et des raisons. Un menu contextuel utilisateur permet aussi de sanctionner directement via un formulaire.
- **Sanctions automatiques** : détection et suppression des publicités invalides dans les salons dédiés (validation des liens d'invitation), avec sanction automatique optionnelle.
- **Détection des sanctions manuelles** : les bans, unbans et timeouts effectués directement depuis Discord sont journalisés automatiquement comme sanctions à partir des logs d'audit.
- **Modification des sanctions** : édition d'une sanction existante (raison, durée, type, modérateur).
- **Vérification des publicités** : chaque publicité postée est envoyée dans un salon de modération dédié, avec des boutons pour la valider ou la refuser. Les boutons restent fonctionnels même après un redémarrage du bot.
- **Blacklist de serveurs** : gestion (ajout, suppression, listing) des serveurs Discord dont les invitations sont interdites, pour éviter de modérer en boucle des serveurs hors ToS.
- **Nettoyage automatique** : suppression des messages publicitaires d'un membre lorsqu'il quitte le serveur, et gestion d'un message de fin de publicité dans les salons dédiés.

## Stack technique

- **Kotlin** 2.4.10 sur **Java** 25
- **[KordEx](https://github.com/Kord-Extensions/kord-extensions)** 2.5.0-SNAPSHOT (basé sur [Kord](https://github.com/kordlib/kord)) pour l'interaction avec l'API Discord
- **Gradle** avec les plugins `dev.kordex.gradle.kordex`, `dev.kordex.gradle.i18n` et KSP
- **SQLite** via `sqlite-jdbc`, embarqué dans le process du bot
- **dotenv-kotlin** pour le chargement de la configuration via `.env`
- **Logback** pour les logs

## Structure du projet

```
src/main/kotlin/
├── main.kt                 # Point d'entrée, connexion DB, enregistrement des extensions
├── entities/                # Entités du domaine (ex: Verification)
├── extensions/               # Extensions KordEx (commandes et événements)
│   ├── AutoCheckAds.kt
│   ├── BannedGuilds.kt
│   ├── DetectSanctions.kt
│   ├── EndMessage.kt
│   ├── Errors.kt
│   ├── ModifySanctions.kt
│   ├── RemoveAds.kt
│   ├── Sanctions.kt
│   ├── UserContextSanctions.kt
│   └── Verifications.kt
├── storage/                 # Accès à la base de données
└── utils/                    # Utilitaires (embeds, snowflakes, invitations, sanctions...)
```

Les traductions se trouvent dans `src/main/resources/translations/rocketmanager/strings.properties` (locale par défaut : français), compilées en code Kotlin via le plugin i18n de KordEx.

## Base de données

Le bot utilise **SQLite**, embarqué dans son propre process : il n'y a pas de serveur de base de données à faire tourner. Le schéma est défini dans `src/main/resources/schema.sql` et appliqué au démarrage, ce qui crée le fichier s'il n'existe pas. Quatre tables :

- `banned_guilds` : serveurs blacklistés (nom, identifiant, raison, date de bannissement)
- `sanctions` : sanctions appliquées (raison, membre, modérateur, durée, type, date)
- `verifications` : suivi des vérifications de publicités (modérateur, date, message)
- `ad_events` : publicités postées, pour les commandes de statistiques

Les tables sont `STRICT` : SQLite refuse une valeur qui ne correspond pas au type déclaré au lieu de la convertir en silence. Les dates sont du texte au format `YYYY-MM-DD HH:MM:SS` en heure locale, seul format que `DATE()` sait grouper.

## Configuration

Le bot se configure via un fichier `.env` à la racine du projet, avec toutes les variables préfixées par `AYFRI_ROCKETMANAGER_` :

| Variable | Description |
| --- | --- |
| `TOKEN` | Token du bot Discord |
| `PREFIX` | Préfixe des commandes textuelles |
| `ENVIRONMENT` | `development` ou autre (active le mode debug) |
| `AUTOMATIC_SANCTIONS` | Active les sanctions automatiques sur publicités invalides |
| `AUTOMATIC_END_MESSAGE` | Active le message de fin de publicité automatique |
| `CHANNEL_SANCTION_ID` | Salon de log des sanctions |
| `CHANNEL_VERIF_ID` | Salon de vérification des publicités |
| `CHANNEL_VERIF_LOGS_ID` | Salon de log des vérifications |
| `DB_PATH` | Chemin du fichier SQLite |

`.env.template` sert de point de départ. Avec Docker, `DB_PATH` est surchargé vers `/app/data/rocketmanager.db`.

## Lancer le bot

### Avec Gradle (développement local)

Nécessite seulement un fichier `.env` complet : la base est créée au premier démarrage.

```bash
./gradlew run
```

Autres tâches utiles :

- `./gradlew build` : build complet
- `./gradlew installDist` : génère la distribution exécutable (utilisée par Docker)

### Avec Docker

```bash
docker compose up --build
```

Cela démarre un seul service, `bot`, buildé en multi-stage à partir de `gradle:9.6.1-jdk25-alpine` puis exécuté sur `eclipse-temurin:25-jre-alpine`.

Les logs sont montés dans `./logs`, et la base vit dans le volume `bot_data`.

## Contribuer

N'hésitez pas à faire des retours ou proposer des améliorations via des commits ou des pull requests.
