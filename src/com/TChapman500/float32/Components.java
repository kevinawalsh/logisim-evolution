package com.TChapman500.float32;

import java.util.Arrays;
import java.util.List;

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
				new AddTool(new FloatAdd()),		// Adds or Subtracts two numbers
				new AddTool(new FloatMultiply()),	// Multiplies two numbers
				new AddTool(new FloatDivide()),		// Divides two numbers, produces quotient and remainder
				//new AddTool(new FloatTrig()),		// Configurable to do Sin/Cos/Tan/ASin/ACos/ATan operations
				//new AddTool(new FloatSqrt()),		// Square root
				//new AddTool(new FloatCompare()),	// Compares two floating point numbers
				new AddTool(new FloatSign()),		// Negates or absolutes a number
				//new AddTool(new FloatRound()),	// Rounds number to selected integer
				
				// Conversion Operations
				//new AddTool(new FloatToInt()),		// Converts float to signed or unsigned integer
				//new AddTool(new IntToFloat()),		// Converts signed or unsigned integer to float
				//new AddTool(new FloatToDouble()),	// Converts float to double
				
				// Memory and Output
				//new AddTool(new FloatRegister()),	// A single floating point register
				//new AddTool(new FloatRegisterBank()),	// A bank of floating point registers
				//new AddTool(new FloatProbe()),		// A floating point probe
			}
		);
	}
	
	public String getDisplayName()
	{
		return "Floating Point";
	}
	    
	/** Returns a list of all the tools available in this library. */
	@Override
	public List<AddTool> getTools()
	{
		return Tools;
	}

}
