/*
 *      Copyright 2010-2011 Battams, Derek
 *       
 *       Licensed under the Apache License, Version 2.0 (the "License");
 *       you may not use this file except in compliance with the License.
 *       You may obtain a copy of the License at
 *
 *          http://www.apache.org/licenses/LICENSE-2.0
 *
 *       Unless required by applicable law or agreed to in writing, software
 *       distributed under the License is distributed on an "AS IS" BASIS,
 *       WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *       See the License for the specific language governing permissions and
 *       limitations under the License.
 */
package com.google.code.sagetvaddons.sjq.server;

import gkusnick.sagetv.api.API;
import it.sauronsoftware.cron4j.Scheduler;

import java.io.File;
import java.io.IOException;
import java.util.Map;
import java.util.Timer;
import java.util.TimerTask;

import org.apache.commons.io.FileUtils;
import org.apache.commons.lang.StringUtils;
import org.apache.log4j.Logger;
import org.apache.log4j.PropertyConfigurator;

import sage.SageTVPlugin;
import sage.SageTVPluginRegistry;
import sagex.api.Configuration;

import com.google.code.sagetvaddons.license.License;
import com.google.code.sagetvaddons.license.LicenseResponse;
import com.google.code.sagetvaddons.sjq.listener.Listener;

/**
 * SageTVPlugin implementation for SJQv4
 * @author dbattams
 * @version $Id$
 */
public final class Plugin implements SageTVPlugin {
	static {
		PropertyConfigurator.configure("plugins/sjq/sjq.log4j.properties");
	}
	static private final Logger LOG = Logger.getLogger(Plugin.class);
	
	static public final String REC_STARTED = "RecordingStarted";
	static public final String NEW_SEGMENT = "RecordingSegmentAdded";
	static public final String MEDIA_IMPORTED = "MediaFileImported";
	static public final String SYS_MSG_POSTED = "SystemMessagePosted";
	static public final String REC_STOPPED = "RecordingStopped";
	static private final String[] EVENTS = new String[] {REC_STARTED, REC_STOPPED, NEW_SEGMENT, MEDIA_IMPORTED, SYS_MSG_POSTED};
	static private final String[] TV_EVENTS = new String[] {REC_STARTED, REC_STOPPED};
	
	static public final String OPT_QUEUE_FREQ = "QueueFreq";
	static public final String OPT_PING_FREQ = "PingFreq";
	static public final String OPT_ACTIVE_TASK_MGR_FREQ = "AtmFreq";
	static public final String OPT_QUEUE_CLEANER_FREQ = "QueueCleanerFreq";
	static public final String OPT_EMAIL = "RegisteredEmail";
	static public final String OPT_STATE = "LicState";
	static public final String OPT_KEEP_COMPLETED_DAYS = "KeepCompletedDays";
	static public final String OPT_KEEP_FAILED_DAYS = "KeepFailedDays";
	static public final String OPT_KEEP_SKIPPED_DAYS = "KeepSkippedDays";
	static private final String[] ALL_OPTS = new String[] {OPT_STATE, OPT_KEEP_COMPLETED_DAYS, OPT_KEEP_FAILED_DAYS, OPT_KEEP_SKIPPED_DAYS, OPT_QUEUE_FREQ, OPT_PING_FREQ, OPT_ACTIVE_TASK_MGR_FREQ, OPT_QUEUE_CLEANER_FREQ};
	
	/**
	 * The location of the SJQv4 crontab file to be used; relative to the base install dir of SageTV
	 */
	static public final File CRONTAB = new File("plugins/sjq/crontab");

	public static final String PLUGIN_ID = "sjq4";
	
	private Timer timer;
	private Thread agent;
	private Scheduler crontab;
	
	/**
	 * Constructor
	 * @param reg The plugin registry
	 */
	public Plugin(SageTVPluginRegistry reg) {
		timer = null;
		agent = null;
		if(!CRONTAB.exists()) {
			try {
				FileUtils.touch(CRONTAB);
			} catch (IOException e) {
				LOG.error("Unable to create crontab file!", e);
			}
		}
		crontab = new Scheduler();
		crontab.setDaemon(true);
		crontab.addTaskCollector(new CronTaskCollector());
		API.apiNullUI.configuration.SetServerProperty("sjq4/enginePort", String.valueOf(Config.get().getPort()));
	}
	
	@Override
	public void destroy() {
		// TODO Auto-generated method stub
		
	}

	@Override
	public String getConfigHelpText(String arg0) {
		if(OPT_QUEUE_FREQ.equals(arg0))
			return "Determines how often, in seconds, the queue looks for unassigned tasks and attempts to assign them to a task client.  Changes to this value require a restart of the plugin.";
		else if(OPT_PING_FREQ.equals(arg0))
			return "Determines how often, in seconds, the engine pings registered task clients to ensure they're still alive.  Changes to this value require a restart of the plugin.";
		else if(OPT_ACTIVE_TASK_MGR_FREQ.equals(arg0))
			return "Determines how often, in seconds, the engine validates all active tasks with their assigned task client.  Changes to this value require a restart of the plugin.";
		else if(OPT_QUEUE_CLEANER_FREQ.equals(arg0))
			return "Determines how often, in seconds, the engine cleans up old, completed entries from the task queue.  Changes to this value require a restart of the plugin.";
		else if(OPT_STATE.equals(arg0))
			return "This button shows the current licensing state of your SJQv4 engine plugin.  Configure your license details in the sagetv-addons license server plugin.  Clicking the button does nothing.";
		else if(OPT_KEEP_COMPLETED_DAYS.equals(arg0))
			return "Number of days to keep output from tasks that completed successfully.  This only deletes output stored in the SJQ database.";
		else if(OPT_KEEP_FAILED_DAYS.equals(arg0))
			return "Number of days to keep output from tasks that failed.  This only deletes output stored in the SJQ database.";
		else if(OPT_KEEP_SKIPPED_DAYS.equals(arg0))
			return "Number of days to keep output from tasks that were skipped.  This only deletes output stored in the SJQ database.";
		else
			return "No help available.";
	}

	@Override
	public String getConfigLabel(String arg0) {
		if(OPT_QUEUE_FREQ.equals(arg0))
			return "Queue Frequency (seconds)";
		else if(OPT_PING_FREQ.equals(arg0))
			return "Task Client Ping Frequency (seconds)";
		else if(OPT_ACTIVE_TASK_MGR_FREQ.equals(arg0))
			return "Active Task Verification Frequency (seconds)";
		else if(OPT_QUEUE_CLEANER_FREQ.equals(arg0))
			return "Queue Cleaner Frequency (seconds)";
		else if(OPT_STATE.equals(arg0))
			return "Licensing State of SJQv4 Engine";
		else if(OPT_KEEP_COMPLETED_DAYS.equals(arg0))
			return "Purge 'COMPLETED' Task Logs (days)";
		else if(OPT_KEEP_FAILED_DAYS.equals(arg0))
			return "Purge 'FAILED' Task Logs (days)";
		else if(OPT_KEEP_SKIPPED_DAYS.equals(arg0))
			return "Purge 'SKIPPED' Task Logs (days)";
		else
			return "<No Label>";
	}

	@Override
	public String[] getConfigOptions(String arg0) {
		// TODO Auto-generated method stub
		return null;
	}

	@Override
	public String[] getConfigSettings() {
		return ALL_OPTS;
	}

	@Override
	public int getConfigType(String arg0) {
		int type;
		if(OPT_QUEUE_FREQ.equals(arg0))
			type = SageTVPlugin.CONFIG_INTEGER;
		else if(OPT_PING_FREQ.equals(arg0))
			type = SageTVPlugin.CONFIG_INTEGER;
		else if(OPT_ACTIVE_TASK_MGR_FREQ.equals(arg0))
			type = SageTVPlugin.CONFIG_INTEGER;
		else if(OPT_QUEUE_CLEANER_FREQ.equals(arg0))
			type = SageTVPlugin.CONFIG_INTEGER;
		else if(OPT_STATE.equals(arg0))
			type = SageTVPlugin.CONFIG_BUTTON;
		else if(OPT_KEEP_COMPLETED_DAYS.equals(arg0))
			type = SageTVPlugin.CONFIG_INTEGER;
		else if(OPT_KEEP_FAILED_DAYS.equals(arg0))
			type = SageTVPlugin.CONFIG_INTEGER;
		else if(OPT_KEEP_SKIPPED_DAYS.equals(arg0))
			type = SageTVPlugin.CONFIG_INTEGER;
		else
			type = SageTVPlugin.CONFIG_TEXT;
		return type;
	}

	@Override
	public String getConfigValue(String arg0) {
		if(!OPT_STATE.equals(arg0))
			return DataStore.get().getSetting(arg0, getDefaultVal(arg0));
		return DataStore.get().isLicensed() ? "Licensed" : "Unlicensed";
	}

	static public String getDefaultVal(String arg0) {
		if(OPT_QUEUE_FREQ.equals(arg0))
			return "30";
		else if(OPT_PING_FREQ.equals(arg0))
			return "120";
		else if(OPT_ACTIVE_TASK_MGR_FREQ.equals(arg0))
			return "60";
		else if(OPT_QUEUE_CLEANER_FREQ.equals(arg0))
			return "1200";
		else if(OPT_KEEP_COMPLETED_DAYS.equals(arg0))
			return "7";
		else if(OPT_KEEP_FAILED_DAYS.equals(arg0))
			return "21";
		else if(OPT_KEEP_SKIPPED_DAYS.equals(arg0))
			return "1";
		else
			return "";
	}

	@Override
	public String[] getConfigValues(String arg0) {
		// TODO Auto-generated method stub
		return null;
	}

	@Override
	public void resetConfig() {
		// TODO Auto-generated method stub
		
	}

	@Override
	public void setConfigValue(String arg0, String arg1) {
		checkValid(arg0, arg1);
		DataStore.get().setSetting(arg0, arg1);
	}

	private void checkValid(String arg0, String arg1) {
		if(OPT_QUEUE_FREQ.equals(arg0))
			validateIntRange(arg1, 30, 300);
		else if(OPT_PING_FREQ.equals(arg0))
			validateIntRange(arg1, 30, 7200);
		else if(OPT_ACTIVE_TASK_MGR_FREQ.equals(arg0))
			validateIntRange(arg1, 15, 120);
		else if(OPT_QUEUE_CLEANER_FREQ.equals(arg0))
			validateIntRange(arg1, 600, 86400);
		else if(OPT_KEEP_COMPLETED_DAYS.equals(arg0))
			validateIntRange(arg1, 1, 365);
		else if(OPT_KEEP_FAILED_DAYS.equals(arg0))
			validateIntRange(arg1, 1, 365);
		else if(OPT_KEEP_SKIPPED_DAYS.equals(arg0))
			validateIntRange(arg1, 1, 365);
	}

	private void validateIntRange(String val, int min, int max) {
		final String ERR_MSG = "Input must be an integer in the range of " + min + " - " + max;
		if(val != null) {
			try {
				int num = Integer.parseInt(val);
				if(num < min || num > max)
					throw new IllegalArgumentException(ERR_MSG);
			} catch(NumberFormatException e) {
				throw new IllegalArgumentException(ERR_MSG, e);
			}
		} else
			throw new IllegalArgumentException(ERR_MSG);
	}
	
	@Override
	public void setConfigValues(String arg0, String[] arg1) {
		// TODO Auto-generated method stub
		
	}

	@Override
	public void start() {
		// Validate the license file
		License.autoConfig(DataStore.get().getSetting(OPT_EMAIL), new File("plugins/sagetv-addons.lic").getAbsolutePath());
		LicenseResponse resp = License.isLicensed(PLUGIN_ID);
		boolean isLicensed = resp.isLicensed();
		if(!isLicensed) {
			LOG.warn("Unable to validate sagetv-addons license file!  Some features of this software have been disabled!");
			LOG.warn("License server response: " + resp.getMessage());
		} else
			LOG.info("sagetv-addons license successfully validated!");
		Configuration.SetServerProperty(DataStore.LIC_PROP, Boolean.toString(isLicensed));

		DataStore ds = DataStore.get();
		ds.setSetting("SupportedEvents", StringUtils.join(EVENTS, ','));
		ds.setSetting("SupportedTvEvents", StringUtils.join(TV_EVENTS, ','));
		
		// Create the timer thread, which will run the agent pinger and the task queue threads periodically
		if(timer != null)
			timer.cancel();
		timer = new Timer(true);
		timer.schedule(new TimerTask() {

			@Override
			public void run() {
				TaskQueue.get().startTasks(false);
			}
			
		}, 15000, 1000L * Long.parseLong(getConfigValue(OPT_QUEUE_FREQ)));
		timer.schedule(new AgentManager(), 15000, 1000L * Long.parseLong(getConfigValue(OPT_PING_FREQ)));
		timer.schedule(new ActiveTaskManager(), 45000, 1000L * Long.parseLong(getConfigValue(OPT_ACTIVE_TASK_MGR_FREQ)));
		timer.schedule(new TaskQueueCleaner(), 60000, 1000L * Long.parseLong(getConfigValue(OPT_QUEUE_CLEANER_FREQ)));
		LOG.info("SJQ timer thread has been started!");
		
		// Start the server agent
		if(agent != null)
			agent.interrupt();
		agent = new Thread() {
			@Override
			public void run() {
				try {
					Thread.sleep(5500);
					new Listener("com.google.code.sagetvaddons.sjq.server.commands", Config.get().getPort(), Config.get().getLogPkg()).init();
					LOG.warn("SJQ server agent has stopped!");
				} catch (Exception e) {
					LOG.fatal("SJQ server agent has stopped unexpectedly!", e);
				}
			}
		};
		agent.start();
		LOG.info("Server agent has started!");
		
		// Start the crontab
		if(!crontab.isStarted())
			crontab.start();
		LOG.info("Server crontab has started!");

		SageTVPluginRegistry reg = (SageTVPluginRegistry)API.apiNullUI.pluginAPI.GetSageTVPluginRegistry();
		for(String event : EVENTS)
			reg.eventSubscribe(this, event);
	}

	@Override
	public void stop() {
		// Kill everything we started in start()
		if(timer != null) {
			timer.cancel();
			timer = null;
			LOG.info("SJQ timer thread has been stopped!");
		}
		if(agent != null) {
			agent.interrupt();
			agent = null;
			LOG.info("SJQ server agent has been stopped!");
		}
		if(crontab.isStarted()) {
			LOG.info("Stopping SJQ crontab...");
			crontab.stop();
			LOG.info("SJQ crontab has been stopped!");
		}
		
		SageTVPluginRegistry reg = (SageTVPluginRegistry)API.apiNullUI.pluginAPI.GetSageTVPluginRegistry();
		for(String event : EVENTS)
			reg.eventUnsubscribe(this, event);
		
		Configuration.SetServerProperty(DataStore.LIC_PROP, Boolean.toString(false));
	}

	@SuppressWarnings("rawtypes")
	@Override
	public void sageEvent(String arg0, Map arg1) {
		LOG.info("Event received: " + arg0);
		TaskLoader loader = null;
		if(arg0.matches(REC_STARTED + "|" + NEW_SEGMENT))
			loader = new TvRecordingTaskLoader(arg0, API.apiNullUI.mediaFileAPI.Wrap(arg1.get("MediaFile")));
		else if(arg0.equals(REC_STOPPED))
			loader = new TvRecordingTaskLoader(arg0, API.apiNullUI.mediaFileAPI.Wrap(arg1.get("MediaFile")));
		else if(arg0.equals(MEDIA_IMPORTED))
			loader = new ImportedMediaTaskLoader(API.apiNullUI.mediaFileAPI.Wrap(arg1.get("MediaFile")));
		else if(arg0.equals(SYS_MSG_POSTED))
			loader = new SystemMessageTaskLoader(API.apiNullUI.systemMessageAPI.Wrap(arg1.get("SystemMessage")));
		else
			LOG.warn("Event ignored: " + arg0);
		if(loader != null)
			loader.load();
	}
}
