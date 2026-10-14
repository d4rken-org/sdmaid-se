---
title: "Deslizador"
lang: pt-BR
translation_key: swiper
permalink: /pt-br/swiper
source_hash: 4c7d8b7a30d3a36e
---
# Deslizador (Swiper)
{: #swiper }

O Deslizador (Swiper) ajuda você a organizar pastas específicas mais rápido do que um gerenciador de arquivos. Você passa pelos arquivos um de cada vez e desliza cada um para mantê-lo, apagá-lo ou pulá-lo. Nada é apagado até você revisar suas escolhas e confirmar.

## Iniciando uma sessão
{: #starting-a-session }

1. Abra o Deslizador pelo painel e toque em `Selecionar pastas`. Você pode escolher pastas, arquivos individuais ou ambos.
2. Opcionalmente, defina um filtro de tipo de arquivo e uma ordem de classificação no cartão da sessão.
3. Toque em `Escanear`. As pastas são pesquisadas incluindo todas as subpastas.
4. Toque em `Começar a deslizar`.

Você também pode criar uma sessão pelo [Analisador de Armazenamento (StorageAnalyzer)](/pt-br/storageanalyzer): selecione itens lá e escolha `Criar sessão do Swiper`.

Bons lugares para começar são o rolo da câmera, a pasta Downloads e as capturas de tela.

### Tipos de arquivo
{: #file-types }

`Mostrar apenas estes tipos de arquivo` limita a sessão a imagens, vídeos, áudio, documentos ou arquivos compactados. Você também pode inserir suas próprias extensões, por exemplo `apk, apks`. Se nada estiver marcado, todos os arquivos são mostrados. Os arquivos são identificados apenas pela extensão.

### Ordem de classificação
{: #sort-order }

Por padrão, os arquivos são mostrados dos mais antigos para os mais recentes, com base na data da última modificação. Você também pode ordenar por mais recentes primeiro, por nome ou por maiores primeiro.

O filtro e a ordem de classificação não podem ser alterados depois que uma verificação encontrou arquivos. Para alterá-los, inicie uma nova sessão.

### Escaneando um armazenamento inteiro
{: #scanning-a-whole-storage }

Se você escolher o nível mais alto de um armazenamento, por exemplo todo o armazenamento interno, todos os arquivos dele entram na sessão, incluindo fotos e mídia de aplicativos. O SD Maid pede que você confirme antes de escanear. Quando, mais tarde, você apagar arquivos de uma sessão assim, primeiro precisa marcar `Entendo que esses arquivos serão permanentemente excluídos.`

## Deslizando
{: #swiping }

| Gesto | Ação padrão |
|---|---|
| Deslizar para a direita | Manter |
| Deslizar para a esquerda | Apagar |
| Deslizar para cima | Pular |
| Deslizar para baixo | Desfazer a última decisão |

Os botões na parte inferior fazem o mesmo. Com [Inverter direções de deslize](#swap-swipe-directions) ativado, esquerda e direita ficam invertidas.

* **Pular** deixa um arquivo sem decisão. Os arquivos pulados voltam depois dos demais.
* **Desfazer** volta pelas suas últimas decisões, até 50.
* **Toque e segure Pular** para deixar um arquivo de fora das próximas verificações do Deslizador. Isso cria uma [exceção](/pt-br/exclusions) de caminho e remove o arquivo da sessão.
* Os botões no cartão abrem o arquivo em outro aplicativo ou mostram uma pré-visualização em tela cheia.
* Toque em uma miniatura na faixa para ir até aquele arquivo. Deslizá-lo de novo substitui sua decisão anterior.

Você pode sair a qualquer momento. Suas decisões são salvas com a sessão e você pode continuar depois. Quando o SD Maid é reiniciado, os arquivos que ele não encontra mais são removidos da sessão. Até lá, a sessão ainda pode listar arquivos que foram apagados ou movidos por outra coisa.

## Revisando e apagando
{: #reviewing-and-deleting }

Toque em `Avaliar` para ver todas as decisões. Quando não sobram arquivos sem decisão, a revisão abre sozinha.

* Altere as decisões arquivo por arquivo, ou toque e segure para selecionar vários arquivos de uma vez.
* Toque em um arquivo para voltar até ele.
* `Apagar` mostra quantos arquivos e quanto espaço são afetados, e em quais pastas, antes que qualquer coisa aconteça.

Apagar é permanente. Os arquivos não são movidos para uma lixeira e o SD Maid não pode restaurá-los.

Depois de apagar, os arquivos mantidos e os apagados saem da sessão. Os arquivos sem decisão ficam para depois. Quando não sobra nada, a sessão é removida. Se alguns arquivos não puderam ser apagados, eles ficam na sessão e você pode tentar de novo.

Remover uma sessão só descarta suas decisões. Nenhum arquivo é alterado.

### Bom saber
{: #things-to-know }

* **Manter só vale para a sessão atual.** Se você iniciar uma nova sessão na mesma pasta, os arquivos mantidos aparecem de novo. Para ocultar um arquivo permanentemente, crie uma exceção para ele (toque e segure Pular).
* **Uma sessão não inclui arquivos novos.** Arquivos adicionados à pasta depois da verificação não aparecem. Inicie uma nova sessão para incluí-los.
* **Arquivos compactados incluem arquivos APK.**
* **Arquivos ocultos são incluídos.**

## Versão gratuita
{: #free-version }

Sem o upgrade, o Deslizador fica limitado a 2 sessões com 50 arquivos cada. Com uma ordem de classificação definida, você recebe os primeiros 50 arquivos nessa ordem.

## Configurações
{: #settings }

### Inverter direções de deslize
{: #swap-swipe-directions }

Deslize para a esquerda para manter e para a direita para apagar. Desativado por padrão.

### Mostrar detalhes do arquivo
{: #show-file-details }

Mostra o nome, o tamanho, a data e a pasta do arquivo na tela de deslizar. Ativado por padrão.

### Vibração tátil
{: #haptic-feedback }

Vibra quando você mantém ou apaga um arquivo. Ativado por padrão.
