import java.rmi.Remote;
import java.rmi.RemoteException;
import java.rmi.registry.LocateRegistry;
import java.rmi.registry.Registry;
import java.rmi.server.UnicastRemoteObject;
import java.util.HashMap;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * ServidorMatch: Pareamento de jogadores via Java RMI na porta 98NN.
 * Suporta pareamento concorrente independente para TCP e UDP (requisito de bônus).
 */
public class ServidorMatch {

    public interface IMatchService extends Remote {
        String getOponent(String jsonIn) throws RemoteException;
    }

    public static class MatchServiceImpl extends UnicastRemoteObject implements IMatchService {
        // Filas separadas por protocolo ("tcp" e "udp")
        private final Map<String, String> jogadoresPendentes = new HashMap<>();
        private final Map<String, String> respostasPendentes = new HashMap<>();
        private final Object lock = new Object();

        public MatchServiceImpl() throws RemoteException {
            super();
        }

        @Override
        public String getOponent(String jsonIn) throws RemoteException {
            String proto = extrairCampo(jsonIn, "protocolo").toLowerCase();
            if (!proto.equals("tcp") && !proto.equals("udp")) {
                proto = "tcp"; // Fallback padrão
            }

            String jogo = extrairCampo(jsonIn, "jogo").toLowerCase();
            if (jogo.isEmpty()) {
                jogo = "velha"; // Fallback padrão para manter retrocompatibilidade
            }

            String chaveFila = proto + "_" + jogo;

            synchronized (lock) {
                if (!jogadoresPendentes.containsKey(chaveFila)) {
                    // Primeiro jogador deste protocolo e jogo: entra na fila e aguarda
                    jogadoresPendentes.put(chaveFila, jsonIn);
                    System.out.println("\n[Matchmaker] Jogador 1 (" + chaveFila.toUpperCase() + ") conectado: " + jsonIn);
                    System.out.println("[Matchmaker] Aguardando oponente compatível...");

                    try {
                        // Tolerância a falhas: timeout de 60 segundos na fila de espera
                        lock.wait(60000);
                    } catch (InterruptedException e) {
                        Thread.currentThread().interrupt();
                    }

                    if (respostasPendentes.containsKey(chaveFila)) {
                        String resposta = respostasPendentes.remove(chaveFila);
                        jogadoresPendentes.remove(chaveFila);
                        System.out.println("[Matchmaker] Retornando para Jogador 1 (" + chaveFila.toUpperCase() + "): " + resposta);
                        return resposta;
                    } else {
                        // Timeout: ninguém com o mesmo protocolo e jogo entrou
                        jogadoresPendentes.remove(chaveFila);
                        System.out.println("[Matchmaker] Timeout na fila (" + chaveFila.toUpperCase() + "). Nenhum oponente encontrado.");
                        throw new RemoteException("Timeout: Nenhum oponente compatível encontrado dentro de 60 segundos para " + chaveFila);
                    }
                } else {
                    // Segundo jogador com o mesmo protocolo e jogo: MATCH FORMADO!
                    System.out.println("\n[Matchmaker] Jogador 2 (" + chaveFila.toUpperCase() + ") conectado: " + jsonIn);
                    System.out.println("[Matchmaker] Par formado com sucesso para: " + chaveFila.toUpperCase());

                    String pendente = jogadoresPendentes.get(chaveFila);
                    String ip1 = extrairCampo(pendente, "ip");
                    int porta1 = extrairInt(pendente, "porta");

                    String ip2 = extrairCampo(jsonIn, "ip");
                    int porta2 = extrairInt(jsonIn, "porta");

                    // Resposta para Jogador 1 (inicia = true)
                    String respostaJ1 = "{\"ip\":\"" + ip2 + "\",\"porta\":" + porta2 + ",\"jogo\":\"" + jogo + "\",\"inicia\":true}";
                    respostasPendentes.put(chaveFila, respostaJ1);

                    // Acorda a thread do Jogador 1
                    lock.notifyAll();

                    // Resposta imediata para Jogador 2 (inicia = false)
                    String respostaJ2 = "{\"ip\":\"" + ip1 + "\",\"porta\":" + porta1 + ",\"jogo\":\"" + jogo + "\",\"inicia\":false}";
                    System.out.println("[Matchmaker] Retornando para Jogador 2 (" + chaveFila.toUpperCase() + "): " + respostaJ2);
                    return respostaJ2;
                }
            }
        }
    }

    public static String extrairCampo(String json, String chave) {
        Pattern pattern = Pattern.compile("\"" + chave + "\"\\s*:\\s*\"([^\"]+)\"");
        Matcher matcher = pattern.matcher(json);
        return matcher.find() ? matcher.group(1) : "";
    }

    public static int extrairInt(String json, String chave) {
        Pattern pattern = Pattern.compile("\"" + chave + "\"\\s*:\\s*(\\d+)");
        Matcher matcher = pattern.matcher(json);
        return matcher.find() ? Integer.parseInt(matcher.group(1)) : 0;
    }

    public static void main(String[] args) {
        int nn = (args.length > 0) ? Integer.parseInt(args[0]) : 14;
        int portaRmi = 9800 + nn;

        try {
            Registry registry = LocateRegistry.createRegistry(portaRmi);
            MatchServiceImpl service = new MatchServiceImpl();
            registry.rebind("MatchService", service);

            System.out.println("==================================================");
            System.out.println("    SERVIDOR DE MATCHMAKING RMI INICIADO");
            System.out.println("    Conta (NN):      " + nn);
            System.out.println("    Porta RMI:       " + portaRmi);
            System.out.println("    Protocolos:      TCP e UDP simultâneos");
            System.out.println("    Serviço Remoto:  MatchService");
            System.out.println("==================================================");
            System.out.println("Aguardando conexões de jogadores...\n");
        } catch (Exception e) {
            System.err.println("Erro ao iniciar ServidorMatch: " + e.getMessage());
            e.printStackTrace();
        }
    }
}
