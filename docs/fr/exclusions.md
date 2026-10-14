---
title: "Exclusions"
lang: fr
translation_key: exclusions
permalink: /fr/exclusions
source_hash: d5c3aea2efa43796
---
# Exclusions
{: #exclusions }

La fonction d’exclusion de SD Maid 2/SE permet de définir des dossiers ou des fichiers précis que l’appli doit ignorer lors des analyses du système et des tâches de maintenance. C’est utile si vous avez des données que les processus de l’appli ne doivent pas toucher. Lorsque vous définissez des exclusions, SD Maid 2/SE ignore ces répertoires ou fichiers lors de tâches comme la Recherche de cadavres (CorpseFinder), le Nettoyeur d’applis (AppCleaner) et les autres fonctions de gestion du système.

Le moyen le plus simple d’ajouter une exclusion est de le faire directement depuis les résultats. Vous pouvez soit utiliser le bouton « Exclure » sur des éléments individuels, soit appuyer longuement sur des entrées ou en sélectionner plusieurs, puis choisir l’option « Exclure ». Les exclusions peuvent aussi être ajoutées manuellement dans les paramètres de l’appli en indiquant des chemins ou des motifs avec caractères génériques, pour plus de contrôle.

Les exclusions peuvent être exportées et importées depuis le menu. L’importation d’exclusions n’est prise en charge qu’entre des versions proches de SD Maid. S’il y a trop de mises à jour entre la version d’importation et celle d’exportation, une erreur peut se produire. Dans ce cas, vous pouvez essayer d’importer sur une version antérieure, puis exporter de nouveau les exclusions.

<img src="/assets/images/exclusions/overview-1.webp" width="150">
<img src="/assets/images/exclusions/overview-2.webp" width="150">
<img src="/assets/images/exclusions/overview-3.webp" width="150">
<img src="/assets/images/exclusions/overview-4.webp" width="150">

## Types
{: #types }

SD Maid prend en charge 3 types d’exclusions différents :

### Exclusion d’applis
{: #app-exclusions }

Les exclusions d’applis reposent sur le nom de paquet de chaque appli. Elles peuvent être créées via l’outil Vérificateur d’applis (AppControl). Il suffit de choisir une appli dans la liste et d’utiliser l’action `Exclure` correspondante.

### Exclusion de chemins
{: #path-exclusions }

Les exclusions de chemins peuvent être créées en sélectionnant (par un appui long) des chemins dans l’outil Analyseur de mémoire (StorageAnalyzer).

### Exclusion de segments 
{: #segment-exclusions }
Les exclusions de segments sont un type d’exclusion de chemins qui ne nécessite pas de correspondance exacte. Un segment est une partie d’un chemin, comme un dossier. Le chemin `/sdcard/DCIM/Camera` se compose de 3 segments. Vous pourriez exclure toutes vos photos d’appareil photo à la fois sur votre stockage interne (`/storage/emulated/0/DCIM/Camera`) et sur votre stockage externe (`/sdcard/DCIM/Camera`) en créant une exclusion de segments pour `DCIM/Camera`.

Si vous cochez l’option `Autoriser les correspondances partielles`, vous pourriez créer une exclusion pour `DCIM/Cam` qui correspondrait à la fois au dossier `Cam` et au dossier `Camera`.

## Outils
{: #tools }

Chaque outil de SD Maid a une vision différente des données. Toutes les exclusions n’affectent pas tous les outils de la même façon.

### Recherche de cadavres
{: #corpsefinder }

La Recherche de cadavres sait ce qu’est une « appli » et quels fichiers appartiennent à une appli. Elle respecte tous les types d’exclusions. Créer une exclusion d’appli signifie que tous les fichiers trouvés par la Recherche de cadavres et qu’elle attribue à une appli exclue seront exclus.

Types d’exclusions pris en charge : `APP`, `PATH`, `SEGMENTS`

### Nettoyeur système
{: #systemcleaner }

Le Nettoyeur système (SystemCleaner) ne travaille que sur des données de chemins. Il ne sait pas ce qu’est une « appli » ni quels fichiers appartiennent à une appli.

Types d’exclusions pris en charge : `PATH`, `SEGMENTS`

### Nettoyeur d’applis
{: #appcleaner }

Le Nettoyeur d’applis respecte tous les types d’exclusions. Vous pouvez utiliser des exclusions pour accélérer les analyses s’il y a des applis comportant de nombreux fichiers à analyser.

Types d’exclusions pris en charge : `APP`, `PATH`, `SEGMENTS`

### Déduplicateur
{: #deduplicator }

Le Déduplicateur (Deduplicator) ne sait pas ce que sont les « applis » et ne travaille qu’avec des données de fichiers.

Types d’exclusions pris en charge : `PATH`, `SEGMENTS`

### Compression des médias
{: #media-squeeze }

La Compression des médias (Media Squeeze) ne travaille qu’avec des données de fichiers. Les fichiers exclus ne sont pas compressés.

Types d’exclusions pris en charge : `PATH`, `SEGMENTS`

### Balayeur
{: #swiper }

Le Balayeur (Swiper) ne travaille qu’avec des données de fichiers. Les fichiers exclus ne sont pas inclus dans les nouvelles sessions. Un appui long sur `Ignorer` pendant le balayage exclut le fichier actuel.

Types d’exclusions pris en charge : `PATH`, `SEGMENTS`

### Analyseur de mémoire
{: #storageanalyzer }

L’Analyseur de mémoire ne prend pas en charge les exclusions, car tout le contenu doit être analysé pour calculer correctement les tailles totales.
