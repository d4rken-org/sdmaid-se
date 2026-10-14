---
title: "Nettoyeur système"
lang: fr
translation_key: systemcleaner
permalink: /fr/systemcleaner
source_hash: 1ced6df9b00f1e05
---
# Nettoyeur système (SystemCleaner)
{: #system-cleaner }

Le Nettoyeur système (SystemCleaner) est essentiellement un moteur de recherche généraliste. Il parcourt tout le stockage accessible et compare les fichiers et dossiers trouvés à un ensemble de filtres.

Contrairement à d’autres outils comme la Recherche de cadavres ou le Nettoyeur d’applis, il ne connaît pas les applis installées. Il travaille uniquement sur les chemins et les attributs des fichiers. C’est pourquoi seules les exclusions de chemins et de segments peuvent s’appliquer au Nettoyeur système.

Chaque fichier trouvé passe par les filtres activés et est attribué au premier filtre auquel il correspond.

## Paramètres
{: #settings }

### Filtres génériques
{: #general-filters }

Les filtres génériques cherchent **dans toutes les zones de stockage accessibles** certains types de fichiers qui peuvent apparaître n’importe où sur l’appareil.

| Filtre | Description |
|--------|-------------|
| **Dossiers vides** | Dossiers vides de tout l’appareil. Les dossiers imbriqués peuvent nécessiter plusieurs passes d’analyse. |
| **Captures d’écran** | Captures d’écran plus anciennes qu’un âge configurable, provenant de plusieurs applis et emplacements. |
| **Fichiers déplacés vers les corbeilles** | Fichiers dans les corbeilles de diverses applis et emplacements, marqués pour suppression mais pas encore supprimés définitivement. |
| **APK superflus** | Fichiers d’installation (APK) dont la version est antérieure ou égale à celle d’applis déjà installées. |
| **Dossiers LOST.DIR** | Fichiers temporaires créés lors du transfert de données entre appareils Android. |
| **Fichiers Linux** | Fichiers et dossiers créés lorsque le stockage a été connecté à un ordinateur Linux. |
| **Fichiers Mac** | Fichiers et dossiers créés lors de la navigation dans le stockage avec l’appli Finder d’Apple (par ex. `.DS_Store`). |
| **Fichiers Windows** | Données que l’explorateur de fichiers de Windows peut créer sur un stockage connecté (par ex. `Thumbs.db`). |
| **Imagettes** | Aperçus mis en cache pour les images et les vidéos (par ex. les dossiers `.thumbnails`). |
| **Fichiers système temporaires** | Fichiers temporaires liés au système, généralement des caches de courte durée pour des opérations en cours. |
| **Fichiers d’analyse** | Données liées aux statistiques d’utilisation et au suivi des bugs. |
| **Fichiers de publicités** | Fichiers créés pour des publicités ou par celles-ci. |
| **Fichiers journaux du système** | Fichiers journaux du système et des services système (par ex. `*.log`) à divers emplacements. |

### Filtres spécifiques
{: #specific-filters }

Les filtres spécifiques ciblent des **chemins fixes et connus** de l’appareil où certains types de données sont stockés.

| Filtre | Description |
|--------|-------------|
| **Résidus de plantage du système** | Si des processus natifs plantent (par ex. des jeux), des fichiers « tombstone » sont créés ici. |
| **Statistiques d’utilisation** | Fichiers liés aux statistiques d’utilisation des applications. |
| **Erreurs ANR** | Traces d’événements « Application Not Responding » (l’application ne répond pas). |
| **Fichiers temporaires locaux** | Fichiers temporaires liés au système (par ex. d’Android Studio). |
| **Fichiers journaux du système (datalogger)** | Fichiers journaux propres aux services système. |
| **Cache des téléchargements** | Dossier de cache système utilisé à diverses fins (par ex. les mises à jour OTA). |
| **Boîte de dépôt des journaux** | Une zone où le système dépose des fichiers journaux. |
| **Cache du programme d’installation** | Données mises en cache liées aux applis installées, utilisées par la gestion des paquets du système. |
| **Tâches récentes** | Données liées à l’historique des tâches dans le sélecteur de tâches du système. |

### Filtres personnalisés
{: #custom-filters }

En plus des filtres génériques fournis par l’appli, vous pouvez créer des filtres personnalisés avec vos propres critères. Les filtres personnalisés se créent depuis **Paramètres > Nettoyeur système > Gérer les filtres personnalisés**.

> **Attention** : les filtres personnalisés peuvent présenter des risques. Vérifiez toujours les résultats de votre analyse avant de supprimer !

#### Critères disponibles
{: #available-criteria }

| Critère | Description |
|-----------|-------------|
| **Étiquette** | Un nom pour votre filtre |
| **Zones de données** | Emplacements de stockage à analyser (laissez vide pour toutes les zones accessibles) |
| **Types de fichiers** | Cible les fichiers, les dossiers ou les liens symboliques (laissez vide pour tous) |
| **Critères du chemin** | Règles de correspondance des chemins de fichiers |
| **Exclusions** | Chemins à exclure de la correspondance |
| **Critères du nom** | Règles de correspondance des noms de fichiers |
| **Taille minimale** | Ne correspond qu’aux fichiers plus grands que cette valeur |
| **Taille maximale** | Ne correspond qu’aux fichiers plus petits que cette valeur |
| **Âge minimal** | Ne correspond qu’aux fichiers plus anciens que cette valeur |
| **Âge maximal** | Ne correspond qu’aux fichiers plus récents que cette valeur |
| **Regex du chemin** | Avancé : expressions régulières pour la correspondance des chemins |

#### Modes des critères du chemin
{: #path-criteria-modes }

| Mode | Description | Exemple |
|------|-------------|---------|
| **Commence par** | Le chemin commence par le mot-clé | `Downloads/temp` correspond à `/sdcard/Downloads/temp/file.txt` |
| **Contient** | Le chemin comprend le mot-clé | `cache` correspond à `/sdcard/app/cache/data.bin` |
| **Se termine par** | Le chemin se termine par le mot-clé | `backup/old` correspond à `/sdcard/backup/old` |
| **Est égal à** | Le chemin correspond exactement au mot-clé | `Download/temp` ne correspond qu’à ce chemin exact |

#### Modes des critères du nom
{: #name-criteria-modes }

| Mode | Description | Exemple |
|------|-------------|---------|
| **Commence par** | Le nom de fichier commence par le mot-clé | `temp_` correspond à `temp_file.txt` |
| **Contient** | Le nom de fichier comprend le mot-clé | `.bak` correspond à `document.bak.old` |
| **Se termine par** | Le nom de fichier se termine par le mot-clé | `.tmp` correspond à `cache.tmp` |
| **Est égal à** | Le nom de fichier correspond exactement | `thumbs.db` ne correspond qu’à `thumbs.db` |

Par défaut, toutes les correspondances sont **insensibles à la casse**.

#### Zones de données
{: #data-areas }

Zones courantes que vous pouvez cibler :

| Zone | Description |
|------|-------------|
| **SDCARD** | Stockage principal (interne ou carte SD) |
| **PUBLIC_MEDIA** | Dossiers de médias (DCIM, Pictures, Music, etc.) |
| **PUBLIC_DATA** | Données d’applis partagées |
| **PRIVATE_DATA** | Stockage privé des applis (nécessite le root) |
| **DOWNLOAD_CACHE** | Cache des téléchargements du système (nécessite le root) |
| **PORTABLE** | Stockage amovible (USB/microSD) |

#### Conseils
{: #tips }

- **Performances** : ne définissez que les critères nécessaires. Trop de motifs regex ralentissent l’analyse.
- **Plusieurs critères** : tous les critères indiqués doivent correspondre (logique ET).
- **Appui long** : appuyez longuement sur les entrées pour afficher des options supplémentaires.
- **Tests** : utilisez l’aperçu de l’analyse avant d’activer la suppression.
- **Importation/exportation** : les filtres peuvent être exportés et partagés avec d’autres utilisateurs.

#### Options obsolètes
{: #deprecated-options }

L’option `rootOnly` de SD Maid 1 **n’est plus disponible**. Utilisez plutôt les **Zones de données** pour contrôler quels emplacements de stockage sont analysés.
