package com.pertamina.tbbm.rewulu.ecodriving.controllers;

import java.util.ArrayList;
import java.util.List;

import retrofit.Callback;
import retrofit.RetrofitError;
import retrofit.client.Response;
import android.content.Context;
import android.os.AsyncTask;

import com.pertamina.tbbm.rewulu.ecodriving.clients.LogsClient;
import com.pertamina.tbbm.rewulu.ecodriving.clients.LogsClient.ResponseLogs;
import com.pertamina.tbbm.rewulu.ecodriving.clients.MotorClient;
import com.pertamina.tbbm.rewulu.ecodriving.clients.MotorClient.ResponseMotor;
import com.pertamina.tbbm.rewulu.ecodriving.clients.TripClient;
import com.pertamina.tbbm.rewulu.ecodriving.clients.TripClient.ResponseData;
import com.pertamina.tbbm.rewulu.ecodriving.clients.UserClient;
import com.pertamina.tbbm.rewulu.ecodriving.clients.UserClient.ResponseUser;
import com.pertamina.tbbm.rewulu.ecodriving.databases.DataLogAdapter;
import com.pertamina.tbbm.rewulu.ecodriving.databases.TripDataAdapter;
import com.pertamina.tbbm.rewulu.ecodriving.databases.sps.UserDataSP;
import com.pertamina.tbbm.rewulu.ecodriving.listener.OnControllerCallback;
import com.pertamina.tbbm.rewulu.ecodriving.locations.GeocoderEngine;
import com.pertamina.tbbm.rewulu.ecodriving.pojos.DataLog;
import com.pertamina.tbbm.rewulu.ecodriving.pojos.Motor;
import com.pertamina.tbbm.rewulu.ecodriving.pojos.Tripdata;
import com.pertamina.tbbm.rewulu.ecodriving.pojos.UserData;
import com.pertamina.tbbm.rewulu.ecodriving.utils.Api;
import com.pertamina.tbbm.rewulu.ecodriving.utils.Loggers;

public class ClientController {
	private boolean available;
	private Context context;
	private OnControllerCallback callback;

	public ClientController(Context context, OnControllerCallback callback) {
		// TODO Auto-generated constructor stub
		this.context = context;
		this.callback = callback;
		available = true;
	}

	public void setInternetAvailable(boolean available) {
		this.available = available;
	}

	public void destroy() {
		registrar.cancel(true);
		sessions.cancel(true);
		tripping.cancel(true);
		updateTrip.cancel(true);
		logging.cancel(true);
		getAddress.cancel(true);
	}

	public void askDataMotor(String email) {
		if (available)
			new RetrieveMotor().executeOnExecutor(
					AsyncTask.THREAD_POOL_EXECUTOR, email);
	}

	public void upDateMotor(List<Motor> motors, String email) {
		if (available)
			new RetrieveMotor(motors).executeOnExecutor(
					AsyncTask.THREAD_POOL_EXECUTOR, email);
	}

	private class RetrieveMotor extends AsyncTask<String, String, Boolean> {
		private List<Motor> motors = new ArrayList<>();

		public RetrieveMotor() {
			// TODO Auto-generated constructor stub
		}

		public RetrieveMotor(List<Motor> motors) {
			// TODO Auto-generated constructor stub
			this.motors = motors;
		}

		private Callback<ResponseMotor> callbackMotor = new Callback<MotorClient.ResponseMotor>() {

			@Override
			public void success(ResponseMotor arg0, Response arg1) {
				// TODO Auto-generated method stub
				callback.retrievingDataMotors(arg0.motors, motors);
			}

			@Override
			public void failure(RetrofitError arg0) {
				// TODO Auto-generated method stub
			}
		};

		@Override
		protected Boolean doInBackground(String... params) {
			// TODO Auto-generated method stub
			MotorClient.retrieveData(params[0], this.callbackMotor);
			return true;
		}

	}

	public void register(UserData userdata) {
		if (userdata != null) {
			if (available) {
				if (registrar.getStatus() == AsyncTask.Status.FINISHED
						|| registrar.getStatus() == AsyncTask.Status.PENDING) {
					registrar = new Registrar();
					registrar.executeOnExecutor(AsyncTask.THREAD_POOL_EXECUTOR,
							userdata);
				}
			} else {
				UserDataSP.put(context, userdata);
			}
		}
	}

	private Registrar registrar = new Registrar();

	private class Registrar extends AsyncTask<UserData, String, Boolean> {
		private UserData user;
		private Callback<ResponseUser> regcallback = new Callback<UserClient.ResponseUser>() {

			@Override
			public void success(ResponseUser arg0, Response arg1) {
				// TODO Auto-generated method stub
				if (!arg0.error) {
					user.setApi_key(arg0.api_key);
					user.setRow_id(arg0.row_id);
					UserDataSP.put(context, user);
					callback.registerResult(user);
				}
			}

			@Override
			public void failure(RetrofitError arg0) {
				// TODO Auto-generated method stub
				if (arg0.getMessage() != null) {
					Loggers.e("Registrar", "" + arg0.getMessage());
				} else

					Loggers.e("Registrar", "" + "arg0.getMessage() ERROR");

				UserDataSP.put(context, user);
				callback.registerResult(null);
			}
		};

		@Override
		protected Boolean doInBackground(UserData... params) {
			// TODO Auto-generated method stub
			this.user = params[0];
			UserClient.register(params[0], regcallback);
			return true;
		}
	}

	public void session(UserData userdata) {
		if (userdata.getRow_id() < 0) {
			register(userdata);
			return;
		}
		if (userdata != null
				&& available
				&& (sessions.getStatus() == AsyncTask.Status.FINISHED || sessions
						.getStatus() == AsyncTask.Status.PENDING)) {
			sessions = new Sessions();
			sessions.executeOnExecutor(AsyncTask.THREAD_POOL_EXECUTOR, userdata);
		}
	}

	private Sessions sessions = new Sessions();

	private class Sessions extends AsyncTask<UserData, String, Boolean> {
		private UserData user;
		private Callback<ResponseUser> regcallback = new Callback<UserClient.ResponseUser>() {

			@Override
			public void success(ResponseUser arg0, Response arg1) {
				// TODO Auto-generated method stub
				if (!arg0.error) {
					user.setApi_key(arg0.api_key);
					user.setRow_id(arg0.row_id);
					UserDataSP.put(context, user);
					callback.registerResult(user);
				}
			}

			@Override
			public void failure(RetrofitError arg0) {
				// TODO Auto-generated method stub
				if (arg0.getMessage() != null) {
					if (arg0.getMessage().contains(Api.INVALID_API_KEY))
						callback.requestNewAPI_KEY();
				}
			}
		};

		@Override
		protected Boolean doInBackground(UserData... params) {
			// TODO Auto-generated method stub
			this.user = params[0];
			UserClient.session(params[0], regcallback);
			return true;
		}
	}

	public void trip(Tripdata trip) {
		if (available && tripping.getStatus() != AsyncTask.Status.RUNNING) {
			tripping = new Tripping();
			tripping.executeOnExecutor(AsyncTask.THREAD_POOL_EXECUTOR, trip);
		} else if (!available) {
			if (trip.getLocal_id() == -1)
				trip.setLocal_id((int) TripDataAdapter
						.insertTrip(context, trip));
		}
	}

	private Tripping tripping = new Tripping();

	private class Tripping extends AsyncTask<Tripdata, Boolean, Tripdata> {
		private Tripdata trip;
		private Callback<ResponseData> tripcallback = new Callback<TripClient.ResponseData>() {

			@Override
			public void success(ResponseData arg0, Response arg1) {
				// TODO Auto-generated method stub
				trip.setRow_id(arg0.row_id);
				if (trip.getLocal_id() == -1)
					trip.setLocal_id((int) TripDataAdapter.insertTrip(context,
							trip));
				else
					TripDataAdapter.updateTrip(context, trip);
				callback.onTripResult(trip);
			}

			@Override
			public void failure(RetrofitError arg0) {
				// TODO Auto-generated method stub
				if (arg0.getMessage() != null) {
					Loggers.e("Tripping",
							"arg0.getMessage() " + arg0.getMessage());
					if (arg0.getMessage().contains(Api.INVALID_API_KEY)) {
						callback.requestNewAPI_KEY();
					}
				}
				if (trip.getLocal_id() < 0)
					trip.setLocal_id((int) TripDataAdapter.insertTrip(context,
							trip));
				callback.onTripResult(trip);
			}
		};

		@Override
		protected Tripdata doInBackground(Tripdata... params) {
			// TODO Auto-generated method stub
			trip = params[0];
			TripClient.trip(params[0], tripcallback);
			return null;

		}
	}

	public void updateTrip(Tripdata trip) {
		if (available && trip.getRow_id() >= 0
				&& updateTrip.getStatus() != AsyncTask.Status.RUNNING) {
			updateTrip = new UpdateTrip();
			updateTrip.executeOnExecutor(AsyncTask.THREAD_POOL_EXECUTOR, trip);
		} else if (!available) {
			TripDataAdapter.updateTrip(context, trip);
			updateTrip = new UpdateTrip();
			callback.onUpdateTripResult(null);
		}
	}

	private UpdateTrip updateTrip = new UpdateTrip();

	private class UpdateTrip extends AsyncTask<Tripdata, String, ResponseData> {
		private Tripdata trip;
		private Callback<ResponseData> tripcallback = new Callback<TripClient.ResponseData>() {

			@Override
			public void success(ResponseData arg0, Response arg1) {
				// TODO Auto-generated method stub
				TripDataAdapter.updateTrip(context, trip);
				callback.onUpdateTripResult(arg0);
			}

			@Override
			public void failure(RetrofitError arg0) {
				// TODO Auto-generated method stub
				TripDataAdapter.updateTrip(context, trip);
				callback.onUpdateTripResult(null);
			}
		};

		@Override
		protected ResponseData doInBackground(Tripdata... params) {
			// TODO Auto-generated method stub
			this.trip = params[0];
			TripDataAdapter.updateTrip(context, params[0]);
			TripClient.update(params[0], tripcallback);
			return null;
		}

		@Override
		protected void onPostExecute(ResponseData result) {
			// TODO Auto-generated method stub
			super.onPostExecute(result);
			if (result != null)
				callback.onUpdateTripResult(result);
		}

	}

	public void logData(List<DataLog> logs) {
		if (available && logging.getStatus() != AsyncTask.Status.RUNNING
				&& !logs.isEmpty()) {
			Loggers.i("", "logData " + logs.size());
			logging = new Logging(logs);
			logging.executeOnExecutor(AsyncTask.THREAD_POOL_EXECUTOR);
		} else if (!available) {
			for (DataLog log : logs)
				if (log.getLocal_id() < 0)
					log.setLocal_id((int) DataLogAdapter
							.insertLog(context, log));
		}
	}

	private Logging logging = new Logging();

	private class Logging extends AsyncTask<DataLog, String, ResponseLogs> {
		private List<DataLog> lgs;
		private Callback<ResponseLogs> logsCallback = new Callback<LogsClient.ResponseLogs>() {

			@Override
			public void success(ResponseLogs arg0, Response arg1) {
				// TODO Auto-generated method stub
				callback.onLoggingResult(arg0);
			}

			@Override
			public void failure(RetrofitError arg0) {
				// TODO Auto-generated method stub
				if (arg0.getMessage() != null)
					if (arg0.getMessage().contains(Api.INVALID_API_KEY)) {
						callback.requestNewAPI_KEY();
					}
			}
		};

		public Logging() {
			// TODO Auto-generated constructor stub
		
		}

		public Logging(List<DataLog> lgs) {
			// TODO Auto-generated constructor stub
			this.lgs = lgs;
		}

		@Override
		protected ResponseLogs doInBackground(DataLog... params) {
			// TODO Auto-generated method stub
			if (this.lgs == null)
				return null;
			for (DataLog log : lgs)
				if (log.getLocal_id() < 0)
					log.setLocal_id((int) DataLogAdapter
							.insertLog(context, log));
			LogsClient.logging(this.lgs, logsCallback);
			return null;
		}
	}

	public void deleteTrip(Tripdata trip) {
		if (available && trip.getRow_id() >= 0
				&& deleteTrip.getStatus() != AsyncTask.Status.RUNNING) {
			deleteTrip = new DeleteTrip();
			deleteTrip.executeOnExecutor(AsyncTask.THREAD_POOL_EXECUTOR, trip);
		} else if (!available) {
			callback.onDeletedTrip(null);
		}
	}

	private DeleteTrip deleteTrip = new DeleteTrip();

	private class DeleteTrip extends AsyncTask<Tripdata, String, Tripdata> {
		private Tripdata trip;
		private Callback<ResponseData> delCallback = new Callback<TripClient.ResponseData>() {

			@Override
			public void success(ResponseData arg0, Response arg1) {
				// TODO Auto-generated method stub
				TripDataAdapter.deleteById(context, trip);
				callback.onDeletedTrip(trip);
			}

			@Override
			public void failure(RetrofitError arg0) {
				// TODO Auto-generated method stub
				if (arg0.getMessage() != null)
					if (arg0.getMessage().contains(Api.INVALID_API_KEY))
						callback.requestNewAPI_KEY();
				callback.onDeletedTrip(null);
			}
		};

		@Override
		protected Tripdata doInBackground(Tripdata... params) {
			// TODO Auto-generated method stub
			trip = params[0];
			TripClient.delete(params[0], delCallback);
			return null;
		}
	}

	public void requestAddressStart(DataLog log) {
		if (log == null)
			return;
		if (!getAddress.getStatus().equals(AsyncTask.Status.RUNNING)) {
			Loggers.i("", "requestAddressStarst");
			getAddress = new GetAddressTask();
			getAddress.setParams(context, true);
			getAddress.executeOnExecutor(AsyncTask.THREAD_POOL_EXECUTOR,
					new Double[] { log.getLatitude(), log.getLongitude() });
		}
	}

	public void requestAddressEnd(DataLog log) {
		if (log == null)
			return;
		if (!getAddress.getStatus().equals(AsyncTask.Status.RUNNING)) {
			Loggers.i("", "requestAddressEnds");
			getAddress = new GetAddressTask();
			getAddress.setParams(context, false);
			getAddress.executeOnExecutor(AsyncTask.THREAD_POOL_EXECUTOR,
					new Double[] { log.getLatitude(), log.getLongitude() });
		}
	}

	private GetAddressTask getAddress = new GetAddressTask();

	private class GetAddressTask extends AsyncTask<Double, Void, String> {
		private Context mContext;
		private boolean start;

		public void setParams(Context context, boolean addrss_start) {
			this.start = addrss_start;
			mContext = context;
		}

		@Override
		protected String doInBackground(Double... params) {
			double latitude = params[0];
			double longitude = params[1];
			GeocoderEngine geocoder = new GeocoderEngine(mContext);
			return geocoder.getAddress(latitude, longitude);

		}

		@Override
		protected void onPostExecute(String result) {
			// TODO Auto-generated method stub
			super.onPostExecute(result);
			if (start)
				callback.onStartAddressResult(result);
			else {
				callback.onEndAddressResult(result);
			}
		}
	}

}
