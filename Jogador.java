import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.io.PrintWriter;
import java.net.*;
import java.nio.charset.StandardCharsets;
import java.rmi.registry.LocateRegistry;
import java.rmi.registry.Registry;
import java.security.MessageDigest;
import java.util.Scanner;

/**
 * Jogador: Módulo cliente com suporte a múltiplos jogos (Jogo da Velha,
 * Pedra-Papel-Tesoura e Par-ou-Ímpar), suporte dual TCP e UDP (bônus),
 * protocolo anti-cheat com Commit-Reveal (SHA-256) e publicação Multicast.
 */
public class Jogador {

    // Abstração de transporte para suportar TCP e UDP transparentemente
    public interface ITransporte {
        void enviar(String msg) throws Exception;
        String receber() throws Exception;
        void fechar();
    }

    // 1. Transporte via Socket TCP
    public static class TransporteTCP implements ITransporte {
        private final Socket socket;
        private final PrintWriter out;
        private final BufferedReader in;

        public TransporteTCP(String ipOponente, int portaOponente, int minhaPorta, boolean inicia) throws Exception {
            if (inicia) {
                System.out.println("[TCP] Conectando ao oponente em " + ipOponente + ":" + portaOponente + "...");
                Socket s = null;
                int tentativas = 0;
                while (s == null && tentativas < 10) {
                    try {
                        Thread.sleep(1000);
                        s = new Socket(ipOponente, portaOponente);
                    } catch (Exception e) {
                        tentativas++;
                        System.out.println("[TCP] Tentando conectar... (" + tentativas + "/10)");
                    }
                }
                if (s == null) throw new SocketException("Não foi possível conectar ao oponente.");
                this.socket = s;
            } else {
                System.out.println("[TCP] Aguardando conexão do oponente na porta " + minhaPorta + "...");
                ServerSocket ss = new ServerSocket(minhaPorta);
                ss.setSoTimeout(65000); // 65s para receber a conexão
                this.socket = ss.accept();
                ss.close();
            }

            // Tolerância a falhas: timeout de 60 segundos por jogada
            this.socket.setSoTimeout(60000);
            this.out = new PrintWriter(socket.getOutputStream(), true);
            this.in = new BufferedReader(new InputStreamReader(socket.getInputStream()));
            System.out.println("[TCP] Canal de comunicação estabelecido com sucesso!");
        }

        @Override
        public void enviar(String msg) {
            out.println(msg);
        }

        @Override
        public String receber() throws Exception {
            return in.readLine();
        }

        @Override
        public void fechar() {
            try { if (socket != null && !socket.isClosed()) socket.close(); } catch (Exception ignored) {}
        }
    }

    // 2. Transporte via Socket UDP (Bônus do professor)
    public static class TransporteUDP implements ITransporte {
        private final DatagramSocket socket;
        private final InetAddress ipOponente;
        private final int portaOponente;

        public TransporteUDP(String ipOponente, int portaOponente, int minhaPorta) throws Exception {
            this.socket = new DatagramSocket(minhaPorta);
            this.socket.setSoTimeout(60000); // Tolerância a falhas: timeout de 60s
            this.ipOponente = InetAddress.getByName(ipOponente);
            this.portaOponente = portaOponente;
            System.out.println("[UDP] DatagramSocket aberto na porta " + minhaPorta + " pronto para enviar para " + ipOponente + ":" + portaOponente);
        }

        @Override
        public void enviar(String msg) throws Exception {
            byte[] buf = msg.getBytes(StandardCharsets.UTF_8);
            DatagramPacket packet = new DatagramPacket(buf, buf.length, ipOponente, portaOponente);
            socket.send(packet);
        }

        @Override
        public String receber() throws Exception {
            byte[] buf = new byte[1024];
            DatagramPacket packet = new DatagramPacket(buf, buf.length);
            socket.receive(packet);
            return new String(packet.getData(), 0, packet.getLength(), StandardCharsets.UTF_8);
        }

        @Override
        public void fechar() {
            if (socket != null && !socket.isClosed()) socket.close();
        }
    }

    // Abstração da lógica de jogos distribuídos
    public interface IJogo {
        void executar(ITransporte transporte, boolean inicia, DatagramSocket udpMulticast, String multicastIp, int multicastPorta, Scanner scanner) throws Exception;
    }

    // =========================================================================
    // JOGO 1: JOGO DA VELHA CLÁSSICO
    // =========================================================================
    public static class JogoVelha implements IJogo {
        private final int[] tabuleiro = new int[9];
        private int meuSimbolo;
        private int oponenteSimbolo;
        private int numeroJogada = 0;

        @Override
        public void executar(ITransporte transporte, boolean inicia, DatagramSocket udpMulticast, String multicastIp, int multicastPorta, Scanner scanner) throws Exception {
            if (inicia) {
                meuSimbolo = 1;       // X
                oponenteSimbolo = 2;  // O
                System.out.println("\n>> VOCÊ É O JOGADOR 'X' E INICIA A PARTIDA! <<\n");
            } else {
                meuSimbolo = 2;       // O
                oponenteSimbolo = 1;  // X
                System.out.println("\n>> VOCÊ É O JOGADOR 'O' E AGUARDA A PRIMEIRA JOGADA! <<\n");
            }

            boolean meuTurno = inicia;

            while (true) {
                if (meuTurno) {
                    imprimirTabuleiro();
                    System.out.print("Sua vez (" + (meuSimbolo == 1 ? "X" : "O") + ")! Digite a casa (0 a 8): ");

                    int jogada;
                    while (true) {
                        if (!scanner.hasNextInt()) {
                            scanner.next();
                            System.out.print("Entrada inválida! Digite um número de 0 a 8: ");
                            continue;
                        }
                        jogada = scanner.nextInt();
                        if (jogada >= 0 && jogada <= 8 && tabuleiro[jogada] == 0) {
                            break;
                        }
                        System.out.print("Casa inválida ou já ocupada! Escolha outra (0 a 8): ");
                    }

                    tabuleiro[jogada] = meuSimbolo;
                    String estado = verificarFimDeJogo(meuSimbolo);

                    String jsonJogada = montarJson(numeroJogada, jogada, estado);
                    numeroJogada++;

                    transporte.enviar(jsonJogada);
                    enviarMulticast(udpMulticast, jsonJogada, multicastIp, multicastPorta);

                    if (estado.equals("vit")) {
                        imprimirTabuleiro();
                        System.out.println("\n[VITORIA] PARABENS! VOCE VENCEU O JOGO DA VELHA!\n");
                        break;
                    } else if (estado.equals("emp")) {
                        imprimirTabuleiro();
                        System.out.println("\n[EMPATE] O JOGO EMPATOU (DEU VELHA)!\n");
                        break;
                    }

                    meuTurno = false;
                } else {
                    System.out.println("Aguardando jogada do oponente...");
                    String msgRecebida;
                    try {
                        msgRecebida = transporte.receber();
                    } catch (SocketTimeoutException te) {
                        System.err.println("\n[TIMEOUT] Tolerancia a falhas: limite de 60s esgotado!");
                        System.err.println("    O oponente não respondeu ou desconectou. Encerrando partida.");
                        break;
                    }

                    if (msgRecebida == null) {
                        System.out.println("\n[Conexão] O oponente encerrou a conexão.");
                        break;
                    }

                    int jogadaOponente = ServidorMatch.extrairInt(msgRecebida, "Jogada");
                    int numRecebido = ServidorMatch.extrairInt(msgRecebida, "Numero");
                    String resRecebido = ServidorMatch.extrairCampo(msgRecebida, "Resultado");

                    if (resRecebido.equals("der")) {
                        System.out.println("\n[Fim de Jogo] Oponente confirmou a derrota.");
                        break;
                    }

                    // Controle anti-cheat
                    boolean jogadaInvalida = (jogadaOponente < 0 || jogadaOponente > 8 || tabuleiro[jogadaOponente] != 0);
                    boolean sequenciaInvalida = (numRecebido != numeroJogada);

                    if (jogadaInvalida || sequenciaInvalida) {
                        System.out.println("\n[ANTI-CHEAT] Oponente tentou jogada ilegal!");
                        System.out.println("    Devolvendo a mesma mensagem para forçar correção...\n");
                        transporte.enviar(msgRecebida);
                        continue;
                    }

                    tabuleiro[jogadaOponente] = oponenteSimbolo;
                    numeroJogada++;

                    if (resRecebido.equals("vit")) {
                        imprimirTabuleiro();
                        System.out.println("\n[DERROTA] Voce perdeu! O oponente completou uma trinca.");
                        String msgDerrota = montarJson(numeroJogada, jogadaOponente, "der");
                        transporte.enviar(msgDerrota);
                        break;
                    } else if (resRecebido.equals("emp")) {
                        imprimirTabuleiro();
                        System.out.println("\n[EMPATE] O JOGO EMPATOU (DEU VELHA)!\n");
                        break;
                    }

                    meuTurno = true;
                }
            }
        }

        private String montarJson(int num, int jogada, String resultado) {
            StringBuilder sb = new StringBuilder();
            sb.append("{\"jogo\":\"velha\",");
            sb.append("\"Numero\":").append(num).append(",");
            sb.append("\"Jogada\":").append(jogada).append(",");
            sb.append("\"Tabuleiro\":[");
            for (int i = 0; i < 9; i++) {
                sb.append(tabuleiro[i]).append(i < 8 ? "," : "");
            }
            sb.append("],");
            sb.append("\"Resultado\":\"").append(resultado).append("\"}");
            return sb.toString();
        }

        private String verificarFimDeJogo(int jogador) {
            int[][] combinacoes = {
                {0,1,2}, {3,4,5}, {6,7,8},
                {0,3,6}, {1,4,7}, {2,5,8},
                {0,4,8}, {2,4,6}
            };
            for (int[] c : combinacoes) {
                if (tabuleiro[c[0]] == jogador && tabuleiro[c[1]] == jogador && tabuleiro[c[2]] == jogador) {
                    return "vit";
                }
            }
            for (int casa : tabuleiro) {
                if (casa == 0) return "jog";
            }
            return "emp";
        }

        private void imprimirTabuleiro() {
            char[] s = new char[9];
            for (int i = 0; i < 9; i++) {
                s[i] = (tabuleiro[i] == 1) ? 'X' : (tabuleiro[i] == 2) ? 'O' : (char) ('0' + i);
            }
            System.out.println("\n=== TABULEIRO ===");
            System.out.println("  " + s[0] + " | " + s[1] + " | " + s[2]);
            System.out.println(" ---+---+---");
            System.out.println("  " + s[3] + " | " + s[4] + " | " + s[5]);
            System.out.println(" ---+---+---");
            System.out.println("  " + s[6] + " | " + s[7] + " | " + s[8]);
            System.out.println("=================\n");
        }
    }

    // =========================================================================
    // JOGO 2: PEDRA, PAPEL E TESOURA (COM COMMIT-REVEAL SHA-256 ANTI-CHEAT)
    // =========================================================================
    public static class JogoPPT implements IJogo {
        @Override
        public void executar(ITransporte transporte, boolean inicia, DatagramSocket udpMulticast, String multicastIp, int multicastPorta, Scanner scanner) throws Exception {
            System.out.println("\n=======================================================");
            System.out.println("   PEDRA, PAPEL E TESOURA (MELHOR DE 3 RODADAS)");
            System.out.println("   Anti-Cheat: Esquema de Compromisso Criptográfico SHA-256");
            System.out.println("=======================================================\n");

            int vitoriasJ1 = 0;
            int vitoriasJ2 = 0;
            int rodada = 1;

            while (vitoriasJ1 < 2 && vitoriasJ2 < 2 && rodada <= 5) {
                System.out.println("--- RODADA #" + rodada + " --- [Placar: Você " + (inicia ? vitoriasJ1 : vitoriasJ2) + " x " + (inicia ? vitoriasJ2 : vitoriasJ1) + " Oponente]");

                if (inicia) {
                    // Jogador 1 inicia com a fase de compromisso (Commit)
                    String minhaEscolha = lerEscolha(scanner);
                    int salt = (int) (Math.random() * 900000) + 100000;
                    String hashCommit = calcularSHA256(minhaEscolha + ":" + salt);

                    // 1. Envia o hash (compromisso oculto)
                    String jsonCommit = "{\"jogo\":\"ppt\",\"tipo\":\"commit\",\"hash\":\"" + hashCommit + "\",\"rodada\":" + rodada + "}";
                    transporte.enviar(jsonCommit);
                    System.out.println("[Segurança] Compromisso SHA-256 enviado ao oponente. Aguardando escolha dele...");

                    // 2. Recebe a escolha aberta do Jogador 2
                    String msgEscolha = transporte.receber();
                    String escolhaJ2 = ServidorMatch.extrairCampo(msgEscolha, "escolha");

                    // 3. Revela a escolha do Jogador 1 junto com o salt
                    String jsonReveal = "{\"jogo\":\"ppt\",\"tipo\":\"reveal\",\"escolha\":\"" + minhaEscolha + "\",\"salt\":" + salt + ",\"rodada\":" + rodada + "}";
                    transporte.enviar(jsonReveal);

                    int vencedor = apurarVencedor(minhaEscolha, escolhaJ2);
                    if (vencedor == 1) vitoriasJ1++;
                    else if (vencedor == 2) vitoriasJ2++;

                    exibirResultadoRodada(minhaEscolha, escolhaJ2, vencedor, true);

                    // Publica no Multicast para os Displays
                    String resultadoGeral = (vitoriasJ1 >= 2) ? "vit_j1" : (vitoriasJ2 >= 2) ? "vit_j2" : "jog";
                    String jsonMulticast = "{\"jogo\":\"ppt\",\"Numero\":" + rodada + ",\"EscolhaJ1\":\"" + minhaEscolha + "\",\"EscolhaJ2\":\"" + escolhaJ2 + "\",\"VencedorRodada\":" + vencedor + ",\"Placar\":[" + vitoriasJ1 + "," + vitoriasJ2 + "],\"Resultado\":\"" + resultadoGeral + "\"}";
                    enviarMulticast(udpMulticast, jsonMulticast, multicastIp, multicastPorta);

                } else {
                    // Jogador 2 aguarda o compromisso do Jogador 1 antes de escolher
                    System.out.println("Aguardando o oponente enviar o compromisso seguro...");
                    String msgCommit = transporte.receber();
                    String hashRecebido = ServidorMatch.extrairCampo(msgCommit, "hash");

                    // Jogador 2 faz sua escolha e envia abertamente
                    String minhaEscolha = lerEscolha(scanner);
                    String jsonEscolha = "{\"jogo\":\"ppt\",\"tipo\":\"escolha\",\"escolha\":\"" + minhaEscolha + "\",\"rodada\":" + rodada + "}";
                    transporte.enviar(jsonEscolha);
                    System.out.println("Sua escolha foi enviada. Aguardando revelação da jogada do oponente...");

                    // Recebe a revelação do Jogador 1
                    String msgReveal = transporte.receber();
                    String escolhaJ1 = ServidorMatch.extrairCampo(msgReveal, "escolha");
                    int saltRecebido = ServidorMatch.extrairInt(msgReveal, "salt");

                    // Validação Anti-Cheat: verifica integridade do SHA-256
                    String hashVerificacao = calcularSHA256(escolhaJ1 + ":" + saltRecebido);
                    if (!hashVerificacao.equalsIgnoreCase(hashRecebido)) {
                        System.err.println("\n[SEGURANCA] O hash do oponente nao confere com a jogada revelada!");
                        System.err.println("   Tentativa de trapaca detectada na rede. Partida abortada.");
                        break;
                    }

                    int vencedor = apurarVencedor(escolhaJ1, minhaEscolha);
                    if (vencedor == 1) vitoriasJ1++;
                    else if (vencedor == 2) vitoriasJ2++;

                    exibirResultadoRodada(escolhaJ1, minhaEscolha, vencedor, false);
                }

                rodada++;
                System.out.println();
            }

            // Resultado Final
            System.out.println("=======================================================");
            if (inicia && vitoriasJ1 > vitoriasJ2 || !inicia && vitoriasJ2 > vitoriasJ1) {
                System.out.println("[FIM DE JOGO] PARABENS, VOCE VENCEU A DISPUTA DE PPT!");
            } else if (vitoriasJ1 == vitoriasJ2) {
                System.out.println("[FIM DE JOGO] O DUELO TERMINOU EMPATADO!");
            } else {
                System.out.println("[FIM DE JOGO] O OPONENTE VENCEU A DISPUTA DE PPT!");
            }
            System.out.println("=======================================================\n");
        }

        private String lerEscolha(Scanner scanner) {
            while (true) {
                System.out.print("Escolha (1: Pedra | 2: Papel | 3: Tesoura): ");
                String input = scanner.next().trim().toLowerCase();
                if (input.equals("1") || input.equals("pedra")) return "pedra";
                if (input.equals("2") || input.equals("papel")) return "papel";
                if (input.equals("3") || input.equals("tesoura")) return "tesoura";
                System.out.println("Opção inválida! Digite 1, 2 ou 3.");
            }
        }

        private int apurarVencedor(String j1, String j2) {
            if (j1.equals(j2)) return 0; // Empate
            if ((j1.equals("pedra") && j2.equals("tesoura")) ||
                (j1.equals("papel") && j2.equals("pedra")) ||
                (j1.equals("tesoura") && j2.equals("papel"))) {
                return 1; // Jogador 1
            }
            return 2; // Jogador 2
        }

        private void exibirResultadoRodada(String j1, String j2, int vencedor, boolean souJ1) {
            System.out.println(">> Escolha do Jogador 1: " + j1.toUpperCase());
            System.out.println(">> Escolha do Jogador 2: " + j2.toUpperCase());
            if (vencedor == 0) {
                System.out.println("-> Resultado da rodada: EMPATE!");
            } else if ((vencedor == 1 && souJ1) || (vencedor == 2 && !souJ1)) {
                System.out.println("-> Resultado da rodada: PONTO PARA VOCE!");
            } else {
                System.out.println("-> Resultado da rodada: Ponto para o oponente.");
            }
        }
    }

    // =========================================================================
    // JOGO 3: PAR OU ÍMPAR (COM COMMIT-REVEAL ANTI-CHEAT)
    // =========================================================================
    public static class JogoParOuImpar implements IJogo {
        @Override
        public void executar(ITransporte transporte, boolean inicia, DatagramSocket udpMulticast, String multicastIp, int multicastPorta, Scanner scanner) throws Exception {
            System.out.println("\n=======================================================");
            System.out.println("      PAR OU ÍMPAR DISTRIBUÍDO (MELHOR DE 3)");
            System.out.println("=======================================================\n");

            String opcaoJ1;
            String opcaoJ2;

            if (inicia) {
                System.out.print("Você inicia! Escolha (1: PAR | 2: ÍMPAR): ");
                while (true) {
                    String in = scanner.next().trim().toLowerCase();
                    if (in.equals("1") || in.equals("par")) {
                        opcaoJ1 = "par";
                        opcaoJ2 = "impar";
                        break;
                    }
                    if (in.equals("2") || in.equals("impar") || in.equals("ímpar")) {
                        opcaoJ1 = "impar";
                        opcaoJ2 = "par";
                        break;
                    }
                    System.out.print("Entrada inválida! Digite 1 (PAR) ou 2 (ÍMPAR): ");
                }
                transporte.enviar("{\"jogo\":\"par_impar\",\"tipo\":\"lado\",\"opcao\":\"" + opcaoJ1 + "\"}");
                System.out.println("Você escolheu: " + opcaoJ1.toUpperCase() + " (Oponente ficou com: " + opcaoJ2.toUpperCase() + ")");
            } else {
                System.out.println("Aguardando o Jogador 1 escolher PAR ou ÍMPAR...");
                String msgLado = transporte.receber();
                opcaoJ1 = ServidorMatch.extrairCampo(msgLado, "opcao");
                opcaoJ2 = opcaoJ1.equals("par") ? "impar" : "par";
                System.out.println("Jogador 1 escolheu: " + opcaoJ1.toUpperCase() + " | Você ficou com: " + opcaoJ2.toUpperCase());
            }

            int vitoriasJ1 = 0;
            int vitoriasJ2 = 0;
            int rodada = 1;

            while (vitoriasJ1 < 2 && vitoriasJ2 < 2 && rodada <= 5) {
                System.out.println("\n--- RODADA #" + rodada + " --- [Placar: Você " + (inicia ? vitoriasJ1 : vitoriasJ2) + " x " + (inicia ? vitoriasJ2 : vitoriasJ1) + " Oponente]");

                int dedosJ1;
                int dedosJ2;

                if (inicia) {
                    // Jogador 1 escolhe seus dedos e faz commit com hash
                    dedosJ1 = lerDedos(scanner);
                    int salt = (int) (Math.random() * 900000) + 100000;
                    String hashCommit = calcularSHA256(dedosJ1 + ":" + salt);

                    transporte.enviar("{\"jogo\":\"par_impar\",\"tipo\":\"commit\",\"hash\":\"" + hashCommit + "\",\"rodada\":" + rodada + "}");
                    System.out.println("[Segurança] Número criptografado com SHA-256. Aguardando oponente jogar os dedos...");

                    String msgDedos = transporte.receber();
                    dedosJ2 = ServidorMatch.extrairInt(msgDedos, "dedos");

                    // Revela os dedos e o salt
                    transporte.enviar("{\"jogo\":\"par_impar\",\"tipo\":\"reveal\",\"dedos\":" + dedosJ1 + ",\"salt\":" + salt + ",\"rodada\":" + rodada + "}");

                } else {
                    System.out.println("Aguardando o Jogador 1 registrar sua jogada de forma segura...");
                    String msgCommit = transporte.receber();
                    String hashRecebido = ServidorMatch.extrairCampo(msgCommit, "hash");

                    dedosJ2 = lerDedos(scanner);
                    transporte.enviar("{\"jogo\":\"par_impar\",\"tipo\":\"dedos\",\"dedos\":" + dedosJ2 + ",\"rodada\":" + rodada + "}");
                    System.out.println("Dedos enviados. Aguardando revelação do Jogador 1...");

                    String msgReveal = transporte.receber();
                    dedosJ1 = ServidorMatch.extrairInt(msgReveal, "dedos");
                    int saltRecebido = ServidorMatch.extrairInt(msgReveal, "salt");

                    // Validação Anti-Cheat
                    if (!calcularSHA256(dedosJ1 + ":" + saltRecebido).equalsIgnoreCase(hashRecebido)) {
                        System.err.println("\n[SEGURANCA] Hash incorreto! Tentativa de trapaca detectada.");
                        break;
                    }
                }

                int soma = dedosJ1 + dedosJ2;
                boolean deuPar = (soma % 2 == 0);
                String resultadoParidade = deuPar ? "par" : "impar";

                int vencedorRodada = resultadoParidade.equals(opcaoJ1) ? 1 : 2;
                if (vencedorRodada == 1) vitoriasJ1++;
                else vitoriasJ2++;

                System.out.println(">> Jogador 1 (" + opcaoJ1.toUpperCase() + ") colocou: " + dedosJ1 + " dedos");
                System.out.println(">> Jogador 2 (" + opcaoJ2.toUpperCase() + ") colocou: " + dedosJ2 + " dedos");
                System.out.println(">> Soma total: " + soma + " -> Deu " + resultadoParidade.toUpperCase() + "!");

                if ((vencedorRodada == 1 && inicia) || (vencedorRodada == 2 && !inicia)) {
                    System.out.println("-> Resultado da rodada: PONTO PARA VOCE!");
                } else {
                    System.out.println("-> Resultado da rodada: Ponto para o oponente.");
                }

                if (inicia) {
                    String resGeral = (vitoriasJ1 >= 2) ? "vit_j1" : (vitoriasJ2 >= 2) ? "vit_j2" : "jog";
                    String jsonMulticast = "{\"jogo\":\"par_impar\",\"Numero\":" + rodada + ",\"OpcaoJ1\":\"" + opcaoJ1 + "\",\"DedosJ1\":" + dedosJ1 + ",\"DedosJ2\":" + dedosJ2 + ",\"Soma\":" + soma + ",\"VencedorRodada\":" + vencedorRodada + ",\"Placar\":[" + vitoriasJ1 + "," + vitoriasJ2 + "],\"Resultado\":\"" + resGeral + "\"}";
                    enviarMulticast(udpMulticast, jsonMulticast, multicastIp, multicastPorta);
                }

                rodada++;
            }

            System.out.println("\n=======================================================");
            if (inicia && vitoriasJ1 > vitoriasJ2 || !inicia && vitoriasJ2 > vitoriasJ1) {
                System.out.println("[FIM DE JOGO] PARABENS, VOCE VENCEU O PAR OU IMPAR!");
            } else {
                System.out.println("[FIM DE JOGO] O OPONENTE VENCEU O PAR OU IMPAR!");
            }
            System.out.println("=======================================================\n");
        }

        private int lerDedos(Scanner scanner) {
            while (true) {
                System.out.print("Quantos dedos você coloca? (0 a 5): ");
                if (!scanner.hasNextInt()) {
                    scanner.next();
                    System.out.println("Entrada inválida! Digite um número de 0 a 5.");
                    continue;
                }
                int d = scanner.nextInt();
                if (d >= 0 && d <= 5) return d;
                System.out.println("Número fora do limite! Escolha entre 0 e 5 dedos.");
            }
        }
    }

    // =========================================================================
    // MÉTODO PRINCIPAL (MAIN)
    // =========================================================================
    public static void main(String[] args) {
        if (args.length < 2) {
            System.out.println("========================================================================================");
            System.out.println("Uso: java Jogador <NN> <MinhaPorta> [IP_RMI] [Protocolo: tcp|udp] [MeuIP] [Jogo]");
            System.out.println("Jogos disponíveis: velha (padrão) | ppt (Pedra-Papel-Tesoura) | par_impar (Par ou Ímpar)");
            System.out.println("Exemplo Velha:     java Jogador 14 5001 127.0.0.1 tcp 127.0.0.1 velha");
            System.out.println("Exemplo PPT:       java Jogador 14 5001 127.0.0.1 tcp 127.0.0.1 ppt");
            System.out.println("Exemplo Par/Ímpar: java Jogador 14 5001 127.0.0.1 udp 127.0.0.1 par_impar");
            System.out.println("========================================================================================");
            return;
        }

        int nn = Integer.parseInt(args[0]);
        int minhaPorta = Integer.parseInt(args[1]);
        String rmiHost = (args.length > 2) ? args[2] : "127.0.0.1";
        String protocolo = (args.length > 3) ? args[3].toLowerCase() : "tcp";

        String meuIp;
        try {
            meuIp = (args.length > 4) ? args[4] : InetAddress.getLocalHost().getHostAddress();
        } catch (Exception e) {
            meuIp = "127.0.0.1";
        }

        String jogo = (args.length > 5) ? args[5].toLowerCase() : "velha";
        if (!jogo.equals("ppt") && !jogo.equals("par_impar")) {
            jogo = "velha";
        }

        int portaRmi = 9800 + nn;
        String multicastIp = "225.1.1." + nn;
        int multicastPorta = 8800 + nn;

        try (Scanner scanner = new Scanner(System.in)) {
            // === FASE 1: CONSULTA RMI ===
            System.out.println("==========================================================");
            System.out.println("      JOGADOR DISTRIBUÍDO (" + protocolo.toUpperCase() + " - " + jogo.toUpperCase() + ")      ");
            System.out.println("==========================================================");
            System.out.println("[RMI] Conectando ao Servidor de Match em " + rmiHost + ":" + portaRmi + "...");

            Registry registry = LocateRegistry.getRegistry(rmiHost, portaRmi);
            ServidorMatch.IMatchService matchService =
                (ServidorMatch.IMatchService) registry.lookup("MatchService");

            String jsonPedido = String.format("{\"ip\":\"%s\",\"porta\":%d,\"protocolo\":\"%s\",\"jogo\":\"%s\"}", meuIp, minhaPorta, protocolo, jogo);
            System.out.println("[RMI] Enviando solicitação: " + jsonPedido);
            System.out.println("[RMI] Aguardando oponente para jogo '" + jogo.toUpperCase() + "' via " + protocolo.toUpperCase() + "...");

            String jsonRetorno = matchService.getOponent(jsonPedido);
            System.out.println("[RMI] Oponente pareado: " + jsonRetorno);

            String ipOponente = ServidorMatch.extrairCampo(jsonRetorno, "ip");
            int portaOponente = ServidorMatch.extrairInt(jsonRetorno, "porta");
            boolean inicia = jsonRetorno.contains("\"inicia\":true");

            // === FASE 2: INICIALIZAÇÃO DO CANAL P2P ===
            ITransporte transporte;
            if (protocolo.equals("udp")) {
                transporte = new TransporteUDP(ipOponente, portaOponente, minhaPorta);
            } else {
                transporte = new TransporteTCP(ipOponente, portaOponente, minhaPorta, inicia);
            }

            DatagramSocket udpMulticast = new DatagramSocket();

            // === FASE 3: EXECUÇÃO DO JOGO SELECIONADO ===
            IJogo jogoInstancia;
            if (jogo.equals("ppt")) {
                jogoInstancia = new JogoPPT();
            } else if (jogo.equals("par_impar")) {
                jogoInstancia = new JogoParOuImpar();
            } else {
                jogoInstancia = new JogoVelha();
            }

            jogoInstancia.executar(transporte, inicia, udpMulticast, multicastIp, multicastPorta, scanner);

            transporte.fechar();
            udpMulticast.close();
            System.out.println("Partida finalizada. Conexões fechadas com sucesso.");
        } catch (Exception e) {
            System.err.println("Erro na execução do Jogador: " + e.getMessage());
            e.printStackTrace();
        }
    }

    public static void enviarMulticast(DatagramSocket sock, String msg, String ip, int porta) {
        try {
            byte[] buf = msg.getBytes(StandardCharsets.UTF_8);
            DatagramPacket packet = new DatagramPacket(buf, buf.length, InetAddress.getByName(ip), porta);
            sock.send(packet);
        } catch (Exception ignored) {}
    }

    public static String calcularSHA256(String texto) {
        try {
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            byte[] hash = md.digest(texto.getBytes(StandardCharsets.UTF_8));
            StringBuilder sb = new StringBuilder();
            for (byte b : hash) {
                sb.append(String.format("%02x", b));
            }
            return sb.toString();
        } catch (Exception e) {
            return String.valueOf(texto.hashCode());
        }
    }
}
