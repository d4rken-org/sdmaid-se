---
title: "Compressor de Mídia"
lang: pt-BR
translation_key: mediasqueeze
permalink: /pt-br/mediasqueeze
source_hash: 1d878fd5a77634c5
---
# Compressor de Mídia (Media Squeeze)
{: #media-squeeze }

O Compressor de Mídia (Media Squeeze) comprime fotos e vídeos para que ocupem menos espaço. Você troca um pouco de qualidade pelo espaço que recupera.

**A compressão reduz a qualidade permanentemente e não pode ser desfeita.** O arquivo original é substituído e nenhuma cópia é mantida. Confira suas configurações e a pré-visualização antes de começar.

A verificação é gratuita. Comprimir requer o upgrade.

## Como funciona
{: #how-it-works }

1. Escolha as pastas a pesquisar. Não há um local padrão, escolha por exemplo a pasta da sua câmera.
2. Defina a qualidade de compressão e a idade mínima.
3. Faça a verificação. Cada resultado mostra o tamanho e uma economia estimada.
4. Toque em um único item, selecione vários tocando e segurando ou use `Comprimir tudo`. Antes, uma confirmação mostra o tamanho total e a economia estimada.

Se a seleção contiver apenas fotos ou apenas vídeos, `Ver comparação` na confirmação mostra o original e a versão comprimida lado a lado. Para vídeos, a comparação é um único quadro e apenas aproximada.

### O que acontece com um arquivo
{: #what-happens-to-a-file }

* Ele é recodificado no próprio formato: um JPEG continua JPEG, um WebP continua WebP, um MP4 continua MP4.
* A resolução da imagem continua a mesma, exceto para [fotos muito grandes](#compress-very-large-photos).
* Nos vídeos, apenas a faixa de vídeo é recodificada com uma taxa de bits menor. A faixa de áudio é copiada sem alterações.
* A data, a localização, a câmera e outros metadados EXIF das fotos são mantidos. Se o SD Maid não conseguir ler ou preservar os metadados EXIF de uma foto, ele deixa a foto inalterada.
* Os metadados XMP não são mantidos, por exemplo informações de photo sphere e panorama ou classificações de editores de fotos.
* A data de modificação do arquivo é mantida, para que as galerias continuem a ordená-lo no mesmo lugar.
* Se a versão comprimida não for menor, o original fica intacto.

A economia estimada é um palpite aproximado com base no tipo de arquivo e na qualidade, não uma medição. A economia real pode ser diferente.

### Locais
{: #locations }

O Compressor de Mídia precisa de acesso direto aos arquivos e de acesso de gravação às pastas deles. Ele não usa root nem ADB. Alguns locais escolhidos pelo seletor de pastas do Android, por exemplo pen drives USB, não podem ser usados e são removidos da seleção.

## Arquivos já comprimidos
{: #previously-compressed-files }

O SD Maid lembra quais arquivos já comprimiu, e quais não ficaram menores, e os pula nas próximas verificações. Ele reconhece um arquivo pelo conteúdo, então isso também funciona depois que o arquivo foi renomeado ou movido. Comprimir um arquivo de novo custa mais qualidade e economiza pouco.

O histórico é mantido no seu dispositivo e faz parte do backup das configurações do SD Maid. Limpá-lo torna os arquivos já comprimidos elegíveis de novo, a menos que tenham um marcador EXIF e [Gravar marcador EXIF](#write-exif-marker) esteja ativado. Limpá-lo não altera nenhum arquivo.

## Fotos puladas por padrão
{: #photos-that-are-skipped-by-default }

Algumas fotos contêm dados que a compressão não consegue manter. O SD Maid as pula se detectar esses dados, a menos que você ative a configuração correspondente. A detecção não é completa: fotos em formatos que ele não reconhece são comprimidas e perdem esses dados.

* **Fotos HDR e com profundidade**: fotos HDR se tornam fotos normais, e fotos de retrato perdem os dados por trás do desfoque do fundo.
* **Motion Photos**: a imagem estática é mantida, o clipe de vídeo é removido.
* **Fotos muito grandes** (8192 pixels ou mais de largura ou altura): a largura e a altura são reduzidas pela metade, e de novo pela metade se necessário, até que ambas fiquem abaixo de 8192 pixels. Um panorama de 20.000 pixels de largura acaba com 5.000 pixels de largura.

Arquivos HEIC que contêm várias imagens são sempre pulados.

O resumo da verificação conta as fotos puladas por terem dados HDR ou de profundidade, Motion Photos e tamanho muito grande, e os arquivos que não estavam acessíveis. Arquivos pulados porque já foram comprimidos antes não são contados, nem arquivos HEIC com várias imagens, a menos que também tenham dados HDR ou de profundidade.

## Configurações
{: #settings }

A qualidade de compressão, os locais de pesquisa e a idade mínima são definidos na tela do Compressor de Mídia antes da verificação. Todo o resto fica em `Configurações > Compressor de Mídia`.

### Qualidade de compressão
{: #compression-quality }

Padrão 80. Uma qualidade menor significa arquivos menores, mas com qualidade visual reduzida. Abaixo de 40, as imagens podem ficar inutilizáveis. A partir de 95 há pouco a economizar.

### Idade mínima
{: #minimum-age }

Inclui apenas arquivos mais antigos que isso. Padrão de 90 dias, com base na data de modificação do arquivo.

### Tamanho mínimo do arquivo
{: #minimum-file-size }

Pula arquivos menores que isso. Padrão de 512 KB.

### JPEG
{: #jpeg }

Inclui imagens JPEG (`.jpg`, `.jpeg`). Ativado por padrão.

### WebP
{: #webp }

Inclui imagens WebP (`.webp`). Ativado por padrão. Arquivos WebP animados não são detectados e perderiam a animação.

### HEIC/HEIF
{: #heicheif }

Inclui imagens HEIC/HEIF (`.heic`, `.heif`). Desativado por padrão. Requer Android 9 ou mais recente. Mapas de ganho HDR, dados de profundidade e partes de Live/Motion Photo não podem ser preservados em HEIC.

### Vídeo MP4
{: #mp4-video }

Inclui vídeos MP4 (`.mp4`). Desativado por padrão. Comprimir um vídeo precisa de espaço livre de cerca de 110% do tamanho dele no mesmo armazenamento.

### Comprimir fotos HDR & profundidade
{: #compress-hdr--depth-photos }

Veja [Fotos puladas por padrão](#photos-that-are-skipped-by-default). Desativado por padrão.

### Comprimir Motion Photos
{: #compress-motion-photos }

Mantém a imagem estática e remove o clipe de vídeo. Desativado por padrão.

### Comprimir fotos muito grandes
{: #compress-very-large-photos }

Inclui fotos com 8192 pixels ou mais de largura ou altura e reduz a resolução delas, veja [Fotos puladas por padrão](#photos-that-are-skipped-by-default). Desativado por padrão.

### Pular já comprimidas
{: #skip-previously-compressed }

Pula arquivos que o SD Maid já comprimiu. Ativado por padrão.

### Gravar marcador EXIF
{: #write-exif-marker }

Adiciona um marcador ao comentário de usuário EXIF da foto, para que o SD Maid a reconheça como comprimida mesmo depois que o histórico de compressão foi limpo. O SD Maid só procura o marcador enquanto esta configuração estiver ativada. Desativado por padrão. Vale apenas para JPEG e WebP.

### Histórico de compressão
{: #compression-history }

Mostra quantos arquivos estão no histórico. Toque para limpar o histórico.

## Exceções
{: #exclusions }

O Compressor de Mídia pula caminhos que têm [exceções](/pt-br/exclusions) gerais. Você também pode criar exceções pela lista de resultados, o que afeta apenas o Compressor de Mídia.

O Compressor de Mídia não pode ser executado pelo [Agendador (Scheduler)](/pt-br/scheduler).
