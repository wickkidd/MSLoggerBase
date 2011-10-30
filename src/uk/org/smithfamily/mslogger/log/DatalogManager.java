package uk.org.smithfamily.mslogger.log;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.PrintWriter;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;

import uk.org.smithfamily.mslogger.ApplicationSettings;
import uk.org.smithfamily.mslogger.ecuDef.Megasquirt;
import android.text.format.DateFormat;

public enum DatalogManager
{
	INSTANCE;

	PrintWriter		writer			= null;
	private String	fileName		= null;
	private String	absolutePath	= null;
	private int		markCounter		= 0;

	public synchronized String getAbsolutePath()
	{
		return absolutePath;
	}

	public synchronized String getFilename()
	{
		if (fileName == null)
		{
			fileName = DateFormat.format("yyyyMMddkkmmss", new Date()).toString() + ".msl";
		}
		return fileName;
	}

	public void write(String logRow)
	{
		if (writer == null)
		{

			File logFile = new File(ApplicationSettings.INSTANCE.getDataDir(), getFilename());
			absolutePath = logFile.getAbsolutePath();
			try
			{
				writer = new PrintWriter(logFile);
				Megasquirt ecuDefinition = ApplicationSettings.INSTANCE.getEcuDefinition();
				String signature = ecuDefinition.getTrueSignature();
				writer.println("\"" + signature + "\"");
				writer.println(ecuDefinition.getLogHeader());
				markCounter = 1;
			}
			catch (FileNotFoundException e)
			{
				// TODO Auto-generated catch block
				e.printStackTrace();
			}
		}
		writer.println(logRow);
	}

	public void stopLog()
	{
		if (writer != null)
		{
			writer.flush();
			writer.close();
		}
		writer = null;
	}

	public void close()
	{
		stopLog();
	}

	public void mark(String msg)
	{
		if (writer != null)
		{
			write(String.format("MARK  %03d - %s - %tc", markCounter++, msg, System.currentTimeMillis()));
			if (markCounter > 999)
			{
				markCounter = 1;
			}
		}

	}

	public void mark()
	{
		mark("Manual");
	}
}
