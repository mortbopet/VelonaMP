package velonamp.memory

import chisel3._
import velonamp.memory._
import chisel3.util._

import velonamp.util._
import velonamp.common.ISA
import velonamp.interconnect._
import chisel3.util.log2Ceil

import chisel3.iotesters.{ChiselFlatSpec, Driver, PeekPokeTester}

class CacheLine(val n_blocks: Int, val tag_bits: Int, val block_width: Int)
    extends Bundle {
  val dirty  = Bool()
  val tag    = UInt(tag_bits.W)
  val blocks = Vec(n_blocks, UInt(block_width.W))
}

object Cache {
  def genMask(n: Int): Vec[Bool] = { VecInit(Seq.fill(n)(1.B)) }
}

/** A simple direct-mapped cache.
  * @param n_lines: 2^(n_lines) cache lines
  * @param n_blocks: 2^(n_blocks) blocks in each cache line
  * @param read_only: If true, disables write interface
  *
  * @todo: Have a way to designated the cached address space to allow for
  * read/write-thru to hardware registers.
  */
class Cache(
    n_lines: Int,
    n_blocks: Int,
    block_bytes: Int,
    read_only: Boolean = false
) extends Module {
  val io = IO(new Bundle {
    val core_interface =
      Flipped(new MemoryExclusiveReadWriteInterface(ISA.REG_WIDTH, block_bytes))
    val host_interface = new OCPMasterInterface()
    val soft_reset     = Input(Bool())
  })

  // todo: static assert (OCP.BUS_DATA_BYTES % block_bytes == 0)
  val s_ready :: s_readLine :: s_writebackLine :: Nil = Enum(3)
  val state                                           = RegInit(s_ready)
  val blocksToStream                                  = RegInit((n_blocks).U)
  val blocksPerOCPTx                                  = OCP.BUS_DATA_BYTES / block_bytes

  // Compute access constants
  val byteOffset  = log2Ceil(block_bytes)
  val blockOffset = byteOffset + log2Ceil(n_blocks)
  val indexOffset = blockOffset + log2Ceil(n_lines)
  val tag_bits    = ISA.REG_WIDTH - indexOffset

  // Instantiate actual cache hardware
  val cacheLines = Reg(
    Vec(n_lines, new CacheLine(n_blocks, tag_bits, block_bytes * 8))
  )
  // Chisel does not seem to like initialization values in Vector-instantiated bundles, so valid bits are kept outside of the CacheLine bundle st. we may zero-initialize
  val validBits = RegInit(VecInit(Seq.fill(n_lines)(0.B)))

  // Compute access
  val blockIdx = (io.core_interface.address >> byteOffset) & Cache.genMask(
    log2Ceil(n_blocks)
  ).asUInt()
  val lineIdx = (io.core_interface.address >> blockOffset) & Cache.genMask(
    log2Ceil(n_lines)
  ).asUInt()
  val currentTag =
    (io.core_interface.address >> indexOffset) & Cache.genMask(tag_bits).asUInt()

  val currentLine = cacheLines(lineIdx)
  val cacheHit    = (currentLine.tag === currentTag && validBits(lineIdx))

  // Core <> Cache logic
  io.core_interface.op.ready := 0.B
  io.core_interface.data_in := currentLine.blocks(blockIdx)
  when(!io.soft_reset && state === s_ready && io.core_interface.op.valid) {
    when(cacheHit) {
      when(
        io.core_interface.op.bits === MemoryExclusiveReadWriteInterface.op_wr
      ) {
        currentLine.blocks(blockIdx) := io.core_interface.data_out
        currentLine.dirty := 1.B
      }
      io.core_interface.op.ready := 1.B
    }.otherwise {
      // Get value from memory; writeback current line if dirty
      blocksToStream := (n_blocks).U
      when(validBits(lineIdx) && currentLine.dirty) {
        state := s_writebackLine
      }.otherwise {
        state := s_readLine
      }
    }
  }

  io.host_interface.master.valid := 0.B
  io.host_interface.master.bits.mAddr := DontCare
  io.host_interface.master.bits.mCmd := OCP.MCmd.idle.U
  io.host_interface.master.bits.mByteEn := Cache.genMask(
    ISA.REG_BYTES
  ) // Always read/write all bytes
  io.host_interface.master.bits.mData := currentLine.blocks(blocksToStream)
  when(!io.soft_reset && state =/= s_ready) {
    io.host_interface.master.valid := 1.B  // Request bus access
    when(io.host_interface.master.ready) { // Wait for bus grant
      when(state === s_readLine) {
        io.host_interface.master.bits.mAddr := ((io.core_interface.address >> blockOffset) << blockOffset) + (blocksToStream << byteOffset)
        io.host_interface.master.bits.mCmd := OCP.MCmd.read.U
      }.elsewhen(state === s_writebackLine) {
        io.host_interface.master.bits.mAddr := (currentLine.tag << blockOffset) + (blocksToStream << byteOffset)
        io.host_interface.master.bits.mCmd := OCP.MCmd.write.U
      }

      when(io.host_interface.slave.sResp === OCP.SResp.dva.U) {
        blocksToStream := blocksToStream - blocksPerOCPTx.U
        io.host_interface.master.bits.mCmd := OCP.MCmd.idle.U
        when(state === s_readLine) {
          for (i <- 0 until blocksPerOCPTx) {
            currentLine.blocks(blocksToStream + i.U) := io.host_interface.slave
              .sData((i + 1) * block_bytes * 8 - 1, i * block_bytes * 8)
          }
        }
      }

      when(
        blocksToStream === 0.U && io.host_interface.master.bits.mCmd === OCP.MCmd.idle.U
      ) {
        // Current line written back to memory, now read requested line into cache
        when(state === s_writebackLine) {
          state := s_readLine
          blocksToStream := (n_blocks).U
        }.otherwise {
          currentLine.tag := currentTag
          validBits(lineIdx) := 1.B
          state := s_ready
        }
      }
    }
  }

  when(io.soft_reset) {
    validBits.foreach(_ := 0.B)
  }
}

/**
  * Wraps a Cache with a second OCP interface which bypasses the cache (read-/
  * write-through to memory) when the core accesses an uncacheable address.
  */
class CacheWrapper(
    n_lines: Int,
    n_blocks: Int,
    block_bytes: Int,
    read_only: Boolean = false
) extends Module {
  val io = IO(new Bundle {
    val core_interface =
      Flipped(new MemoryExclusiveReadWriteInterface(ISA.REG_WIDTH, block_bytes))
    val host_interface = new OCPMasterInterface()
    val soft_reset     = Input(Bool())
  })

  val uncacheable_access =
    UNCACHEABLE_START.U <= io.core_interface.address && io.core_interface.address < UNCACHEABLE_END.U
  val uncacheable_access_handled_reg = RegInit(0.B)

  /* Cache instantiation */
  val cache = Module(new Cache(n_lines, n_blocks, block_bytes, read_only))
  val cache_core_interface = Wire(new MemoryExclusiveReadWriteInterface(ISA.REG_WIDTH, block_bytes))
  cache_core_interface.address := io.core_interface.address
  cache_core_interface.mask := io.core_interface.mask
  cache_core_interface.data_out := io.core_interface.data_out
  cache_core_interface.op.valid := io.core_interface.op.valid && !uncacheable_access
  cache_core_interface.op.bits := io.core_interface.op.bits
  cache.io.core_interface <> cache_core_interface
  cache.io.soft_reset := io.soft_reset

  /* bypass OCP interface*/
  val bypass_interface = Wire(new OCPMasterInterface())
  val uncacheable_access_handled = WireDefault(0.B)
  val uncacheable_data_in = WireDefault(0.U(ISA.REG_WIDTH.W))
  bypass_interface.master.bits.mAddr := io.core_interface.address
  bypass_interface.master.bits.mByteEn := Cache.genMask(ISA.REG_BYTES)
  bypass_interface.master.bits.mCmd := Mux(io.core_interface.op.bits === MemoryExclusiveReadWriteInterface.op_rd, OCP.MCmd.read.U, OCP.MCmd.write.U)
  bypass_interface.master.bits.mData := io.core_interface.data_out
  bypass_interface.master.ready := io.host_interface.master.ready

  // OCP I/O logic
  bypass_interface.master.valid := 0.B
  when(uncacheable_access) {
    bypass_interface.master.valid := 1.B // Request bus access
    when(bypass_interface.master.ready && io.host_interface.slave.sResp === OCP.SResp.dva.U) { // Wait for bus grant
      uncacheable_access_handled := 1.B
    }
  }

  /* Outputs towards core */
  io.core_interface.op.ready := Mux(uncacheable_access, uncacheable_access_handled, cache_core_interface.op.ready)
  io.core_interface.data_in := Mux(uncacheable_access, uncacheable_data_in, cache_core_interface.data_in)

  /* Selected host interface MUX */
  cache.io.host_interface.master.ready := 0.B
  cache.io.host_interface.slave.sResp := DontCare
  cache.io.host_interface.slave.sData := DontCare
  bypass_interface.master.ready := 0.B
  bypass_interface.slave.sResp := DontCare
  bypass_interface.slave.sData := DontCare
  when(uncacheable_access) {
    io.host_interface <> bypass_interface
  } .otherwise {
    io.host_interface <> cache.io.host_interface
  }
}

class CacheTester(c: CacheWrapper) extends PeekPokeTester(c) {
  // Core interface
  poke(c.io.core_interface.address, 4)
  poke(c.io.core_interface.op.bits, MemoryExclusiveReadWriteInterface.op_rd)
  poke(c.io.core_interface.op.valid, 1)

  // ======================== Test 1: Cacheable address ========================
  poke(c.io.host_interface.master.ready, 1) // Bus grant
  poke(c.io.host_interface.slave.sData, 0)
  poke(c.io.host_interface.slave.sResp, OCP.SResp.dva)
  step(1)
  expect(c.io.core_interface.op.ready, 0, "Line should not be cached")
  step(4) // Stream in
  expect(c.io.core_interface.op.ready, 1, "Line should now be available")

  poke(c.io.core_interface.address, 5)
  poke(c.io.core_interface.op.bits, MemoryExclusiveReadWriteInterface.op_wr)
  expect(
    c.io.core_interface.op.ready,
    1,
    "Line already cached, write should go through"
  )
  step(1)
  poke(c.io.host_interface.master.ready, 0) // Bus released

  poke(c.io.core_interface.address, 4 + 1 << 8)
  poke(c.io.core_interface.op.bits, MemoryExclusiveReadWriteInterface.op_rd)
  expect(
    c.io.core_interface.op.ready,
    0,
    "address is not cached, but line is valid in cache. Line must be written back to memory"
  )
  step(4)
  poke(c.io.host_interface.master.ready, 1) // Bus granted
  step(10)

  // ======================= Test 2: Uncacheable address =======================
  poke(c.io.core_interface.address, UNCACHEABLE_START)
  poke(c.io.host_interface.slave.sData, 0)
  poke(c.io.host_interface.slave.sResp, OCP.SResp.dva)
  step(10)
}

class CacheSpec extends ChiselFlatSpec {
  "CacheSpec" should "pass" in {
    Driver.execute(
      Array("--generate-vcd-output", "on"),
      () => new CacheWrapper(4, 4, ISA.REG_BYTES, false)
    ) { c =>
      new CacheTester(c)
    } should be(true)
  }
}
