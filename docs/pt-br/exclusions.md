---
title: "Exceções"
lang: pt-BR
translation_key: exclusions
permalink: /pt-br/exclusions
source_hash: d5c3aea2efa43796
---
# Exceções
{: #exclusions }

O recurso de exceções do SD Maid 2/SE permite que os usuários definam pastas ou arquivos específicos que o aplicativo deve ignorar durante as verificações do sistema e as tarefas de manutenção. Isso é útil se houver dados que você quer manter fora do alcance dos processos do aplicativo. Ao definir exceções, o SD Maid 2/SE vai pular esses diretórios ou arquivos durante tarefas como o Localizador de Cadáver (CorpseFinder), o Limpador de Aplicativos (AppCleaner) e outros recursos de gerenciamento do sistema.

A maneira mais fácil de adicionar uma exceção é diretamente pelos resultados. Você pode usar o botão "Ignorar" em itens individuais ou tocar e segurar/selecionar vários itens e depois escolher a opção "Ignorar". Além disso, as exceções podem ser adicionadas manualmente nas configurações do aplicativo, especificando caminhos ou padrões com curingas para ter mais controle.

As exceções podem ser exportadas e importadas pelo menu. A importação de exceções só é suportada em versões parecidas do SD Maid. Se houver atualizações demais entre a versão da importação e a da exportação, pode ocorrer um erro. Nesse caso, você pode tentar importar em uma versão anterior e depois exportar as exceções novamente.

<img src="/assets/images/exclusions/overview-1.webp" width="150">
<img src="/assets/images/exclusions/overview-2.webp" width="150">
<img src="/assets/images/exclusions/overview-3.webp" width="150">
<img src="/assets/images/exclusions/overview-4.webp" width="150">

## Tipos
{: #types }

O SD Maid suporta 3 tipos diferentes de exceções:

### Exceções de aplicativo
{: #app-exclusions }

As exceções de aplicativo se baseiam no nome do pacote de cada aplicativo. Elas podem ser criadas pela ferramenta Gerenciamento de aplicativos (AppControl). Basta escolher um aplicativo da lista e usar a ação `Ignorar` correspondente.

### Exceções de caminho
{: #path-exclusions }

As exceções de caminho podem ser criadas selecionando (tocando e segurando) caminhos na ferramenta Analisador de Armazenamento (StorageAnalyzer).

### Exceções por segmentos 
{: #segment-exclusions }
As exceções por segmentos são um tipo de exceção de caminho que não exige uma correspondência exata. Um segmento é uma parte de um caminho, como uma pasta. O caminho `/sdcard/DCIM/Camera` é composto de 3 segmentos. Você poderia deixar de fora todas as fotos da câmera tanto do armazenamento interno (`/storage/emulated/0/DCIM/Camera`) quanto do armazenamento externo (`/sdcard/DCIM/Camera`) criando uma exceção por segmentos para `DCIM/Camera`.

Se você marcar a opção `Permitir correspondências parciais`, poderia criar uma exceção para `DCIM/Cam` que corresponde tanto à pasta `Cam` quanto à pasta `Camera`.

## Ferramentas
{: #tools }

Cada ferramenta do SD Maid tem uma visão diferente dos dados. Nem todas as exceções afetam todas as ferramentas da mesma forma.

### Localizador de Cadáver
{: #corpsefinder }

A ferramenta Localizador de Cadáver sabe o que é um "aplicativo" e quais arquivos pertencem a um aplicativo. Ela respeita todos os tipos de exceção. Criar uma exceção de aplicativo significa que todos os arquivos que o Localizador de Cadáver encontrar e considerar pertencentes a um aplicativo com exceção serão deixados de fora dos resultados.

Tipos de exceção suportados: `APP`, `PATH`, `SEGMENTS`

### Limpador do sistema
{: #systemcleaner }

A ferramenta Limpador do sistema (SystemCleaner) trabalha apenas com dados de caminho. Ela não sabe o que é um "aplicativo" nem quais arquivos pertencem a um aplicativo.

Tipos de exceção suportados: `PATH`, `SEGMENTS`

### Limpador de Aplicativos
{: #appcleaner }

A ferramenta Limpador de Aplicativos respeita todos os tipos de exceção. Você pode usar exceções para acelerar as verificações se houver aplicativos com muitos arquivos a verificar.

Tipos de exceção suportados: `APP`, `PATH`, `SEGMENTS`

### Desduplicador
{: #deduplicator }

A ferramenta Desduplicador (Deduplicator) não sabe o que são "aplicativos" e trabalha apenas com dados de arquivos.

Tipos de exceção suportados: `PATH`, `SEGMENTS`

### Compressor de Mídia
{: #media-squeeze }

O Compressor de Mídia (Media Squeeze) trabalha apenas com dados de arquivos. Arquivos com exceção não são comprimidos.

Tipos de exceção suportados: `PATH`, `SEGMENTS`

### Deslizador
{: #swiper }

O Deslizador (Swiper) trabalha apenas com dados de arquivos. Arquivos com exceção não são incluídos em novas sessões. Tocar e segurar `Pular` enquanto desliza cria uma exceção para o arquivo atual.

Tipos de exceção suportados: `PATH`, `SEGMENTS`

### Analisador de Armazenamento
{: #storageanalyzer }

O Analisador de Armazenamento não suporta exceções, pois todo o conteúdo precisa ser verificado para calcular os tamanhos totais corretos.
