package uk.org.smithfamily.mslogger.activity;

import java.util.ArrayList;
import java.util.List;

import uk.org.smithfamily.mslogger.ApplicationSettings;
import uk.org.smithfamily.mslogger.R;
import uk.org.smithfamily.mslogger.ecuDef.Megasquirt;
import uk.org.smithfamily.mslogger.log.DatalogManager;
import uk.org.smithfamily.mslogger.log.DebugLogManager;
import uk.org.smithfamily.mslogger.log.EmailManager;
import uk.org.smithfamily.mslogger.log.FRDLogManager;
import uk.org.smithfamily.mslogger.service.MSLoggerService;
import uk.org.smithfamily.mslogger.widgets.*;
import android.app.Activity;
import android.app.Dialog;
import android.bluetooth.BluetoothAdapter;
import android.content.BroadcastReceiver;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.ServiceConnection;
import android.content.SharedPreferences;
import android.content.SharedPreferences.Editor;
import android.content.pm.*;
import android.content.pm.PackageManager.NameNotFoundException;
import android.graphics.PointF;
import android.os.Bundle;
import android.os.Debug;
import android.os.IBinder;
import android.preference.PreferenceManager;
import android.util.Log;
import android.view.*;
import android.view.GestureDetector.SimpleOnGestureListener;
import android.view.View.OnClickListener;
import android.view.View.OnLongClickListener;
import android.view.View.OnTouchListener;
import android.widget.*;

public class MSLoggerActivity extends Activity implements SharedPreferences.OnSharedPreferenceChangeListener, OnClickListener
{
    private MSGauge gauge1;
    private MSGauge gauge2;
    private MSGauge gauge3;
    private MSGauge gauge4;
    private MSGauge gauge5;

    public class GaugeClickListener implements OnClickListener
    {

        private MSGauge gauge;

        public GaugeClickListener(MSGauge gauge)
        {
            this.gauge = gauge;
        }

        @Override
        public void onClick(View arg0)
        {
            String g3name = gauge3.getName();
            gauge3.initFromName(gauge.getName());
            gauge.initFromName(g3name);
            gauge.invalidate();
            gauge3.invalidate();
        }

    }

    private boolean scrolling;

    class RotationDetector extends SimpleOnGestureListener
    {
        private MSGauge gauge;
        private float dragStartDeg;

        public RotationDetector(MSGauge gauge)
        {
            this.gauge = gauge;
        }

        private float positionToAngle(float x, float y)
        {
            float radius = PointF.length((x - 0.5f), (y - 0.5f));
            if (radius < 0.1f || radius > 0.5f)
            { // ignore center and out of bounds events
                return Float.NaN;
            }
            else
            {
                return (float) Math.toDegrees(Math.atan2(x - 0.5f, y - 0.5f));
            }
        }

        @Override
        public boolean onFling(MotionEvent e1, MotionEvent e2, float velocityX, float velocityY)
        {
            return false;
        }

        @Override
        public boolean onDoubleTap(MotionEvent e)
        {
            EditGaugeDialog dialog = new EditGaugeDialog(MSLoggerActivity.this, gauge.getName());
            dialog.show();
            gauge.initFromName(gauge.getName());
            gauge.invalidate();
            return true;
        }

        @Override
        public boolean onScroll(MotionEvent e1, MotionEvent e2, float distanceX, float distanceY)
        {

            if (!scrolling)
            {
                float x = e1.getX() / ((float) gauge.getWidth());
                float y = e1.getY() / ((float) gauge.getHeight());
                
                dragStartDeg = positionToAngle(x, y);
                
                scrolling = !Float.isNaN(dragStartDeg);
                return scrolling;
            }
            float currentDeg = positionToAngle(e2.getX() / gauge.getWidth(), 
                    e2.getY() / gauge.getHeight());
 
            
            gauge.setOffsetAngle(dragStartDeg-currentDeg);
            gauge.invalidate();
            
            scrolling = true;
            return true;

        }

    }

    private MSLoggerService   service;
    private static final int  REQUEST_ENABLE_BT = 0;
    private BroadcastReceiver updateReceiver    = new Reciever();
    private IndicatorManager  indicatorManager;
    private ToggleButton      connectButton;
    private TextView          messages;
    private Button            markButton;
    public boolean            connected;
    private boolean           receivedData      = false;

    private final class MSServiceConnection implements ServiceConnection
    {

        public void onServiceConnected(ComponentName className, IBinder binder)
        {
            service = ((MSLoggerService.MSLoggerBinder) binder).getService();
        }

        public void onServiceDisconnected(ComponentName className)
        {
            service = null;
        }
    }

    private final class LogButtonListener implements OnClickListener
    {
        private ToggleButton button;

        private LogButtonListener(ToggleButton button)
        {
            this.button = button;
        }

        @Override
        public void onClick(View arg0)
        {
            DebugLogManager.INSTANCE.log("LogButton:" + button.isChecked());
            if (System.currentTimeMillis() > 1322697601000L)
            {
                messages.setText("This beta version has expired");
                button.setChecked(false);
            }
            else
            {
                markButton.setEnabled(logButton.isChecked());

                if (service != null)
                {
                    if (button.isChecked())
                    {
                        service.startLogging();
                    }
                    else
                    {
                        service.stopLogging();
                        if (ApplicationSettings.INSTANCE.emailEnabled())
                        {
                            List<String> paths = new ArrayList<String>();
                            paths.add(DatalogManager.INSTANCE.getAbsolutePath());
                            paths.add(FRDLogManager.INSTANCE.getAbsolutePath());
                            paths.add(DebugLogManager.INSTANCE.getAbsolutePath());
                            String emailText = "Logfiles generated by MSLogger.\n\n\nhttps://bitbucket.org/scudderfish/mslogger";

                            String subject = String.format("MSLogger files %tc", System.currentTimeMillis());
                            EmailManager.email(MSLoggerActivity.this, ApplicationSettings.INSTANCE.getEmailDestination(), null,
                                    subject, emailText, paths);
                        }

                    }
                }
            }
        }

    }

    private final class ConnectButtonListener implements OnClickListener
    {
        private final ToggleButton button;

        private ConnectButtonListener(ToggleButton button)
        {
            this.button = button;
        }

        @Override
        public void onClick(View arg0)
        {
            DebugLogManager.INSTANCE.log("ConnectButton:" + button.isChecked());
            logButton.setChecked(false);

            if (button.isChecked())
            {
                startService(new Intent(MSLoggerActivity.this, MSLoggerService.class));
                doBindService();
                connected = true;
            }
            else
            {
                resetConnection();
                saveGauges();
            }
        }
    }

    private final class Reciever extends BroadcastReceiver
    {
        @Override
        public void onReceive(Context context, Intent intent)
        {
            Log.i(ApplicationSettings.TAG, "Received :" + intent.getAction());
            if (intent.getAction().equals(Megasquirt.CONNECTED))
            {
                indicatorManager.setDisabled(false);
                connectButton.setEnabled(true);
            }
            if (intent.getAction().equals(Megasquirt.DISCONNECTED))
            {
                if (receivedData && connectButton.isChecked())
                {
                    // We've been unfortunately disconnected so re-establish
                    // comms as if nothing happened
                    service.reconnect();
                    // connectButton.setEnabled(false);
                    // logButton.setEnabled(false);
                }
                else
                {
                    resetConnection();
                    messages.setText("Disconnected");
                }
            }

            if (intent.getAction().equals(Megasquirt.NEW_DATA))
            {
                logButton.setEnabled(connected);
                processData();
                receivedData = true;
            }
            if (intent.getAction().equals(ApplicationSettings.GENERAL_MESSAGE))
            {
                String msg = intent.getStringExtra(ApplicationSettings.MESSAGE);

                messages.setText(msg);
                Log.i(ApplicationSettings.TAG, "Message : " + msg);
                DebugLogManager.INSTANCE.log("Message : " + msg);

            }
        }
    }

    private ServiceConnection mConnection = new MSServiceConnection();
    private ToggleButton      logButton;
    private GestureDetector   gestureDetector;

    synchronized void doBindService()
    {

        bindService(new Intent(this, MSLoggerService.class), mConnection, Context.BIND_AUTO_CREATE);
    }

    @Override
    public void onStop()
    {
        super.onStop();
        saveGauges();
    }

    private void saveGauges()
    {
        SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(this);
        Editor editor = prefs.edit();
        editor.putString("gauge1", gauge1.getName());
        editor.putString("gauge2", gauge2.getName());
        editor.putString("gauge3", gauge3.getName());
        editor.putString("gauge4", gauge4.getName());
        editor.putString("gauge5", gauge5.getName());
        editor.commit();
    }

    /** Called when the activity is first created. */
    @Override
    public void onCreate(Bundle savedInstanceState)
    {
        super.onCreate(savedInstanceState);

        PreferenceManager.getDefaultSharedPreferences(this).registerOnSharedPreferenceChangeListener(this);

        indicatorManager = IndicatorManager.INSTANCE;

        setContentView(R.layout.displaygauge);
        indicatorManager.setDisabled(true);
        messages = (TextView) findViewById(R.id.messages);
        initGauges();
        initButtons();

        registerMessages();

        testBluetooth();
    }

    private void initGauges()
    {
        gauge1 = (MSGauge) findViewById(R.id.g1);
        gauge2 = (MSGauge) findViewById(R.id.g2);
        gauge3 = (MSGauge) findViewById(R.id.g3);
        gauge4 = (MSGauge) findViewById(R.id.g4);
        gauge5 = (MSGauge) findViewById(R.id.g5);
        Megasquirt ecu = ApplicationSettings.INSTANCE.getEcuDefinition();
        String[] defaultGauges = ecu.defaultGauges();
        gauge1.initFromName(ApplicationSettings.INSTANCE.getOrSetPref("gauge1", defaultGauges[0]));
        gauge2.initFromName(ApplicationSettings.INSTANCE.getOrSetPref("gauge2", defaultGauges[1]));
        gauge3.initFromName(ApplicationSettings.INSTANCE.getOrSetPref("gauge3", defaultGauges[2]));
        gauge4.initFromName(ApplicationSettings.INSTANCE.getOrSetPref("gauge4", defaultGauges[3]));
        gauge5.initFromName(ApplicationSettings.INSTANCE.getOrSetPref("gauge5", defaultGauges[4]));

        gauge1.setOnClickListener(new GaugeClickListener(gauge1));
        gauge2.setOnClickListener(new GaugeClickListener(gauge2));
        gauge4.setOnClickListener(new GaugeClickListener(gauge4));
        gauge5.setOnClickListener(new GaugeClickListener(gauge5));

        gestureDetector = new GestureDetector(new RotationDetector(gauge3));
        OnTouchListener gestureListener = new View.OnTouchListener()
        {
            public boolean onTouch(View v, MotionEvent event)
            {
                if (gestureDetector.onTouchEvent(event))
                {
                    return true;
                }

                if (event.getAction() == MotionEvent.ACTION_UP)
                {
                    if (scrolling)
                    {
                        scrolling = false;
                        GaugeDetails gd = gauge3.getDetails();
                        GaugeRegister.INSTANCE.persistDetails(gd);
                    }
                }

                return false;
            }
        };
        gauge3.setOnClickListener(MSLoggerActivity.this);
        gauge3.setOnTouchListener(gestureListener);

        gauge1.invalidate();
        gauge2.invalidate();
        gauge3.invalidate();
        gauge4.invalidate();
        gauge5.invalidate();

    }

    private void testBluetooth()
    {
        BluetoothAdapter mBluetoothAdapter = BluetoothAdapter.getDefaultAdapter();
        if (mBluetoothAdapter == null)
        {
            return;
        }
        boolean bluetoothOK = mBluetoothAdapter.isEnabled();
        if (!bluetoothOK)
        {
            Intent enableBtIntent = new Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE);
            startActivityForResult(enableBtIntent, REQUEST_ENABLE_BT);
        }
        else
        {
            connectButton.setEnabled(true);
        }
    }

    private void registerMessages()
    {
        IntentFilter connectedFilter = new IntentFilter(Megasquirt.CONNECTED);
        registerReceiver(updateReceiver, connectedFilter);
        IntentFilter disconnectedFilter = new IntentFilter(Megasquirt.DISCONNECTED);
        registerReceiver(updateReceiver, disconnectedFilter);
        IntentFilter dataFilter = new IntentFilter(Megasquirt.NEW_DATA);
        registerReceiver(updateReceiver, dataFilter);
        IntentFilter msgFilter = new IntentFilter(ApplicationSettings.GENERAL_MESSAGE);
        registerReceiver(updateReceiver, msgFilter);
    }

    private void initButtons()
    {
        connectButton = (ToggleButton) findViewById(R.id.connectButton);
        connectButton.setEnabled(MSLoggerService.isCreated());
        connectButton.setOnClickListener(new ConnectButtonListener(connectButton));

        logButton = (ToggleButton) findViewById(R.id.logButton);
        logButton.setEnabled(MSLoggerService.isCreated());
        logButton.setOnClickListener(new LogButtonListener(logButton));

        markButton = (Button) findViewById(R.id.Mark);
        markButton.setEnabled(logButton.isChecked());
        markButton.setOnClickListener(new OnClickListener()
        {

            @Override
            public void onClick(View arg0)
            {
                DatalogManager.INSTANCE.mark();
            }
        });
    }

    protected void processData()
    {
        List<Indicator> indicators;
        if ((indicators = indicatorManager.getIndicators()) != null)
        {
            indicatorManager.setDisabled(false);
            for (Indicator i : indicators)
            {
                String channelName = i.getChannel();
                if (channelName != null && service != null)
                {
                    double value = service.getValue(channelName);
                    i.setCurrentValue(value);
                }
                else
                {
                    i.setCurrentValue(0);
                }
            }
        }
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu)
    {
        MenuInflater inflater = getMenuInflater();
        inflater.inflate(R.menu.menu, menu);
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item)
    {
        // Handle item selection
        switch (item.getItemId())
        {
        case R.id.preferences:
            openPreferences();
            return true;
        case R.id.calibrate:
            openCalibrateTPS();
            return true;
        case R.id.about:
            showAbout();
            return true;
        default:
            return super.onOptionsItemSelected(item);
        }
    }

    private void showAbout()
    {
        Dialog dialog = new Dialog(this);

        dialog.setContentView(R.layout.about);

        TextView text = (TextView) dialog.findViewById(R.id.text);
        String title = "";
        try
        {
            PackageInfo pInfo = getPackageManager().getPackageInfo(getPackageName(), PackageManager.GET_META_DATA);
            ApplicationInfo ai;
            ai = pInfo.applicationInfo;
            final String applicationName = (String) (ai != null ? getPackageManager().getApplicationLabel(ai) : "(unknown)");
            title = applicationName + " " + pInfo.versionName;
        }
        catch (NameNotFoundException e)
        {
            e.printStackTrace();
        }
        dialog.setTitle(title);

        text.setText("An application to log information from Megasquirt ECUs.\n\nThanks to:\nPieter Corts\nMatthew Robson\nMartin Walton");
        ImageView image = (ImageView) dialog.findViewById(R.id.image);
        image.setImageResource(R.drawable.injector);

        dialog.show();
    }

    private void openCalibrateTPS()
    {
        Intent launchCalibrate = new Intent(this, CalibrateActivity.class);
        startActivity(launchCalibrate);
    }

    private void openPreferences()
    {
        Intent launchPrefs = new Intent(this, PreferencesActivity.class);
        startActivity(launchPrefs);
    }

    @Override
    protected void onDestroy()
    {
        super.onDestroy();
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data)
    {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode == REQUEST_ENABLE_BT && resultCode == RESULT_OK)
        {
            if (connectButton != null)
            {
                connectButton.setEnabled(true);
            }
        }
    }

    synchronized private void resetConnection()
    {
        DebugLogManager.INSTANCE.log("resetConnection()");

        connected = false;
        receivedData = false;
        service.stopLogging();
        markButton.setEnabled(false);
        logButton.setChecked(false);
        logButton.setEnabled(false);
        indicatorManager.setDisabled(true);
        connectButton.setChecked(false);
        connectButton.setEnabled(true);

        try
        {
            unbindService(mConnection);
            stopService(new Intent(MSLoggerActivity.this, MSLoggerService.class));
        }
        catch (Exception e)
        {

        }
    }

    @Override
    public void onSharedPreferenceChanged(SharedPreferences prefs, String key)
    {
        if (key.startsWith("gauge"))
        {
            initGauges();
        }

    }

    @Override
    public void onClick(View v)
    {
        // TODO Auto-generated method stub

    }
}
