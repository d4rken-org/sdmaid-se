---
title: "Configurando o SD Maid SE"
lang: pt-BR
translation_key: setup
permalink: /pt-br/setup
source_hash: a14003a95dd2336d
---
# Configurando o SD Maid SE
{: #setting-up-sd-maid-se }
Informações sobre as diferentes etapas de configuração do SD Maid SE.

O SD Maid se adapta ao acesso que estiver disponível. Embora todas as permissões sejam opcionais, se você não conceder nenhuma permissão, a maioria dos recursos não vai funcionar. O SD Maid funciona melhor quando todos os itens da configuração estão concluídos.

O SD Maid vai tentar concluir automaticamente algumas das etapas de configuração se [root](#root-access) ou [acesso ADB](#shizuku) estiver disponível.

Algumas permissões se sobrepõem ou englobam outras, por exemplo você pode dar ao SD Maid tanto acesso root quanto acesso ADB. O SD Maid sempre tenta usar o melhor método de acesso possível para cada ação e, se ele falhar, tenta de novo com o segundo melhor método de acesso, depois com o terceiro melhor etc. Conceder ao SD Maid permissões sobrepostas melhora a confiabilidade.

> **Dica:** algumas etapas de configuração abaixo envolvem executar comandos ADB. Se você não tem o ADB configurado no seu computador, pode usar o [Web ADB Helper](https://d4rken.github.io/web-adb/) para executá-los diretamente no navegador.

## Gerenciar armazenamento
{: #manage-storage }

A permissão "Gerenciar armazenamento" é uma permissão de acesso ao armazenamento de amplo alcance.

* No Android 12 e anteriores, ela concede acesso a todo o armazenamento público.
* No Android 13+, ela ainda concede acesso a todo o armazenamento público, mas não aos subdiretórios em `Android/data`.

## Estatísticas de uso
{: #usage-statistics }

As permissões de estatísticas de uso dão ao SD Maid acesso aos tamanhos de dados e de cache e à informação de se um aplicativo está em execução ou quando foi usado pela última vez.

## Framework de acesso ao armazenamento
{: #storage-access-framework }
O SD Maid usa o [Storage Access Framework do Android (SAF)](http://developer.android.com/guide/topics/providers/document-provider.html) para obter acesso a áreas de armazenamento adicionais. O SD Maid vai pedir que você conceda acesso a caminhos específicos que não ficam acessíveis só com a permissão "Gerenciar armazenamento" (coisas do Android™️).

* Toque no botão `Permitir acesso` 
* Uma nova janela se abre
* Se o caminho correto já estiver selecionado, ótimo, senão você precisa navegar até o caminho que o SD Maid quer.
* Depois toque no botão `Usar esta pasta`, o item de configuração desse caminho será desbloqueado e ficará verde.

Repita esse processo até que todos os caminhos estejam verdes.

<a href="/assets/images/setup/saf-1.webp"><img src="/assets/images/setup/saf-1.webp" width="100" alt=""></a>
<a href="/assets/images/setup/saf-2.webp"><img src="/assets/images/setup/saf-2.webp" width="100" alt=""></a>
<a href="/assets/images/setup/saf-3.webp"><img src="/assets/images/setup/saf-3.webp" width="100" alt=""></a>
<a href="/assets/images/setup/saf-4.webp"><img src="/assets/images/setup/saf-4.webp" width="100" alt=""></a>

### SAF: solução de problemas
{: #saf-troubleshooting }
Para quando as coisas não saem como planejado.

#### SAF: acesso a `Android/data|obb`
{: #saf-androiddataobb-access }

Conceder acesso a `Android/data|obb` não é possível com as versões mais recentes do aplicativo `Files` (Arquivos) do sistema (`com.google.android.documentsui`) no Android 11 e no Android 12. Se o aplicativo `Files` instalado tiver como alvo o Android 14 (nível de API 34) ou mais recente, o SD Maid vai ocultar o pedido de acesso SAF para esses caminhos (como no Android 13+).

O Google Play atualiza o aplicativo `Files` pelo serviço `Google Play system update` (atualização do sistema do Google Play). O aplicativo "Files" original (compilado para o nível de API 31, por exemplo `aml_doc_310851020 (310851020)`) que veio com o Android 12 permite que o SD Maid abra a janela de seleção de caminho do SAF em `Android/Data`, enquanto o aplicativo "Files" mais recente (compilado para o nível de API 34 ou posterior, por exemplo `14-10492947 (340916000)`) impede isso.

**Solução alternativa:**

Você pode desinstalar a atualização do `Files` e depois configurar o SD Maid. Atualizar o aplicativo `Files` de novo não vai desfazer as permissões já concedidas.

* Abra as configurações do dispositivo
* Vá em "Apps & notifications" (Apps e notificações) ou "Application Manager" (gerenciador de aplicativos)
* Encontre o aplicativo do sistema `Files`
* Toque no menu de três pontos, normalmente no canto superior direito
* Escolha "Uninstall updates" (Desinstalar atualizações)

ou

_Você também pode executar este comando usando o [Web ADB Helper](https://d4rken.github.io/web-adb/)._

`adb uninstall com.google.android.documentsui`

#### SAF: OPEN_DOCUMENT_TREE ActivityNotFoundException
{: #saf-open_document_tree-activitynotfoundexception }

Conceder acesso a caminhos pelo Storage Access Framework requer um aplicativo que permita ao usuário confirmar os caminhos aos quais o acesso será concedido. Esse aplicativo é o aplicativo "Documents" (Documentos). É um aplicativo do sistema que vem embutido em todo dispositivo Android desde o Android 5.0  (`com.android.documentsui` ou `com.google.android.documentsui`). Se você recebe esse erro, alguém apagou ou desativou esse aplicativo. Na maioria dos casos, alguém executou uma ferramenta de "remoção de bloatware" exagerada que o desativou. Você pode reativá-lo via ADB:

_Você também pode executar estes comandos usando o [Web ADB Helper](https://d4rken.github.io/web-adb/)._

```
adb shell pm enable com.android.documentsui
or
adb shell pm enable com.google.android.documentsui
```

#### SAF: janela vazia
{: #saf-empty-window }
Se a janela usada para conceder acesso aos caminhos estiver vazia, talvez o pacote do provedor de armazenamento externo (`com.android.externalstorage`) esteja desativado (provavelmente por uma ferramenta de "remoção de bloatware" exagerada). Você pode reativá-lo via ADB:

_Você também pode executar este comando usando o [Web ADB Helper](https://d4rken.github.io/web-adb/)._

```
adb shell pm enable com.android.externalstorage
```

Executar o `Google Play system update` de novo vai restaurar a atualização. Você também pode baixar manualmente uma versão atualizada no [APKMirror.com](https://www.apkmirror.com/apk/google-inc/files-6/).

## Notificações
{: #notifications }

Sem essa permissão, o SD Maid não consegue publicar notificações. Em algumas ROMs, isso pode afetar negativamente a confiabilidade da execução em segundo plano.

## Inventário do aplicativo
{: #app-inventory }

O SD Maid requer a permissão `QUERY_ALL_PACKAGES` para verificar quais aplicativos estão instalados no seu dispositivo (ou seja, ["visibilidade do inventário de aplicativos instalados"](https://support.google.com/googleplay/android-developer/answer/10158779?hl=en)). Veja também ["Permissão de estatísticas de uso"](/pt-br/setup#usage-statistics)

A permissão `QUERY_ALL_PACKAGES` é obrigatória para:

* [Localizador de Cadáver (CorpseFinder)](/pt-br/corpsefinder): para entender quais arquivos podem ser apagados, o SD Maid precisa saber se você tem aplicativos instalados que possam precisar deles.
* [Limpador de Aplicativos (AppCleaner)](/pt-br/appcleaner): determinar os caches de cada aplicativo
* [Gerenciamento de aplicativos (AppControl)](/pt-br/appcontrol): conseguir listar todos os aplicativos instalados
* [Analisador de Armazenamento (StorageAnalyzer)](/pt-br/storageanalyzer): atribuir o armazenamento usado aos aplicativos instalados

Essa permissão existe desde o [Android 11](https://developer.android.com/training/package-visibility) e normalmente é concedida por padrão. Desde o Android 14, existem alguns dispositivos (por exemplo [#1015](https://github.com/d4rken-org/sdmaid-se/issues/1015), [#1354](https://github.com/d4rken-org/sdmaid-se/issues/1354)) em que a permissão não é concedida por padrão ou pode ser revogada.

[<img src="/assets/images/setup/app-inventory.webp" width="100">](/assets/images/setup/app-inventory.webp)

### Lista de aplicativos inválida
{: #invalid-app-list }

Em alguns dispositivos, a permissão "Lista de aplicativos" (`QUERY_ALL_PACKAGES` / `GET_INSTALLED_APPS`) está concedida, mas o sistema ainda retorna uma **lista incompleta de aplicativos instalados**, por exemplo uma lista sem pacotes essenciais do sistema como `android`. Quando o SD Maid detecta isso, ele mostra um aviso **"Inventário de aplicativos inválido"** e uma observação **"Possível limitação do dispositivo"** no cartão de configuração, em vez de continuar.

O SD Maid para aqui de propósito: se ele não consegue ver a lista completa de aplicativos instalados, não consegue saber com segurança quais arquivos pertencem a um aplicativo que ainda está instalado. Continuar mesmo assim poderia significar apagar dados que pertencem a um aplicativo que o SD Maid simplesmente não conseguiu ver, então os recursos relacionados a aplicativos ficam desativados até que uma lista completa esteja disponível.

**O que você pode tentar:**

* Abra as configurações do sistema e verifique se o SD Maid tem a permissão "Lista de aplicativos". Em alguns fabricantes, essa opção tem outro nome (por exemplo "Consultar todos os pacotes") ou fica em uma tela separada de "Privacidade" ou "Gerenciador de permissões".
* Verifique se algum economizador de bateria embutido, proteção de privacidade ou recurso de "ocultar aplicativos" está filtrando a lista de aplicativos que o SD Maid consegue ver, e coloque o SD Maid como exceção nele.
* Reinicie o dispositivo e abra o SD Maid de novo para que o estado da permissão seja reavaliado.

Se a permissão "Lista de aplicativos" já estiver permitida e a lista continuar sendo considerada inválida, normalmente isso é uma **limitação do dispositivo**, e não algo que você possa corrigir: alguns sistemas (por exemplo o HarmonyOS ou versões do Android muito modificadas) nunca entregam uma lista completa de aplicativos a aplicativos de terceiros. Veja [#2441](https://github.com/d4rken-org/sdmaid-se/issues/2441) para um exemplo.

Nesse caso, as ferramentas relacionadas a aplicativos ([Localizador de Cadáver](/pt-br/corpsefinder), [Limpador de Aplicativos](/pt-br/appcleaner), [Gerenciamento de aplicativos](/pt-br/appcontrol)) ficam desativadas para proteger seus dados, mas o [Analisador de Armazenamento](/pt-br/storageanalyzer) continua funcionando: ele mostra quanto espaço seu armazenamento está usando, só que com menos detalhes. Os tamanhos das pastas são mostrados somente para leitura e não podem ser atribuídos a aplicativos individuais.

## Serviço de acessibilidade
{: #accessibility-service }
O serviço de acessibilidade permite que o SD Maid interaja com o seu dispositivo como uma pessoa, ou seja, vendo a tela e tocando em botões. O SD Maid pode usá-lo para automatizar ações tediosas, como tocar em "Limpar cache" em centenas de aplicativos. O serviço de acessibilidade é um serviço que o próprio aplicativo não pode iniciar, ele precisa ser iniciado pelo sistema, porque esse tipo de serviço concede ao aplicativo acesso adicional ao seu dispositivo.

### ACS: solução de problemas
{: #acs-troubleshooting }

O serviço pode parar de funcionar e exigir que você o ative de novo em várias situações:
* Você atualiza o aplicativo.
* O aplicativo trava. Se isso acontecer, você deve relatar como um bug. Se isso acontecer com muita frequência, talvez você não consiga reiniciá-lo desligando e ligando de novo até que o dispositivo seja reiniciado.
* O sistema encerra o aplicativo por causa de "otimizações de bateria" agressivas demais (veja também <https://dontkillmyapp.com/>)
* O próprio aplicativo de segurança do fabricante o desliga (veja [Bloqueado pelo Phone Manager](#acs-blocked-by-phone-manager-oneplus-oppo-realme))

#### ACS: o serviço está desativado
{: #acs-service-is-disabled }

Algumas ROMs (por exemplo o MIUI) são famosas por interromper agressivamente tudo o que não está na lista de permissões do fabricante do dispositivo.
Se pedirem constantemente que você reative o serviço de acessibilidade, o sistema pode estar desativando-o toda vez que o SD Maid não está em uso ativo.

Tente desativar as otimizações de bateria e, nas ROMs MIUI, há uma configuração extra chamada "Autostart" (início automático); ative-a para o SD Maid. Em dispositivos Xiaomi/POCO/Redmi:

* Vá em `Security>Manage applications>Auto start in background` e permita isso para o SD Maid.
* Vá em `Security>Increase speed>Lock apps` e selecione o SD Maid.


Você também pode considerar conceder uma permissão adicional que [permite ao SD Maid reiniciar o serviço automaticamente](#acs-automatic-service-restart).

[<img src="/assets/images/setup/acs-disabled.webp" width="100">](/assets/images/setup/acs-disabled.webp)

#### ACS: bloqueado pelo Phone Manager (OnePlus, OPPO, Realme)
{: #acs-blocked-by-phone-manager-oneplus-oppo-realme }

No OxygenOS e no ColorOS, o aplicativo pré-instalado "Phone Manager" pode desligar o serviço de acessibilidade por conta própria, normalmente em até um segundo depois de uma limpeza terminar. A opção de acessibilidade do Android fica de fato desligada depois disso, então parece que o SD Maid parou de funcionar no meio da execução.

Desligue o recurso em `Phone Manager>Utilities>Block suspicious app activities`.

Essa mesma tela mantém uma lista do que foi bloqueado. Itens com o rótulo "Abnormal device control" (controle anormal do dispositivo) com horários que coincidem com as limpezas que você executou confirmam que essa é a causa. A chave "Blocking alerts" (alertas de bloqueio) ao lado só controla se você é notificado, então com os alertas desligados isso acontece silenciosamente.

#### ACS: ativar via ADB
{: #acs-enable-via-adb }

Se o seu dispositivo não tiver uma forma de ativar serviços de acessibilidade pela tela (por exemplo, uma TV), é possível ativar o serviço usando o ADB executando:

_Você também pode executar este comando usando o [Web ADB Helper](https://d4rken.github.io/web-adb/)._

```bash
adb shell settings put secure enabled_accessibility_services eu.darken.sdmse/.automation.core.AutomationService
```

#### ACS: reinício automático do serviço
{: #acs-automatic-service-restart }

_Observação: esta é uma técnica avançada, voltada para usuários avançados._

Você pode conceder uma permissão adicional que permite ao SD Maid reiniciar o serviço por conta própria.
A permissão se chama `WRITE_SECURE_SETTINGS` e só pode ser concedida [via ADB](https://www.google.com/search?q=android+adb).
Se você já tem isso configurado, execute este comando para conceder a permissão:

_Você também pode executar este comando usando o [Web ADB Helper](https://d4rken.github.io/web-adb/)._

```bash
adb shell pm grant eu.darken.sdmse android.permission.WRITE_SECURE_SETTINGS
```

Com essa permissão concedida, o SD Maid pode usar o comando [descrito aqui](#acs-enable-via-adb) para ativar o serviço quando necessário.
O SD Maid só vai usar isso se você tiver ativado o serviço de acessibilidade manualmente pelo menos uma vez.
Se você voltar à configuração e disser ao SD Maid para não usar o serviço de acessibilidade, essa técnica de reinício automático não será usada até que você ative manualmente o serviço de acessibilidade de novo.

Para remover essa permissão do SD Maid novamente, use este comando:

_Você também pode executar este comando usando o [Web ADB Helper](https://d4rken.github.io/web-adb/)._

```bash
adb shell pm revoke eu.darken.sdmse android.permission.WRITE_SECURE_SETTINGS
```

Se você receber `Operation not allowed: java.lang.SecurityException`, [veja aqui](https://stackoverflow.com/questions/52079343). Alguns dispositivos restringem as permissões via ADB. Veja se há uma configuração extra (por exemplo `Disable Permission Monitoring`) nas opções do desenvolvedor que precisa ser ativada; depois de usar o comando, você pode desativá-la de novo.

#### ACS: Proteção Avançada
{: #acs-advanced-protection }

No Android 17 e mais recentes, o modo [**Advanced Protection**](https://developer.android.com/privacy-and-security/advanced-protection-mode) (Proteção Avançada) do Google impede que aplicativos que não são ferramentas de acessibilidade dedicadas (leitores de tela, acesso com interruptor etc.) usem o serviço de acessibilidade. O SD Maid é uma ferramenta de manutenção/limpeza, então, enquanto a Proteção Avançada estiver ativada, o serviço de acessibilidade dele **não pode** ser ativado: o Android rejeita o serviço no nível do sistema, mesmo se você tentar concedê-lo por root ou acesso ADB.

O SD Maid não pode se isentar disso: só os aplicativos que o Google classifica como ferramentas de acessibilidade são elegíveis, e os limpadores ficam explicitamente de fora.

Você tem duas opções:

- **Usar root ou acesso ADB** Quando o root ou o acesso ADB está configurado, o Limpador de Aplicativos (AppCleaner) do SD Maid limpa os caches dos aplicativos por meio de uma chamada direta ao sistema e **não** precisa do serviço de acessibilidade. Com **root**, isso também cobre a limpeza de aplicativos selecionados individualmente. Com **acesso ADB**, uma execução completa de "limpar todos os aplicativos" funciona sem o serviço de acessibilidade, mas limpar o cache de aplicativos *específicos* selecionados ainda depende dele. Configure isso na Configuração do SD Maid → [Permissão root](#root-access) ou [Acesso ADB](#shizuku).
- **Desligar a Proteção Avançada** se você quiser especificamente a limpeza baseada em acessibilidade. Ela fica em `Settings → Security & privacy → Advanced Protection` (o caminho exato varia de acordo com o dispositivo). Religá-la depois vai desativar o serviço de acessibilidade novamente.

#### ACS: restrições do AppOps
{: #acs-appops-restrictions }

O Android 13+ pode aplicar restrições adicionais a aplicativos instalados por sideload. Então, se o SD Maid não foi instalado de uma fonte conhecida (pelo Google), como o Google Play ou outras lojas de aplicativos, talvez você não consiga conceder diretamente algumas permissões ao SD Maid, por exemplo iniciar um serviço de acessibilidade.

##### OxygenOS 15 - OnePlus
{: #oxygenos-15---oneplus }

A OnePlus esqueceu de incluir (ou simplesmente não incluiu) a ação `Allow restricted settings` (permitir configurações restritas) nas configurações do aplicativo no OxygenOS 15. Se você não estiver instalando pelo Google Play, o instalador precisa definir a flag `INSTALL_ALL_WHITELIST_RESTRICTED_PERMISSIONS`. Se você tiver o Shizuku configurado, pode usar o [aplicativo "Install with Options"](https://github.com/zacharee/InstallWithOptions).


##### Outras ROMs
{: #other-roms }

Para remover essas restrições, abra a tela de configurações do SD Maid no sistema. Abra o menu no canto superior direito e selecione `Allow restricted settings` (permitir configurações restritas); depois disso, você pode voltar à lista de serviços de acessibilidade e ativar o serviço do SD Maid.

**Observação:**
Para melhorar a usabilidade (isto é sarcasmo), o Google decidiu que essa configuração só fica visível se:

* Você viu pelo menos uma vez o aviso "Restricted setting" (configuração restrita) depois de tocar no item do serviço de acessibilidade do SD Maid
* Você abriu as configurações do sistema e navegou até a página de configurações do SD Maid manualmente, e não por outro aplicativo ou atalho.

😮‍💨


Algumas informações adicionais de contexto [podem ser encontradas aqui](https://blog.esper.io/android-13-sideloading-restriction-harder-malware-abuse-accessibility-apis/).

<a href="/assets/images/setup/acs-restricted-settings-1.webp"><img src="/assets/images/setup/acs-restricted-settings-1.webp" width="100" alt=""></a>
<a href="/assets/images/setup/acs-restricted-settings-2.webp"><img src="/assets/images/setup/acs-restricted-settings-2.webp" width="100" alt=""></a>
<a href="/assets/images/setup/acs-restricted-settings-3.webp"><img src="/assets/images/setup/acs-restricted-settings-3.webp" width="100" alt=""></a>
<a href="/assets/images/setup/acs-restricted-settings-4.webp"><img src="/assets/images/setup/acs-restricted-settings-4.webp" width="100" alt=""></a>

## Permissão root
{: #root-access }

Com acesso root, o SD Maid tem acesso total ao seu dispositivo e pode fazer a verificação mais completa possível. Normalmente isso significa que você desbloqueou o bootloader e configurou o Magisk.

Se o SD Maid achar que seu dispositivo pode ter root, ele vai pedir acesso root primeiro. Se o acesso root for concedido, o SD Maid vai usá-lo para conceder o máximo possível das outras permissões. O acesso root é o nível de acesso mais alto que você poderia dar a um aplicativo; as outras permissões passam então a ser necessárias apenas por conveniência e desempenho, e é por isso que elas não exigem consentimento extra durante a configuração se o SD Maid já tiver root.

## Acesso ADB
{: #shizuku }

O acesso ADB permite que o SD Maid use a interface de depuração do Android (ADB) para operações privilegiadas. Ele precisa de um aplicativo separado que execute um serviço ADB no seu dispositivo e dê a outros aplicativos acesso a ele: o [Porter](#porter) ou o [Shizuku](#using-shizuku). É uma modificação que você pode fazer no seu celular e da qual outros aplicativos podem tirar proveito.

É parecido com o root, mas não é a mesma coisa. As permissões são um pouco menos poderosas, mas a modificação não é permanente e não exige o desbloqueio do bootloader.

### Porter
{: #porter }

O [Porter](https://porter.darken.eu) é o meu fork mantido do Shizuku, com suporte opcional a root. O site dele explica como instalá-lo e iniciá-lo. No Android 11 e mais recentes, a depuração sem fio permite iniciá-lo sem um computador.

Se você está configurando o acesso ADB pela primeira vez, use o Porter. As versões do SD Maid do GitHub e do F-Droid indicam o Porter quando nenhum aplicativo de acesso ADB está instalado. A versão do Google Play indica o Shizuku no Google Play, mas funciona com o Porter da mesma forma.

Se o Porter e o Shizuku estiverem instalados, o SD Maid usa o Porter. O cartão de configuração então diz `Porter está instalado, então o SD Maid espera por Porter ao invés de Shizuku`. Inicie o Porter para continuar, ou desinstale-o se quiser que o SD Maid use o Shizuku.

### Usando o Shizuku
{: #using-shizuku }

Existem duas versões do Shizuku:

* O [fork do thedjchi](https://github.com/thedjchi/Shizuku) ([guia de configuração](https://github.com/thedjchi/Shizuku/wiki/setup)) é mantido ativamente e, segundo relatos, funciona em dispositivos onde o original [nunca se conecta](#shizuku-never-connects).
* O [original da RikkaApps](https://shizuku.rikka.app/guide/setup/) continua funcionando bem na maioria dos dispositivos.

Eles não podem ser instalados ao mesmo tempo. Se o original já funciona para você, não há necessidade de trocar.

### Concedendo acesso
{: #granting-access }

Depois de configurar o Porter ou o Shizuku:

* Na configuração do SD Maid, escolha `Tente usar o acesso ADB`. Isso é interno ao SD Maid, você pode usar para ativar/desativar o uso do acesso ADB pelo SD Maid
* Depois de dar o consentimento, uma caixa de diálogo do Porter ou do Shizuku pergunta se o SD Maid pode usá-lo
* Confirme a caixa de diálogo
* O cartão de configuração `Acesso ADB` mostra se o SD Maid consegue se conectar

Configurar o acesso ADB quando você já concedeu acesso root ao SD Maid não ativa recursos adicionais, mas melhora a confiabilidade. Se as ações falharem com a permissão root, o SD Maid vai tentar de novo com o acesso ADB.

### Acesso ADB: solução de problemas
{: #shizuku-troubleshooting }

Você pode conferir o status do acesso ADB do SD Maid indo em `Configurações > Configuração` e olhando o cartão de configuração `Acesso ADB`. Ele deve dizer `O SD Maid agora pode usar o acesso ao ADB através de Porter` (ou `através de Shizuku`).

* `Porter ainda não permitiu o SD Maid`: confirme a caixa de diálogo de acesso ou permita o SD Maid no aplicativo Porter (ou Shizuku).
* `Porter negou o acesso ao SD Maid`: o SD Maid foi negado permanentemente. Abra o Porter (ou o Shizuku) e permita o SD Maid lá.
* `Atualize o Porter para usá-lo com o SD Maid` ou `Esta versão do SD Maid é muito antiga para o Porter`: os dois aplicativos não têm versões compatíveis. Atualize aquele que o cartão indicar.

#### O Shizuku nunca se conecta
{: #shizuku-never-connects }

O cartão diz `Falha ao acessar ADB` e `O Shizuku está funcionando, mas nunca conectou o SD Maid ao seu serviço auxiliar.` Versões mais antigas diziam `Waiting for link with Shizuku` (aguardando conexão com o Shizuku) ou `Shizuku link failed` (falha na conexão com o Shizuku).

Abra o aplicativo Shizuku. Se ele disser que está em execução e o SD Maid estiver na lista de autorizados, o Shizuku está configurado corretamente e configurá-lo de novo não vai ajudar. O que está falhando é o serviço auxiliar do Shizuku: ele morre na inicialização antes de conseguir dar retorno, e o SD Maid desiste depois de cerca de 15 segundos. `Tentar novamente` executa a mesma verificação e falha da mesma forma.

Isso é um bug do Shizuku ([RikkaApps/Shizuku#1198](https://github.com/RikkaApps/Shizuku/issues/1198)), não do SD Maid, e afeta qualquer aplicativo que use o serviço auxiliar do Shizuku. Ele afeta dispositivos MediaTek e dispositivos com HyperOS ou MIUI.

Segundo relatos, o [fork do thedjchi](https://github.com/thedjchi/Shizuku) corrige isso. Para trocar:

* Desinstale o original primeiro, eles não podem coexistir
* Desfaça o pareamento dele na depuração sem fio, se você usou isso
* Instale o fork pelas [versões](https://github.com/thedjchi/Shizuku/releases) dele e siga o [guia de configuração](https://github.com/thedjchi/Shizuku/wiki/setup)
* Conceda de novo a permissão do Shizuku ao SD Maid e a tudo o mais que o usava
