---
title: "Analyseur de mémoire"
lang: fr
translation_key: storageanalyzer
permalink: /fr/storageanalyzer
source_hash: 2dbf8557900cc687
---
# Analyseur de mémoire (StorageAnalyzer)
{: #storage-analyzer }

L’Analyseur de mémoire (StorageAnalyzer) répond à la question « Qu’est-ce qui prend autant de place sur mon stockage ? »

Il peut ressembler à un explorateur de fichiers, mais ce n’en est pas un. Tout est trié par taille, ce qui vous permet de suivre les éléments les plus volumineux jusqu’à ce qui remplit réellement votre stockage.

## Stockages
{: #storages }

Le premier écran affiche chaque stockage de votre appareil :

* **Mémoire principale** : le stockage intégré de votre appareil, applis et données système comprises.
* **Mémoire secondaire** : généralement une carte SD amovible.
* **Support de stockage amovible** : par ex. une clé USB connectée par USB.

Chaque carte affiche l’espace libre et utilisé, ainsi que l’évolution du stockage au cours des 7 derniers jours, voir [Tendance de stockage](#storage-trend).

## Catégories
{: #categories }

Ouvrir un stockage lance son analyse et répartit l’espace utilisé en catégories :

* **Applis** : les applis installées avec leur code, leurs données, leurs caches et leurs médias.
* **Fichiers utilisateur** : photos, vidéos, téléchargements et toutes les autres données qui n’appartiennent pas à une appli précise.
* **Données système** : le système d’exploitation et tout ce qui n’a pas pu être attribué ailleurs.
* **Autres utilisateurs** : les applis et fichiers d’autres utilisateurs ou de profils professionnels sur cet appareil.

Données système et Autres utilisateurs n’existent que pour la mémoire principale.

### Applis
{: #apps }

Les applis sont triées par taille et peuvent être recherchées par nom. Les applis système peuvent aussi être listées ici, avec leurs données. Pour les applis système sans mise à jour installée, le code intégré de l’appli compte comme Données système. Ouvrir une appli montre où va son espace :

* **Code de l’appli** : l’appli elle-même. Désinstaller ou archiver l’appli libère cet espace.
* **Données de l’appli** : données, paramètres et caches.
* **Médias de l’appli** : fichiers multimédias de cette appli, par ex. les photos WhatsApp reçues.
* **Fichiers d’appli supplémentaires** : données que l’appli a placées en dehors de ses propres dossiers, par ex. des photos ou des téléchargements dans un dossier de premier niveau.

`Afficher les paramètres` ouvre la page des paramètres système de l’appli.

### Pourquoi les chiffres ne correspondent pas aux paramètres d’Android
{: #why-the-numbers-dont-match-androids-settings }

Les catégories de l’analyseur ne correspondent pas exactement à la répartition affichée dans les paramètres d’Android.

**Données système** correspond à ce qui reste de l’espace utilisé une fois Applis, Fichiers utilisateur et Autres utilisateurs comptés. Tout ce que SD Maid ne peut pas voir ou attribuer s’y retrouve : zones système protégées, stockage d’autres utilisateurs qui n’a pas pu être mesuré, surcoût du système de fichiers, et la différence entre la taille annoncée et la taille réelle du stockage.

Sans les autorisations [Inventaire des applis](/fr/setup#app-inventory) et [Statistiques d’utilisation](/fr/setup#usage-statistics), les applis ne peuvent pas être mesurées. Leur espace apparaît alors comme Données système.

## Accès
{: #access }

Ce que l’analyseur peut voir dépend de l’accès dont dispose SD Maid :

* **Sans root**, le code des applis et les données privées des applis ne sont affichés que sous forme de totaux et ne peuvent pas être parcourus. Sous Android 13 et versions ultérieures, `Android/data` nécessite aussi le root ou l’accès ADB.
* **Avec le [root](/fr/setup#root-access) ou l’[accès ADB](/fr/setup#shizuku)**, vous pouvez parcourir les Données système. Le root permet aussi de parcourir le code des applis et les données privées des applis, et de mesurer entièrement les autres utilisateurs.
* **Sans l’autorisation Inventaire des applis**, les fichiers ne peuvent pas être associés aux applis auxquelles ils appartiennent. Les Fichiers utilisateur sont alors affichés en lecture seule : vous pouvez voir les tailles, mais la suppression, l’exclusion, la création de filtres et les sessions Balayeur sont désactivées.

Les éléments auxquels SD Maid ne peut pas accéder affichent `Aucun accès direct n’est proposé` lorsqu’on appuie dessus.

Les dossiers de plus de 100 000 éléments sont seulement totalisés et ne peuvent pas être parcourus.

## Actions
{: #actions }

Appuyez sur un fichier pour l’ouvrir dans une autre appli. Appuyez longuement sur des éléments pour les sélectionner, puis :

* **Supprimer** : supprime les éléments sélectionnés après confirmation. Gratuit.
* **Exclure** : crée une [exclusion](/fr/exclusions) pour les chemins sélectionnés. Ces exclusions s’appliquent à tous les outils. L’analyseur lui-même n’utilise pas les exclusions, car il doit tout voir pour calculer des totaux corrects.
* **Filtrer** : crée un [filtre personnalisé du Nettoyeur système](/fr/systemcleaner#custom-filters) pour les éléments sélectionnés. Nécessite la mise à niveau.
* **Créer une session Balayeur** : lance une session du [Balayeur](/fr/swiper) avec les éléments sélectionnés.

Les Données système et le contenu des autres utilisateurs peuvent seulement être parcourus. Il n’est pas possible d’y supprimer quoi que ce soit.

## Tendance de stockage
{: #storage-trend }

SD Maid enregistre l’espace libre de vos stockages à quelques heures d’intervalle et après chaque nettoyage. `Tendance de stockage` l’affiche sous forme de graphique, avec des repères indiquant combien chaque nettoyage a libéré.

* Les cartes de stockage indiquent l’évolution de chaque stockage au cours des 7 derniers jours.
* Le tableau de bord affiche l’évolution combinée de tous les stockages ou, à la place, une estimation du nombre de jours restants lorsque le stockage se remplit.
* La durée de conservation des relevés se règle sous `Paramètres > Historique > Historique du stockage`. 90 jours par défaut, jusqu’à 365.

Le graphique Tendance de stockage nécessite la mise à niveau. L’évolution sur 7 jours sur les cartes de stockage et le tableau de bord, ainsi que l’estimation des jours restants, sont gratuites.

## Paramètres
{: #settings }

### Seuil de faible stockage
{: #low-storage-threshold }

Lorsque l’espace libre descend sous ce seuil, SD Maid considère que le stockage arrive à saturation. Par défaut `Automatique` : 5 % de la taille du stockage, 2 Go au maximum.

### Avertissement de faible espace
{: #low-space-warning }

Nécessite la mise à niveau. Avertit lorsque l’espace libre de votre mémoire principale atteint le seuil de faible stockage. Avec au moins 5 jours d’historique cohérent, il peut aussi avertir plus tôt : lorsque le stockage est en voie d’atteindre le seuil dans les 14 jours et que l’espace libre ne représente déjà plus que 20 % du stockage au maximum, plafonné à 25 Go. Désactivé par défaut.
