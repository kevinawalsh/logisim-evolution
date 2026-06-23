/**
 * This file is part of Logisim-evolution.
 *
 * Logisim-evolution is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by the
 * Free Software Foundation, either version 3 of the License, or (at your
 * option) any later version.
 *
 * Logisim-evolution is distributed in the hope that it will be useful, but
 * WITHOUT ANY WARRANTY; without even the implied warranty of MERCHANTABILITY
 * or FITNESS FOR A PARTICULAR PURPOSE.  See the GNU General Public License
 * for more details.
 *
 * You should have received a copy of the GNU General Public License along
 * with Logisim-evolution.  If not, see <http://www.gnu.org/licenses/>.
 *
 * Original code by Carl Burch (http://www.cburch.com), 2011.
 * Subsequent modifications by:
 *   + Haute École Spécialisée Bernoise
 *     http://www.bfh.ch
 *   + Haute École du paysage, d'ingénierie et d'architecture de Genève
 *     http://hepia.hesge.ch/
 *   + Haute École d'Ingénierie et de Gestion du Canton de Vaud
 *     http://www.heig-vd.ch/
 *   + REDS Institute - HEIG-VD, Yverdon-les-Bains, Switzerland
 *     http://reds.heig-vd.ch
 * This version of the project is currently maintained by:
 *   + Kevin Walsh (kwalsh@holycross.edu, http://mathcs.holycross.edu/~kwalsh)
 */
package com.cburch.logisim.std.io;

import java.io.File;

import com.bfh.logisim.hdlgenerator.HDLGenerator;
import com.bfh.logisim.hdlgenerator.HiddenPort;
import com.bfh.logisim.netlist.NetlistComponent;
import com.cburch.logisim.hdl.Hdl;

public class KeyboardHDLGenerator extends HDLGenerator {

  public KeyboardHDLGenerator(ComponentContext ctx) {
    super(ctx, "io", "Keyboard", "i_Kbd");
    int w = _attrs.getValue(Keyboard.ATTR_WIDTH);
    int d = _attrs.getValue(Keyboard.ATTR_BUFFER);
    parameters.add("AsciiWidth", w);
    parameters.add("FIFO_DEPTH", d);
    // See HDL code below for explanation of these parameters.
    int freq = (int)Math.round(ctx.fpgaFreq);
    int counter_size = (int)Math.ceil(Math.log(5.0*freq/1e6) / Math.log(2));
    parameters.add("clk_freq", freq);
    parameters.add("counter_size", counter_size);
    vhdlLibraries.add(IEEE_UNSIGNED); // for addition on std_logic_vector

    // Note: We expect the slow clock to actually be FPGAClock (or its inverse).
    // This is the case whenever the clock input is connected to a proper clock
    // bus. Other cases, e.g. using a gated clock, are not supported here:
    // proper HDL would be tricky, since we need data to cross between the raw
    // clock domain and slow clock domain. There is a warning in HDLGenerator
    // about this.

    clockPort = new ClockPortInfo("FPGAClock", "SlowClockEnable", Keyboard.CK);
    // inPorts.add("FPGAClock", 1, -1, null); // see getPortMappings, below
    inPorts.add("ReadEnable", 1, Keyboard.RE, true); // default enable = true
    // inPorts.add("Clear", 1, Keyboard.CLR, false); // todo
    outPorts.add("Data", "AsciiWidth", Keyboard.OUT, null);
    outPorts.add("Available", 1, Keyboard.AVL, null);

    String[] labels = new String[] { "ps2kb_clk", "ps2kb_dat", "ps2ms_clk", "ps2ms_dat" };
    // clk is output, dat is bidir, but hiddenport doesn't have a way to be that
    // specific, so we use bidir for both.
    hiddenPort = HiddenPort.makeInOutport(labels, HiddenPort.BiRibbon, HiddenPort.BiPin);
  }

  @Override
	protected Hdl getArchitecture() {
    Hdl out = new Hdl(_lang, _err);
    generateFileHeader(out);

    if (out.isVhdl) {
      out.add("");
      out.add("-------------------------------------------------------------------------------");
      out.add("-- Title      : PS2 scancode protocol, ascii conversion, and character buffer");
      out.add("-------------------------------------------------------------------------------");
      out.add("-- File       : PS2_ASCII_BUFFER.vhd");
      out.add("-- Author     : <kwalsh@holycross.edu>");
      out.add("-- Created    : 2016-08-19");
      out.add("-------------------------------------------------------------------------------");
      out.add("-- Description: Accepts raw PS2 keyboard inputs and presents a character buffer");
      out.add("--    interface. It needs two separate clock sources: a 50MHz \"fast\" clock,");
      out.add("--    which is used for the scancode handling and low-level timing; and a");
      out.add("--    second \"slow\" clock used to control the rate at which characters are");
      out.add("--    removed from the character buffer. The slow clock can be the same as the");
      out.add("--    fast clock or any fixed multiple slower. Actually, the slow clock does not");
      out.add("--    really need to be a fixed multiple at all, but it must be synchronized");
      out.add("--    with the fast clock (i.e. all rising edges of the slow clock must");
      out.add("--    coincide with rising edges of the fast clock). This module is a");
      out.add("--    combination of what was previously four separate modules:");
      out.add("--          1. Debouncing PS2 inputs.");
      out.add("--          2. Checking PS2 parity and detecting PS2 scancodes.");
      out.add("--          3. Converting PS2 scancodes (set 2) to ascii.");
      out.add("--          4. Buffering ascii characters in a FIFO queue.");
      out.add("--");
      out.add("-------------------------------------------------------------------------------");
      out.add("-- Copyright (c) 2016 ");
      out.add("-------------------------------------------------------------------------------");
      out.add("-- Date        Version  Author  Description");
      out.add("-- 201?-??-??  1.0      kwalsh  Created");
      out.add("-- 2016-02-05  1.1      kwalsh  Adapted for Logisim Evolution");
      out.add("-- 2016-08-19  1.2      kwalsh  Merged with ps2_keyboard and STD_FIFO");
      out.add("-------------------------------------------------------------------------------");
      out.add("--");
      out.add("-- Some code adapated from ps2_keyboard.vhd, with following disclaimer:");
      out.add("--------------------------------------------------------------------------------");
      out.add("--");
      out.add("--   FileName:         ps2_keyboard.vhd");
      out.add("--   Dependencies:     debounce.vhd");
      out.add("--   Design Software:  Quartus II 32-bit Version 12.1 Build 177 SJ Full Version");
      out.add("--");
      out.add("--   HDL CODE IS PROVIDED \"AS IS.\"  DIGI-KEY EXPRESSLY DISCLAIMS ANY");
      out.add("--   WARRANTY OF ANY KIND, WHETHER EXPRESS OR IMPLIED, INCLUDING BUT NOT");
      out.add("--   LIMITED TO, THE IMPLIED WARRANTIES OF MERCHANTABILITY, FITNESS FOR A");
      out.add("--   PARTICULAR PURPOSE, OR NON-INFRINGEMENT. IN NO EVENT SHALL DIGI-KEY");
      out.add("--   BE LIABLE FOR ANY INCIDENTAL, SPECIAL, INDIRECT OR CONSEQUENTIAL");
      out.add("--   DAMAGES, LOST PROFITS OR LOST DATA, HARM TO YOUR EQUIPMENT, COST OF");
      out.add("--   PROCUREMENT OF SUBSTITUTE GOODS, TECHNOLOGY OR SERVICES, ANY CLAIMS");
      out.add("--   BY THIRD PARTIES (INCLUDING BUT NOT LIMITED TO ANY DEFENSE THEREOF),");
      out.add("--   ANY CLAIMS FOR INDEMNITY OR CONTRIBUTION, OR OTHER SIMILAR COSTS.");
      out.add("--");
      out.add("--   Version History");
      out.add("--   Version 1.0 11/25/2013 Scott Larson");
      out.add("--     Initial Public Release");
      out.add("--");
      out.add("--------------------------------------------------------------------------------");
      out.add("");
      out.add("architecture logisim_generated of Keyboard is");
      out.add("");
      out.add("  -----------------------------------------------------------------------------");
      out.add("  -- ps2_keyboard scancode protocol");
      out.add("  -----------------------------------------------------------------------------");
      out.add("");
      out.add("  -- The ps2 clock is 10 - 16.7 kHz, or 60-100us per cycle (30-50us per half cycle),");
      out.add("  -- so if the ps clock has remained high for approx 55us, then it has gone idle.");
      out.add("  -- We also debounce signals for about 5us.");
      out.add("  -- Note: The code below uses an idle counter limit of 50mHz/18000Hz = 2750, which");
      out.add("  -- works out to 2750/50mHz = 50mHz/18000Hz/50mHz = 1/18000Hz = 55us.");
      out.add("");
      out.add("  -- constant clk_freq     : integer := 50_000_000;        -- frequency of clk (e.g. 50 MHz)");
      out.add("  -- constant counter_size : integer := 8;                 -- width (minus 1) for ps2 debounce counters, sized so 2**size/freq = 5us");
      out.add("  constant idle_max     : integer := clk_freq/18_000;   -- max value for idle counter, sized so max/freq >= 55us");
      out.add("");
      out.add("  signal ps2bus         : std_logic_vector(3 downto 0);  -- all four ps2 connector signals");
      out.add("  signal sync_ffs       : std_logic_vector(3 downto 0);  -- synchronizer flip-flops for PS/2 signals");
      out.add("");
      out.add("  signal d_ps2clk_ffs   : std_logic_vector(1 downto 0);  -- ps2clk debounce input flip flops");
      out.add("  signal d_ps2clk_set   : std_logic;                     -- ps2clk debounce sync reset to zero");
      out.add("  signal d_ps2clk_out   : std_logic_vector(counter_size downto 0) := (others => '0'); -- ps2clk debounce counter output");
      out.add("  signal ps2clk         : std_logic;                     -- debounced clock signal from PS/2 keyboard");
      out.add("");
      out.add("  signal d_ps2data_ffs  : std_logic_vector(1 downto 0);  -- ps2data debounce input flip flops");
      out.add("  signal d_ps2data_set  : std_logic;                     -- ps2data debounce sync reset to zero");
      out.add("  signal d_ps2data_out  : std_logic_vector(counter_size downto 0) := (others => '0'); -- ps2data debounce counter output");
      out.add("  signal ps2data        : std_logic;                     -- debounced data signal from PS/2 keyboard");
      out.add("  ");
      out.add("  signal ps2_prev_new   : std_logic;                     -- PS/2 keyboard clock edge detection");
      out.add("  signal ps2_curr_new   : std_logic;                     -- PS/2 keyboard clock edge detection");
      out.add("");
      out.add("  signal ps2_word       : std_logic_vector(10 downto 0); -- stores the ps2 data word");
      out.add("  signal ps2_error      : std_logic;                     -- validate parity, start, and stop bits");
      out.add("  signal count_idle     : integer range 0 to idle_max;   -- counter to determine whether PS/2 is idle");
      out.add("");
      out.add("  signal ps2_recv       : std_logic;                     -- flag that new scancode is available in ps2_code");
      out.add("  signal ps2_code       : std_logic_vector(7 downto 0);  -- scancode received from PS/2");
      out.add("");
      out.add("  -----------------------------------------------------------------------------");
      out.add("  -- ps2_ascii scancode to ascii conversion");
      out.add("  -----------------------------------------------------------------------------");
      out.add("");
      out.add("  signal scc : integer range 0 to 7;         -- count of buffered scancodes");
      out.add("  signal sc0 : std_logic_vector(7 downto 0); -- max 8 buffered scancodes");
      out.add("  signal sc1 : std_logic_vector(7 downto 0);");
      out.add("  -- signal sc2 : std_logic_vector(7 downto 0);");
      out.add("  -- signal sc3 : std_logic_vector(7 downto 0);");
      out.add("  -- signal sc4 : std_logic_vector(7 downto 0);");
      out.add("  -- signal sc5 : std_logic_vector(7 downto 0);");
      out.add("  -- signal sc6 : std_logic_vector(7 downto 0);");
      out.add("  -- signal sc7 : std_logic_vector(7 downto 0);");
      out.add("");
      out.add("  signal caps : std_logic;");
      out.add("  signal shift : std_logic;");
      out.add("  signal ascii_w : std_logic_vector(7 downto 0);");
      out.add("  signal ready_w : std_logic;");
      out.add("  signal ctrl_w : std_logic;");
      out.add("  signal caps_w : std_logic;");
      out.add("  signal shift_w : std_logic;");
      out.add("  signal ps2_recv_prev : std_logic;");
      out.add("");
      out.add("  signal char_ascii : std_logic_vector(7 downto 0);");
      out.add("  signal char_ready : std_logic;");
      out.add("");
      out.add("  -----------------------------------------------------------------------------");
      out.add("  -- std_fifo character buffer");
      out.add("  -----------------------------------------------------------------------------");
      out.add("");
      out.add("  constant DATA_WIDTH : positive := 8;");
      out.add("  signal fifo_pop : std_logic;");
      out.add("");
      out.add("  type FIFO_Memory is array (0 to FIFO_DEPTH - 1) of std_logic_vector (DATA_WIDTH - 1 downto 0);");
      out.add("  signal Memory : FIFO_Memory;");
      out.add("  signal Head : natural range 0 to FIFO_DEPTH - 1 := 0;");
      out.add("  signal Tail : natural range 0 to FIFO_DEPTH - 1 := 0;");
      out.add("  signal Full : boolean := false;");
      out.add("");
      out.add("  signal clk : std_logic;");
      out.add("  signal ready : std_logic := '0';");
      out.add("  signal ascii : std_logic_vector(7 downto 0) := X\"00\";");
      out.add("");
      out.add("begin");
      out.add("");
      out.add("  clk <= FPGAClock;");
      out.add("  Data <= ascii(AsciiWidth-1 downto 0);");
      out.add("  Available <= ready;");
      // out.add("  HeadHack <= std_logic_vector(to_unsigned(Head, 4));");
      // out.add("  TailHack <= std_logic_vector(to_unsigned(Tail, 4));");
      // out.add("  FullHack <= '1' WHEN Full else '0';");
      out.add("");
      out.add("  -----------------------------------------------------------------------------");
      out.add("  -- ps2_keyboard scancode protocol");
      out.add("  -----------------------------------------------------------------------------");
      out.add("");
      out.add("  -- synchronize ps2 inputs");
      out.add("  process (clk)");
      out.add("  begin");
      out.add("    if (rising_edge(clk)) then  -- sample PS/2 bus signals");
      out.add("      sync_ffs(0) <= ps2kb_clk;");
      out.add("      sync_ffs(1) <= ps2kb_dat;");
      out.add("      sync_ffs(2) <= ps2ms_clk;");
      out.add("      sync_ffs(3) <= ps2ms_dat;");
      out.add("    end if;");
      out.add("  end process;");
      out.add("");
      out.add("  -- debounce ps2clk signal");
      out.add("  d_ps2clk_set <= d_ps2clk_ffs(0) xor d_ps2clk_ffs(1);  -- determine when to start/reset counter");
      out.add("  process (clk)");
      out.add("  begin");
      out.add("    if (rising_edge(clk)) then");
      out.add("      d_ps2clk_ffs(0) <= sync_ffs(0);");
      out.add("      d_ps2clk_ffs(1) <= d_ps2clk_ffs(0);");
      out.add("      if (d_ps2clk_set = '1') then  -- reset counter because input is changing");
      out.add("        d_ps2clk_out <= (others => '0');");
      out.add("      elsif (d_ps2clk_out(counter_size) = '0') then  -- stable input time is not yet met");
      out.add("        d_ps2clk_out <= d_ps2clk_out + 1;");
      out.add("      else  -- stable input time is met");
      out.add("        ps2clk <= d_ps2clk_ffs(1);");
      out.add("      end if;    ");
      out.add("    end if;");
      out.add("  end process;");
      out.add("");
      out.add("  -- debounce ps2data signal");
      out.add("  d_ps2data_set <= d_ps2data_ffs(0) xor d_ps2data_ffs(1);  -- determine when to start/reset counter");
      out.add("  process (clk)");
      out.add("  begin");
      out.add("    if (rising_edge(clk)) then");
      out.add("      d_ps2data_ffs(0) <= sync_ffs(1);");
      out.add("      d_ps2data_ffs(1) <= d_ps2data_ffs(0);");
      out.add("      if (d_ps2data_set = '1') then  -- reset counter because input is changing");
      out.add("        d_ps2data_out <= (others => '0');");
      out.add("      elsif (d_ps2data_out(counter_size) = '0') then  -- stable input time is not yet met");
      out.add("        d_ps2data_out <= d_ps2data_out + 1;");
      out.add("      else  -- stable input time is met");
      out.add("        ps2data <= d_ps2data_ffs(1);");
      out.add("      end if;    ");
      out.add("    end if;");
      out.add("  end process;");
      out.add("");
      out.add("  -- move PS2 data into shift register");
      out.add("  process (ps2clk)");
      out.add("  begin");
      out.add("    if (falling_edge(ps2clk)) then  -- falling edge of PS2 clock");
      out.add("      ps2_word <= ps2data & ps2_word(10 downto 1);  -- shift in PS2 data bit");
      out.add("    end if;");
      out.add("  end process;");
      out.add("    ");
      out.add("  -- verify that parity, start, and stop bits are all correct");
      out.add("  ps2_error <= not (not ps2_word(0) and ps2_word(10) and (ps2_word(9) xor ps2_word(8) xor");
      out.add("        ps2_word(7) xor ps2_word(6) xor ps2_word(5) xor ps2_word(4) xor ps2_word(3) xor ");
      out.add("        ps2_word(2) xor ps2_word(1)));  ");
      out.add("");
      out.add("  -- determine if PS2 port is idle (i.e. last transaction is finished) and output result");
      out.add("  process (clk)");
      out.add("  begin");
      out.add("    if (rising_edge(clk)) then");
      out.add("      if (ps2clk = '0') then                  -- low PS2 clock, PS/2 is active");
      out.add("        count_idle <= 0;                      -- reset idle counter");
      out.add("      elsif (count_idle /= idle_max) then     -- PS2 clock has been high less than a half clock period (<55us)");
      out.add("          count_idle <= count_idle + 1;       -- continue counting");
      out.add("      end if;");
      out.add("      ");
      out.add("      ps2_prev_new <= ps2_curr_new;");
      out.add("      if (count_idle = idle_max and ps2_error = '0') then     -- idle threshold reached and no errors detected");
      out.add("        ps2_curr_new <= '1';                                  -- set flag that new PS/2 code is available");
      out.add("        ps2_code <= ps2_word(8 downto 1);                     -- output new PS/2 code");
      out.add("      else                                                    -- PS/2 port active or error detected");
      out.add("        ps2_curr_new <= '0';                                  -- set flag that PS/2 transaction is in progress");
      out.add("      end if;");
      out.add("      ps2_recv <= (not ps2_prev_new) and ps2_curr_new;");
      out.add("    end if;");
      out.add("  end process;");
      out.add("");
      out.add("  -----------------------------------------------------------------------------");
      out.add("  -- ps2_ascii scancode to ascii conversion");
      out.add("  -----------------------------------------------------------------------------");
      out.add("");
      out.add("  process (clk) is");
      out.add("  begin");
      out.add("    if rising_edge(clk) then");
      out.add("      char_ascii <= ascii_w;");
      out.add("      char_ready <= ready_w;");
      out.add("      caps <= caps_w;");
      out.add("      shift <= shift_w;");
      out.add("");
      out.add("      ps2_recv_prev <= ps2_recv;");
      out.add("      if ps2_recv = '1' and ps2_recv_prev = '0' then");
      out.add("        -- sc7 <= sc6;");
      out.add("        -- sc6 <= sc5;");
      out.add("        -- sc5 <= sc4;");
      out.add("        -- sc4 <= sc3;");
      out.add("        -- sc3 <= sc2;");
      out.add("        -- sc2 <= sc1;");
      out.add("        sc1 <= sc0;");
      out.add("        sc0 <= ps2_code;");
      out.add("        if (ready_w = '1') or (ctrl_w = '1') then");
      out.add("          scc <= 1;");
      out.add("        else");
      out.add("          scc <= scc + 1;");
      out.add("        end if;");
      out.add("      elsif (ready_w = '1') or (ctrl_w = '1') then");
      out.add("        scc <= 0;");
      out.add("      end if;");
      out.add("    end if;");
      out.add("  end process;");
      out.add("");
      out.add("  process (caps, shift, scc, sc0, sc1) is");
      out.add("  begin");
      out.add("    caps_w <= caps;");
      out.add("    shift_w <= shift;");
      out.add("    if (scc = 1) and (caps = shift) then");
      out.add("            -- unshifted");
      out.add("      case sc0 is");
      out.add("        when X\"1C\" => ascii_w <= X\"61\"; ready_w <= '1'; ctrl_w <= '0';  -- 1C | F0,1C (ascii \"a\")");
      out.add("        when X\"32\" => ascii_w <= X\"62\"; ready_w <= '1'; ctrl_w <= '0';  -- 32 | F0,32 (ascii \"b\")");
      out.add("        when X\"21\" => ascii_w <= X\"63\"; ready_w <= '1'; ctrl_w <= '0';  -- 21 | F0,21 (ascii \"c\")");
      out.add("        when X\"23\" => ascii_w <= X\"64\"; ready_w <= '1'; ctrl_w <= '0';  -- 23 | F0,23 (ascii \"d\")");
      out.add("        when X\"24\" => ascii_w <= X\"65\"; ready_w <= '1'; ctrl_w <= '0';  -- 24 | F0,24 (ascii \"e\")");
      out.add("        when X\"2B\" => ascii_w <= X\"66\"; ready_w <= '1'; ctrl_w <= '0';  -- 2B | F0,2B (ascii \"f\")");
      out.add("        when X\"34\" => ascii_w <= X\"67\"; ready_w <= '1'; ctrl_w <= '0';  -- 34 | F0,34 (ascii \"g\")");
      out.add("        when X\"33\" => ascii_w <= X\"68\"; ready_w <= '1'; ctrl_w <= '0';  -- 33 | F0,33 (ascii \"h\")");
      out.add("        when X\"43\" => ascii_w <= X\"69\"; ready_w <= '1'; ctrl_w <= '0';  -- 43 | F0,43 (ascii \"i\")");
      out.add("        when X\"3B\" => ascii_w <= X\"6a\"; ready_w <= '1'; ctrl_w <= '0';  -- 3B | F0,3B (ascii \"j\")");
      out.add("        when X\"42\" => ascii_w <= X\"6b\"; ready_w <= '1'; ctrl_w <= '0';  -- 42 | F0,42 (ascii \"k\")");
      out.add("        when X\"4B\" => ascii_w <= X\"6c\"; ready_w <= '1'; ctrl_w <= '0';  -- 4B | F0,4B (ascii \"l\")");
      out.add("        when X\"3A\" => ascii_w <= X\"6d\"; ready_w <= '1'; ctrl_w <= '0';  -- 3A | F0,3A (ascii \"m\")");
      out.add("        when X\"31\" => ascii_w <= X\"6e\"; ready_w <= '1'; ctrl_w <= '0';  -- 31 | F0,31 (ascii \"n\")");
      out.add("        when X\"44\" => ascii_w <= X\"6f\"; ready_w <= '1'; ctrl_w <= '0';  -- 44 | F0,44 (ascii \"o\")");
      out.add("        when X\"4D\" => ascii_w <= X\"70\"; ready_w <= '1'; ctrl_w <= '0';  -- 4D | F0,4D (ascii \"p\")");
      out.add("        when X\"15\" => ascii_w <= X\"71\"; ready_w <= '1'; ctrl_w <= '0';  -- 15 | F0,15 (ascii \"q\")");
      out.add("        when X\"2D\" => ascii_w <= X\"72\"; ready_w <= '1'; ctrl_w <= '0';  -- 2D | F0,2D (ascii \"r\")");
      out.add("        when X\"1B\" => ascii_w <= X\"73\"; ready_w <= '1'; ctrl_w <= '0';  -- 1B | F0,1B (ascii \"s\")");
      out.add("        when X\"2C\" => ascii_w <= X\"74\"; ready_w <= '1'; ctrl_w <= '0';  -- 2C | F0,2C (ascii \"t\")");
      out.add("        when X\"3C\" => ascii_w <= X\"75\"; ready_w <= '1'; ctrl_w <= '0';  -- 3C | F0,3C (ascii \"u\")");
      out.add("        when X\"2A\" => ascii_w <= X\"76\"; ready_w <= '1'; ctrl_w <= '0';  -- 2A | F0,2A (ascii \"v\")");
      out.add("        when X\"1D\" => ascii_w <= X\"77\"; ready_w <= '1'; ctrl_w <= '0';  -- 1D | F0,1D (ascii \"w\")");
      out.add("        when X\"22\" => ascii_w <= X\"78\"; ready_w <= '1'; ctrl_w <= '0';  -- 22 | F0,22 (ascii \"x\")");
      out.add("        when X\"35\" => ascii_w <= X\"79\"; ready_w <= '1'; ctrl_w <= '0';  -- 35 | F0,35 (ascii \"y\")");
      out.add("        when X\"1A\" => ascii_w <= X\"7a\"; ready_w <= '1'; ctrl_w <= '0';  -- 1A | F0,1A (ascii \"z\")");
      out.add("");
      out.add("        when X\"45\" => ascii_w <= X\"30\"; ready_w <= '1'; ctrl_w <= '0';  -- 45 | F0,45 (ascii \"0\")");
      out.add("        when X\"16\" => ascii_w <= X\"31\"; ready_w <= '1'; ctrl_w <= '0';  -- 16 | F0,16 (ascii \"1\")");
      out.add("        when X\"1E\" => ascii_w <= X\"32\"; ready_w <= '1'; ctrl_w <= '0';  -- 1E | F0,1E (ascii \"2\")");
      out.add("        when X\"26\" => ascii_w <= X\"33\"; ready_w <= '1'; ctrl_w <= '0';  -- 26 | F0,26 (ascii \"3\")");
      out.add("        when X\"25\" => ascii_w <= X\"34\"; ready_w <= '1'; ctrl_w <= '0';  -- 25 | F0,25 (ascii \"4\")");
      out.add("        when X\"2E\" => ascii_w <= X\"35\"; ready_w <= '1'; ctrl_w <= '0';  -- 2E | F0,2E (ascii \"5\")");
      out.add("        when X\"36\" => ascii_w <= X\"36\"; ready_w <= '1'; ctrl_w <= '0';  -- 36 | F0,36 (ascii \"6\")");
      out.add("        when X\"3D\" => ascii_w <= X\"37\"; ready_w <= '1'; ctrl_w <= '0';  -- 3D | F0,3D (ascii \"7\")");
      out.add("        when X\"3E\" => ascii_w <= X\"38\"; ready_w <= '1'; ctrl_w <= '0';  -- 3E | F0,3E (ascii \"8\")");
      out.add("        when X\"46\" => ascii_w <= X\"39\"; ready_w <= '1'; ctrl_w <= '0';  -- 46 | F0,46 (ascii \"9\")");
      out.add("");
      out.add("        when X\"54\" => ascii_w <= X\"5b\"; ready_w <= '1'; ctrl_w <= '0';  -- 54 | F0,54 (ascii \"[\")");
      out.add("        when X\"0E\" => ascii_w <= X\"60\"; ready_w <= '1'; ctrl_w <= '0';  -- 0E | F0,0E (ascii \"`\")");
      out.add("        when X\"4E\" => ascii_w <= X\"2d\"; ready_w <= '1'; ctrl_w <= '0';  -- 4E | F0,4E (ascii \"-\")");
      out.add("        when X\"55\" => ascii_w <= X\"3d\"; ready_w <= '1'; ctrl_w <= '0';  -- 55 | F0,55 (ascii \"=\")");
      out.add("        when X\"5D\" => ascii_w <= X\"5c\"; ready_w <= '1'; ctrl_w <= '0';  -- 5D | F0,5D (ascii \"\\\\\") -- should be backslash ");
      out.add("        when X\"5B\" => ascii_w <= X\"5d\"; ready_w <= '1'; ctrl_w <= '0';  -- 5B | F0,5B (ascii \"]\")");
      out.add("        when X\"4C\" => ascii_w <= X\"3b\"; ready_w <= '1'; ctrl_w <= '0';  -- 4C | F0,4C (ascii \";\")");
      out.add("        when X\"52\" => ascii_w <= X\"27\"; ready_w <= '1'; ctrl_w <= '0';  -- 52 | F0,52 (ascii \"'\")");
      out.add("        when X\"41\" => ascii_w <= X\"2c\"; ready_w <= '1'; ctrl_w <= '0';  -- 41 | F0,41 (ascii \",\")");
      out.add("        when X\"49\" => ascii_w <= X\"2e\"; ready_w <= '1'; ctrl_w <= '0';  -- 49 | F0,49 (ascii \".\")");
      out.add("        when X\"4A\" => ascii_w <= X\"2f\"; ready_w <= '1'; ctrl_w <= '0';  -- 4A | F0,4A (ascii \"/\")");
      out.add("");
      out.add("        when X\"7C\" => ascii_w <= X\"2a\"; ready_w <= '1'; ctrl_w <= '0';  -- 7C | F0,7C (KP) (ascii \"*\")");
      out.add("        when X\"7B\" => ascii_w <= X\"2d\"; ready_w <= '1'; ctrl_w <= '0';  -- 7B | F0,7B (KP) (ascii \"-\")");
      out.add("        when X\"79\" => ascii_w <= X\"2b\"; ready_w <= '1'; ctrl_w <= '0';  -- 79 | F0,79 (KP) (ascii \"+\")");
      out.add("        when X\"71\" => ascii_w <= X\"2e\"; ready_w <= '1'; ctrl_w <= '0';  -- 71 | F0,71 (KP) (ascii \".\")");
      out.add("        when X\"70\" => ascii_w <= X\"30\"; ready_w <= '1'; ctrl_w <= '0';  -- 70 | F0,70 (KP) (ascii \"0\")");
      out.add("        when X\"69\" => ascii_w <= X\"31\"; ready_w <= '1'; ctrl_w <= '0';  -- 69 | F0,69 (KP) (ascii \"1\")");
      out.add("        when X\"72\" => ascii_w <= X\"32\"; ready_w <= '1'; ctrl_w <= '0';  -- 72 | F0,72 (KP) (ascii \"2\")");
      out.add("        when X\"7A\" => ascii_w <= X\"33\"; ready_w <= '1'; ctrl_w <= '0';  -- 7A | F0,7A (KP) (ascii \"3\")");
      out.add("        when X\"6B\" => ascii_w <= X\"34\"; ready_w <= '1'; ctrl_w <= '0';  -- 6B | F0,6B (KP) (ascii \"4\")");
      out.add("        when X\"73\" => ascii_w <= X\"35\"; ready_w <= '1'; ctrl_w <= '0';  -- 73 | F0,73 (KP) (ascii \"5\")");
      out.add("        when X\"74\" => ascii_w <= X\"36\"; ready_w <= '1'; ctrl_w <= '0';  -- 74 | F0,74 (KP) (ascii \"6\")");
      out.add("        when X\"6C\" => ascii_w <= X\"37\"; ready_w <= '1'; ctrl_w <= '0';  -- 6C | F0,6C (KP) (ascii \"7\")");
      out.add("        when X\"75\" => ascii_w <= X\"38\"; ready_w <= '1'; ctrl_w <= '0';  -- 75 | F0,75 (KP) (ascii \"8\")");
      out.add("        when X\"7D\" => ascii_w <= X\"39\"; ready_w <= '1'; ctrl_w <= '0';  -- 7D | F0,7D (KP) (ascii \"9\")");
      out.add("");
      out.add("        when X\"29\" => ascii_w <= X\"20\"; ready_w <= '1'; ctrl_w <= '0';  -- 29 | F0,29 (ascii \" \")");
      out.add("        when X\"0D\" => ascii_w <= X\"09\"; ready_w <= '1'; ctrl_w <= '0';  -- 0D | F0,0D (ascii \"\\t\")");
      out.add("        when X\"5A\" => ascii_w <= X\"0a\"; ready_w <= '1'; ctrl_w <= '0';  -- 5A | F0,5A (ascii \"\\n\")");
      out.add("");
      out.add("        when X\"66\" => ascii_w <= X\"00\"; ready_w <= '0'; ctrl_w <= '1';  -- 66 | F0,66 (BKSP)");
      out.add("        when X\"58\" => ascii_w <= X\"00\"; ready_w <= '0'; ctrl_w <= '1'; caps_w <= not caps;  -- 58 | F0,58 (CAPS)");
      out.add("        when X\"77\" => ascii_w <= X\"00\"; ready_w <= '0'; ctrl_w <= '1';  -- 77 | F0,77 (NUM)");
      out.add("        when X\"12\" => ascii_w <= X\"00\"; ready_w <= '0'; ctrl_w <= '1'; shift_w <= '1';  -- 12 | F0,12 (L SHFT)");
      out.add("        when X\"14\" => ascii_w <= X\"00\"; ready_w <= '0'; ctrl_w <= '1';  -- 14 | F0,14 (L CTRL)");
      out.add("        when X\"11\" => ascii_w <= X\"00\"; ready_w <= '0'; ctrl_w <= '1';  -- 11 | F0,11 (L ALT)");
      out.add("        when X\"59\" => ascii_w <= X\"00\"; ready_w <= '0'; ctrl_w <= '1'; shift_w <= '1';  -- 59 | F0,59 (R SHFT)");
      out.add("        when X\"76\" => ascii_w <= X\"00\"; ready_w <= '0'; ctrl_w <= '1';  -- 76 | F0,76 (ESC)");
      out.add("        when X\"05\" => ascii_w <= X\"00\"; ready_w <= '0'; ctrl_w <= '1';  -- 05 | F0,05 (F1)");
      out.add("        when X\"06\" => ascii_w <= X\"00\"; ready_w <= '0'; ctrl_w <= '1';  -- 06 | F0,06 (F2)");
      out.add("        when X\"04\" => ascii_w <= X\"00\"; ready_w <= '0'; ctrl_w <= '1';  -- 04 | F0,04 (F3)");
      out.add("        when X\"0C\" => ascii_w <= X\"00\"; ready_w <= '0'; ctrl_w <= '1';  -- 0C | F0,0C (F4)");
      out.add("        when X\"03\" => ascii_w <= X\"00\"; ready_w <= '0'; ctrl_w <= '1';  -- 03 | F0,03 (F5)");
      out.add("        when X\"0B\" => ascii_w <= X\"00\"; ready_w <= '0'; ctrl_w <= '1';  -- 0B | F0,0B (F6)");
      out.add("        when X\"83\" => ascii_w <= X\"00\"; ready_w <= '0'; ctrl_w <= '1';  -- 83 | F0,83 (F7)");
      out.add("        when X\"0A\" => ascii_w <= X\"00\"; ready_w <= '0'; ctrl_w <= '1';  -- 0A | F0,0A (F8)");
      out.add("        when X\"01\" => ascii_w <= X\"00\"; ready_w <= '0'; ctrl_w <= '1';  -- 01 | F0,01 (F9)");
      out.add("        when X\"09\" => ascii_w <= X\"00\"; ready_w <= '0'; ctrl_w <= '1';  -- 09 | F0,09 (F10)");
      out.add("        when X\"78\" => ascii_w <= X\"00\"; ready_w <= '0'; ctrl_w <= '1';  -- 78 | F0,78 (F11)");
      out.add("        when X\"07\" => ascii_w <= X\"00\"; ready_w <= '0'; ctrl_w <= '1';  -- 07 | F0,07 (F12)");
      out.add("        when X\"7E\" => ascii_w <= X\"00\"; ready_w <= '0'; ctrl_w <= '1';  -- 7E | F0,7E (SCROLL)");
      out.add("");
      out.add("        when X\"F0\" => ascii_w <= X\"00\"; ready_w <= '0'; ctrl_w <= '0';  -- start of break code");
      out.add("        when X\"E0\" => ascii_w <= X\"00\"; ready_w <= '0'; ctrl_w <= '0';  -- start of multi-byte make or break code");
      out.add("        when X\"E1\" => ascii_w <= X\"00\"; ready_w <= '0'; ctrl_w <= '0';  -- start of multi-byte make or break code");
      out.add("");
      out.add("        when others => ascii_w <= X\"00\"; ready_w <= '0'; ctrl_w <= '1'; -- unrecognized, discard");
      out.add("");
      out.add("      end case;");
      out.add("    elsif scc = 1 then");
      out.add("            -- shifted");
      out.add("      case sc0 is");
      out.add("        when X\"1C\" => ascii_w <= X\"41\"; ready_w <= '1'; ctrl_w <= '0';  -- 1C | F0,1C (ascii \"A\")");
      out.add("        when X\"32\" => ascii_w <= X\"42\"; ready_w <= '1'; ctrl_w <= '0';  -- 32 | F0,32 (ascii \"B\")");
      out.add("        when X\"21\" => ascii_w <= X\"43\"; ready_w <= '1'; ctrl_w <= '0';  -- 21 | F0,21 (ascii \"C\")");
      out.add("        when X\"23\" => ascii_w <= X\"44\"; ready_w <= '1'; ctrl_w <= '0';  -- 23 | F0,23 (ascii \"D\")");
      out.add("        when X\"24\" => ascii_w <= X\"45\"; ready_w <= '1'; ctrl_w <= '0';  -- 24 | F0,24 (ascii \"E\")");
      out.add("        when X\"2B\" => ascii_w <= X\"46\"; ready_w <= '1'; ctrl_w <= '0';  -- 2B | F0,2B (ascii \"F\")");
      out.add("        when X\"34\" => ascii_w <= X\"47\"; ready_w <= '1'; ctrl_w <= '0';  -- 34 | F0,34 (ascii \"G\")");
      out.add("        when X\"33\" => ascii_w <= X\"48\"; ready_w <= '1'; ctrl_w <= '0';  -- 33 | F0,33 (ascii \"H\")");
      out.add("        when X\"43\" => ascii_w <= X\"49\"; ready_w <= '1'; ctrl_w <= '0';  -- 43 | F0,43 (ascii \"I\")");
      out.add("        when X\"3B\" => ascii_w <= X\"4a\"; ready_w <= '1'; ctrl_w <= '0';  -- 3B | F0,3B (ascii \"J\")");
      out.add("        when X\"42\" => ascii_w <= X\"4b\"; ready_w <= '1'; ctrl_w <= '0';  -- 42 | F0,42 (ascii \"K\")");
      out.add("        when X\"4B\" => ascii_w <= X\"4c\"; ready_w <= '1'; ctrl_w <= '0';  -- 4B | F0,4B (ascii \"L\")");
      out.add("        when X\"3A\" => ascii_w <= X\"4d\"; ready_w <= '1'; ctrl_w <= '0';  -- 3A | F0,3A (ascii \"M\")");
      out.add("        when X\"31\" => ascii_w <= X\"4e\"; ready_w <= '1'; ctrl_w <= '0';  -- 31 | F0,31 (ascii \"N\")");
      out.add("        when X\"44\" => ascii_w <= X\"4f\"; ready_w <= '1'; ctrl_w <= '0';  -- 44 | F0,44 (ascii \"O\")");
      out.add("        when X\"4D\" => ascii_w <= X\"50\"; ready_w <= '1'; ctrl_w <= '0';  -- 4D | F0,4D (ascii \"P\")");
      out.add("        when X\"15\" => ascii_w <= X\"51\"; ready_w <= '1'; ctrl_w <= '0';  -- 15 | F0,15 (ascii \"Q\")");
      out.add("        when X\"2D\" => ascii_w <= X\"52\"; ready_w <= '1'; ctrl_w <= '0';  -- 2D | F0,2D (ascii \"R\")");
      out.add("        when X\"1B\" => ascii_w <= X\"53\"; ready_w <= '1'; ctrl_w <= '0';  -- 1B | F0,1B (ascii \"S\")");
      out.add("        when X\"2C\" => ascii_w <= X\"54\"; ready_w <= '1'; ctrl_w <= '0';  -- 2C | F0,2C (ascii \"T\")");
      out.add("        when X\"3C\" => ascii_w <= X\"55\"; ready_w <= '1'; ctrl_w <= '0';  -- 3C | F0,3C (ascii \"U\")");
      out.add("        when X\"2A\" => ascii_w <= X\"56\"; ready_w <= '1'; ctrl_w <= '0';  -- 2A | F0,2A (ascii \"V\")");
      out.add("        when X\"1D\" => ascii_w <= X\"57\"; ready_w <= '1'; ctrl_w <= '0';  -- 1D | F0,1D (ascii \"W\")");
      out.add("        when X\"22\" => ascii_w <= X\"58\"; ready_w <= '1'; ctrl_w <= '0';  -- 22 | F0,22 (ascii \"X\")");
      out.add("        when X\"35\" => ascii_w <= X\"59\"; ready_w <= '1'; ctrl_w <= '0';  -- 35 | F0,35 (ascii \"Y\")");
      out.add("        when X\"1A\" => ascii_w <= X\"5a\"; ready_w <= '1'; ctrl_w <= '0';  -- 1A | F0,1A (ascii \"Z\")");
      out.add("");
      out.add("        when X\"45\" => ascii_w <= X\"29\"; ready_w <= '1'; ctrl_w <= '0';  -- 45 | F0,45 (ascii \"0\", shifted to \")\")");
      out.add("        when X\"16\" => ascii_w <= X\"21\"; ready_w <= '1'; ctrl_w <= '0';  -- 16 | F0,16 (ascii \"1\", shifted to \"!\")");
      out.add("        when X\"1E\" => ascii_w <= X\"40\"; ready_w <= '1'; ctrl_w <= '0';  -- 1E | F0,1E (ascii \"2\", shifted to \"@\")");
      out.add("        when X\"26\" => ascii_w <= X\"23\"; ready_w <= '1'; ctrl_w <= '0';  -- 26 | F0,26 (ascii \"3\", shifted to \"#\")");
      out.add("        when X\"25\" => ascii_w <= X\"24\"; ready_w <= '1'; ctrl_w <= '0';  -- 25 | F0,25 (ascii \"4\", shifted to \"$\")");
      out.add("        when X\"2E\" => ascii_w <= X\"25\"; ready_w <= '1'; ctrl_w <= '0';  -- 2E | F0,2E (ascii \"5\", shifted to \"%\")");
      out.add("        when X\"36\" => ascii_w <= X\"5e\"; ready_w <= '1'; ctrl_w <= '0';  -- 36 | F0,36 (ascii \"6\", shifted to \"^\")");
      out.add("        when X\"3D\" => ascii_w <= X\"26\"; ready_w <= '1'; ctrl_w <= '0';  -- 3D | F0,3D (ascii \"7\", shifted to \"&\")");
      out.add("        when X\"3E\" => ascii_w <= X\"2a\"; ready_w <= '1'; ctrl_w <= '0';  -- 3E | F0,3E (ascii \"8\", shifted to \"*\")");
      out.add("        when X\"46\" => ascii_w <= X\"28\"; ready_w <= '1'; ctrl_w <= '0';  -- 46 | F0,46 (ascii \"9\", shifted to \"(\")");
      out.add("");
      out.add("        when X\"54\" => ascii_w <= X\"7b\"; ready_w <= '1'; ctrl_w <= '0';  -- 54 | F0,54 (ascii \"[\", shifted to \"{\")");
      out.add("        when X\"0E\" => ascii_w <= X\"7e\"; ready_w <= '1'; ctrl_w <= '0';  -- 0E | F0,0E (ascii \"`\", shifted to \"~\")");
      out.add("        when X\"4E\" => ascii_w <= X\"5f\"; ready_w <= '1'; ctrl_w <= '0';  -- 4E | F0,4E (ascii \"-\", shifted to \"_\")");
      out.add("        when X\"55\" => ascii_w <= X\"2b\"; ready_w <= '1'; ctrl_w <= '0';  -- 55 | F0,55 (ascii \"=\", shifted to \"+\")");
      out.add("        when X\"5D\" => ascii_w <= X\"7c\"; ready_w <= '1'; ctrl_w <= '0';  -- 5D | F0,5D (ascii \"?\", shifted to \"|\") -- should be backslash ");
      out.add("        when X\"5B\" => ascii_w <= X\"7d\"; ready_w <= '1'; ctrl_w <= '0';  -- 5B | F0,5B (ascii \"]\", shifted to \"}\")");
      out.add("        when X\"4C\" => ascii_w <= X\"3a\"; ready_w <= '1'; ctrl_w <= '0';  -- 4C | F0,4C (ascii \";\", shifted to \":\")");
      out.add("        when X\"52\" => ascii_w <= X\"22\"; ready_w <= '1'; ctrl_w <= '0';  -- 52 | F0,52 (ascii \"'\", shifted to \"\\\"\")");
      out.add("        when X\"41\" => ascii_w <= X\"3c\"; ready_w <= '1'; ctrl_w <= '0';  -- 41 | F0,41 (ascii \",\", shifted to \"<\")");
      out.add("        when X\"49\" => ascii_w <= X\"3e\"; ready_w <= '1'; ctrl_w <= '0';  -- 49 | F0,49 (ascii \".\", shifted to \">\")");
      out.add("        when X\"4A\" => ascii_w <= X\"3f\"; ready_w <= '1'; ctrl_w <= '0';  -- 4A | F0,4A (ascii \"/\", shifted to \"?\")");
      out.add("");
      out.add("        when X\"7C\" => ascii_w <= X\"2a\"; ready_w <= '1'; ctrl_w <= '0';  -- 7C | F0,7C (KP) (ascii \"*\")");
      out.add("        when X\"7B\" => ascii_w <= X\"2d\"; ready_w <= '1'; ctrl_w <= '0';  -- 7B | F0,7B (KP) (ascii \"-\")");
      out.add("        when X\"79\" => ascii_w <= X\"2b\"; ready_w <= '1'; ctrl_w <= '0';  -- 79 | F0,79 (KP) (ascii \"+\")");
      out.add("        when X\"71\" => ascii_w <= X\"2e\"; ready_w <= '1'; ctrl_w <= '0';  -- 71 | F0,71 (KP) (ascii \".\")");
      out.add("        when X\"70\" => ascii_w <= X\"30\"; ready_w <= '1'; ctrl_w <= '0';  -- 70 | F0,70 (KP) (ascii \"0\")");
      out.add("        when X\"69\" => ascii_w <= X\"31\"; ready_w <= '1'; ctrl_w <= '0';  -- 69 | F0,69 (KP) (ascii \"1\")");
      out.add("        when X\"72\" => ascii_w <= X\"32\"; ready_w <= '1'; ctrl_w <= '0';  -- 72 | F0,72 (KP) (ascii \"2\")");
      out.add("        when X\"7A\" => ascii_w <= X\"33\"; ready_w <= '1'; ctrl_w <= '0';  -- 7A | F0,7A (KP) (ascii \"3\")");
      out.add("        when X\"6B\" => ascii_w <= X\"34\"; ready_w <= '1'; ctrl_w <= '0';  -- 6B | F0,6B (KP) (ascii \"4\")");
      out.add("        when X\"73\" => ascii_w <= X\"35\"; ready_w <= '1'; ctrl_w <= '0';  -- 73 | F0,73 (KP) (ascii \"5\")");
      out.add("        when X\"74\" => ascii_w <= X\"36\"; ready_w <= '1'; ctrl_w <= '0';  -- 74 | F0,74 (KP) (ascii \"6\")");
      out.add("        when X\"6C\" => ascii_w <= X\"37\"; ready_w <= '1'; ctrl_w <= '0';  -- 6C | F0,6C (KP) (ascii \"7\")");
      out.add("        when X\"75\" => ascii_w <= X\"38\"; ready_w <= '1'; ctrl_w <= '0';  -- 75 | F0,75 (KP) (ascii \"8\")");
      out.add("        when X\"7D\" => ascii_w <= X\"39\"; ready_w <= '1'; ctrl_w <= '0';  -- 7D | F0,7D (KP) (ascii \"9\")");
      out.add("");
      out.add("        when X\"29\" => ascii_w <= X\"20\"; ready_w <= '1'; ctrl_w <= '0';  -- 29 | F0,29 (ascii \" \")");
      out.add("        when X\"0D\" => ascii_w <= X\"09\"; ready_w <= '1'; ctrl_w <= '0';  -- 0D | F0,0D (ascii \"\\t\")");
      out.add("        when X\"5A\" => ascii_w <= X\"0a\"; ready_w <= '1'; ctrl_w <= '0';  -- 5A | F0,5A (ascii \"\\n\")");
      out.add("");
      out.add("        when X\"66\" => ascii_w <= X\"00\"; ready_w <= '0'; ctrl_w <= '1';  -- 66 | F0,66 (BKSP)");
      out.add("        when X\"58\" => ascii_w <= X\"00\"; ready_w <= '0'; ctrl_w <= '1'; caps_w <= not caps;  -- 58 | F0,58 (CAPS)");
      out.add("        when X\"77\" => ascii_w <= X\"00\"; ready_w <= '0'; ctrl_w <= '1';  -- 77 | F0,77 (NUM)");
      out.add("        when X\"12\" => ascii_w <= X\"00\"; ready_w <= '0'; ctrl_w <= '1'; shift_w <= '1';  -- 12 | F0,12 (L SHFT)");
      out.add("        when X\"14\" => ascii_w <= X\"00\"; ready_w <= '0'; ctrl_w <= '1';  -- 14 | F0,14 (L CTRL)");
      out.add("        when X\"11\" => ascii_w <= X\"00\"; ready_w <= '0'; ctrl_w <= '1';  -- 11 | F0,11 (L ALT)");
      out.add("        when X\"59\" => ascii_w <= X\"00\"; ready_w <= '0'; ctrl_w <= '1'; shift_w <= '1';  -- 59 | F0,59 (R SHFT)");
      out.add("        when X\"76\" => ascii_w <= X\"00\"; ready_w <= '0'; ctrl_w <= '1';  -- 76 | F0,76 (ESC)");
      out.add("        when X\"05\" => ascii_w <= X\"00\"; ready_w <= '0'; ctrl_w <= '1';  -- 05 | F0,05 (F1)");
      out.add("        when X\"06\" => ascii_w <= X\"00\"; ready_w <= '0'; ctrl_w <= '1';  -- 06 | F0,06 (F2)");
      out.add("        when X\"04\" => ascii_w <= X\"00\"; ready_w <= '0'; ctrl_w <= '1';  -- 04 | F0,04 (F3)");
      out.add("        when X\"0C\" => ascii_w <= X\"00\"; ready_w <= '0'; ctrl_w <= '1';  -- 0C | F0,0C (F4)");
      out.add("        when X\"03\" => ascii_w <= X\"00\"; ready_w <= '0'; ctrl_w <= '1';  -- 03 | F0,03 (F5)");
      out.add("        when X\"0B\" => ascii_w <= X\"00\"; ready_w <= '0'; ctrl_w <= '1';  -- 0B | F0,0B (F6)");
      out.add("        when X\"83\" => ascii_w <= X\"00\"; ready_w <= '0'; ctrl_w <= '1';  -- 83 | F0,83 (F7)");
      out.add("        when X\"0A\" => ascii_w <= X\"00\"; ready_w <= '0'; ctrl_w <= '1';  -- 0A | F0,0A (F8)");
      out.add("        when X\"01\" => ascii_w <= X\"00\"; ready_w <= '0'; ctrl_w <= '1';  -- 01 | F0,01 (F9)");
      out.add("        when X\"09\" => ascii_w <= X\"00\"; ready_w <= '0'; ctrl_w <= '1';  -- 09 | F0,09 (F10)");
      out.add("        when X\"78\" => ascii_w <= X\"00\"; ready_w <= '0'; ctrl_w <= '1';  -- 78 | F0,78 (F11)");
      out.add("        when X\"07\" => ascii_w <= X\"00\"; ready_w <= '0'; ctrl_w <= '1';  -- 07 | F0,07 (F12)");
      out.add("        when X\"7E\" => ascii_w <= X\"00\"; ready_w <= '0'; ctrl_w <= '1';  -- 7E | F0,7E (SCROLL)");
      out.add("");
      out.add("        when X\"F0\" => ascii_w <= X\"00\"; ready_w <= '0'; ctrl_w <= '0';  -- start of break code");
      out.add("        when X\"E0\" => ascii_w <= X\"00\"; ready_w <= '0'; ctrl_w <= '0';  -- start of multi-byte make or break code");
      out.add("        when X\"E1\" => ascii_w <= X\"00\"; ready_w <= '0'; ctrl_w <= '0';  -- start of multi-byte make or break code");
      out.add("");
      out.add("        when others => ascii_w <= X\"00\"; ready_w <= '0'; ctrl_w <= '1'; -- unrecognized, discard");
      out.add("");
      out.add("      end case;");
      out.add("    elsif scc = 2 then");
      out.add("      -- KP /      | E0,4A                     | E0,F0,4A");
      out.add("      -- KP EN     | E0,5A                     | E0,F0,5A");
      out.add("      -- INSERT    | E0,70                     | E0,F0,70");
      out.add("      -- HOME      | E0,6C                     | E0,F0,6C");
      out.add("      -- PG UP     | E0,7D                     | E0,F0,7D");
      out.add("      -- DELETE    | E0,71                     | E0,F0,71");
      out.add("      -- end       | E0,69                     | E0,F0,69");
      out.add("      -- PG DN     | E0,7A                     | E0,F0,7A");
      out.add("      -- U ARROW   | E0,75                     | E0,F0,75");
      out.add("      -- L ARROW   | E0,6B                     | E0,F0,6B");
      out.add("      -- D ARROW   | E0,72                     | E0,F0,72");
      out.add("      -- R ARROW   | E0,74                     | E0,F0,74");
      out.add("      -- L GUI     | E0,1F                     | E0,F0,1F");
      out.add("      -- R ALT     | E0,11                     | E0,F0,11");
      out.add("      -- R CTRL    | E0,14                     | E0,F0,14");
      out.add("      -- R GUI     | E0,27                     | E0,F0,27");
      out.add("      -- APPS      | E0,2F                     | E0,F0,2F");
      out.add("      -- PRNT SCRN | E0,12, E0,7C              | E0,F0,7C, E0,F0,12 ");
      out.add("      -- PAUSE     | E1,14,77, E1,F0,14, F0,77 | ");
      out.add("");
      out.add("      if (sc1 = X\"F0\") and ((sc0 = X\"12\") or (sc0 = X\"59\")) then");
      out.add("        ascii_w <= X\"00\"; ready_w <= '0'; ctrl_w <= '1'; shift_w <= '0'; -- break for (L SHFT) or (R SHFT)");
      out.add("      elsif sc1 = X\"F0\" then");
      out.add("        ascii_w <= X\"00\"; ready_w <= '0'; ctrl_w <= '1'; -- break for other 1 byte make codes");
      out.add("      elsif (sc1 = X\"E0\") and (sc0 = X\"4a\") then");
      out.add("        ascii_w <= X\"2f\"; ready_w <= '1'; ctrl_w <= '0'; -- E0,4A | E0,F0,4A (KP) (ascii \"/\")");
      out.add("      elsif (sc1 = X\"E0\") and (sc0 = X\"5a\") then");
      out.add("        ascii_w <= X\"0a\"; ready_w <= '1'; ctrl_w <= '0'; -- E0,5A | E0,F0,5A (KP) (ascii \"\\n\")");
      out.add("      elsif (sc1 = X\"E0\") and (sc0 /= X\"F0\") then");
      out.add("        ascii_w <= X\"00\"; ready_w <= '0'; ctrl_w <= '1'; -- other 2 byte make codes");
      out.add("      else");
      out.add("        ascii_w <= X\"00\"; ready_w <= '0'; ctrl_w <= '0'; -- other");
      out.add("      end if;");
      out.add("    elsif scc = 3 then");
      out.add("      ascii_w <= X\"00\";");
      out.add("      ready_w <= '0';");
      out.add("      ctrl_w <= '1'; -- probably 3 byte make code or break code for 2 byte make code ");
      out.add("    else");
      out.add("      ascii_w <= X\"00\";");
      out.add("      ready_w <= '0';");
      out.add("      ctrl_w <= '0';");
      out.add("    end if;");
      out.add("  end process;");
      out.add("");
      out.add("  -----------------------------------------------------------------------------");
      out.add("  -- ps2_keyboard scancode protocol");
      out.add("  -----------------------------------------------------------------------------");
      out.add("");
      out.add("  fifo_pop <= SlowClockEnable AND ReadEnable;");
      out.add("");
      out.add("  -- fifo process");
      out.add("  process (clk)");
      out.add("    -- type FIFO_Memory is array (0 to FIFO_DEPTH - 1) of std_logic_vector (DATA_WIDTH - 1 downto 0);");
      out.add("    -- variable Memory : FIFO_Memory;");
      out.add("    -- variable Head : natural range 0 to FIFO_DEPTH - 1;");
      out.add("    -- variable Tail : natural range 0 to FIFO_DEPTH - 1;");
      out.add("    -- variable Full : boolean;");
      out.add("    variable NewHead : natural range 0 to FIFO_DEPTH - 1;");
      out.add("    variable NewTail : natural range 0 to FIFO_DEPTH - 1;");
      out.add("    variable NewFull : boolean;");
      out.add("    variable WasEmpty : boolean;");
      out.add("    variable DoPop : boolean;");
      out.add("  begin");
      out.add("    if (rising_edge(clk)) then");
      out.add("      NewHead := Head; -- default");
      out.add("      NewTail := Tail; -- default");
      out.add("      NewFull := Full; -- default");
      out.add("      WasEmpty := (not Full) and (Head = Tail);");
      out.add("      DoPop := (fifo_pop = '1') and (not WasEmpty);");
      out.add("");
      out.add("      if ((char_ready = '0') and DoPop) then -- Pop old data only");
      out.add("        NewHead := Head;");
      out.add("        if (Tail = FIFO_DEPTH - 1) then");
      out.add("          NewTail := 0;");
      out.add("        else");
      out.add("          NewTail := Tail + 1;");
      out.add("        end if;");
      out.add("        NewFull := false;");
      out.add("        -- ascii <= Memory(NewTail); -- only matters when queue will still not be empty");
      out.add("        -- ready <= '1' when (NewTail /= NewHead) else '0';");
      out.add("        if (NewTail /= NewHead) then");
      out.add("          ascii <= Memory(NewTail);");
      out.add("          ready <= '1';");
      out.add("        else");
      out.add("          ascii <= X\"00\";");
      out.add("          ready <= '0';");
      out.add("        end if;");
      out.add("");
      out.add("      elsif ((char_ready = '1') and (not DoPop)) then -- Push new data only");
      out.add("        if (not Full) then");
      out.add("          if (Head = FIFO_DEPTH - 1) then");
      out.add("            NewHead := 0;");
      out.add("          else");
      out.add("            NewHead := Head + 1;");
      out.add("          end if;");
      out.add("          NewTail := Tail;");
      out.add("          NewFull := (NewTail = NewHead);");
      out.add("          Memory(Head) <= char_ascii;");
      out.add("          if (WasEmpty) then");
      out.add("            ascii <= char_ascii;");
      out.add("            ready <= '1';");
      out.add("          end if;");
      out.add("        end if;");
      out.add("");
      out.add("      elsif ((char_ready = '1') and DoPop) then -- Push and also pop");
      out.add("        if (Head = FIFO_DEPTH - 1) then");
      out.add("          NewHead := 0;");
      out.add("        else");
      out.add("          NewHead := Head + 1;");
      out.add("        end if;");
      out.add("        if (Tail = FIFO_DEPTH - 1) then");
      out.add("          NewTail := 0;");
      out.add("        else");
      out.add("          NewTail := Tail + 1;");
      out.add("        end if;");
      out.add("        NewFull := (NewTail = NewHead); -- same as Full (will be unchanged)");
      out.add("        Memory(Head) <= char_ascii;");
      out.add("        ascii <= Memory(NewTail);");
      out.add("        ready <= '1';");
      out.add("      end if;");
      out.add("");
      out.add("      Head <= NewHead;");
      out.add("      Tail <= NewTail;");
      out.add("      Full <= NewFull;");
      out.add("    end if;");
      out.add("  end process;");
      out.add("");
      out.add("end logisim_generated;");
      out.add("");
    }
    return out;
  }

  // @Override
  // protected void getPortMappings(Hdl.Map map, NetlistComponent comp, PortInfo p) {
  //   if (p.name.equals("FPGAClock"))
  //     map.add(p.name, TickHDLGenerator.FPGA_CLK_NET);
  //   else
  //     super.getPortMappings(map, comp, p);
  // }

  @Override
  protected Hdl.Map getPortMappings(NetlistComponent comp) {
    // todo: support CLR
    if (comp.endIsConnected(Keyboard.CLR))
      _err.AddWarning("Clear signal is not yet supported for Keyboard component in HDL");
    return super.getPortMappings(comp);
  }
}
