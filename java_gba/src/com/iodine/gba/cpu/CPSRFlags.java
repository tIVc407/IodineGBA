package com.iodine.gba.cpu;

/**
 * CPSRFlags - CPU Status Register flag management
 * Handles N (Negative), Z (Zero), C (Carry), V (Overflow) flags
 */
public class CPSRFlags {
    public int negative;
    public int zero;
    public int carry;
    public int overflow;

    public CPSRFlags() {
        this.negative = 0;
        this.zero = 0;
        this.carry = 0;
        this.overflow = 0;
    }

    public int getNZCV() {
        return ((negative & 0x80000000) | ((zero & 0xFFFFFFFF) == 0 ? 0x40000000 : 0) |
                ((carry != 0) ? 0x20000000 : 0) | ((overflow != 0) ? 0x10000000 : 0));
    }

    public void setNZCV(int flags) {
        negative = flags;
        zero = (flags & 0x40000000) == 0 ? 1 : 0;
        carry = (flags & 0x20000000) != 0 ? 1 : 0;
        overflow = (flags & 0x10000000) != 0 ? 1 : 0;
    }

    public void setNegative(int value) {
        negative = value;
    }

    public void setZero(int value) {
        zero = value;
    }

    public void setCarry(boolean value) {
        carry = value ? 1 : 0;
    }

    public void setOverflow(boolean value) {
        overflow = value ? 1 : 0;
    }

    public boolean getNegativeFlag() {
        return negative < 0;
    }

    public boolean getZeroFlag() {
        return zero == 0;
    }

    public boolean getCarryFlag() {
        return carry != 0;
    }

    public boolean getOverflowFlag() {
        return overflow != 0;
    }

    // ALU Operations with flag setting
    public int setSUBFlags(int operand1, int operand2) {
        int result = operand1 - operand2;
        negative = result;
        zero = result;
        carry = (operand1 >= operand2) ? 1 : 0;
        overflow = (((operand1 ^ operand2) & (operand1 ^ result)) >>> 31);
        return result;
    }

    public int setADDFlags(int operand1, int operand2) {
        int result = operand1 + operand2;
        negative = result;
        zero = result;
        carry = ((operand1 >>> 31) + (operand2 >>> 31)) > (result >>> 31) ? 1 : 0;
        overflow = (~(operand1 ^ operand2) & (operand1 ^ result)) >>> 31;
        return result;
    }

    public int setADCFlags(int operand1, int operand2) {
        int result = operand1 + operand2 + carry;
        int carryBit = carry;
        negative = result;
        zero = result;
        carry = ((operand1 >>> 31) + (operand2 >>> 31) + carryBit) > (result >>> 31) ? 1 : 0;
        overflow = (~(operand1 ^ operand2) & (operand1 ^ result)) >>> 31;
        return result;
    }

    public int setSBCFlags(int operand1, int operand2) {
        int result = operand1 - operand2 - (1 - carry);
        negative = result;
        zero = result;
        carry = (((operand1 >>> 31) + (1 - carry)) >= ((operand2 >>> 31) + 1)) ? 1 : 0;
        overflow = (((operand1 ^ operand2) & (operand1 ^ result)) >>> 31);
        return result;
    }

    public void setLogicFlags(int result) {
        negative = result;
        zero = result;
        // Carry and overflow are not affected
    }
}
