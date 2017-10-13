package com.camunda.demo.resilience;

import org.camunda.bpm.engine.delegate.DelegateExecution;
import org.camunda.bpm.engine.delegate.JavaDelegate;

public class ServiceB implements JavaDelegate {

  public static boolean fail = false;
  public static int countFailed = 0;
  public static int countSuccess = 0;

  @Override
  public void execute(DelegateExecution ctx) throws Exception {
    if (fail) {
      countFailed++;
      throw new RuntimeException("ServiceB fails as expected");
    }
    countSuccess++;
    System.out.println(ctx.getCurrentActivityId());
  }

}
