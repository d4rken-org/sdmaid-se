---
title: "Recherche de cadavres"
lang: fr
translation_key: corpsefinder
permalink: /fr/corpsefinder
source_hash: 9e16286f0fda1dc8
---
# Recherche de cadavres (CorpseFinder)
{: #corpse-finder }

L’outil `Recherche de cadavres` (CorpseFinder) trouve les fichiers appartenant à des applis qui ne sont plus installées.


## Niveau de risque
{: #risklevel }

Par défaut, toutes les options de niveau de risque sont désactivées. Ne les activez que si vous savez ce que vous faites, et vérifiez d’abord les résultats avant de supprimer quoi que ce soit.
Activer une option de niveau de risque augmente le risque de supprimer des fichiers que vous ne vouliez pas supprimer.

Les niveaux de risque s’appliquent surtout aux emplacements sur liste blanche, comme la racine de votre stockage public (`/storage/emulated/0`). Ces emplacements n’ont pas de schéma de nommage commun, et SD Maid ne peut en déterminer le propriétaire qu’à l’aide d’une base de données tenue à jour manuellement.


### Inclure les résidus souhaitables
{: #include-desirable-remnants }

Les résidus souhaitables sont des fichiers dont le propriétaire est connu mais n’est pas installé, et que SD Maid marque en interne comme `KEEPER`. Il s’agit généralement de fichiers créés par l’utilisateur, par ex. des photos que vous avez prises.

### Inclure les noms courants
{: #include-common-names }

Certaines applis utilisent simplement un nom très courant. Il existe probablement des centaines d’applis de prise de notes qui créent un dossier `/sdcard/Notes` pour y enregistrer leurs données. SD Maid connaît peut-être l’une de ces applis et elle n’est pas installée sur votre téléphone, mais SD Maid ne peut pas être sûr qu’aucune autre appli de votre téléphone n’utilise encore ce dossier sans que SD Maid sache qu’elle s’en sert aussi. 

## Filtre
{: #filter }

Chaque filtre est responsable d’une zone de données précise. En activant un filtre, la Recherche de cadavres cherche aussi dans cette zone supplémentaire.

### Carte SD
{: #sdcard }

* Généralement l’endroit où l’utilisateur place ses fichiers
* Stockage public (intégré comme amovible), à l’exception de certains sous-répertoires dans `Android/data`.
* Les fichiers à cet endroit ne suivent aucun schéma de nommage.

Exemples :

```bash
/storage/emulated/0/SDMaid
/storage/DEAD-BEEF/SDMaid
/sdcard/SDMaid
```

### Médias publics
{: #public-media }

* Contient les médias publics des applis, par ex. les médias reçus via WhatsApp. Les dossiers portent le nom de paquet des applis.
* Sous-répertoire du stockage public ayant une signification particulière pour le système. 

Exemples :

```bash
/storage/emulated/0/Android/media/eu.darken.sdmse
/storage/DEAD-BEEF/Android/media/eu.darken.sdmse
/sdcard/Android/media/eu.darken.sdmse
```

### Données publiques d’applis
{: #public-app-data }

* Fichiers supplémentaires des applis.
* Les dossiers portent le nom de paquet des applis.
* Sous-répertoire du stockage public ayant une signification particulière pour le système.

Exemples :

```bash
/storage/emulated/0/Android/data/eu.darken.sdmse
/storage/DEAD-BEEF/Android/data/eu.darken.sdmse
/sdcard/Android/data/eu.darken.sdmse
```

### Ressources OBB
{: #obb-resources }

* Généralement des fichiers de ressources supplémentaires pour les jeux.
* Les dossiers et fichiers portent le nom de paquet des applis.
* Sous-répertoire du stockage public ayant une signification particulière pour le système.

Exemples :

```bash
/storage/emulated/0/Android/obb/eu.darken.sdmse
/storage/DEAD-BEEF/Android/obb/eu.darken.sdmse
/sdcard/Android/obb/eu.darken.sdmse
```

### Données privées d’applis
{: #private-app-data }

* Les dossiers et fichiers portent le nom de paquet des applis.
* Sous-répertoire du stockage public ayant une signification particulière pour le système.

Exemples :

```bash
/data/data/eu.darken.sdmse
```

### Cache Dalvik
{: #dalvik-cache }

* Contient des versions optimisées du code des applis pour une exécution plus rapide.
* Situé dans la partition système, souvent dans des répertoires propres à chaque architecture.
* Les fichiers sont nommés d’après le nom de paquet de l’appli.

Exemples :

```bash
/data/dalvik-cache/arm64/data@app@eu.darken.sdmse@base.apk@classes.dex
/data/dalvik-cache/arm/data@app@eu.darken.sdmse@base.apk@classes.dex
```

### Bibliothèques des applis
{: #app-libraries }

* Contient les bibliothèques natives utilisées par les applis, comme les fichiers `.so`.
* Généralement situées dans le répertoire de données de l’appli, dans un dossier `lib`.
* Les fichiers sont nommés selon l’architecture et les besoins de la bibliothèque.

Exemples :

```bash
/data/app/eu.darken.sdmse-1/lib/arm64/libexample.so
/data/app/eu.darken.sdmse-2/lib/arm/libexample.so
```

### Sources d’applis
{: #app-sources }

* Désigne les fichiers APK qui servent de code source aux applis.
* Stockés dans des répertoires spécifiques pour les applis système, utilisateur ou constructeur.
* Les fichiers sont nommés d’après le nom de paquet et la version de l’appli.

Exemples :

```bash
/data/app/eu.darken.sdmse-1/base.apk
/system/app/ExampleApp/ExampleApp.apk
```

### Sources privées d’applis
{: #private-app-sources }

* Semblables aux sources d’applis, mais stockées dans des répertoires protégés pour une sécurité renforcée.
* Accessibles uniquement par le système ou l’appli concernée.
* Les fichiers sont nommés d’après le nom de paquet de l’appli.

Exemples :

```bash
/data/app-private/eu.darken.sdmse/base.apk
```
