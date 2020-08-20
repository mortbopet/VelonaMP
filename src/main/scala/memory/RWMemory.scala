package velonamp.memory

import chisel3._
import chisel3.util._
import chisel3.iotesters.{ChiselFlatSpec, Driver, PeekPokeTester}

import velonamp.util._
import velonamp.common.{ABI, ISA}
import velonamp.interconnect._

import scala.collection.mutable

class MemoryReadInterface(val addr_width: Int, val data_bytes : Int) extends Bundle {
  val address = Output(UInt(addr_width.W))
  val data    = Flipped(Decoupled(UInt((data_bytes*8).W)))
}

class MemoryWriteInterface(val addr_width: Int, val data_bytes : Int) extends Bundle {
  val address = Output(UInt(addr_width.W))
  val mask = Output(Vec(data_bytes, Bool()))
  val data    = Decoupled(UInt((data_bytes*8).W))
}

class MemoryReadWriteInterface(val addr_width: Int, val w_data: Int)
    extends Bundle {
  val read  = new MemoryReadInterface(addr_width, w_data)
  val write = new MemoryWriteInterface(addr_width, w_data)
}

object MemoryExclusiveReadWriteInterface {
  val op_rd :: op_wr :: Nil = Enum(2)
}

class MemoryExclusiveReadWriteInterface(val addr_width: Int, val data_bytes : Int)
  extends Bundle {
  val address = Output(UInt(addr_width.W))
  val mask = Output(Vec(data_bytes, Bool()))
  val data_out = Output(UInt((data_bytes*8).W))
  val data_in    = Input(UInt((data_bytes*8).W))
  // Valid/ready handshaking between source and sink to be performed on the
  // op signal
  val op = Decoupled(UInt())
}

class RWMemory(addr_width: Int, data_bytes: Int, size: Int) extends Module {
  val io = IO(Flipped(new MemoryReadWriteInterface(addr_width, data_bytes)))

  val mem = SyncReadMem(size, Vec(data_bytes, UInt(8.W)))
  val writeToReadAddr = io.read.address === io.write.address && io.write.data.valid

  val doForwardWrite = RegNext(writeToReadAddr)
  val delayedWriteData = RegNext(io.write.data.bits)
  // Any operation through the memory takes 1 cycle
  val operationSuccessful = RegNext(io.write.data.valid)

  val inVec = Wire(Vec(data_bytes, UInt(8.W)))
  val readData = Wire(UInt((data_bytes * 8).W))

  // Split input word to bytes
  for(i <- 0 until data_bytes) {
    inVec(i) := io.write.data.bits((i+1)*8 - 1, i*8)
  }
  // Merge output bytes to word
  readData := mem.read(io.read.address).asUInt()

  io.read.data.bits := Mux(doForwardWrite, delayedWriteData, readData)
  when(io.write.data.valid) {
    mem.write(io.write.address, inVec, io.write.mask)
  }
  io.write.data.ready := operationSuccessful
  io.read.data.valid := operationSuccessful
}
class OCPRWMemory(addr_start : Int, addr_width: Int, data_bytes: Int, size: Int) extends OCPSlave {
  def ocpStart(): UInt = addr_start.U
  def ocpEnd(): UInt = addr_start.U + size.U

  // Any operation through the memory takes 1 cycle
  val supportsOperation = (ocp_interface.master.mCmd === OCP.MCmd.read.U) || (ocp_interface.master.mCmd === OCP.MCmd.write.U)
  val operationSuccessful = RegNext(accessIsInThisAddressRange && supportsOperation)

  val inVec = Wire(Vec(data_bytes, UInt(8.W)))
  val readData = Wire(UInt((data_bytes * 8).W))
  val mem = SyncReadMem(size, Vec(data_bytes, UInt(8.W)))

  // Split input word to bytes
  for(i <- 0 until data_bytes) {
    inVec(i) := ocp_interface.master.mData((i+1)*8 - 1, i*8)
  }
  // Merge output bytes to word
  readData := mem.read(ocp_interface.master.mAddr).asUInt()

  ocp_interface.slave.bits.sData := readData
  when(accessIsInThisAddressRange && ocp_interface.master.mCmd === OCP.MCmd.write.U) {
    mem.write(ocp_interface.master.mAddr, inVec, ocp_interface.master.mByteEn)
  }

  ocp_interface.slave.bits.sResp := Mux(operationSuccessful, OCP.SResp.dva.U, OCP.SResp.none.U)
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
