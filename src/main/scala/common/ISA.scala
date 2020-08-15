package velonamp.common
import chisel3._
import chisel3.util.{BitPat, Enum}

object ISA {

  // ISA constants
  def INSTR_WIDTH = 16
  def REG_WIDTH   = 32

  // Datapath operation constants
  def OP_nop     = 0.U(5.W)
  def OP_addr    = 1.U(5.W)
  def OP_addi    = 2.U(5.W)
  def OP_subr    = 3.U(5.W)
  def OP_subi    = 4.U(5.W)
  def OP_shra    = 5.U(5.W)
  def OP_load    = 6.U(5.W)
  def OP_loadi   = 7.U(5.W)
  def OP_andr    = 8.U(5.W)
  def OP_andi    = 9.U(5.W)
  def OP_orr     = 10.U(5.W)
  def OP_ori     = 11.U(5.W)
  def OP_xorr    = 12.U(5.W)
  def OP_xori    = 13.U(5.W)
  def OP_loadhi  = 14.U(5.W)
  def OP_loadh2i = 15.U(5.W)
  def OP_loadh3i = 16.U(5.W)
  def OP_store   = 17.U(5.W)
  def OP_jal     = 18.U(5.W)
  def OP_ldaddr  = 19.U(5.W)
  def OP_ldind   = 20.U(5.W)
  def OP_ldindb  = 21.U(5.W)
  def OP_ldindh  = 22.U(5.W)
  def OP_stind   = 23.U(5.W)
  def OP_stindb  = 24.U(5.W)
  def OP_stindh  = 25.U(5.W)
  def OP_br      = 26.U(5.W)
  def OP_brz     = 27.U(5.W)
  def OP_brnz    = 28.U(5.W)
  def OP_brp     = 29.U(5.W)
  def OP_brn     = 30.U(5.W)

  // Opcode match expressions to functions
  val Opcodes = List(
    (BitPat("b00000???"), OP_nop),     // OPC_NOP
    (BitPat("b000010?0"), OP_addr),    // OPC_ADD
    (BitPat("b000010?1"), OP_addi),    // OPC_ADDI
    (BitPat("b000011?0"), OP_subr),    // OPC_SUB
    (BitPat("b000011?1"), OP_subi),    // OPC_SUBI
    (BitPat("b00010???"), OP_shra),    // OPC_SRA
    (BitPat("b00100000"), OP_load),    // OPC_LOAD
    (BitPat("b00100001"), OP_loadi),   // OPC_LOADI
    (BitPat("b00100010"), OP_andr),    // OPC_AND
    (BitPat("b00100011"), OP_andi),    // OPC_ANDI
    (BitPat("b00100100"), OP_orr),     // OPC_OR
    (BitPat("b00100101"), OP_ori),     // OPC_ORI
    (BitPat("b00100110"), OP_xorr),    // OPC_XOR
    (BitPat("b00100111"), OP_xori),    // OPC_XORI
    (BitPat("b00101001"), OP_loadhi),  // OPC_LOADHI
    (BitPat("b00101010"), OP_loadh2i), // OPC_LOADH2I
    (BitPat("b00101011"), OP_loadh3i), // OPC_LOADH3I
    (BitPat("b00110???"), OP_store),   // OPC_STORE
    (BitPat("b001110??"), OP_nop),     // OPC_OUT
    (BitPat("b000001??"), OP_nop),     // OPC_IN
    (BitPat("b01000???"), OP_jal),     // OPC_JAL
    (BitPat("b01010???"), OP_ldaddr),  // OPC_LDADDR
    (BitPat("b01100?00"), OP_ldind),   // OPC_LDIND
    (BitPat("b01100?01"), OP_ldindb),  // OPC_LDINDB
    (BitPat("b01100?10"), OP_ldindh),  // OPC_LDINDH
    (BitPat("b01110?00"), OP_stind),   // OPC_STIND
    (BitPat("b01110?01"), OP_stindb),  // OPC_STINDB
    (BitPat("b01110?10"), OP_stindh),  // OPC_STINDH
    (BitPat("b1000????"), OP_br),      // OPC_BR
    (BitPat("b1001????"), OP_brz),     // OPC_BRZ
    (BitPat("b1010????"), OP_brnz),    // OPC_BRNZ
    (BitPat("b1011????"), OP_brp),     // OPC_BRP
    (BitPat("b1100????"), OP_brn)      // OPC_BRN
  )
}

object ABI {
    val ConstantRegisters = List(
    (100.U, 0x0.U),
    (101.U, 0x1.U),
    (102.U, 0x80.U),
    (103.U, 0x8000.U),
    (104.U, "x80000000".U),
    (105.U, 0xff.U),
    (106.U, 0xffff.U),
    (107.U, "xffff0000".U),
    (108.U, 0x7fffffff.U)
  )
}