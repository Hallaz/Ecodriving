package com.pertamina.tbbm.rewulu.ecodriving.listener;

import java.util.List;

import com.pertamina.tbbm.rewulu.ecodriving.helpers.ResultData;
import com.pertamina.tbbm.rewulu.ecodriving.pojos.Motor;
import com.pertamina.tbbm.rewulu.ecodriving.pojos.Tripdata;
import com.pertamina.tbbm.rewulu.ecodriving.pojos.UserData;

public interface OnLayangCallback {
	public void retrievingDataMotors(List<Motor> motors);

	public void registerResult(UserData user);

	public void requestedStartTrip(Tripdata trip);

	public void requestedStartResult(ResultData resultData);

	public void requestedHistories(List<Tripdata> trips);

	public void isInternetAvailable(boolean arg0);

	public UserData getUser();

	public void onStartingLayang();

	public void onStoppingLayang();

	public void GPSDisabled();
}
