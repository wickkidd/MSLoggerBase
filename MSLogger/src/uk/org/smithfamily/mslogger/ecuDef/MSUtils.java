package uk.org.smithfamily.mslogger.ecuDef;

import uk.org.smithfamily.mslogger.ApplicationSettings;
import uk.org.smithfamily.mslogger.GPSLocationManager;

/**
 * Megasquirt utilities class heavily used in the ECU definition Java class
 */
public enum MSUtils implements MSUtilsInterface
{
    INSTANCE;
     /**
     * Get the long for the specified index in the buffer
     * 
     * @param ochBuffer
     * @param i
     * @return
     */
    public int getLong(byte[] ochBuffer, int i)
    {
        return getWord(ochBuffer, i) * 65536 + getWord(ochBuffer, i + 2);
    }

    /**
     * Get the word for the specified index in the buffer
     * 
     * @param ochBuffer
     * @param i
     * @return
     */
    public int getWord(byte[] ochBuffer, int i)
    {
        return (ochBuffer[i] << 8) | getByte(ochBuffer,i + 1);
    }

    /**
     * 
     * Get the byte for the specified index in the buffer
     * 
     * @param ochBuffer
     * @param i
     * @return
     */
    public int getByte(byte[] ochBuffer, int i)
    {
        return (int) ochBuffer[i] & 0xFF;
    }

    /**
     * Get the signed long for the specified index in the buffer
     * 
     * @param ochBuffer
     * @param i
     * @return
     */
    public int getSignedLong(byte[] ochBuffer, int i)
    {
        int x = getLong(ochBuffer, i);
        if (x > 2 << 32 - 1)
        {
            x = 2 << 32 - x;
        }
        return x;
    }

    /**
     * Get the signed byte for the specified index in the buffer
     * 
     * @param ochBuffer
     * @param i
     * @return
     */
    public int getSignedByte(byte[] ochBuffer, int i)
    {
        int x = getByte(ochBuffer, i);
        if (x > 127)
        {
            x = 256 - x;
        }
        return x;
    }

    /**
     * Get the signed word for the specified index in the buffer
     * 
     * @param ochBuffer
     * @param i
     * @return
     */
    public int getSignedWord(byte[] ochBuffer, int i)
    {
        int x = getWord(ochBuffer, i);
        if (x > 32767)
        {
            x = 32768 - x;
        }
        return x;
    }
    
    /**
     * Get bits at the specified index for the page buffer 
     * 
     * @param pageBuffer    Page buffer of data
     * @param i             Index where the value is
     * @param _bitLo
     * @param _bitHi
     * @param bitOffset
     * @return
     */
    public int getBits(byte[] pageBuffer, int i, int _bitLo, int _bitHi,int bitOffset)
    {
        int val = 0;
        byte b = pageBuffer[i];

        long mask = ((1 << (_bitHi - _bitLo + 1)) - 1) << _bitLo;
        val = (int) ((b & mask) >> _bitLo) + bitOffset;

        return val;
    }
    
    /**
     * @return The latitude of the last known location
     */
    public double getLatitude()
    {
        return GPSLocationManager.INSTANCE.getLastKnownLocation().getLatitude();
    }
    
    /**
     * @return The longitude of the last known location
     */
    public double getLongitude()
    {
        return GPSLocationManager.INSTANCE.getLastKnownLocation().getLongitude();
    }
    
    /**
     * @return The speed of the last known location
     */
    public double getSpeed()
    {
        return GPSLocationManager.INSTANCE.getLastKnownLocation().getSpeed();
    }
    
    /**
     * @return The bearing of the last known location
     */
    public double getBearing()
    {
        return GPSLocationManager.INSTANCE.getLastKnownLocation().getBearing();
    }
    
    /**
     * @return The accuracy of the last known location
     */
    public double getAccuracy()
    {
        return GPSLocationManager.INSTANCE.getLastKnownLocation().getAccuracy();
    }
    
    /**
     * @return The time of the last known location
     */
    public long getTime()
    {
        long time = GPSLocationManager.INSTANCE.getLastKnownLocation().getTime();
        return time;
    }

    /**
     * @return The location header used in datalog
     */
    public String getLocationLogHeader()
    {
    	String s = "Latitude\tLongitude\tSpeed (m/s)\tHeading\tAccuracy\tGPSTime\tGPS_Update";
    	if (ApplicationSettings.INSTANCE.isDeviceTimeEnabled()) 
    	{
    		s +="\tDeviceTime";
    	}
        return s;
    }
    
    /**
     * @return A datalog row of location information
     */
    public String getLocationLogRow()
    {
    	String s = getLatitude() + "\t" + 
    			   getLongitude() + "\t" +
    			   getSpeed() + "\t" +
    			   getBearing() + "\t" +
    			   getAccuracy() + "\t" +
    			   getTime() +"\t"+
    			   getGPSUpdate();

    	if (ApplicationSettings.INSTANCE.isDeviceTimeEnabled()) 
    	{
            long time = System.currentTimeMillis();
    		s +="\t"+time;
    	}
        return s;
    }

    /**
     * @return
     */
    private int getGPSUpdate()
    {
        return GPSLocationManager.INSTANCE.getFreshness();
    }
    
    /**
     * Take an MS command like this one 119,0,4,0,20,0,2,0,250 (to set cranking RPM to 250rpm on MS2Extra)
     * and convert it to byte array that can be used with Megasquirt.writeCommand()
     * 
     * @param command The command to convert to byte array
     * @return A byte array with every byte of the command
     */
    public byte[] commandStringtoByteArray(String command)
    {
        String[] split = command.split(",");
        
        byte[] bytes = new byte[split.length];
        
        for (int i = 0; i < split.length; i++)
        {
            bytes[i] = Byte.valueOf(split[i]);
        }
        
        return bytes;
    }
    
    /**
     * Round a double number to a specific number of decimals
     * 
     * @param number The number to round
     * @param decimals The number of decimals to keep
     * 
     * @return The rounded number
     */
    public double roundDouble(double number, int decimals)
    {
        double p = (double) Math.pow(10, decimals);
        number = number * p;
        double tmp = Math.round(number);
        return tmp / p;
    }
}
