package ioio.example.tlc5940;

import ioio.lib.api.DigitalInput;
import ioio.lib.api.DigitalOutput;
import ioio.lib.api.DigitalOutput.Spec.Mode;
import ioio.lib.api.IOIO;
import ioio.lib.api.PwmOutput;
import ioio.lib.api.SpiMaster;
import ioio.lib.api.exception.ConnectionLostException;

public class IOIOTLC5940 {
	
	// public constants
	public static final int TLC_CHANNEL_NUM = 16;
	public static final int GRAYSCALE_STEP_NUM = 4096;
	public static final int DOT_CORRECTION_STEP_NUM = 64;
	// private constants
	private final int TLC_GRAYSCALE_BYTE_SIZE = 24;
	private final int TLC_DOT_CORRECTION_BYTE_SIZE = 12;
	private final int TLC_STATUS_INFORMATION_DATA_BYTE_SIZE = 24;
	private final int GRAYSCALE_BIT_NUM = 12;
	private final int DOT_CORRECTION_BIT_NUM = 6;
	private final int GRAYSCALE_OUTPUT_HZ = 960;	// is this current PWM output limit ?
	private final SpiMaster.Rate SPI_RATE_HZ = SpiMaster.Rate.RATE_8M;
	private final int blankFrequency_ = GRAYSCALE_OUTPUT_HZ;
	private final int gsclkFrequency_ = GRAYSCALE_OUTPUT_HZ * (GRAYSCALE_STEP_NUM + 1);
	// Number of TLC5940
	private final int tlcNum_;
	private final int totalChannelNum_;
	// Data
	private final int[] grayscaleData_;
	private final int[] dotCorrectionData_;
	private final byte[][] statusInformationData_;
	// internal parameters
	private boolean needsExtraSclk_;
	private boolean isBlankMode_;
	private boolean isStatusInformationDataEnabled_;
	private boolean hasStatusInformationData_;
	private boolean hasNewGrayscaleData_;
	private boolean hasNewDotCorrectionData_;
	// Pin assigns
	private final int vprgPinNum_;
	private final int sinPinNum_;
	private final int sclkPinNum_;
	private final int xlatPinNum_;
	private final int blankPinNum_;
	private final int gsclkPinNum_;
	private final int soutPinNum_;
	private final int slaveSelectPinNum_;
	// IOIO instances
	private IOIO ioio_;
	// IOIO Pin instances
	private DigitalOutput xlat_;
	private DigitalOutput vprg_;
	private SpiMaster spi_;
	private PwmOutput blank_;
	private PwmOutput gsclk_;
	
	public IOIOTLC5940(IOIO ioio, int tlcNum, 
			int vprgPinNum, int sinPinNum, int sclkPinNum, int xlatPinNum,
			int blankPinNum, int gsclkPinNum, int soutPinNum, int slaveSelectPinNum,
			boolean isStatusInformationDataEnabled) throws ConnectionLostException{

		// This call might be unnecessary
		android.os.Process.setThreadPriority(android.os.Process.THREAD_PRIORITY_URGENT_AUDIO);
		
		ioio_ = ioio;
		tlcNum_ = tlcNum;
		totalChannelNum_ = tlcNum_ * TLC_CHANNEL_NUM;
		grayscaleData_ = new int [tlcNum_ * TLC_CHANNEL_NUM];
		dotCorrectionData_ = new int [tlcNum_ * TLC_CHANNEL_NUM];
		statusInformationData_ = new byte [tlcNum_][TLC_STATUS_INFORMATION_DATA_BYTE_SIZE];

		vprgPinNum_ = vprgPinNum;
		sinPinNum_ = sinPinNum;
		sclkPinNum_ = sclkPinNum;
		xlatPinNum_ = xlatPinNum;
		blankPinNum_ = blankPinNum;
		gsclkPinNum_ = gsclkPinNum;
		soutPinNum_ = soutPinNum;
		slaveSelectPinNum_ = slaveSelectPinNum;
		
		needsExtraSclk_ = true;
		isBlankMode_ = false;
		isStatusInformationDataEnabled_ = isStatusInformationDataEnabled;
		hasStatusInformationData_ = false;
		hasNewGrayscaleData_ = false;
		hasNewDotCorrectionData_ = false;

		xlat_ = ioio.openDigitalOutput(xlatPinNum_, false);
		vprg_ = ioio.openDigitalOutput(vprgPinNum_, false);
		spi_ = ioio.openSpiMaster(new DigitalInput.Spec(soutPinNum_), new DigitalOutput.Spec(sinPinNum_), new DigitalOutput.Spec(sclkPinNum_), new DigitalOutput.Spec[]{ new DigitalOutput.Spec(slaveSelectPinNum_)}, new SpiMaster.Config(SPI_RATE_HZ, false, false));
//		spi_ = ioio.openSpiMaster(new DigitalInput.Spec(soutPinNum_), new DigitalOutput.Spec(sinPinNum_), new DigitalOutput.Spec(sclkPinNum_), new DigitalOutput.Spec[]{ new DigitalOutput.Spec(slaveSelectPinNum_)}, new SpiMaster.Config(SPI_RATE_HZ, false, true));
		blank_ = ioio.openPwmOutput(new DigitalOutput.Spec(blankPinNum_, Mode.OPEN_DRAIN), blankFrequency_);
		setBLANK();
		gsclk_ = ioio.openPwmOutput(gsclkPinNum_, gsclkFrequency_);
		stopGSCLK();
	}

	public void setEnabled(boolean enabled) throws ConnectionLostException{
		if(enabled) removeBLANK();
		else setBLANK();
	}
	
	public boolean isEnabled(){
		return isBlankMode_;
	}
	
	private void setBLANK() throws ConnectionLostException{
		blank_.setDutyCycle((float) 1.0);
		isBlankMode_ = true;
	}
	
	private void removeBLANK() throws ConnectionLostException{
		blank_.setDutyCycle((float) 1/(GRAYSCALE_STEP_NUM + 1));
		isBlankMode_ = false;
	}
	
	private void stopGSCLK() throws ConnectionLostException{
		gsclk_.setDutyCycle((float) 0.0);
	}

	private void startGSCLK() throws ConnectionLostException{
		gsclk_.setDutyCycle((float) 0.5);
	}
	
	private void pulse(DigitalOutput output) throws ConnectionLostException{
		output.write(true);
		output.write(false);
	}
	
	private void alignData(byte[] dstData, int refTlcNum, int[] srcData, int srcDataBitWidth){
		int srcDataMostSignificantBit = 1 << (srcDataBitWidth - 1);	
		int dstDataIndex = 0;
		int dstDataShiftCounter = 0;
		
		// starts with most significant channel
		for(int i = TLC_CHANNEL_NUM - 1; i >= 0; --i){
			int srcDataIndex = TLC_CHANNEL_NUM * (refTlcNum - 1) + i;
			int bitChecker = srcDataMostSignificantBit;			
			while(bitChecker != 0){
				dstData[dstDataIndex] |= bitChecker == (bitChecker & srcData[srcDataIndex]) ? 0x1 : 0x0;
				bitChecker >>>= 1;

				// 1byte == 8bit ...
				if(++dstDataShiftCounter >= 8){
					dstDataShiftCounter = 0;
					++dstDataIndex;
				}else{
					dstData[dstDataIndex] <<= 1;
				}
			}
		}
	}
	
	public void updateGrayscaleDirectly(int[] data) throws ConnectionLostException, InterruptedException{
		for(int i = 0; i < tlcNum_ * TLC_CHANNEL_NUM; ++i){
			setGrayscale(i, data[i]);
		}
		updateGrayscale();
	}
	
	public void updateGrayscaleInStepDirectly(int[] data) throws ConnectionLostException, InterruptedException{
		for(int i = 0; i < tlcNum_ * TLC_CHANNEL_NUM; ++i){
			setGrayscaleInStep(i, data[i]);
		}
		updateGrayscale();
	}

	public void updateGrayscale() throws ConnectionLostException, InterruptedException{

		// update only when it has new data
		if(!hasNewGrayscaleData_) return;
		
		// making gray scale data
		byte[][] grayscaleOutputData = new byte[tlcNum_][TLC_GRAYSCALE_BYTE_SIZE];
		for(int i = 0; i < tlcNum_; ++i){
			alignData(grayscaleOutputData[i], (tlcNum_ - i), grayscaleData_, GRAYSCALE_BIT_NUM);
		}

		// depending on the mode of just before
		if(needsExtraSclk_){
			if(isStatusInformationDataEnabled_){
				for(int i = 0; i < tlcNum_; ++i){
					spi_.writeRead(grayscaleOutputData[i], grayscaleOutputData[i].length, grayscaleOutputData[i].length, statusInformationData_[tlcNum_ - 1 - i], statusInformationData_[tlcNum_ - 1 - i].length);
				}
				hasStatusInformationData_ = true;
			}else{
				ioio_.beginBatch();
				for(int i = 0; i < tlcNum_ -1; ++i){
					spi_.writeReadAsync(0, grayscaleOutputData[i], grayscaleOutputData[i].length, grayscaleOutputData[i].length, null, 0);
				}
				ioio_.endBatch();
				byte[] dummyRead = new byte[1];
				spi_.writeRead(grayscaleOutputData[tlcNum_ - 1], grayscaleOutputData[tlcNum_ - 1].length, grayscaleOutputData[tlcNum_ - 1].length, dummyRead, dummyRead.length);
				hasStatusInformationData_ = false;
			}
		}else{
			ioio_.beginBatch();
			vprg_.write(false);
			needsExtraSclk_ = true;
			for(int i = 0; i < tlcNum_ -1; ++i){
				spi_.writeReadAsync(0, grayscaleOutputData[i], grayscaleOutputData[i].length, grayscaleOutputData[i].length, null, 0);
			}
			ioio_.endBatch();
			byte[] dummyRead = new byte[1];
			spi_.writeRead(grayscaleOutputData[tlcNum_ - 1], grayscaleOutputData[tlcNum_ - 1].length, grayscaleOutputData[tlcNum_ - 1].length, dummyRead, dummyRead.length);
			hasStatusInformationData_ = false;
		}
		
		// latch!
		ioio_.beginBatch();
		stopGSCLK();
		if(isBlankMode_){
			pulse(xlat_);
		}else{
			setBLANK();
			pulse(xlat_);
			removeBLANK();
		}
		startGSCLK();
		ioio_.endBatch();
		hasNewGrayscaleData_ = false;
	}
	
	public void setStatusInformationDataEnabled(boolean enabled){
		isStatusInformationDataEnabled_ = enabled;
	}
	
	public boolean isStatusInformationDataEnabled(){
		return isStatusInformationDataEnabled_;
	}
	
	public boolean hasStatusInformationData(){
		return hasStatusInformationData_;
	}
	
	public boolean getLedOpenDetection(int channel){
		if(!hasStatusInformationData_) return false;
		if(channel < 0 || channel >= totalChannelNum_) return false;
		
		int tlcIndex = channel / TLC_CHANNEL_NUM;
		int channelIndex = channel % TLC_CHANNEL_NUM;
		// 8 - 15
		if(channelIndex > 7){
			byte bitChecker = (byte) (1 << (channelIndex-8));			
			return bitChecker == (bitChecker & statusInformationData_[tlcIndex][0]);
		// 0 - 7
		}else{
			byte bitChecker = (byte) (1 << channelIndex);			
			return bitChecker == (bitChecker & statusInformationData_[tlcIndex][1]);
		}
	}
	
	public boolean getThermalErrorFlag(int tlcNum){
		if(tlcNum < 0 || tlcNum >= tlcNum_) return false;
		byte bitChecker = (byte) (1 << 7);
		return bitChecker == (bitChecker & statusInformationData_[tlcNum][2]);
	}
	
	public boolean hasNewGrayscaleData(){
		return hasNewGrayscaleData_;
	}

	private int arrangeGrayscaleValueInRange(int value){
		if(value < 0) return 0;
		else if(value >= GRAYSCALE_STEP_NUM) return GRAYSCALE_STEP_NUM - 1;
		else return value;
	}
	
	public void setGrayscaleInStep(int channel, int value){
		if(channel < 0 || channel >= totalChannelNum_) return;
		value = arrangeGrayscaleValueInRange(value);
		// new value?
		if(grayscaleData_[channel] == value) return;
		grayscaleData_[channel] = value;
		hasNewGrayscaleData_ = true;
	}
	
	public void setGrayscale(int channel, float value){
		setGrayscaleInStep(channel, (int) Math.round((GRAYSCALE_STEP_NUM - 1) * value));
	}
	
	public int getGrayscaleInStep(int channel){
		return grayscaleData_[channel];
	}
	
	public float getGrayscale(int channel) {
		return grayscaleData_[channel] / (GRAYSCALE_STEP_NUM - 1);
	}
	
	public void setAllGrayscaleInStep(int value){
		value = arrangeGrayscaleValueInRange(value);
		for(int i = 0; i < grayscaleData_.length; ++i) grayscaleData_[i] = value;
		hasNewGrayscaleData_ = true;
	}

	public void setAllGrayscale(float value){
		setAllGrayscaleInStep((int) Math.round((GRAYSCALE_STEP_NUM - 1) * value));
	}
	
	public void updateDotCorrection() throws ConnectionLostException, InterruptedException{

		// update only when it has new data
		if(!hasNewDotCorrectionData_) return;
		
		ioio_.beginBatch();
		// enter dot-correction mode
		vprg_.write(true);
		needsExtraSclk_ = false;
		// making byte data
		byte[][] dotCorrectionOutputData = new byte[tlcNum_][TLC_DOT_CORRECTION_BYTE_SIZE];
		for(int i = 0; i < tlcNum_; ++i){
			alignData(dotCorrectionOutputData[i], (tlcNum_ - i), dotCorrectionData_, DOT_CORRECTION_BIT_NUM);
		}

		// output with SPI
		for(int i = 0; i < tlcNum_-1; ++i){
			spi_.writeReadAsync(0, dotCorrectionOutputData[i], dotCorrectionOutputData[i].length, dotCorrectionOutputData[i].length, null, 0);
		}
		ioio_.endBatch();
		byte[] dummyRead = new byte[1];
		spi_.writeRead(dotCorrectionOutputData[tlcNum_-1], dotCorrectionOutputData[tlcNum_-1].length, dotCorrectionOutputData[tlcNum_-1].length, dummyRead, dummyRead.length);

		// latch
		pulse(xlat_);
		hasNewDotCorrectionData_ = false;
	}
	
	public boolean hasNewDotCorrectionData(){
		return hasNewDotCorrectionData_;
	}
	
	private int arrangeDotCorrectionValueInRange(int value){
		if(value < 0) return 0;
		else if(value >= DOT_CORRECTION_STEP_NUM) return DOT_CORRECTION_STEP_NUM - 1;		
		else return value;
	}
	
	public void setDotCorrectionInStep(int channel, int value){
		if(channel < 0 || channel >= totalChannelNum_) return;
		value = arrangeDotCorrectionValueInRange(value);
		// new data?
		if(dotCorrectionData_[channel] == value) return;
		dotCorrectionData_[channel] = value;
		hasNewDotCorrectionData_ = true;
	}
	
	public void setDotCorrection(int channel, float value){
		setDotCorrectionInStep(channel, (int) Math.round((DOT_CORRECTION_STEP_NUM - 1) * value));
	}	
	
	public int getDotCorrectionInStep(int channel){
		return dotCorrectionData_[channel];
	}

	public float getDotCorrection(int channel){
		return dotCorrectionData_[channel] / (DOT_CORRECTION_STEP_NUM - 1);
	}
	
	public void setAllDotCorrectionInStep(int value){
		value = arrangeDotCorrectionValueInRange(value);
		for(int i = 0; i < dotCorrectionData_.length; ++i) dotCorrectionData_[ i ] = value;
		hasNewDotCorrectionData_ = true;
	}
	
	public void setAllDotCorrection(float value){
		setAllDotCorrectionInStep((int) Math.round((DOT_CORRECTION_STEP_NUM - 1) * value));
	}
}
