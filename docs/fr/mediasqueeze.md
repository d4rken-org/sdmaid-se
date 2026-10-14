---
title: "Compression des médias"
lang: fr
translation_key: mediasqueeze
permalink: /fr/mediasqueeze
source_hash: 1d878fd5a77634c5
---
# Compression des médias (Media Squeeze)
{: #media-squeeze }

La Compression des médias (Media Squeeze) compresse les photos et les vidéos pour qu’elles prennent moins de place. Vous sacrifiez un peu de qualité en échange de l’espace récupéré.

**La compression réduit définitivement la qualité et ne peut pas être annulée.** Le fichier original est remplacé et aucune copie n’est conservée. Vérifiez vos paramètres et l’aperçu avant de commencer.

L’analyse est gratuite. La compression nécessite la mise à niveau.

## Fonctionnement
{: #how-it-works }

1. Choisissez les dossiers dans lesquels chercher. Il n’y a pas d’emplacement par défaut : choisissez par ex. le dossier de votre appareil photo.
2. Définissez la qualité de compression et l’âge minimal.
3. Lancez l’analyse. Chaque résultat indique sa taille et une estimation du gain.
4. Appuyez sur un élément, sélectionnez-en plusieurs par un appui long, ou utilisez `Tout compresser`. Une confirmation affiche d’abord la taille totale et le gain estimé.

Si la sélection ne contient que des photos ou que des vidéos, `Afficher la comparaison` dans la confirmation présente côte à côte l’original et la version compressée. Pour les vidéos, la comparaison porte sur une seule image et n’est qu’approximative.

### Ce qu’il advient d’un fichier
{: #what-happens-to-a-file }

* Il est réencodé dans son propre format : un JPEG reste un JPEG, un WebP reste un WebP, un MP4 reste un MP4.
* La résolution des images reste la même, sauf pour les [photos très volumineuses](#compress-very-large-photos).
* Pour les vidéos, seule la piste vidéo est réencodée avec un débit plus faible. La piste audio est copiée sans modification.
* La date, le lieu, l’appareil photo et les autres métadonnées EXIF des photos sont conservés. Si SD Maid ne peut pas lire ou préserver les métadonnées EXIF d’une photo, il laisse la photo inchangée.
* Les métadonnées XMP ne sont pas conservées, par ex. les informations de photo sphère et de panorama ou les notes attribuées par les éditeurs photo.
* La date de modification du fichier est conservée, pour que les galeries continuent de le classer au même endroit.
* Si la version compressée n’est pas plus petite, l’original n’est pas modifié.

Le gain estimé est une approximation grossière fondée sur le type de fichier et la qualité, pas une mesure. Le gain réel peut être différent.

### Emplacements
{: #locations }

La Compression des médias a besoin d’un accès direct aux fichiers et d’un accès en écriture à leurs dossiers. Elle n’utilise ni le root ni ADB. Certains emplacements choisis via le sélecteur de dossiers d’Android, par ex. les clés USB, ne peuvent pas être utilisés et sont retirés de la sélection.

## Fichiers déjà compressés
{: #previously-compressed-files }

SD Maid se souvient des fichiers qu’il a déjà compressés, et de ceux qui ne sont pas devenus plus petits, et les ignore lors des analyses suivantes. Il reconnaît un fichier à son contenu, ce qui fonctionne donc aussi après que le fichier a été renommé ou déplacé. Compresser de nouveau un fichier coûte davantage de qualité et fait gagner peu d’espace.

L’historique est conservé sur votre appareil et fait partie de la sauvegarde des paramètres de SD Maid. L’effacer rend les fichiers déjà compressés de nouveau éligibles, sauf s’ils portent un marqueur EXIF et que [Écrire le marqueur EXIF](#write-exif-marker) est activé. L’effacer ne modifie aucun fichier.

## Photos ignorées par défaut
{: #photos-that-are-skipped-by-default }

Certaines photos contiennent des données que la compression ne peut pas conserver. SD Maid les ignore s’il détecte ces données, sauf si vous activez le paramètre correspondant. La détection n’est pas exhaustive : les photos dans des formats qu’il ne reconnaît pas sont compressées et perdent ces données.

* **Photos HDR et de profondeur** : les photos HDR deviennent des photos normales, et les photos en mode portrait perdent les données à l’origine du flou d’arrière-plan.
* **Motion Photos** : l’image fixe est conservée, le clip vidéo est supprimé.
* **Photos très volumineuses** (8192 pixels ou plus en largeur ou en hauteur) : leur largeur et leur hauteur sont divisées par deux, et de nouveau si nécessaire, jusqu’à ce que les deux soient inférieures à 8192 pixels. Un panorama de 20 000 pixels de large finit à 5 000 pixels de large.

Les fichiers HEIC contenant plusieurs images sont toujours ignorés.

Le résumé de l’analyse compte les photos ignorées en raison de données HDR ou de profondeur, les Motion Photos et les photos très volumineuses, ainsi que les fichiers inaccessibles. Les fichiers ignorés parce qu’ils ont déjà été compressés ne sont pas comptés, pas plus que les fichiers HEIC à images multiples, sauf s’ils contiennent aussi des données HDR ou de profondeur.

## Paramètres
{: #settings }

La qualité de compression, les emplacements de recherche et l’âge minimal se règlent sur l’écran de la Compression des médias avant l’analyse. Tout le reste se trouve sous `Paramètres > Compression des médias`.

### Qualité de compression
{: #compression-quality }

80 par défaut. Une qualité plus faible donne des fichiers plus petits mais une qualité visuelle réduite. En dessous de 40, les images peuvent devenir inutilisables. À partir de 95, il y a peu à gagner.

### Âge minimal
{: #minimum-age }

N’inclut que les fichiers plus anciens que cette valeur. 90 jours par défaut, d’après la date de modification du fichier.

### Taille minimale des fichiers
{: #minimum-file-size }

Ignore les fichiers plus petits que cette valeur. 512 Ko par défaut.

### JPEG
{: #jpeg }

Inclut les images JPEG (`.jpg`, `.jpeg`). Activé par défaut.

### WebP
{: #webp }

Inclut les images WebP (`.webp`). Activé par défaut. Les fichiers WebP animés ne sont pas détectés et perdraient leur animation.

### HEIC/HEIF
{: #heicheif }

Inclut les images HEIC/HEIF (`.heic`, `.heif`). Désactivé par défaut. Nécessite Android 9 ou version ultérieure. Les gain maps HDR, les données de profondeur et les parties Live/Motion Photo ne peuvent pas être préservées pour le format HEIC.

### Vidéo MP4
{: #mp4-video }

Inclut les vidéos MP4 (`.mp4`). Désactivé par défaut. Compresser une vidéo nécessite un espace libre d’environ 110 % de sa taille sur le même stockage.

### Compresser les photos HDR et de profondeur
{: #compress-hdr--depth-photos }

Voir [Photos ignorées par défaut](#photos-that-are-skipped-by-default). Désactivé par défaut.

### Compresser les Motion Photos
{: #compress-motion-photos }

Conserve l’image fixe et supprime le clip vidéo. Désactivé par défaut.

### Compresser les photos très volumineuses
{: #compress-very-large-photos }

Inclut les photos de 8192 pixels ou plus en largeur ou en hauteur, et réduit leur résolution, voir [Photos ignorées par défaut](#photos-that-are-skipped-by-default). Désactivé par défaut.

### Ignorer les images déjà compressées
{: #skip-previously-compressed }

Ignore les fichiers que SD Maid a déjà compressés. Activé par défaut.

### Écrire le marqueur EXIF
{: #write-exif-marker }

Ajoute un marqueur au commentaire utilisateur EXIF de la photo, pour que SD Maid la reconnaisse comme compressée même après l’effacement de l’historique de compression. SD Maid ne recherche le marqueur que lorsque ce paramètre est activé. Désactivé par défaut. Ne s’applique qu’aux formats JPEG et WebP.

### Historique de compression
{: #compression-history }

Indique combien de fichiers figurent dans l’historique. Appuyez dessus pour effacer l’historique.

## Exclusions
{: #exclusions }

La Compression des médias ignore les chemins [exclus](/fr/exclusions) de manière générale. Vous pouvez aussi exclure des résultats depuis la liste des résultats, ce qui n’affecte que la Compression des médias.

La Compression des médias ne peut pas être exécutée par le [Programmateur](/fr/scheduler).
