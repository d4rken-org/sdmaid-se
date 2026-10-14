---
title: "Limpador de Aplicativos"
lang: pt-BR
translation_key: appcleaner
permalink: /pt-br/appcleaner
source_hash: 505b031f74bf71a2
---
# Limpador de Aplicativos (AppCleaner)
{: #app-cleaner }

O Limpador de Aplicativos (AppCleaner) é uma ferramenta que reconhece os aplicativos e encontra arquivos "dispensáveis". Um arquivo é considerado dispensável se puder ser apagado sem efeitos adversos e, além disso, não for necessário ou for recriado quando preciso. A maioria dos tipos de arquivo em um "cache" se encaixa nessa definição.

## Configurações
{: #settings }

### Incluir apps do sistema
{: #include-system-apps }

Esta configuração inclui os aplicativos do sistema nos resultados. Ela não vem ativada por padrão porque alguns aplicativos do sistema (dependendo da ROM) reagem mal quando seu cache é limpo. Alguns aplicativos do sistema também não têm um botão "Limpar cache", o que pode fazer o SD Maid travar ao limpar o cache pelo serviço de acessibilidade.

### Incluir outros usuários
{: #include-other-users }

Requer root. Se ativado, o SD Maid vai procurar nas pastas de outros usuários, além do usuário que está executando o SD Maid no momento.

### Incluir apps em execução
{: #include-running-apps }

Define se os aplicativos em execução devem ser incluídos ou não.

### Tamanho mínimo
{: #minimum-size }

O tamanho que o cache de um aplicativo precisa ter para aparecer na lista. Definir um tamanho mínimo é um bom equilíbrio entre utilidade e velocidade. Limpar o cache de um aplicativo que tem apenas alguns KB normalmente não traz nenhum benefício. Isso vale para a quantidade total de cache encontrada para um aplicativo (considerando todos os filtros ativos)

### Idade mínima
{: #minimum-age }

Pode ser usada para apagar apenas arquivos de cache antigos. É aplicada a cada arquivo ou pasta nos resultados.

### Incluir caches inacessíveis
{: #include-inaccessible-caches }

Dependendo das permissões disponíveis para o SD Maid, alguns arquivos podem não ser acessíveis diretamente. Normalmente isso se refere ao cache privado padrão dos aplicativos (por exemplo, `/data/data/eu.darken.sdmse/cache`). Se esta configuração estiver ativada, o SD Maid vai tentar usar um serviço de acessibilidade para navegar até as configurações do sistema e tocar no botão `Limpar cache` do aplicativo em questão.

### Filtro
{: #filter }

Você pode ativar ou desativar diferentes filtros para influenciar quais resultados o Limpador de Aplicativos vai encontrar.

#### Filtros genéricos
{: #common-filters }

Os filtros genéricos visam vários tipos de arquivo em todos os aplicativos.

#### Filtros específicos
{: #specific-filters }

Os filtros específicos visam aplicativos específicos, por exemplo WhatsApp ou Telegram.

## Solução de problemas
{: #troubleshooting }
### `com.miui.securitycenter`
{: #commiuisecuritycenter }
#### Permissão `GET_USAGE_STATS` ausente
{: #missing-get_usage_stats-permission }

O MIUI e o HyperOS usam um aplicativo extra chamado "Security" ou "Security Center" ("Segurança", nome do pacote: `com.miui.securitycenter`) para mostrar os detalhes do sistema de um aplicativo. O fluxo normal de ações para limpar o cache de um aplicativo é: tocar em `Clear data` ("Limpar dados"), selecionar `Clear cache` ("Limpar cache") e confirmar a ação.

Se o botão "Clear data" ("Limpar dados") estiver acinzentado em todos os aplicativos e não puder ser tocado, então falta ao aplicativo "Security Center" a permissão `GET_USAGE_STATS`. Normalmente ela deveria ser concedida por padrão. Não está claro como isso acontece, mas a solução é conceder essa permissão novamente.

Vá para: `System Settings` > `Security and privacy` > `More security and privacy` > `Special permissions` > `Usage access` > encontre o aplicativo `Security` > conceda a permissão

Como alternativa, se você tiver acesso ADB ou superior, execute o comando `pm grant com.miui.securitycenter android.permission.PACKAGE_USAGE_STATS`

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
