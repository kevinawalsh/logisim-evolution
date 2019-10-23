package com.TChapman500.FloatingPoint;

import java.awt.Color;
import java.awt.Graphics;

import com.cburch.logisim.LogisimVersion;
import com.cburch.logisim.comp.Component;
import com.cburch.logisim.comp.ComponentDrawContext;
import com.cburch.logisim.comp.ComponentFactory;
import com.cburch.logisim.data.Attribute;
import com.cburch.logisim.data.AttributeSet;
import com.cburch.logisim.data.BitWidth;
import com.cburch.logisim.data.Bounds;
import com.cburch.logisim.data.Direction;
import com.cburch.logisim.data.Location;
import com.cburch.logisim.data.Value;
import com.cburch.logisim.util.GraphicsUtil;
import com.cburch.logisim.util.StringGetter;

import com.cburch.logisim.instance.InstanceFactory;
import com.cburch.logisim.instance.InstancePainter;
import com.cburch.logisim.instance.InstanceState;
import com.cburch.logisim.instance.Port;

public class FloatSign extends InstanceFactory
{
	static final int PORT_IN = 0;
	static final int PORT_MODE = 1;
	static final int PORT_OUT = 2;

	public FloatSign()
	{
		super("Single-Precision Sign Changer");

		setOffsetBounds(Bounds.create(-40, -20, 40, 40));
		setIconName("adder.gif");
		
		// Set port attributes
		Port[] ports = new Port[3];
		ports[PORT_IN] = new Port(-40, 0, Port.INPUT, 32);
		ports[PORT_MODE] = new Port(-20, -20, Port.INPUT, 1);
		ports[PORT_OUT] = new Port(0, 0, Port.OUTPUT, 32);
		setPorts(ports);
	}

	@Override
	public void paintInstance(InstancePainter painter)
	{
		// TODO Auto-generated method stub

		Graphics g = painter.getGraphics();
		painter.drawBounds();
		
		g.setColor(Color.GRAY);
		painter.drawPort(PORT_IN);
		painter.drawPort(PORT_MODE, "abs", Direction.NORTH);
		painter.drawPort(PORT_OUT);
		
		Location loc = painter.getLocation();
		int x = loc.getX();
		int y = loc.getY();
		GraphicsUtil.switchToWidth(g, 2);
		g.setColor(Color.BLACK);
		GraphicsUtil.switchToWidth(g, 1);
	}

	@Override
	public void propagate(InstanceState state)
	{
		
		Value a = state.getPortValue(PORT_IN);
		Value mode = state.getPortValue(PORT_MODE);
		Value sum = computeResult(a, mode);
		
		state.setPort(PORT_OUT, sum, 24);
	}

	static Value computeResult(Value a, Value mode)
	{

		Value result;
		
		if (a.isFullyDefined())
		{
			float aFloat = Float.intBitsToFloat(a.toIntValue());
			float sum;
			
			// Adds by default, but subtracts if sub value is fully-defined.
			if (mode.isFullyDefined() && mode == Value.TRUE) sum = Math.abs(aFloat);
			else sum = -aFloat;
			
			// Result of the operation
			result = Value.createKnown(BitWidth.create(32), Float.floatToIntBits(sum));
		}
		else result = Value.ERROR;
		
		return result;
	}


}
