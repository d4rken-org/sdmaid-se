---
title: "Bugs"
lang: fr
translation_key: bugs
permalink: /fr/bugs
source_hash: a218be31a6c4ae63
---
# Bugs
{: #bugs }

🐌 🦋 🐛 🐜 🐝 🐞

Les bugs font partie de la vie d’une appli. Ils ne vous veulent aucun mal, mais ils ne sont pas là pour rester non plus 😠.

Qu’est-ce qu’un bug ?

* Si l’appli plante, c’est évidemment un bug
* Si quelque chose se comporte de manière inattendue, il peut s’agir d’un bug.

Dans tous les cas, vérifiez que vous disposez de la dernière mise à jour : le bug est peut-être déjà corrigé.

Si le bug n’est pas corrigé, signalez-le :grin:.

## Signaler un bug
{: #reporting-a-bug }

Créez un ticket sur GitHub :
<https://github.com/d4rken-org/sdmaid-se/issues>

* Décrivez ce que vous attendiez et ce qui s’est réellement passé
* Listez les étapes qu’une autre personne devrait suivre pour reproduire le problème
* Décrivez votre configuration
    * Votre type d’appareil (par ex. Pixel 5)
    * La version d’Android (par ex. Android 13.1)
    * Si votre appareil est rooté, la version de Magisk

Joignez un [journal de débogage](#debug-log) si vous pouvez reproduire le problème. 

Astuce : vous pouvez [appuyer longuement sur l’entrée « Journal des changements »](https://github.com/d4rken-org/sdmaid-se/pull/940) dans les paramètres de SD Maid pour copier la version dans votre presse-papiers.

## Journal de débogage
{: #debug-log }

Un journal de débogage est un enregistrement des actions de SD Maid. Vous lancez l’enregistrement, reproduisez le problème, puis l’arrêtez, et vous pouvez alors partager un fichier zip contenant un compte rendu chronologique de ce que SD Maid a fait.

Pour lancer l’enregistrement d’un journal de débogage :
* Ouvrez les paramètres de SD Maid en appuyant sur l’icône des paramètres en bas à droite du tableau de bord
* Trouvez l’entrée `Assistance` dans la liste des paramètres
* Appuyez sur `Enregistrer le journal de débogage` sur l’écran d’assistance, puis de nouveau sur `Enregistrer le journal de débogage` dans la boîte de dialogue
* Pendant l’enregistrement, le tableau de bord affiche une carte `Enregistrement du journal de débogage`
* **Reproduisez maintenant le problème que vous voulez enregistrer**
* Une fois le problème reproduit, appuyez sur `Arrêter l’enregistrement` sur cette carte du tableau de bord ou sur l’écran d’assistance. Si l’enregistrement est très court, SD Maid vous demande d’abord confirmation ; appuyez sur `Arrêter quand même` pour l’arrêter.
* L’écran `Journal de débogage` s’ouvre. Une fois le fichier journal compressé, appuyez sur `Partager` pour l’envoyer.

Le journal de débogage est enregistré jusqu’à ce que vous l’arrêtiez : l’enregistrement continue même si vous forcez l’arrêt de SD Maid et le redémarrez.

Si vous cherchez la cause de problèmes liés aux fonctions de base, par ex. la détection du root ou les zones de stockage détectées, il est utile de forcer l’arrêt de SD Maid et de le redémarrer pendant l’enregistrement, car certaines données ne sont initialisées qu’une fois par session de l’appli.

Les fichiers du journal de débogage sont stockés dans `/storage/emulated/0/Android/data/eu.darken.sdmse/cache/debug/logs`.

En images :

<a href="/assets/images/bugs/debug-log-1.webp"><img src="/assets/images/bugs/debug-log-1.webp" width="100" alt=""></a>
<a href="/assets/images/bugs/debug-log-2.webp"><img src="/assets/images/bugs/debug-log-2.webp" width="100" alt=""></a>
<a href="/assets/images/bugs/debug-log-3.webp"><img src="/assets/images/bugs/debug-log-3.webp" width="100" alt=""></a>
<a href="/assets/images/bugs/debug-log-4.webp"><img src="/assets/images/bugs/debug-log-4.webp" width="100" alt=""></a>
<a href="/assets/images/bugs/debug-log-5.webp"><img src="/assets/images/bugs/debug-log-5.webp" width="100" alt=""></a>
<a href="/assets/images/bugs/debug-log-6.webp"><img src="/assets/images/bugs/debug-log-6.webp" width="100" alt=""></a>
<a href="/assets/images/bugs/debug-log-7.webp"><img src="/assets/images/bugs/debug-log-7.webp" width="100" alt=""></a>
<a href="/assets/images/bugs/debug-log-8.webp"><img src="/assets/images/bugs/debug-log-8.webp" width="100" alt=""></a>
