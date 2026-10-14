---
title: "Balayeur"
lang: fr
translation_key: swiper
permalink: /fr/swiper
source_hash: 4c7d8b7a30d3a36e
---
# Balayeur (Swiper)
{: #swiper }

Le Balayeur (Swiper) vous aide à désencombrer des dossiers précis plus vite qu’un explorateur de fichiers. Vous passez les fichiers en revue un par un et balayez chacun pour le conserver, le supprimer ou l’ignorer. Rien n’est supprimé tant que vous n’avez pas révisé vos choix et confirmé.

## Démarrer une session
{: #starting-a-session }

1. Ouvrez le Balayeur depuis le tableau de bord et appuyez sur `Choisir des dossiers`. Vous pouvez choisir des dossiers, des fichiers individuels, ou les deux.
2. Si vous le souhaitez, définissez un filtre de type de fichier et un ordre de tri sur la carte de la session.
3. Appuyez sur `Analyser`. Les dossiers sont parcourus avec tous leurs sous-dossiers.
4. Appuyez sur `Commencer à balayer`.

Vous pouvez aussi créer une session depuis l’[Analyseur de mémoire](/fr/storageanalyzer) : sélectionnez-y des éléments et choisissez `Créer une session Balayeur`.

Votre pellicule photo, le dossier Downloads et les captures d’écran sont de bons points de départ.

### Types de fichiers
{: #file-types }

`N’afficher que ces types de fichiers` limite la session aux images, vidéos, sons, documents ou fichiers compressés. Vous pouvez aussi saisir vos propres extensions, par ex. `apk, apks`. Si rien n’est coché, tous les fichiers sont affichés. Les fichiers sont reconnus uniquement par leur extension.

### Ordre de tri
{: #sort-order }

Par défaut, les fichiers sont affichés du plus ancien au plus récent, d’après leur date de dernière modification. Vous pouvez aussi les trier du plus récent au plus ancien, par nom, ou du plus grand au plus petit.

Le filtre et l’ordre de tri ne peuvent plus être modifiés une fois qu’une analyse a trouvé des fichiers. Pour les changer, démarrez une nouvelle session.

### Analyser un stockage entier
{: #scanning-a-whole-storage }

Si vous choisissez le niveau supérieur d’un stockage, par ex. l’ensemble de votre stockage interne, chaque fichier qu’il contient se retrouve dans la session, photos et médias des applis compris. SD Maid vous demande de confirmer avant l’analyse. Lorsque vous supprimez ensuite des fichiers d’une telle session, vous devez d’abord cocher `Je comprends que ces fichiers seront supprimés définitivement.`

## Balayer
{: #swiping }

| Geste | Action par défaut |
|---|---|
| Balayer vers la droite | Conserver |
| Balayer vers la gauche | Supprimer |
| Balayer vers le haut | Ignorer |
| Balayer vers le bas | Annuler la dernière décision |

Les boutons en bas font la même chose. Lorsque [Inverser le sens de balayage](#swap-swipe-directions) est activé, la gauche et la droite sont inversées.

* **Ignorer** laisse un fichier sans décision. Les fichiers ignorés reviennent après tous les autres.
* **Annuler** revient en arrière sur vos dernières décisions, jusqu’à 50.
* **Appuyez longuement sur Ignorer** pour exclure un fichier des futures analyses du Balayeur. Cela crée une [exclusion](/fr/exclusions) de chemin et retire le fichier de la session.
* Les boutons de la carte ouvrent le fichier dans une autre appli ou affichent un aperçu en plein écran.
* Appuyez sur une miniature dans le bandeau pour accéder directement à ce fichier. Le balayer à nouveau remplace votre décision précédente.

Vous pouvez quitter à tout moment. Vos décisions sont enregistrées avec la session et vous pouvez reprendre plus tard. Au redémarrage de SD Maid, les fichiers qu’il ne trouve plus sont retirés de la session. D’ici là, la session peut encore lister des fichiers supprimés ou déplacés par autre chose.

## Réviser et supprimer
{: #reviewing-and-deleting }

Appuyez sur `Réviser` pour voir toutes les décisions. Lorsqu’il ne reste plus de fichiers sans décision, la révision s’ouvre d’elle-même.

* Modifiez les décisions fichier par fichier, ou appuyez longuement pour sélectionner plusieurs fichiers à la fois.
* Appuyez sur un fichier pour y revenir.
* `Supprimer` indique combien de fichiers et quelle quantité d’espace sont concernés, et dans quels dossiers, avant que quoi que ce soit ne se passe.

La suppression est définitive. Les fichiers ne sont pas déplacés dans une corbeille et SD Maid ne peut pas les restaurer.

Après la suppression, les fichiers conservés et supprimés quittent la session. Les fichiers sans décision restent pour plus tard. Une fois qu’il ne reste plus rien, la session est supprimée. Si certains fichiers n’ont pas pu être supprimés, ils restent dans la session et vous pouvez réessayer.

Supprimer une session ne fait qu’effacer vos décisions. Cela ne touche à aucun fichier.

### Bon à savoir
{: #things-to-know }

* **Conserver ne s’applique qu’à la session en cours.** Si vous démarrez une nouvelle session sur le même dossier, les fichiers conservés réapparaissent. Pour masquer un fichier définitivement, excluez-le (appui long sur Ignorer).
* **Une session ne prend pas en compte les nouveaux fichiers.** Les fichiers ajoutés au dossier après l’analyse n’apparaissent pas. Démarrez une nouvelle session pour les inclure.
* **Les fichiers compressés incluent les fichiers APK.**
* **Les fichiers cachés sont inclus.**

## Version gratuite
{: #free-version }

Sans la mise à niveau, le Balayeur est limité à 2 sessions de 50 fichiers chacune. Si un ordre de tri est défini, vous obtenez les 50 premiers fichiers dans cet ordre.

## Paramètres
{: #settings }

### Inverser le sens de balayage
{: #swap-swipe-directions }

Balayez vers la gauche pour conserver, vers la droite pour supprimer. Désactivé par défaut.

### Afficher les détails des fichiers
{: #show-file-details }

Affiche le nom, la taille, la date et le dossier du fichier sur l’écran de balayage. Activé par défaut.

### Retour haptique
{: #haptic-feedback }

Vibre lorsque vous conservez ou supprimez un fichier. Activé par défaut.
