package uk.org.smithfamily.mslogger;

import java.io.File;
import java.util.Date;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;

import uk.org.smithfamily.mslogger.parser.MsDatabase;
import uk.org.smithfamily.mslogger.parser.Repository;
import uk.org.smithfamily.mslogger.parser.Symbol;
import uk.org.smithfamily.mslogger.parser.log.Datalog;
import uk.org.smithfamily.mslogger.parser.log.DebugLogManager;
import android.app.*;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.location.LocationListener;
import android.location.LocationManager;
import android.os.*;
import android.preference.PreferenceManager;
import android.text.format.DateFormat;
import android.util.Log;
import bsh.commands.LocationController;

public class MSControlService extends Service implements SharedPreferences.OnSharedPreferenceChangeListener
{
	private static final DebugLogManager	dblog				= DebugLogManager.INSTANCE;
	private static AtomicBoolean			initialiseStarted	= new AtomicBoolean(false);
	protected static final String			CONNECTED			= "uk.org.smithfamily.mslogger.CONNECTED";
	protected static final String			NEW_DATA			= "uk.org.smithfamily.mslogger.NEW_DATA";
	private static final int				NOTIFICATION_ID		= 42;
	protected static final int				CLEAR_MESSAGES		= 0;

	private boolean							logging				= false;
	private NotificationManager				mNM;
	private final LocalBinder				mBinder				= new LocalBinder();
	private Handler							mHandler			= new Handler();
	private List<Symbol>					currentOutputs		= null;
	private Runnable						mUpdateTimeTask		= new Runnable()
																{
																	public void run()
																	{
																		// Debug.startMethodTracing("MSService");
																		int delayTime = 100;
																		long start = System.currentTimeMillis();
																		boolean connected = MsDatabase.INSTANCE.calculateRuntime();
																		dblog.log("calculateRuntime() : "
																				+ (System.currentTimeMillis() - start));
																		if (connected)
																		{
																			handleData();
																			Intent broadcast = new Intent();
																			broadcast.setAction(NEW_DATA);
																			// broadcast.putExtra(LOCATION,
																			// location);
																			sendBroadcast(broadcast);
																		}
																		else
																		{
																			delayTime = 1000;
																		}
																		// Debug.stopMethodTracing();
																		if (logging)
																			mHandler.postDelayed(this, delayTime);
																	}
																};
	private LocationManager					gpsLocationManager;

	/*
	 * private BroadcastReceiver updateReceiver = new BroadcastReceiver() {
	 * 
	 * @Override public void onReceive(Context context, Intent intent) { if
	 * (intent.getAction().equals(MSControlService.CONNECTED)) { startLogging();
	 * } }
	 * 
	 * };
	 */
	public class LocalBinder extends Binder
	{
		MSControlService getService()
		{
			return MSControlService.this;
		}
	}

	@Override
	public IBinder onBind(Intent arg0)
	{
		mNM = (NotificationManager) getSystemService(NOTIFICATION_SERVICE);
		gpsLocationManager = (LocationManager) getSystemService(Context.LOCATION_SERVICE);
		
		gpsLocationManager.requestLocationUpdates(LocationManager.GPS_PROVIDER, 1000, 0, LocationController.INSTANCE);

		// Display a notification about us starting. We put an icon in the
		// status bar.
		// showNotification(R.string.location_service_started);

		return mBinder;
		// interface.

	}

	public boolean isLogging()
	{
		return logging;
	}

	private void showNotification(int msg)
	{
		if (mNM == null)
			return;

		mNM.cancelAll();
		if (msg == CLEAR_MESSAGES)
		{
			return;
		}
		// What happens when the notification item is clicked
		Intent contentIntent = new Intent(this, MSLoggerActivity.class);

		PendingIntent pending = PendingIntent.getActivity(getBaseContext(), 0, contentIntent,
				android.content.Intent.FLAG_ACTIVITY_NEW_TASK);

		Notification nfc = new Notification(R.drawable.injector, null, System.currentTimeMillis());
		nfc.flags |= Notification.FLAG_ONGOING_EVENT;

		String contentText = getString(msg);

		nfc.setLatestEventInfo(getBaseContext(), getString(msg), contentText, pending);

		mNM.notify(NOTIFICATION_ID, nfc);
	}

	protected void handleData()
	{
		currentOutputs = MsDatabase.INSTANCE.cDesc.getOutputChannels();
	}

	public List<Symbol> getCurrentData()
	{
		return currentOutputs;
	}

	public void startLogging()
	{
		File externalStorageDirectory = Environment.getExternalStorageDirectory();
		File dir = new File(externalStorageDirectory, Repository.INSTANCE.getDataDir());
		dir.mkdirs();

		Date now = new Date();

		String fileName = DateFormat.format("yyyyMMddkkmmss", now).toString() + ".msl";
		File logFile = new File(dir, fileName);
		Datalog.INSTANCE.open(logFile);

		MsDatabase.INSTANCE.startLogging();
		mHandler.removeCallbacks(mUpdateTimeTask);
		mHandler.postDelayed(mUpdateTimeTask, 100);
		logging = true;

	}

	public void stopLogging()
	{
		MsDatabase.INSTANCE.stopLogging();
		mHandler.removeCallbacks(mUpdateTimeTask);
		logging = false;
		Datalog.INSTANCE.close();
	}

	@Override
	public void onCreate()
	{
		dblog.log("MSControlService:onCreate()");
		SharedPreferences settings = PreferenceManager.getDefaultSharedPreferences(this);
		settings.registerOnSharedPreferenceChangeListener(this);

		startInitialisation();

		/*
		 * IntentFilter connectionFilter = new
		 * IntentFilter(MSControlService.CONNECTED); IntentFilter newDataFilter
		 * = new IntentFilter(MSControlService.NEW_DATA);
		 * registerReceiver(updateReceiver, connectionFilter);
		 * registerReceiver(updateReceiver, newDataFilter);
		 */
	}

	private void startInitialisation()
	{
		if (initialiseStarted.getAndSet(true))
		{
			return;
		}

		new Thread(new Runnable()
		{
			public void run()
			{
				showNotification(R.string.connecting);
				Looper.prepare();
				while (Repository.INSTANCE.readInit(MSControlService.this) == false)
				{
					showNotification(R.string.cannot_connect);
					try
					{
						Thread.sleep(5000);

					}
					catch (InterruptedException e)
					{
					}
					showNotification(R.string.connecting);
				}

				Intent broadcast = new Intent();
				broadcast.setAction(CONNECTED);
				// broadcast.putExtra(LOCATION, location);
				sendBroadcast(broadcast);
				showNotification(R.string.connected);

			}
		}).start();

	}

	@Override
	public void onDestroy()
	{
		stopLogging();
		dblog.log("MSControlService:onDestroy()");
		mHandler.removeCallbacks(mUpdateTimeTask);
		showNotification(CLEAR_MESSAGES);
		gpsLocationManager.removeUpdates(LocationController.INSTANCE);
		
		SharedPreferences settings = PreferenceManager.getDefaultSharedPreferences(this);
		settings.unregisterOnSharedPreferenceChangeListener(this);
		super.onDestroy();

	}

	@Override
	public int onStartCommand(Intent intent, int flags, int startId)
	{
		Log.i("LocationService", "Received start id " + startId + ": " + intent);
		// We want this service to continue running until it is explicitly
		// stopped, so return sticky.
		return START_STICKY;
	}

	@Override
	public void onSharedPreferenceChanged(SharedPreferences sharedPreferences, String key)
	{
	}
}
