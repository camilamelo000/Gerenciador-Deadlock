import java.util.*;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;

public class Controle {
    
    private final Map<String, Recurso> recursos;
    private final Map<Thread, Set<String>> recursosAlocados = new HashMap<>();
    private final Map<Thread, Integer> prioridades = new HashMap<>();
    private int nextPrioridade = 1;
    private final Random random = new Random();

    public Controle(Map<String, Recurso> recursos) {
        this.recursos = recursos;
    }

    public void executarThread(Thread thread) {
        definirPrioridade(thread);
        List<String> recursosNecessarios = escolherRecursosAleatoriamente(3);
        System.out.println(thread.getName() + " vai tentar adquirir os recursos: " + recursosNecessarios);
        boolean primeiraAquisição = true;
        for (String recursoNome : recursosNecessarios) {
            System.out.println(thread.getName() + " - Tentando adquirir o recurso: " + recursoNome);
            try {
                if (solicitarRecurso(thread, recursoNome)) {
                    System.out.println(thread.getName() + " - Aquiriu o recurso: " + recursoNome);
                    if (primeiraAquisição) {
                        Thread.sleep(random.nextInt(800));
                        primeiraAquisição = false;
                    } else {
                        Thread.sleep(random.nextInt(1200));
                    }
                } else {
                    System.out.println(thread.getName() + " - Falhou ao adquirir o recurso: " + recursoNome + ". Tentando novamente mais tarde.");
                    Thread.sleep(random.nextInt(500));
                    if (solicitarRecurso(thread, recursoNome)) {
                        System.out.println(thread.getName() + " - Aquiriu o recurso (após tentativa): " + recursoNome);
                        if (primeiraAquisição) {
                            Thread.sleep(random.nextInt(800));
                            primeiraAquisição = false;
                        } else {
                            Thread.sleep(random.nextInt(1200));
                        }
                    } else {
                        System.out.println(thread.getName() + " - Falha na segunda tentativa de adquirir: " + recursoNome + ". Abortando aquisição deste recurso.");
                        break;
                    }
                }
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                rollback(thread, "Operação interrompida.");
                liberarTodosRecursos(thread);
                return;
            }
        }
        System.out.println(thread.getName() + " - Vai liberar todos os recursos.");
        liberarTodosRecursos(thread);
    }

    private List<String> escolherRecursosAleatoriamente(int maxRecursos) {
        List<String> todosRecursosNomes = new ArrayList<>(recursos.keySet());
        Collections.shuffle(todosRecursosNomes);
        int numRecursos = 1 + random.nextInt(maxRecursos);
        return todosRecursosNomes.subList(0, Math.min(numRecursos, todosRecursosNomes.size()));
    }

    public synchronized boolean solicitarRecurso(Thread thread, String nomeRecurso) {
        Recurso recurso = recursos.get(nomeRecurso);
        if (recurso != null) {
            System.out.println(thread.getName() + " tentando bloquear " + nomeRecurso);
            if (recurso.tryLock()) {
                System.out.println(thread.getName() + " bloqueou " + nomeRecurso);
                recursosAlocados.computeIfAbsent(thread, k -> new HashSet<>()).add(nomeRecurso);
                return true;
            } else {
                detectarDeadlock(thread, nomeRecurso);
                return false;
            }
        }
        return false;
    }

    public void liberarTodosRecursos(Thread thread) {
        Set<String> alocados = recursosAlocados.get(thread);
        if (alocados != null) {
            new HashSet<>(alocados).forEach(this::liberarRecursoInterno);
        }
    }

    private void liberarRecursoInterno(String nomeRecurso) {
        Thread thread = Thread.currentThread();
        Set<String> alocados = recursosAlocados.get(thread);
        Recurso recurso = recursos.get(nomeRecurso);
        if (alocados != null && alocados.contains(nomeRecurso) && recurso != null && recurso.isHeldByCurrentThread()) {
            recurso.unlock();
            alocados.remove(nomeRecurso);
            System.out.println(thread.getName() + " liberou " + nomeRecurso);
        }
    }

    public synchronized void definirPrioridade(Thread thread) {
        prioridades.put(thread, nextPrioridade++);
        System.out.println(thread.getName() + " recebeu prioridade: " + prioridades.get(thread));
    }

    private void detectarDeadlock(Thread threadBloqueada, String recursoEsperado) {
        System.out.println("Potencial deadlock detectado! " + threadBloqueada.getName() + " esperando por " + recursoEsperado);

        for (Map.Entry<Thread, Set<String>> entry : recursosAlocados.entrySet()) {
            Thread outraThread = entry.getKey();
            if (outraThread != threadBloqueada && entry.getValue().contains(recursoEsperado)) {
                Set<String> esperandoPorOutra = getRecursosEsperados(outraThread);
                String recursoBloqueadoPelaThreadBloqueada = getRecursoBloqueado(threadBloqueada);
                if (esperandoPorOutra != null && recursoBloqueadoPelaThreadBloqueada != null && esperandoPorOutra.contains(recursoBloqueadoPelaThreadBloqueada)) {
                    System.out.println("* Deadlock detectado entre " + threadBloqueada.getName() + " e " + outraThread.getName() + " *");
                    Thread vitima = escolherVitima(threadBloqueada, outraThread);
                    abortarThread(vitima);
                    return;
                }
            }
        }
    }

    private String getRecursoBloqueado(Thread thread) {
        Set<String> alocados = recursosAlocados.get(thread);
        return alocados != null && !alocados.isEmpty() ? alocados.iterator().next() : null;
    }

    private Set<String> getRecursosEsperados(Thread thread) {
        List<String> todosRecursosNomes = new ArrayList<>(recursos.keySet());
        Collections.shuffle(todosRecursosNomes);
        return Collections.singleton(todosRecursosNomes.get(0));
    }

    private Thread escolherVitima(Thread thread1, Thread thread2) {
        Integer prioridade1 = prioridades.getOrDefault(thread1, Integer.MAX_VALUE);
        Integer prioridade2 = prioridades.getOrDefault(thread2, Integer.MAX_VALUE);

        System.out.println("Prioridade de " + thread1.getName() + ": " + prioridade1);
        System.out.println("Prioridade de " + thread2.getName() + ": " + prioridade2);

        return prioridade1 > prioridade2 ? thread1 : thread2;
    }

    private void abortarThread(Thread vitima) {
        System.out.println("* Abortando thread: " + vitima.getName() + " *");
        rollback(vitima, "Operação da thread abortada devido a deadlock.");

        Set<String> recursosDaVitimaNomes = new HashSet<>(recursosAlocados.remove(vitima));
        if (recursosDaVitimaNomes != null) {
            for (String recursoNome : recursosDaVitimaNomes) {
                Recurso recurso = recursos.get(recursoNome);
                if (recurso != null && recurso.isHeldByCurrentThread()) {
                    recurso.unlock();
                    System.out.println(vitima.getName() + " liberou (após aborto): " + recursoNome);
                } else if (recurso != null) {
                    recurso.unlock();
                    System.out.println(vitima.getName() + " (abortada) liberou: " + recursoNome);
                }
            }
        }
        vitima.interrupt();
        prioridades.remove(vitima);
    }

    private void rollback(Thread thread, String mensagem) {
        System.out.println("* Rollback para " + thread.getName() + ": " + mensagem + " *");
    }
}
