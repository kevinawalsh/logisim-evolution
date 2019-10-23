package com.TChapman500.FloatingPoint;

import java.awt.Color;
import java.awt.Graphics;

import com.cburch.logisim.data.Attribute;
import com.cburch.logisim.data.BitWidth;
import com.cburch.logisim.data.Bounds;
import com.cburch.logisim.data.Direction;
import com.cburch.logisim.data.Location;
import com.cburch.logisim.data.Value;
import com.cburch.logisim.instance.InstanceFactory;
import com.cburch.logisim.instance.InstancePainter;
import com.cburch.logisim.instance.InstanceState;
import com.cburch.logisim.instance.Port;
import com.cburch.logisim.instance.StdAttr;
import com.cburch.logisim.util.GraphicsUtil;

public class FloatAdd extends InstanceFactory
{
	static final int PORT_A_IN = 0;
	static final int PORT_B_IN = 1;
	static final int PORT_OUT = 2;
	static final int PORT_SUBTRACT = 3;
	
	static Value computeSum(Value a, Value b, Value sub)
	{
		Value result;
		
		if (a.isFullyDefined() && b.isFullyDefined())
		{
			float aFloat = Float.intBitsToFloat(a.toIntValue());
			float bFloat = Float.intBitsToFloat(b.toIntValue());
			float sum;
			
			// Adds by default, but subtracts if sub value is fully-defined.
			if (sub.isFullyDefined() && sub == Value.TRUE) sum = aFloat - bFloat;
			else sum = aFloat + bFloat;
			
			// Result of the operation
			result = Value.createKnown(BitWidth.create(32), Float.floatToIntBits(sum));
		}
		else result = Value.ERROR;
		
		return result;
	}
	
	public FloatAdd()
	{
		// Set component name
		super("Single-Precision Adder");
		
		// Set component appearance
		setOffsetBounds(Bounds.create(-40, -20, 40, 40));
		setIconName("adder.gif");
		
		// Set port attributes
		Port[] ports = new Port[4];
		ports[PORT_A_IN] = new Port(-40, -10, Port.INPUT, 32);
		ports[PORT_B_IN] = new Port(-40, 10, Port.INPUT, 32);
		ports[PORT_OUT] = new Port(0, 0, Port.OUTPUT, 32);
		ports[PORT_SUBTRACT] = new Port(-20, -20, Port.INPUT, 1);
		setPorts(ports);
	}

	@Override
	public void paintInstance(InstancePainter painter)
	{
		Graphics g = painter.getGraphics();
		painter.drawBounds();
		
		g.setColor(Color.GRAY);
		painter.drawPort(PORT_A_IN);
		painter.drawPort(PORT_B_IN);
		painter.drawPort(PORT_OUT);
		painter.drawPort(PORT_SUBTRACT, "sub", Direction.NORTH);
		
		Location loc = painter.getLocation();
		int x = loc.getX();
		int y = loc.getY();
		GraphicsUtil.switchToWidth(g, 2);
		g.setColor(Color.BLACK);
		g.drawLine(x - 15, y, x - 5, y);
		g.drawLine(x - 10, y - 5, x - 10, y + 5);
		GraphicsUtil.switchToWidth(g, 1);
	}

	@Override
	public void propagate(InstanceState state)
	{
		// TODO Auto-generated method stub
		Value a = state.getPortValue(PORT_A_IN);
		Value b = state.getPortValue(PORT_B_IN);
		Value sub = state.getPortValue(PORT_SUBTRACT);
		Value sum = computeSum(a, b, sub);
		
		state.setPort(PORT_OUT, sum, 24);
	}

}
