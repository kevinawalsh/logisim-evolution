Logisim-Evolution (Holy Cross Edition)
======================================

Logisim is an educational tool for designing and simulating digital logic circuits.
It was originally created by [Dr. Carl Burch](http://www.cburch.com/logisim/)
and actively developed by him until 2011. This is the "Holy Cross Edition",
which is maintained and used by [Holy Cross](https://www.holycross.edu/) and
others.

You can get the latest *stable version* of Logisim-Evolution (Holy Cross Edition) here:

[![Download for Windows](https://raw.githubusercontent.com/kevinawalsh/logisim-evolution/holycross/images/download-windows.jpg)](https://github.com/kevinawalsh/logisim-evolution/releases/download/v5.0.4/Logisim-Evolution-5.0.4hc.exe)
[![Download for Mac](https://raw.githubusercontent.com/kevinawalsh/logisim-evolution/holycross/images/download-mac.jpg)](https://github.com/kevinawalsh/logisim-evolution/releases/download/v5.0.4/Logisim-Evolution-5.0.4-HC.pkg)
[![Download for Intel Mac](https://raw.githubusercontent.com/kevinawalsh/logisim-evolution/holycross/images/download-mac-x86.jpg)](https://github.com/kevinawalsh/logisim-evolution/releases/download/v5.0.4/Logisim-Evolution-5.0.4-HC-x86.pkg)
[![Download for Linux](https://raw.githubusercontent.com/kevinawalsh/logisim-evolution/holycross/images/download-linux.jpg)](https://github.com/kevinawalsh/logisim-evolution/releases/download/v5.0.4/logisim-evolution-5.0.4hc.jar)

**Which version for Mac?**: Recent Mac systems should use the "Apple Silicon"
version. Older Intel-based Macs should use the x86 version.

**Mac Security Warning**: On some recent MacOS versions, the OS security
gatekeeper may prevent you from opening the PKG file above directly. Instead,
right-click and "Save As..." to save the PKG file to your download folder. Then
go to your download folder in the finder, right click the PKG file and "Open
with... Installer (Default)". When given a security warning, click "Open". This
should install the program.

An alternative [ZIP format version for Windows](https://github.com/kevinawalsh/logisim-evolution/releases/download/v5.0.4/Logisim-Evolution-5.0.4hc-windows.zip) is
also available. After downloading it, extract the compressed contents to a directory of your choice and run the
extracted exe file.

Windows and Mac versions will work only on Windows or Mac. The platform-independent JAR file should work on any
platform, but requires that Java version 11 or above be installed separately
(Java version 17 is recommended; the Adoptium project's Temurin OpenJDK 17
available [here](https://adoptium.net/) is one possible choice).
To run the JAR file, open a command line (or Mac Terminal or Windows CMD.exe
prompt) and type `java -jar logisim-evolution-5.0.4hc.jar` from within the
directory where you have downloaded the JAR file.

## What's new in version 5.x.x

* Mac releases are compiled for Apple M1 and M2 processors, so should perform
  much better.
* A new built-in audio component library.
* Custom appearance shapes can be automatically hidden or shown depending on
  circuit state. This enables building fancy dynamic components that change
  appearance depending on their state.
* Higher auto-tick rates.
* Scroll/zoom now works on mac, and many other bug fixes and small
  user-interface improvements.
* Auto-backup and recovery of projects to help avoid losing your work.

## History

This is a fork of
[reds-heig logisim-evolution](https://github.com/reds-heig/logisim-evolution), which in
turn is a fork of the original
[Logisim by Dr. Carl Burch](http://www.cburch.com/logisim/). Mainly, the changes
revolve around:
* better support for the Altera DE0 FPGA prototype board;
* bidirectional FPGA I/O ports (e.g. to support the DE0 keyboard and LCD module).
* new HDL components (multipliers, divmod, etc.);
* support for VHDL _generics_;
* UI changes to aid in HDL editing and FPGA downloading.
* Performance has been improved in this version with help from the [YourKit java profiler](https://www.yourkit.com/java/profiler/).
  ![YourKit Logo](https://mathcs.holycross.edu/~kwalsh/yklogo.png)

On a historical note: I am also the author of all the scattered code mentioning
"Cornell's version of Logisim", both in the reds-heig fork and Carl Burch's
version. That code was written when I taught cs3410 (previously cs314) as a grad
student and, before that, as an undergrad at Cornell. Unsurprisingly, this new
fork is to support the course I now teach, csci226, at
[Holy Cross](https://mathcs.holycross.edu/~csci226/).

This version, Logisim-evolution (Holy Cross Edition) is maintained by:
* Kevin Walsh (kwalsh@holycross.edu), [College of the Holy Cross](https://holycross.edu) 

If you find a bug or have an idea for an interesting feature, please do not
hesitate to contact me. Or even better, contribute code, examples, or fixes!

## License
The code is licensed under the GNU GENERAL PUBLIC LICENSE, version 3.

## Credits
The following institutions/people actively contributed to Logisim-evolution:
* Carl Burch - Hendrix College - USA
* [Haute École Spécialisée Bernoise](http://www.bfh.ch) - Switzerland
* [Haute École du paysage, d'ingénierie et d'architecture de Genève](http://hepia.hesge.ch) - Switzerland
* [Haute École d'Ingénierie et de Gestion du Canton de Vaud](http://www.heig-vd.ch) - Switzerland
* Theldo Cruz Franqueira - Pontifícia Universidade Católica de Minas Gerais - Brasil
* Moshe Berman - Brooklyn College
