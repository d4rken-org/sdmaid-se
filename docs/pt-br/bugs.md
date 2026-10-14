---
title: "Bugs"
lang: pt-BR
translation_key: bugs
permalink: /pt-br/bugs
source_hash: a218be31a6c4ae63
---
# Bugs
{: #bugs }

🐌 🦋 🐛 🐜 🐝 🐞

Bugs fazem parte da vida de um aplicativo. Eles não querem fazer mal a ninguém, mas também não vieram para ficar 😠.

O que são bugs?

* Se o aplicativo trava, obviamente é um bug
* Se algo está se comportando de forma inesperada, pode ser um bug.

Em qualquer caso, verifique se você está na atualização mais recente, o bug pode já ter sido corrigido.

Se o bug não foi corrigido, relate-o :grin:.

## Relatando um bug
{: #reporting-a-bug }

Crie uma issue no GitHub:
<https://github.com/d4rken-org/sdmaid-se/issues>

* Descreva o que você esperava e o que realmente aconteceu
* Liste os passos que outra pessoa teria que seguir para reproduzir o problema
* Descreva sua configuração
    * O modelo do seu dispositivo (por exemplo, Pixel 5)
    * A versão do Android (por exemplo, Android 13.1)
    * Se você tiver root, a versão do Magisk

Inclua um [log de depuração](#debug-log) se você conseguir reproduzir o problema. 

Dica: você pode [tocar e segurar o item "Atualizações"](https://github.com/d4rken-org/sdmaid-se/pull/940) nas configurações do SD Maid para copiar a versão para a área de transferência.

## Log de depuração
{: #debug-log }

Um log de depuração é uma gravação das ações do SD Maid. Você inicia a gravação, reproduz o problema e depois a interrompe, podendo compartilhar um arquivo zip com um relato cronológico do que o SD Maid fez.

Para começar a gravar um log de depuração:
* Abra as configurações do SD Maid tocando no ícone de configurações no canto inferior direito do painel
* Encontre o item `Suporte` na lista de configurações
* Toque em `Gravar log de depuração` na tela de suporte e depois em `Gravar log de depuração` novamente na caixa de diálogo
* Durante a gravação, o painel mostra um cartão `Gravando log de depuração`
* **Agora reproduza o problema que você quer gravar**
* Depois de reproduzir o problema, toque em `Parar gravação` nesse cartão do painel ou na tela de suporte. Se a gravação for muito curta, o SD Maid pergunta antes; toque em `Parar mesmo assim` para interrompê-la.
* A tela `Registro de depuração` é aberta. Assim que o arquivo de log for compactado, toque em `Compartilhar` para enviá-lo.

O log de depuração será gravado até você interrompê-lo, a gravação continua mesmo se você forçar a parada do SD Maid e abri-lo de novo.

Se você estiver investigando problemas relacionados às funções principais, por exemplo a detecção de root ou as áreas de armazenamento detectadas, é útil forçar a parada do SD Maid e abri-lo de novo durante a gravação, pois alguns dados só são inicializados uma vez por sessão do aplicativo.

Os arquivos de log de depuração são armazenados em `/storage/emulated/0/Android/data/eu.darken.sdmse/cache/debug/logs`.

Em imagens:

<a href="/assets/images/bugs/debug-log-1.webp"><img src="/assets/images/bugs/debug-log-1.webp" width="100" alt=""></a>
<a href="/assets/images/bugs/debug-log-2.webp"><img src="/assets/images/bugs/debug-log-2.webp" width="100" alt=""></a>
<a href="/assets/images/bugs/debug-log-3.webp"><img src="/assets/images/bugs/debug-log-3.webp" width="100" alt=""></a>
<a href="/assets/images/bugs/debug-log-4.webp"><img src="/assets/images/bugs/debug-log-4.webp" width="100" alt=""></a>
<a href="/assets/images/bugs/debug-log-5.webp"><img src="/assets/images/bugs/debug-log-5.webp" width="100" alt=""></a>
<a href="/assets/images/bugs/debug-log-6.webp"><img src="/assets/images/bugs/debug-log-6.webp" width="100" alt=""></a>
<a href="/assets/images/bugs/debug-log-7.webp"><img src="/assets/images/bugs/debug-log-7.webp" width="100" alt=""></a>
<a href="/assets/images/bugs/debug-log-8.webp"><img src="/assets/images/bugs/debug-log-8.webp" width="100" alt=""></a>
