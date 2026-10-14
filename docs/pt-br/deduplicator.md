---
title: "Desduplicador"
lang: pt-BR
translation_key: deduplicator
permalink: /pt-br/deduplicator
source_hash: 08f0bb39baf9fb1e
---
# Desduplicador (Deduplicator)
{: #deduplicator }

O Desduplicador (Deduplicator) encontra arquivos duplicados no seu dispositivo: cópias exatas e, opcionalmente, imagens, vídeos ou áudios com aparência ou som parecidos. Ele os agrupa em conjuntos e apaga todos os arquivos de cada conjunto, menos um.

A verificação é gratuita. Apagar requer o upgrade.

## Métodos de detecção
{: #detection-methods }

Você pode ativar cada método em `Configurações > Desduplicador`.

### Soma de verificação de conteúdo
{: #content-checksum }

Ativado por padrão. Encontra arquivos com exatamente o mesmo conteúdo, byte a byte, independentemente dos nomes. Este é o método seguro: todo arquivo em um conjunto desses é uma cópia verdadeira.

### Hash perceptivo
{: #perceptual-hash }

Desativado por padrão. Encontra imagens parecidas, por exemplo a mesma foto salva em outro formato ou tamanho, ou com pequenas edições. As imagens são comparadas em tons de cinza, então imagens que só diferem na cor podem corresponder.

### Impressão digital de mídia
{: #media-fingerprint }

Desativado por padrão. Encontra vídeos e arquivos de áudio com som parecido, e vídeos sem som com aparência parecida. Só são comparados arquivos de duração aproximadamente igual.

Arquivos semelhantes não são idênticos. Fotos em sequência, versões editadas ou cópias redimensionadas podem acabar no mesmo conjunto. **Confira os conjuntos encontrados por hash perceptivo ou impressão digital de mídia antes de apagá-los.** Cada arquivo semelhante mostra o quanto ele corresponde.

## O que é verificado
{: #what-gets-scanned }

Por padrão, o Desduplicador pesquisa todo o armazenamento acessível: armazenamento interno, cartões SD, armazenamento USB, `Android/data` e `Android/media`. `Android/obb` não é incluído. Você pode limitar a pesquisa em [Pesquisar os locais](#search-locations).

O acesso a `Android/data` no Android 13 e mais recentes requer [root](/pt-br/setup#root-access) ou [acesso ADB](/pt-br/setup#shizuku).

Só são considerados arquivos acima do [tamanho mínimo](#minimum-size). Por padrão, apenas tipos de arquivo comuns são incluídos, veja [Ignorar tipos de arquivo incomuns](#skip-uncommon-file-types).

## Qual arquivo é mantido
{: #which-file-is-kept }

Ao apagar um conjunto, o SD Maid mantém um arquivo e apaga o restante. Qual deles é mantido é decidido pela estratégia de exclusão em `Configurações > Desduplicador > Estratégia de exclusão`.

O SD Maid verifica cada regra em ordem. A primeira regra que produz um vencedor claro decide. Arraste as regras para reordená-las e toque em uma regra para mudar o modo dela.

| Regra | Padrão |
|---|---|
| Tipos de arquivos duplicados | Manter correspondências exatas em vez de arquivos semelhantes |
| Locais preferidos | Manter arquivos nas pastas que você escolher. Por padrão, nenhuma pasta é definida. |
| Indexação de mídia | Manter arquivos que sua galeria e outros aplicativos de mídia conhecem |
| Local de armazenamento | Manter arquivos no armazenamento interno. Cartões SD e armazenamento USB contam como externos. |
| Profundidade de pastas | Manter arquivos em pastas pouco aninhadas |
| Data de modificação | Manter arquivos mais antigos |
| Tamanho do arquivo | Manter arquivos maiores |

**Locais preferidos** ajuda a escolher qual arquivo é mantido dentro de um grupo, junto com as outras regras. Essa regra não escolhe entre grupos. Em um conjunto com cópias exatas e arquivos semelhantes, Tipos de arquivos duplicados decide qual grupo é mantido: por padrão, todos os arquivos do grupo de semelhantes são apagados, mesmo em um local preferido. Por exemplo, se a foto da sua câmera e duas cópias idênticas e redimensionadas de um aplicativo de mensagens acabarem em um mesmo conjunto, o padrão mantém uma cópia do aplicativo de mensagens e apaga o original da câmera. Confira esses conjuntos antes de apagá-los e [crie exceções](/pt-br/exclusions) para as pastas cujos arquivos nunca devem ser apagados.

Não há como marcar um único arquivo como aquele que deve ser mantido. Para manter outro arquivo, altere a estratégia de exclusão ou selecione você mesmo os arquivos a apagar.

Alterar a estratégia de exclusão descarta os resultados atuais. Faça uma nova verificação depois.

## Apagando
{: #deleting }

### Apagando um conjunto inteiro
{: #deleting-a-whole-set }

Apagar um conjunto mantém um arquivo e apaga **todos os outros arquivos desse conjunto**. Em um conjunto com arquivos semelhantes, isso inclui arquivos que são apenas semelhantes ao mantido, não idênticos.

Mude a lista de resultados para o layout de lista para ver um ícone de apagar em cada arquivo que será removido ao apagar o conjunto inteiro. O layout de grade padrão não mostra esses ícones.

### Apagando arquivos individuais
{: #deleting-individual-files }

Selecione arquivos tocando e segurando e apague-os. Pelo menos um arquivo por conjunto precisa permanecer, a menos que você ative [Permitir "Excluir todos" possíveis](#make-delete-all-possible).

Nos detalhes do conjunto, a visualização `Pastas` agrupa os arquivos por pasta. Apagar uma pasta ali apaga as duplicatas nessa pasta. Se a pasta contiver todos os arquivos de um conjunto, o arquivo que seria mantido ao apagar o conjunto inteiro fica de fora. Com [Permitir "Excluir todos" possíveis](#make-delete-all-possible) ativado, a confirmação permite apagar todos eles.

### Apagando pelo painel
{: #deleting-from-the-dashboard }

Apagar pelo cartão do Desduplicador no painel apaga todos os arquivos de cada conjunto, menos um, após uma confirmação. Se o botão principal do painel também executa o Desduplicador é definido em [Um toque](#one-tap).

## Exceções
{: #exclusions }

Caminhos com exceção para o Desduplicador, ou com exceção geral, não são verificados. Ignorar um conjunto nos resultados cria exceções para todos os arquivos dele, incluindo aquele que teria sido mantido. Veja [Exceções](/pt-br/exclusions).

O Desduplicador não pode ser executado pelo [Agendador (Scheduler)](/pt-br/scheduler).

## Um toque
{: #one-tap }

`Configurações > Gerais > Ferramentas Um-Toque` define se o botão principal do painel, o atalho `Escanear + Excluir` e o widget incluem o Desduplicador. Vem desativado por padrão. O atalho e o widget verificam e apagam sem confirmação, usando sua estratégia de exclusão. No painel, o `Modo apenas um toque` faz o mesmo, mas se já houver resultados aguardando, Apagar ainda pergunta antes. Só inclua o Desduplicador se a [Soma de verificação de conteúdo](#content-checksum) for o único método de detecção.

## Configurações
{: #settings }

### Pesquisar os locais
{: #search-locations }

As pastas a pesquisar. O padrão são todas as áreas acessíveis. Toque e segure para redefinir.

### Estratégia de exclusão
{: #deletion-strategy }

Qual arquivo de um conjunto é mantido, veja [Qual arquivo é mantido](#which-file-is-kept).

### Permitir "Excluir todos" possíveis
{: #make-delete-all-possible }

Mostra opções para apagar todos os arquivos de um conjunto, de modo que nenhuma cópia sobre. Desativado por padrão.

### Tamanho mínimo
{: #minimum-size }

Ignora arquivos abaixo desse tamanho. Padrão de 512 KB.

### Ignorar tipos de arquivo incomuns
{: #skip-uncommon-file-types }

Inclui apenas imagens, vídeos, arquivos de áudio, documentos, arquivos compactados e arquivos APK comuns. Ativado por padrão. O tipo é determinado pela extensão do arquivo.

### Método de detecção
{: #detection-method }

Ative [Soma de verificação de conteúdo](#content-checksum), [Hash perceptivo](#perceptual-hash) e [Impressão digital de mídia](#media-fingerprint).
