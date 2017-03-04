package com.shuffle.sfnetworkscan;

import java.io.IOException;
import java.net.Inet4Address;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

public class SfNetworkScan {

	private static final transient Log log = LogFactory.getLog(SfNetworkScan.class);

	private static SfNetworkScan instance;

	private Set<String> devices = new HashSet<String>();

	private long scanInterval;

	private ScheduledExecutorService scheduledExecutorService;

	private DeviceManagerRunnable deviceManagerRunnable = new DeviceManagerRunnable(this);

	private int scanTimeout;

	private Map<String, Boolean> deviceStatus = new HashMap<>();

	private SfNetworkScanListener listener;

	private boolean allActive;

	private boolean allInactive;

	private Lock reentrantLock = new ReentrantLock();

	public static SfNetworkScan getInstance() {
		if (instance == null) {
			throw new IllegalArgumentException("Need to set scan interval at first time");
		}
		return instance;
	}

	public static SfNetworkScan getInstance(long scanInerval) {
		if (instance == null) {
			instance = new SfNetworkScan(scanInerval);
		}
		return getInstance();
	}

	private SfNetworkScan(long scanInterval) {
		this.scanInterval = scanInterval;
		this.scanTimeout = 2000;
		scheduledExecutorService = Executors.newSingleThreadScheduledExecutor();
		scheduledExecutorService.scheduleAtFixedRate(deviceManagerRunnable, 10, this.scanInterval, TimeUnit.SECONDS);
	}

	public Set<String> getDevices() {
		return devices;
	}

	public void setDevices(Set<String> devices) {
		this.devices = devices;
	}

	public void addDevice(String device) {
		this.devices.add(device);
	}

	public void removeDevice(String device) {
		this.devices.remove(device);
	}

	public long getScanInterval() {
		return scanInterval;
	}

	public void setScanInterval(long scanInterval) {
		this.scanInterval = scanInterval;
	}

	public int getScanTimeout() {
		return scanTimeout;
	}

	public void setScanTimeout(int scanTimeout) {
		this.scanTimeout = scanTimeout;
	}

	public SfNetworkScanListener getListener() {
		return listener;
	}

	public void setListener(SfNetworkScanListener listener) {
		this.listener = listener;
	}

	public boolean isAllActive() {
		try {
			reentrantLock.lock();
			return allActive;
		} finally {
			reentrantLock.unlock();
		}
	}

	public boolean isAllInactive() {
		try {
			reentrantLock.lock();
			return allInactive;
		} finally {
			reentrantLock.unlock();
		}
	}

	private class DeviceManagerRunnable implements Runnable {

		private SfNetworkScan deviceManager;

		public DeviceManagerRunnable(SfNetworkScan deviceManager) {
			this.deviceManager = deviceManager;
		}

		@Override
		public void run() {
			log.info("Started");
			try {
				reentrantLock.lock();

				deviceManager.allActive = true;
				deviceManager.allInactive = true;
				Map<String, Boolean> deviceStatus = new HashMap<>();
				for (String device : devices) {
					try {
						deviceStatus.put(device, Inet4Address.getByName(device).isReachable(scanTimeout));

					} catch (IOException e) {
						deviceManager.allActive = false;
					}
				}
				log.info("deviceStatus : " + deviceStatus);
				int totalChanged = 0;
				int totalActive = 0;
				int totalInactive = 0;
				for (String device : deviceStatus.keySet()) {
					Boolean oldStatus = deviceManager.deviceStatus.get(device);
					Boolean newStatus = deviceStatus.get(device);
					log.trace("oldStatus : " + oldStatus);
					log.trace("newStatus : " + newStatus);
					if (oldStatus == null || oldStatus != newStatus) {
						deviceManager.deviceStatus.put(device, newStatus);
						if (listener != null) {
							if (newStatus) {
								listener.onActive(device);
							} else {
								listener.onInactive(device);
							}
						}
						totalChanged++;
					}
					if (newStatus) {
						totalActive++;
					} else if (!newStatus) {
						totalInactive++;
					}
				}
				log.debug("totalActive : " + totalActive);
				log.debug("totalInactive : " + totalInactive);
				if (totalInactive == devices.size()) {
					deviceManager.allInactive = true;
				} else {
					deviceManager.allInactive = false;
				}
				if (totalActive == devices.size()) {
					deviceManager.allActive = true;
				} else {
					deviceManager.allActive = false;
				}
				log.debug("allActive : " + deviceManager.allActive);
				log.debug("allInactive : " + deviceManager.allInactive);
				if (listener != null) {
					if (deviceManager.allActive && totalChanged > 0) {
						listener.onAllActive();
					}
					if (deviceManager.allInactive && totalChanged > 0) {
						listener.onAllInactive();
					}
				}
			} catch (Exception e) {
				log.error("Something went wrong", e);
			} finally {
				reentrantLock.unlock();
			}
			log.info("Finished");
		}

	}
}