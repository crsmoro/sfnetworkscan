package com.shuffle.sfnetworkscan;
public interface SfNetworkScanListener {

	void onActive(String device);
	
	void onInactive(String device);
	
	void onAllActive();
	
	void onAllInactive();
}