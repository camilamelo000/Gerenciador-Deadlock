import java.util.*;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;

public class Fronteira {
    public static void main(String[] args) {
        Map<String, Recurso> recursos = new HashMap<>();
        recursos.put("Bluebird", new Recurso("Bluebird"));
        recursos.put("Candy Necklace", new Recurso("Candy Necklace"));
        recursos.put("Breaking Up Slowly", new Recurso("Breaking Up Slowly"));
        recursos.put("West Coast", new Recurso("West Coast"));
        recursos.put("Yayo", new Recurso("Yayo"));
        recursos.put("Arcadia", new Recurso("Arcadia"));
        recursos.put("Blue Jeans", new Recurso("Blue Jeans"));
        recursos.put("Violets for Roses", new Recurso("Violets for Roses"));
        recursos.put("Dark Paradise", new Recurso("Dark Paradise"));
        recursos.put("Ride", new Recurso("Ride"));

        Controle gerenciador = new Controle(recursos);

        for (int i = 1; i <= 10; i++) {
            String threadName = "Thread_" + i;
            Thread thread = new Thread(threadName) {
                @Override
                public void run() {
                    gerenciador.executarThread(Thread.currentThread());
                }
            };
            thread.start();
        }
    }
}
