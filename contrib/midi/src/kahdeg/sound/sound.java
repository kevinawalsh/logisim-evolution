package kahdeg.sound;

import java.awt.Color;
import java.awt.Component;
import java.awt.Graphics;
import javax.swing.Icon;
import javax.sound.midi.*;

import com.cburch.logisim.data.Attribute;
import com.cburch.logisim.data.AttributeOption;
import com.cburch.logisim.data.Attributes;
import com.cburch.logisim.data.Bounds;
import com.cburch.logisim.data.Value;
import com.cburch.logisim.data.BitWidth;
import com.cburch.logisim.instance.InstanceFactory;
import com.cburch.logisim.instance.InstancePainter;
import com.cburch.logisim.instance.InstancePoker;
import com.cburch.logisim.instance.InstanceState;
import com.cburch.logisim.instance.Port;

public class sound extends InstanceFactory{

  private static final BitWidth BIT_WIDTH4 = BitWidth.create(4);
  private static final BitWidth BIT_WIDTH7 = BitWidth.create(7);
  private static final BitWidth BIT_WIDTH8 = BitWidth.create(8);

  static AttributeOption instlist[];
  static Attribute<AttributeOption> ATTR_INSTR;
  static Attribute<Integer> ATTR_CHAN;

  public sound(){
    super("MIDI output");
    init();
    setAttributes(new Attribute[] { ATTR_INSTR, ATTR_CHAN },
        new Object[] { instlist[0], 0 });
    setOffsetBounds(Bounds.create(-30, -30, 30, 60));
    Port[] ps=new Port[5];
    ps[0]=new Port(-30, 0, Port.INPUT, BIT_WIDTH8);
    ps[0].setToolTip(new SimpleStringGetter("Note"));
    ps[1]=new Port(-30, 10, Port.INPUT, BIT_WIDTH7);
    ps[1].setToolTip(new SimpleStringGetter("Velocity"));
    ps[2]=new Port(-30, -10, Port.INPUT, 1);
    ps[2].setToolTip(new SimpleStringGetter("Damping"));
    ps[3]=new Port(-30, -20, Port.INPUT, BIT_WIDTH4);
    ps[3].setToolTip(new SimpleStringGetter("Channel"));
    ps[4]=new Port(-30, 20, Port.INPUT, BIT_WIDTH8);
    ps[4].setToolTip(new SimpleStringGetter("Instrument"));
    setPorts(ps);
    setIcon(new SpeakerIcon());
    setInstancePoker(Poker.class);
  }

  static class SpeakerIcon implements Icon {
    public SpeakerIcon() { }
    public int getIconWidth() { return 16; }
    public int getIconHeight() { return 16; }
    public void paintIcon(Component c, Graphics g, int x, int y) {
      g.setColor(Color.BLACK);
      g.drawRect(x+1, y+5, 4, 6);
      int[] bx = new int[] { x+4, x+8, x+9, x+9, x+8, x+4 };
      int[] by = new int[] { y+5, y+1, y+1, y+15, y+15, y+11 };
      g.setColor(Color.BLUE);
      g.drawPolyline(bx, by, 6);
      g.drawArc(x+9, y-1, 6, 18, -60, 120);
      g.drawArc(x+7, y+3, 6, 10, -60, 120);
    }
  }

  @Override
  public void propagate(InstanceState state) {
    int note = 0; 		//7 bit in
    int velocity = 0; 	//7 bit in
    int chan=0;
    int inst=0;
    int damper=0;
    Value valnote = state.getPortValue(0);
    Value valvelocity = state.getPortValue(1);
    Value valdamper = state.getPortValue(2);
    Value valchan = state.getPortValue(3);
    Value valinst = state.getPortValue(4);
    note = valnote.isFullyDefined() ? valnote.toIntValue() : -1;
    velocity = valvelocity.isFullyDefined() ? valvelocity.toIntValue() : -1;
    if ((velocity<0)&&(note>=0)) { velocity=127; }
    damper = valdamper.isFullyDefined() ? valdamper.toIntValue() : -1;
    if (damper<0) { damper=1; }
    chan = valchan.isFullyDefined() ? valchan.toIntValue() : -1;
    if (chan<0) { chan=state.getAttributeValue(ATTR_CHAN); }
    inst = valinst.isFullyDefined() ? valinst.toIntValue() : -1;
    if (inst<0)
    {
      String instv = state.getAttributeValue(ATTR_INSTR).toString();
      int i;
      for (i=1;i<3;i++) { if (instv.charAt(i)==':') break; }
      inst=Integer.parseInt(instv.substring(0,i));
    }

    if (note>=128) { play(0,0,damper,chan,inst); }
    // if (note==128) { play(0,0,damper,chan,inst); }
    // else if (note>128) { play(note-128,velocity,damper,chan,inst); }
    else { play(note,velocity,damper,chan,inst); }
  }

  public void paintInstance(InstancePainter painter) {
    painter.drawBounds();
    painter.drawPort(0); // draw a triangle on port 0
    painter.drawPort(1); // draw port 1 as just a dot
    painter.drawPort(2);
    painter.drawPort(3);
    painter.drawPort(4);

    Bounds bds = painter.getNominalBounds();
    Graphics g = painter.getGraphics();
    int x = bds.x + (bds.width-24)/2;
    int y = bds.y + (bds.height-24)/2;
    g.setColor(Color.BLACK);
    g.drawRect(x+1, y+7, 6, 10);
    int[] bx = new int[] { x+7, x+13, x+14, x+14, x+13, x+7 };
    int[] by = new int[] { y+7, y+1, y+1, y+23, y+23, y+17 };
    g.setColor(Color.BLUE);
    g.drawPolyline(bx, by, 6);
    g.drawArc(x+14, y+1, 9, 22, -60, 120);
    g.drawArc(x+10, y+5, 9, 12, -60, 120);
    // Display the current counter value centered within the rectangle.
    // However, if the context says not to show state (as when generating
    // printer output), then skip this.
    // if(painter.getShowState()) {
    //   Bounds bds = painter.getBounds();
    // }
  }

  Synthesizer midiSynth;
  Instrument[] instr;
  MidiChannel[] mChannels;
  int[] mInstruments;

  public void init()
  {
    try{
      midiSynth = MidiSystem.getSynthesizer(); 
      midiSynth.open();
      //get and load default instrument and channel lists
      instr = midiSynth.getDefaultSoundbank().getInstruments();
      /*
         for (int i=0;i<instr.length;i++) { System.out.print(i+": "+instr[i].getName()+"\n"); }
         */
      instlist=new AttributeOption[instr.length];
      for (int i=0;i<instr.length;i++)
      {
        String name=i+": "+instr[i].getName();
        instlist[i]=new AttributeOption(name,name,new SimpleStringGetter(name));
      }
      ATTR_INSTR=Attributes.forOption("instrument",
          new SimpleStringGetter("Instrument"),instlist);
      mChannels = midiSynth.getChannels();
      ATTR_CHAN=Attributes.forIntegerRange("channel", new SimpleStringGetter("Channel"),0,mChannels.length-1);
      mChannels[0].allNotesOff();
      mInstruments = new int[mChannels.length];
      for (int i = 0; i < mInstruments.length; i++)
        mInstruments[i] = -1;
    } catch (MidiUnavailableException e) {}
  }

  public void play(int note,int velocity,int damper,int chan,int inst) { 
    //int note = 90;
    //int velocity = 100;
    int volume = 127;
    int durat=200;

    if (note<0) { note=0; }
    if (velocity<0) { velocity=0; }
    if (damper<0) { damper=1; }
    if (chan<0) { chan=0; }
    if (chan>=mChannels.length) { chan=mChannels.length-1; }
    if (inst<0) { inst=0; }
    if (inst>=instr.length) { inst=instr.length-1; }
    //System.out.print(note+" "+velocity+" "+damper+" "+chan+" "+inst+"\n");

    if (damper==1)
      mChannels[chan].allNotesOff();

    if (mInstruments[chan] != inst) {
      // switch instruments
      midiSynth.loadInstrument(instr[inst]);
      mChannels[chan].programChange(instr[inst].getPatch().getProgram());
      mChannels[chan].controlChange(7,volume);
      mInstruments[chan] = inst;
    }

    mChannels[chan].noteOn(note, velocity);
  }

  public static class Poker extends InstancePoker {
    /* @Override
       public void mousePressed(InstanceState state, MouseEvent e) {
       InstanceDataSingleton data = (InstanceDataSingleton) state.getData();
       }

       @Override
       public void mouseReleased(InstanceState state, MouseEvent e) {
       }

       private void setValue(InstanceState state, Value val) {
       InstanceDataSingleton data = (InstanceDataSingleton) state.getData();
       if (data == null) {
       state.setData(new InstanceDataSingleton(val));
       } else {
       data.setValue(val);
       }
       state.getInstance().fireInvalidated();
       } */
  }

}    
