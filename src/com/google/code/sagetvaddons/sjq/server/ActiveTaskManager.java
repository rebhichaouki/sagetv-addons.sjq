/**
 * 
 */
package com.google.code.sagetvaddons.sjq.server;

import java.io.IOException;
import java.util.Date;
import java.util.TimerTask;

import org.apache.log4j.Logger;

import com.google.code.sagetvaddons.sjq.server.network.AgentClient;
import com.google.code.sagetvaddons.sjq.shared.QueuedTask;

/**
 * @author dbattams
 *
 */
final class ActiveTaskManager extends TimerTask {
	static private final Logger LOG = Logger.getLogger(ActiveTaskManager.class);
	
	/* (non-Javadoc)
	 * @see java.util.TimerTask#run()
	 */
	@Override
	public void run() {
		int i = 0;
		synchronized(TaskQueue.get()) {
			DataStore ds = DataStore.get();
			for(QueuedTask qt : ds.getActiveQueue()) {
				if(qt.getState() == QueuedTask.State.RUNNING) {
					++i;
					AgentClient agent = null;
					try {
						agent = new AgentClient(qt.getAssignee());
						if(!agent.isTaskActive(qt)) {
							qt.setState(QueuedTask.State.FAILED);
							qt.setCompleted(new Date());
							ds.updateTask(qt);
							LOG.warn("Marked " + qt + " as failed because " + qt.getAssignee() + " says it's not running the task!");
						}
					} catch (IOException e) {
						LOG.error("IOError", e);
					} finally {
						if(agent != null)
							agent.close();
					}
				}
			}
		}
		LOG.info("Validated " + i + " running task(s)!");
	}
}
