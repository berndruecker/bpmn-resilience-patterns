package com.camunda.demo.resilience;

import org.camunda.bpm.engine.delegate.DelegateExecution;
import org.camunda.bpm.engine.delegate.ExecutionListener;

public class DecrementRetries implements ExecutionListener {
  
  private int defaultRetries=1;

  @Override
  public void notify(DelegateExecution execution) throws Exception {
    if (!execution.hasVariable("retries")) {
      execution.setVariable("retries", defaultRetries); // default, could be configured
    }
    else {
      execution.setVariable("retries", (Integer)execution.getVariable("retries")-1);
    }    
  }

}
