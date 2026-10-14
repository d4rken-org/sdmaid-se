---
title: "Limpador do sistema"
lang: pt-BR
translation_key: systemcleaner
permalink: /pt-br/systemcleaner
source_hash: 1ced6df9b00f1e05
---
# Limpador do sistema (SystemCleaner)
{: #system-cleaner }

A ferramenta Limpador do sistema (SystemCleaner) é basicamente um mecanismo de busca de uso geral. Ela percorre todo o armazenamento acessível e compara os arquivos e pastas encontrados com um conjunto de filtros.

Ao contrário de outras ferramentas, como o Localizador de Cadáver (CorpseFinder) ou o Limpador de Aplicativos (AppCleaner), ela não sabe nada sobre os aplicativos instalados. Ela trabalha puramente com caminhos e atributos de arquivos. Por isso, apenas exceções de caminho e por segmentos podem ser aplicadas ao Limpador do sistema.

Todo arquivo encontrado passa pelos filtros ativados e é atribuído ao primeiro filtro que corresponder.

## Configurações
{: #settings }

### Filtros gerais
{: #general-filters }

Os filtros gerais procuram **em todas as áreas de armazenamento acessíveis** por certos tipos de arquivo que podem aparecer em qualquer lugar do dispositivo.

| Filtro | Descrição |
|--------|-------------|
| **Pastas vazias** | Pastas vazias de todo o dispositivo. Pastas aninhadas podem exigir várias passagens de verificação. |
| **Capturas de Tela** | Capturas de tela mais antigas que uma idade configurável, de vários aplicativos e locais. |
| **Arquivos descartados** | Arquivos em lixeiras de vários aplicativos e locais que estão marcados para serem apagados, mas ainda não foram removidos permanentemente. |
| **APKs supérfluos** | Arquivos de instalação (APKs) de versões mais antigas ou iguais às versões de aplicativos que já estão instaladas. |
| **Pastas LOST.DIR** | Arquivos temporários criados ao transferir dados entre dispositivos Android. |
| **Arquivos Linux** | Arquivos e pastas criados quando o armazenamento foi conectado a um computador Linux. |
| **Arquivos Mac** | Arquivos e pastas criados ao navegar pelo armazenamento com o aplicativo Finder da Apple (por exemplo, `.DS_Store`). |
| **Arquivos Windows** | Dados que o explorador de arquivos do Windows pode criar no armazenamento conectado (por exemplo, `Thumbs.db`). |
| **Imagens em miniatura** | Pré-visualizações em cache de imagens e vídeos (por exemplo, pastas `.thumbnails`). |
| **Arquivos temporários do sistema** | Arquivos temporários relacionados ao sistema, geralmente caches de curta duração para operações em andamento. |
| **Arquivos de análise** | Dados relacionados a análises e rastreamento de bugs. |
| **Arquivos de anúncios** | Arquivos que foram criados para ou por anúncios. |
| **Arquivos de log do sistema** | Arquivos de log do sistema e dos serviços do sistema (por exemplo, `*.log`) em vários locais. |

### Filtros específicos
{: #specific-filters }

Os filtros específicos visam **caminhos fixos e conhecidos** no dispositivo onde certos tipos de dados são armazenados.

| Filtro | Descrição |
|--------|-------------|
| **Resíduo do sistema** | Se processos nativos travarem (por exemplo, jogos), arquivos "tombstone" são criados aqui. |
| **Estatísticas de uso** | Arquivos relacionados às estatísticas de uso dos aplicativos. |
| **Erros de ANR** | Rastros de eventos "Application Not Responding" (aplicativo não está respondendo). |
| **Locais de arquivos temporários** | Arquivos temporários relacionados ao sistema (por exemplo, do Android Studio). |
| **Arquivos de log do sistema (datalogger)** | Arquivos de log específicos dos serviços do sistema. |
| **Cache de download** | Pasta de cache do sistema usada para várias coisas (por exemplo, atualizações OTA). |
| **Log dropbox** | Uma área onde o sistema deposita arquivos de log. |
| **Cache do instalador** | Dados em cache relacionados aos aplicativos instalados, usados pelo gerenciamento de pacotes do sistema. |
| **Tarefas recentes** | Dados relacionados ao histórico de tarefas no alternador de tarefas do sistema. |

### Filtros personalizados
{: #custom-filters }

Além dos filtros gerais fornecidos pelo aplicativo, você pode criar filtros personalizados com seus próprios critérios. Os filtros personalizados podem ser criados em **Configurações > Limpador do sistema > Gerenciar filtros personalizados**.

> **Aviso**: filtros personalizados podem ser arriscados. Sempre confira os resultados da verificação antes de apagar!

#### Critérios disponíveis
{: #available-criteria }

| Critério | Descrição |
|-----------|-------------|
| **Rótulo do filtro** | Um nome para o seu filtro |
| **Áreas de dados** | Locais de armazenamento a verificar (deixe vazio para todas as áreas acessíveis) |
| **Restrições de tipo de arquivo** | Arquivos, pastas ou links simbólicos alvo (deixe vazio para todos) |
| **Critério de diretório** | Regras para corresponder caminhos de arquivos |
| **Exclusões** | Caminhos a deixar de fora da correspondência |
| **Critério do nome** | Regras para corresponder nomes de arquivos |
| **Tamanho mínimo** | Só corresponde a arquivos maiores que isso |
| **Tamanho máximo** | Só corresponde a arquivos menores que isso |
| **Idade mínima** | Só corresponde a arquivos mais antigos que isso |
| **Idade máxima** | Só corresponde a arquivos mais recentes que isso |
| **Regex de caminho** | Avançado: expressões regulares para corresponder caminhos |

#### Modos do critério de diretório
{: #path-criteria-modes }

| Modo | Descrição | Exemplo |
|------|-------------|---------|
| **Começa com** | O caminho começa com a palavra-chave | `Downloads/temp` corresponde a `/sdcard/Downloads/temp/file.txt` |
| **Contém** | O caminho inclui a palavra-chave | `cache` corresponde a `/sdcard/app/cache/data.bin` |
| **Termina com** | O caminho termina com a palavra-chave | `backup/old` corresponde a `/sdcard/backup/old` |
| **Igual a** | O caminho corresponde exatamente à palavra-chave | `Download/temp` corresponde apenas a esse caminho exato |

#### Modos do critério do nome
{: #name-criteria-modes }

| Modo | Descrição | Exemplo |
|------|-------------|---------|
| **Começa com** | O nome do arquivo começa com a palavra-chave | `temp_` corresponde a `temp_file.txt` |
| **Contém** | O nome do arquivo inclui a palavra-chave | `.bak` corresponde a `document.bak.old` |
| **Termina com** | O nome do arquivo termina com a palavra-chave | `.tmp` corresponde a `cache.tmp` |
| **Igual a** | O nome do arquivo corresponde exatamente | `thumbs.db` corresponde apenas a `thumbs.db` |

Por padrão, todas as correspondências **não diferenciam maiúsculas de minúsculas**.

#### Áreas de dados
{: #data-areas }

Áreas comuns que você pode escolher:

| Área | Descrição |
|------|-------------|
| **SDCARD** | Armazenamento principal (interno ou cartão SD) |
| **PUBLIC_MEDIA** | Pastas de mídia (DCIM, Pictures, Music etc.) |
| **PUBLIC_DATA** | Dados compartilhados de aplicativos |
| **PRIVATE_DATA** | Armazenamento privado dos aplicativos (requer root) |
| **DOWNLOAD_CACHE** | Cache de download do sistema (requer root) |
| **PORTABLE** | Armazenamento removível (USB/microSD) |

#### Dicas
{: #tips }

- **Desempenho**: defina apenas os critérios necessários. Muitos padrões regex deixam a verificação mais lenta.
- **Vários critérios**: todos os critérios especificados precisam corresponder (lógica E).
- **Tocar e segurar**: toque e segure nos itens para ver opções adicionais.
- **Testes**: use a pré-visualização da verificação antes de ativar a remoção.
- **Importar/Exportar**: os filtros podem ser exportados e compartilhados com outros usuários.

#### Opções obsoletas
{: #deprecated-options }

A opção `rootOnly` do SD Maid 1 **não está mais disponível**. Em vez dela, use **Áreas de dados** para controlar quais locais de armazenamento são verificados.
