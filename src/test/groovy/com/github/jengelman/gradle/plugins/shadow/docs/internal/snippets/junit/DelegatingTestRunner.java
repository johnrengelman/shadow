package com.github.jengelman.gradle.plugins.shadow.docs.internal.snippets.junit;

import org.junit.runner.Runner;
import org.junit.runners.Suite;
import org.junit.runners.model.InitializationError;
import org.junit.runners.model.RunnerScheduler;

import java.lang.reflect.InvocationTargetException;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

public class DelegatingTestRunner extends Suite {

  public DelegatingTestRunner(Class<?> clazz) throws InitializationError {
    super(clazz, extractRunners(clazz));

    setScheduler(new RunnerScheduler() {

      private final ExecutorService service = Executors.newFixedThreadPool(
        System.getenv("CI") != null ? 1 : Runtime.getRuntime().availableProcessors()
      );

      public void schedule(Runnable childStatement) {
        service.submit(childStatement);
      }

      public void finished() {
        try {
          service.shutdown();
          service.awaitTermination(Long.MAX_VALUE, TimeUnit.NANOSECONDS);
        } catch (InterruptedException e) {
          e.printStackTrace(System.err);
        }
      }
    });
  }

  @SuppressWarnings("unchecked")
  private static List<Runner> extractRunners(Class<?> clazz) throws InitializationError {
    if (!RunnerProvider.class.isAssignableFrom(clazz)) {
      throw new InitializationError(clazz.getName() + " does not implement " + RunnerProvider.class.getName());
    }

    Class<RunnerProvider> asType = (Class<RunnerProvider>) clazz;
    RunnerProvider instance;
    try {
      instance = asType.getConstructor().newInstance();
    } catch (InstantiationException | IllegalAccessException | InvocationTargetException | NoSuchMethodException e) {
      throw new InitializationError(e);
    }

    return instance.getRunners();
  }
}
