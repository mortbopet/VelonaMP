library ieee;
use ieee.std_logic_1164.all;

entity VelonaMP_top is
    port (
        clock : in std_logic;
        reset : in std_logic;
        dummy_out : out std_logic_vector(31 downto 0);
        instr_addr : in std_logic_vector(31 downto 0);
        instr_data : in std_logic_vector(15 downto 0)
    );
end VelonaMP_top;

architecture behavioral of VelonaMP_top is
begin
    mp : entity VelonaMP
    port map (
        clock => clock,
        reset => reset,
        io_dummy_out => dummy_out,
        io_instr_addr => instr_addr,
        io_instr_data => instr_data
    );
end behavioral;
