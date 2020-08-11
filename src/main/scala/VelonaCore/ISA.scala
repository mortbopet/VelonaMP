package VelonaCore
import chisel3._
import chisel3.util.BitPat
import chisel3.experimental.ChiselEnum

object ISA {

  // ISA constants
  val INSTR_WIDTH = 16
  val REG_WIDTH = 32

  // Datapath operations
  object Op extends ChiselEnum {
    val nop, addr, addi, subr, subi, shra, load, loadi, andr, andi, orr, ori,
        xorr, xori, loadhi, loadh2i, loadh3i, store, ioout, ioin, jal, ldaddr,
        ldind, ldindb, ldindh, stind, stindb, stindh, br, brz, brnz, brp, brn =
      Value;
  }

  // Opcode match expressions to functions
  val Opcodes = List(
    (BitPat("b00000???"), Op.nop), // OPC_NOP
    (BitPat("b000010?0"), Op.addr), // OPC_ADD
    (BitPat("b000010?1"), Op.addi), // OPC_ADDI
    (BitPat("b000011?0"), Op.subr), // OPC_SUB
    (BitPat("b000011?1"), Op.subi), // OPC_SUBI
    (BitPat("b00010???"), Op.shra), // OPC_SRA
    (BitPat("b00100000"), Op.load), // OPC_LOAD
    (BitPat("b00100001"), Op.loadi), // OPC_LOADI
    (BitPat("b00100010"), Op.andr), // OPC_AND
    (BitPat("b00100011"), Op.andi), // OPC_ANDI
    (BitPat("b00100100"), Op.orr), // OPC_OR
    (BitPat("b00100101"), Op.ori), // OPC_ORI
    (BitPat("b00100110"), Op.xorr), // OPC_XOR
    (BitPat("b00100111"), Op.xori), // OPC_XORI
    (BitPat("b00101001"), Op.loadhi), // OPC_LOADHI
    (BitPat("b00101010"), Op.loadh2i), // OPC_LOADH2I
    (BitPat("b00101011"), Op.loadh3i), // OPC_LOADH3I
    (BitPat("b00110???"), Op.store), // OPC_STORE
    (BitPat("b001110??"), Op.nop), // OPC_OUT
    (BitPat("b000001??"), Op.nop), // OPC_IN
    (BitPat("b01000???"), Op.jal), // OPC_JAL
    (BitPat("b01010???"), Op.ldaddr), // OPC_LDADDR
    (BitPat("b01100?00"), Op.ldind), // OPC_LDIND
    (BitPat("b01100?01"), Op.ldindb), // OPC_LDINDB
    (BitPat("b01100?10"), Op.ldindh), // OPC_LDINDH
    (BitPat("b01110?00"), Op.stind), // OPC_STIND
    (BitPat("b01110?01"), Op.stindb), // OPC_STINDB
    (BitPat("b01110?10"), Op.stindh), // OPC_STINDH
    (BitPat("b1000????"), Op.br), // OPC_BR
    (BitPat("b1001????"), Op.brz), // OPC_BRZ
    (BitPat("b1010????"), Op.brnz), // OPC_BRNZ
    (BitPat("b1011????"), Op.brp), // OPC_BRP
    (BitPat("b1100????"), Op.brn) // OPC_BRN
  )
}
