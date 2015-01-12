package com.pertamina.tbbm.rewulu.ecodriving.services;

import java.util.ArrayList;
import java.util.List;

import android.app.Service;
import android.content.Intent;
import android.os.AsyncTask;
import android.os.Binder;
import android.os.IBinder;

import com.pertamina.tbbm.rewulu.ecodriving.clients.LogsClient.ResponseLogs;
import com.pertamina.tbbm.rewulu.ecodriving.clients.TripClient.ResponseData;
import com.pertamina.tbbm.rewulu.ecodriving.controllers.ClientController;
import com.pertamina.tbbm.rewulu.ecodriving.databases.LogDataAdapter;
import com.pertamina.tbbm.rewulu.ecodriving.databases.MotorDataAdapter;
import com.pertamina.tbbm.rewulu.ecodriving.databases.TripDataAdapter;
import com.pertamina.tbbm.rewulu.ecodriving.listener.OnControllerCallback;
import com.pertamina.tbbm.rewulu.ecodriving.listener.OnLayangCallback;
import com.pertamina.tbbm.rewulu.ecodriving.pojos.DataLog;
import com.pertamina.tbbm.rewulu.ecodriving.pojos.Motor;
import com.pertamina.tbbm.rewulu.ecodriving.pojos.ResponseLog;
import com.pertamina.tbbm.rewulu.ecodriving.pojos.Tripdata;
import com.pertamina.tbbm.rewulu.ecodriving.pojos.UserData;
import com.pertamina.tbbm.rewulu.ecodriving.utils.Loggers;
import com.pertamina.tbbm.rewulu.ecodriving.utils.Utils;

public class Layang extends Service implements OnControllerCallback {
	private OnLayangCallback callback;
	// internet status
	private boolean available;
	// new session by userdata
	private boolean newSession;

	private Tripdata trip;
	private List<DataLog> logs;
	private List<DataLog> unLogged;
	private List<DataLog> tempLogs;

	private ClientController clientController;

	public class MyBinder extends Binder {
		public Layang getService() {
			return Layang.this;
		}
	}

	public void setOnCallback(OnLayangCallback callback) {
		this.callback = callback;
		start();
	}

	private final IBinder mBinder = new MyBinder();

	@Override
	public IBinder onBind(Intent intent) {
		// TODO Auto-generated method stub
		return mBinder;
	}

	@Override
	public int onStartCommand(Intent intent, int flags, int startId) {
		// TODO Auto-generated method stub
		start();
		return Service.START_NOT_STICKY;
	}

	private void start() {
		// TODO Auto-generated method stub
		if (timer.getStatus() != AsyncTask.Status.RUNNING)
			timer.executeOnExecutor(AsyncTask.THREAD_POOL_EXECUTOR);
		available = true;
		newSession = false;
		clientController = new ClientController(getApplicationContext(), this);
	}

	public void delete() {
		if (trip != null) {
			end();
			deleteTrip();
		}
	}

	public void end() {
		timer.cancel(true);
		clientController.destroy();
	}

	private Timer timer = new Timer();

	private class Timer extends AsyncTask<String, Boolean, Boolean> {
		@Override
		protected void onProgressUpdate(Boolean... values) {
			// TODO Auto-generated method stub
			super.onProgressUpdate(values);
			if (available != values[0]) {
				available = values[0];
				callback.isInternetAvailable(available);
				clientController.setInternetAvailable(available);
			}
			available();
		}

		@Override
		protected Boolean doInBackground(String... params) {
			// TODO Auto-generated method stub
			while (true) {
				publishProgress(Utils.isInternetAvailable());
				try {
					Thread.sleep(15000);
				} catch (InterruptedException e) {
					// TODO Auto-generated catch block
					Loggers.e("Timer", e.toString());
				}
			}
		}

	}

	private void available() {
		if (trip != null) {
			if (trip.getRow_id() < 0) {
				trip();
			} else if (unLogged.size() > 30) {
				logging();
			}
		}
		if (!newSession) {
			if (callback.getUser() != null) {
				if (callback.getUser().getRow_id() >= 0)
					session(callback.getUser());
				else
					register(callback.getUser());
			}
		}
	}

	public void startTrip(Tripdata trip) {
		if (trip == null) {
			Loggers.w("Layang - startTrip", "trip == null");
			return;
		}
		clientController.destroy();
		this.trip = trip;
		this.trip.setRunning();
		trip();
		logs = new ArrayList<>();
		unLogged = new ArrayList<>();
		start();
		callback.requestedStartTrip(trip);
		callback.onStartingLayang();
	}

	public void startResult() {
		if (this.trip == null) {
			Loggers.w("Layang -startResult ", "trip == null");
			return;
		}
		trip.setIncomplete();
		logging();
		if (!trip.isAddressStartSet())
			clientController.requestAddressStart(logs.get(0));
		clientController.requestAddressEnd(logs.get(logs.size() - 1));
		callback.requestedStartResult(trip, logs);
	}

	public void startHistoryResult(Tripdata trip) {
		if (trip == null) {
			Loggers.w("Layang - startResult", "trip == null");
			return;
		}
		this.trip = null;
		tempLogs = new ArrayList<>();
		logs = new ArrayList<>();
		unLogged = new ArrayList<>();
		callback.requestedStartResult(trip,
				LogDataAdapter.readAllLogByTrip(getApplicationContext(), trip));
	}

	public void setGraphTime_trip(ArrayList<Integer> graph) {
		if (this.trip == null) {
			Loggers.w("Layang - setGraphTime_trip", "trip == null");
			return;
		}
		trip.setGraph_time(graph);
	}

	public void setUser(UserData user) {
		if (this.trip == null) {
			Loggers.w("Layang - setUser", "trip == null");
			return;
		}
		trip.setUser(user);
	}

	public void setDataTrip(double eco_fuel, double non_eco_fuel,
			double eco_distance, double non_eco_distance) {
		if (this.trip == null) {
			Loggers.w("Layang - setDataTrip", "trip == null");
			return;
		}
		if (trip.getEco_fuel() <= 0) {
			Loggers.w("Layang", "trip.getEco_fuel() >= 0");
			return;
		}
		this.trip.setEco_fuel(eco_fuel);
		trip.setTotal_time(logs.get(logs.size() - 1).getTime()
				- logs.get(0).getTime());
		trip.setNon_eco_fuel(non_eco_fuel);
		trip.setEco_distance(eco_distance);
		trip.setNon_eco_distance(non_eco_distance);
		trip.Log("setDataTrip");
		updateTrip();
	}

	public void startHistories() {
		callback.requestedHistories(TripDataAdapter
				.readAllTrip(getApplicationContext()));
	}

	public Tripdata getTripdata() {
		return trip;
	}

	public void askDataMotor(String email) {
		clientController.askDataMotor(email);
	}

	public void upDateMotor(List<Motor> motors, String email) {
		clientController.upDateMotor(motors, email);
	}

	public void register(UserData userdata) {
		clientController.register(userdata);
	}

	public void session(UserData userdata) {
		clientController.session(userdata);
	}

	private void trip() {
		if (this.trip == null) {
			Loggers.w("Layang - trip", "trip == null");
			return;
		}
		Loggers.i("Layang", "trying to register trip");
		trip.Log("trip");
		clientController.trip(trip);
	}

	private void updateTrip() {
		if (this.trip == null) {
			Loggers.w("Layang - updateTrip", "trip == null");
			return;
		}
		Loggers.i("Layang", "trying to update trip");
		trip.Log("updateTrip");
		clientController.updateTrip(trip);
	}

	public void logData(DataLog log) {
		if (this.trip == null) {
			Loggers.w("Layang - logData", "trip == null");
			return;
		}
		if (!trip.isAddressStartSet()) {
			if (logs.isEmpty())
				clientController.requestAddressStart(log);
			else
				clientController.requestAddressStart(logs.get(0));
		}
		log.setTripdata(trip);
		log.setId(logs.size());
		unLogged.add(log);
		logs.add(log);
		Loggers.i("Layang", " receive log " + unLogged.size());
		if (trip.getRow_id() < 0) {
			clientController.trip(trip);
		} else if (unLogged.size() > 25) {
			logging();
		}
	}

	private void logging() {
		// TODO Auto-generated method stub
		if (this.trip == null) {
			Loggers.w("Layang - logging", "trip == null");
			return;
		}
		if (!unLogged.isEmpty()) {
			Loggers.i("Layang", "trying to log data size " + unLogged.size());
			tempLogs = unLogged;
			unLogged = new ArrayList<>();
			clientController.logData(tempLogs);
		}
	}

	private void deleteTrip() {
		if (this.trip == null) {
			Loggers.w("Layang - deleteTrip", "trip == null");
			return;
		}
		Loggers.i("Layang", "trying to delete trip");
		trip.Log("deleteTrip");
		clientController.deleteTrip(trip);
	}

	@Override
	public void onStartAddressResult(String address) {
		// TODO Auto-generated method stub
		if (this.trip == null) {
			Loggers.w("Layang - onStartAddressResult", "trip == null");
			return;
		}
		if (address != null) {
			Loggers.i("Layang", "receive start address");
			Loggers.i("Layang", address);
			trip.setAddrss_start(address);
			if (trip.isIncomplete())
				updateTrip();
		}
	}

	@Override
	public void onEndAddressResult(String address) {
		// TODO Auto-generated method stub
		if (this.trip == null) {
			Loggers.w("Layang - onEndAddressResult", "trip == null");
			return;
		}
		if (address != null) {
			Loggers.i("Layang", "receive end address");
			Loggers.i("Layang", address);
			trip.setAddrss_end(address);
			trip.Log("onEndAddressResult");
			if (trip.isIncomplete())
				updateTrip();
		}
	}

	@Override
	public void retrievingDataMotors(List<Motor> result, List<Motor> motors) {
		// TODO Auto-generated method stub
		List<Motor> rst = new ArrayList<>();
		if (callback != null && result != null) {
			if (motors != null) {
				for (int w = 0; w < motors.size(); w++)
					for (int e = 0; e < result.size(); e++) {
						if (motors.get(w).equality(result.get(e))) {
							rst.add(motors.get(w));
							motors.remove(w);
							result.remove(e);
							w -= 1;
							e -= 1;
						} else {
							if (motors.get(w).getSample()
									.equals(result.get(e).getSample())
									|| motors.get(w).getRow_id() == result.get(
											e).getRow_id()) {
								result.get(e).setLocal_id(
										motors.get(w).getLocal_id());
								rst.add(result.get(e));
								motors.remove(w);
								result.remove(e);
								w -= 1;
								e -= 1;
							}
						}
						if (w < 0)
							break;
					}
				for (Motor mtr : result)
					rst.add(mtr);
				for (Motor mtr : rst) {
					if (mtr.getLocal_id() >= 0)
						MotorDataAdapter.updateMotor(getApplicationContext(),
								mtr);
					else
						mtr.setLocal_id((int) MotorDataAdapter.insertMotor(
								getApplicationContext(), mtr));
				}
				callback.retrievingDataMotors(rst);
				MotorDataAdapter.deleteMotors(getApplicationContext(), motors);
				return;
			}
			for (Motor mtr : result) {
				if (mtr.getLocal_id() >= 0)
					MotorDataAdapter.updateMotor(getApplicationContext(), mtr);
				else
					mtr.setLocal_id((int) MotorDataAdapter.insertMotor(
							getApplicationContext(), mtr));
			}
			callback.retrievingDataMotors(result);
		}

	}

	@Override
	public void registerResult(UserData result) {
		// TODO Auto-generated method stub
		if (result.getRow_id() >= 0) {
			callback.registerResult(result);
			newSession = true;
		}
	}

	@Override
	public void onDeletedTrip(Tripdata result) {
		// TODO Auto-generated method stub
		if (this.trip == null) {
			Loggers.w("Layang - onDeletedTrip", "trip == null");
			return;
		}
		trip = null;
		logs = null;
		callback.onStoppingLayang();
	}

	@Override
	public void onLoggingResult(ResponseLogs result) {
		// TODO Auto-generated method stub
		if (result != null) {
			if (tempLogs != null)
				for (int e = 0; e < result.logs.size(); e++) {
					// errorsd
					for (int w = 0; w < tempLogs.size(); w++) {
						if (e < 0 || w < 0)
							break;
						if (tempLogs.isEmpty() || result.logs.isEmpty()
								|| result.logs.get(e).id < 0)
							break;
						if (tempLogs.get(w).getId() == result.logs.get(e).id) {
							logs.get(tempLogs.get(w).getId()).setRow_id(
									result.logs.get(e).row_id);
							LogDataAdapter.updateLog(getApplicationContext(),
									logs.get(tempLogs.get(w).getId()));
							tempLogs.remove(w);
							w -= 1;
							result.logs.remove(e);
							e -= 1;
						}
					}
					if (result.logs.isEmpty())
						break;
				}
			if (tempLogs != null && !tempLogs.isEmpty()) {
				unLogged.addAll(tempLogs);
			}
			for (ResponseLog res : result.logs) {
				logs.get(res.id).setRow_id(res.row_id);
				LogDataAdapter.updateLog(getApplicationContext(),
						logs.get(res.id));
			}
			if (trip != null && !unLogged.isEmpty()) {
				if (trip.isIncomplete())
					logging();
			}
		}

	}

	@Override
	public void onUpdateTripResult(ResponseData result) {
		// TODO Auto-generated method stub
		if (this.trip == null) {
			Loggers.w("Layang - onUpdateTripResult", "trip == null");
		}
		if (unLogged.isEmpty()) {
			Loggers.i("Layang", "Update trip success " + result.row_id);
			trip.setComplete();
		}
		if (trip != null) {
			TripDataAdapter.updateTrip(getApplicationContext(), trip);
			trip.Log("onUpdateTripResult");
		}
		trip = null;
		callback.onStoppingLayang();
	}

	@Override
	public void onTripResult(Tripdata result) {
		// TODO Auto-generated method stub
		if (this.trip == null) {
			Loggers.w("Layang - onTripResult", "trip == null");
			return;
		}
		trip = result;
		if (!logs.isEmpty())
			for (DataLog log : logs)
				log.setTripdata(result);
	}
}