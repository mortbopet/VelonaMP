package velonamp.memory

import chisel3._
import chisel3.util._
import chisel3.iotesters.{ChiselFlatSpec, Driver, PeekPokeTester}

import velonamp.util._
import velonamp.common.{ABI, ISA}
import velonamp.interconnect._

import scala.collection.mutable

class MemoryReadInterface(val addr_width: Int, val data_bytes : Int) extends Bundle {
  val address = Input(UInt(addr_width.W))
  val data    = Decoupled(UInt((data_bytes*8).W))
}

class MemoryWriteInterface(val addr_width: Int, val data_bytes : Int) extends Bundle {
  val address = Input(UInt(addr_width.W))
  val mask = Input(Vec(data_bytes, Bool()))
  val data    = Flipped(Decoupled(UInt((data_bytes*8).W)))
}

class MemoryReadWriteInterface(val addr_width: Int, val w_data: Int)
    extends Bundle {
  val read  = new MemoryReadInterface(addr_width, w_data)
  val write = new MemoryWriteInterface(addr_width, w_data)
}

object MemoryExclusiveReadWriteInterface {
  val op_nop :: op_rd :: op_wr :: Nil = Enum(3)
}

class MemoryExclusiveReadWriteInterface(val addr_width: Int, val data_bytes : Int)
  extends Bundle {
  val address = Output(UInt(addr_width.W))
  val mask = Output(Vec(data_bytes, Bool()))
  val data_in    = Output(UInt((data_bytes*8).W))
  // Valid/ready handshaking between source and sink to be performed on the
  // op signal
  val op = Decoupled(UInt(2.W))
  val data_out = Input(UInt((data_bytes*8).W))
}

class RWMemory(addr_width: Int, data_bytes: Int, size: Int) extends Module {
  val io = IO(new MemoryReadWriteInterface(addr_width, data_bytes))

  val mem = SyncReadMem(size, Vec(data_bytes, UInt(8.W)))
  val writeToReadAddr = io.read.address === io.write.address && io.write.data.valid

  val doForwardWrite = RegNext(writeToReadAddr)
  val delayedWriteData = RegNext(io.write.data.bits)

  val inVec = Wire(Vec(data_bytes, UInt(8.W)))
  val readData = Wire(UInt((data_bytes * 8).W))

  // Split input word to bytes
  for(i <- 0 until data_bytes) {
    inVec(i) := io.write.data.bits((i+1)*8 - 1, i*8)
  }
  // Merge output bytes to word
  readData := mem.read(io.read.address).asUInt()

  io.read.data.bits := Mux(doForwardWrite, delayedWriteData, readData)
  io.read.data.valid := true.B
  io.write.data.ready := false.B
  when(io.write.data.valid) {
    mem.write(io.write.address, inVec, io.write.mask)
  }
  io.write.data.ready := true.B
}
class OCPRWMemory(addr_start : Int, addr_width: Int, data_bytes: Int, size: Int) extends OCPSlave {
  val mem = Module(new RWMemory(addr_width, data_bytes, size))
  def ocpStart(): UInt = addr_start.U
  def ocpEnd(): UInt = addr_start.U + size.U


  // Translation between OCP <> Read/Write interface

  val mem_addr = ocp_interface.master.bits.mAddr - addr_start.U
  /** @todo All of the following assignments are temporary; Proper handshaking will be
   * implemented later.*/
  ocp_interface.master.ready := mem.io.read.data.valid // Unused
  ocp_interface.slave.bits.sData := mem.io.read.data.bits
  ocp_interface.slave.bits.sCmdAccept := 1.B
  ocp_interface.slave.bits.sResp := OCP.SResp.dva.U

  // Write interface
  mem.io.write.address := mem_addr
  mem.io.write.mask := ocp_interface.master.bits.mByteEn
  mem.io.write.data.bits := ocp_interface.master.bits.mData
  mem.io.write.data.valid := ocp_interface.master.bits.mCmd === OCP.MCmd.write.U
  mem.io.read.data.ready := 1.B // unused

  // Read interface
  mem.io.read.address := mem_addr
}

class RWMemoryTester(c: RWMemory) extends PeekPokeTester(c) {
  def toPokeableValue[T <: Int](x : T) = BigInt(x)
  val addr    = 42
  val value_1 = 123
  val value_2 = value_1 + 1
  val mask = Array(1,1,1,1) map toPokeableValue


  poke(c.io.write.address, addr)
  poke(c.io.read.address, addr)

  // Write
  poke(c.io.write.data.bits, value_1)
  poke(c.io.write.mask, mask)

  poke(c.io.write.data.valid, true)
  step(1)
  poke(c.io.write.data.valid, false)

  // read
  poke(c.io.read.data.ready, true)
  step(1)
  expect(c.io.read.data.bits, value_1)

  // Simultanious read/write
  poke(c.io.read.data.ready, true)
  poke(c.io.write.data.valid, true)
  poke(c.io.write.data.bits, value_2)
  step(1)
  expect(c.io.read.data.bits, value_2)
  step(1)
  expect(c.io.read.data.bits, value_2)
}

class PodMemorySpec extends ChiselFlatSpec {
    "PodMemory" should "pass" in {
    Driver.execute(Array("--generate-vcd-output", "on") ,()
        => new RWMemory(ISA.REG_WIDTH, ISA.REG_BYTES, 256)) { c =>
      new RWMemoryTester(c)
    } should be(true)
  }
}
