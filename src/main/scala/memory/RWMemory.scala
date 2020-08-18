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

  /** @todo All of the following assignments are temporary; Proper handshaking will be
   * implemented later.*/
  ocp_interface.master.ready := 1.B // Unused
  ocp_interface.slave.bits.sData := mem.io.read.data
  ocp_interface.slave.bits.sCmdAccept := 1.B
  ocp_interface.slave.bits.sResp := OCP.SResp.dva.U

  val mem_addr = ocp_interface.master.bits.mAddr - addr_start.U
  mem.io.write.address := mem_addr
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
