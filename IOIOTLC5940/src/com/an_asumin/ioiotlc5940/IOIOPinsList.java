package com.an_asumin.ioiotlc5940;

public class IOIOPinsList {
	// ----------------------------------------------------
	// PIN Settings !
	// According to the pin function table of IOIO-OTG, please set the number of pin
	public static final int VPRG_PIN_NUM = 26;		// DigitalOutput
	public static final int SIN_PIN_NUM = 31;		// SpiMaster - mosiPin
	public static final int SCLK_PIN_NUM = 30;		// SpiMaster - clkPin
	public static final int XLAT_PIN_NUM = 27;		// DigitalOutput
	public static final int BLANK_PIN_NUM = 28;		// PwmOutput
	public static final int GSCLK_PIN_NUM = 29;		// PwmOutput
	// if you don't want to receive status information data, you don't have to connect sout pin.
	public static final int SOUT_PIN_NUM = 32;		// SpiMaster - misoPin
	// Slave Select pin must be set in order to use the function of SPI communication in IOIO-OTG board, there is no role in practice.
	// So don't connect this pin to any pin.
	public static final int SLAVE_SELECT_PIN_NUM = 8;
	//-----------------------------------------------------
}
