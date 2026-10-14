---
title: "Localizador de Cadáver"
lang: pt-BR
translation_key: corpsefinder
permalink: /pt-br/corpsefinder
source_hash: 9e16286f0fda1dc8
---
# Localizador de Cadáver (CorpseFinder)
{: #corpse-finder }

A ferramenta `Localizador de Cadáver` (CorpseFinder) encontra arquivos que pertencem a aplicativos que não estão mais instalados.


## Nível de risco
{: #risklevel }

Por padrão, todas as opções de nível de risco estão desativadas. Só as ative se você souber o que está fazendo e, antes de apagar qualquer coisa, confira os resultados primeiro.
Ativar uma opção de nível de risco aumenta a chance de serem apagados arquivos que você não queria apagar.

Os níveis de risco se aplicam principalmente a locais de lista de permissões, como a raiz do seu armazenamento público (`/storage/emulated/0`). Esses locais não têm um padrão de nomes comum, e o SD Maid só consegue determinar a quem pertencem por meio de um banco de dados mantido manualmente.


### Incluir remanescentes desejáveis
{: #include-desirable-remnants }

Remanescentes desejáveis são arquivos com donos conhecidos que não estão instalados, mas que são marcados internamente pelo SD Maid como `KEEPER`. Normalmente são arquivos criados pelo usuário, por exemplo fotos que você tirou.

### Incluir nomes comuns
{: #include-common-names }

Alguns aplicativos simplesmente usam nomes muito comuns. Provavelmente existem centenas de aplicativos de notas que criam uma pasta `/sdcard/Notes` para salvar dados. O SD Maid pode conhecer um desses aplicativos e ele pode não estar instalado no seu celular, mas o SD Maid não tem como ter certeza de que não há outro aplicativo no seu celular usando essa pasta, um aplicativo que o SD Maid não sabe que também usa essa pasta. 

## Filtro
{: #filter }

Cada filtro é responsável por uma área de dados específica. Ao ativar um filtro, o Localizador de Cadáver vai procurar também nessa área.

### Cartão SD
{: #sdcard }

* Normalmente é onde o usuário coloca os arquivos
* Armazenamento público (interno e removível), exceto subdiretórios específicos em `Android/data`.
* Os arquivos aqui não seguem nenhum esquema de nomes.

Exemplos:

```bash
/storage/emulated/0/SDMaid
/storage/DEAD-BEEF/SDMaid
/sdcard/SDMaid
```

### Mídia pública
{: #public-media }

* Armazena mídia pública dos aplicativos, por exemplo mídia recebida pelo WhatsApp. As pastas têm o nome do pacote dos aplicativos.
* Subdiretório do armazenamento público com significado especial para o sistema operacional. 

Exemplos:

```bash
/storage/emulated/0/Android/media/eu.darken.sdmse
/storage/DEAD-BEEF/Android/media/eu.darken.sdmse
/sdcard/Android/media/eu.darken.sdmse
```

### Dados públicos de apps
{: #public-app-data }

* Arquivos extras dos aplicativos.
* As pastas têm o nome do pacote dos aplicativos.
* Subdiretório do armazenamento público com significado especial para o sistema operacional.

Exemplos:

```bash
/storage/emulated/0/Android/data/eu.darken.sdmse
/storage/DEAD-BEEF/Android/data/eu.darken.sdmse
/sdcard/Android/data/eu.darken.sdmse
```

### Recursos do OBB
{: #obb-resources }

* Normalmente são arquivos de recursos extras para jogos.
* As pastas e os arquivos têm o nome do pacote dos aplicativos.
* Subdiretório do armazenamento público com significado especial para o sistema operacional.

Exemplos:

```bash
/storage/emulated/0/Android/obb/eu.darken.sdmse
/storage/DEAD-BEEF/Android/obb/eu.darken.sdmse
/sdcard/Android/obb/eu.darken.sdmse
```

### Dados privados de aplicativo
{: #private-app-data }

* As pastas e os arquivos têm o nome do pacote dos aplicativos.
* Subdiretório do armazenamento público com significado especial para o sistema operacional.

Exemplos:

```bash
/data/data/eu.darken.sdmse
```

### Cache Dalvik
{: #dalvik-cache }

* Armazena versões otimizadas do código dos aplicativos para uma execução mais rápida.
* Fica na partição do sistema, muitas vezes em diretórios específicos de cada arquitetura.
* Os arquivos são nomeados com o nome do pacote do aplicativo.

Exemplos:

```bash
/data/dalvik-cache/arm64/data@app@eu.darken.sdmse@base.apk@classes.dex
/data/dalvik-cache/arm/data@app@eu.darken.sdmse@base.apk@classes.dex
```

### Bibliotecas de aplicativo
{: #app-libraries }

* Contém bibliotecas nativas usadas pelos aplicativos, como arquivos `.so`.
* Normalmente fica no diretório de dados do aplicativo, em uma pasta `lib`.
* Os arquivos são nomeados de acordo com a arquitetura e os requisitos da biblioteca.

Exemplos:

```bash
/data/app/eu.darken.sdmse-1/lib/arm64/libexample.so
/data/app/eu.darken.sdmse-2/lib/arm/libexample.so
```

### Código-fontes de apps
{: #app-sources }

* Refere-se aos arquivos APK que servem como código-fonte dos aplicativos.
* Armazenados em diretórios específicos para aplicativos do sistema, do usuário ou do fabricante.
* Os arquivos têm o nome do pacote e a versão do aplicativo.

Exemplos:

```bash
/data/app/eu.darken.sdmse-1/base.apk
/system/app/ExampleApp/ExampleApp.apk
```

### Fontes de aplicativos privados
{: #private-app-sources }

* Semelhantes às fontes de aplicativos, mas armazenadas em diretórios protegidos para maior segurança.
* Acessíveis apenas pelo sistema ou pelo respectivo aplicativo.
* Os arquivos têm o nome do pacote do aplicativo.

Exemplos:

```bash
/data/app-private/eu.darken.sdmse/base.apk
```
