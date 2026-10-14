---
title: "Programmateur"
lang: fr
translation_key: scheduler
permalink: /fr/scheduler
source_hash: 74e1452091eaf142
---
# Programmateur (Scheduler)
{: #scheduler }

La fonction **Programmateur** (Scheduler) de SD Maid 2 vous permet d’automatiser des tâches de maintenance à intervalles précis, pour que votre appareil reste optimisé sans effort manuel. Elle prend en charge plusieurs outils comme le **Nettoyeur d’applis** (AppCleaner), la **Recherche de cadavres** (CorpseFinder) et le **Nettoyeur système** (SystemCleaner), qui peuvent être configurés pour s’exécuter à intervalles réguliers ou à des heures précises.

<img src="/assets/images/scheduler/overview-1.webp" width="150">
<img src="/assets/images/scheduler/overview-2.webp" width="150">
<img src="/assets/images/scheduler/overview-3.webp" width="150">
<img src="/assets/images/scheduler/overview-4.webp" width="150">
<img src="/assets/images/scheduler/overview-5.webp" width="150">
<img src="/assets/images/scheduler/overview-6.webp" width="150">


## Configurer une programmation
{: #how-to-set-up-a-schedule }

1. Ouvrez les **Paramètres** et sélectionnez **Programmateur**.
2. Appuyez sur le bouton **+** pour créer une nouvelle programmation ou gérer les programmations existantes.
3. Choisissez les outils (par ex. **Nettoyeur d’applis**, **Recherche de cadavres**, **Nettoyeur système**) qui doivent s’exécuter selon la programmation.
4. Définissez l’**intervalle de répétition** (par ex. tous les 3 jours) et le **temps approximatif** de l’exécution.
5. Activez la programmation à l’aide de l’interrupteur.

## Modifier ou supprimer des programmations
{: #editing-or-deleting-schedules }

1. Ouvrez les paramètres du **Programmateur** pour gérer vos programmations.
2. Vous pouvez modifier les programmations existantes, ajuster les intervalles ou les supprimer si vous n’en avez plus besoin.

Une fois qu’une programmation est active, SD Maid exécute les tâches choisies aux moments indiqués.

## Options supplémentaires
{: #additional-options }

Quelques options supplémentaires se configurent dans le menu principal des paramètres : `Paramètres > Programmateur`.

- **Ignorer si la batterie faible** : si l’appareil est en mode économie d’énergie, les tâches programmées sont ignorées.
- **Ignorer si l’appareil n’est pas en charge** : les tâches programmées ne s’exécutent que lorsque l’appareil est en charge, ce qui évite de vider la batterie.
- **Commandes postprogrammation** : vous pouvez définir des commandes shell à exécuter une fois les tâches programmées terminées.
- **Service d’accessibilité** : par défaut, le programmateur ignore les fonctions qui reposent sur le service d’accessibilité, car elles ne fonctionnent pas lorsque l’appareil est verrouillé. Si vous activez cette option sans rien changer d’autre sur votre appareil, le programmateur échouera. À moins de savoir ce que vous faites, laissez-la donc désactivée. Elle n’est utile que dans des cas particuliers, comme un appareil Android TV toujours allumé.

## Dépannage
{: #troubleshooting }

### Le programmateur ne s’exécute pas 
{: #scheduler-is-not-executing }

Faites la distinction entre un programmateur qui ne s’exécute pas du tout et des opérations exécutées qui ne donnent pas les résultats souhaités. Vous pouvez consulter l’historique des exécutions dans la [section Historique](/fr/stats) de SD Maid. Si le programmateur s’est exécuté mais a échoué, une erreur devrait y figurer.

Si le programmateur ne s’est pas exécuté du tout, il est possible que le système ait empêché SD Maid de démarrer pour économiser la batterie. Après avoir activé votre première programmation, vous auriez dû voir une invite intitulée « Conseil » avec une action « Corriger », vous demandant de désactiver les optimisations de batterie. Si vous avez fermé cette invite par erreur, vous devez ouvrir manuellement les paramètres système de SD Maid et vérifier si SD Maid est « optimisé » par le système.

### Service d’accessibilité
{: #accessibility-service }
Les suppressions basées sur l’accessibilité, par ex. la suppression des « éléments inaccessibles » par le Nettoyeur d’applis, ne fonctionnent pas via le programmateur. Lorsque le programmateur s’exécute, l’appareil est généralement verrouillé : de même que vous ne pouvez rien toucher sur un écran éteint ou verrouillé, SD Maid ne le peut pas non plus.

Par défaut, le programmateur n’effectue pas d’opérations basées sur l’accessibilité. Vous pouvez toutefois activer l’option « Service d’accessibilité » dans les paramètres du programmateur si vous savez ce que vous faites.

Solutions de contournement : rootez l’appareil (applis utilisateur et système) ou installez Shizuku (applis utilisateur uniquement). L’une ou l’autre de ces modifications permet à SD Maid d’effectuer des opérations similaires sans utiliser le service d’accessibilité.
