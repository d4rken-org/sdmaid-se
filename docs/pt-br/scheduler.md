---
title: "Agendador"
lang: pt-BR
translation_key: scheduler
permalink: /pt-br/scheduler
source_hash: 74e1452091eaf142
---
# Agendador (Scheduler)
{: #scheduler }

O recurso **Agendador** (Scheduler) do SD Maid 2 permite automatizar tarefas de manutenção em intervalos específicos, garantindo que seu dispositivo continue otimizado sem esforço manual. Ele suporta várias ferramentas, como o **Limpador de Aplicativos** (AppCleaner), o **Localizador de Cadáver** (CorpseFinder) e o **Limpador do sistema** (SystemCleaner), que podem ser configuradas para rodar em intervalos regulares ou em horários específicos.

<img src="/assets/images/scheduler/overview-1.webp" width="150">
<img src="/assets/images/scheduler/overview-2.webp" width="150">
<img src="/assets/images/scheduler/overview-3.webp" width="150">
<img src="/assets/images/scheduler/overview-4.webp" width="150">
<img src="/assets/images/scheduler/overview-5.webp" width="150">
<img src="/assets/images/scheduler/overview-6.webp" width="150">


## Como configurar um agendamento
{: #how-to-set-up-a-schedule }

1. Vá até **Configurações** e selecione **Agendador**.
2. Toque no botão **+** para criar um novo agendamento ou gerenciar os existentes.
3. Escolha quais ferramentas (por exemplo, **Limpador de Aplicativos**, **Localizador de Cadáver**, **Limpador do sistema**) devem ser executadas de acordo com o agendamento.
4. Defina o **intervalo de repetição** (por exemplo, a cada 3 dias) e a **hora aproximada** da execução.
5. Ative o agendamento acionando a chave.

## Editar ou apagar agendamentos
{: #editing-or-deleting-schedules }

1. Vá até as configurações do **Agendador** para gerenciar seus agendamentos.
2. Você pode modificar os agendamentos existentes, ajustar os intervalos ou apagá-los se não forem mais necessários.

Quando um agendamento está ativo, o SD Maid vai executar as tarefas escolhidas nos horários especificados.

## Opções adicionais
{: #additional-options }

Configuradas pelo menu principal de configurações: em `Configurações > Agendador` há algumas opções extras.

- **Suspender quando a bateria estiver baixa**: se o dispositivo estiver no modo de economia de energia, as tarefas agendadas serão puladas.
- **Pular quando não estiver carregando**: as tarefas agendadas só serão executadas quando o dispositivo estiver carregando, evitando o consumo da bateria.
- **Comandos pós-agendado**: você pode definir comandos de shell para serem executados depois que as tarefas agendadas terminarem.
- **Serviço de acessibilidade**: por padrão, o agendador pula os recursos baseados no serviço de acessibilidade, pois eles não funcionam quando o dispositivo está bloqueado. Se você ativar isso sem fazer nenhuma outra alteração no seu dispositivo, o agendador vai falhar. Então, a menos que você saiba o que está fazendo, mantenha isso desativado. Isso só é útil em casos extremos, como um dispositivo Android TV sempre ligado.

## Solução de problemas
{: #troubleshooting }

### O agendador não está executando 
{: #scheduler-is-not-executing }

Diferencie entre o agendador não ser executado de forma alguma e as operações executadas não produzirem os resultados desejados. Você pode conferir o histórico de execuções na [seção Histórico](/pt-br/stats) do SD Maid. Se o agendador foi executado mas falhou, deve haver um erro.

Se o agendador não foi executado de forma alguma, é possível que o sistema tenha impedido o SD Maid de ser iniciado para economizar bateria. Depois de ativar seu primeiro agendamento, você deve ter visto um aviso com o título "Aviso" e a ação "Corrigir", pedindo para desativar as otimizações de bateria. Se você dispensou esse aviso sem querer, vai precisar ir manualmente às configurações do sistema para o SD Maid e verificar se o SD Maid está sendo "otimizado" pelo sistema.

### Serviço de acessibilidade
{: #accessibility-service }
Remoções baseadas em acessibilidade, por exemplo o Limpador de Aplicativos apagando "itens inacessíveis", não funcionam pelo agendador. Quando o agendador é executado, o dispositivo normalmente está bloqueado, e assim como você não consegue tocar em nada com a tela desligada ou bloqueada, o SD Maid também não consegue.

Por padrão, o agendador não realiza operações baseadas em acessibilidade. No entanto, você pode ativar a opção "Serviço de acessibilidade" nas configurações do agendador se souber o que está fazendo.

Alternativas: faça root no dispositivo (aplicativos do usuário + do sistema) ou instale o Shizuku (somente aplicativos do usuário). Qualquer uma dessas modificações permite que o SD Maid realize operações semelhantes sem usar o recurso do serviço de acessibilidade.
