package com.devtiro.tickets.concurrency;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

/** Runs tasks on separate threads that all start at the same instant. */
final class ConcurrencyTestSupport {

  private ConcurrencyTestSupport() {
  }

  /** Outcome of one concurrent task: either a result or the exception it threw. */
  record Outcome<T>(T result, Throwable error) {
    boolean succeeded() {
      return error == null;
    }
  }

  static <T> List<Outcome<T>> runConcurrently(List<Callable<T>> tasks) throws InterruptedException {
    ExecutorService pool = Executors.newFixedThreadPool(tasks.size());
    CountDownLatch ready = new CountDownLatch(tasks.size());
    CountDownLatch start = new CountDownLatch(1);
    List<Future<Outcome<T>>> futures = new ArrayList<>();
    for (Callable<T> task : tasks) {
      futures.add(pool.submit(() -> {
        ready.countDown();
        start.await();
        try {
          return new Outcome<>(task.call(), null);
        } catch (Throwable t) {
          return new Outcome<T>(null, t);
        }
      }));
    }
    ready.await(10, TimeUnit.SECONDS);
    start.countDown(); // release every thread at once
    List<Outcome<T>> outcomes = new ArrayList<>();
    for (Future<Outcome<T>> f : futures) {
      try {
        outcomes.add(f.get(30, TimeUnit.SECONDS));
      } catch (Exception e) {
        outcomes.add(new Outcome<>(null, e));
      }
    }
    pool.shutdownNow();
    return outcomes;
  }
}
