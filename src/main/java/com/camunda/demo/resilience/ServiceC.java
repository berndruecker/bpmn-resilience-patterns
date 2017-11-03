package com.camunda.demo.resilience;

import org.camunda.bpm.engine.delegate.DelegateExecution;
import org.camunda.bpm.engine.delegate.JavaDelegate;

public class ServiceC implements JavaDelegate {

  public static boolean fail = false;
  public static int countFailed = 0;
  public static int countSuccess = 0;

  @Override
  public void execute(DelegateExecution ctx) throws Exception {
    if (fail) {
      countFailed++;
      throw new RuntimeException("ServiceC fails as expected");
    }
    countSuccess++;
    System.out.println(ctx.getCurrentActivityId());
  }


  public static void initFailing() {
    fail = true;    
    countSuccess = 0;
    countFailed = 0;
  }
  public static void initNotFailing() {
    fail = false;    
    countSuccess = 0;
    countFailed = 0;
  }

}
