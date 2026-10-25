import java.net.DatagramPacket;
import java.net.InetAddress;
import java.net.MulticastSocket;

/**
 * Display: Módulo espectador que escuta jogadas via Multicast UDP
 * no grupo 225.1.1.NN na porta 88NN e renderiza o status da partida
 * dinamicamente para múltiplos jogos: Jogo da Velha, Pedra-Papel-Tesoura
 * e Par-ou-Ímpar.
 */
public class Display {

    public static void main(String[] args) {
        int nn = (args.length > 0) ? Integer.parseInt(args[0]) : 14;
        String multicastIp = "225.1.1." + nn;
        int multicastPorta = 8800 + nn;

        try {
            @SuppressWarnings("deprecation")
            MulticastSocket socket = new MulticastSocket(multicastPorta);
            InetAddress group = InetAddress.getByName(multicastIp);

            // Inscreve-se no grupo multicast da disciplina
            socket.joinGroup(group);

            System.out.println("==================================================");
            System.out.println("          DISPLAY ESPECTADOR (CANAL ESPORTE)      ");
            System.out.println("==================================================");
            System.out.println("Conta (NN):      " + nn);
            System.out.println("Grupo Multicast: " + multicastIp + ":" + multicastPorta);
            System.out.println("Suporte:         Jogo da Velha • PPT • Par ou Ímpar");
            System.out.println("Aguardando partidas ao vivo...\n");

            byte[] buffer = new byte[1024];

            while (true) {
                DatagramPacket packet = new DatagramPacket(buffer, buffer.length);
                socket.receive(packet);

                String json = new String(packet.getData(), 0, packet.getLength());
                processarMensagemMulticast(json);
            }
        } catch (Exception e) {
            System.err.println("Erro no Display Multicast: " + e.getMessage());
            e.printStackTrace();
        }
    }

    private static void processarMensagemMulticast(String json) {
        String jogo = ServidorMatch.extrairCampo(json, "jogo");

        if (jogo.equalsIgnoreCase("ppt")) {
            desenharPPT(json);
        } else if (jogo.equalsIgnoreCase("par_impar")) {
            desenharParOuImpar(json);
        } else {
            desenharVelha(json);
        }
    }

    // 1. Renderização do Pedra, Papel e Tesoura
    private static void desenharPPT(String json) {
        int num = ServidorMatch.extrairInt(json, "Numero");
        String escolhaJ1 = ServidorMatch.extrairCampo(json, "EscolhaJ1").toUpperCase();
        String escolhaJ2 = ServidorMatch.extrairCampo(json, "EscolhaJ2").toUpperCase();
        int vencedorRodada = ServidorMatch.extrairInt(json, "VencedorRodada");
        String resultado = ServidorMatch.extrairCampo(json, "Resultado");

        String statusVencedor = (vencedorRodada == 0) ? "EMPATE NA RODADA" :
            (vencedorRodada == 1) ? "PONTO PARA O JOGADOR 1" : "PONTO PARA O JOGADOR 2";

        String statusPartida = resultado.equals("vit_j1") ? "🏆 CAMPEÃO DA DISPUTA: JOGADOR 1!" :
            resultado.equals("vit_j2") ? "🏆 CAMPEÃO DA DISPUTA: JOGADOR 2!" : "⚔️ DISPUTA EM ANDAMENTO";

        System.out.println("==================================================");
        System.out.println("  RODADA #" + num + " • PEDRA, PAPEL E TESOURA (AO VIVO)");
        System.out.println("==================================================");
        System.out.println("   Jogador 1: " + obterIconePPT(escolhaJ1) + " " + escolhaJ1);
        System.out.println("   Jogador 2: " + obterIconePPT(escolhaJ2) + " " + escolhaJ2);
        System.out.println("--------------------------------------------------");
        System.out.println("   Resultado da Rodada: " + statusVencedor);
        System.out.println("   Status do Duelo:     " + statusPartida);
        System.out.println("==================================================\n");
    }

    private static String obterIconePPT(String escolha) {
        if (escolha.contains("PEDRA")) return "[🪨 PEDRA]";
        if (escolha.contains("PAPEL")) return "[📄 PAPEL]";
        if (escolha.contains("TESOURA")) return "[✂️ TESOURA]";
        return "[" + escolha + "]";
    }

    // 2. Renderização do Par ou Ímpar
    private static void desenharParOuImpar(String json) {
        int num = ServidorMatch.extrairInt(json, "Numero");
        String opcaoJ1 = ServidorMatch.extrairCampo(json, "OpcaoJ1").toUpperCase();
        String opcaoJ2 = opcaoJ1.equalsIgnoreCase("PAR") ? "ÍMPAR" : "PAR";
        int dedosJ1 = ServidorMatch.extrairInt(json, "DedosJ1");
        int dedosJ2 = ServidorMatch.extrairInt(json, "DedosJ2");
        int soma = ServidorMatch.extrairInt(json, "Soma");
        int vencedorRodada = ServidorMatch.extrairInt(json, "VencedorRodada");
        String resultado = ServidorMatch.extrairCampo(json, "Resultado");

        String paridade = (soma % 2 == 0) ? "PAR" : "ÍMPAR";
        String statusVencedor = (vencedorRodada == 1) ? "PONTO PARA O JOGADOR 1 (" + opcaoJ1 + ")" : "PONTO PARA O JOGADOR 2 (" + opcaoJ2 + ")";
        String statusPartida = resultado.equals("vit_j1") ? "🏆 CAMPEÃO DO DUELO: JOGADOR 1!" :
            resultado.equals("vit_j2") ? "🏆 CAMPEÃO DO DUELO: JOGADOR 2!" : "⚔️ DISPUTA EM ANDAMENTO";

        System.out.println("==================================================");
        System.out.println("     RODADA #" + num + " • PAR OU ÍMPAR (AO VIVO)");
        System.out.println("==================================================");
        System.out.println("   Jogador 1 (" + opcaoJ1 + "): " + dedosJ1 + " dedo(s)");
        System.out.println("   Jogador 2 (" + opcaoJ2 + "): " + dedosJ2 + " dedo(s)");
        System.out.println("   Soma: " + dedosJ1 + " + " + dedosJ2 + " = " + soma + " (" + paridade + ")");
        System.out.println("--------------------------------------------------");
        System.out.println("   Resultado da Rodada: " + statusVencedor);
        System.out.println("   Status do Duelo:     " + statusPartida);
        System.out.println("==================================================\n");
    }

    // 3. Renderização do Jogo da Velha (Clássico)
    private static void desenharVelha(String json) {
        int num = ServidorMatch.extrairInt(json, "Numero");
        int jogada = ServidorMatch.extrairInt(json, "Jogada");
        String resultado = ServidorMatch.extrairCampo(json, "Resultado");

        // Extrai o vetor Tabuleiro [0, 1, 2...]
        int[] tab = new int[9];
        int start = json.indexOf("[");
        int end = json.indexOf("]");
        if (start != -1 && end != -1 && end > start + 1) {
            String[] tokens = json.substring(start + 1, end).split(",");
            for (int i = 0; i < 9 && i < tokens.length; i++) {
                try {
                    tab[i] = Integer.parseInt(tokens[i].trim());
                } catch (NumberFormatException ignored) {}
            }
        }

        char[] s = new char[9];
        for (int i = 0; i < 9; i++) {
            s[i] = (tab[i] == 1) ? 'X' : (tab[i] == 2) ? 'O' : ' ';
        }

        String statusDescricao;
        switch (resultado) {
            case "vit":
                statusDescricao = "🏆 VITÓRIA DO JOGADOR DA VEZ!";
                break;
            case "der":
                statusDescricao = "🏁 PARTIDA CONCLUÍDA (DERROTA CONFIRMADA)";
                break;
            case "emp":
                statusDescricao = "🤝 EMPATE (DEU VELHA!)";
                break;
            default:
                statusDescricao = "⚔️ EM ANDAMENTO";
                break;
        }

        System.out.println("--------------------------------------------------");
        System.out.println(" Jogada #" + num + " | Casa jogada: " + jogada + " | Status: " + statusDescricao);
        System.out.println("--------------------------------------------------");
        System.out.println("   " + s[0] + " | " + s[1] + " | " + s[2]);
        System.out.println("  ---+---+---");
        System.out.println("   " + s[3] + " | " + s[4] + " | " + s[5]);
        System.out.println("  ---+---+---");
        System.out.println("   " + s[6] + " | " + s[7] + " | " + s[8]);
        System.out.println("--------------------------------------------------\n");
    }
}
