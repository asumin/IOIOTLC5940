IOIOTLC5940
===========

This is sample IOIO-OTG application to use TLC5940. You can control many LEDs from your Android device through Bluetooth or wired USB communication. This includes TLC5940 driver class (IOIOTLC5940.java).

[![IMAGE ALT TEXT HERE](http://img.youtube.com/vi/WhSfL354fno/0.jpg)](http://www.youtube.com/watch?v=WhSfL354fno)

###IOIO-OTG
IOIO-OTG is a Board that gives many I/Os to Android device. Please check the [ioio wiki](https://github.com/ytai/ioio/wiki)
 for more information.  

###TLC5940
TLC5940 is a 16ch LED driver with with PWM control and dot correction. You can control not only LEDs but also servos. (Anything is good as long as it uses a PWM control.) For more detail, please check [here](http://www.ti.com/product/tlc5940). (I recommend to read the data sheet.)

#Usage
(Please check IOIOTLC5940.java and include that file to your IOIO app project.)

###`IOIOTLC5940(IOIO ioio, int tlcNum, int vprgPinNum, int sinPinNum, int sclkPinNum, int xlatPinNum, int blankPinNum, int gsclkPinNum, int soutPinNum, int slaveSelectPinNum, boolean isStatusInformationDataEnabled)`
This is constructor. Please call this when you setup. If you use `BaseIOIOLooper`, call this on `setup()`. blankPin and gsclkPin use PWM pin of IOIO-OTG board. slaveSelectPin is required to use the SPI communication, but it is not actually used, and it is not necessary to connect. Last boolean argument indicates whether the receive status information data. If you do not want to receive the data, please set false, then you don't have to connect SOUT pin.

###`void setDotCorrection(int channel, float value)`  
This method sets dot correction data. Valid value is 0.0f(dark) to 1.0f(Bright). It is for covering difference of individual LED, not for controlling brightness. You can call `void setAllDotCorrectionData(float value)` instead.

###`void updateDotCorrection()`
This method updates dot correction data to TLC5940s through SPI communication.

###`void setGrayscale(int channel, float value)`
This method sets dot correction data. Valid value is 0.0f(dark) to 1.0f(Bright).

###`void updateGrayscale()`
This method updates grayscale data to TLC5940s through SPI communication.

###`void setEnabled(boolean enabled)`
BLANK(no light) is enabled in the initial to save from abnormal lighting. This method enables/disables lights. So, when you ready to light, call `setEnabled(true)`.

There are more function in IOIOTLC5940.java...

#Note
 * Please refer to the IOIOLib(s) in the same way as developing normal IOIO application 
 * Please connect BLANK pin with PULL-UP resistor
 * This driver class(IOIOTLC5940.java) don't use XERR and EEPROM

#In oder to run sample app...
I recommend to use only TLC5940 driver class. This is because, since the  sample application's configuration is very limited.(e.g. API level)

1.Prepare the following:

 * Android device (API level 14 or higher)
 * IOIO-OTG board 
 * micro-USB cable or Bluetooth Adapter, or both
 * TLC5940 (more than 3)
 * Anode common full color LEDs (more than 16, e.g. OSTA71A1D-A)
 * Some bread boards, jump wires and carbon resistors...  

2.Make the circuit

 * Recommend to reference [this page](http://tlc5940arduino.googlecode.com/svn/wiki/images/breadboard-arduino-tlc5940.png)
 * Please connect BLANK pin with PULL-UP resistor
 * 2k resistor for IREF is good for most LEDs

3.Modify java code

 * Load the project with Eclipse (with Android SDK and IOIO lib)
 * Check "IOIOPinsList.java" and change the number of pin settings...

```java:IOIOPinsList.java
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
```

4.Run! :-)
