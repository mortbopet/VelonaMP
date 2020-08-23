package velonamp.assembler

object LerosAssembler {
  val NOP     = 0x00
  val ADD     = 0x08
  val ADDI    = 0x09
  val SUB     = 0x0c
  val SUBI    = 0x0d
  val SHR     = 0x10
  val LDI     = 0x21
  val LOAD    = 0x20
  val STORE   = 0x30
  val LOADI   = 0x21
  val LOADHI  = 0x29
  val LOADH2I = 0x2a
  val LOADH3I = 0x2b
  val AND     = 0x22
  val ANDI    = 0x23
  val OR      = 0x24
  val ORI     = 0x25
  val XOR     = 0x26
  val XORI    = 0x27
  val LDHI    = 0x29
  val LDH2I   = 0x2a
  val LDH3I   = 0x2b
  val STIND   = 0x30
  val LDIND   = 0x60
  val BR      = 0x80
  val BRZ     = 0x90
  val BRNZ    = 0x10
  val BRP     = 0x11
  val BRN     = 0x12
  val LDADDR  = 0x50

  def toInt(s: String): Int = {
    if (s.startsWith("0x")) {
      Integer.parseInt(s.substring(2), 16)
    } else {
      Integer.parseInt(s)
    }
  }
  def regNumber(s: String): Int = {
    assert(s.startsWith("r"), "Register numbers shall start with \'r\''")
    s.substring(1).toInt
  }

  def assembleInstruction(s: String): Int = {
    val tokens = s.trim.split(' ')
    tokens(0) match {
      case "add"    => (ADD << 8) | regNumber(tokens(1))
      case "sub"    => (SUB << 8) | regNumber(tokens(1))
      case "and"    => (AND << 8) | regNumber(tokens(1))
      case "or"     => (OR << 8) | regNumber(tokens(1))
      case "xor"    => (XOR << 8) | regNumber(tokens(1))
      case "load"   => (LOAD << 8) | regNumber(tokens(1))
      case "store"  => (STORE << 8) | regNumber(tokens(1))
      case "addi"   => (ADDI << 8) | toInt(tokens(1)) & 0xff
      case "subi"   => (SUBI << 8) | toInt(tokens(1)) & 0xff
      case "andi"   => (ANDI << 8) | toInt(tokens(1)) & 0xff
      case "ori"    => (ORI << 8) | toInt(tokens(1)) & 0xff
      case "xori"   => (XORI << 8) | toInt(tokens(1)) & 0xff
      case "ldaddr" => (LDADDR << 8)
      case "shr"    => (SHR << 8)
      case "ldind"  => (LDIND << 8) | toInt(tokens(1)) & 0xff
      case "stind"  => (STIND << 8) | toInt(tokens(1)) & 0xff
      case "br"     => (BR << 8) | (toInt(tokens(1)) >> 1) & 0xfff
      case "brz"    => (BRZ << 8) | (toInt(tokens(1)) >> 1) & 0xfff
      case "brnz"   => (BRNZ << 8) | (toInt(tokens(1)) >> 1) & 0xfff
      case "brp"    => (BRP << 8) | (toInt(tokens(1)) >> 1) & 0xfff
      case "brn"    => (BRN << 8) | (toInt(tokens(1)) >> 1) & 0xfff
      case "loadi"  => (LOADI << 8) | toInt(tokens(1)) & 0xff
      case "loadhi"  => (LOADHI << 8) | toInt(tokens(1)) & 0xff
      case "loadh2i"  => (LOADH2I << 8) | toInt(tokens(1)) & 0xff
      case "loadh3i"  => (LOADH3I << 8) | toInt(tokens(1)) & 0xff
      case t: String =>
        throw new Exception("Assembler error: unknown instruction: " + t)
      case _ => throw new Exception("Assembler error")
    }
  }

  def intToBitSeq(v: Int) = (0 until 8).map { i => (v >> i) & 1 }.toSeq
  def intToByteSeq(v: Int, n: Int) =
    (0 until n).map { i => intToBitSeq((v >> i * 8) & 0xff) }.toSeq

  /**
    * Assembles input program @param s into a sequence of bytes
    */
  def assembleProgram(s: String): Seq[Seq[Int]] = {
    val program =
      s.split("\\\n").map(assembleInstruction).map(intToByteSeq(_, 2))
    program.reduce { (acc, i) => acc ++ i }
  }
}
