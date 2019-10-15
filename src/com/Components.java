package com.TChapman500.components;

import java.util.Arrays;
import java.util.List;

//import com.TChapman500.float32.FloatAdd;
//import com.TChapman500.float32.FloatMultiply;
import com.cburch.logisim.tools.AddTool;
import com.cburch.logisim.tools.Library;
import com.cburch.logisim.tools.Tool;

public class Components extends Library
{
	List<AddTool> Tools;
	
	public Components()
	{
		Tools = Arrays.asList(new AddTool[]
			{
				// Arithmatic Operations
				//new AddTool(new Increment()),
				//new AddTool(new Decrement()),
				//new AddTool(new Absolute()),
				//new AddTool(new Test()),
				
				// Memory and Output
				new AddTool(new TriStateRegister()),	// A single floating point register
				//new AddTool(new RegisterBank()),	// A bank of floating point registers
			}
		);
	}
	
	public String getDisplayName()
	{
		return "TChapman500's Components";
	}
	    
	/** Returns a list of all the tools available in this library. */
	@Override
	public List<AddTool> getTools()
	{
		return Tools;
	}

}
