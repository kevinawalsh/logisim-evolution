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

package com.cburch.logisim;

// Version number formats:
//   {major}.{minor}.{release}                ... no variant, FINAL_REVISION
//   {major}.{minor}.{release}-HC             ... "HC" variant, FINAL_REVISION
//   {major}.{minor}.{release}.{revision}     ... no variant
//   {major}.{minor}.{release}.{revision}-HC  ... "HC" variant
//   {major}.{minor}.{release}-HC ({hash})    ... "HC" variant, FINAL_REVISIONA, with git hash
// When parsing:
// - All leading and trailing whitespace is ignored.
// - If the result doesn't start with a digit, then
//   major, minor, release, and revision are all 0.
// - Otherwise, up to 4 non-negative integers, each separated
//   by one dot, one dash, or whitespace, are used for major,
//   minor, release, and revision. If omitted, these default
//   to 0, except revision which defaults to FINAL_REVISION.
// - Everything after the last of those 4 integers is for the
//   variant and git hash. If it ends in a set of parens, then
//   that part is the git commit hash, ignoring whitespace
//   around the parens. If omitted, the githash this defaults
//   to "". Whatever is left is the variiant, ignoring all
//   leading dots, dashes, and whitespace.
// Examples, with reasonable formatting:
//   5.0.5-HC  --> 5, 0, 5, FINAL_REVISION, "HC"
//   4.9.5-HC  --> 4, 9, 5, FINAL_REVISION, "HC"
//   4.9-HC    --> 4, 9, 0, FINAL_REVISION, "HC"
//   4         --> 4, 0, 0, FINAL_REVISION, ""
//   0         --> 0, 0, 0, FINAL_REVISION, ""
//   5.0.5-HC (c3052f2)  --> 4, 9, 0, FINAL_REVISION, "HC", githash="c3052f2"
// Examples, with unusual but accepted formatting:
//   5.0.5hc   --> 5, 0, 5, FINAL_REVISION, "hc"
//   5-0-5     --> 5, 0, 5, FINAL_REVISION, ""
//   5-0.5hc   --> 5, 0, 5, FINAL_REVISION, "hc"
//   4.9hc     --> 4, 9, 0, FINAL_REVISION, "hc"
//   4.9---hc  --> 4, 9, 0, FINAL_REVISION, "hc"
//   4 9. -hc  --> 4, 9, 0, FINAL_REVISION, "hc"
//   0  hc     --> 0, 0, 0, FINAL_REVISION, "hc"
// Examples, with unacceptable formatting / unusual results:
//   -1.1.1    --> 0, 0, 0, 0, "-1.1.1"
//   .1.1.1    --> 0, 0, 0, 0, ".1.1.1"
//   mystery   --> 0, 0, 0, 0, "mystery"
//   . . .     --> 0, 0, 0, 0, ""
//   .0. .     --> 0, 0, 0, 0, "0. ."
//   3 . 1 5   --> 3, 0, 0, FINAL_REVISION, "1 5"
public class LogisimVersion {
  
  // Create a version, used within xml parsing code in
  // backwards compatibility handling.
  public static LogisimVersion get(int major, int minor, int release) {
    if (major < 0) throw new IllegalArgumentException("major");
    if (minor < 0) throw new IllegalArgumentException("minor");
    if (release < 0) throw new IllegalArgumentException("release");
    return new LogisimVersion(major, minor, release, FINAL_REVISION, "");
  }

  // number of [0-9] chars at start of s
  private static int countLeadingDigits(String s) {
    int n = s.length();
    for (int i = 0; i < n; i++)
      if (!Character.isDigit(s.charAt(i)))
        return i;
    return n;
  }

  // // true iff s starts with ".", "-", or whitespace.
  // private static boolean hasLeadingSeparator(String s) {
  //   if (!s.isEmpty())
  //     return false;
  //   char c = s.charAt(0);
  //   return c == '.' || c == '-' || Character.isWhitespace(c);
  // }

  // if s starts with a valid separator then a digit,
  //   returns length of the separator
  // otherwise, if no digit, or invalid separator, returns 0
  private static int leadingSeparatorLength(String s) {
    int n = s.length();
    if (n < 2)
      return 0;
    // dot digit ...
    // dash digit ...
    char c = s.charAt(0);
    if ((c == '.' || c == '-') && Character.isDigit(s.charAt(1)))
      return 1;
    // whitespace digit ...
    if (!Character.isWhitespace(c))
      return 0;
    for (int i = 1; i < n; i++) {
      c = s.charAt(i);
      if (Character.isDigit(c))
        return i;
      if (!Character.isWhitespace(c))
        return 0;
    }
    return 0; // missing digit
  }

  private static String stripLeadingDotsDashesWhitespace(String s) {
    int n = s.length(), i = 0;
    while (i < n) {
      char c = s.charAt(i);
      if (c == '.' || c == '-' || Character.isWhitespace(c))
        i++;
      else
        break;
    }
    return s.substring(i);
  }

  /**
   * Parse a string containing a version number and returns the corresponding
   * LogisimVersion object. No exception is thrown if the version string
   * contains non-integers, because literal values are allowed.
   *
   * @return LogisimVersion built from the string passed as parameter
   */
  public static LogisimVersion parse(String versionString) {
    // ignore all leading and dtrailing whitespace
    versionString = versionString.strip();

    // if blank, return a minimal version
    if (versionString.isBlank())
      return new LogisimVersion(0, 0, 0, 0, "");

    int i;
    String s = versionString;

    // parse major, if present
    i = countLeadingDigits(s);
    if (i <= 0)
      return new LogisimVersion(0, 0, 0, 0, s);
    // {major}xxx
    int major = Integer.parseInt(s.substring(0, i));
    s = s.substring(i);

    // parse minor, if present
    i = leadingSeparatorLength(s);
    if (i <= 0)
      return new LogisimVersion(major, 0, 0, FINAL_REVISION, s);
    // {major}.{minor}xxx
    s = s.substring(i);
    i = countLeadingDigits(s);
    int minor = Integer.parseInt(s.substring(0, i));
    s = s.substring(i);

    // parse release, if present
    i = leadingSeparatorLength(s);
    if (i <= 0)
      return new LogisimVersion(major, minor, 0, FINAL_REVISION, s);
    // {major}.{minor}.{relese}xxx
    s = s.substring(i);
    i = countLeadingDigits(s);
    int release = Integer.parseInt(s.substring(0, i));
    s = s.substring(i);

    // parse revision, if present
    i = leadingSeparatorLength(s);
    if (i <= 0)
      return new LogisimVersion(major, minor, release, FINAL_REVISION, s);
    // {major}.{minor}.{release}.{revision}xxx
    s = s.substring(i);
    i = countLeadingDigits(s);
    int revision = Integer.parseInt(s.substring(0, i));
    s = s.substring(i);

    return new LogisimVersion(major, minor, release, revision, s);
  }

  private static final int FINAL_REVISION = Integer.MAX_VALUE / 4; // why div by 4?
  private final int major;
  private final int minor;
  private final int release;
  private final int revision;
  private final String variant;
  private final String githash;
  private final String str; // friendly version for printing, no githash

  private LogisimVersion(int major, int minor, int release, int revision, String vh) {
    this.major = major;
    this.minor = minor;
    this.release = release;
    this.revision = revision;

    vh = vh.strip();
    int i;
    if (vh.endsWith(")") && (i = vh.lastIndexOf('(')) >= 0) {
      githash = vh.substring(i+1, vh.length()-1).strip();
      vh = (i == 0) ? "" : vh.substring(0, i).strip();
    } else {
      githash = "";
    }
    this.variant = stripLeadingDotsDashesWhitespace(vh);

    String s = major + "." + minor + "." + release;
    if (revision != FINAL_REVISION)
        s += "." + revision;
    if (!variant.isEmpty())
        s += "-" + variant;
    // if (!githash.isEmpty())
    //     s += " (" + githash + ")";
    this.str = s;
  }

  // Compare two versions, where variant and githash are
  // used in some arbitrary way to break ties.
  // Returns negative if this is older than other.
  // Returns positive if this is newer than other.
  // Returns zero if this is equal to other.
  public int compareTo(LogisimVersion other) {
    int ret = this.major - other.major;
    if (ret == 0) ret = this.minor - other.minor;
    if (ret == 0) ret = this.release - other.release;
    if (ret == 0) ret = this.revision - other.revision;
    if (ret == 0) ret = this.variant.compareTo(other.variant);
    if (ret == 0) ret = this.githash.compareTo(other.githash);
    return ret;
  }

  @Override
  public boolean equals(Object other) {
    if (other instanceof LogisimVersion) {
      LogisimVersion o = (LogisimVersion) other;
      return this.major == o.major && this.minor == o.minor
          && this.release == o.release && this.revision == o.revision
          && this.variant.equals(o.variant) && this.githash.equals(o.githash);
    } else {
      return false;
    }
  }

  @Override
  public int hashCode() {
    int ret = major * 31 + minor;
    ret = ret * 31 + release;
    ret = ret * 31 + revision;
    ret = ret * 31 + variant.hashCode();
    ret = ret * 31 + githash.hashCode();
    return ret;
  }

  public String mainVersion() {
    return major + "." + minor + "." + release;
  }

  public String rev() {
    return revision != FINAL_REVISION ? "rev. " + revision : "";
  }

  public String edition() {
    if (variant.equalsIgnoreCase("HC"))
      return "Holy Cross Edition";
    else if (variant.isEmpty())
      return "";
    else
      return variant + " Edition";
  }

  @Override
  public String toString() {
    return str;
  }

  public String toDetailString() {
    return str + " (" + githash + ")";
  }

  // // test cases
  // static {
  //   String[] examples = {
  //   
  //     "5.0.5-HC (abcd1234)",
  //     "5.0.5-HC",
  //     "4.9.5-HC",
  //     "4.9-HC",
  //     "4",
  //     "0",
  //     "0 (abcd1234)",
  //    
  //     "5.0.5hc (abc123)",
  //     "5.0.5hc",
  //     "5-0-5",
  //     "5-0.5hc",
  //     "    5-0-5  hc  ( abcd1234 )  ",
  //     "    5-0-5  hc  ",
  //     "4.9hc",
  //     "4.9---hc",
  //     "4.9- . -  .  -hc---",
  //     "4 9. -hc",
  //     "0  hc",
  //     "  0  hc  ",
  //     "  0  hc  (deadf00d)",
  //     "  0  mystery  ",
  //     "  0 (123abc)  ",
  //     "  (123abc)  ",
  //     "  (0)  ",
  //     "  ()  ",
  //     
  //     "-1.1.1",
  //     ".1.1.1",
  //     "mystery",
  //     ". . .",
  //     ".0. .",
  //     "3 . 1 5",
  //     "3 . 1 5)",
  //     "(1.2.3)",
  //     "1.2.3)",
  //     "(1.2.3())",
  //     "1.2.3())",

  //     "0.0.0.abc",
  //     "0.0.0.5",
  //     "0.0.0.5-abc",
  //     "0.0.0.5.abc",
  //   };
  //   for (String s : examples) {
  //     LogisimVersion ver = LogisimVersion.parse(s);
  //     System.out.printf("%35s --> %d, %d, %d, %d, \"%s\"  [%s] [edition: '%s'] [githash: '%s']\n",
  //         "'" + s + "'",
  //         ver.major, ver.minor, ver.release, ver.revision, ver.variant,
  //         ver.toString(),
  //         ver.edition(),
  //         ver.githash);
  //   }
  // }

}
