---
title: "Configurer SD Maid SE"
lang: fr
translation_key: setup
permalink: /fr/setup
source_hash: a14003a95dd2336d
---
# Configurer SD Maid SE
{: #setting-up-sd-maid-se }
Informations sur les différentes étapes de configuration de SD Maid SE.

SD Maid s’adapte aux accès disponibles. Toutes les autorisations sont facultatives, mais si vous n’en accordez aucune, la plupart des fonctionnalités ne fonctionneront pas. SD Maid donne les meilleurs résultats lorsque tous les éléments de configuration sont terminés.

SD Maid essaie automatiquement de terminer certaines étapes de configuration si le [root](#root-access) ou l’[accès ADB](#shizuku) est disponible.

Certaines autorisations se recoupent ou en englobent d’autres : par ex., vous pouvez accorder à SD Maid à la fois l’accès root et l’accès ADB. SD Maid essaie toujours d’utiliser la meilleure méthode d’accès possible pour chaque action et, si elle échoue, réessaie avec la deuxième meilleure méthode d’accès, puis la troisième, etc. Accorder à SD Maid des autorisations qui se recoupent améliore la fiabilité.

> **Astuce :** certaines étapes de configuration ci-dessous impliquent l’exécution de commandes ADB. Si ADB n’est pas configuré sur votre ordinateur, vous pouvez utiliser le [Web ADB Helper](https://d4rken.github.io/web-adb/) pour les exécuter directement depuis votre navigateur.

## Gérer la mémoire
{: #manage-storage }

L’autorisation « Gérer la mémoire » est une autorisation d’accès au stockage de portée étendue.

* Sous Android 12 et versions antérieures, elle donne accès à tout le stockage public.
* Sous Android 13+, elle donne toujours accès à tout le stockage public, mais pas aux sous-répertoires de `Android/data`.

## Statistiques d’utilisation
{: #usage-statistics }

L’autorisation Statistiques d’utilisation donne à SD Maid accès aux tailles des données et des caches, et lui permet de savoir si une appli est en cours d’exécution ou quand elle a été utilisée pour la dernière fois.

## Cadre d’accès à la mémoire
{: #storage-access-framework }
SD Maid utilise le [Storage Access Framework (SAF) d’Android](http://developer.android.com/guide/topics/providers/document-provider.html) pour obtenir l’accès à d’autres zones de stockage. SD Maid vous demandera d’accorder l’accès à des chemins précis qui ne sont pas accessibles avec la seule autorisation « Gérer la mémoire » (Just Android Things™️).

* Appuyez sur le bouton `Accorder l’accès` 
* Une nouvelle fenêtre s’ouvre
* Le bon chemin est peut-être déjà sélectionné ; sinon, vous devez naviguer jusqu’au chemin demandé par SD Maid.
* Appuyez ensuite sur le bouton `Utiliser ce dossier` : l’entrée de configuration de ce chemin se débloque et passe au vert.

Répétez cette opération jusqu’à ce que tous les chemins soient verts.

<a href="/assets/images/setup/saf-1.webp"><img src="/assets/images/setup/saf-1.webp" width="100" alt=""></a>
<a href="/assets/images/setup/saf-2.webp"><img src="/assets/images/setup/saf-2.webp" width="100" alt=""></a>
<a href="/assets/images/setup/saf-3.webp"><img src="/assets/images/setup/saf-3.webp" width="100" alt=""></a>
<a href="/assets/images/setup/saf-4.webp"><img src="/assets/images/setup/saf-4.webp" width="100" alt=""></a>

### SAF : dépannage
{: #saf-troubleshooting }
Pour quand les choses ne se passent pas comme prévu.

#### SAF : accès à `Android/data|obb`
{: #saf-androiddataobb-access }

Il n’est pas possible d’accorder l’accès à `Android/data|obb` avec les versions récentes de l’appli système `Fichiers` (`com.google.android.documentsui`) sous Android 11 et Android 12. Si l’appli `Fichiers` installée cible Android 14 (niveau d’API 34) ou une version ultérieure, SD Maid masque la demande d’accès SAF pour ces chemins (comme sous Android 13+).

Google Play met à jour l’appli `Fichiers` via le service `Mise à jour du système Google Play`. L’appli « Fichiers » d’origine (compilée pour le niveau d’API 31, par ex. `aml_doc_310851020 (310851020)`) fournie avec Android 12 permet à SD Maid d’ouvrir la fenêtre de sélection de chemin SAF sur `Android/Data`, alors que la dernière appli « Fichiers » (compilée pour le niveau d’API 34 ou ultérieur, par ex. `14-10492947 (340916000)`) l’en empêche.

**Solution de contournement :**

Vous pouvez désinstaller la mise à jour de l’appli `Fichiers`, puis configurer SD Maid. Mettre de nouveau à jour l’appli `Fichiers` n’annulera pas les autorisations déjà accordées.

* Ouvrez les paramètres de l’appareil
* Allez dans « Applications et notifications » ou « Gestionnaire d’applications »
* Trouvez l’appli système `Fichiers`
* Appuyez sur le menu à trois points, généralement en haut à droite
* Choisissez « Désinstaller les mises à jour »

ou

_Vous pouvez aussi exécuter cette commande à l’aide du [Web ADB Helper](https://d4rken.github.io/web-adb/)._

`adb uninstall com.google.android.documentsui`

#### SAF : OPEN_DOCUMENT_TREE ActivityNotFoundException
{: #saf-open_document_tree-activitynotfoundexception }

Accorder l’accès à des chemins via le Storage Access Framework nécessite une appli qui permet à l’utilisateur de confirmer les chemins auxquels l’accès sera accordé. Cette appli est l’appli « Documents ». C’est une appli système intégrée à tous les appareils Android depuis Android 5.0  (`com.android.documentsui` ou `com.google.android.documentsui`). Si vous obtenez cette erreur, c’est que quelqu’un a supprimé ou désactivé cette appli. Dans la plupart des cas, quelqu’un a exécuté un outil de « debloat » trop zélé qui l’a désactivée. Vous pouvez la réactiver via ADB :

_Vous pouvez aussi exécuter ces commandes à l’aide du [Web ADB Helper](https://d4rken.github.io/web-adb/)._

```
adb shell pm enable com.android.documentsui
or
adb shell pm enable com.google.android.documentsui
```

#### SAF : fenêtre vide
{: #saf-empty-window }
Si la fenêtre servant à accorder l’accès aux chemins est vide, le paquet du fournisseur de stockage externe (`com.android.externalstorage`) est peut-être désactivé (probablement par un outil de « debloat » trop zélé). Vous pouvez le réactiver via ADB :

_Vous pouvez aussi exécuter cette commande à l’aide du [Web ADB Helper](https://d4rken.github.io/web-adb/)._

```
adb shell pm enable com.android.externalstorage
```

Exécuter de nouveau la `Mise à jour du système Google Play` réinstallera la mise à jour. Vous pouvez aussi télécharger manuellement une version à jour sur [APKMirror.com](https://www.apkmirror.com/apk/google-inc/files-6/).

## Notifications
{: #notifications }

Sans cette autorisation, SD Maid ne pourra pas publier de notifications. Sur certaines ROM, cela peut nuire à la fiabilité lors de l’exécution en arrière-plan.

## Inventaire des applis
{: #app-inventory }

SD Maid a besoin de l’autorisation `QUERY_ALL_PACKAGES` pour vérifier quelles applis sont installées sur votre appareil (c’est-à-dire la [« visibilité sur l’inventaire des applis installées »](https://support.google.com/googleplay/android-developer/answer/10158779?hl=en)). Voir aussi [« Autorisation Statistiques d’utilisation »](/fr/setup#usage-statistics)

L’autorisation `QUERY_ALL_PACKAGES` est indispensable pour :

* [Recherche de cadavres (CorpseFinder)](/fr/corpsefinder) : pour comprendre quels fichiers peuvent être supprimés, SD Maid doit savoir si vous avez installé des applis qui pourraient en avoir besoin.
* [Nettoyeur d’applis (AppCleaner)](/fr/appcleaner) : déterminer les caches de chaque appli
* [Vérificateur d’applis (AppControl)](/fr/appcontrol) : pouvoir lister toutes les applis installées
* [Analyseur de mémoire (StorageAnalyzer)](/fr/storageanalyzer) : attribuer le stockage utilisé aux applis installées

Cette autorisation existe depuis [Android 11](https://developer.android.com/training/package-visibility) et est normalement accordée par défaut. Depuis Android 14, il existe des appareils (par ex. [#1015](https://github.com/d4rken-org/sdmaid-se/issues/1015), [#1354](https://github.com/d4rken-org/sdmaid-se/issues/1354)) sur lesquels l’autorisation n’est pas accordée par défaut ou peut être révoquée.

[<img src="/assets/images/setup/app-inventory.webp" width="100">](/assets/images/setup/app-inventory.webp)

### Liste d’applis invalide
{: #invalid-app-list }

Sur certains appareils, l’autorisation « App list » (liste des applis, `QUERY_ALL_PACKAGES` / `GET_INSTALLED_APPS`) est accordée, mais le système renvoie quand même une **liste incomplète des applis installées**, par exemple une liste à laquelle il manque des paquets système essentiels comme `android`. Lorsque SD Maid le détecte, il affiche un avertissement **« L’inventaire des applis est invalide »** et une remarque **« Limitation possible de l’appareil »** dans la carte de configuration au lieu de continuer.

SD Maid s’arrête là volontairement : s’il ne peut pas voir la liste complète des applis installées, il ne peut pas déterminer de manière fiable quels fichiers appartiennent à une appli encore installée. Continuer malgré tout pourrait conduire à supprimer des données appartenant à une appli que SD Maid n’a simplement pas pu voir ; les fonctionnalités liées aux applis restent donc désactivées jusqu’à ce qu’une liste complète soit disponible.

**Ce que vous pouvez essayer :**

* Ouvrez les paramètres système et assurez-vous que SD Maid dispose de l’autorisation « App list » (liste des applis). Chez certains fabricants, cette option porte un autre nom (par ex. « Query all packages », interroger tous les paquets) ou se trouve dans un écran distinct « Confidentialité » ou « Gestionnaire d’autorisations ».
* Vérifiez si une fonction intégrée d’économie de batterie, de protection de la vie privée ou de « masquage d’applis » filtre la liste des applis que SD Maid peut voir, et excluez-en SD Maid.
* Redémarrez l’appareil et rouvrez SD Maid pour que l’état de l’autorisation soit réévalué.

Si l’autorisation « App list » est déjà accordée et que la liste est toujours signalée comme invalide, il s’agit généralement d’une **limitation de l’appareil** plutôt que de quelque chose que vous pouvez corriger : certains systèmes (par exemple HarmonyOS, ou des versions d’Android fortement modifiées) ne fournissent jamais une liste complète des applis aux applis tierces. Voir [#2441](https://github.com/d4rken-org/sdmaid-se/issues/2441) pour un exemple.

Dans ce cas, les outils liés aux applis ([Recherche de cadavres](/fr/corpsefinder), [Nettoyeur d’applis](/fr/appcleaner), [Vérificateur d’applis](/fr/appcontrol)) restent désactivés pour protéger vos données, mais l’[Analyseur de mémoire](/fr/storageanalyzer) fonctionne toujours : il indique combien d’espace votre stockage utilise, simplement avec moins de détails ; les tailles des dossiers sont affichées en lecture seule et ne peuvent pas être attribuées à des applis individuelles.

## Service d’accessibilité
{: #accessibility-service }
Le service d’accessibilité permet à SD Maid d’interagir avec votre appareil comme un humain, c’est-à-dire de voir l’écran et d’appuyer sur des boutons. SD Maid peut l’utiliser pour automatiser des actions fastidieuses comme appuyer sur « Vider le cache » pour des centaines d’applis. Le service d’accessibilité est un service que l’appli ne peut pas lancer elle-même : il doit être lancé par le système, car ce type de service accorde à une appli un accès supplémentaire à votre appareil.

### ACS : dépannage
{: #acs-troubleshooting }

Le service peut cesser de fonctionner et vous obliger à le réactiver dans diverses situations :
* Vous mettez à jour l’appli.
* L’appli plante. Si cela se produit, vous devriez le signaler comme un bug. Si cela arrive trop souvent, il se peut que vous ne puissiez plus le redémarrer en le désactivant puis en le réactivant tant que l’appareil n’a pas été redémarré.
* Le système arrête l’appli en raison d’« optimisations de batterie » trop agressives (voir aussi <https://dontkillmyapp.com/>)
* L’appli de sécurité du fabricant le désactive (voir [Bloqué par Phone Manager](#acs-blocked-by-phone-manager-oneplus-oppo-realme))

#### ACS : le service est désactivé
{: #acs-service-is-disabled }

Certaines ROM (par ex. MIUI) sont connues pour arrêter agressivement tout ce qui n’est pas sur la liste blanche du fabricant de l’appareil.
Si l’on vous demande constamment de réactiver le service d’accessibilité, il est possible que le système le désactive chaque fois que SD Maid n’est pas activement utilisé.

Essayez de désactiver les optimisations de batterie ; sur les ROM MIUI, il existe en plus un paramètre appelé « Autostart » (démarrage automatique) : activez-le pour SD Maid. Pour les appareils Xiaomi/POCO/Redmi :

* Allez dans `Security>Manage applications>Auto start in background` (Sécurité > Gérer les applications > Démarrage automatique en arrière-plan) et autorisez-le pour SD Maid.
* Allez dans `Security>Increase speed>Lock apps` (Sécurité > Accélérer > Verrouiller les applications) et sélectionnez SD Maid.


Vous pouvez aussi envisager d’accorder une autorisation supplémentaire qui [permet à SD Maid de redémarrer le service automatiquement](#acs-automatic-service-restart).

[<img src="/assets/images/setup/acs-disabled.webp" width="100">](/assets/images/setup/acs-disabled.webp)

#### ACS : bloqué par Phone Manager (OnePlus, OPPO, Realme)
{: #acs-blocked-by-phone-manager-oneplus-oppo-realme }

Sous OxygenOS et ColorOS, l’appli préinstallée « Phone Manager » (gestionnaire du téléphone) peut désactiver d’elle-même le service d’accessibilité, généralement dans la seconde qui suit la fin d’un nettoyage. L’interrupteur d’accessibilité d’Android est alors réellement désactivé, si bien que SD Maid semble avoir cessé de fonctionner en cours d’exécution.

Désactivez la fonction sous `Phone Manager>Utilities>Block suspicious app activities` (Gestionnaire du téléphone > Utilitaires > Bloquer les activités suspectes des applis).

Ce même écran conserve une liste de ce qu’il a bloqué. Des entrées intitulées « Abnormal device control » (contrôle anormal de l’appareil) dont l’horodatage correspond aux nettoyages que vous avez lancés confirment que c’est la cause. L’interrupteur « Blocking alerts » (alertes de blocage) situé à côté détermine seulement si vous êtes averti : avec les alertes désactivées, cela se produit donc sans bruit.

#### ACS : activer via ADB
{: #acs-enable-via-adb }

Si votre appareil ne permet pas d’activer les services d’accessibilité depuis l’écran (par ex. un téléviseur), il est possible d’activer le service via ADB en exécutant :

_Vous pouvez aussi exécuter cette commande à l’aide du [Web ADB Helper](https://d4rken.github.io/web-adb/)._

```bash
adb shell settings put secure enabled_accessibility_services eu.darken.sdmse/.automation.core.AutomationService
```

#### ACS : redémarrage automatique du service
{: #acs-automatic-service-restart }

_Remarque : il s’agit d’une technique avancée destinée aux utilisateurs expérimentés._

Vous pouvez accorder une autorisation supplémentaire qui permet à SD Maid de redémarrer le service de lui-même.
Cette autorisation s’appelle `WRITE_SECURE_SETTINGS` et ne peut être accordée que [via ADB](https://www.google.com/search?q=android+adb).
Si c’est configuré, exécutez cette commande pour accorder l’autorisation :

_Vous pouvez aussi exécuter cette commande à l’aide du [Web ADB Helper](https://d4rken.github.io/web-adb/)._

```bash
adb shell pm grant eu.darken.sdmse android.permission.WRITE_SECURE_SETTINGS
```

Une fois cette autorisation accordée, SD Maid peut utiliser la commande [décrite ici](#acs-enable-via-adb) pour activer le service si nécessaire.
SD Maid ne l’utilise que si vous avez activé le service d’accessibilité manuellement au moins une fois.
Si vous retournez dans la configuration et indiquez à SD Maid de ne pas utiliser le service d’accessibilité, cette technique de redémarrage automatique ne sera plus utilisée tant que vous n’aurez pas réactivé manuellement le service d’accessibilité.

Pour retirer de nouveau cette autorisation à SD Maid, utilisez cette commande :

_Vous pouvez aussi exécuter cette commande à l’aide du [Web ADB Helper](https://d4rken.github.io/web-adb/)._

```bash
adb shell pm revoke eu.darken.sdmse android.permission.WRITE_SECURE_SETTINGS
```

Si vous obtenez `Operation not allowed: java.lang.SecurityException`, [voir ici](https://stackoverflow.com/questions/52079343). Certains appareils restreignent les autorisations ADB. Vérifiez s’il existe dans les options pour les développeurs un paramètre supplémentaire (par ex. `Disable Permission Monitoring`, désactiver la surveillance des autorisations) qu’il faut activer ; après avoir utilisé la commande, vous pouvez le désactiver de nouveau.

#### ACS : Advanced Protection
{: #acs-advanced-protection }

Sous Android 17 et versions ultérieures, le mode [**Advanced Protection**](https://developer.android.com/privacy-and-security/advanced-protection-mode) (protection avancée) de Google empêche les applis qui ne sont pas de véritables outils d’accessibilité (lecteurs d’écran, accès via un contacteur, etc.) d’utiliser le service d’accessibilité. SD Maid est un outil de maintenance et de nettoyage : tant qu’Advanced Protection est activé, son service d’accessibilité **ne peut pas** être activé. Android rejette le service au niveau du système, même si vous essayez de l’accorder via le root ou l’accès ADB.

SD Maid ne peut pas s’en exempter : seules les applis que Google classe comme outils d’accessibilité y ont droit, et les nettoyeurs en sont explicitement exclus.

Vous avez deux possibilités :

- **Utiliser le root ou l’accès ADB** Lorsque le root ou l’accès ADB est configuré, le Nettoyeur d’applis de SD Maid vide les caches des applis via un appel système direct et n’a **pas** besoin du service d’accessibilité. Avec le **root**, cela couvre aussi le nettoyage d’applis sélectionnées individuellement. Avec l’**accès ADB**, un nettoyage complet de « toutes les applis » fonctionne sans le service d’accessibilité, mais le vidage du cache d’applis *précises* sélectionnées en dépend toujours. Configurez cela dans SD Maid sous Configuration → [Accès superutilisateur](#root-access) ou [Accès ADB](#shizuku).
- **Désactiver Advanced Protection** si vous voulez précisément un nettoyage basé sur l’accessibilité. Cette option se trouve sous `Settings → Security & privacy → Advanced Protection` (Paramètres → Sécurité et confidentialité → Protection avancée ; le chemin exact varie selon l’appareil). La réactiver plus tard désactivera de nouveau le service d’accessibilité.

#### ACS : restrictions AppOps
{: #acs-appops-restrictions }

Android 13+ peut appliquer des restrictions supplémentaires aux applis installées manuellement (sideloading). Si SD Maid n’est pas installé depuis une source connue (de Google) comme Google Play ou d’autres boutiques d’applis, il se peut donc que vous ne puissiez pas accorder directement certaines autorisations à SD Maid, par ex. démarrer un service d’accessibilité.

##### OxygenOS 15 - OnePlus
{: #oxygenos-15---oneplus }

OnePlus a oublié d’inclure (ou n’a simplement pas inclus) l’action `Allow restricted settings` (Autoriser les paramètres restreints) dans les paramètres des applis sous OxygenOS 15. Si vous n’installez pas l’appli via Google Play, l’installateur doit définir l’indicateur `INSTALL_ALL_WHITELIST_RESTRICTED_PERMISSIONS`. Si Shizuku est configuré, vous pouvez utiliser l’appli [« Install with Options »](https://github.com/zacharee/InstallWithOptions).


##### Autres ROM
{: #other-roms }

Pour lever ces restrictions, ouvrez l’écran des paramètres de l’appli SD Maid dans le système. Ouvrez le menu en haut à droite et sélectionnez `Autoriser les paramètres restreints` ; vous pouvez ensuite revenir à la liste des services d’accessibilité et activer le service de SD Maid.

**Remarque :**
Pour améliorer l’ergonomie (c’est du sarcasme), Google a décidé que ce paramètre ne serait visible que si :

* Vous avez vu au moins une fois le message « Paramètre restreint » après avoir appuyé sur l’entrée du service d’accessibilité de SD Maid
* Vous avez ouvert les paramètres système et êtes allé à la page des paramètres de SD Maid à la main, et non via une autre appli ou des raccourcis.

😮‍💨


Quelques informations de contexte supplémentaires [sont disponibles ici](https://blog.esper.io/android-13-sideloading-restriction-harder-malware-abuse-accessibility-apis/).

<a href="/assets/images/setup/acs-restricted-settings-1.webp"><img src="/assets/images/setup/acs-restricted-settings-1.webp" width="100" alt=""></a>
<a href="/assets/images/setup/acs-restricted-settings-2.webp"><img src="/assets/images/setup/acs-restricted-settings-2.webp" width="100" alt=""></a>
<a href="/assets/images/setup/acs-restricted-settings-3.webp"><img src="/assets/images/setup/acs-restricted-settings-3.webp" width="100" alt=""></a>
<a href="/assets/images/setup/acs-restricted-settings-4.webp"><img src="/assets/images/setup/acs-restricted-settings-4.webp" width="100" alt=""></a>

## Accès superutilisateur
{: #root-access }

Avec l’accès root, SD Maid a un accès complet à votre appareil et peut effectuer l’analyse la plus complète possible. En général, cela signifie que vous avez déverrouillé votre bootloader et installé Magisk.

Si SD Maid pense que votre appareil est peut-être rooté, il demandera d’abord l’accès root. Si l’accès root est accordé, SD Maid l’utilisera pour s’accorder autant d’autres autorisations que possible. L’accès root est le niveau d’accès le plus élevé que vous puissiez donner à une appli ; les autres autorisations ne sont alors nécessaires que pour la facilité d’utilisation et les performances, c’est pourquoi elles ne nécessitent pas de consentement supplémentaire lors de la configuration si SD Maid dispose déjà du root.

## Accès ADB
{: #shizuku }

L’accès ADB permet à SD Maid d’utiliser l’interface de débogage d’Android (ADB) pour des opérations nécessitant des privilèges élevés. Il nécessite une appli distincte qui exécute un service ADB sur votre appareil et y donne accès aux autres applis : [Porter](#porter) ou [Shizuku](#using-shizuku). C’est une modification que vous pouvez apporter à votre téléphone et dont d’autres applis peuvent tirer parti.

C’est semblable au root, mais pas identique. Les autorisations sont un peu moins puissantes, mais la modification n’est pas permanente et ne nécessite pas de déverrouiller votre bootloader.

### Porter
{: #porter }

[Porter](https://porter.darken.eu) est mon fork maintenu de Shizuku, avec prise en charge facultative du root. Son site web explique comment l’installer et le démarrer. Sous Android 11 et versions ultérieures, le débogage sans fil permet de le démarrer sans ordinateur.

Si vous configurez l’accès ADB pour la première fois, utilisez Porter. Les versions de SD Maid issues de GitHub et de F-Droid vous orientent vers Porter lorsqu’aucune appli d’accès ADB n’est installée. La version Google Play vous oriente vers Shizuku sur Google Play, mais fonctionne de la même manière avec Porter.

Si Porter et Shizuku sont tous deux installés, SD Maid utilise Porter. La carte de configuration indique alors `Porter est installé, donc SD Maid attend Porter au lieu de Shizuku`. Démarrez Porter pour continuer, ou désinstallez-le si vous voulez que SD Maid utilise Shizuku.

### Utiliser Shizuku
{: #using-shizuku }

Il existe deux versions de Shizuku :

* [Le fork de thedjchi](https://github.com/thedjchi/Shizuku) ([guide de configuration](https://github.com/thedjchi/Shizuku/wiki/setup)) est activement maintenu et fonctionnerait sur les appareils où l’original [ne se connecte jamais](#shizuku-never-connects).
* [L’original de RikkaApps](https://shizuku.rikka.app/guide/setup/) fonctionne toujours bien sur la plupart des appareils.

Ils ne peuvent pas être installés en même temps. Si l’original fonctionne déjà chez vous, inutile de changer.

### Accorder l’accès
{: #granting-access }

Une fois Porter ou Shizuku configuré :

* Dans la configuration de SD Maid, choisissez `Essayer d'utiliser l'accès ADB`. Ce réglage est interne à SD Maid : vous pouvez l’utiliser pour activer ou désactiver l’utilisation de l’accès ADB par SD Maid
* Après votre consentement, une boîte de dialogue de Porter ou de Shizuku demande si SD Maid peut l’utiliser
* Confirmez la boîte de dialogue
* La carte de configuration `Accès ADB` indique si SD Maid parvient à se connecter

Configurer l’accès ADB alors que vous avez déjà accordé l’accès root à SD Maid n’active pas de fonctionnalités supplémentaires, mais améliore la fiabilité. Si des actions échouent avec l’autorisation root, SD Maid réessaie avec l’accès ADB.

### Accès ADB : dépannage
{: #shizuku-troubleshooting }

Vous pouvez vérifier l’état de l’accès ADB de SD Maid en allant dans `Paramètres > Configuration` et en consultant la carte de configuration `Accès ADB`. Elle devrait indiquer `SD Maid peut maintenant utiliser l'accès ADB via Porter` (ou `via Shizuku`).

* `Porter n'a pas encore autorisé SD Maid` : confirmez la boîte de dialogue d’accès, ou autorisez SD Maid dans l’appli Porter (ou Shizuku).
* `Porter refuse l'accès à SD Maid` : l’accès a été refusé définitivement à SD Maid. Ouvrez Porter (ou Shizuku) et autorisez-y SD Maid.
* `Mets à jour Porter pour l'utiliser avec SD Maid` ou `Cette version de SD Maid est trop ancienne pour Porter` : les deux applis n’ont pas de versions compatibles. Mettez à jour celle que la carte nomme.

#### Shizuku ne se connecte jamais
{: #shizuku-never-connects }

La carte indique `L'accès ADB a échoué` et `Shizuku est en cours d'exécution, mais il n'a jamais connecté SD Maid à son service d'assistance.` Les anciennes versions affichaient `Waiting for link with Shizuku` (en attente de la liaison avec Shizuku) ou `Shizuku link failed` (échec de la liaison avec Shizuku).

Ouvrez l’appli Shizuku. Si elle indique qu’elle est en cours d’exécution et que SD Maid figure dans sa liste d’applis autorisées, Shizuku est correctement configuré et le reconfigurer n’aidera pas. Ce qui échoue, c’est le service d’assistance de Shizuku : il s’arrête au démarrage avant de pouvoir répondre, et SD Maid abandonne au bout d’environ 15 secondes. `Réessayer` lance la même vérification et échoue de la même façon.

Il s’agit d’un bug de Shizuku ([RikkaApps/Shizuku#1198](https://github.com/RikkaApps/Shizuku/issues/1198)), et non de SD Maid, et il fait échouer toutes les applis qui utilisent le service d’assistance de Shizuku. Il touche les appareils MediaTek ainsi que les appareils sous HyperOS ou MIUI.

[Le fork de thedjchi](https://github.com/thedjchi/Shizuku) corrigerait ce problème. Pour changer :

* Désinstallez d’abord l’original, les deux ne peuvent pas coexister
* Dissociez-le du débogage sans fil si vous l’utilisiez
* Installez le fork depuis ses [versions publiées](https://github.com/thedjchi/Shizuku/releases) et suivez son [guide de configuration](https://github.com/thedjchi/Shizuku/wiki/setup)
* Accordez de nouveau l’autorisation Shizuku à SD Maid et à toutes les autres applis qui l’utilisaient
