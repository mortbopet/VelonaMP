package velonamp.peripherals

import chisel3._
import chisel3.util._

import chisel3.iotesters.{ChiselFlatSpec, Driver, PeekPokeTester}

class UART_tx(baud : Int, freq : Int) extends Module {
    val io = IO(new Bundle {
        val tx = Output(Bool())
        val data_in = Flipped(Decoupled(UInt(8.W)))
    })

    // Clock generation
    val baud_max = freq / baud - 1
    val baud_counter = Reg(UInt(log2Ceil(baud_max).W))
    val baud_clk = Mux(baud_counter === 0.U, 1.B, 0.B)

    // 2 stop bits, 1 start bit, 8 data bits
    val bits = 2 + 1 + 8

    val shift_reg = RegInit((-1.S(bits.W)).asUInt) // all 1's
    val cnt_reg = RegInit(0.U(log2Ceil(bits).W))

    // Output default high when not transmitting
    io.tx := Mux(cnt_reg === 0.U, 1.U, shift_reg(0))
    io.data_in.ready := cnt_reg === 0.U

    when(io.data_in.valid) {
        shift_reg := 1.B ## 1.B ## io.data_in.bits ## 0.B
        cnt_reg := bits.U
        baud_counter := baud_max.U
    } .elsewhen(cnt_reg != 0.U && baud_clk) {
        shift_reg := 0.B ## shift_reg(bits - 1, 1)
        cnt_reg := cnt_reg - 1.U
    }

    // Baud clock
    when(cnt_reg != 0.U) {
        when(baud_counter === 0.U) {
            baud_counter := baud_max.U
        }.otherwise {
            baud_counter := baud_counter - 1.U
        }
    }
}

class UART_rx(baud : Int, freq : Int) extends Module {
    val io = IO(new Bundle {
        val rx = Input(Bool())
        val data_out = Decoupled(UInt(8.W))
    })

    // Clock generation
    val baud_max = freq / baud - 1
    val baud_counter = Reg(UInt(log2Ceil(baud_max).W))
    // Sample rx value halfway through baud cycle
    val baud_clk = Mux(baud_counter === (baud_max / 2).U, 1.B, 0.B)

    // 2 stop bits, 1 start bit, 8 data bits
    val bits = 2 + 1 + 8

    val shift_reg = Reg(UInt(bits.W))
    val cnt_reg = RegInit(0.U(log2Ceil(bits).W))
    val got_data = RegInit(0.B)

    io.data_out.valid := got_data
    io.data_out.bits := shift_reg(8, 1)

    when(cnt_reg === 0.U && io.rx === 0.B) {
        // Start bit
        cnt_reg := bits.U
        baud_counter := baud_max.U
    } .elsewhen(cnt_reg != 0.U && baud_clk) {
        when(cnt_reg === 1.U) {
            got_data := 1.U
        }
        cnt_reg := cnt_reg - 1.U
        shift_reg := io.rx ## shift_reg(bits - 1, 1)
    }.otherwise {
        got_data := 0.U
    }

    // Baud clock
    when(cnt_reg != 0.U) {
        when(baud_counter === 0.U) {
            baud_counter := baud_max.U
        }.otherwise {
            baud_counter := baud_counter - 1.U
        }
    }
}

class UART(baud : Int, freq : Int) extends Module {
    val io = IO(new Bundle{
        val tx = Output(Bool())
        val rx = Input(Bool())
        val data_in = Flipped(Decoupled(UInt(8.W)))
        val data_out = Decoupled(UInt(8.W))
    })

    val tx_mod = Module(new UART_tx(baud, freq))
    val rx_mod = Module(new UART_rx(baud, freq))

    io.tx := tx_mod.io.tx
    io.data_in <> tx_mod.io.data_in

    rx_mod.io.rx := io.rx
    rx_mod.io.data_out <> io.data_out
}


class UART_echo(baud : Int, freq : Int) extends Module {
    val io = IO(new Bundle {
        val tx = Output(Bool())
        val rx = Input(Bool())
    })

    val uart = Module(new UART(baud, freq))

    val cnt = RegInit(0.U(8.W))

    when(uart.io.data_out.valid) {
        cnt := cnt + uart.io.data_out.bits
    }

    io.tx := uart.io.tx
    uart.io.rx := io.rx

    uart.io.data_out.ready := uart.io.data_in.ready
    uart.io.data_in.bits := cnt
    uart.io.data_in.valid := uart.io.data_out.valid
}


object UART_echo extends App {
  chisel3.Driver.execute(Array[String](), () => new UART_echo(115200, 100000000))
}

class UART_echoTester(c: UART_echo, baud : Int, freq : Int) extends PeekPokeTester(c) {
    val baud_cycles = freq / baud
    def tx(bit : Int) {
        poke(c.io.rx, bit)
        step(baud_cycles)
    }

    poke(c.io.rx, 1)
    poke(c.io.tx, 1)
    step((baud_cycles*1.23).toInt)


    val bytes = List(
        Seq(1, 0, 0, 0, 0, 0, 0 ,0 ),
        Seq(1, 0, 0, 0, 0, 0, 0 ,0 ),
        Seq(1, 0, 0, 0, 0, 0, 0 ,0 ),
        Seq(1, 0, 0, 0, 0, 0, 0 ,0 )
    )

    for (byte <- bytes) {
        // Start bit
        tx(0)

        // Data
        for (bit <- byte) {
            tx(bit)
        }

        // Stop bits
        tx(1)
        tx(1)
    }

    step((baud_cycles * 11.12).toInt)
}


class UART_echoSpec extends ChiselFlatSpec {
  "UART_echoSpec" should "pass" in {
    Driver.execute(Array("--generate-vcd-output", "on") ,()
        => new UART_echo(115200, 100000000)) { c =>
      new UART_echoTester(c, 115200, 100000000)
    } should be(true)
  }
}
