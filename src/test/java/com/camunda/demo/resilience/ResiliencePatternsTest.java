package com.camunda.demo.resilience;

import static org.assertj.core.api.Assertions.fail;
import static org.camunda.bpm.engine.test.assertions.ProcessEngineAssertions.assertThat;
import static org.camunda.bpm.engine.test.assertions.ProcessEngineAssertions.init;
import static org.camunda.bpm.engine.test.assertions.ProcessEngineAssertions.processEngine;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.apache.ibatis.logging.LogFactory;
import org.camunda.bpm.engine.impl.ProcessEngineImpl;
import org.camunda.bpm.engine.impl.context.Context;
import org.camunda.bpm.engine.impl.interceptor.CommandExecutor;
import org.camunda.bpm.engine.impl.jobexecutor.ExecuteJobHelper;
import org.camunda.bpm.engine.impl.jobexecutor.JobExecutorContext;
import org.camunda.bpm.engine.runtime.Job;
import org.camunda.bpm.engine.runtime.ProcessInstance;
import org.camunda.bpm.engine.test.Deployment;
import org.camunda.bpm.engine.test.ProcessEngineRule;
import org.camunda.bpm.extension.process_test_coverage.junit.rules.TestCoverageProcessEngineRuleBuilder;
import org.junit.Before;
import org.junit.ClassRule;
import org.junit.Rule;
import org.junit.Test;


public class ResiliencePatternsTest {

  @Rule
  public ProcessEngineRule rule = TestCoverageProcessEngineRuleBuilder.create().build();

  static {
    LogFactory.useSlf4jLogging(); // MyBatis
  }

  @Before
  public void setup() {
    init(rule.getProcessEngine());
  }

  @Test
  @Deployment(resources = "models/sync-retry-1.bpmn")
  public void testSyncRetryFallback() {
    GuardedServiceA.fail = true;	  
    com.camunda.demo.resilience.GuardedServiceA.countFailed=0;
    ServiceB.fail = false;    
    ServiceB.countSuccess=0;

    // wait for https://forum.camunda.org/t/fallback-on-other-path-in-bpmn-when-no-retries-are-left/5019
	  ProcessInstance processInstance = 
	      processEngine().getRuntimeService().startProcessInstanceByKey("sync-retry-1");	 

	  // first try to execute service -> failure -> 1 retry scheduled for the future
	  assertTrue(executeNextJob(processInstance));
	  
	  // Ignore the retry due dates in the future - just do it right away
	  
    // 1. retry -> failure -> no retries left
    assertTrue(executeNextJob(processInstance));
    
    // so no more executable jobs
    assertFalse(executeNextJob(processInstance));
	  
	  // ServiceA was called 2 times
	  assertEquals(2, GuardedServiceA.countFailed);
    // ServiceB was called 1 times
    assertEquals(1, ServiceB.countSuccess);
  }

  @Test
  @Deployment(resources = "models/sync-retry-2.bpmn")
  public void testSyncRetryFallbackModeled() {
    ServiceA.fail = true;   
    ServiceA.countFailed=0;
    ServiceB.fail = false;    
    ServiceB.countSuccess=0;
    
    ProcessInstance processInstance = 
        processEngine().getRuntimeService().startProcessInstanceByKey("sync-retry-2");
    
    assertThat(processInstance).isWaitingAt("Timer");

    // Ignore the retry due dates in the future - just do it right away    
    // 1. retry -> failure -> no retries left
    assertTrue(executeNextJob(processInstance));
    
    // so no more executable jobs
    assertFalse(executeNextJob(processInstance));
    
    // ServiceA was called 2 times
    assertEquals(2, ServiceA.countFailed);
    // ServiceB was called 1 times
    assertEquals(1, ServiceB.countSuccess);
  }
  
  
  @Test
  @Deployment(resources = "models/async-retry-1.bpmn")
  public void testASyncRetryFallback() {
    ServiceA.fail = false;   
    ServiceA.countSuccess=0;
    ServiceB.fail = false;    
    ServiceB.countSuccess=0;
    
    ProcessInstance processInstance = 
        processEngine().getRuntimeService().startProcessInstanceByKey("async-retry-1");

    assertThat(processInstance).isWaitingAt("ReceiveTask");

    // now we do NOT send an answer
    // Ignore the retry due dates in the future - just do it right away    
    // This will resend the message the first time
    assertTrue(executeNextJob(processInstance));

    // This will resend the message the second time - then we do not have any more retries left
    assertTrue(executeNextJob(processInstance));

    // so no more executable jobs
    assertFalse(executeNextJob(processInstance));
    
    // ServiceA was called 2 times
    assertEquals(2, ServiceA.countSuccess);
    // ServiceB was called 1 times
    assertEquals(1, ServiceB.countSuccess);
 
  }
  
  private boolean executeNextJob(ProcessInstance pi) {
    long count = processEngine().getManagementService().createJobQuery() //
        .processInstanceId(pi.getId()) //
        .withRetriesLeft() // do not query for executable to get timers in the future as well
        .active() // do not take suspended jobs into account (same as real Job Executor)
        .count();
    if (count==0) {
      return false;
    }
    
    Job job = processEngine().getManagementService().createJobQuery() //
        .processInstanceId(pi.getId()) //
        .withRetriesLeft() //
        .active() // do not take suspended jobs into account (same as real Job Executor)
        .list().get(0);
    
    CommandExecutor commandExecutor = ((ProcessEngineImpl) processEngine()).getProcessEngineConfiguration().getCommandExecutorTxRequired();
    try {
      Context.setJobExecutorContext(new JobExecutorContext());
      ExecuteJobHelper.executeJob(job.getId(), commandExecutor);
    }
    catch (RuntimeException ex) {}
    finally {
      Context.removeJobExecutorContext();
    }
    return true;
  }

  @Test
  @Deployment(resources = "models/async-retry-2.bpmn")
  public void testASyncRetrySuspend() {    
    ProcessInstance processInstance = 
        processEngine().getRuntimeService().startProcessInstanceByKey("async-retry-2");
    
    assertThat(processInstance).isWaitingAt("SendTask_A");
    assertTrue(executeNextJob(processInstance));
    
    assertThat(processInstance).isWaitingAt("ReceiveTask");
    
    // Now we suspend all jobs containing "_A" in the corresponding ActivityId (in BPMN
    String SYSTEM_TO_SUSPEND = "_A";
    
    processEngine().getManagementService().createJobDefinitionQuery().list() //
      .stream()
      .filter(jobDef -> jobDef.getActivityId().contains(SYSTEM_TO_SUSPEND))
      .forEach(jobDef -> {
        
        // suspend, including already existing jobs
        processEngine().getManagementService().suspendJobDefinitionById(jobDef.getId(), true);
        
      });

    // now let's receive the message and go back in the loop
    processEngine().getRuntimeService().createMessageCorrelation("RESPONSE_A") //
      .processInstanceId(processInstance.getId()) //
      .correlateWithResult();

    assertThat(processInstance).isWaitingAt("SendTask_A");
    // now the job es suspended, meaning it will NOT be executed, but we keep being in the Send Task waiting
    assertFalse(executeNextJob(processInstance));
    assertThat(processInstance).isWaitingAt("SendTask_A");

    // now we can resume the job definition
    processEngine().getManagementService().createJobDefinitionQuery().list() //
    .stream()
    .filter(s -> s.getActivityId().contains(SYSTEM_TO_SUSPEND))
    .forEach(jobDef -> {
      
      // resume/activate, including already existing jobs
      processEngine().getManagementService().activateJobDefinitionById(jobDef.getId(), true);
      
    });

    // now it will move again
    assertTrue(executeNextJob(processInstance));
    assertThat(processInstance).isWaitingAt("ReceiveTask");    
  }

}
