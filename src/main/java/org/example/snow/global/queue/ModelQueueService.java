package org.example.snow.global.queue;

import jakarta.annotation.PreDestroy;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.LinkedBlockingQueue;

@Slf4j
@Component
public class ModelQueueService {

    private final LinkedBlockingQueue<Runnable> embeddingQueue;
    private final LinkedBlockingQueue<Runnable> generationQueue;
    private final ExecutorService embeddingExecutor;
    private final ExecutorService generationExecutor;

    public ModelQueueService(
            @Value("${model-queue.embedding-capacity}") int embeddingCapacity,
            @Value("${model-queue.generation-capacity}") int generationCapacity
    ) {
        this.embeddingQueue = new LinkedBlockingQueue<>(embeddingCapacity);
        this.generationQueue = new LinkedBlockingQueue<>(generationCapacity);
        this.embeddingExecutor = Executors.newSingleThreadExecutor();
        this.generationExecutor = Executors.newSingleThreadExecutor();

        this.embeddingExecutor.submit(() -> drainQueue("embedding", embeddingQueue));
        this.generationExecutor.submit(() -> drainQueue("generation", generationQueue));

        log.info("ModelQueueService 초기화 완료 | embeddingCapacity={} generationCapacity={}",
                embeddingCapacity, generationCapacity);
    }

    /**
     * 임베딩 큐에 task 등록.
     * 정원 초과 시 false 반환.
     */
    public boolean submitEmbedding(Runnable task) {
        boolean accepted = embeddingQueue.offer(task);
        if (!accepted) {
            log.warn("임베딩 큐 정원 초과 — task 거부");
        }
        return accepted;
    }

    /**
     * 생성 큐에 task 등록.
     * 정원 초과 시 false 반환.
     */
    public boolean submitGeneration(Runnable task) {
        boolean accepted = generationQueue.offer(task);
        if (!accepted) {
            log.warn("생성 큐 정원 초과 — task 거부");
        }
        return accepted;
    }

    /**
     * 임베딩 큐에 여유 공간이 있는지 확인.
     * 사전 체크용 — offer() 직전 race condition이 있으므로 afterCommit fallback도 반드시 구현할 것.
     */
    public boolean canAcceptEmbedding() {
        return embeddingQueue.remainingCapacity() > 0;
    }

    /**
     * 생성 큐에 여유 공간이 있는지 확인.
     */
    public boolean canAcceptGeneration() {
        return generationQueue.remainingCapacity() > 0;
    }

    /**
     * Spring 컨텍스트 종료 시 executor 정리.
     * 미구현 시 non-daemon 스레드가 살아있어 JVM이 종료되지 않음.
     */
    @PreDestroy
    public void shutdown() {
        log.info("ModelQueueService 종료 — executor 정리 시작");
        embeddingExecutor.shutdownNow();
        generationExecutor.shutdownNow();
        log.info("ModelQueueService 종료 완료");
    }

    /**
     * 큐에서 task를 꺼내 순서대로 실행하는 루프.
     * 이 메서드는 각 executor의 단일 스레드 위에서 앱 종료 시까지 영구 실행된다.
     *
     * drainQueue의 catch(Exception)은 루프 보호 전용이다.
     * job 상태(FAILED 등) 관리는 각 task 내부의 try-catch가 담당해야 한다.
     */
    private void drainQueue(String queueName, LinkedBlockingQueue<Runnable> queue) {
        log.info("[{}] drainQueue 루프 시작", queueName);
        while (!Thread.currentThread().isInterrupted()) {
            try {
                Runnable task = queue.take();
                log.debug("[{}] task 실행 시작 | 대기 중={}", queueName, queue.size());
                task.run();
                log.debug("[{}] task 실행 완료 | 대기 중={}", queueName, queue.size());
            } catch (InterruptedException e) {
                log.info("[{}] drainQueue 루프 인터럽트 — 종료", queueName);
                Thread.currentThread().interrupt();
            } catch (Exception e) {
                // task 내부 예외 처리 누락 시 여기로 전파됨.
                // 루프는 계속 유지하되, task 자체가 markFailed 등 상태 관리를 직접 해야 한다.
                log.error("[{}] task 실행 중 처리되지 않은 예외 발생 (task 내부 예외 처리 누락 의심)", queueName, e);
            }
        }
    }
}
