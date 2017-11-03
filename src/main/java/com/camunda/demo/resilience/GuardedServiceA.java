package com.camunda.demo.resilience;

import org.camunda.bpm.engine.delegate.BpmnError;
import org.camunda.bpm.engine.delegate.DelegateExecution;
import org.camunda.bpm.engine.delegate.JavaDelegate;
import org.camunda.bpm.engine.impl.context.Context;
import org.camunda.bpm.engine.impl.jobexecutor.JobExecutorContext;

public class GuardedServiceA implements JavaDelegate {

  private static final String NO_RETRIES_ERROR = "NO_RETRIES";
  
  public static boolean fail = false;
  public static int countFailed = 0;
  public static int countSuccess = 0;

  @Override
  public void execute(DelegateExecution ctx) throws Exception {
    JobExecutorContext jobExecutorContext = Context.getJobExecutorContext();
    if (jobExecutorContext!=null && jobExecutorContext.getCurrentJob()!=null) {
      // this is called from a Job
      if (jobExecutorContext.getCurrentJob().getRetries()<=1) {
        // and the job will run out of retries when it fails again
        try {
          doExecute(ctx);          
        } catch (Exception ex) {
          // Probably save the exception somewhere
          throw new BpmnError(NO_RETRIES_ERROR);
        }
        return;
      }      
    }
    // otherwise normal behavior (including retries possibly)
    doExecute(ctx);    
  }

  private void doExecute(DelegateExecution ctx) {
    if (fail) {
      countFailed++;
      throw new RuntimeException("ServiceA fails as expected");
    }
    countSuccess++;    
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
