import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;

public class Recurso{
    private final String nome;
    private final Lock lock = new ReentrantLock();

    public Recurso(String nome) {
        this.nome = nome;
    }

    public String getNome() {
        return nome;
    }

    public Lock getLock() {
        return lock;
    }

    public boolean tryLock() {
        return lock.tryLock();
    }

    public void unlock() {
        lock.unlock();
    }

    public boolean isHeldByCurrentThread() {
        return ((ReentrantLock) lock).isHeldByCurrentThread();
    }
}

