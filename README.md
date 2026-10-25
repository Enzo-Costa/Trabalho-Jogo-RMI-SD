# Suíte de Jogos Distribuídos (RMI + Sockets TCP/UDP + Multicast)

Trabalho prático da disciplina de **Sistemas Distribuídos** — Prof. Alexandre Sztajnberg (UERJ • IME).  
**Alunos**: Enzo Costa e Marcos Alves.  
**Jogos Implementados**: Jogo da Velha, Pedra, Papel e Tesoura (PPT) e Par ou Ímpar.  
**Tecnologias**: Java RMI, Sockets TCP/UDP, Multicast UDP e SHA-256.

---

## Arquitetura do Sistema

O sistema é composto por **3 programas Java independentes**, sem bibliotecas externas, seguindo a comunicação distribuída em 3 camadas:

1. **Pareamento RMI (`ServidorMatch`)**: Serviço remoto na porta `98NN` que organiza filas concorrentes por protocolo (`tcp`/`udp`) e por jogo (`velha`/`ppt`/`par_impar`).
2. **Conexão Direta P2P (`Jogador`)**: Os jogadores trocam jogadas diretamente via Sockets TCP ou UDP. Nos jogos simultâneos, utilizam o protocolo criptográfico de compromisso (**Commit-Reveal com SHA-256**).
3. **Exibição em Tempo Real (`Display`)**: Quem conclui a jogada/rodada transmite o estado para o grupo Multicast `225.1.1.NN:88NN`, onde os displays desenham a partida em tempo real.

```
+-----------------------------------------------------------------------------------+
|               1. SERVIDOR DE MATCH (RMI) — [Passo 1: Bind no Registry]            |
|                      VM 152.92.236.14 • RMIRegistry Porta 98NN                    |
|        [Passo 3: lock.wait() retém Jogador 1 até Jogador 2 entrar no Match]       |
+-----------------------------------------------------------------------------------+
   |         ^                                                         ^         |   
   |         | (Passo 2) getOponent(...)     (Passo 4) getOponent(...) |         |   
   |         | (aguarda oponente)            (completa o par)          |         |   
   v         |                                                         |         v   
 (Passo 6) Retorno RMI:                                 (Passo 6) Retorno RMI:       
 Oponente + inicia: true                                Oponente + inicia: false     
+--------------------------+    (Passo 7) Handshake    +--------------------------+
|       2. JOGADOR 1       | <=========================> |       2. JOGADOR 2       |
|    VM: 152.92.236.16     | (Passo 8) Troca de Jogadas |    VM: 152.92.236.17     |
|   (inicia: true - 'X')   |   Sockets P2P (TCP / UDP)   |   (inicia: false - 'O')  |
+--------------------------+                             +--------------------------+
             |                                                         |             
             | (Passo 5) Multicast UDP                  Multicast UDP  |             
             v                                                         v             
+-----------------------------------------------------------------------------------+
|                           3. GRUPO MULTICAST DE DISPLAYS                          |
|         [Passo 5: joinGroup() — Display 1 (Canal 1), Display 2, Display N]        |
|                               IP: 225.1.1.NN:88NN                                 |
+-----------------------------------------------------------------------------------+
```

---

## Estrutura de Arquivos

```
Trabalho-Jogo-RMI-SD/
├── ServidorMatch.java            # Módulo RMI de Pareamento (Multi-Jogos e Multi-Protocolos)
├── Jogador.java                  # Módulo Jogador (Interface IJogo, Sockets P2P, SHA-256 e Multicast)
├── Display.java                  # Módulo Espectador com renderização adaptativa no Multicast
├── index.html                    # Documentação Web completa com modo claro/escuro e link do Pitch
├── README.md                     # Este guia principal de execução e arquitetura
├── GUIA_DESENVOLVEDOR.md         # Guia de manutenção e navegação de código para a dupla
├── IMPLEMENTACAO_NOVOS_JOGOS.md  # Registro técnico detalhado dos jogos PPT e Par ou Ímpar
├── compile.bat                   # Script de compilação rápida no Windows
└── compile.sh                    # Script de compilação rápida no Linux (VMs da UERJ)
```

---

## Como Compilar

Compilação direta usando o compilador padrão do Java (Java SE 8+), sem Maven ou Gradle:

### No Windows:
```bash
javac *.java
# ou execute:
compile.bat
```

### No Linux (Máquinas Virtuais da UERJ):
```bash
chmod +x compile.sh
./compile.sh
# ou direto:
javac *.java
```

---

## Como Executar Localmente (Teste Rápido com 4 Terminais)

Abra 4 terminais na pasta do projeto e utilize `127.0.0.1` (substitua `14` pelo seu número de conta):

* **Terminal 1 — Servidor de Pareamento (RMI)**:
  ```bash
  java ServidorMatch 14
  ```

* **Terminal 2 — Display Espectador (Multicast)**:
  ```bash
  java Display 14
  ```

* **Terminal 3 — Jogador 1 (Porta 5001)**:
  ```bash
  # Jogo da Velha (Padrão):
  java Jogador 14 5001 127.0.0.1 tcp 127.0.0.1 velha

  # Ou Pedra, Papel e Tesoura:
  java Jogador 14 5001 127.0.0.1 tcp 127.0.0.1 ppt

  # Ou Par ou Ímpar (via UDP):
  java Jogador 14 5001 127.0.0.1 udp 127.0.0.1 par_impar
  ```

* **Terminal 4 — Jogador 2 (Porta 5002)**:
  ```bash
  # Jogo da Velha (Padrão):
  java Jogador 14 5002 127.0.0.1 tcp 127.0.0.1 velha

  # Ou Pedra, Papel e Tesoura:
  java Jogador 14 5002 127.0.0.1 tcp 127.0.0.1 ppt

  # Ou Par ou Ímpar (via UDP):
  java Jogador 14 5002 127.0.0.1 udp 127.0.0.1 par_impar
  ```

---

## Como Executar nas VMs

As VMs do laboratório de Sistemas Distribuídos são: `152.92.236.14` (Servidor), `152.92.236.16` (Jogador 1) e `152.92.236.17` (Jogador 2).

Substitua `NN` pelo número da sua conta no laboratório (ex: conta 14 $\rightarrow$ `NN = 14`):

1. **Na VM 152.92.236.14 (Servidor)**:
   ```bash
   javac *.java
   java ServidorMatch NN
   ```

2. **Em qualquer VM (ou máquina local com acesso à rede UERJ)**:
   ```bash
   java Display NN
   ```

3. **Na VM 152.92.236.16 (Jogador 1)**:
   ```bash
   # Jogo da Velha (Padrão TCP):
   java Jogador NN 5001 152.92.236.14 tcp 152.92.236.16 velha

   # Para jogar PPT:
   java Jogador NN 5001 152.92.236.14 tcp 152.92.236.16 ppt

   # Para jogar Par ou Ímpar via UDP:
   java Jogador NN 5001 152.92.236.14 udp 152.92.236.16 par_impar
   ```

4. **Na VM 152.92.236.17 (Jogador 2)**:
   ```bash
   # Jogo da Velha (Padrão TCP):
   java Jogador NN 5002 152.92.236.14 tcp 152.92.236.17 velha

   # Para jogar PPT:
   java Jogador NN 5002 152.92.236.14 tcp 152.92.236.17 ppt

   # Para jogar Par ou Ímpar via UDP:
   java Jogador NN 5002 152.92.236.14 udp 152.92.236.17 par_impar
   ```

> **Nota de Compatibilidade**: Se o último argumento for omitido, o sistema assume `velha` automaticamente.

---

## Formato das Mensagens (JSON)

Toda a serialização e leitura de mensagens JSON é feita em Java puro sem dependências externas:

### 1. Pareamento no RMI
* **Entrada (`jsonIn`)**: `{"ip":"152.92.236.16","porta":5001,"protocolo":"tcp","jogo":"velha"}`
* **Retorno (`jsonRet`)**: `{"ip":"152.92.236.17","porta":5002,"jogo":"velha","inicia":true}`

### 2. Jogo da Velha (Multicast e P2P)
```json
{
  "Numero": 0,
  "Jogada": 4,
  "Tabuleiro": [0, 0, 0, 0, 1, 0, 0, 0, 0],
  "Resultado": "jog"
}
```

### 3. Pedra, Papel e Tesoura (Multicast)
```json
{
  "jogo": "ppt",
  "Numero": 1,
  "EscolhaJ1": "pedra",
  "EscolhaJ2": "tesoura",
  "VencedorRodada": 1,
  "Placar": [1, 0],
  "Resultado": "jog"
}
```

### 4. Par ou Ímpar (Multicast)
```json
{
  "jogo": "par_impar",
  "Numero": 1,
  "OpcaoJ1": "par",
  "DedosJ1": 4,
  "DedosJ2": 3,
  "Soma": 7,
  "VencedorRodada": 2,
  "Placar": [0, 1],
  "Resultado": "jog"
}
```

---

## Tratamento de Falhas

1. **Tolerância a Falhas e Timeouts (60s)**:
   - **No RMI**: se um jogador entrar e nenhum adversário aparecer em 60 segundos, o `lock.wait(60000)` libera a chamada sem travar o servidor.
   - **No Socket P2P**: sockets configurados com `setSoTimeout(60000)`, evitando processos zumbis se o oponente fechar o terminal ou cair a rede.
2. **Validação Anti-Cheat**:
   - **Sequência de Turnos**: no Jogo da Velha, cada lance valida o contador `"Numero"` e se a posição já estava marcada, reenviando o estado anterior se houver violação.
   - **Criptografia em Jogos Simultâneos (Commit-Reveal SHA-256)**: em PPT e Par ou Ímpar, as jogadas devem ser secretas. O Jogador 1 envia o hash criptográfico `SHA256(jogada + ":" + salt)`. O Jogador 2 responde abertamente e o Jogador 1 revela a chave e o salt. Qualquer divergência aborta a partida por tentativa de trapaça.
3. **Publicação Multicast sem Duplicações**:
   - Apenas o jogador ativo transmite o estado atualizado para o grupo `225.1.1.NN:88NN`. Quem apenas recebe atualiza seu estado local sem retransmitir, mantendo a tela limpa.
4. **Suporte a Dois Protocolos (Bônus TCP e UDP)**:
   - Fila concorrente segregada no servidor RMI (`tcp` e `udp`) com camada de transporte desacoplada via `ITransporte`.
5. **Suíte Multi-Jogos Modular**:
   - Interface `IJogo` implementada por `JogoVelha`, `JogoPPT` e `JogoParOuImpar`, com detecção dinâmica e renderização contextual no `Display`.
6. **Documentação Web e Vídeo Pitch (`index.html`)**:
   - Página pronta para hospedagem no servidor Apache da VM `152.92.236.14` (`~/public_html/`), com suporte a temas claro/escuro e link para a apresentação de 10 minutos.
