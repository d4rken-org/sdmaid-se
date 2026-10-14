---
title: "Vérificateur d’applis"
lang: fr
translation_key: appcontrol
permalink: /fr/appcontrol
source_hash: 1a4a7450fc5b86ca
---
# Vérificateur d’applis (AppControl)
{: #appcontrol }
Le Vérificateur d’applis (AppControl) réunit en un seul endroit toutes les actions liées au contrôle des applications installées. Après l’analyse, vous disposez d’une liste consultable de vos applis installées. Les applis système ne sont pas affichées par défaut : cela doit être activé dans les paramètres.
Certaines options ne sont disponibles (possibles) que si SD Maid a obtenu les droits root.

Certaines actions peuvent être exécutées sur plusieurs applis, d’autres uniquement sur des éléments individuels. Pour exécuter des actions sur plusieurs applis, appuyez longuement sur une appli, puis appuyez sur d’autres applis pour les sélectionner.

## Actions
{: #actions }
Certaines de ces actions ne sont accessibles que dans les détails de l’appli, que l’on ouvre en appuyant sur une entrée de la liste.

### Lancer l’appli
{: #run-app }
Lance l’appli. Son activité principale, généralement la même action que celle qu’exécuterait le lanceur.

### Arrêt forcé
{: #force-stop }
Tente de forcer l’arrêt de l’appli. Fonctionne le mieux avec le root ou ADB/Shizuku, sinon le service d’accessibilité est utilisé.

### Exporter l’APK
{: #export-apk }
Tente de copier le fichier `.apk` de l’appli depuis le stockage privé interne vers le répertoire de téléchargement de votre stockage public. Si l’appli est chiffrée, cette action nécessite le root ; les applis non chiffrées peuvent aussi être exportées sans root.

### Geler l’appli
{: #freeze-app }
Nécessite le root ou ADB/Shizuku. Désactive l’appli. L’appli n’est plus visible et ne peut pas démarrer d’elle-même. Elle doit être réactivée pour fonctionner. Aucune donnée n’est perdue, et cet état persiste même si vous désinstallez SD Maid. Toute bonne appli d’activation/désactivation d’applis peut réactiver les applis désactivées avec SD Maid, et inversement.

### Supprimer l’appli
{: #delete-app }
Supprime l’appli et ses données.


## Paramètres
{: #settings }
### Déterminer les tailles
{: #determine-size }
Peut être désactivé pour accélérer le chargement de la liste des applis. Déterminer la taille d’une appli prend beaucoup de temps.

### Déterminer l’activité
{: #determine-activity }
Peut être désactivé pour accélérer le chargement de la liste des applis. Déterminer si une appli a été active récemment prend beaucoup de temps.
