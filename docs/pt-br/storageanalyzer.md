---
title: "Analisador de Armazenamento"
lang: pt-BR
translation_key: storageanalyzer
permalink: /pt-br/storageanalyzer
source_hash: 2dbf8557900cc687
---
# Analisador de Armazenamento (Storage Analyzer)
{: #storage-analyzer }

O Analisador de Armazenamento (StorageAnalyzer) responde à pergunta "O que está ocupando tanto espaço no meu armazenamento?"

Ele pode parecer um navegador de arquivos, mas não é um navegador de arquivos. Tudo é ordenado por tamanho, para que você possa seguir os maiores itens até chegar ao que realmente está enchendo seu armazenamento.

## Armazenamentos
{: #storages }

A primeira tela mostra cada armazenamento do seu dispositivo:

* **Armazenamento primário**: o armazenamento interno do seu dispositivo, incluindo aplicativos e dados do sistema.
* **Armazenamento secundário**: normalmente um cartão SD removível.
* **Armazenamento portátil**: por exemplo, um pen drive conectado via USB.

Cada cartão mostra o espaço livre e o usado, e quanto o armazenamento mudou nos últimos 7 dias, veja [Tendência de Armazenamento](#storage-trend).

## Categorias
{: #categories }

Abrir um armazenamento faz a verificação dele e divide o espaço usado em categorias:

* **Aplicativos**: aplicativos instalados com seu código, dados, caches e mídia.
* **Arquivos do usuário**: fotos, vídeos, downloads e quaisquer outros dados que não pertencem a um aplicativo específico.
* **Dados do sistema**: o sistema operacional e tudo o que não pôde ser atribuído a outro lugar.
* **Outros usuários**: aplicativos e arquivos de outros usuários ou perfis de trabalho neste dispositivo.

Dados do sistema e Outros usuários só existem no armazenamento primário.

### Aplicativos
{: #apps }

Os aplicativos são ordenados por tamanho e podem ser pesquisados pelo nome. Os aplicativos do sistema também podem ser listados aqui, incluindo seus dados. Para aplicativos do sistema sem uma atualização instalada, o código do aplicativo embutido conta como Dados do sistema. Abrir um aplicativo mostra para onde vai o espaço dele:

* **Código da aplicação**: o próprio aplicativo. Desinstalar ou arquivar o aplicativo libera esse espaço.
* **Dados do aplicativo**: dados, configurações e caches.
* **Mídia da aplicação**: arquivos de mídia deste aplicativo, por exemplo fotos recebidas no WhatsApp.
* **Arquivos extras do aplicativo**: dados que o aplicativo colocou fora das próprias pastas, por exemplo fotos ou downloads em uma pasta de nível superior.

`Mostrar configurações` abre a página do aplicativo nas configurações do sistema.

### Por que os números não batem com as configurações do Android
{: #why-the-numbers-dont-match-androids-settings }

As categorias do analisador não vão corresponder exatamente à divisão mostrada nas configurações do Android.

**Dados do sistema** é o que sobra do espaço usado depois que Aplicativos, Arquivos do usuário e Outros usuários são contabilizados. Tudo o que o SD Maid não consegue ver ou atribuir vai parar ali: áreas protegidas do sistema, armazenamento de outros usuários que não pôde ser medido, a sobrecarga do sistema de arquivos e a diferença entre o tamanho anunciado e o tamanho real do armazenamento.

Sem as permissões [Inventário do aplicativo](/pt-br/setup#app-inventory) e [Estatísticas de uso](/pt-br/setup#usage-statistics), os aplicativos não podem ser medidos. O espaço deles então aparece como Dados do sistema.

## Acesso
{: #access }

O que o analisador consegue ver depende do acesso que o SD Maid tem:

* **Sem root**, o código dos aplicativos e os dados privados dos aplicativos são mostrados apenas como totais e não podem ser navegados. No Android 13 e mais recentes, `Android/data` também precisa de root ou acesso ADB.
* **Com [root](/pt-br/setup#root-access) ou [acesso ADB](/pt-br/setup#shizuku)**, você pode navegar pelos Dados do sistema. O root também permite navegar pelo código dos aplicativos e pelos dados privados dos aplicativos, além de medir completamente os outros usuários.
* **Sem a permissão Inventário do aplicativo**, os arquivos não podem ser associados aos aplicativos a que pertencem. Os Arquivos do usuário são então mostrados somente para leitura: você pode ver os tamanhos, mas apagar, criar exceções, criar filtros e criar sessões do Deslizador ficam desativados.

Itens que o SD Maid não consegue acessar mostram `Nenhum acesso direto disponível` quando tocados.

Pastas com mais de 100.000 itens são apenas totalizadas e não podem ser navegadas.

## Ações
{: #actions }

Toque em um arquivo para abri-lo em outro aplicativo. Toque e segure os itens para selecioná-los e depois:

* **Apagar**: apaga os itens selecionados após uma confirmação. Uso gratuito.
* **Ignorar**: cria uma [exceção](/pt-br/exclusions) para os caminhos selecionados. Essas exceções valem para todas as ferramentas. O próprio analisador não usa exceções, pois precisa ver tudo para calcular os totais corretos.
* **Filtrar**: cria um [filtro personalizado do Limpador do sistema (SystemCleaner)](/pt-br/systemcleaner#custom-filters) para os itens selecionados. Requer o upgrade.
* **Criar sessão do Swiper**: inicia uma sessão do [Deslizador (Swiper)](/pt-br/swiper) com os itens selecionados.

Os Dados do sistema e o conteúdo de outros usuários só podem ser navegados. Não é possível apagar nada ali.

## Tendência de Armazenamento
{: #storage-trend }

O SD Maid registra o espaço livre dos seus armazenamentos a cada poucas horas e após cada limpeza. `Tendência de Armazenamento` mostra isso em um gráfico, com marcadores indicando quanto cada limpeza liberou.

* Os cartões de armazenamento mostram quanto cada armazenamento mudou nos últimos 7 dias.
* O painel mostra a mudança combinada de todos os armazenamentos ou, em vez disso, uma estimativa dos dias restantes quando o armazenamento está enchendo.
* Por quanto tempo os registros são mantidos é definido em `Configurações > Histórico > Histórico de armazenamento`. O padrão é 90 dias, até 365.

O gráfico Tendência de Armazenamento requer o upgrade. A mudança de 7 dias nos cartões de armazenamento e no painel, e a estimativa de dias restantes, são gratuitas.

## Configurações
{: #settings }

### Limite de armazenamento baixo
{: #low-storage-threshold }

Quando o espaço livre cai abaixo desse valor, o SD Maid considera que o armazenamento está acabando. Padrão `Automático`: 5% do tamanho do armazenamento, no máximo 2 GB.

### Aviso de espaço insuficiente
{: #low-space-warning }

Requer o upgrade. Avisa quando o espaço livre no seu armazenamento primário atinge o limite de armazenamento baixo. Com pelo menos 5 dias de histórico consistente, ele também pode avisar antes: quando o armazenamento está a caminho de atingir o limite em até 14 dias e o espaço livre já é de no máximo 20% do armazenamento, limitado a 25 GB. Desativado por padrão.
