import java.util.*;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;

public class Deadlock {

    private static final Map<String, Lock> recursos = new HashMap<>();
    private static final Map<Thread, Set<String>> recursosAlocados = new HashMap<>();
    private static final Map<Thread, Integer> prioridades = new HashMap<>();
    private static int nextPrioridade = 1;
    private static final Random random = new Random();

    public static void main(String[] args) {
        
        recursos.put("Bluebird", new ReentrantLock());
        recursos.put("Candy Necklace", new ReentrantLock());
        recursos.put("Breaking Up Slowly", new ReentrantLock());
        recursos.put("West Coast", new ReentrantLock());
        recursos.put("Yayo", new ReentrantLock());
        recursos.put("Arcadia", new ReentrantLock());
        recursos.put("Blue Jeans", new ReentrantLock());
        recursos.put("Violets for Roses", new ReentrantLock());
        recursos.put("Dark Paradise", new ReentrantLock());
        recursos.put("Ride", new ReentrantLock());

        for (int i = 1; i <= 10; i++) {
            String threadName = "Thread_" + i;
            Thread thread = new Thread(threadName) {
                @Override
                public void run() {
                    definirPrioridade(Thread.currentThread());
                    List<String> recursosNecessarios = escolherRecursosAleatoriamente(3); 
                    System.out.println(Thread.currentThread().getName() + " vai tentar adquirir os recursos: " + recursosNecessarios);
                    boolean primeiraAquisição = true;
                    for (String recursoNome : recursosNecessarios) {
                        System.out.println(Thread.currentThread().getName() + " - Tentando adquirir o recurso: " + recursoNome);
                        try {
                            if (solicitarRecurso(Thread.currentThread(), recursoNome)) {
                                System.out.println(Thread.currentThread().getName() + " - Aquiriu o recurso: " + recursoNome);
                                if (primeiraAquisição) {
                                    Thread.sleep(random.nextInt(800));
                                    primeiraAquisição = false;
                                } else {
                                    Thread.sleep(random.nextInt(1200)); 
                                }
                            } else {
                                System.out.println(Thread.currentThread().getName() + " - Falhou ao adquirir o recurso: " + recursoNome + ". Tentando novamente mais tarde.");
                                Thread.sleep(random.nextInt(500));
                                if (solicitarRecurso(Thread.currentThread(), recursoNome)) {
                                    System.out.println(Thread.currentThread().getName() + " - Aquiriu o recurso (após tentativa): " + recursoNome);
                                    if (primeiraAquisição) {
                                        Thread.sleep(random.nextInt(800)); 
                                        primeiraAquisição = false;
                                    } else {
                                        Thread.sleep(random.nextInt(1200)); 
                                    }
                                } else {
                                    System.out.println(Thread.currentThread().getName() + " - Falha na segunda tentativa de adquirir: " + recursoNome + ". Abortando aquisição deste recurso.");
                                    break; 
                                }
                            }
                        } catch (InterruptedException e) {
                            Thread.currentThread().interrupt();
                            rollback(Thread.currentThread(), "Operação interrompida.");
                            liberarTodosRecursos(Thread.currentThread());
                            return;
                        }
                    }
                    System.out.println(Thread.currentThread().getName() + " - Vai liberar todos os recursos.");
                    liberarTodosRecursos(Thread.currentThread());
                }
            };
            thread.start();
        }
    }

    private static List<String> escolherRecursosAleatoriamente(int maxRecursos) {
        List<String> todosRecursos = new ArrayList<>(recursos.keySet());
        Collections.shuffle(todosRecursos);
        int numRecursos = 1 + random.nextInt(maxRecursos); 
        return todosRecursos.subList(0, Math.min(numRecursos, todosRecursos.size()));
    }

    private static synchronized boolean solicitarRecurso(Thread thread, String nomeRecurso) {
        Lock recurso = recursos.get(nomeRecurso);
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

    private static void liberarTodosRecursos(Thread thread) {
        Set<String> alocados = recursosAlocados.get(thread);
        if (alocados != null) {
            new HashSet<>(alocados).forEach(recursoNome -> liberarRecursoInterno(thread, recursoNome));
        }
    }

    private static void liberarRecursoInterno(Thread thread, String nomeRecurso) {
        Set<String> alocados = recursosAlocados.get(thread);
        Lock recurso = recursos.get(nomeRecurso);
        if (alocados != null && alocados.contains(nomeRecurso) && recurso != null && ((ReentrantLock) recurso).isHeldByCurrentThread()) {
            recurso.unlock();
            alocados.remove(nomeRecurso);
            System.out.println(thread.getName() + " liberou " + nomeRecurso);
        }
    }

    private static synchronized void definirPrioridade(Thread thread) {
        prioridades.put(thread, nextPrioridade++);
        System.out.println(thread.getName() + " recebeu prioridade: " + prioridades.get(thread));
    }

    private static void detectarDeadlock(Thread threadBloqueada, String recursoEsperado) {
        System.out.println("Potencial deadlock detectado! " + threadBloqueada.getName() + " esperando por " + recursoEsperado);

        for (Map.Entry<Thread, Set<String>> entry : recursosAlocados.entrySet()) {
            Thread outraThread = entry.getKey();
            if (outraThread != threadBloqueada && entry.getValue().contains(recursoEsperado)) {
                Set<String> esperandoPorOutra = getRecursosEsperados(outraThread);
                String recursoBloqueadoPelaThreadBloqueada = getRecursoBloqueado(threadBloqueada);
                if (esperandoPorOutra != null && recursoBloqueadoPelaThreadBloqueada != null && esperandoPorOutra.contains(recursoBloqueadoPelaThreadBloqueada)) {
                    System.out.println("*** Deadlock detectado entre " + threadBloqueada.getName() + " e " + outraThread.getName() + " ***");
                    Thread vitima = escolherVitima(threadBloqueada, outraThread);
                    abortarThread(vitima);
                    return;
                }
            }
        }
    }

    private static String getRecursoBloqueado(Thread thread) {
        Set<String> alocados = recursosAlocados.get(thread);
        return alocados != null && !alocados.isEmpty() ? alocados.iterator().next() : null;
    }

    private static Set<String> getRecursosEsperados(Thread thread) {
        List<String> todosRecursos = new ArrayList<>(recursos.keySet());
        Collections.shuffle(todosRecursos);
        return Collections.singleton(todosRecursos.get(0));
    }

    private static Thread escolherVitima(Thread thread1, Thread thread2) {
        Integer prioridade1 = prioridades.getOrDefault(thread1, Integer.MAX_VALUE);
        Integer prioridade2 = prioridades.getOrDefault(thread2, Integer.MAX_VALUE);

        System.out.println("Prioridade de " + thread1.getName() + ": " + prioridade1);
        System.out.println("Prioridade de " + thread2.getName() + ": " + prioridade2);

        return prioridade1 > prioridade2 ? thread1 : thread2;
    }

    private static void abortarThread(Thread vitima) {
        System.out.println("*** Abortando thread: " + vitima.getName() + " ***");
        rollback(vitima, "Operação da thread abortada devido a deadlock.");

        Set<String> recursosDaVitima = recursosAlocados.remove(vitima);
        if (recursosDaVitima != null) {
            for (String recurso : recursosDaVitima) {
                if (recursos.containsKey(recurso) && ((ReentrantLock) recursos.get(recurso)).isHeldByCurrentThread()) {
                    recursos.get(recurso).unlock();
                    System.out.println(vitima.getName() + " liberou (após aborto): " + recurso);
                } else if (recursos.containsKey(recurso)) {
                    recursos.get(recurso).unlock();  
                    System.out.println(vitima.getName() + " (abortada) liberou: " + recurso);
                }
            }
        }
        vitima.interrupt();
        prioridades.remove(vitima);
    }

    private static void rollback(Thread thread, String mensagem) {
        System.out.println("*** Rollback para " + thread.getName() + ": " + mensagem + " ***");
    }
}