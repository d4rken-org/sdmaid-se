---
title: "Gerenciamento de aplicativos"
lang: pt-BR
translation_key: appcontrol
permalink: /pt-br/appcontrol
source_hash: 1a4a7450fc5b86ca
---
# Gerenciamento de aplicativos (AppControl)
{: #appcontrol }
O Gerenciamento de aplicativos (AppControl) reúne em um só lugar todas as ações relacionadas ao controle dos aplicativos instalados. Após a verificação, você terá uma lista pesquisável dos seus aplicativos instalados. Os aplicativos do sistema não são mostrados por padrão, isso precisa ser ativado nas configurações.
Algumas opções só podem estar disponíveis (ser possíveis) se o SD Maid tiver recebido permissões de root.

Algumas ações podem ser executadas em vários aplicativos, outras apenas em itens individuais. Para executar ações em vários aplicativos, toque e segure um aplicativo e depois toque para selecionar outros aplicativos.

## Ações
{: #actions }
Algumas dessas ações só estão acessíveis nos detalhes do aplicativo, que podem ser abertos tocando em um item da lista.

### Iniciar aplicativo
{: #run-app }
Inicia o aplicativo. Sua atividade principal, normalmente a mesma ação que o inicializador também executaria.

### Forçar parada
{: #force-stop }
Tenta forçar a parada do aplicativo. Funciona melhor com root ou ADB/Shizuku, caso contrário usa o serviço de acessibilidade.

### Exportar apk
{: #export-apk }
Tenta copiar o arquivo `.apk` do aplicativo do armazenamento interno privado para a pasta de downloads do armazenamento público. Se o aplicativo estiver criptografado, esta ação requer root; aplicativos não criptografados também podem ser exportados sem root.

### Congelar aplicativo
{: #freeze-app }
Requer root ou ADB/Shizuku. Desativa o aplicativo. O aplicativo não ficará visível e não pode se iniciar sozinho. Ele precisa ser ativado novamente para funcionar. Nenhum dado é perdido, e esse estado também persiste se você desinstalar o SD Maid. Qualquer bom aplicativo para ativar/desativar aplicativos consegue ativar aplicativos que foram desativados com o SD Maid, e vice-versa.

### Apagar aplicativo
{: #delete-app }
Apaga o aplicativo e seus dados.


## Configurações
{: #settings }
### Determinar o tamanho
{: #determine-size }
Pode ser desativado para acelerar o carregamento da lista de aplicativos. Determinar o tamanho de um aplicativo leva muito tempo.

### Determinar a atividade
{: #determine-activity }
Pode ser desativado para acelerar o carregamento da lista de aplicativos. Determinar se um aplicativo esteve ativo recentemente leva muito tempo.
