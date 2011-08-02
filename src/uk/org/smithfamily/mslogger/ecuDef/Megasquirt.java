package uk.org.smithfamily.mslogger.ecuDef;

import java.io.*;
import java.lang.reflect.Field;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import uk.org.smithfamily.mslogger.ApplicationSettings;
import uk.org.smithfamily.mslogger.comms.MsComm;

import android.content.Context;
import android.content.Intent;
import android.content.res.AssetManager;

public abstract class Megasquirt implements Runnable
{
	public static final String	NEW_DATA	= "uk.org.smithfamily.mslogger.ecuDef.Megasquirt.NEW_DATA";

	protected Context			context;

	public abstract String getSignature();

	public abstract byte[] getOchCommand();

	public abstract byte[] getSigCommand();

	public abstract void loadConstants();

	public abstract void calculate(byte[] ochBuffer);

	public abstract String getLogHeader();

	public abstract String getLogRow();

	public abstract int getBlockSize();

	static Map<String, List<Integer>>	tables		= new HashMap<String, List<Integer>>();

	private long						lastTime	= System.currentTimeMillis();

	private volatile boolean			running		= false;
	protected MsComm					comm;
	private byte[]						ochBuffer;

	public void setRunning(boolean r)
	{
		running = r;
	}

	public void run()
	{
		running = true;

		if(connected())
		{
			loadConstants();
		}
		while (running)
		{
			if (connected())
			{
				comm.flush();
				getRuntimeVars();
				calculateValues();
				broadcastNewData();
				throttle();
			}
			else
			{
				sendMessage("Cannot connect to Megasquirt");
				delay(5000);
			}
		}
		disconnect();
	}

	private void disconnect()
	{
		// TODO Auto-generated method stub

	}

	private boolean connected()
	{
		return comm.isConnected();
	}

	private void sendMessage(String msg)
	{
		Intent broadcast = new Intent();
		broadcast.setAction(ApplicationSettings.GENERAL_MESSAGE);
		broadcast.putExtra(ApplicationSettings.MESSAGE, msg);
		context.sendBroadcast(broadcast);

	}

	private void delay(long pauseTime)
	{
		try
		{
			Thread.sleep(pauseTime);
		}
		catch (InterruptedException e)
		{
			e.printStackTrace();
		}

	}

	private void throttle()
	{
		long now = System.currentTimeMillis();
		long diff = now - lastTime;
		long pauseTime = (1000 / ApplicationSettings.INSTANCE.getHertz()) - diff;
		if (pauseTime > 0)
		{
			delay(pauseTime);
		}

	}

	private void broadcastNewData()
	{
		Intent broadcast = new Intent();
		broadcast.setAction(NEW_DATA);
		// broadcast.putExtra(LOCATION, location);
		context.sendBroadcast(broadcast);

	}

	private void getRuntimeVars()
	{
		comm.write(this.getOchCommand());
		comm.read(ochBuffer);
	}

	private void calculateValues()
	{
		calculate(ochBuffer);
	}

	public Megasquirt(Context c)
	{
		this.context = c;

	}

	public long number(String s)
	{

		// Prefixes: % = binary, $ = hexadecimal, ! = decimal.
		// Suffixes: Q = binary, H = hexadecimal, T = decimal.

		int base = 10;
		char ch = s.charAt(0);

		switch (ch)
		{
		case '%':
			base = 2;
			s = ' ' + s.substring(1);
			break;
		case '$':
			base = 16;
			s = ' ' + s.substring(1);
			break;
		case '!':
			base = 10;
			s = ' ' + s.substring(1);
			break;

		case '0':
		case '1':
		case '2':
		case '3':
		case '4':
		case '5':
		case '6':
		case '7':
		case '8':
		case '9':
		case 'A':
		case 'B':
		case 'C':
		case 'D':
		case 'E':
		case 'F':
		case 'a':
		case 'b':
		case 'c':
		case 'd':
		case 'e':
		case 'f':
			ch = s.charAt(s.length() - 1);
			switch (ch)
			{
			case 'Q':
			case 'q':
				base = 2;
				break;
			case 'H':
			case 'h':
				base = 16;
				break;
			case 'T':
			case 't':
				base = 10;
				break;

			case '0':
			case '1':
			case '2':
			case '3':
			case '4':
			case '5':
			case '6':
			case '7':
			case '8':
			case '9':
			case 'A':
			case 'B':
			case 'C':
			case 'D':
			case 'E':
			case 'F':
			case 'a':
			case 'b':
			case 'c':
			case 'd':
			case 'e':
			case 'f':
				// Everthing is ok so far.
				break;

			default:
				throw new NumberFormatException(s);

			}
			break;
		default:
			throw new NumberFormatException(s);

		}

		long n = Long.valueOf(s, base);
		return n;
	}

	public int numberW(String s)
	{
		long nn = number(s);
		if (nn > 65536 || nn < 0)
			throw new NumberFormatException(s);
		return (int) nn;

	}

	public int numberB(String s)
	{
		long nn = number(s);
		if (nn > 255 || nn < 0)
			throw new NumberFormatException(s);

		return (int) nn;
	}

	public void readTable(String fileName, List<Integer> values)
	{
		values.clear();
		Pattern p = Pattern.compile("\\s*[Dd][BbWw]\\s*(\\d*).*");

		fileName = "tables" + File.separator + fileName;
		AssetManager assetManager = context.getResources().getAssets();

		BufferedReader input = null;
		try
		{
			try
			{
				input = new BufferedReader(new InputStreamReader(assetManager.open(fileName)));
				String line;

				while ((line = input.readLine()) != null)
				{
					Matcher matcher = p.matcher(line);
					if (matcher.matches())
					{
						String num = matcher.group(1);

						if (num != null)
						{
							values.add(Integer.valueOf(num));
						}
					}
				}

			}
			finally
			{
				if (input != null)
					input.close();
			}

		}
		catch (IOException e)
		{
			// TODO Auto-generated catch block
			e.printStackTrace();

		}
	}

	protected int getWord(byte[] ochBuffer, int i)
	{

		return (ochBuffer[i] & 0xFF) * 256 + ochBuffer[i + 1] & 0xFF;
	}

	protected int getByte(byte[] ochBuffer, int i)
	{
		return (int) ochBuffer[i] & 0xFF;
	}

	protected long timeNow()
	{
		return System.currentTimeMillis();
	}

	protected double tempCvt(int t)
	{
		return (t - 32.0) * 5.0 / 9.0;
	}

	protected int table(int i1, String s2)
	{
		List<Integer> table = tables.get(s2);
		if (table == null)
		{
			table = new ArrayList<Integer>();
			readTable(s2, table);
			tables.put(s2, table);
		}
		return table.get(i1);

	}

	public boolean initialised()
	{
		comm = ApplicationSettings.INSTANCE.getComms();
		ochBuffer = new byte[this.getBlockSize()];
		boolean connected = comm.openConnection();

		if (connected)
		{
			String sig = comm.getSignature(this.getSigCommand());
			connected = getSignature().equals(sig);
		}

		return connected;

	}

	public float getValue(String channel)
	{
		float value = 0;
		Class<?> c = this.getClass();
		try
		{
			Field f = c.getField(channel);
			value = f.getFloat(this);
		}
		catch (Exception e)
		{
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
		return value;
	}

}
