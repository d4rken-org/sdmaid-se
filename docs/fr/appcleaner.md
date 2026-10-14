---
title: "Nettoyeur d’applis"
lang: fr
translation_key: appcleaner
permalink: /fr/appcleaner
source_hash: 505b031f74bf71a2
---
# Nettoyeur d’applis (AppCleaner)
{: #app-cleaner }

Le Nettoyeur d’applis (AppCleaner) est un outil qui tient compte des applis et trouve les fichiers « superflus ». Un fichier est considéré comme superflu s’il peut être supprimé sans effet indésirable et qu’il n’est pas nécessaire ou sera recréé si besoin. La plupart des types de fichiers d’un « cache » répondent à cette définition.

## Paramètres
{: #settings }

### Inclure les applis système
{: #include-system-apps }

Ce paramètre inclut les applis système dans les résultats. Il n’est pas activé par défaut, car certaines applis système (selon la ROM) réagissent mal au vidage de leur cache. Certaines applis système n’ont pas non plus de bouton « Vider le cache », ce qui peut bloquer SD Maid lors du vidage du cache via le service d’accessibilité.

### Inclure d’autres utilisateurs
{: #include-other-users }

Nécessite le root. Si cette option est activée, SD Maid cherche aussi dans les dossiers des autres utilisateurs, en plus de l’utilisateur qui exécute actuellement SD Maid.

### Inclure les applis en cours d’exécution
{: #include-running-apps }

Inclure ou non les applis en cours d’exécution.

### Taille minimale
{: #minimum-size }

La taille que doit atteindre le cache d’une appli pour être listé. Définir une taille minimale est un bon compromis entre utilité et vitesse. Vider le cache d’une appli qui ne fait que quelques Ko n’apporte généralement aucun bénéfice. Cela s’applique à la quantité totale de cache trouvée pour une appli (en tenant compte de tous les filtres actifs)

### Âge minimum
{: #minimum-age }

Permet de ne supprimer que les anciens fichiers de cache. S’applique à chaque fichier ou dossier des résultats.

### Inclure les caches inaccessibles
{: #include-inaccessible-caches }

Selon les autorisations dont dispose SD Maid, certains fichiers peuvent ne pas être directement accessibles. Il s’agit le plus souvent du cache privé par défaut des applis (par ex. `/data/data/eu.darken.sdmse/cache`). Si ce paramètre est activé, SD Maid essaie d’utiliser un service d’accessibilité pour naviguer dans les paramètres système et appuyer sur le bouton `Clear cache` (Vider le cache) de l’appli cible.

### Filtre
{: #filter }

Vous pouvez activer ou désactiver différents filtres pour influencer les résultats que le Nettoyeur d’applis fera apparaître.

#### Filtres génériques
{: #common-filters }

Les filtres génériques ciblent divers types de fichiers dans toutes les applis.

#### Filtres spécifiques
{: #specific-filters }

Les filtres spécifiques ciblent des applis précises, par ex. WhatsApp ou Telegram.

## Dépannage
{: #troubleshooting }
### `com.miui.securitycenter`
{: #commiuisecuritycenter }
#### Autorisation `GET_USAGE_STATS` manquante
{: #missing-get_usage_stats-permission }

MIUI et HyperOS utilisent une appli supplémentaire appelée « Security » ou « Security Center » (Sécurité, nom de paquet : `com.miui.securitycenter`) pour afficher les détails système d’une appli. Le déroulement normal pour vider le cache d’une appli est le suivant : appuyer sur `Clear data` (Effacer les données), choisir `Clear cache` (Vider le cache), confirmer l’action.

Si le bouton « Clear data » (Effacer les données) est grisé pour toutes les applis et qu’on ne peut pas appuyer dessus, l’appli « Security Center » n’a pas l’autorisation `GET_USAGE_STATS`. Normalement, celle-ci devrait être accordée par défaut. On ne sait pas exactement comment cela se produit, mais la solution consiste à accorder de nouveau cette autorisation.

Allez dans : `System Settings` (Paramètres système) > `Security and privacy` (Sécurité et confidentialité) > `More security and privacy` (Plus de paramètres de sécurité et de confidentialité) > `Special permissions` (Autorisations spéciales) > `Usage access` (Accès aux données d’utilisation) > trouvez l’appli `Security` (Sécurité) > accordez l’autorisation

Sinon, si vous disposez d’un accès ADB ou supérieur, exécutez la commande `pm grant com.miui.securitycenter android.permission.PACKAGE_USAGE_STATS`

<a href="/assets/images/appcleaner/miui-usage-stats-1.webp">
  <img src="/assets/images/appcleaner/miui-usage-stats-1.webp" width="100px">
</a>
<a href="/assets/images/appcleaner/miui-usage-stats-2.webp">
  <img src="/assets/images/appcleaner/miui-usage-stats-2.webp" width="100px">
</a>
<a href="/assets/images/appcleaner/miui-usage-stats-3.webp">
  <img src="/assets/images/appcleaner/miui-usage-stats-3.webp" width="100px">
</a>
<a href="/assets/images/appcleaner/miui-usage-stats-4.webp">
  <img src="/assets/images/appcleaner/miui-usage-stats-4.webp" width="100px">
</a>
<a href="/assets/images/appcleaner/miui-usage-stats-5.webp">
  <img src="/assets/images/appcleaner/miui-usage-stats-5.webp" width="100px">
</a>
<a href="/assets/images/appcleaner/miui-usage-stats-6.webp">
  <img src="/assets/images/appcleaner/miui-usage-stats-6.webp" width="100px">
</a>
