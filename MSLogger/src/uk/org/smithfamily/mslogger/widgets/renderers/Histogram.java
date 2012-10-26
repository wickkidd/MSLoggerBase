package uk.org.smithfamily.mslogger.widgets.renderers;

import uk.org.smithfamily.mslogger.R;
import uk.org.smithfamily.mslogger.widgets.Indicator;
import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;

public class Histogram extends Renderer
{    
      
    public Histogram(Indicator parent, Context c)
    {
        super(parent, c);
    }

    private Paint           backgroundPaint;
    private Paint           linePaint;
    private Paint           valuePaint;
    
    private static final int NB_VALUES   = 50;
    private double[]        values      = new double[NB_VALUES];
    private int             indexValue  = 0;
    
    @Override
    protected void init(Context c)
    {
        initDrawingTools(c);
    }
    
    /**
     * 
     * @param context
     */
    private void initDrawingTools(Context context)
    {        
        int anti_alias_flag = Paint.ANTI_ALIAS_FLAG;
        if (parent.isInEditMode())
        {
            anti_alias_flag = 0;
        }
        
        backgroundPaint = new Paint();
        backgroundPaint.setColor(Color.WHITE);
        backgroundPaint.setTextSize(0.06f);
        backgroundPaint.setTextAlign(Paint.Align.LEFT);
        backgroundPaint.setStyle(Paint.Style.STROKE);
        backgroundPaint.setFlags(anti_alias_flag);
        backgroundPaint.setAntiAlias(true);
        
        valuePaint = new Paint();
        valuePaint.setColor(Color.DKGRAY);
        valuePaint.setTextSize(0.06f);
        valuePaint.setTextAlign(Paint.Align.RIGHT);
        valuePaint.setFlags(anti_alias_flag);
        valuePaint.setAntiAlias(true);
        
        linePaint = new Paint();
        linePaint.setColor(Color.BLUE);
        linePaint.setFlags(anti_alias_flag);
        linePaint.setAntiAlias(true);       
    }
    
    /**
     * @param value
     */
    public void setValue(double value)
    {
        // We haven't reach the limit of the array yet
        if (indexValue < NB_VALUES)
        {
           values[indexValue++] = (float) value; 
        }
        // Otherwise we shift all the values and replace the last one with the new value
        else
        {
            int i;
            for (i = 0; i < NB_VALUES - 1; i++)
            {
                values[i] = values[i + 1];
            }        
            
            values[i] = value;
        }
        
        parent.setValue((float) value);
    }
    
    
    /**
     * @param canvas
     */
    @Override
    public void paint(Canvas canvas)
    {
        int height = parent.getMeasuredHeight();

        int width = parent.getMeasuredWidth();

        float scale = (float)parent.getWidth();
        canvas.save(Canvas.MATRIX_SAVE_FLAG);
        canvas.scale(scale, scale);
        float dx = 0.0f;
        float dy = 0.0f;
        if (width > height)
        {
            dx = (width - height) / 2.0f;
        }
        if (height > width)
        {
            dy = (height - width) / 2.0f;
        }
        canvas.translate(dx, dy);
        
        drawBackground(canvas);
        
        if (!parent.isDisabled())
        {
            drawValue(canvas);
        }

        drawTitle(canvas);
        
        canvas.restore();     
    }
    
    public void drawBackground(Canvas canvas)
    {
        canvas.drawRect(0.05f, 0.05f, 0.94f, 0.83f, backgroundPaint);
    }
    
    public void drawValue(Canvas canvas)
    {
        valuePaint.setColor(getFgColour());

        float displayValue = (float) (Math.floor(parent.getValue() / Math.pow(10, -parent.getVd()) + 0.5) * Math.pow(10, -parent.getVd()));

        String text;

        if (parent.getVd() <= 0)
        {
            text = Integer.toString((int) displayValue);
        }
        else
        {
            text = Float.toString(displayValue);
        }

        canvas.drawText(text, 0.93f, 0.90f, valuePaint);
        
        // We need at least two pair of coords to draw a line
        if (indexValue > 1)
        {            
            final float x = 0.035f;
            final float y = 0.06f;
            final float height = 0.76f;
            final float pixelsBetweenValue = 0.018f;
            
            for (int i = 1; i < indexValue; i++)
            {
                double oldValue = values[i - 1];
                double currentValue = values[i];
              
                double currentPercent = (1 - (currentValue - parent.getMin()) / (parent.getMax() - parent.getMin()));
                if (currentPercent < 0) currentPercent = 0;
                if (currentPercent > 100) currentPercent = 100;
                
                double currentY = y + height * currentPercent;              
              
                double oldPercent = (1 - (oldValue - parent.getMin()) / (parent.getMax() - parent.getMin()));
                if (oldPercent < 0) oldPercent = 0;
                if (oldPercent > 100) oldPercent = 100;
                
                double oldX = x + (pixelsBetweenValue * i);
                double oldY = y + height * oldPercent;     
                
                // Draw one value
                canvas.drawLine((float) oldX, (float) oldY, (float) oldX + pixelsBetweenValue, (float) currentY, linePaint);
            }
        }
    }
    
    public void drawTitle(Canvas canvas)
    {
        backgroundPaint.setColor(getFgColour());
        
        String text = parent.getTitle();
        if (!parent.getUnits().equals(""))
        {
            text += " (" + parent.getUnits() + ")";
        }
        
        canvas.drawText(text, 0.05f, 0.90f, backgroundPaint);
    }
    
    @Override
    public String getType()
    {
        return parent.getContext().getString(R.string.histogram);
    }   
}