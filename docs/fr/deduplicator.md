---
title: "Déduplicateur"
lang: fr
translation_key: deduplicator
permalink: /fr/deduplicator
source_hash: 08f0bb39baf9fb1e
---
# Déduplicateur (Deduplicator)
{: #deduplicator }

Le Déduplicateur (Deduplicator) trouve les fichiers en double sur votre appareil : les copies exactes et, en option, les images, vidéos ou fichiers audio qui se ressemblent visuellement ou à l’écoute. Il les regroupe en ensembles et supprime tous les fichiers de chaque ensemble sauf un.

L’analyse est gratuite. La suppression nécessite la mise à niveau.

## Méthodes de détection
{: #detection-methods }

Vous pouvez activer chaque méthode sous `Paramètres > Déduplicateur`.

### Somme de contrôle du contenu
{: #content-checksum }

Activée par défaut. Trouve les fichiers dont le contenu est exactement identique, octet par octet, quel que soit leur nom. C’est la méthode sûre : chaque fichier d’un tel ensemble est une véritable copie.

### Hachage perceptuel
{: #perceptual-hash }

Désactivé par défaut. Trouve les images qui se ressemblent, par ex. la même photo enregistrée dans un autre format ou une autre taille, ou avec de petites retouches. Les images sont comparées en niveaux de gris, si bien que des images qui ne diffèrent que par la couleur peuvent correspondre.

### Empreinte numérique du média
{: #media-fingerprint }

Désactivée par défaut. Trouve les vidéos et fichiers audio dont le son se ressemble, ainsi que les vidéos muettes dont l’image se ressemble. Seuls les fichiers de durée à peu près identique sont comparés.

Des fichiers similaires ne sont pas identiques. Des photos en rafale, des versions retouchées ou des copies redimensionnées peuvent se retrouver dans le même ensemble. **Vérifiez les ensembles trouvés par hachage perceptuel ou empreinte numérique du média avant de les supprimer.** Chaque fichier similaire indique son degré de correspondance.

## Ce qui est analysé
{: #what-gets-scanned }

Par défaut, le Déduplicateur cherche dans tout le stockage accessible : stockage interne, cartes SD, stockage USB, `Android/data` et `Android/media`. `Android/obb` n’est pas inclus. Vous pouvez limiter la recherche sous [Chercher dans les emplacements](#search-locations).

Sous Android 13 et versions ultérieures, l’accès à `Android/data` nécessite le [root](/fr/setup#root-access) ou l’[accès ADB](/fr/setup#shizuku).

Seuls les fichiers dépassant la [taille minimale](#minimum-size) sont pris en compte. Par défaut, seuls les types de fichiers courants sont inclus, voir [Ignorer les types de fichier inhabituels](#skip-uncommon-file-types).

## Quel fichier est conservé
{: #which-file-is-kept }

Lors de la suppression d’un ensemble, SD Maid conserve un fichier et supprime les autres. Le fichier conservé est déterminé par la stratégie de suppression sous `Paramètres > Déduplicateur > Stratégie de suppression`.

SD Maid examine chaque règle dans l’ordre. La première règle qui désigne clairement un gagnant l’emporte. Faites glisser les règles pour les réorganiser, appuyez sur une règle pour changer son mode.

| Règle | Par défaut |
|---|---|
| Type de doublon | Conserver les correspondances exactes plutôt que les fichiers similaires |
| Emplacements préférés | Conserver les fichiers des dossiers que vous choisissez. Aucun dossier n’est défini par défaut. |
| Indexation des médias | Conserver les fichiers que votre galerie et les autres applis multimédias connaissent |
| Emplacement de stockage | Conserver les fichiers du stockage interne. Les cartes SD et le stockage USB comptent comme externes. |
| Profondeur des dossiers | Conserver les fichiers des dossiers peu imbriqués |
| Date de modification | Conserver les fichiers les plus anciens |
| Taille du fichier | Conserver les fichiers les plus volumineux |

**Emplacements préférés** aide à choisir quel fichier est conservé au sein d’un groupe, en combinaison avec les autres règles. Cette règle ne choisit pas entre les groupes. Dans un ensemble contenant à la fois des copies exactes et des fichiers similaires, Type de doublon décide quel groupe est conservé : par défaut, tous les fichiers du groupe similaire sont supprimés, même s’ils se trouvent dans un emplacement préféré. Par exemple, si votre photo prise avec l’appareil photo et deux copies identiques redimensionnées provenant d’une messagerie se retrouvent dans un même ensemble, le réglage par défaut conserve une copie de la messagerie et supprime l’original de l’appareil photo. Vérifiez ce type d’ensemble avant de le supprimer, et [excluez](/fr/exclusions) les dossiers dont les fichiers ne doivent jamais être supprimés.

Il n’est pas possible de désigner un fichier précis comme celui à conserver. Pour conserver un autre fichier, modifiez la stratégie de suppression, ou sélectionnez vous-même les fichiers à supprimer.

Modifier la stratégie de suppression efface les résultats actuels. Relancez ensuite l’analyse.

## Suppression
{: #deleting }

### Supprimer un ensemble entier
{: #deleting-a-whole-set }

Supprimer un ensemble conserve un fichier et supprime **tous les autres fichiers de cet ensemble**. Dans un ensemble contenant des fichiers similaires, cela inclut les fichiers qui sont seulement similaires au fichier conservé, et non identiques.

Passez la liste des résultats en affichage liste pour voir une icône de suppression sur chaque fichier que la suppression de l’ensemble entier retirerait. L’affichage en grille par défaut ne montre pas ces icônes.

### Supprimer des fichiers individuels
{: #deleting-individual-files }

Sélectionnez des fichiers par un appui long et supprimez-les. Au moins un fichier par ensemble doit rester, sauf si vous activez [Rendre « Tout effacer » possible](#make-delete-all-possible).

Dans les détails d’un ensemble, la vue `Dossiers` regroupe les fichiers par dossier. Supprimer un dossier à cet endroit supprime les doublons qu’il contient. Si le dossier contient tous les fichiers d’un ensemble, le fichier que conserverait la suppression de l’ensemble entier est laissé de côté. Si [Rendre « Tout effacer » possible](#make-delete-all-possible) est activé, la confirmation vous permet de tous les supprimer.

### Supprimer depuis le tableau de bord
{: #deleting-from-the-dashboard }

Supprimer depuis la carte du Déduplicateur sur le tableau de bord supprime tous les fichiers de chaque ensemble sauf un, après confirmation. Le fait que le bouton principal du tableau de bord exécute aussi le Déduplicateur se règle sous [Accès direct](#one-tap).

## Exclusions
{: #exclusions }

Les chemins exclus pour le Déduplicateur, ou de manière générale, ne sont pas analysés. Exclure un ensemble des résultats exclut tous les fichiers qu’il contient, y compris celui qui aurait été conservé. Voir [Exclusions](/fr/exclusions).

Le Déduplicateur ne peut pas être exécuté par le [Programmateur](/fr/scheduler).

## Accès direct
{: #one-tap }

`Paramètres > Général > Outils à accès direct` détermine si le bouton principal du tableau de bord, le raccourci `Analyser + supprimer` et le widget incluent le Déduplicateur. C’est désactivé par défaut. Le raccourci et le widget analysent et suppriment sans confirmation, en appliquant votre stratégie de suppression. Sur le tableau de bord, `Mode d’accès direct` fait de même, mais si des résultats sont déjà en attente, Supprimer demande quand même une confirmation. N’incluez le Déduplicateur que si [Somme de contrôle du contenu](#content-checksum) est la seule méthode de détection.

## Paramètres
{: #settings }

### Chercher dans les emplacements
{: #search-locations }

Les dossiers dans lesquels chercher. Par défaut, toutes les zones accessibles. Appuyez longuement pour le réinitialiser.

### Stratégie de suppression
{: #deletion-strategy }

Quel fichier d’un ensemble est conservé, voir [Quel fichier est conservé](#which-file-is-kept).

### Rendre « Tout effacer » possible
{: #make-delete-all-possible }

Affiche des options permettant de supprimer tous les fichiers d’un ensemble, de sorte qu’aucune copie ne reste. Désactivé par défaut.

### Taille minimale
{: #minimum-size }

Ignore les fichiers en dessous de cette taille. 512 Ko par défaut.

### Ignorer les types de fichier inhabituels
{: #skip-uncommon-file-types }

N’inclut que les images, vidéos, fichiers audio, documents, fichiers compressés et fichiers APK courants. Activé par défaut. Le type est déterminé par l’extension du fichier.

### Méthode de détection
{: #detection-method }

Active [Somme de contrôle du contenu](#content-checksum), [Hachage perceptuel](#perceptual-hash) et [Empreinte numérique du média](#media-fingerprint).
