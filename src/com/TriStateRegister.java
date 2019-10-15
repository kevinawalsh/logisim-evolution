package com.TChapman500.components;


import static com.cburch.logisim.std.Strings.S;

import java.awt.Color;
import java.awt.Graphics;
import java.awt.event.KeyEvent;

import com.cburch.logisim.std.memory.*;

import com.bfh.logisim.hdlgenerator.HDLSupport;
import com.cburch.logisim.circuit.appear.DynamicElement;
import com.cburch.logisim.circuit.appear.DynamicElement.Path;
import com.cburch.logisim.circuit.appear.DynamicElementProvider;
import com.cburch.logisim.data.Attribute;
import com.cburch.logisim.data.AttributeSet;
import com.cburch.logisim.data.Attributes;
import com.cburch.logisim.data.BitWidth;
import com.cburch.logisim.data.Bounds;
import com.cburch.logisim.data.Direction;
import com.cburch.logisim.data.Location;
import com.cburch.logisim.data.Value;
import com.cburch.logisim.instance.Instance;
import com.cburch.logisim.instance.InstanceFactory;
import com.cburch.logisim.instance.InstancePainter;
import com.cburch.logisim.instance.InstanceState;
import com.cburch.logisim.instance.Port;
import com.cburch.logisim.instance.StdAttr;
import com.cburch.logisim.tools.key.BitWidthConfigurator;
import com.cburch.logisim.util.GraphicsUtil;
import com.cburch.logisim.util.StringUtil;
import com.cburch.logisim.tools.key.JoinedConfigurator;
import com.cburch.logisim.tools.key.DirectionConfigurator;

public class TriStateRegister extends InstanceFactory implements DynamicElementProvider
{
	static final int PORT_DATA_INOUT = 0;
	static final int PORT_CLOCK = 1;
	static final int PORT_WRITE_ENABLE = 2;
	static final int PORT_READ_ENABLE = 3;
	static final int PORT_RESET = 4;
	
	public static final Attribute<Boolean> ATTR_SHOW_IN_TAB = Attributes.forBoolean("showInTab", S.getter("registerShowInTab"));

	public TriStateRegister()
	{
		super("Tri-State Register");
		
		setAttributes(
			new Attribute[]
			{
				StdAttr.WIDTH,
				StdAttr.TRIGGER,
				StdAttr.LABEL,
				StdAttr.LABEL_LOC,
				StdAttr.LABEL_FONT,
				StdAttr.LABEL_COLOR,
				ATTR_SHOW_IN_TAB
			},
			new Object[]
			{
				BitWidth.create(8),
				StdAttr.TRIG_RISING,
				"",
				Direction.NORTH,
				StdAttr.DEFAULT_LABEL_FONT,
				Color.BLACK,
				true
			}
		);
		
		setKeyConfigurator(JoinedConfigurator.create(
			new BitWidthConfigurator(StdAttr.WIDTH),
			new DirectionConfigurator(StdAttr.LABEL_LOC, KeyEvent.ALT_DOWN_MASK)
		));
		

	    setIconName("register.gif");
	    setInstancePoker(RegisterPoker.class);
	    setInstanceLogger(RegisterLogger.class);
	}

	@Override
	public void paintInstance(InstancePainter painter)
	{
		
		
	}

	@Override
	public void propagate(InstanceState state)
	{
		
	}

	@Override
	public DynamicElement createDynamicElement(int x, int y, Path path)
	{
		
		return null;
	}

}
