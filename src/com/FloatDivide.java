package com.TChapman500.float32;

import java.awt.Color;
import java.awt.Graphics;

import com.cburch.logisim.data.BitWidth;
import com.cburch.logisim.data.Bounds;
import com.cburch.logisim.data.Direction;
import com.cburch.logisim.data.Location;
import com.cburch.logisim.data.Value;
import com.cburch.logisim.instance.InstanceFactory;
import com.cburch.logisim.instance.InstancePainter;
import com.cburch.logisim.instance.InstanceState;
import com.cburch.logisim.instance.Port;
import com.cburch.logisim.util.GraphicsUtil;

public class FloatDivide extends InstanceFactory {

	static final int PORT_A_IN = 0;
	static final int PORT_B_IN = 1;
	static final int PORT_OUT = 2;
	
	public FloatDivide() {
		super("Single-Precision Divider");
		
		// Set component appearance
		setOffsetBounds(Bounds.create(-40, -20, 40, 40));
		setIconName("multiplier.gif");
		
		// Set port attributes
		Port[] ports = new Port[3];
		ports[PORT_A_IN] = new Port(-40, -10, Port.INPUT, 32);
		ports[PORT_B_IN] = new Port(-40, 10, Port.INPUT, 32);
		ports[PORT_OUT] = new Port(0, 0, Port.OUTPUT, 32);
		setPorts(ports);
	}

	@Override
	public void paintInstance(InstancePainter painter) {
		// TODO Auto-generated method stub

		Graphics g = painter.getGraphics();
		painter.drawBounds();
		
		g.setColor(Color.GRAY);
		painter.drawPort(PORT_A_IN);
		painter.drawPort(PORT_B_IN);
		painter.drawPort(PORT_OUT);
		
		Location loc = painter.getLocation();
		int x = loc.getX();
		int y = loc.getY();
		GraphicsUtil.switchToWidth(g, 2);
		g.setColor(Color.BLACK);
		g.fillOval(x - 12, y - 7, 4, 4);
		g.drawLine(x - 15, y, x - 5, y);
		g.fillOval(x - 12, y + 3, 4, 4);
		GraphicsUtil.switchToWidth(g, 1);

	}

	@Override
	public void propagate(InstanceState state) {
		Value a = state.getPortValue(PORT_A_IN);
		Value b = state.getPortValue(PORT_B_IN);
		Value product = computeQuotient(a, b);
		
		state.setPort(PORT_OUT, product, 16);

	}

	static Value computeQuotient(Value a, Value b)
	{
		Value result;
		if (a.isFullyDefined() && b.isFullyDefined())
		{
			float aFloat = Float.intBitsToFloat(a.toIntValue());
			float bFloat = Float.intBitsToFloat(b.toIntValue());
			float product = aFloat / bFloat;
			result = Value.createKnown(BitWidth.create(32), Float.floatToIntBits(product));
		}
		else result = Value.ERROR;
		
		return result;
	}

}
